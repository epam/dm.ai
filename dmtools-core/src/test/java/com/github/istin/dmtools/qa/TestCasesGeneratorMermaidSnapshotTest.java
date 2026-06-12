// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import com.github.istin.dmtools.ai.agent.RelatedTestCasesAgent;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.TrackerParams;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestCasesGeneratorMermaidSnapshotTest {

    @TempDir
    Path tempDir;

    private TestCasesGenerator buildGenerator(
            TrackerClient<ITicket> trackerClient,
            RelatedTestCasesAgent relatedTestCasesAgent,
            RelatedTestCaseAgent relatedTestCaseAgent,
            TestCaseGeneratorAgent testCaseGeneratorAgent) throws Exception {
        TestCasesGenerator gen = new TestCasesGenerator();
        setField(gen, "trackerClient", trackerClient);
        setField(gen, "confluence", mock(Confluence.class));
        setField(gen, "relatedTestCasesAgent", relatedTestCasesAgent);
        setField(gen, "relatedTestCaseAgent", relatedTestCaseAgent);
        setField(gen, "testCaseGeneratorAgent", testCaseGeneratorAgent);
        setField(gen, "ai", mock(AI.class));
        return gen;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ITicket testTicket(String key, String text) throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn(key);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.toText()).thenReturn(text);
        when(ticket.getTicketTitle()).thenReturn(key + " title");
        return ticket;
    }

    @Test
    void searchUsesSnapshotTextVerificationUsesFullText() throws Exception {
        // Given: snapshot repo
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-TC-1");
        Files.createDirectories(ticketDir);
        Files.write(ticketDir.resolve("Snapshot.mmd"), "flowchart TD\nA --> B".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
        RelatedTestCasesAgent relatedTestCasesAgent = mock(RelatedTestCasesAgent.class);
        RelatedTestCaseAgent relatedTestCaseAgent = mock(RelatedTestCaseAgent.class);
        TestCaseGeneratorAgent testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);

        ITicket existingTest = testTicket("PROJ-TC-1", "FULL TICKET TEXT");

        TestCasesGenerator gen = buildGenerator(trackerClient, relatedTestCasesAgent, relatedTestCaseAgent, testCaseGeneratorAgent);

        when(relatedTestCasesAgent.run(any(), any()))
                .thenReturn(new JSONArray("[\"PROJ-TC-1\"]"));
        when(relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "related"));
        when(trackerClient.getTestCases(any(), anyString())).thenReturn(Collections.emptyList());

        ITicket mainTicket = testTicket("PROJ-123", "Story text");
        TicketContext ctx = mock(TicketContext.class);
        when(ctx.getTicket()).thenReturn(mainTicket);
        when(ctx.toText()).thenReturn("Story text");

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setFindRelated(true);
        params.setLinkRelated(false);
        params.setGenerateNew(false);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setMermaidIndexStoragePath(tempDir.toString());
        params.setMermaidIndexIntegration("jira");

        gen.generateTestCases(ctx, "", List.of(existingTest), params);

        // Then: search agent receives snapshot text
        ArgumentCaptor<RelatedTestCasesAgent.Params> searchCaptor = ArgumentCaptor.forClass(RelatedTestCasesAgent.Params.class);
        verify(relatedTestCasesAgent).run(isNull(), searchCaptor.capture());
        String searchExistingCases = searchCaptor.getValue().getExistingTestCases();
        assertTrue(searchExistingCases.contains("Mermaid snapshot:"), "Search should receive snapshot marker");
        assertTrue(searchExistingCases.contains("flowchart TD"), "Search should receive diagram content");
        assertFalse(searchExistingCases.contains("FULL TICKET TEXT"), "Search should not receive full ticket text");

        // Then: verification agent receives full ticket text
        ArgumentCaptor<RelatedTestCaseAgent.Params> verifyCaptor = ArgumentCaptor.forClass(RelatedTestCaseAgent.Params.class);
        verify(relatedTestCaseAgent).run(isNull(), verifyCaptor.capture());
        String verifyExistingCase = verifyCaptor.getValue().getExistingTestCase();
        assertEquals("FULL TICKET TEXT", verifyExistingCase, "Verification should receive full ticket text");
    }

    @Test
    void generationUsesFullTextByDefault() throws Exception {
        // Given: snapshot repo
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-TC-2");
        Files.createDirectories(ticketDir);
        Files.write(ticketDir.resolve("Snapshot.mmd"), "graph LR\nX --> Y".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
        RelatedTestCasesAgent relatedTestCasesAgent = mock(RelatedTestCasesAgent.class);
        RelatedTestCaseAgent relatedTestCaseAgent = mock(RelatedTestCaseAgent.class);
        TestCaseGeneratorAgent testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);

        ITicket existingTest = testTicket("PROJ-TC-2", "FULL TICKET TEXT FOR GENERATION");

        TestCasesGenerator gen = buildGenerator(trackerClient, relatedTestCasesAgent, relatedTestCaseAgent, testCaseGeneratorAgent);

        when(relatedTestCasesAgent.run(any(), any()))
                .thenReturn(new JSONArray("[\"PROJ-TC-2\"]"));
        when(relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "related"));
        when(trackerClient.getTestCases(any(), anyString())).thenReturn(Collections.emptyList());
        when(testCaseGeneratorAgent.run(anyString(), any()))
                .thenReturn(Collections.emptyList());

        ITicket mainTicket = testTicket("PROJ-124", "Story text");
        TicketContext ctx = mock(TicketContext.class);
        when(ctx.getTicket()).thenReturn(mainTicket);
        when(ctx.toText()).thenReturn("Story text");

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setFindRelated(true);
        params.setLinkRelated(false);
        params.setGenerateNew(true);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setTestCasesPriorities("High");
        params.setModelTestCasesCreation("gpt-4");
        params.setMermaidIndexStoragePath(tempDir.toString());
        params.setMermaidIndexIntegration("jira");

        gen.generateTestCases(ctx, "", List.of(existingTest), params);

        // Then: generation agent receives full ticket text
        ArgumentCaptor<TestCaseGeneratorAgent.Params> genCaptor = ArgumentCaptor.forClass(TestCaseGeneratorAgent.Params.class);
        verify(testCaseGeneratorAgent).run(eq("gpt-4"), genCaptor.capture());
        String existingTestCasesText = genCaptor.getValue().getExistingTestCases();
        assertTrue(existingTestCasesText.contains("FULL TICKET TEXT FOR GENERATION"), "Generation should receive full ticket text by default");
        assertFalse(existingTestCasesText.contains("graph LR"), "Generation should not receive snapshot text by default");
    }

    @Test
    void generationUsesSnapshotTextWhenEnabled() throws Exception {
        // Given: snapshot repo
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-TC-3");
        Files.createDirectories(ticketDir);
        Files.write(ticketDir.resolve("Snapshot.mmd"), "graph LR\nX --> Y".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
        RelatedTestCasesAgent relatedTestCasesAgent = mock(RelatedTestCasesAgent.class);
        RelatedTestCaseAgent relatedTestCaseAgent = mock(RelatedTestCaseAgent.class);
        TestCaseGeneratorAgent testCaseGeneratorAgent = mock(TestCaseGeneratorAgent.class);

        ITicket existingTest = testTicket("PROJ-TC-3", "FULL TICKET TEXT FOR GENERATION");

        TestCasesGenerator gen = buildGenerator(trackerClient, relatedTestCasesAgent, relatedTestCaseAgent, testCaseGeneratorAgent);

        when(relatedTestCasesAgent.run(any(), any()))
                .thenReturn(new JSONArray("[\"PROJ-TC-3\"]"));
        when(relatedTestCaseAgent.run(any(), any()))
                .thenReturn(new RelatedTestCaseAgent.Result(true, "related"));
        when(trackerClient.getTestCases(any(), anyString())).thenReturn(Collections.emptyList());
        when(testCaseGeneratorAgent.run(anyString(), any()))
                .thenReturn(Collections.emptyList());

        ITicket mainTicket = testTicket("PROJ-125", "Story text");
        TicketContext ctx = mock(TicketContext.class);
        when(ctx.getTicket()).thenReturn(mainTicket);
        when(ctx.toText()).thenReturn("Story text");

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setFindRelated(true);
        params.setLinkRelated(false);
        params.setGenerateNew(true);
        params.setOutputType(TrackerParams.OutputType.none);
        params.setTestCasesPriorities("High");
        params.setModelTestCasesCreation("gpt-4");
        params.setMermaidIndexStoragePath(tempDir.toString());
        params.setMermaidIndexIntegration("jira");
        params.setUseMermaidSnapshotForGeneration(true);

        gen.generateTestCases(ctx, "", List.of(existingTest), params);

        // Then: generation agent receives snapshot text
        ArgumentCaptor<TestCaseGeneratorAgent.Params> genCaptor = ArgumentCaptor.forClass(TestCaseGeneratorAgent.Params.class);
        verify(testCaseGeneratorAgent).run(eq("gpt-4"), genCaptor.capture());
        String existingTestCasesText = genCaptor.getValue().getExistingTestCases();
        assertTrue(existingTestCasesText.contains("graph LR"), "Generation should receive snapshot text when enabled");
    }
}
