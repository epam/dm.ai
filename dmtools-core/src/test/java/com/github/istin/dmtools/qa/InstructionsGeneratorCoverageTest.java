// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.agent.InstructionGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class InstructionsGeneratorCoverageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private InstructionsGenerator generator;
    private TrackerClient trackerClient;
    private Confluence confluence;
    private InstructionGeneratorAgent instructionGeneratorAgent;
    private ContentMergeAgent contentMergeAgent;
    private AI ai;

    @Before
    public void setUp() throws Exception {
        // Pin the chunk token limit so chunk/merge counts below are deterministic
        // regardless of the developer's local dmtools.env (gitignored; CI default
        // is 50000 while these tests were derived for 100000).
        PropertyReader.setOverrides(Collections.singletonMap("PROMPT_CHUNK_TOKEN_LIMIT", "100000"));

        generator = new InstructionsGenerator();
        trackerClient = mock(TrackerClient.class);
        confluence = mock(Confluence.class);
        instructionGeneratorAgent = mock(InstructionGeneratorAgent.class);
        contentMergeAgent = mock(ContentMergeAgent.class);
        ai = mock(AI.class);

        setField(generator, "trackerClient", trackerClient);
        setField(generator, "confluence", confluence);
        setField(generator, "instructionGeneratorAgent", instructionGeneratorAgent);
        setField(generator, "contentMergeAgent", contentMergeAgent);
        setField(generator, "ai", ai);

        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary", "description"});
    }

    @After
    public void tearDown() {
        PropertyReader.clearOverrides();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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

    private ITicket ticket(String key, String text) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn(key);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.toText()).thenReturn(text);
        return ticket;
    }

    private InstructionsGeneratorParams fileParams(File output) {
        InstructionsGeneratorParams params = new InstructionsGeneratorParams();
        params.setInputJql("project = TEST");
        params.setFields(Arrays.asList("summary", "description"));
        params.setInstructionType("test_cases");
        params.setModel("test-model");
        params.setOutputDestination("file");
        params.setOutputPath(output.getAbsolutePath());
        params.setMergeWithExisting(false);
        params.setGenerationThreads(2);
        params.setMergingThreads(1);
        return params;
    }

    private Content buildContent(String storageValue) {
        JSONObject json = new JSONObject();
        json.put("id", "123456");
        json.put("title", "Existing Page");
        json.put("body", new JSONObject().put("storage", new JSONObject()
                .put("value", storageValue)
                .put("representation", "storage")));
        json.put("_expandable", new JSONObject().put("space", "/rest/api/space/DOC"));
        json.put("ancestors", new JSONArray().put(new JSONObject().put("id", "999")));
        return new Content(json);
    }

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }

    @Test
    public void testRunJob_NoTicketsFound() throws Exception {
        stubTickets();

        InstructionsGenerator.InstructionsResult result = generator.runJob(
                fileParams(new File(tempFolder.getRoot(), "out.txt")));

        assertTrue(result.isSuccess());
        assertEquals(0, result.getTicketsProcessed());
        assertEquals(0, result.getChunksProcessed());
        assertEquals("No tickets found matching the query", result.getErrorMessage());
        verify(instructionGeneratorAgent, never()).run(any(), any());
    }

    @Test
    public void testRunJob_SingleChunk_FileOutput() throws Exception {
        stubTickets(ticket("TEST-1", "First ticket text"), ticket("TEST-2", "Second ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Generated instructions body");

        File out = new File(tempFolder.getRoot(), "out.txt");
        InstructionsGenerator.InstructionsResult result = generator.runJob(fileParams(out));

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTicketsProcessed());
        assertEquals(1, result.getChunksProcessed());
        assertEquals("Generated instructions body", result.getGeneratedInstructions());
        assertEquals(out.getAbsolutePath(), result.getOutputLocation());
        assertEquals("Generated instructions body",
                new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
        verify(instructionGeneratorAgent, times(1)).run(any(), any());
        verify(contentMergeAgent, never()).run(any(), any());
    }

    @Test
    public void testRunJob_AdditionalContextCombinedWithConfluencePages() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Instructions");

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "out.txt"));
        params.setAdditionalContext("Team rules");
        // second entry is empty and must be skipped by extractAdditionalContext
        params.setConfluencePages(new String[]{"Plain documentation text", ""});

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        ArgumentCaptor<InstructionGeneratorAgent.Params> captor =
                ArgumentCaptor.forClass(InstructionGeneratorAgent.Params.class);
        verify(instructionGeneratorAgent).run(any(), captor.capture());
        InstructionGeneratorAgent.Params agentParams = captor.getValue();
        assertEquals("test_cases", agentParams.getInstructionType());
        assertEquals(Arrays.asList("summary", "description"), agentParams.getTargetFields());
        assertEquals("jira", agentParams.getPlatform());
        assertTrue(agentParams.getTicketsText().contains("Ticket text"));
        String combined = agentParams.getAdditionalContext();
        assertTrue(combined.startsWith("Team rules"));
        assertTrue(combined.contains("=== Additional Context from Documentation ==="));
        assertTrue(combined.contains("Plain documentation text"));
    }

    @Test
    public void testRunJob_UserContextOnly_NoConfluencePages() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Instructions");

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "out.txt"));
        params.setAdditionalContext("Rules only");

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        ArgumentCaptor<InstructionGeneratorAgent.Params> captor =
                ArgumentCaptor.forClass(InstructionGeneratorAgent.Params.class);
        verify(instructionGeneratorAgent).run(any(), captor.capture());
        assertEquals("Rules only", captor.getValue().getAdditionalContext());
    }

    @Test
    public void testRunJob_MergeWithExistingFile() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("New instructions");
        when(contentMergeAgent.run(any(), any())).thenReturn("Merged final");

        File out = tempFolder.newFile("existing.txt");
        Files.write(out.toPath(), "Old content".getBytes(StandardCharsets.UTF_8));

        InstructionsGeneratorParams params = fileParams(out);
        params.setMergeWithExisting(true);
        params.setConfluencePages(new String[]{"Doc page text"});

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals("Merged final", result.getGeneratedInstructions());
        assertEquals("Merged final",
                new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));

        ArgumentCaptor<ContentMergeAgent.Params> captor = ArgumentCaptor.forClass(ContentMergeAgent.Params.class);
        verify(contentMergeAgent, times(1)).run(any(), captor.capture());
        ContentMergeAgent.Params mergeParams = captor.getValue();
        assertEquals("Old content", mergeParams.getSourceContent());
        assertEquals("New instructions", mergeParams.getNewContent());
        assertEquals("text", mergeParams.getContentType());
        assertTrue(mergeParams.getTask().contains("test_cases"));

        ArgumentCaptor<InstructionGeneratorAgent.Params> agentCaptor =
                ArgumentCaptor.forClass(InstructionGeneratorAgent.Params.class);
        verify(instructionGeneratorAgent).run(any(), agentCaptor.capture());
        // extracted-only context: header is present, no user context prefix
        assertTrue(agentCaptor.getValue().getAdditionalContext()
                .startsWith("=== Additional Context from Documentation ==="));
    }

    @Test
    public void testRunJob_MergeWithExisting_FileNotPresent_CreatesDirectories() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Fresh instructions");

        File out = new File(new File(tempFolder.getRoot(), "nested/deep"), "out.txt");
        InstructionsGeneratorParams params = fileParams(out);
        params.setMergeWithExisting(true);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals("Fresh instructions", result.getGeneratedInstructions());
        assertTrue(out.exists());
        verify(contentMergeAgent, never()).run(any(), any());
    }

    @Test
    public void testRunJob_ConfluenceOutput_UpdatesExistingPage() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Confluence instructions");
        Content page = buildContent("<p>old body</p>");
        when(confluence.contentByUrl(anyString())).thenReturn(page);
        when(confluence.updatePage(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(page);

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "unused.txt"));
        params.setOutputDestination("confluence");
        params.setOutputPath("https://confluence.example.com/pages/123456");

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals("Confluence page updated: https://confluence.example.com/pages/123456",
                result.getOutputLocation());
        verify(confluence).updatePage("123456", "Existing Page", "999",
                "Confluence instructions", "DOC");
        verify(contentMergeAgent, never()).run(any(), any());
    }

    @Test
    public void testRunJob_ConfluenceOutput_MergeWithExistingHtml() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("New html instructions");
        Content page = buildContent("<p>old body</p>");
        when(confluence.contentByUrl(anyString())).thenReturn(page);
        when(confluence.updatePage(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(page);
        when(contentMergeAgent.run(any(), any())).thenReturn("Merged html");

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "unused.txt"));
        params.setOutputDestination("confluence");
        params.setOutputPath("https://confluence.example.com/pages/123456");
        params.setMergeWithExisting(true);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals("Merged html", result.getGeneratedInstructions());
        verify(confluence, times(2)).contentByUrl(anyString());

        ArgumentCaptor<ContentMergeAgent.Params> captor = ArgumentCaptor.forClass(ContentMergeAgent.Params.class);
        verify(contentMergeAgent, times(1)).run(any(), captor.capture());
        assertEquals("<p>old body</p>", captor.getValue().getSourceContent());
        assertEquals("html", captor.getValue().getContentType());

        verify(confluence).updatePage("123456", "Existing Page", "999", "Merged html", "DOC");
    }

    @Test
    public void testRunJob_ConfluenceOutput_PageNotFound() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Instructions");
        when(confluence.contentByUrl(anyString())).thenReturn(null);

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "unused.txt"));
        params.setOutputDestination("confluence");
        params.setOutputPath("https://confluence.example.com/pages/999");
        params.setMergeWithExisting(false);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Failed to write to Confluence URL"));
        verify(confluence, never()).updatePage(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testRunJob_ConfluenceLoadExistingThrows_FallsBackToNoMerge() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Instructions");
        Content page = buildContent("<p>old body</p>");
        // first call (loadExistingContent) fails, second (outputInstructions) succeeds
        when(confluence.contentByUrl(anyString()))
                .thenThrow(new IOException("read failure"))
                .thenReturn(page);
        when(confluence.updatePage(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(page);

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "unused.txt"));
        params.setOutputDestination("confluence");
        params.setOutputPath("https://confluence.example.com/pages/123456");
        params.setMergeWithExisting(true);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals("Instructions", result.getGeneratedInstructions());
        verify(contentMergeAgent, never()).run(any(), any());
        verify(confluence).updatePage(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testRunJob_UnsupportedOutputDestination() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("Instructions");

        InstructionsGeneratorParams params = fileParams(new File(tempFolder.getRoot(), "unused.txt"));
        params.setOutputDestination("printer");
        params.setOutputPath("nowhere");

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unsupported output destination: printer"));
    }

    @Test
    public void testRunJob_TrackerClientThrows() throws Exception {
        doThrow(new IOException("jira down")).when(trackerClient)
                .searchAndPerform(any(JiraClient.Performer.class), any(), any());

        InstructionsGenerator.InstructionsResult result = generator.runJob(
                fileParams(new File(tempFolder.getRoot(), "out.txt")));

        assertFalse(result.isSuccess());
        assertEquals("jira down", result.getErrorMessage());
        assertEquals(0, result.getTicketsProcessed());
    }

    @Test
    public void testRunJob_ChunkProcessingFails() throws Exception {
        stubTickets(ticket("TEST-1", "Ticket text"));
        when(instructionGeneratorAgent.run(any(), any())).thenThrow(new RuntimeException("AI failed"));

        InstructionsGenerator.InstructionsResult result = generator.runJob(
                fileParams(new File(tempFolder.getRoot(), "out.txt")));

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Error getting result for chunk 1"));
    }

    @Test
    public void testRunJob_MultipleChunks_SequentialMerge() throws Exception {
        // each ticket is ~26k tokens > half of the 49k chunk limit -> 2 separate ticket chunks
        // (PROMPT_CHUNK_TOKEN_LIMIT=100000 from dmtools.env -> (100000-2000)/2 = 49000)
        String ticketText = repeat("aaaa ", 13000);
        stubTickets(ticket("TEST-1", ticketText), ticket("TEST-2", ticketText));
        // each chunk result is ~16k tokens -> combined ~32k tokens > 30k merge limit,
        // ChunkPreparation splits it into 6 instruction chunks (splitLargeObject behavior)
        when(instructionGeneratorAgent.run(any(), any())).thenReturn(repeat("bbbb ", 8000));
        when(contentMergeAgent.run(any(), any())).thenReturn("Sequentially merged");

        File out = new File(tempFolder.getRoot(), "out.txt");
        InstructionsGeneratorParams params = fileParams(out);
        params.setMergingThreads(1);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getChunksProcessed());
        assertEquals("Sequentially merged", result.getGeneratedInstructions());
        verify(instructionGeneratorAgent, times(2)).run(any(), any());
        // mergingThreads=1 forces sequential merging: 6 chunks -> 5 pairwise merge calls
        verify(contentMergeAgent, times(5)).run(any(), any());
    }

    @Test
    public void testRunJob_MultipleChunks_ParallelMergeWithOddCarry() throws Exception {
        // 3 tickets of ~26k tokens each -> 3 ticket chunks (49k chunk limit)
        String ticketText = repeat("aaaa ", 13000);
        stubTickets(ticket("TEST-1", ticketText), ticket("TEST-2", ticketText), ticket("TEST-3", ticketText));
        // each chunk result is ~10k tokens -> combined ~30k+ tokens,
        // ChunkPreparation splits it into 6 instruction chunks
        when(instructionGeneratorAgent.run(any(), any())).thenReturn(repeat("bbbb ", 5000));
        when(contentMergeAgent.run(any(), any())).thenReturn("merged pair");

        File out = new File(tempFolder.getRoot(), "out.txt");
        InstructionsGeneratorParams params = fileParams(out);
        params.setGenerationThreads(3);
        params.setMergingThreads(2);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getChunksProcessed());
        assertEquals("merged pair", result.getGeneratedInstructions());
        verify(instructionGeneratorAgent, times(3)).run(any(), any());
        // parallel pairwise merging: 6 -> 3 -> 2 -> 1, the 3-chunk level carries the odd element forward
        verify(contentMergeAgent, times(5)).run(any(), any());
    }

    @Test
    public void testRunJob_MultipleTicketChunks_SingleInstructionChunk() throws Exception {
        // 2 ticket chunks, but the combined instructions are small enough to stay a single
        // merge chunk -> mergeInstructions returns it directly without ContentMergeAgent
        String ticketText = repeat("aaaa ", 13000);
        stubTickets(ticket("TEST-1", ticketText), ticket("TEST-2", ticketText));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn("small result");

        File out = new File(tempFolder.getRoot(), "out.txt");
        InstructionsGenerator.InstructionsResult result = generator.runJob(fileParams(out));

        assertTrue(result.isSuccess());
        assertEquals(2, result.getChunksProcessed());
        assertEquals("small result\n\n---\n\nsmall result", result.getGeneratedInstructions());
        verify(instructionGeneratorAgent, times(2)).run(any(), any());
        verify(contentMergeAgent, never()).run(any(), any());
    }

    @Test
    public void testRunJob_ParallelMergeFails() throws Exception {
        String ticketText = repeat("aaaa ", 13000);
        stubTickets(ticket("TEST-1", ticketText), ticket("TEST-2", ticketText), ticket("TEST-3", ticketText));
        when(instructionGeneratorAgent.run(any(), any())).thenReturn(repeat("bbbb ", 5000));
        when(contentMergeAgent.run(any(), any())).thenThrow(new RuntimeException("merge AI down"));

        File out = new File(tempFolder.getRoot(), "out.txt");
        InstructionsGeneratorParams params = fileParams(out);
        params.setGenerationThreads(3);
        params.setMergingThreads(2);

        InstructionsGenerator.InstructionsResult result = generator.runJob(params);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Failed to merge pair"));
    }

    @Test
    public void testInstructionsResultDataClass() {
        InstructionsGenerator.InstructionsResult result =
                new InstructionsGenerator.InstructionsResult(5, 3, "location", "generated", true, "none");

        assertEquals(5, result.getTicketsProcessed());
        assertEquals(3, result.getChunksProcessed());
        assertEquals("location", result.getOutputLocation());
        assertEquals("generated", result.getGeneratedInstructions());
        assertTrue(result.isSuccess());
        assertEquals("none", result.getErrorMessage());
        assertNotNull(result.toString());

        InstructionsGenerator.InstructionsResult same =
                new InstructionsGenerator.InstructionsResult(5, 3, "location", "generated", true, "none");
        assertEquals(result, same);
        assertEquals(result.hashCode(), same.hashCode());

        InstructionsGenerator.InstructionsResult empty = new InstructionsGenerator.InstructionsResult();
        empty.setTicketsProcessed(1);
        empty.setChunksProcessed(2);
        empty.setOutputLocation("loc");
        empty.setGeneratedInstructions("gen");
        empty.setSuccess(false);
        empty.setErrorMessage("err");
        assertEquals(1, empty.getTicketsProcessed());
        assertEquals(2, empty.getChunksProcessed());
        assertEquals("loc", empty.getOutputLocation());
        assertEquals("gen", empty.getGeneratedInstructions());
        assertFalse(empty.isSuccess());
        assertEquals("err", empty.getErrorMessage());
    }
}
