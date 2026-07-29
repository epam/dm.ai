// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.kb.tool;

import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.agent.KBOrchestrator;
import com.github.istin.dmtools.common.kb.model.KBProcessingMode;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.model.SourceConfig;
import com.github.istin.dmtools.common.kb.model.SourceInfo;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Additional coverage tests for {@link KBTools}: kbGet, kbBuild, kbProcess,
 * kbAggregate, path resolution fallbacks and kbProcessInbox edge branches.
 */
class KBToolsCoverageTest {

    private KBTools kbTools;
    private KBOrchestrator mockOrchestrator;
    private PropertyReader mockPropertyReader;
    private SourceConfigManager mockSourceConfigManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockOrchestrator = mock(KBOrchestrator.class);
        mockPropertyReader = mock(PropertyReader.class);
        mockSourceConfigManager = mock(SourceConfigManager.class);

        kbTools = new KBTools(mockOrchestrator, mockPropertyReader, mockSourceConfigManager);

        when(mockPropertyReader.getValue("DMTOOLS_KB_OUTPUT_PATH"))
            .thenReturn(tempDir.toString());
    }

    // ========== kbGet Tests ==========

    @Test
    void testKbGet_SourceFound() throws Exception {
        SourceConfig config = new SourceConfig();
        SourceInfo info = new SourceInfo();
        info.setLastSyncDate("2024-10-10T12:00:00Z");
        config.getSources().put("teams_chat", info);
        when(mockSourceConfigManager.loadConfig(any(Path.class))).thenReturn(config);

        String result = kbTools.kbGet("teams_chat", tempDir.toString());

        assertEquals("2024-10-10T12:00:00Z", result);
    }

    @Test
    void testKbGet_SourceNotFound() throws Exception {
        when(mockSourceConfigManager.loadConfig(any(Path.class))).thenReturn(new SourceConfig());

        String result = kbTools.kbGet("unknown_source", tempDir.toString());

        assertEquals("Source not found: unknown_source", result);
    }

    @Test
    void testKbGet_Exception() throws Exception {
        when(mockSourceConfigManager.loadConfig(any(Path.class)))
            .thenThrow(new RuntimeException("config load failed"));

        String result = kbTools.kbGet("teams_chat", tempDir.toString());

        assertTrue(result.startsWith("Error: "));
        assertTrue(result.contains("config load failed"));
    }

    // ========== kbBuild Tests ==========

    @Test
    void testKbBuild_InputFileNotFound() throws Exception {
        String result = kbTools.kbBuild("teams_chat", tempDir.resolve("missing.json").toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Input file not found"));
        verify(mockOrchestrator, never()).run(any());
    }

    @Test
    void testKbBuild_Success() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        KBResult mockResult = createMockResult(true, "Built", 5, 3, 2);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);

        String result = kbTools.kbBuild("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"message\": \"Built\""));
        assertTrue(result.contains("\"questions\": 5"));
        assertTrue(result.contains("\"answers\": 3"));
        assertTrue(result.contains("\"notes\": 2"));

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        KBOrchestratorParams params = paramsCaptor.getValue();
        assertEquals("teams_chat", params.getSourceName());
        assertEquals(inputFile.toString(), params.getInputFile());
        assertEquals("2024-10-10T12:00:00Z", params.getDateTime());
        assertEquals(KBProcessingMode.FULL, params.getProcessingMode());
        assertFalse(params.isCleanSourceBeforeProcessing());
    }

    @Test
    void testKbBuild_CleanSourceEnabled() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        KBResult mockResult = createMockResult(true, "Built", 1, 1, 1);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        String result = kbTools.kbBuild("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), "TRUE");

        assertTrue(result.contains("\"success\": true"));

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertTrue(paramsCaptor.getValue().isCleanSourceBeforeProcessing());
    }

    @Test
    void testKbBuild_OrchestratorException() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenThrow(new RuntimeException("build \"failed\"\n"));

        String result = kbTools.kbBuild("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("build \\\"failed\\\"\\n"));
    }

    // ========== kbProcess Tests ==========

    @Test
    void testKbProcess_InputFileNotFound() throws Exception {
        String result = kbTools.kbProcess("teams_chat", tempDir.resolve("missing.json").toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("Input file not found"));
        verify(mockOrchestrator, never()).run(any());
    }

    @Test
    void testKbProcess_Success() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        KBResult mockResult = createMockResult(true, "Processed", 4, 2, 1);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        String result = kbTools.kbProcess("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"questions\": 4"));

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertEquals(KBProcessingMode.PROCESS_ONLY, paramsCaptor.getValue().getProcessingMode());
        assertFalse(paramsCaptor.getValue().isCleanSourceBeforeProcessing());
    }

    @Test
    void testKbProcess_CleanSourceEnabled() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        KBResult mockResult = createMockResult(true, "Processed", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        kbTools.kbProcess("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), "true");

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertTrue(paramsCaptor.getValue().isCleanSourceBeforeProcessing());
    }

    @Test
    void testKbProcess_OrchestratorException() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenThrow(new RuntimeException("process failed"));

        String result = kbTools.kbProcess("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("process failed"));
    }

    // ========== kbAggregate Tests ==========

    @Test
    void testKbAggregate_SuccessDefaultSmartMode() throws Exception {
        KBResult mockResult = createMockResult(true, "Aggregated", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        String result = kbTools.kbAggregate("teams_chat", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"message\": \"Aggregated\""));

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        KBOrchestratorParams params = paramsCaptor.getValue();
        assertEquals("teams_chat", params.getSourceName());
        assertEquals(KBProcessingMode.AGGREGATE_ONLY, params.getProcessingMode());
        assertTrue(params.isSmartAggregation());
    }

    @Test
    void testKbAggregate_SmartModeDisabled() throws Exception {
        KBResult mockResult = createMockResult(true, "Aggregated", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        kbTools.kbAggregate(null, tempDir.toString(), "false");

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertNull(paramsCaptor.getValue().getSourceName());
        assertFalse(paramsCaptor.getValue().isSmartAggregation());
    }

    @Test
    void testKbAggregate_Exception() throws Exception {
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenThrow(new RuntimeException("aggregate failed"));

        String result = kbTools.kbAggregate("teams_chat", tempDir.toString(), "true");

        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("aggregate failed"));
    }

    // ========== resolveOutputPath fallback Tests ==========

    @Test
    void testResolveOutputPath_FallsBackToUserDir() throws Exception {
        // No output path and no env var -> falls back to current working directory
        when(mockPropertyReader.getValue("DMTOOLS_KB_OUTPUT_PATH")).thenReturn(null);
        KBResult mockResult = createMockResult(true, "Aggregated", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        String result = kbTools.kbAggregate(null, null, null);

        assertTrue(result.contains("\"success\": true"));

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().toString(),
                paramsCaptor.getValue().getOutputPath());
    }

    @Test
    void testResolveOutputPath_BlankEnvVarFallsBackToUserDir() throws Exception {
        when(mockPropertyReader.getValue("DMTOOLS_KB_OUTPUT_PATH")).thenReturn("  ");
        KBResult mockResult = createMockResult(true, "Aggregated", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        kbTools.kbAggregate(null, "  ", null);

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().toString(),
                paramsCaptor.getValue().getOutputPath());
    }

    @Test
    void testResolveOutputPath_UsesEnvVar() throws Exception {
        // No output path -> uses DMTOOLS_KB_OUTPUT_PATH from property reader (stubbed in setUp)
        KBResult mockResult = createMockResult(true, "Aggregated", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        kbTools.kbAggregate(null, null, null);

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator).run(paramsCaptor.capture());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(),
                paramsCaptor.getValue().getOutputPath());
    }

    @Test
    void testKbBuild_NullResultMessageEscaped() throws Exception {
        Path inputFile = tempDir.resolve("messages.json");
        Files.writeString(inputFile, "{}");

        KBResult mockResult = createMockResult(true, null, 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        String result = kbTools.kbBuild("teams_chat", inputFile.toString(),
                "2024-10-10T12:00:00Z", tempDir.toString(), null);

        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"message\": \"\""));
    }

    // ========== kbProcessInbox additional branches ==========

    @Test
    void testProcessInbox_AggregatePhaseFails() throws Exception {
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        Files.writeString(inboxRaw.resolve("msg.json"), "{}");

        KBResult processResult = createMockResult(true, "Success", 1, 1, 0);
        KBResult aggregateResult = createMockResult(false, "Aggregate failed", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(processResult)
            .thenReturn(aggregateResult);

        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);

        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 1 files"));
        verify(mockOrchestrator, times(2)).run(any());
    }

    @Test
    void testProcessInbox_AggregatePhaseThrows() throws Exception {
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        Files.writeString(inboxRaw.resolve("msg.json"), "{}");

        KBResult processResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(processResult)
            .thenThrow(new RuntimeException("aggregate exploded"));

        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);

        // Aggregate failure must not fail the overall operation
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 1 files"));
        verify(mockOrchestrator, times(2)).run(any());
    }

    @Test
    void testProcessInbox_GenerateDescriptionsDisabled() throws Exception {
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        Files.writeString(inboxRaw.resolve("msg.json"), "{}");

        KBResult mockResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        String result = kbTools.kbProcessInbox(tempDir.toString(), "false", null);

        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 1 files"));
        // Only PROCESS_ONLY call, no AGGREGATE_ONLY
        verify(mockOrchestrator, times(1)).run(any());
    }

    @Test
    void testProcessInbox_SmartAggregationDisabled() throws Exception {
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        Files.writeString(inboxRaw.resolve("msg.json"), "{}");

        KBResult mockResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult);

        kbTools.kbProcessInbox(tempDir.toString(), "true", "false");

        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator, times(2)).run(paramsCaptor.capture());
        KBOrchestratorParams aggregateParams = paramsCaptor.getAllValues().get(1);
        assertEquals(KBProcessingMode.AGGREGATE_ONLY, aggregateParams.getProcessingMode());
        assertNull(aggregateParams.getSourceName());
        assertFalse(aggregateParams.isSmartAggregation());
    }

    @Test
    void testProcessInbox_OuterException() {
        // Exception in path resolution triggers the outer catch block
        when(mockPropertyReader.getValue("DMTOOLS_KB_OUTPUT_PATH"))
            .thenThrow(new RuntimeException("property read failed"));

        String result = kbTools.kbProcessInbox(null, null, null);

        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("property read failed"));
    }

    // ========== Helper Methods ==========

    /**
     * Create a mock KBResult for testing
     */
    private KBResult createMockResult(boolean success, String message, int questions, int answers, int notes) {
        KBResult result = mock(KBResult.class);
        when(result.isSuccess()).thenReturn(success);
        when(result.getMessage()).thenReturn(message);
        when(result.getQuestionsCount()).thenReturn(questions);
        when(result.getAnswersCount()).thenReturn(answers);
        when(result.getNotesCount()).thenReturn(notes);
        when(result.getTopicsCount()).thenReturn(0);
        when(result.getAreasCount()).thenReturn(0);
        when(result.getPeopleCount()).thenReturn(0);
        return result;
    }
}
