// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import com.github.istin.dmtools.ai.agent.RelatedTestCasesAgent;
import com.github.istin.dmtools.ai.agent.TestCaseDeduplicationAgent;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.Priority;
import com.github.istin.dmtools.atlassian.jira.model.Relationship;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.job.JavaScriptExecutor;
import com.github.istin.dmtools.job.TrackerParams;
import com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestCasesGeneratorCoverageTest {

    /**
     * Generator subclass that intercepts JavaScript execution so no real JS engine runs.
     */
    static class TestableGenerator extends TestCasesGenerator {
        Object jsResult;
        boolean jsThrows;
        List<String> jsCodes = new ArrayList<>();

        @Override
        protected JavaScriptExecutor js(String jsCode) {
            jsCodes.add(jsCode);
            JavaScriptExecutor executor = mock(JavaScriptExecutor.class, RETURNS_SELF);
            try {
                if (jsThrows) {
                    when(executor.execute()).thenThrow(new RuntimeException("js failure"));
                } else {
                    when(executor.execute()).thenReturn(jsResult);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return executor;
        }
    }

    @SuppressWarnings("unchecked")
    private TestableGenerator newGenerator() {
        TestableGenerator gen = new TestableGenerator();
        gen.trackerClient = mock(TrackerClient.class);
        gen.confluence = mock(Confluence.class);
        gen.ai = mock(AI.class);
        gen.testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);
        gen.relatedTestCasesAgent = mock(RelatedTestCasesAgent.class);
        gen.relatedTestCaseAgent = mock(RelatedTestCaseAgent.class);
        gen.testCaseDeduplicationAgent = mock(TestCaseDeduplicationAgent.class);
        return gen;
    }

    @SuppressWarnings("unchecked")
    private TrackerClient<ITicket> tracker(TestableGenerator gen) {
        return (TrackerClient<ITicket>) gen.trackerClient;
    }

    private ITicket mockTicket(String key) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn(key);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.toText()).thenReturn("text of " + key);
        return ticket;
    }

    private TicketContext mockContext(ITicket ticket, String text) throws Exception {
        TicketContext ctx = mock(TicketContext.class);
        when(ctx.getTicket()).thenReturn(ticket);
        when(ctx.toText()).thenReturn(text);
        return ctx;
    }

    private TestCasesGeneratorParams baseParams() {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setFindRelated(false);
        params.setGenerateNew(false);
        params.setOutputType(TrackerParams.OutputType.none);
        return params;
    }

    // ---- runJob tests ----

    @Test
    @SuppressWarnings("unchecked")
    public void runJob_success_postsCommentAndReturnsResult() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        TestCasesGeneratorParams params = baseParams();
        params.setInputJql("project = STORY");
        params.setOutputType(TrackerParams.OutputType.comment);
        params.setInitiator("qa@example.com");

        ITicket story = mockTicket("STORY-1");
        when(story.toText()).thenReturn("plain story text without references");

        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0);
            performer.perform(story);
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), eq("project = STORY"), any(String[].class));
        when(trackerClient.searchAndPerform(any(), any(String[].class))).thenReturn(Collections.emptyList());
        when(trackerClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());
        when(trackerClient.tag(anyString())).thenReturn("");

        List<TestCasesGenerator.TestCasesResult> results = gen.runJob(params);

        assertEquals(1, results.size());
        assertEquals("STORY-1", results.get(0).getKey());
        verify(trackerClient).postCommentIfNotExists(eq("STORY-1"), contains("similar test cases are linked and new test cases are generated."));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void runJob_jqlModifierJsAction_appliesModifiedJql() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);
        gen.jsResult = "project = NEW";

        TestCasesGeneratorParams params = baseParams();
        params.setInputJql("project = STORY");
        params.setExistingTestCasesJql("project = OLD");
        params.setJqlModifierJSAction("modifier.js");

        ITicket story = mockTicket("STORY-1");
        when(story.toText()).thenReturn("plain story text");
        when(story.getTicketTitle()).thenReturn("Story title");
        when(story.getTicketDescription()).thenReturn("Story description");

        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0);
            performer.perform(story);
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), eq("project = STORY"), any(String[].class));
        when(trackerClient.searchAndPerform(any(), any(String[].class))).thenReturn(Collections.emptyList());
        when(trackerClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());

        gen.runJob(params);

        ArgumentCaptor<String> jqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).searchAndPerform(jqlCaptor.capture(), any(String[].class));
        assertEquals("project = NEW", jqlCaptor.getValue());
        assertTrue(gen.jsCodes.contains("modifier.js"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void runJob_errorDuringTicketProcessing_postsErrorCommentAndRethrows() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        TestCasesGeneratorParams params = baseParams();
        params.setInputJql("project = STORY");
        params.setOutputType(TrackerParams.OutputType.comment);
        params.setInitiator("qa@example.com");

        ITicket story = mockTicket("STORY-1");
        when(story.toText()).thenReturn("plain story text");

        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0);
            performer.perform(story);
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), eq("project = STORY"), any(String[].class));
        when(trackerClient.searchAndPerform(any(), any(String[].class))).thenThrow(new RuntimeException("boom"));
        when(trackerClient.tag(anyString())).thenReturn("qa-tag");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> gen.runJob(params));
        assertEquals("Test case generation failed for ticket STORY-1", thrown.getMessage());

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("STORY-1"), commentCaptor.capture());
        assertTrue(commentCaptor.getValue().startsWith("qa-tag, "));
        assertTrue(commentCaptor.getValue().contains("boom"));
        assertTrue(commentCaptor.getValue().contains("Stack trace:"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void runJob_errorCommentFails_stillRethrowsOriginalError() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        TestCasesGeneratorParams params = baseParams();
        params.setInputJql("project = STORY");
        params.setOutputType(TrackerParams.OutputType.comment);
        params.setInitiator("qa@example.com");

        ITicket story = mockTicket("STORY-1");
        when(story.toText()).thenReturn("plain story text");

        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});
        doAnswer(inv -> {
            JiraClient.Performer<ITicket> performer = inv.getArgument(0);
            performer.perform(story);
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), eq("project = STORY"), any(String[].class));
        when(trackerClient.searchAndPerform(any(), any(String[].class))).thenThrow(new RuntimeException("boom"));
        when(trackerClient.tag(anyString())).thenReturn("");
        doThrow(new IOException("comment failed")).when(trackerClient).postComment(anyString(), anyString());

        assertThrows(RuntimeException.class, () -> gen.runJob(params));
        verify(trackerClient).postComment(eq("STORY-1"), contains("boom"));
    }

    // ---- generateTestCases: related test cases comment (HTML) ----

    @Test
    @SuppressWarnings("unchecked")
    public void generateTestCases_postLinkedComment_htmlFormatIncludesSummary() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        ITicket tc1 = mockTicket("TC-1");
        when(tc1.getTicketTitle()).thenReturn("Login test");

        when(gen.relatedTestCasesAgent.run(any(), any())).thenReturn(new JSONArray("[\"TC-1\"]"));
        when(gen.relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "validates the same auth flow"));
        when(trackerClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());
        when(trackerClient.getTextType()).thenReturn(TrackerClient.TextType.HTML);

        ITicket mainTicket = mockTicket("STORY-1");
        TicketContext ctx = mockContext(mainTicket, "Story text");

        TestCasesGeneratorParams params = baseParams();
        params.setFindRelated(true);
        params.setLinkRelated(false);
        params.setPostLinkedTestCasesComment(true);

        gen.generateTestCases(ctx, "", List.of(tc1), params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("STORY-1"), commentCaptor.capture());
        String comment = commentCaptor.getValue();
        assertTrue(comment.startsWith("<b>Related test cases identified:</b><ul>"));
        assertTrue(comment.contains("<li><b>TC-1</b>: Login test"));
        assertTrue(comment.contains("validates the same auth flow"));
        assertTrue(comment.endsWith("</ul>"));
    }

    // ---- generateTestCases: creation output type ----

    @Test
    @SuppressWarnings("unchecked")
    public void generateTestCases_creationOutput_jiraFieldsAndLinking() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        TestCasesGeneratorParams params = baseParams();
        params.setGenerateNew(true);
        params.setOutputType(TrackerParams.OutputType.creation);
        params.setConvertToJiraMarkdown(true);
        params.setTestCaseIssueType("Test Case");
        params.setTestCasesPriorities("High");
        params.setModelTestCasesCreation("gpt-4");
        params.setCustomFieldsRules("inline custom rules");

        JSONObject customFields = new JSONObject().put("cf1", "v1");
        List<TestCaseGeneratorAgent.TestCase> generated = new ArrayList<>();
        generated.add(new TestCaseGeneratorAgent.TestCase("High", "Sum 1", "Description text", customFields));
        when(gen.testCaseGeneratorAgent.run(anyString(), any())).thenReturn(generated);
        when(trackerClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());

        TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
        when(trackerClient.createTicketInProject(anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    TrackerClient.FieldsInitializer initializer = inv.getArgument(4);
                    initializer.init(fields);
                    return "body-json";
                });
        ITicket created = mockTicket("TC-NEW");
        when(trackerClient.createTicket("body-json")).thenReturn(created);

        ITicket mainTicket = mockTicket("DMC-123");
        TicketContext ctx = mockContext(mainTicket, "Story text");

        TestCasesGenerator.TestCasesResult result = gen.generateTestCases(ctx, "", Collections.emptyList(), params);

        assertEquals("TC-NEW", result.getNewTestCases().get(0).getKey());
        verify(trackerClient).createTicketInProject(eq("DMC"), eq("Test Case"), eq("Sum 1"), anyString(), any());
        verify(trackerClient).linkIssueWithRelationship("DMC-123", "TC-NEW", Relationship.IS_TESTED_BY);
        verify(fields).set(eq("priority"), any(JSONObject.class));
        verify(fields).set(eq("labels"), any(JSONArray.class));
        verify(fields).set("cf1", "v1");

        ArgumentCaptor<TestCaseGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(TestCaseGeneratorAgent.Params.class);
        verify(gen.testCaseGeneratorAgent).run(eq("gpt-4"), paramsCaptor.capture());
        assertEquals("inline custom rules", paramsCaptor.getValue().getCustomFieldsRules());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generateTestCases_creationOutput_adoFieldsWithNumericAndTextPriority() throws Exception {
        TestableGenerator gen = newGenerator();
        AzureDevOpsClient adoClient = mock(AzureDevOpsClient.class);
        gen.trackerClient = adoClient;

        TestCasesGeneratorParams params = baseParams();
        params.setGenerateNew(true);
        params.setOutputType(TrackerParams.OutputType.creation);
        params.setTestCaseIssueType("Test Case");
        params.setModelTestCasesCreation("gpt-4");

        List<TestCaseGeneratorAgent.TestCase> generated = new ArrayList<>();
        generated.add(new TestCaseGeneratorAgent.TestCase("1", "Ado numeric", "Desc 1"));
        generated.add(new TestCaseGeneratorAgent.TestCase("High", "Ado text", "Desc 2"));
        when(gen.testCaseGeneratorAgent.run(anyString(), any())).thenReturn(generated);
        when(adoClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());

        TrackerClient.TrackerTicketFields fields = mock(TrackerClient.TrackerTicketFields.class);
        when(adoClient.createTicketInProject(anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    TrackerClient.FieldsInitializer initializer = inv.getArgument(4);
                    initializer.init(fields);
                    return "ado-body";
                });
        WorkItem created = mock(WorkItem.class);
        when(created.getKey()).thenReturn("TC-A1");
        when(created.getTicketKey()).thenReturn("TC-A1");
        when(adoClient.createTicket("ado-body")).thenReturn(created);

        WorkItem mainTicket = mock(WorkItem.class);
        when(mainTicket.getTicketKey()).thenReturn("WI-1");
        when(mainTicket.getKey()).thenReturn("WI-1");
        when(mainTicket.getProject()).thenReturn("ADOProj");
        TicketContext ctx = mockContext(mainTicket, "ADO story text");

        TestCasesGenerator.TestCasesResult result = gen.generateTestCases(ctx, "", Collections.emptyList(), params);

        assertEquals(2, result.getNewTestCases().size());
        verify(adoClient, times(2)).createTicketInProject(eq("ADOProj"), eq("Test Case"), anyString(), anyString(), any());
        verify(fields).set("Microsoft.VSTS.Common.Priority", 1);
        verify(fields).set("Microsoft.VSTS.Common.Priority", 2);
        verify(fields, times(2)).set("System.Tags", "ai_generated");
        verify(adoClient, times(2)).linkIssueWithRelationship(eq("WI-1"), eq("TC-A1"), eq(Relationship.IS_TESTED_BY));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generateTestCases_creationOutput_customAdapterDelegatesCreationAndLinking() throws Exception {
        TestableGenerator gen = newGenerator();
        TestCasesTrackerAdapter adapter = mock(TestCasesTrackerAdapter.class);

        TestCasesGeneratorParams params = baseParams();
        params.setGenerateNew(true);
        params.setOutputType(TrackerParams.OutputType.creation);
        params.setModelTestCasesCreation("gpt-4");

        List<TestCaseGeneratorAgent.TestCase> generated = new ArrayList<>();
        generated.add(new TestCaseGeneratorAgent.TestCase("High", "Adapter case", "Desc"));
        when(gen.testCaseGeneratorAgent.run(anyString(), any())).thenReturn(generated);

        when(adapter.getLinkedCases("DMC-123")).thenReturn(Collections.emptyList());
        ITicket created = mockTicket("C1");
        when(adapter.createTestCase(any(), eq("DMC-123"), any())).thenReturn(created);

        ITicket mainTicket = mockTicket("DMC-123");
        TicketContext ctx = mockContext(mainTicket, "Story text");

        TestCasesGenerator.TestCasesResult result = gen.generateTestCases(ctx, "", Collections.emptyList(), params, adapter);

        assertEquals("C1", result.getNewTestCases().get(0).getKey());
        verify(adapter).createTestCase(any(), eq("DMC-123"), eq(params));
        verify(adapter).linkToSource("C1", "DMC-123", Relationship.IS_TESTED_BY);
        verify(tracker(gen), never()).createTicketInProject(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ---- generateTestCases: chunked generation + deduplication ----

    private String bigText(int targetTokens) {
        // "word " costs ~2 tokens in Claude35TokenCounter (word + space)
        int words = Math.max(targetTokens / 2, 1);
        StringBuilder sb = new StringBuilder(words * 5);
        for (int i = 0; i < words; i++) {
            sb.append("word ");
        }
        return sb.toString();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generateTestCases_multipleExistingChunks_generatesPerChunkAndDeduplicates() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        int limit = new PropertyReader().getPromptChunkTokenLimit();
        // Story text eats almost the whole token budget so the per-chunk limit becomes tiny
        String storyText = bigText(limit - 2000);

        List<ITicket> allCases = new ArrayList<>();
        JSONArray keys = new JSONArray();
        for (int i = 0; i < 30; i++) {
            ITicket tc = mockTicket("TC-" + i);
            when(tc.toText()).thenReturn(bigText(400));
            allCases.add(tc);
            keys.put("TC-" + i);
        }

        when(gen.relatedTestCasesAgent.run(any(), any())).thenReturn(keys);
        when(gen.relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "related"));
        when(trackerClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());

        List<TestCaseGeneratorAgent.TestCase> generated = new ArrayList<>();
        generated.add(new TestCaseGeneratorAgent.TestCase("High", "Gen", "Desc"));
        when(gen.testCaseGeneratorAgent.run(anyString(), any())).thenReturn(generated);
        when(gen.testCaseDeduplicationAgent.run(any(), any())).thenReturn(generated);

        ITicket mainTicket = mockTicket("STORY-1");
        TicketContext ctx = mockContext(mainTicket, storyText);

        TestCasesGeneratorParams params = baseParams();
        params.setFindRelated(true);
        params.setLinkRelated(false);
        params.setGenerateNew(true);
        params.setModelTestCasesCreation("gpt-4");
        params.setModelTestCaseDeduplication("gpt-4");

        TestCasesGenerator.TestCasesResult result = gen.generateTestCases(ctx, "", allCases, params);

        assertEquals(30, result.getSimilarTestCases().size());
        assertEquals(1, result.getNewTestCases().size());
        assertEquals("Gen", result.getNewTestCases().get(0).getSummary());
        // multiple chunks of existing cases -> multiple generation calls
        verify(gen.testCaseGeneratorAgent, atLeast(2)).run(anyString(), any());
        // self-deduplication (1 call) + per-chunk deduplication against existing (>=2 calls)
        verify(gen.testCaseDeduplicationAgent, atLeast(3)).run(any(), any());
    }

    // ---- findAndLinkSimilarTestCasesBySummary: parallel paths + custom adapter ----

    @Test
    @SuppressWarnings("unchecked")
    public void findAndLink_parallelPostVerification_linksViaTrackerAndSkipsUnknownKeys() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        ITicket tc1 = mockTicket("TC-1");
        ITicket tc2 = mockTicket("TC-2");
        List<ITicket> allCases = List.of(tc1, tc2);

        when(gen.relatedTestCasesAgent.run(any(), any()))
                .thenReturn(new JSONArray("[\"TC-1\", \"TC-2\", \"TC-MISSING\"]"));
        when(gen.relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "expl"));

        TestCasesGeneratorParams params = baseParams();
        params.setEnableParallelTestCaseCheck(false);
        params.setEnableParallelPostVerification(true);
        params.setParallelPostVerificationThreads(2);

        List<TestCasesGenerator.VerifiedTestCase> results = gen.findAndLinkSimilarTestCasesBySummary(
                "STORY-1", "short story text", allCases, true, null,
                Relationship.RELATES_TO, Collections.emptyList(), params);

        assertEquals(2, results.size());
        verify(trackerClient).linkIssueWithRelationship("STORY-1", "TC-1", Relationship.RELATES_TO);
        verify(trackerClient).linkIssueWithRelationship("STORY-1", "TC-2", Relationship.RELATES_TO);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void findAndLink_parallelPostVerification_customAdapterLinksAndNormalizesKeys() throws Exception {
        TestableGenerator gen = newGenerator();
        TestCasesTrackerAdapter adapter = mock(TestCasesTrackerAdapter.class);

        ITicket tc1 = mockTicket("C1");
        ITicket tc2 = mockTicket("C2");
        List<ITicket> allCases = List.of(tc1, tc2);

        when(adapter.normalizeKeyFromAI(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(gen.relatedTestCasesAgent.run(any(), any()))
                .thenReturn(new JSONArray("[\"C1\", \"C2\"]"));
        when(gen.relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "expl"));

        TestCasesGeneratorParams params = baseParams();
        params.setEnableParallelTestCaseCheck(false);
        params.setEnableParallelPostVerification(true);
        params.setParallelPostVerificationThreads(2);

        List<TestCasesGenerator.VerifiedTestCase> results = gen.findAndLinkSimilarTestCasesBySummary(
                "STORY-1", "short story text", allCases, true, null,
                "refs", Collections.emptyList(), params, adapter);

        assertEquals(2, results.size());
        verify(adapter).linkToSource("C1", "STORY-1", "refs");
        verify(adapter).linkToSource("C2", "STORY-1", "refs");
        verify(adapter, atLeastOnce()).normalizeKeyFromAI(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void findAndLink_parallelChunkProcessing_collectsDeduplicatedResults() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        int limit = new PropertyReader().getPromptChunkTokenLimit();
        String storyText = bigText(limit - 2000);

        List<ITicket> allCases = new ArrayList<>();
        JSONArray keys = new JSONArray();
        for (int i = 0; i < 30; i++) {
            ITicket tc = mockTicket("TC-" + i);
            when(tc.toText()).thenReturn(bigText(400));
            allCases.add(tc);
            keys.put("TC-" + i);
        }

        when(gen.relatedTestCasesAgent.run(any(), any())).thenReturn(keys);
        when(gen.relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "related"));

        TestCasesGeneratorParams params = baseParams();
        params.setEnableParallelTestCaseCheck(true);
        params.setParallelTestCaseCheckThreads(2);

        List<TestCasesGenerator.VerifiedTestCase> results = gen.findAndLinkSimilarTestCasesBySummary(
                "STORY-1", storyText, allCases, false, null,
                Relationship.RELATES_TO, Collections.emptyList(), params);

        assertEquals(30, results.size());
        verify(gen.relatedTestCasesAgent, atLeast(2)).run(any(), any());
        verify(trackerClient, never()).linkIssueWithRelationship(anyString(), anyString(), anyString());
    }

    // ---- preprocessTestCases via preprocessJSAction ----

    private TestCasesGeneratorParams preprocessParams() {
        TestCasesGeneratorParams params = baseParams();
        params.setGenerateNew(true);
        params.setPreprocessJSAction("preprocess.js");
        params.setModelTestCasesCreation("gpt-4");
        return params;
    }

    @SuppressWarnings("unchecked")
    private TestCasesGenerator.TestCasesResult runPreprocessScenario(TestableGenerator gen, Object jsResult) throws Exception {
        gen.jsResult = jsResult;
        TrackerClient<ITicket> trackerClient = tracker(gen);
        when(trackerClient.getTestCases(any(), any())).thenReturn(Collections.emptyList());
        List<TestCaseGeneratorAgent.TestCase> generated = new ArrayList<>();
        generated.add(new TestCaseGeneratorAgent.TestCase("High", "Original", "Original desc"));
        when(gen.testCaseGeneratorAgent.run(anyString(), any())).thenReturn(generated);

        ITicket mainTicket = mockTicket("STORY-1");
        when(mainTicket.getTicketTitle()).thenReturn("Title");
        when(mainTicket.getTicketDescription()).thenReturn("Description");
        TicketContext ctx = mockContext(mainTicket, "Story text");

        return gen.generateTestCases(ctx, "", Collections.emptyList(), preprocessParams());
    }

    @Test
    public void preprocess_jsReturnsJsonArray_replacesTestCases() throws Exception {
        TestableGenerator gen = newGenerator();
        JSONArray jsArray = new JSONArray("[{\"priority\":\"Low\",\"summary\":\"JS Summary\",\"description\":\"JS Desc\",\"customFields\":{\"cf\":\"v\"}}]");

        TestCasesGenerator.TestCasesResult result = runPreprocessScenario(gen, jsArray);

        assertEquals(1, result.getNewTestCases().size());
        TestCaseGeneratorAgent.TestCase tc = result.getNewTestCases().get(0);
        assertEquals("Low", tc.getPriority());
        assertEquals("JS Summary", tc.getSummary());
        assertEquals("JS Desc", tc.getDescription());
        assertEquals("v", tc.getCustomFields().getString("cf"));
    }

    @Test
    public void preprocess_jsReturnsJsonString_parsesTestCases() throws Exception {
        TestableGenerator gen = newGenerator();

        TestCasesGenerator.TestCasesResult result = runPreprocessScenario(gen,
                "[{\"summary\":\"S2\",\"description\":\"D2\"}]");

        assertEquals(1, result.getNewTestCases().size());
        assertEquals("S2", result.getNewTestCases().get(0).getSummary());
        assertEquals("", result.getNewTestCases().get(0).getPriority());
        assertNotNull(result.getNewTestCases().get(0).getCustomFields());
    }

    @Test
    public void preprocess_jsReturnsInvalidString_fallsBackToOriginal() throws Exception {
        TestableGenerator gen = newGenerator();

        TestCasesGenerator.TestCasesResult result = runPreprocessScenario(gen, "not a json at all");

        assertEquals(1, result.getNewTestCases().size());
        assertEquals("Original", result.getNewTestCases().get(0).getSummary());
    }

    @Test
    public void preprocess_jsReturnsNull_fallsBackToOriginal() throws Exception {
        TestableGenerator gen = newGenerator();

        TestCasesGenerator.TestCasesResult result = runPreprocessScenario(gen, null);

        assertEquals(1, result.getNewTestCases().size());
        assertEquals("Original", result.getNewTestCases().get(0).getSummary());
    }

    @Test
    public void preprocess_jsReturnsListOfMaps_convertsToTestCases() throws Exception {
        TestableGenerator gen = newGenerator();
        Map<String, Object> item = new HashMap<>();
        item.put("priority", "Medium");
        item.put("summary", "S3");
        item.put("description", "D3");

        TestCasesGenerator.TestCasesResult result = runPreprocessScenario(gen, List.of(item));

        assertEquals(1, result.getNewTestCases().size());
        assertEquals("S3", result.getNewTestCases().get(0).getSummary());
        assertEquals("Medium", result.getNewTestCases().get(0).getPriority());
    }

    @Test
    public void preprocess_jsReturnsUnconvertibleObject_fallsBackToOriginal() throws Exception {
        TestableGenerator gen = newGenerator();

        TestCasesGenerator.TestCasesResult result = runPreprocessScenario(gen, new Object());

        assertEquals(1, result.getNewTestCases().size());
        assertEquals("Original", result.getNewTestCases().get(0).getSummary());
    }

    // ---- unpackExamples ----

    @Test
    @SuppressWarnings("unchecked")
    public void unpackExamples_confluenceUrl_extractsPageContent() throws Exception {
        TestableGenerator gen = newGenerator();
        Confluence confluence = gen.confluence;
        Content content = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(content.getStorage()).thenReturn(storage);
        when(storage.getValue()).thenReturn("<p>page body</p>");
        when(confluence.contentByUrl("https://conf.example.com/page")).thenReturn(content);

        String result = gen.unpackExamples("https://conf.example.com/page", new String[]{"f"}, null);

        assertTrue(result.contains("page body"));
        assertTrue(result.contains("https://conf.example.com/page"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void unpackExamples_qlExpression_searchesTrackerAndExtractsCustomFields() throws Exception {
        TestableGenerator gen = newGenerator();
        TrackerClient<ITicket> trackerClient = tracker(gen);

        ITicket example = mockTicket("EX-1");
        when(example.getPriority()).thenReturn("High");
        when(example.getTicketTitle()).thenReturn("Example title");
        when(example.getTicketDescription()).thenReturn("Example desc");
        when(example.getFieldsAsJSON()).thenReturn(new JSONObject().put("steps", "step json"));
        when(example.getFieldValueAsString("extra")).thenReturn("extra value");
        when(trackerClient.searchAndPerform(eq("project = EX"), any(String[].class))).thenReturn(List.of(example));

        String result = gen.unpackExamples("ql(project = EX)", new String[]{"f"}, new String[]{"steps", "extra"});

        assertTrue(result.contains("step json"));
        assertTrue(result.contains("extra value"));
        assertTrue(result.contains("Example title"));
        assertTrue(result.contains("High"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void unpackExamples_qlExpressionWithAdapter_usesAdapterSearchAndFields() throws Exception {
        TestableGenerator gen = newGenerator();
        TestCasesTrackerAdapter adapter = mock(TestCasesTrackerAdapter.class);

        ITicket example = mockTicket("C1");
        when(example.getPriority()).thenReturn("Low");
        when(example.getTicketTitle()).thenReturn("Adapter example");
        when(example.getTicketDescription()).thenReturn("Adapter desc");
        when(adapter.searchCases("label-x")).thenReturn(List.of(example));
        when(adapter.extractCustomFieldsForExample(any(), any()))
                .thenReturn(new JSONObject().put("custom_steps_json", "steps value"));

        String result = gen.unpackExamples("ql(label-x)", new String[]{"f"}, new String[]{"custom_steps_json"}, adapter);

        verify(adapter).searchCases("label-x");
        assertTrue(result.contains("Adapter example"));
        assertTrue(result.contains("steps value"));
    }

    @Test
    public void unpackExamples_plainTextOrNull_returnsAsIsOrEmpty() throws Exception {
        TestableGenerator gen = newGenerator();

        assertEquals("raw examples text", gen.unpackExamples("raw examples text", new String[]{"f"}, null));
        assertEquals("", gen.unpackExamples(null, new String[]{"f"}, null));
    }

    // ---- applyJqlModifier / extractJqlFromResult (reflection) ----

    private Method method(String name, Class<?>... types) throws Exception {
        Method m = TestCasesGenerator.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void applyJqlModifier_modifiedJql_isTrimmedAndReturned() throws Exception {
        TestableGenerator gen = newGenerator();
        gen.jsResult = "  project = MODIFIED  ";

        TestCasesGeneratorParams params = baseParams();
        params.setExistingTestCasesJql("project = ORIG");
        params.setJqlModifierJSAction("mod.js");

        ITicket ticket = mockTicket("STORY-1");
        String result = (String) method("applyJqlModifier", ITicket.class, TestCasesGeneratorParams.class)
                .invoke(gen, ticket, params);

        assertEquals("project = MODIFIED", result);
    }

    @Test
    public void applyJqlModifier_unchangedJql_returnsOriginal() throws Exception {
        TestableGenerator gen = newGenerator();
        gen.jsResult = "project = ORIG";

        TestCasesGeneratorParams params = baseParams();
        params.setExistingTestCasesJql("project = ORIG");
        params.setJqlModifierJSAction("mod.js");

        String result = (String) method("applyJqlModifier", ITicket.class, TestCasesGeneratorParams.class)
                .invoke(gen, mockTicket("STORY-1"), params);

        assertEquals("project = ORIG", result);
    }

    @Test
    public void applyJqlModifier_jsThrows_fallsBackToOriginal() throws Exception {
        TestableGenerator gen = newGenerator();
        gen.jsThrows = true;

        TestCasesGeneratorParams params = baseParams();
        params.setExistingTestCasesJql("project = ORIG");
        params.setJqlModifierJSAction("mod.js");

        String result = (String) method("applyJqlModifier", ITicket.class, TestCasesGeneratorParams.class)
                .invoke(gen, mockTicket("STORY-1"), params);

        assertEquals("project = ORIG", result);
    }

    @Test
    public void extractJqlFromResult_mapAndJsonStringVariants() throws Exception {
        TestableGenerator gen = newGenerator();
        Method m = method("extractJqlFromResult", Object.class, String.class);

        assertEquals("project = MAP", m.invoke(gen, Map.of("existingTestCasesJql", "project = MAP"), "fallback"));
        assertEquals("fallback", m.invoke(gen, Map.of("other", "value"), "fallback"));
        assertEquals("project = JSON", m.invoke(gen, "{\"existingTestCasesJql\": \"project = JSON\"}", "fallback"));
    }

    // ---- createTicketContextJson / createParamsJson / getOutputTypeSafe (reflection) ----

    @Test
    public void createTicketContextJson_simpleTicket_containsKeyTitleDescription() throws Exception {
        TestableGenerator gen = newGenerator();
        ITicket ticket = mockTicket("STORY-1");
        when(ticket.getTicketTitle()).thenReturn("Title");
        when(ticket.getTicketDescription()).thenReturn("Description");

        JSONObject json = (JSONObject) method("createTicketContextJson", ITicket.class).invoke(gen, ticket);

        assertEquals("STORY-1", json.getString("key"));
        assertEquals("Title", json.getString("title"));
        assertEquals("Description", json.getString("description"));
        assertFalse(json.has("status"));
    }

    @Test
    public void createTicketContextJson_jiraTicket_includesStatusPriorityIssueType() throws Exception {
        TestableGenerator gen = newGenerator();
        Ticket ticket = mock(Ticket.class);
        Fields fields = mock(Fields.class);
        Status status = mock(Status.class);
        Priority priority = mock(Priority.class);
        IssueType issueType = mock(IssueType.class);

        when(ticket.getTicketKey()).thenReturn("JIRA-1");
        when(ticket.getTicketTitle()).thenReturn("Jira title");
        when(ticket.getTicketDescription()).thenReturn("Jira desc");
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getStatus()).thenReturn(status);
        when(status.getName()).thenReturn("Open");
        when(fields.getPriority()).thenReturn(priority);
        when(priority.getName()).thenReturn("High");
        when(fields.getIssueType()).thenReturn(issueType);
        when(issueType.getName()).thenReturn("Story");

        JSONObject json = (JSONObject) method("createTicketContextJson", ITicket.class).invoke(gen, ticket);

        assertEquals("Open", json.getString("status"));
        assertEquals("High", json.getString("priority"));
        assertEquals("Story", json.getString("issueType"));
    }

    @Test
    public void createTicketContextJson_ticketThrows_returnsEmptyJson() throws Exception {
        TestableGenerator gen = newGenerator();
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenThrow(new RuntimeException("broken ticket"));

        JSONObject json = (JSONObject) method("createTicketContextJson", ITicket.class).invoke(gen, ticket);

        assertEquals(0, json.length());
    }

    @Test
    public void createParamsJson_populatedParams_containsAllFields() throws Exception {
        TestableGenerator gen = newGenerator();
        TestCasesGeneratorParams params = baseParams();
        params.setTestCaseIssueType("Test Case");
        params.setTestCasesPriorities("High,Medium");
        params.setTargetProject("TP");
        params.setInputJql("project = TP");

        JSONObject json = (JSONObject) method("createParamsJson", TestCasesGeneratorParams.class).invoke(gen, params);

        assertEquals("Test Case", json.getString("testCaseIssueType"));
        assertEquals("High,Medium", json.getString("testCasesPriorities"));
        assertEquals("TP", json.getString("targetProject"));
        assertEquals("project = TP", json.getString("inputJql"));
    }

    @Test
    public void getOutputTypeSafe_nullOutputType_defaultsToCreation() throws Exception {
        TestableGenerator gen = newGenerator();
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setOutputType(null);

        TrackerParams.OutputType result = (TrackerParams.OutputType)
                method("getOutputTypeSafe", TestCasesGeneratorParams.class).invoke(gen, params);

        assertEquals(TrackerParams.OutputType.creation, result);
    }
}
