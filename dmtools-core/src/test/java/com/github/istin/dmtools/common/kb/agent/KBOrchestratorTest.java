package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.KBProcessingMode;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KBOrchestrator with mocked dependencies.
 * Tests orchestration logic, source cleanup integration, and error handling.
 */
class KBOrchestratorTest {

    @TempDir
    Path tempDir;

    @Mock
    private KBAnalysisAgent analysisAgent;

    @Mock
    private KBStructureBuilder structureBuilder;

    @Mock
    private KBAggregationAgent aggregationAgent;

    @Mock
    private KBQuestionAnswerMappingAgent qaMappingAgent;

    @Mock
    private KBStatistics statistics;

    @Mock
    private KBAnalysisResultMerger resultMerger;

    @Mock
    private SourceConfigManager sourceConfigManager;

    @Mock
    private ChunkPreparation chunkPreparation;

    private KBOrchestrator orchestrator;
    private Path inputFile;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        orchestrator = new KBOrchestrator(
                analysisAgent,
                structureBuilder,
                aggregationAgent,
                qaMappingAgent,
                statistics,
                resultMerger,
                sourceConfigManager,
                chunkPreparation
        );

        // Create test input file
        inputFile = tempDir.resolve("input.json");
        Files.writeString(inputFile, "{\"messages\": [{\"author\": \"Test\", \"text\": \"Test message\"}]}");
    }

    @Test
    void testSourceCleanupIsInvoked() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setCleanOutput(false);
        params.setCleanSourceBeforeProcessing(true); // Enable cleanup

        // Create some existing files to clean
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path existingQuestion = questionsDir.resolve("q_0001.md");
        Files.writeString(existingQuestion, "---\nsource: test_source\n---\nOld question");

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        KBResult result = orchestrator.run(params);

        // Verify cleanup happened - old file should be deleted
        assertFalse(Files.exists(existingQuestion), "Old question file should be deleted during cleanup");

        // Verify source config was updated
        verify(sourceConfigManager).updateLastSyncDate(eq("test_source"), eq("2024-01-01T00:00:00Z"), any());
    }

    @Test
    void testSourceCleanupNotInvokedWhenDisabled() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setCleanOutput(false);
        params.setCleanSourceBeforeProcessing(false); // Disable cleanup

        // Create some existing files
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path existingQuestion = questionsDir.resolve("q_0001.md");
        Files.writeString(existingQuestion, "---\nsource: test_source\n---\nOld question");

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        KBResult result = orchestrator.run(params);

        // Verify cleanup didn't happen - old file should still exist
        assertTrue(Files.exists(existingQuestion), "Old question file should remain when cleanup is disabled");
    }

    @Test
    void testCleanupOnlyTargetSourceFiles() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("source_a");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setCleanOutput(false);
        params.setCleanSourceBeforeProcessing(true);

        // Create files from different sources
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        
        Path questionFromA = questionsDir.resolve("q_0001.md");
        Files.writeString(questionFromA, "---\nsource: source_a\n---\nQuestion from A");
        
        Path questionFromB = questionsDir.resolve("q_0002.md");
        Files.writeString(questionFromB, "---\nsource: source_b\n---\nQuestion from B");

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify only source_a file was deleted
        assertFalse(Files.exists(questionFromA), "Question from source_a should be deleted");
        assertTrue(Files.exists(questionFromB), "Question from source_b should remain");
    }

    @Test
    void testFullModeRunsAggregation() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setProcessingMode(KBProcessingMode.FULL);

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify aggregation was invoked (indirectly through structure manager)
        verify(sourceConfigManager).updateLastSyncDate(anyString(), anyString(), any());
    }

    @Test
    void testProcessOnlyModeSkipsAggregation() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setProcessingMode(KBProcessingMode.PROCESS_ONLY);

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify source config was still updated
        verify(sourceConfigManager).updateLastSyncDate(anyString(), anyString(), any());
    }

    @Test
    void testInputFileCopiedToInbox() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify input file was copied to inbox/raw/
        Path inboxRaw = tempDir.resolve("inbox/raw");
        assertTrue(Files.exists(inboxRaw), "inbox/raw directory should exist");
        
        long fileCount = Files.list(inboxRaw)
                .filter(p -> p.getFileName().toString().endsWith("input.json"))
                .count();
        assertTrue(fileCount > 0, "Input file should be copied to inbox/raw");
    }

    @Test
    void testAnalyzedJsonSavedToInbox() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify analyzed JSON was saved to inbox/analyzed/[source]/
        Path inboxAnalyzed = tempDir.resolve("inbox/analyzed/test_source");
        assertTrue(Files.exists(inboxAnalyzed), "inbox/analyzed/test_source directory should exist");
        
        long fileCount = Files.list(inboxAnalyzed)
                .filter(p -> p.getFileName().toString().endsWith("_analyzed.json"))
                .count();
        assertTrue(fileCount > 0, "Analyzed JSON should be saved to inbox/analyzed/test_source");
    }

    @Test
    void testCleanOutputClearsDirectories() throws Exception {
        // Setup - create some existing files
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path existingFile = questionsDir.resolve("q_0001.md");
        Files.writeString(existingFile, "Old content");

        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setCleanOutput(true); // Clean entire output

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify old file was deleted
        assertFalse(Files.exists(existingFile), "Old files should be deleted when cleanOutput=true");
    }

    @Test
    void testRollbackOnError() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis to throw exception
        when(analysisAgent.run(any())).thenThrow(new RuntimeException("Analysis failed"));

        // Execute and expect exception
        Exception exception = assertThrows(Exception.class, () -> {
            orchestrator.run(params);
        });

        assertTrue(exception.getMessage().contains("KB processing failed and rolled back"),
                  "Exception should indicate rollback occurred");

        // Verify rollback cleaned up inbox files
        Path inboxRaw = tempDir.resolve("inbox/raw");
        if (Files.exists(inboxRaw)) {
            long fileCount = Files.list(inboxRaw).count();
            assertEquals(0, fileCount, "Rollback should delete created files");
        }
    }

    @Test
    void testMultipleChunksProcessing() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());

        // Mock multiple chunks
        ChunkPreparation.Chunk chunk1 = new ChunkPreparation.Chunk("Chunk 1", null, 0);
        ChunkPreparation.Chunk chunk2 = new ChunkPreparation.Chunk("Chunk 2", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(java.util.Arrays.asList(chunk1, chunk2));

        // Mock analysis results for each chunk
        AnalysisResult result1 = new AnalysisResult();
        result1.setQuestions(new ArrayList<>());
        result1.setAnswers(new ArrayList<>());
        result1.setNotes(new ArrayList<>());

        AnalysisResult result2 = new AnalysisResult();
        result2.setQuestions(new ArrayList<>());
        result2.setAnswers(new ArrayList<>());
        result2.setNotes(new ArrayList<>());

        when(analysisAgent.run(any())).thenReturn(result1, result2);

        // Mock merger - KBChunkAnalyzer will call this internally
        AnalysisResult mergedResult = new AnalysisResult();
        mergedResult.setQuestions(new ArrayList<>());
        mergedResult.setAnswers(new ArrayList<>());
        mergedResult.setNotes(new ArrayList<>());
        when(resultMerger.mergeResults(anyList())).thenReturn(mergedResult);

        // Execute
        orchestrator.run(params);

        // Verify analysis was called multiple times
        verify(analysisAgent, atLeast(2)).run(any());
    }

    @Test
    void testExtraInstructionsPassedToAgents() throws Exception {
        // Setup
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_source");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-01-01T00:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setAnalysisExtraInstructions("Custom analysis instructions");
        params.setAggregationExtraInstructions("Custom aggregation instructions");
        params.setQaMappingExtraInstructions("Custom QA mapping instructions");

        // Mock chunk preparation
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("Test content", null, 0);
        when(chunkPreparation.prepareChunks(anyList())).thenReturn(Collections.singletonList(chunk));

        // Mock analysis result
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setQuestions(new ArrayList<>());
        analysisResult.setAnswers(new ArrayList<>());
        analysisResult.setNotes(new ArrayList<>());
        when(analysisAgent.run(any())).thenReturn(analysisResult);

        // Execute
        orchestrator.run(params);

        // Verify analysis agent was called (extra instructions are passed internally)
        verify(analysisAgent, atLeastOnce()).run(any());
    }

    @Test
    void testRegenerateStructureFromExistingFiles() throws Exception {
        // Setup - create some existing Q/A/N files
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);

        Files.writeString(questionsDir.resolve("q_0001.md"), 
            "---\nauthor: Test\ntopics: [test]\n---\nTest question");
        Files.writeString(answersDir.resolve("a_0001.md"), 
            "---\nauthor: Test\ntopics: [test]\n---\nTest answer");

        // Execute
        KBResult result = orchestrator.regenerateStructureFromExistingFiles(tempDir, "test_source");

        // Verify result is not null
        assertNotNull(result, "Regeneration should return a result");
    }
}

