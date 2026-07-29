// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.index.mermaid.tool.MermaidIndexTools;
import com.github.istin.dmtools.job.JavaScriptExecutor;
import com.github.istin.dmtools.job.Params;
import com.github.istin.dmtools.job.ResultItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end flow coverage tests for {@link Teammate#runJobImpl}.
 *
 * The per-ticket processing flow is exercised with fully mocked dependencies
 * (tracker, AI, context orchestrator, index tools) so that output handling,
 * attachment filtering, hooks, indexes and CLI branches of Teammate are covered
 * without any network access.
 */
public class TeammateRunJobFlowCoverageTest {

    /**
     * Test subclass that overrides the protected js() method to inject scripted
     * executors for specific JS action codes (pre-action, timer action, ...).
     */
    private static class TeammateWithJsSpy extends Teammate {
        final Map<String, JavaScriptExecutor> scriptedExecutors = new HashMap<>();
        final List<String> jsCalls = new ArrayList<>();

        @Override
        protected JavaScriptExecutor js(String jsCode) {
            jsCalls.add(jsCode);
            JavaScriptExecutor executor = scriptedExecutors.get(jsCode);
            return executor != null ? executor : super.js(jsCode);
        }
    }

    @Mock
    private AI ai;

    @Mock
    private GenericRequestAgent genericRequestAgent;

    @Mock
    private ContextOrchestrator contextOrchestrator;

    @Mock
    private UriToObjectFactory uriToObjectFactory;

    @Mock
    private Confluence confluence;

    @Mock
    private MermaidIndexTools mermaidIndexTools;

    @Mock
    private SourceCode sourceCode;

    @Mock
    private ITicket ticket;

    @TempDir
    Path tempDir;

    private AutoCloseable mocks;
    private TrackerClient<ITicket> trackerClient;
    private TeammateWithJsSpy teammate;
    private Teammate.TeammateParams params;
    private RequestDecompositionAgent.Result agentParams;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        trackerClient = mock(TrackerClient.class);

        teammate = new TeammateWithJsSpy();
        teammate.trackerClient = trackerClient;
        teammate.ai = ai;
        teammate.genericRequestAgent = genericRequestAgent;
        teammate.contextOrchestrator = contextOrchestrator;
        teammate.uriToObjectFactory = uriToObjectFactory;
        teammate.confluence = confluence;
        teammate.mermaidIndexTools = mermaidIndexTools;
        teammate.instructionProcessor = new InstructionProcessor(null, tempDir.toString());
        teammate.agentParamsFileWriter = new AgentParamsFileWriter(teammate.instructionProcessor);

        agentParams = new RequestDecompositionAgent.Result(
                "Test role", "Test request",
                new String[]{"Test question"}, new String[]{"Test task"},
                new String[]{"Test instructions"}, "Test known info",
                "Test formatting", "Test fewshots");

        params = new Teammate.TeammateParams();
        params.setAgentParams(agentParams);
        params.setInputJql("key = TEST-1");
        params.setOutputType(Params.OutputType.none);

        when(ticket.getKey()).thenReturn("TEST-1");
        when(ticket.getTicketKey()).thenReturn("TEST-1");
        when(ticket.toText()).thenReturn("Mock ticket text");
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());

        when(trackerClient.getTextFieldsOnly(any())).thenReturn("Test fields");
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});
        when(trackerClient.getComments(anyString(), any())).thenReturn(Collections.emptyList());
        when(trackerClient.tag(anyString())).thenAnswer(inv -> "[~" + inv.getArgument(0) + "]");
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0, JiraClient.Performer.class);
            try {
                performer.perform(ticket);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());

        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());
        when(genericRequestAgent.run(any())).thenReturn("AI response");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    // ---- output handling: field ----

    @Test
    void testFieldOutputAppendOperation() throws Exception {
        params.setOutputType(Params.OutputType.field);
        params.setFieldName("Description");
        params.setOperationType(Params.OperationType.Append);
        params.setInitiator("user-1");
        params.setSystemRequestCommentAlias("SYS-1");

        when(trackerClient.resolveFieldName(anyString(), anyString())).thenReturn("description");
        when(ticket.getFieldValueAsString("description")).thenReturn("Existing value");

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("TEST-1", results.get(0).getKey());
        assertEquals("AI response", results.get(0).getResult());

        ArgumentCaptor<TrackerClient.FieldsInitializer> captor =
                ArgumentCaptor.forClass(TrackerClient.FieldsInitializer.class);
        verify(trackerClient).updateTicket(eq("TEST-1"), captor.capture());
        TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
        captor.getValue().init(fields);
        verify(fields).set("description", "Existing value\n\nAI response");

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("TEST-1"), commentCaptor.capture());
        assertTrue(commentCaptor.getValue().contains("System Request: SYS-1"));
        assertTrue(commentCaptor.getValue().contains("Description"));
    }

    @Test
    void testFieldOutputReplaceOperation() throws Exception {
        params.setOutputType(Params.OutputType.field);
        params.setFieldName("Description");
        params.setOperationType(Params.OperationType.Replace);

        when(trackerClient.resolveFieldName(anyString(), anyString())).thenReturn("description");
        when(ticket.getFieldValueAsString("description")).thenReturn("Old value");

        teammate.runJobImpl(params);

        ArgumentCaptor<TrackerClient.FieldsInitializer> captor =
                ArgumentCaptor.forClass(TrackerClient.FieldsInitializer.class);
        verify(trackerClient).updateTicket(eq("TEST-1"), captor.capture());
        TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
        captor.getValue().init(fields);
        verify(fields).set("description", "AI response");

        // no initiator -> no notification comment
        verify(trackerClient, never()).postComment(anyString(), anyString());
    }

    @Test
    void testFieldOutputAppendWithEmptyCurrentValue() throws Exception {
        params.setOutputType(Params.OutputType.field);
        params.setFieldName("Description");
        params.setOperationType(Params.OperationType.Append);

        when(trackerClient.resolveFieldName(anyString(), anyString())).thenReturn("description");
        when(ticket.getFieldValueAsString("description")).thenReturn("   ");

        teammate.runJobImpl(params);

        ArgumentCaptor<TrackerClient.FieldsInitializer> captor =
                ArgumentCaptor.forClass(TrackerClient.FieldsInitializer.class);
        verify(trackerClient).updateTicket(eq("TEST-1"), captor.capture());
        TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
        captor.getValue().init(fields);
        verify(fields).set("description", "AI response");
    }

    // ---- output handling: comment / none ----

    @Test
    void testCommentOutputWithSystemRequestAlias() throws Exception {
        params.setOutputType(Params.OutputType.comment);
        params.setInitiator("user-1");
        params.setSystemRequestCommentAlias("SYS-1");

        teammate.runJobImpl(params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postCommentIfNotExists(eq("TEST-1"), commentCaptor.capture());
        String comment = commentCaptor.getValue();
        assertTrue(comment.contains("System Request: SYS-1"));
        assertTrue(comment.contains("AI Response is: \nAI response"));
        assertTrue(comment.contains("[~user-1]"));
    }

    @Test
    void testCommentOutputWithoutAlias() throws Exception {
        params.setOutputType(Params.OutputType.comment);
        params.setInitiator("user-1");

        teammate.runJobImpl(params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postCommentIfNotExists(eq("TEST-1"), commentCaptor.capture());
        assertEquals("[~user-1], \n\nAI Response is: \nAI response", commentCaptor.getValue());
    }

    @Test
    void testOutputTypeNoneSkipsPublishing() throws Exception {
        params.setOutputType(Params.OutputType.none);

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        verify(trackerClient, never()).updateTicket(anyString(), any());
        verify(trackerClient, never()).postCommentIfNotExists(anyString(), anyString());
        verify(trackerClient, never()).postComment(anyString(), anyString());
    }

    // ---- skipAIProcessing branches ----

    @Test
    void testSkipAiProcessingStrictModePostsErrorComment() throws Exception {
        params.setSkipAIProcessing(true);
        params.setOutputType(Params.OutputType.comment);
        params.setInitiator("user-1");
        // requireCliOutputFile defaults to true (strict mode), no CLI commands -> no output file

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("No CLI commands executed or results available.", results.get(0).getResult());

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("TEST-1"), commentCaptor.capture());
        String comment = commentCaptor.getValue();
        assertTrue(comment.contains("⚠️ CLI command execution issue"));
        assertTrue(comment.contains("No CLI commands executed or results available."));
        assertTrue(comment.contains("[~user-1]"));

        // field update / regular comment must be skipped in strict mode
        verify(trackerClient, never()).updateTicket(anyString(), any());
        verify(trackerClient, never()).postCommentIfNotExists(anyString(), anyString());
        verify(genericRequestAgent, never()).run(any());
    }

    @Test
    void testSkipAiProcessingStrictModeErrorCommentWithoutInitiator() throws Exception {
        params.setSkipAIProcessing(true);
        params.setOutputType(Params.OutputType.comment);

        teammate.runJobImpl(params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("TEST-1"), commentCaptor.capture());
        assertTrue(commentCaptor.getValue().startsWith("⚠️ CLI command execution issue"));
    }

    @Test
    void testSkipAiProcessingPermissiveModeUsesFallback() throws Exception {
        params.setSkipAIProcessing(true);
        params.setRequireCliOutputFile(false);
        params.setOutputType(Params.OutputType.field);
        params.setFieldName("Description");
        params.setOperationType(Params.OperationType.Replace);

        when(trackerClient.resolveFieldName(anyString(), anyString())).thenReturn("description");
        when(ticket.getFieldValueAsString("description")).thenReturn("Old");

        teammate.runJobImpl(params);

        ArgumentCaptor<TrackerClient.FieldsInitializer> captor =
                ArgumentCaptor.forClass(TrackerClient.FieldsInitializer.class);
        verify(trackerClient).updateTicket(eq("TEST-1"), captor.capture());
        TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
        captor.getValue().init(fields);
        verify(fields).set("description", "No CLI commands executed or results available.");
        verify(genericRequestAgent, never()).run(any());
    }

    @Test
    void testCompletionCommentFailureIsSwallowed() throws Exception {
        params.setOutputType(Params.OutputType.comment);
        params.setCiRunUrl("https://ci.example.com/run/7");

        // first postComment (processing started) succeeds, completion comment fails
        doNothing().doThrow(new IOException("completion comment failed"))
                .when(trackerClient).postComment(anyString(), anyString());

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        verify(trackerClient, times(2)).postComment(eq("TEST-1"), anyString());
    }

    // ---- pre JS action ----

    @Test
    void testPreJSActionReturningFalseSkipsTicket() throws Exception {
        JavaScriptExecutor falseExecutor = mock(JavaScriptExecutor.class);
        when(falseExecutor.mcp(any(), any(), any(), any())).thenReturn(falseExecutor);
        when(falseExecutor.withJobContext(any(), any(), any())).thenReturn(falseExecutor);
        when(falseExecutor.with(anyString(), any())).thenReturn(falseExecutor);
        when(falseExecutor.execute()).thenReturn(false);
        teammate.scriptedExecutors.put("agents/js/pre.js", falseExecutor);

        params.setPreJSAction("agents/js/pre.js");

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("Skipped by pre-action", results.get(0).getResult());
        verify(genericRequestAgent, never()).run(any());
        verify(trackerClient, never()).updateTicket(anyString(), any());
    }

    // ---- hooks as context ----

    @Test
    void testHooksAsContextAppendedToKnownInfo() throws Exception {
        params.setHooksAsContext(new String[]{"hook-one"});
        teammate.sourceCodes = new ArrayList<>(List.of(sourceCode));
        when(sourceCode.callHookAndWaitResponse(eq("hook-one"), anyString())).thenReturn("HOOK RESPONSE");

        teammate.runJobImpl(params);

        String knownInfo = agentParams.getKnownInfo();
        assertTrue(knownInfo.contains("Additional Context:"));
        assertTrue(knownInfo.contains("Tools Information (hook-one):"));
        assertTrue(knownInfo.contains("HOOK RESPONSE"));
    }

    @Test
    void testHookFailureDoesNotFailJob() throws Exception {
        params.setHooksAsContext(new String[]{"broken-hook"});
        teammate.sourceCodes = new ArrayList<>(List.of(sourceCode));
        when(sourceCode.callHookAndWaitResponse(anyString(), anyString()))
                .thenThrow(new RuntimeException("hook exploded"));

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        assertFalse(agentParams.getKnownInfo().contains("Additional Context:"));
    }

    // ---- attachment filtering ----

    @Test
    void testSkipVideoAttachmentsFiltersVideoFiles() throws Exception {
        params.setSkipVideoAttachments(true);

        IAttachment video = mock(IAttachment.class);
        when(video.getName()).thenReturn("demo.mp4");
        IAttachment doc = mock(IAttachment.class);
        when(doc.getName()).thenReturn("doc.txt");
        List<IAttachment> attachments = new ArrayList<>();
        attachments.add(video);
        attachments.add(null); // null entries must be kept (not crash the filter)
        attachments.add(doc);
        doReturn(attachments).when(ticket).getAttachments();

        teammate.runJobImpl(params);

        List<?> captured = captureAttachmentsArgument();
        assertNotNull(captured);
        assertEquals(2, captured.size());
        assertFalse(captured.contains(video));
        assertTrue(captured.contains(doc));
    }

    @Test
    void testSkipAllAttachmentsPassesNullToOrchestrator() throws Exception {
        params.setSkipAllAttachments(true);

        IAttachment doc = mock(IAttachment.class);
        when(doc.getName()).thenReturn("doc.txt");
        doReturn(new ArrayList<>(List.of(doc))).when(ticket).getAttachments();

        teammate.runJobImpl(params);

        assertNull(captureAttachmentsArgument());
    }

    @SuppressWarnings("unchecked")
    private List<?> captureAttachmentsArgument() throws Exception {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(contextOrchestrator, atLeastOnce())
                .processUrisInContent(captor.capture(), anyList(), eq(1));
        for (Object arg : captor.getAllValues()) {
            if (arg == null || arg instanceof List) {
                return (List<?>) arg;
            }
        }
        fail("Attachments argument was not passed to contextOrchestrator");
        return null;
    }

    // ---- attachResponseAsFile ----

    @Test
    void testAttachResponseAsFileAttachesToTicket() throws Exception {
        params.setAttachResponseAsFile(true);
        when(trackerClient.performTicket(eq("TEST-1"), any())).thenReturn(ticket);

        teammate.runJobImpl(params);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).attachFileToTicket(
                eq("TEST-1"), nameCaptor.capture(), eq("text/plain"), any());
        assertTrue(nameCaptor.getValue().endsWith("_final_answer.txt"));
    }

    // ---- indexes ----

    @Test
    void testIndexWithSkipAiProcessingAttachesIndexData() throws Exception {
        params.setSkipAIProcessing(true);
        params.setRequireCliOutputFile(false);

        Teammate.IndexConfig indexConfig = new Teammate.IndexConfig();
        indexConfig.setIntegration("mermaid");
        indexConfig.setStoragePath("/tmp/diagrams");
        params.setIndexes(new Teammate.IndexConfig[]{indexConfig});

        ToText indexEntry = () -> "diagram content";
        when(mermaidIndexTools.read("mermaid", "/tmp/diagrams"))
                .thenReturn(new ArrayList<>(List.of(indexEntry)));
        when(trackerClient.performTicket(eq("TEST-1"), any())).thenReturn(ticket);

        teammate.runJobImpl(params);

        assertTrue(agentParams.getKnownInfo().contains("Index Data (mermaid):"));
        assertTrue(agentParams.getKnownInfo().contains("diagram content"));

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).attachFileToTicket(
                eq("TEST-1"), nameCaptor.capture(), eq("text/plain"), any());
        assertTrue(nameCaptor.getValue().endsWith("_index_mermaid.txt"));
    }

    @Test
    void testIndexWithAiProcessingFeedsChunksToAgent() throws Exception {
        Teammate.IndexConfig indexConfig = new Teammate.IndexConfig();
        indexConfig.setIntegration("mermaid");
        indexConfig.setStoragePath("/tmp/diagrams");
        params.setIndexes(new Teammate.IndexConfig[]{indexConfig});

        ToText indexEntry = () -> "diagram content for ai";
        when(mermaidIndexTools.read("mermaid", "/tmp/diagrams"))
                .thenReturn(new ArrayList<>(List.of(indexEntry)));
        // summarize() must return a mutable list because index chunks are appended to it
        when(contextOrchestrator.summarize()).thenAnswer(inv -> new ArrayList<>());

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        verify(genericRequestAgent).run(any());
        // index data goes through chunking, not through knownInfo, when AI processing is on
        assertFalse(agentParams.getKnownInfo().contains("Index Data ("));
        verify(trackerClient, never()).attachFileToTicket(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testIndexToolExceptionIsLoggedAndJobContinues() throws Exception {
        Teammate.IndexConfig indexConfig = new Teammate.IndexConfig();
        indexConfig.setIntegration("mermaid");
        indexConfig.setStoragePath("/tmp/broken");
        params.setIndexes(new Teammate.IndexConfig[]{indexConfig});

        when(mermaidIndexTools.read(anyString(), anyString()))
                .thenThrow(new IOException("index unavailable"));

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
    }

    // ---- uri processing sources failure ----

    @Test
    void testUriProcessingSourcesIOExceptionIsWrapped() throws Exception {
        when(uriToObjectFactory.createUriProcessingSources())
                .thenThrow(new IOException("cannot build sources"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> teammate.runJobImpl(params));

        assertEquals("Failed to create URI processing sources", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    // ---- CI run tracing comments ----

    @Test
    void testCiRunUrlPostsProcessingStartedAndCompletionComments() throws Exception {
        params.setOutputType(Params.OutputType.comment);
        params.setCiRunUrl("https://ci.example.com/run/7");
        params.setInitiator("user-1");

        teammate.runJobImpl(params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient, times(2)).postComment(eq("TEST-1"), commentCaptor.capture());
        List<String> comments = commentCaptor.getAllValues();
        assertTrue(comments.get(0).contains("Processing started. CI Run: https://ci.example.com/run/7"));
        assertTrue(comments.get(1).contains("Teammate run completed. CI Run: https://ci.example.com/run/7"));
    }

    @Test
    void testProcessingStartedCommentFailureDoesNotStopJob() throws Exception {
        params.setOutputType(Params.OutputType.comment);
        params.setCiRunUrl("https://ci.example.com/run/7");

        doThrow(new IOException("comment failed")).doNothing()
                .when(trackerClient).postComment(anyString(), anyString());

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        // first postComment (processing started) failed, completion comment still attempted
        verify(trackerClient, times(2)).postComment(eq("TEST-1"), anyString());
    }

    // ---- agent params handling ----

    @Test
    void testAdditionalInstructionsMergedIntoInstructions() throws Exception {
        params.setAdditionalInstructions(new String[]{"extra instruction"});

        teammate.runJobImpl(params);

        String[] instructions = agentParams.getInstructions();
        assertEquals(2, instructions.length);
        assertEquals("Test instructions", instructions[0]);
        assertEquals("extra instruction", instructions[1]);
    }

    @Test
    void testDefaultAgentParamsCreatedWhenAgentParamsNull() throws Exception {
        params.setAgentParams(null);

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        verify(genericRequestAgent).run(any());
    }

    // ---- CLI commands path (temp user.dir, mocked command line) ----

    @Test
    void testCliCommandsPathWithParentConfluenceScan() throws Exception {
        Ticket jiraTicket = mock(Ticket.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getTicketKey()).thenReturn("TEST-1");
        when(jiraTicket.toText()).thenReturn("Jira ticket text");
        when(jiraTicket.getAttachments()).thenReturn(Collections.emptyList());

        Fields jiraFields = mock(Fields.class);
        Ticket parent = mock(Ticket.class);
        when(parent.getKey()).thenReturn("PARENT-1");
        when(jiraFields.getParent()).thenReturn(parent);
        when(jiraTicket.getFields()).thenReturn(jiraFields);

        ITicket parentTicket = mock(ITicket.class);
        when(trackerClient.performTicket(eq("PARENT-1"), any())).thenReturn(parentTicket);
        when(trackerClient.getTextFieldsOnly(parentTicket)).thenReturn("parent confluence text");

        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0, JiraClient.Performer.class);
            try {
                performer.perform(jiraTicket);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());

        params.setCliCommands(new String[]{"echo ok"});
        params.setCliPrompt("do the thing");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);

                assertEquals(1, results.size());
                mocked.verify(() -> CommandLineUtils.runCommand(
                        argThat(cmd -> cmd.startsWith("echo ok")), any(), any(), any()));
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        // parent ticket text fields must have been fetched for the Confluence scan
        verify(trackerClient).performTicket(eq("PARENT-1"), any());
        verify(trackerClient).getTextFieldsOnly(parentTicket);
        // CLI responses are appended to knownInfo
        assertTrue(agentParams.getKnownInfo().contains("CLI Execution Results:"));
        // input folder cleaned up (cleanupInputFolder defaults to true); note that
        // CliExecutionHelper resolves the relative input/ folder against the process cwd
        assertFalse(Files.exists(java.nio.file.Paths.get("input", "TEST-1")));
    }

    @Test
    void testParentTicketFetchFailureIsSkippedDuringConfluenceScan() throws Exception {
        Ticket jiraTicket = mock(Ticket.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getTicketKey()).thenReturn("TEST-1");
        when(jiraTicket.toText()).thenReturn("Jira ticket text");
        when(jiraTicket.getAttachments()).thenReturn(Collections.emptyList());

        Fields jiraFields = mock(Fields.class);
        Ticket parent = mock(Ticket.class);
        when(parent.getKey()).thenReturn("PARENT-1");
        when(jiraFields.getParent()).thenReturn(parent);
        when(jiraTicket.getFields()).thenReturn(jiraFields);

        when(trackerClient.performTicket(eq("PARENT-1"), any()))
                .thenThrow(new IOException("parent fetch failed"));

        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0, JiraClient.Performer.class);
            try {
                performer.perform(jiraTicket);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());

        params.setCliCommands(new String[]{"echo ok"});

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);
                assertEquals(1, results.size());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testParentConfluenceScanSkippedWhenDisabled() throws Exception {
        Ticket jiraTicket = mock(Ticket.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getTicketKey()).thenReturn("TEST-1");
        when(jiraTicket.toText()).thenReturn("Jira ticket text");
        when(jiraTicket.getAttachments()).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0, JiraClient.Performer.class);
            try {
                performer.perform(jiraTicket);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());

        params.setCliCommands(new String[]{"echo ok"});
        params.setIncludeParentConfluence(false);
        params.setCleanupInputFolder(false);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                teammate.runJobImpl(params);
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        // parent must never be fetched when includeParentConfluence=false
        verify(trackerClient, never()).performTicket(eq("PARENT-1"), any());
        // cleanupInputFolder=false keeps the input folder for inspection; note that
        // CliExecutionHelper resolves the relative input/ folder against the process cwd
        Path inputFolder = java.nio.file.Paths.get("input", "TEST-1");
        try {
            assertTrue(Files.exists(inputFolder));
            assertTrue(Files.exists(inputFolder.resolve("request.md")));
        } finally {
            // remove the folder this test created in the module working directory
            deleteRecursively(inputFolder);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
    }

    @Test
    void testCliExecutionExceptionCreatesErrorResultAndJobContinues() throws Exception {
        params.setCliCommands(new String[]{"failing-command"});

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenThrow(new RuntimeException("boom"));
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);

                assertEquals(1, results.size());
                verify(genericRequestAgent).run(any());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testTimerJSActionConfiguredWithCliCommands() throws Exception {
        JavaScriptExecutor timerExecutor = mock(JavaScriptExecutor.class);
        when(timerExecutor.mcp(any(), any(), any(), any())).thenReturn(timerExecutor);
        when(timerExecutor.withJobContext(any(), any(), any())).thenReturn(timerExecutor);
        when(timerExecutor.with(anyString(), any())).thenReturn(timerExecutor);
        when(timerExecutor.execute()).thenReturn(null);
        teammate.scriptedExecutors.put("agents/js/timer.js", timerExecutor);

        params.setCliCommands(new String[]{"echo ok"});
        params.setTimerJSAction("agents/js/timer.js");
        params.setTimerIntervalSeconds(1);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenAnswer(inv -> {
                            Thread.sleep(1200);
                            return "ok\nExit Code: 0";
                        });
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);
                assertEquals(1, results.size());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        assertTrue(teammate.jsCalls.contains("agents/js/timer.js"),
                "timerJSAction must be executed at least once during a long CLI command");
    }

    // ---- additional branch coverage ----

    @Test
    void testWriteAgentParamsToFilesFalseKeepsCombinedRequest() throws Exception {
        params.setCliCommands(new String[]{"echo ok"});
        params.setWriteAgentParamsToFiles(false);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);
                assertEquals(1, results.size());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testCiRunUrlWithCommentsDisabledDoesNotPost() throws Exception {
        params.setOutputType(Params.OutputType.none);
        params.setCiRunUrl("https://ci.example.com/run/9");

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        verify(trackerClient, never()).postComment(anyString(), anyString());
    }

    @Test
    void testTrackerSpecificCliPromptsMergedInFlow() throws Exception {
        params.setCliCommands(new String[]{"echo ok"});
        params.setCliPrompts(new String[]{"base prompt"});
        // configuration is null -> default tracker type "ado" is used for merging
        params.setCliPromptsByTracker(Map.of("ado", new String[]{"tracker prompt"}));

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("ok\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);
                assertEquals(1, results.size());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testStrictModeWithCliCommandsButNoOutputFile() throws Exception {
        params.setSkipAIProcessing(true);
        // requireCliOutputFile defaults to true -> strict mode
        params.setOutputType(Params.OutputType.comment);
        params.setCliCommands(new String[]{"echo ok"});

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("command output\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);

                assertEquals(1, results.size());
                assertTrue(results.get(0).getResult()
                        .contains("CLI command executed but did not produce output file"));

                ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
                verify(trackerClient).postComment(eq("TEST-1"), commentCaptor.capture());
                assertTrue(commentCaptor.getValue().contains("⚠️ CLI command execution issue"));
                verify(trackerClient, never()).postCommentIfNotExists(anyString(), anyString());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testPermissiveModeUsesCliCommandResponses() throws Exception {
        params.setSkipAIProcessing(true);
        params.setRequireCliOutputFile(false);
        params.setCliCommands(new String[]{"echo ok"});

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (MockedStatic<CommandLineUtils> mocked = mockStatic(CommandLineUtils.class)) {
                mocked.when(() -> CommandLineUtils.runCommand(anyString(), any(), any(), any()))
                        .thenReturn("command output\nExit Code: 0");
                mocked.when(() -> CommandLineUtils.loadEnvironmentFromFile(anyString()))
                        .thenReturn(Map.of());

                List<ResultItem> results = teammate.runJobImpl(params);

                assertEquals(1, results.size());
                assertTrue(results.get(0).getResult().contains("command output"));
                verify(genericRequestAgent, never()).run(any());
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testCliInputContextFailureCreatesErrorResult() throws Exception {
        // ticket without a key makes createInputContext throw IllegalArgumentException,
        // which Teammate converts into a "CLI Execution Error" result
        when(ticket.getTicketKey()).thenReturn(null);
        params.setCliCommands(new String[]{"echo ok"});

        List<ResultItem> results = teammate.runJobImpl(params);

        // the CLI failure is converted into an error result and the job continues with AI
        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        verify(genericRequestAgent).run(any());
    }

    @Test
    void testPostJSActionErrorIsRethrown() {
        TeammateWithJsSpy throwingTeammate = new TeammateWithJsSpy() {
            @Override
            protected JavaScriptExecutor js(String jsCode) {
                if ("agents/js/post.js".equals(jsCode)) {
                    throw new RuntimeException("post action exploded");
                }
                return super.js(jsCode);
            }
        };
        throwingTeammate.trackerClient = trackerClient;
        throwingTeammate.ai = ai;
        throwingTeammate.genericRequestAgent = genericRequestAgent;
        throwingTeammate.contextOrchestrator = contextOrchestrator;
        throwingTeammate.uriToObjectFactory = uriToObjectFactory;
        throwingTeammate.confluence = confluence;
        throwingTeammate.mermaidIndexTools = mermaidIndexTools;
        throwingTeammate.instructionProcessor = teammate.instructionProcessor;
        throwingTeammate.agentParamsFileWriter = teammate.agentParamsFileWriter;

        params.setPostJSAction("agents/js/post.js");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> throwingTeammate.runJobImpl(params));
        assertEquals("post action exploded", exception.getCause().getMessage());
    }

    @Test
    void testNullIndexConfigInArrayIsSkipped() throws Exception {
        params.setIndexes(new Teammate.IndexConfig[]{null});

        List<ResultItem> results = teammate.runJobImpl(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        verify(mermaidIndexTools, never()).read(anyString(), anyString());
    }

    // ---- params defaults / value object coverage ----

    @Test
    void testTeammateParamsDefaultsAndAccessors() {
        Teammate.TeammateParams fresh = new Teammate.TeammateParams();

        assertTrue(fresh.isRequireCliOutputFile());
        assertTrue(fresh.isCleanupInputFolder());
        assertFalse(fresh.isSkipAIProcessing());
        assertFalse(fresh.isSkipVideoAttachments());
        assertFalse(fresh.isSkipAllAttachments());
        assertTrue(fresh.isWriteAgentParamsToFiles());
        assertTrue(fresh.isIgnoreClonedByRelationship());
        assertTrue(fresh.isAutoConvertionToMarkdown());
        assertEquals(60, fresh.getTimerIntervalSeconds());
        assertEquals(1, fresh.getConfluenceDepth());
        assertTrue(fresh.isConfluenceAttachments());
        assertTrue(fresh.isIncludeParentConfluence());
        assertNull(fresh.getHooksAsContext());
        assertNull(fresh.getCliCommands());
        assertNull(fresh.getCliPrompt());
        assertNull(fresh.getCliPrompts());
        assertNull(fresh.getCliPromptsByTracker());
        assertNull(fresh.getSystemRequestCommentAlias());
        assertNull(fresh.getPreCliJSAction());
        assertNull(fresh.getAdditionalInstructions());
        assertNull(fresh.getTimerJSAction());
        assertNull(fresh.getIndexes());
        assertNull(fresh.getExcludedEnvVariables());
        assertNull(fresh.getExcludedEnvRegexes());
        assertEquals("systemRequestCommentAlias", Teammate.TeammateParams.SYSTEM_REQUEST_COMMENT_ALIAS);

        fresh.setHooksAsContext(new String[]{"h"});
        fresh.setCliCommands(new String[]{"c"});
        fresh.setCliPrompt("p");
        fresh.setCliPrompts(new String[]{"cp"});
        fresh.setCliPromptsByTracker(Map.of("jira", new String[]{"jp"}));
        fresh.setSystemRequestCommentAlias("alias");
        fresh.setPreCliJSAction("pre.js");
        fresh.setAdditionalInstructions(new String[]{"ai"});
        fresh.setTimerJSAction("timer.js");
        fresh.setTimerIntervalSeconds(30);
        fresh.setConfluenceDepth(2);
        fresh.setConfluenceAttachments(false);
        fresh.setIncludeParentConfluence(false);
        fresh.setSkipAIProcessing(true);
        fresh.setRequireCliOutputFile(false);
        fresh.setCleanupInputFolder(false);
        fresh.setSkipVideoAttachments(true);
        fresh.setSkipAllAttachments(true);
        fresh.setWriteAgentParamsToFiles(false);
        fresh.setIgnoreClonedByRelationship(false);
        fresh.setAutoConvertionToMarkdown(false);
        fresh.setExcludedEnvVariables(new String[]{"SECRET"});
        fresh.setExcludedEnvRegexes(new String[]{".*_TOKEN"});

        Teammate.IndexConfig indexConfig = new Teammate.IndexConfig();
        indexConfig.setIntegration("mermaid");
        indexConfig.setStoragePath("/tmp/x");
        fresh.setIndexes(new Teammate.IndexConfig[]{indexConfig});

        assertArrayEquals(new String[]{"h"}, fresh.getHooksAsContext());
        assertArrayEquals(new String[]{"c"}, fresh.getCliCommands());
        assertEquals("p", fresh.getCliPrompt());
        assertArrayEquals(new String[]{"cp"}, fresh.getCliPrompts());
        assertArrayEquals(new String[]{"jp"}, fresh.getCliPromptsByTracker().get("jira"));
        assertEquals("alias", fresh.getSystemRequestCommentAlias());
        assertEquals("pre.js", fresh.getPreCliJSAction());
        assertArrayEquals(new String[]{"ai"}, fresh.getAdditionalInstructions());
        assertEquals("timer.js", fresh.getTimerJSAction());
        assertEquals(30, fresh.getTimerIntervalSeconds());
        assertEquals(2, fresh.getConfluenceDepth());
        assertFalse(fresh.isConfluenceAttachments());
        assertFalse(fresh.isIncludeParentConfluence());
        assertTrue(fresh.isSkipAIProcessing());
        assertFalse(fresh.isRequireCliOutputFile());
        assertFalse(fresh.isCleanupInputFolder());
        assertTrue(fresh.isSkipVideoAttachments());
        assertTrue(fresh.isSkipAllAttachments());
        assertFalse(fresh.isWriteAgentParamsToFiles());
        assertFalse(fresh.isIgnoreClonedByRelationship());
        assertFalse(fresh.isAutoConvertionToMarkdown());
        assertArrayEquals(new String[]{"SECRET"}, fresh.getExcludedEnvVariables());
        assertArrayEquals(new String[]{".*_TOKEN"}, fresh.getExcludedEnvRegexes());
        assertEquals("mermaid", fresh.getIndexes()[0].getIntegration());
        assertEquals("/tmp/x", fresh.getIndexes()[0].getStoragePath());
    }

    @Test
    void testDefaultConstructor() {
        Teammate defaultTeammate = new Teammate();
        assertNotNull(defaultTeammate);
    }
}
