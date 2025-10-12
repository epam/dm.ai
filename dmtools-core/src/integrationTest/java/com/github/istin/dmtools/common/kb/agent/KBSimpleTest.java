package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.common.kb.*;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.prompt.PromptManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal integration test for KBOrchestrator with very simple test data
 * 
 * Test Data:
 * - Batch 1: 5 messages, 3 people (Alice, Bob, Charlie), 1 topic (Docker)
 * - Batch 2: 5 messages, same 3 people, same topic (Docker optimization)
 * 
 * Easy to verify all connections and relationships manually
 */
public class KBSimpleTest {

    private static final Logger logger = LogManager.getLogger(KBSimpleTest.class);

    private AI ai;
    private KBOrchestrator orchestrator;
    private Path tempDir;
    private Path inputFile1;
    private Path inputFile2;

    @BeforeEach
    void setUp() throws Exception {
        logger.info("=".repeat(80));
        logger.info("SETUP: KBSimpleTest - Minimal Test Data");
        logger.info("=".repeat(80));

        // Initialize components
        PropertyReader propertyReader = new PropertyReader();
        ConversationObserver observer = new ConversationObserver();
        ai = BasicGeminiAI.create(observer, propertyReader);

        KBAnalysisAgent analysisAgent = new KBAnalysisAgent(ai, new PromptManager());
        KBStructureBuilder structureBuilder = new KBStructureBuilder();
        KBAggregationAgent aggregationAgent = new KBAggregationAgent(ai, new PromptManager());
        KBQuestionAnswerMappingAgent qaMappingAgent = new KBQuestionAnswerMappingAgent(ai, new PromptManager());
        KBStatistics statistics = new KBStatistics();
        KBAnalysisResultMerger resultMerger = new KBAnalysisResultMerger(new ContentMergeAgent(ai, new PromptManager()));
        SourceConfigManager sourceConfigManager = new SourceConfigManager();
        ChunkPreparation chunkPreparation = new ChunkPreparation();

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

        // Use static directory in project's temp folder
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        tempDir = projectRoot.resolve("temp/kb_simple_test");

        // Clean directory before test
        if (Files.exists(tempDir)) {
            logger.info("Cleaning existing directory: {}", tempDir);
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder())
                     .forEach(path -> {
                         try {
                             Files.deleteIfExists(path);
                         } catch (IOException e) {
                             logger.warn("Failed to delete: {}", path, e);
                         }
                     });
            }
        }

        // Create fresh directory
        Files.createDirectories(tempDir);
        logger.info("Using static directory: {}", tempDir);

        // Create test input files
        inputFile1 = createFirstBatch();
        inputFile2 = createSecondBatch();
        logger.info("Created test input files");
        logger.info("");
    }

    @AfterEach
    void tearDown() {
        logger.info("");
        logger.info("Test completed. KB generated at: {}", tempDir);
        logger.info("=".repeat(80));
    }

    @Test
    void testSimpleKBBuild() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST 1: Build KB from First Batch (5 messages, 3 people, 1 topic: Docker)");
        logger.info("MODE: PROCESS_ONLY (no AI descriptions)");
        logger.info("=".repeat(80));

        // First batch: 5 messages about Docker
        KBOrchestratorParams params1 = new KBOrchestratorParams();
        params1.setSourceName("source_simple_test");
        params1.setInputFile(inputFile1.toString());
        params1.setDateTime("2024-10-10T12:00:00Z");
        params1.setOutputPath(tempDir.toString());
        params1.setProcessingMode(com.github.istin.dmtools.common.kb.model.KBProcessingMode.PROCESS_ONLY);

        logger.info("Building KB from first batch (PROCESS_ONLY mode)...");
        logger.info("  People: Alice, Bob, Charlie");
        logger.info("  Messages: 5 (2 questions, 3 answers/notes)");
        logger.info("  URLs: 2 (in answer and note)");
        KBResult result1 = orchestrator.run(params1);

        logger.info("✓ First batch processed");
        logger.info("  Themes: {}", result1.getThemesCount());
        logger.info("  Questions: {}", result1.getQuestionsCount());
        logger.info("  Answers: {}", result1.getAnswersCount());

        assertNotNull(result1);
        assertTrue(result1.isSuccess());

        logger.info("");
        logger.info("=".repeat(80));
        logger.info("TEST 2: Incremental Update (5 new messages, same 3 people, same topic)");
        logger.info("MODE: PROCESS_ONLY (no AI descriptions)");
        logger.info("=".repeat(80));

        // Second batch: 5 messages about Docker optimization (same topic)
        KBOrchestratorParams params2 = new KBOrchestratorParams();
        params2.setSourceName("source_simple_test");
        params2.setInputFile(inputFile2.toString());
        params2.setDateTime("2024-10-11T12:00:00Z");
        params2.setOutputPath(tempDir.toString());
        params2.setProcessingMode(com.github.istin.dmtools.common.kb.model.KBProcessingMode.PROCESS_ONLY);

        logger.info("Building KB from second batch (incremental, PROCESS_ONLY mode)...");
        logger.info("  People: Same 3 (Alice, Bob, Charlie)");
        logger.info("  Messages: 5 (1 question, 4 answers)");
        KBResult result2 = orchestrator.run(params2);

        logger.info("✓ Second batch processed");
        logger.info("  Themes: {}", result2.getThemesCount());
        logger.info("  Questions: {}", result2.getQuestionsCount());
        logger.info("  Answers: {}", result2.getAnswersCount());

        assertNotNull(result2);
        assertTrue(result2.isSuccess());

        logger.info("");
        logger.info("=".repeat(80));
        logger.info("✓ ALL TESTS PASSED - Inspect results at: {}", tempDir);
        logger.info("=".repeat(80));
    }

    /**
     * First batch: 5 messages about Docker, 3 people
     */
    private Path createFirstBatch() throws IOException {
        String testData = """
                [
                    {
                        "date": "2024-10-10T09:00:00Z",
                        "author": "Alice",
                        "body": "How do I create a Dockerfile for my Python app?"
                    },
                    {
                        "date": "2024-10-10T09:10:00Z",
                        "author": "Bob",
                        "body": "Start with FROM python:3.11-slim, then COPY your requirements.txt and RUN pip install -r requirements.txt. See https://docs.docker.com/engine/reference/builder/ for full Dockerfile reference."
                    },
                    {
                        "date": "2024-10-10T09:15:00Z",
                        "author": "Alice",
                        "body": "Thanks! What about the CMD instruction?"
                    },
                    {
                        "date": "2024-10-10T09:20:00Z",
                        "author": "Bob",
                        "body": "Use CMD ['python', 'app.py'] to run your application"
                    },
                    {
                        "date": "2024-10-10T09:30:00Z",
                        "author": "Charlie",
                        "body": "Don't forget to use .dockerignore to exclude unnecessary files like __pycache__ and .git. More info at https://docs.docker.com/build/building/context/#dockerignore-files"
                    }
                ]
                """;

        Path tempFile = Files.createTempFile(tempDir, "batch1_", ".json");
        Files.writeString(tempFile, testData);
        return tempFile;
    }

    /**
     * Second batch: 5 messages about Docker optimization (same topic), same 3 people
     */
    private Path createSecondBatch() throws IOException {
        String testData = """
                [
                    {
                        "date": "2024-10-11T09:00:00Z",
                        "author": "Charlie",
                        "body": "My Docker builds are really slow. How can I speed them up?"
                    },
                    {
                        "date": "2024-10-11T09:10:00Z",
                        "author": "Bob",
                        "body": "Use multi-stage builds to reduce image size. Put static dependencies first to leverage Docker's layer caching."
                    },
                    {
                        "date": "2024-10-11T09:20:00Z",
                        "author": "Alice",
                        "body": "Also make sure your .dockerignore file is properly configured to exclude node_modules, .git, and other large directories"
                    },
                    {
                        "date": "2024-10-11T09:30:00Z",
                        "author": "Charlie",
                        "body": "Great tips! Should I use BuildKit?"
                    },
                    {
                        "date": "2024-10-11T09:40:00Z",
                        "author": "Bob",
                        "body": "Yes, enable BuildKit with DOCKER_BUILDKIT=1. It provides parallel builds and better caching."
                    }
                ]
                """;

        Path tempFile = Files.createTempFile(tempDir, "batch2_", ".json");
        Files.writeString(tempFile, testData);
        return tempFile;
    }

    @Test
    void testAllProcessingModes() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: All Processing Modes (FULL, PROCESS_ONLY, AGGREGATE_ONLY)");
        logger.info("=".repeat(80));
        
        // Create SEPARATE directories for this test (not in main tempDir)
        Path modesTestRoot = Paths.get("temp/kb_modes_test");
        
        // Clean up completely
        if (Files.exists(modesTestRoot)) {
            try (Stream<Path> walk = Files.walk(modesTestRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
            }
        }
        
        Path modeTestDir = modesTestRoot.resolve("mode_test");
        
        // ========== TEST 1: FULL MODE ==========
        logger.info("\n" + "=".repeat(80));
        logger.info("MODE 1: FULL (complete processing with AI descriptions)");
        logger.info("=".repeat(80));
        
        KBOrchestratorParams fullParams = new KBOrchestratorParams();
        fullParams.setSourceName("source_simple_test");
        fullParams.setInputFile(inputFile1.toString());
        fullParams.setDateTime("2024-10-10T12:00:00Z");
        fullParams.setOutputPath(modeTestDir.toString());
        fullParams.setProcessingMode(com.github.istin.dmtools.common.kb.model.KBProcessingMode.FULL);
        
        logger.info("Building KB with FULL mode...");
        KBResult fullResult = orchestrator.run(fullParams);
        
        assertTrue(fullResult.isSuccess(), "FULL mode should succeed");
        assertTrue(fullResult.getQuestionsCount() > 0, "FULL mode should create questions");
        assertTrue(fullResult.getAnswersCount() > 0, "FULL mode should create answers");
        assertTrue(fullResult.getPeopleCount() > 0, "FULL mode should create people profiles");
        
        // Verify AI descriptions were generated
        Path aliceDescFile = modeTestDir.resolve("people/Alice/Alice-desc.md");
        assertTrue(Files.exists(aliceDescFile), "FULL mode should generate person descriptions");
        String aliceDesc = Files.readString(aliceDescFile);
        assertTrue(aliceDesc.contains("AI_CONTENT_START"), "Person description should have AI content");
        
        logger.info("FULL mode result: Q={}, A={}, N={}, People={}, Topics={}", 
                   fullResult.getQuestionsCount(),
                   fullResult.getAnswersCount(),
                   fullResult.getNotesCount(),
                   fullResult.getPeopleCount(),
                   fullResult.getTopicsCount());
        
        // ========== TEST 2: PROCESS_ONLY MODE ==========
        logger.info("\n" + "=".repeat(80));
        logger.info("MODE 2: PROCESS_ONLY (fast processing without AI descriptions)");
        logger.info("=".repeat(80));
        
        // Use separate directory for PROCESS_ONLY test
        Path processOnlyDir = modesTestRoot.resolve("process_only_test");
        
        KBOrchestratorParams processOnlyParams = new KBOrchestratorParams();
        processOnlyParams.setSourceName("source_simple_test");
        processOnlyParams.setInputFile(inputFile1.toString());
        processOnlyParams.setDateTime("2024-10-10T12:00:00Z");
        processOnlyParams.setOutputPath(processOnlyDir.toString());
        processOnlyParams.setProcessingMode(com.github.istin.dmtools.common.kb.model.KBProcessingMode.PROCESS_ONLY);
        
        logger.info("Building KB with PROCESS_ONLY mode...");
        KBResult processOnlyResult = orchestrator.run(processOnlyParams);
        
        assertTrue(processOnlyResult.isSuccess(), "PROCESS_ONLY mode should succeed");
        assertTrue(processOnlyResult.getQuestionsCount() > 0, "PROCESS_ONLY mode should create questions");
        assertTrue(processOnlyResult.getAnswersCount() > 0, "PROCESS_ONLY mode should create answers");
        assertTrue(processOnlyResult.getPeopleCount() > 0, "PROCESS_ONLY mode should create people profiles");
        
        // Verify AI descriptions were NOT generated
        Path aliceDescFileProcessOnly = processOnlyDir.resolve("people/Alice/Alice-desc.md");
        assertFalse(Files.exists(aliceDescFileProcessOnly), 
                   "PROCESS_ONLY mode should NOT generate person descriptions");
        
        // Verify structure exists
        Path aliceFileProcessOnly = processOnlyDir.resolve("people/Alice/Alice.md");
        assertTrue(Files.exists(aliceFileProcessOnly), 
                  "PROCESS_ONLY mode should create person profile structure");
        
        logger.info("PROCESS_ONLY mode result: Q={}, A={}, N={}, People={}, Topics={}", 
                   processOnlyResult.getQuestionsCount(),
                   processOnlyResult.getAnswersCount(),
                   processOnlyResult.getNotesCount(),
                   processOnlyResult.getPeopleCount(),
                   processOnlyResult.getTopicsCount());
        
        // ========== TEST 3: AGGREGATE_ONLY MODE ==========
        logger.info("\n" + "=".repeat(80));
        logger.info("MODE 3: AGGREGATE_ONLY (generate AI descriptions for existing KB)");
        logger.info("=".repeat(80));
        
        KBOrchestratorParams aggregateParams = new KBOrchestratorParams();
        aggregateParams.setSourceName("source_simple_test");
        aggregateParams.setOutputPath(processOnlyDir.toString());  // Use existing KB from PROCESS_ONLY
        aggregateParams.setProcessingMode(com.github.istin.dmtools.common.kb.model.KBProcessingMode.AGGREGATE_ONLY);
        
        logger.info("Generating AI descriptions with AGGREGATE_ONLY mode...");
        KBResult aggregateResult = orchestrator.run(aggregateParams);
        
        assertTrue(aggregateResult.isSuccess(), "AGGREGATE_ONLY mode should succeed");
        
        // Now AI descriptions should exist
        Path aliceDescFileAfterAggregate = processOnlyDir.resolve("people/Alice/Alice-desc.md");
        assertTrue(Files.exists(aliceDescFileAfterAggregate), 
                  "AGGREGATE_ONLY mode should generate person descriptions");
        String aliceDescAfter = Files.readString(aliceDescFileAfterAggregate);
        assertTrue(aliceDescAfter.contains("AI_CONTENT_START"), 
                  "Person description should have AI content after AGGREGATE_ONLY");
        
        logger.info("AGGREGATE_ONLY mode result: Q={}, A={}, N={}, People={}, Topics={}", 
                   aggregateResult.getQuestionsCount(),
                   aggregateResult.getAnswersCount(),
                   aggregateResult.getNotesCount(),
                   aggregateResult.getPeopleCount(),
                   aggregateResult.getTopicsCount());
        
        // ========== VERIFICATION ==========
        logger.info("\n" + "=".repeat(80));
        logger.info("VERIFICATION: All modes should produce equivalent final results");
        logger.info("=".repeat(80));
        
        // Compare FULL mode with (PROCESS_ONLY + AGGREGATE_ONLY)
        assertEquals(fullResult.getQuestionsCount(), aggregateResult.getQuestionsCount(), 
                    "Question count should match");
        assertEquals(fullResult.getAnswersCount(), aggregateResult.getAnswersCount(), 
                    "Answer count should match");
        assertEquals(fullResult.getPeopleCount(), aggregateResult.getPeopleCount(), 
                    "People count should match");
        
        logger.info("✅ All processing modes work correctly!");
        logger.info("  - FULL mode: Complete processing with descriptions");
        logger.info("  - PROCESS_ONLY mode: Fast processing without descriptions");
        logger.info("  - AGGREGATE_ONLY mode: Generate descriptions for existing KB");
        logger.info("  - PROCESS_ONLY + AGGREGATE_ONLY = FULL (equivalent results)");
    }
}

