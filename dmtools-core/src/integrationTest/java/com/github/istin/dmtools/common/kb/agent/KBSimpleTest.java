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
        KBStatistics statistics = new KBStatistics();
        KBAnalysisResultMerger resultMerger = new KBAnalysisResultMerger(new ContentMergeAgent(ai, new PromptManager()));
        SourceConfigManager sourceConfigManager = new SourceConfigManager();
        ChunkPreparation chunkPreparation = new ChunkPreparation();

        orchestrator = new KBOrchestrator();
        orchestrator.analysisAgent = analysisAgent;
        orchestrator.structureBuilder = structureBuilder;
        orchestrator.aggregationAgent = aggregationAgent;
        orchestrator.statistics = statistics;
        orchestrator.resultMerger = resultMerger;
        orchestrator.sourceConfigManager = sourceConfigManager;
        orchestrator.chunkPreparation = chunkPreparation;

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
        logger.info("=".repeat(80));

        // First batch: 5 messages about Docker
        KBOrchestratorParams params1 = new KBOrchestratorParams();
        params1.setSourceName("simple_test");
        params1.setInputFile(inputFile1.toString());
        params1.setDateTime("2024-10-10T12:00:00Z");
        params1.setOutputPath(tempDir.toString());

        logger.info("Building KB from first batch...");
        logger.info("  People: Alice, Bob, Charlie");
        logger.info("  Messages: 5 (2 questions, 3 answers/notes)");
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
        logger.info("=".repeat(80));

        // Second batch: 5 messages about Docker optimization (same topic)
        KBOrchestratorParams params2 = new KBOrchestratorParams();
        params2.setSourceName("simple_test");
        params2.setInputFile(inputFile2.toString());
        params2.setDateTime("2024-10-11T12:00:00Z");
        params2.setOutputPath(tempDir.toString());

        logger.info("Building KB from second batch (incremental)...");
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
                        "body": "Start with FROM python:3.11-slim, then COPY your requirements.txt and RUN pip install -r requirements.txt"
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
                        "body": "Don't forget to use .dockerignore to exclude unnecessary files like __pycache__ and .git"
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
}

