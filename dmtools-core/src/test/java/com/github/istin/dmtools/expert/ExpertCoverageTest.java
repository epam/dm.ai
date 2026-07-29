// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.expert;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.KeywordGeneratorAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.ai.agent.SearchResultsAssessmentAgent;
import com.github.istin.dmtools.ai.agent.SnippetExtensionAgent;
import com.github.istin.dmtools.ai.agent.SummaryContextAgent;
import com.github.istin.dmtools.ai.agent.TeamAssistantAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.di.SourceCodeFactory;
import com.github.istin.dmtools.job.Params;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.search.AbstractSearchOrchestrator;
import com.github.istin.dmtools.search.ConfluenceSearchOrchestrator;
import com.github.istin.dmtools.search.SearchStats;
import com.github.istin.dmtools.search.TrackerSearchOrchestrator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExpertCoverageTest {

    private Expert expert;
    private JiraClient trackerClient;
    private Confluence confluence;
    private AI ai;
    private IPromptTemplateReader promptTemplateReader;
    private SourceCodeFactory sourceCodeFactory;
    private RequestDecompositionAgent requestDecompositionAgent;
    private TeamAssistantAgent teamAssistantAgent;
    private ApplicationConfiguration configuration;
    private ConfluenceSearchOrchestrator confluenceSearchOrchestrator;
    private TrackerSearchOrchestrator trackerSearchOrchestrator;
    private ContextOrchestrator contextOrchestrator;
    private UriToObjectFactory uriToObjectFactory;
    private KeywordGeneratorAgent keywordGeneratorAgent;
    private SnippetExtensionAgent snippetExtensionAgent;
    private SummaryContextAgent summaryContextAgent;
    private SearchResultsAssessmentAgent searchResultsAssessmentAgent;

    @Before
    public void setUp() throws Exception {
        expert = new Expert();
        trackerClient = mock(JiraClient.class);
        confluence = mock(Confluence.class);
        ai = mock(AI.class);
        promptTemplateReader = mock(IPromptTemplateReader.class);
        sourceCodeFactory = mock(SourceCodeFactory.class);
        requestDecompositionAgent = mock(RequestDecompositionAgent.class);
        teamAssistantAgent = mock(TeamAssistantAgent.class);
        configuration = mock(ApplicationConfiguration.class);
        confluenceSearchOrchestrator = mock(ConfluenceSearchOrchestrator.class);
        trackerSearchOrchestrator = mock(TrackerSearchOrchestrator.class);
        contextOrchestrator = mock(ContextOrchestrator.class);
        uriToObjectFactory = mock(UriToObjectFactory.class);
        keywordGeneratorAgent = mock(KeywordGeneratorAgent.class);
        snippetExtensionAgent = mock(SnippetExtensionAgent.class);
        summaryContextAgent = mock(SummaryContextAgent.class);
        searchResultsAssessmentAgent = mock(SearchResultsAssessmentAgent.class);

        setField(expert, "trackerClient", trackerClient);
        setField(expert, "confluence", confluence);
        setField(expert, "ai", ai);
        setField(expert, "promptTemplateReader", promptTemplateReader);
        setField(expert, "sourceCodeFactory", sourceCodeFactory);
        setField(expert, "requestDecompositionAgent", requestDecompositionAgent);
        setField(expert, "teamAssistantAgent", teamAssistantAgent);
        setField(expert, "configuration", configuration);
        setField(expert, "confluenceSearchOrchestrator", confluenceSearchOrchestrator);
        setField(expert, "trackerSearchOrchestrator", trackerSearchOrchestrator);
        setField(expert, "contextOrchestrator", contextOrchestrator);
        setField(expert, "uriToObjectFactory", uriToObjectFactory);
        setField(expert, "keywordGeneratorAgent", keywordGeneratorAgent);
        setField(expert, "snippetExtensionAgent", snippetExtensionAgent);
        setField(expert, "summaryContextAgent", summaryContextAgent);
        setField(expert, "searchResultsAssessmentAgent", searchResultsAssessmentAgent);

        when(uriToObjectFactory.createUriProcessingSources(nullable(SourceCodeConfig[].class)))
                .thenReturn(new ArrayList<>());
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary", "description"});
        when(trackerClient.getBasePath()).thenReturn("https://jira.example.com");
        when(trackerClient.tag(any())).thenReturn("[~initiator]");
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("text fields only");
        doReturn(new ArrayList<>()).when(trackerClient).getComments(anyString(), any());
        when(contextOrchestrator.summarize()).thenReturn(new ArrayList<>());
        when(requestDecompositionAgent.run(any())).thenReturn(decompositionResult());
        when(teamAssistantAgent.run(any())).thenReturn("AI response");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = Expert.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private RequestDecompositionAgent.Result decompositionResult() {
        return new RequestDecompositionAgent.Result(
                "role", "decomposed request", null, null, null, "known info", "rules", "shots");
    }

    private ExpertParams baseParams() {
        ExpertParams params = new ExpertParams();
        params.setInputJql("project = TEST");
        params.setRequest("User request");
        params.setInitiator("initiator@example.com");
        params.setOutputType(Params.OutputType.comment);
        return params;
    }

    private void stubTickets(ITicket... tickets) throws Exception {
        doAnswer(invocation -> {
            JiraClient.Performer performer = invocation.getArgument(0);
            for (ITicket ticket : tickets) {
                performer.perform(ticket);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), any(), any());
    }

    private ITicket ticket(String key) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn(key);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.toText()).thenReturn("Ticket text for " + key);
        doReturn(new ArrayList<>()).when(ticket).getAttachments();
        when(ticket.getFieldsAsJSON()).thenReturn(new JSONObject());
        return ticket;
    }

    private Ticket jiraTicketWithAttachments(List<IAttachment> attachments) {
        Ticket jiraTicket = mock(Ticket.class);
        doReturn(attachments).when(jiraTicket).getAttachments();
        return jiraTicket;
    }

    private Content confluenceContent(String storageValue) {
        JSONObject json = new JSONObject();
        json.put("id", "123456");
        json.put("title", "Wiki Page");
        json.put("body", new JSONObject().put("storage", new JSONObject()
                .put("value", storageValue)
                .put("representation", "storage")));
        json.put("_links", new JSONObject()
                .put("webui", "/pages/123456")
                .put("base", "https://confluence.example.com"));
        return new Content(json);
    }

    @Test
    public void testRunJob_CommentOutput_BasicFlow() throws Exception {
        stubTickets(ticket("TEST-1"));

        List<ResultItem> results = expert.runJob(baseParams());

        assertEquals(1, results.size());
        assertEquals("TEST-1", results.get(0).getKey());
        assertEquals("AI response", results.get(0).getResult());
        verify(trackerClient).postCommentIfNotExists(eq("TEST-1"), contains("AI response"));
        verify(contextOrchestrator).processFullContent(eq("TEST-1"), anyString(),
                any(), anyList(), eq(1));
        verify(contextOrchestrator, atLeastOnce()).processUrisInContent(any(), anyList(), eq(1));
    }

    @Test
    public void testRunJob_CommentOutput_WithSystemRequestAlias() throws Exception {
        stubTickets(ticket("TEST-1"));
        ExpertParams params = baseParams();
        params.setSystemRequestCommentAlias("Alias for system request");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postCommentIfNotExists(eq("TEST-1"), commentCaptor.capture());
        assertTrue(commentCaptor.getValue().contains("System Request: Alias for system request"));
    }

    @Test
    public void testRunJob_SystemRequestFromConfluenceUrl_HtmlContent() throws Exception {
        stubTickets(ticket("TEST-1"));
        when(confluence.contentByUrl("https://confluence.example.com/pages/1"))
                .thenReturn(confluenceContent("<p style=\"color:red\">System instructions</p>"));

        ExpertParams params = baseParams();
        params.setSystemRequest("https://confluence.example.com/pages/1");
        params.setTransformConfluencePagesToMarkdown(true);

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(confluence).contentByUrl("https://confluence.example.com/pages/1");
        ArgumentCaptor<RequestDecompositionAgent.Params> captor =
                ArgumentCaptor.forClass(RequestDecompositionAgent.Params.class);
        verify(requestDecompositionAgent).run(captor.capture());
        assertTrue(captor.getValue().getUserRequest().contains("System instructions"));
    }

    @Test
    public void testRunJob_SystemRequestFromConfluenceUrl_YamlContent() throws Exception {
        stubTickets(ticket("TEST-1"));
        String yamlMacro = "<ac:structured-macro ac:name=\"code\">"
                + "<ac:parameter ac:name=\"language\">yaml</ac:parameter>"
                + "<ac:plain-text-body><![CDATA[role: assistant]]></ac:plain-text-body>"
                + "</ac:structured-macro>";
        when(confluence.contentByUrl("https://confluence.example.com/pages/2"))
                .thenReturn(confluenceContent(yamlMacro));

        ExpertParams params = baseParams();
        params.setSystemRequest("https://confluence.example.com/pages/2");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        ArgumentCaptor<RequestDecompositionAgent.Params> captor =
                ArgumentCaptor.forClass(RequestDecompositionAgent.Params.class);
        verify(requestDecompositionAgent).run(captor.capture());
        assertTrue(captor.getValue().getUserRequest().contains("role: assistant"));
    }

    @Test
    public void testRunJob_ProjectContextFromConfluenceUrl() throws Exception {
        stubTickets(ticket("TEST-1"));
        when(confluence.contentByUrl("https://confluence.example.com/pages/3"))
                .thenReturn(confluenceContent("<p>Project context body</p>"));

        ExpertParams params = baseParams();
        params.setProjectContext("https://confluence.example.com/pages/3");
        params.setTransformConfluencePagesToMarkdown(true);

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        ArgumentCaptor<RequestDecompositionAgent.Params> captor =
                ArgumentCaptor.forClass(RequestDecompositionAgent.Params.class);
        verify(requestDecompositionAgent).run(captor.capture());
        assertTrue(captor.getValue().getRawData().contains("Project context body"));
    }

    @Test
    public void testRunJob_ConfluencePagesContext() throws Exception {
        stubTickets(ticket("TEST-1"));
        when(confluence.contentsByUrls(any())).thenReturn(
                Collections.singletonList(confluenceContent("<p>Confluence page content</p>")));

        ExpertParams params = baseParams();
        params.setConfluencePages("https://confluence.example.com/pages/4");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(confluence).contentsByUrls(any());
        verify(contextOrchestrator, atLeastOnce()).processFullContent(
                anyString(), anyString(), eq(confluence), isNull(), eq(0));
    }

    @Test
    public void testRunJob_FieldOutput_Append() throws Exception {
        ITicket ticket = ticket("TEST-1");
        when(ticket.getFields()).thenReturn(new Fields(
                new JSONObject().put("customfield_100", "previous value")));
        stubTickets(ticket);
        when(trackerClient.getFieldCustomCode("TEST", "AI Field")).thenReturn("customfield_100");
        doAnswer(invocation -> {
            TrackerClient.FieldsInitializer initializer = invocation.getArgument(1);
            TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
            initializer.init(fields);
            verify(fields).set(eq("customfield_100"), argThat(value ->
                    value.toString().startsWith("previous value")));
            return null;
        }).when(trackerClient).updateTicket(eq("TEST-1"), any(TrackerClient.FieldsInitializer.class));

        ExpertParams params = baseParams();
        params.setOutputType(Params.OutputType.field);
        params.setFieldName("AI Field");
        params.setOperationType(Params.OperationType.Append);
        params.setSystemRequestCommentAlias("Sys alias");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(trackerClient).updateTicket(eq("TEST-1"), any(TrackerClient.FieldsInitializer.class));
        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("TEST-1"), commentCaptor.capture());
        assertTrue(commentCaptor.getValue().contains("there is AI response in 'AI Field'"));
        assertTrue(commentCaptor.getValue().contains("System Request: Sys alias"));
    }

    @Test
    public void testRunJob_FieldOutput_Replace() throws Exception {
        ITicket ticket = ticket("TEST-1");
        when(ticket.getFields()).thenReturn(new Fields(
                new JSONObject().put("customfield_100", "previous value")));
        stubTickets(ticket);
        when(trackerClient.getFieldCustomCode("TEST", "AI Field")).thenReturn("customfield_100");
        doAnswer(invocation -> {
            TrackerClient.FieldsInitializer initializer = invocation.getArgument(1);
            TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
            initializer.init(fields);
            verify(fields).set(eq("customfield_100"), argThat(value ->
                    !value.toString().contains("previous value")));
            return null;
        }).when(trackerClient).updateTicket(eq("TEST-1"), any(TrackerClient.FieldsInitializer.class));

        ExpertParams params = baseParams();
        params.setOutputType(Params.OutputType.field);
        params.setFieldName("AI Field");
        params.setOperationType(Params.OperationType.Replace);

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("TEST-1"), commentCaptor.capture());
        assertFalse(commentCaptor.getValue().contains("System Request:"));
    }

    @Test
    public void testRunJob_PreActionReturnsFalse_SkipsTicket() throws Exception {
        stubTickets(ticket("TEST-1"));

        ExpertParams params = baseParams();
        params.setPreJSAction("function action(params) { return false; }");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        assertEquals("TEST-1", results.get(0).getKey());
        assertEquals("Skipped by pre-action", results.get(0).getResult());
        verify(teamAssistantAgent, never()).run(any());
        verify(trackerClient, never()).postCommentIfNotExists(anyString(), anyString());
    }

    @Test
    public void testRunJob_CiRunUrl_PostsProcessingStartedComment() throws Exception {
        stubTickets(ticket("TEST-1"));

        ExpertParams params = baseParams();
        params.setCiRunUrl("https://ci.example.com/runs/42");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(trackerClient).postComment(eq("TEST-1"),
                contains("Processing started. CI Run: https://ci.example.com/runs/42"));
    }

    @Test
    public void testRunJob_CiRunUrl_CommentPostingFails_Continues() throws Exception {
        stubTickets(ticket("TEST-1"));
        doThrow(new IOException("comment failed")).when(trackerClient)
                .postComment(eq("TEST-1"), contains("Processing started"));

        ExpertParams params = baseParams();
        params.setCiRunUrl("https://ci.example.com/runs/42");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        assertEquals("AI response", results.get(0).getResult());
        verify(trackerClient).postCommentIfNotExists(eq("TEST-1"), contains("AI response"));
    }

    @Test
    public void testRunJob_CiRunUrl_OutputTypeNone_NoProcessingComment() throws Exception {
        stubTickets(ticket("TEST-1"));

        ExpertParams params = baseParams();
        params.setCiRunUrl("https://ci.example.com/runs/42");
        params.setOutputType(Params.OutputType.none);

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(trackerClient, never()).postComment(anyString(), contains("Processing started"));
    }

    @Test
    public void testRunJob_UriProcessingSourcesIOException_ThrowsRuntime() throws Exception {
        when(uriToObjectFactory.createUriProcessingSources(nullable(SourceCodeConfig[].class)))
                .thenThrow(new IOException("source config broken"));

        try {
            expert.runJob(baseParams());
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Failed to create URI processing sources", e.getMessage());
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testRunJob_CodeAsSource_ExtendsContextWithCode() throws Exception {
        ITicket ticket = ticket("TEST-1");
        stubTickets(ticket);

        SourceCode sourceCode = mock(SourceCode.class);
        when(sourceCode.getListOfFiles(any(), any(), any())).thenReturn(new ArrayList<>());
        when(sourceCode.searchFiles(any(), any(), anyString(), anyInt())).thenReturn(new ArrayList<>());
        when(sourceCodeFactory.createSourceCodes(nullable(SourceCodeConfig[].class)))
                .thenReturn(Collections.singletonList(sourceCode));
        when(keywordGeneratorAgent.run(any())).thenReturn(new JSONArray().put("keyword1"));

        doReturn(jiraTicketWithAttachments(new ArrayList<>())).when(trackerClient)
                .performTicket(eq("TEST-1"), any(String[].class));

        ExpertParams params = baseParams();
        params.setCodeAsSource(true);
        params.setAttachResponseAsFile(true);
        params.setKeywordsBlacklist(null);

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(sourceCodeFactory).createSourceCodes(nullable(SourceCodeConfig[].class));
        verify(keywordGeneratorAgent, atLeastOnce()).run(any());
        // final answer attachment + stats attachments from saveAndAttachStats
        verify(trackerClient, atLeast(2)).attachFileToTicket(
                eq("TEST-1"), anyString(), anyString(), any(File.class));
        assertEquals(1, expert.listOfCodebaseSearchOrchestrator.size());
    }

    @Test
    public void testRunJob_ConfluenceAsSource_BlacklistFromUrl() throws Exception {
        ITicket ticket = ticket("TEST-1");
        stubTickets(ticket);
        when(confluence.contentByUrl("https://confluence.example.com/pages/blacklist"))
                .thenReturn(confluenceContent("blacklisted,keywords"));
        when(confluenceSearchOrchestrator.run(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(confluenceSearchOrchestrator.getSearchStats()).thenReturn(new SearchStats());
        doReturn(jiraTicketWithAttachments(new ArrayList<>())).when(trackerClient)
                .performTicket(eq("TEST-1"), any(String[].class));

        ExpertParams params = baseParams();
        params.setConfluenceAsSource(true);
        params.setAttachResponseAsFile(true);
        params.setKeywordsBlacklist("https://confluence.example.com/pages/blacklist");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(confluenceSearchOrchestrator).run(anyString(), eq("blacklisted,keywords"), anyInt(), anyInt());
        verify(trackerClient, atLeast(2)).attachFileToTicket(
                eq("TEST-1"), anyString(), anyString(), any(File.class));
    }

    @Test
    public void testRunJob_TrackerAsSource_PlainBlacklist() throws Exception {
        stubTickets(ticket("TEST-1"));
        when(trackerSearchOrchestrator.run(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        ExpertParams params = baseParams();
        params.setTrackerAsSource(true);
        params.setKeywordsBlacklist("kw1,kw2");

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        verify(trackerSearchOrchestrator).run(anyString(), eq("kw1,kw2"), anyInt(), anyInt());
        verify(trackerClient, never()).attachFileToTicket(
                anyString(), anyString(), anyString(), any(File.class));
    }

    @Test
    public void testRunJob_RequestDecompositionChunkProcessing_PassesChunks() throws Exception {
        stubTickets(ticket("TEST-1"));
        List<ChunkPreparation.Chunk> chunks = new ArrayList<>();
        chunks.add(new ChunkPreparation.Chunk("chunk text", null, 0));
        when(contextOrchestrator.summarize()).thenReturn(chunks);

        ExpertParams params = baseParams();
        params.setRequestDecompositionChunkProcessing(true);

        List<ResultItem> results = expert.runJob(params);

        assertEquals(1, results.size());
        ArgumentCaptor<RequestDecompositionAgent.Params> captor =
                ArgumentCaptor.forClass(RequestDecompositionAgent.Params.class);
        verify(requestDecompositionAgent).run(captor.capture());
        assertNotNull(captor.getValue().getChunks());
        assertEquals(1, captor.getValue().getChunks().size());
        assertEquals("chunk text", captor.getValue().getChunks().get(0).getText());
    }

    @Test
    public void testRunJob_NoTickets_ReturnsEmptyResults() throws Exception {
        stubTickets();

        List<ResultItem> results = expert.runJob(baseParams());

        assertTrue(results.isEmpty());
        verify(teamAssistantAgent, never()).run(any());
    }

    @Test
    public void testAttachResponse_GeneratesUniqueFileNameAndAttaches() throws Exception {
        IAttachment attachment = mock(IAttachment.class);
        when(attachment.getName()).thenReturn("TeamAssistantAgent_final.txt");
        List<IAttachment> attachments = new ArrayList<>();
        attachments.add(attachment);
        doReturn(jiraTicketWithAttachments(attachments)).when(trackerClient)
                .performTicket(eq("TEST-1"), any(String[].class));

        expert.attachResponse(teamAssistantAgent, "_final.txt", "response body",
                "TEST-1", "text/plain");

        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(trackerClient).attachFileToTicket(eq("TEST-1"), fileNameCaptor.capture(),
                eq("text/plain"), fileCaptor.capture());
        // name must differ from the existing attachment
        assertNotEquals("TeamAssistantAgent_final.txt", fileNameCaptor.getValue());
        assertTrue(fileNameCaptor.getValue().startsWith("TeamAssistantAgent"));
        // temp file must be cleaned up
        assertFalse(fileCaptor.getValue().exists());
    }

    @Test
    public void testSaveAndAttachStats_HandlesExceptionGracefully() throws Exception {
        AbstractSearchOrchestrator orchestrator = mock(AbstractSearchOrchestrator.class);
        // getSearchStats() returns null -> NPE inside, must be swallowed and logged
        expert.saveAndAttachStats("TEST-1", new ArrayList<>(), orchestrator);

        verify(trackerClient, never()).attachFileToTicket(
                anyString(), anyString(), anyString(), any(File.class));
    }

    @Test
    public void testSaveAndAttachStats_WithFilesInChunks() throws Exception {
        doReturn(jiraTicketWithAttachments(new ArrayList<>())).when(trackerClient)
                .performTicket(eq("TEST-1"), any(String[].class));
        AbstractSearchOrchestrator orchestrator = mock(AbstractSearchOrchestrator.class);
        when(orchestrator.getSearchStats()).thenReturn(new SearchStats());

        List<ChunkPreparation.Chunk> chunks = new ArrayList<>();
        List<File> files = new ArrayList<>();
        files.add(new File("SomeFile.java"));
        chunks.add(new ChunkPreparation.Chunk("chunk with files", files, 0));

        expert.saveAndAttachStats("TEST-1", chunks, orchestrator);

        verify(trackerClient, times(2)).attachFileToTicket(
                eq("TEST-1"), anyString(), anyString(), any(File.class));
    }
}
