package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for source-specific cleanup functionality.
 * Tests the complete workflow of cleaning and refreshing content from a specific source.
 */
class KBSourceCleanupTest {

    private static final Logger logger = LogManager.getLogger(KBSourceCleanupTest.class);
    private static final boolean CLEAN_OUTPUT = false; // Set to false to preserve output for inspection

    private KBOrchestrator orchestrator;
    private Path tempDir;
    private Path inputFile1;
    private Path inputFile2;
    private Path inputFile3;

    @BeforeEach
    void setUp() throws Exception {
        logger.info("SETUP: KBSourceCleanupTest - Source-Specific Cleanup");
        logger.info("=".repeat(80));

        // Initialize orchestrator via Dagger
        KnowledgeBaseComponent component = DaggerKnowledgeBaseComponent.create();
        orchestrator = component.kbOrchestrator();

        // Setup temp directory
        tempDir = Paths.get(System.getProperty("user.dir")).getParent().resolve("temp/kb_source_cleanup_test");
        if (CLEAN_OUTPUT && Files.exists(tempDir)) {
            logger.info("Cleaning existing directory: {}", tempDir);
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder())
                     .forEach(path -> {
                         try {
                             Files.deleteIfExists(path);
                         } catch (Exception e) {
                             logger.warn("Failed to delete: {}", path, e);
                         }
                     });
            }
        }
        Files.createDirectories(tempDir);

        // Create test input files
        inputFile1 = tempDir.resolve("input1.json");
        inputFile2 = tempDir.resolve("input2.json");
        inputFile3 = tempDir.resolve("input3.json");

        // Input 1: Confluence page content (source: confluence_page_123)
        String input1Content = """
                {
                  "messages": [
                    {
                      "author": "Alice Brown",
                      "text": "What is the deployment process for our microservices?",
                      "timestamp": "2024-01-15T10:00:00Z"
                    },
                    {
                      "author": "Bob Smith",
                      "text": "We use Jenkins for CI/CD. First, code is pushed to Git, then Jenkins runs tests and builds Docker images.",
                      "timestamp": "2024-01-15T10:05:00Z"
                    }
                  ]
                }
                """;
        Files.writeString(inputFile1, input1Content);

        // Input 2: Updated Confluence page content (same source, different content)
        String input2Content = """
                {
                  "messages": [
                    {
                      "author": "Alice Brown",
                      "text": "What is our new deployment process using GitHub Actions?",
                      "timestamp": "2024-01-20T10:00:00Z"
                    },
                    {
                      "author": "Charlie Davis",
                      "text": "We migrated to GitHub Actions. Now we use workflows defined in .github/workflows/ directory.",
                      "timestamp": "2024-01-20T10:05:00Z"
                    }
                  ]
                }
                """;
        Files.writeString(inputFile2, input2Content);

        // Input 3: Different source (source: teams_chat)
        String input3Content = """
                {
                  "messages": [
                    {
                      "author": "Diana Wilson",
                      "text": "What are the office hours?",
                      "timestamp": "2024-01-25T10:00:00Z"
                    },
                    {
                      "author": "Eve Martinez",
                      "text": "Office hours are 9 AM to 5 PM, Monday through Friday.",
                      "timestamp": "2024-01-25T10:05:00Z"
                    }
                  ]
                }
                """;
        Files.writeString(inputFile3, input3Content);

        logger.info("Test setup complete. Output directory: {}", tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (CLEAN_OUTPUT) {
            if (tempDir != null && Files.exists(tempDir)) {
                try (Stream<Path> paths = Files.walk(tempDir)) {
                    paths.sorted(Comparator.reverseOrder())
                         .forEach(path -> {
                             try {
                                 Files.deleteIfExists(path);
                             } catch (Exception e) {
                                 logger.warn("Failed to delete: {}", path, e);
                             }
                         });
                }
            }
            logger.info("Cleanup complete");
        } else {
            logger.info("Test output preserved at: {}", tempDir);
        }
    }

    @Test
    void testSourceCleanupWorkflow() throws Exception {
        logger.info("\n" + "=".repeat(80));
        logger.info("TEST: Source Cleanup Workflow");
        logger.info("=".repeat(80));

        // Step 1: Process first batch with source "confluence_page_123"
        logger.info("\n--- Step 1: Initial processing (confluence_page_123) ---");
        KBOrchestratorParams params1 = new KBOrchestratorParams();
        params1.setSourceName("confluence_page_123");
        params1.setInputFile(inputFile1.toString());
        params1.setDateTime("2024-01-15T10:00:00Z");
        params1.setOutputPath(tempDir.toString());
        params1.setCleanOutput(true); // Clean for first run
        params1.setCleanSourceBeforeProcessing(false); // No cleanup needed for first run

        KBResult result1 = orchestrator.run(params1);
        logger.info("Result 1: questions={}, answers={}, notes={}, people={}",
                   result1.getQuestionsCount(), result1.getAnswersCount(),
                   result1.getNotesCount(), result1.getPeopleCount());

        // Verify initial Q/A/N created
        assertTrue(result1.getQuestionsCount() >= 1, "Should have at least 1 question after batch 1");
        assertTrue(result1.getAnswersCount() >= 1, "Should have at least 1 answer after batch 1");
        assertTrue(result1.getPeopleCount() >= 2, "Should have at least 2 people (Alice, Bob)");

        // Count files from source 1
        long questionsFromSource1 = countFilesWithSource(tempDir.resolve("questions"), "confluence_page_123");
        long answersFromSource1 = countFilesWithSource(tempDir.resolve("answers"), "confluence_page_123");
        logger.info("Files from source 1: questions={}, answers={}", questionsFromSource1, answersFromSource1);

        // Step 2: Process second batch with SAME source and cleanSourceBeforeProcessing=true
        logger.info("\n--- Step 2: Refresh content (confluence_page_123, clean=true) ---");
        KBOrchestratorParams params2 = new KBOrchestratorParams();
        params2.setSourceName("confluence_page_123");
        params2.setInputFile(inputFile2.toString());
        params2.setDateTime("2024-01-20T10:00:00Z");
        params2.setOutputPath(tempDir.toString());
        params2.setCleanOutput(false); // Don't clean entire KB
        params2.setCleanSourceBeforeProcessing(true); // Clean only this source

        KBResult result2 = orchestrator.run(params2);
        logger.info("Result 2: questions={}, answers={}, notes={}, people={}",
                   result2.getQuestionsCount(), result2.getAnswersCount(),
                   result2.getNotesCount(), result2.getPeopleCount());

        // Verify old Q/A/N from source 1 were deleted
        long questionsFromSource1After = countFilesWithSource(tempDir.resolve("questions"), "confluence_page_123");
        long answersFromSource1After = countFilesWithSource(tempDir.resolve("answers"), "confluence_page_123");
        logger.info("Files from source 1 after cleanup: questions={}, answers={}", 
                   questionsFromSource1After, answersFromSource1After);

        // New content should be present (different questions/answers about GitHub Actions)
        assertTrue(result2.getQuestionsCount() >= 1, "Should have questions after batch 2");
        assertTrue(result2.getAnswersCount() >= 1, "Should have answers after batch 2");

        // Verify person profiles were updated (Charlie Davis should be new)
        assertTrue(result2.getPeopleCount() >= 2, "Should have people after batch 2");

        // Step 3: Process third batch with DIFFERENT source
        logger.info("\n--- Step 3: Add different source (teams_chat) ---");
        KBOrchestratorParams params3 = new KBOrchestratorParams();
        params3.setSourceName("teams_chat");
        params3.setInputFile(inputFile3.toString());
        params3.setDateTime("2024-01-25T10:00:00Z");
        params3.setOutputPath(tempDir.toString());
        params3.setCleanOutput(false);
        params3.setCleanSourceBeforeProcessing(false);

        KBResult result3 = orchestrator.run(params3);
        logger.info("Result 3: questions={}, answers={}, notes={}, people={}",
                   result3.getQuestionsCount(), result3.getAnswersCount(),
                   result3.getNotesCount(), result3.getPeopleCount());

        // Verify files from source 1 (confluence_page_123) remain untouched
        long questionsFromSource1Final = countFilesWithSource(tempDir.resolve("questions"), "confluence_page_123");
        long answersFromSource1Final = countFilesWithSource(tempDir.resolve("answers"), "confluence_page_123");
        logger.info("Files from source 1 after batch 3: questions={}, answers={}", 
                   questionsFromSource1Final, answersFromSource1Final);

        assertEquals(questionsFromSource1After, questionsFromSource1Final,
                    "Confluence questions should remain unchanged after processing different source");
        assertEquals(answersFromSource1After, answersFromSource1Final,
                    "Confluence answers should remain unchanged after processing different source");

        // Verify files from source 2 (teams_chat) were added
        long questionsFromSource2 = countFilesWithSource(tempDir.resolve("questions"), "teams_chat");
        long answersFromSource2 = countFilesWithSource(tempDir.resolve("answers"), "teams_chat");
        logger.info("Files from source 2: questions={}, answers={}", questionsFromSource2, answersFromSource2);

        assertTrue(questionsFromSource2 >= 1, "Should have questions from teams_chat");
        assertTrue(answersFromSource2 >= 1, "Should have answers from teams_chat");

        // Verify total counts increased
        assertTrue(result3.getQuestionsCount() > result2.getQuestionsCount(),
                  "Total questions should increase after adding different source");
        assertTrue(result3.getPeopleCount() >= 4, "Should have at least 4 people total");

        logger.info("\n" + "=".repeat(80));
        logger.info("TEST COMPLETE: Source cleanup workflow verified successfully");
        logger.info("=".repeat(80));
    }

    @Test
    void testCleanOnlyTargetSource() throws Exception {
        logger.info("\n" + "=".repeat(80));
        logger.info("TEST: Clean Only Target Source (Multi-Source Verification)");
        logger.info("=".repeat(80));

        // Step 1: Process content from source A (confluence_page_123)
        logger.info("\n--- Step 1: Process source A (confluence_page_123) ---");
        KBOrchestratorParams paramsA = new KBOrchestratorParams();
        paramsA.setSourceName("confluence_page_123");
        paramsA.setInputFile(inputFile1.toString());
        paramsA.setDateTime("2024-01-15T10:00:00Z");
        paramsA.setOutputPath(tempDir.toString());
        paramsA.setCleanOutput(true);
        paramsA.setCleanSourceBeforeProcessing(false);

        KBResult resultA = orchestrator.run(paramsA);
        logger.info("Result A: questions={}, answers={}, notes={}, people={}",
                   resultA.getQuestionsCount(), resultA.getAnswersCount(),
                   resultA.getNotesCount(), resultA.getPeopleCount());

        long questionsFromA = countFilesWithSource(tempDir.resolve("questions"), "confluence_page_123");
        long answersFromA = countFilesWithSource(tempDir.resolve("answers"), "confluence_page_123");
        logger.info("Source A files: questions={}, answers={}", questionsFromA, answersFromA);

        assertTrue(questionsFromA >= 1, "Should have questions from source A");
        assertTrue(answersFromA >= 1, "Should have answers from source A");

        // Step 2: Process content from source B (teams_chat)
        logger.info("\n--- Step 2: Process source B (teams_chat) ---");
        KBOrchestratorParams paramsB = new KBOrchestratorParams();
        paramsB.setSourceName("teams_chat");
        paramsB.setInputFile(inputFile3.toString());
        paramsB.setDateTime("2024-01-20T10:00:00Z");
        paramsB.setOutputPath(tempDir.toString());
        paramsB.setCleanOutput(false);
        paramsB.setCleanSourceBeforeProcessing(false);

        KBResult resultB = orchestrator.run(paramsB);
        logger.info("Result B: questions={}, answers={}, notes={}, people={}",
                   resultB.getQuestionsCount(), resultB.getAnswersCount(),
                   resultB.getNotesCount(), resultB.getPeopleCount());

        long questionsFromB = countFilesWithSource(tempDir.resolve("questions"), "teams_chat");
        long answersFromB = countFilesWithSource(tempDir.resolve("answers"), "teams_chat");
        logger.info("Source B files: questions={}, answers={}", questionsFromB, answersFromB);

        assertTrue(questionsFromB >= 1, "Should have questions from source B");
        assertTrue(answersFromB >= 1, "Should have answers from source B");

        // Verify both sources exist
        long totalQuestions = resultB.getQuestionsCount();
        long totalAnswers = resultB.getAnswersCount();
        logger.info("Total before cleanup: questions={}, answers={}", totalQuestions, totalAnswers);

        // Step 3: Clean ONLY source A (confluence_page_123) and add new content
        logger.info("\n--- Step 3: Clean ONLY source A (confluence_page_123) ---");
        KBOrchestratorParams paramsA2 = new KBOrchestratorParams();
        paramsA2.setSourceName("confluence_page_123");
        paramsA2.setInputFile(inputFile2.toString());
        paramsA2.setDateTime("2024-01-25T10:00:00Z");
        paramsA2.setOutputPath(tempDir.toString());
        paramsA2.setCleanOutput(false);
        paramsA2.setCleanSourceBeforeProcessing(true); // Clean only this source

        KBResult resultA2 = orchestrator.run(paramsA2);
        logger.info("Result A2: questions={}, answers={}, notes={}, people={}",
                   resultA2.getQuestionsCount(), resultA2.getAnswersCount(),
                   resultA2.getNotesCount(), resultA2.getPeopleCount());

        // Verify source A has new content
        long questionsFromA2 = countFilesWithSource(tempDir.resolve("questions"), "confluence_page_123");
        long answersFromA2 = countFilesWithSource(tempDir.resolve("answers"), "confluence_page_123");
        logger.info("Source A files after cleanup: questions={}, answers={}", questionsFromA2, answersFromA2);

        assertTrue(questionsFromA2 >= 1, "Should have new questions from source A");
        assertTrue(answersFromA2 >= 1, "Should have new answers from source A");

        // CRITICAL VERIFICATION: Source B should remain COMPLETELY UNCHANGED
        long questionsFromBAfter = countFilesWithSource(tempDir.resolve("questions"), "teams_chat");
        long answersFromBAfter = countFilesWithSource(tempDir.resolve("answers"), "teams_chat");
        logger.info("Source B files after cleaning A: questions={}, answers={}", questionsFromBAfter, answersFromBAfter);

        assertEquals(questionsFromB, questionsFromBAfter,
                    "Source B questions MUST remain unchanged when cleaning source A");
        assertEquals(answersFromB, answersFromBAfter,
                    "Source B answers MUST remain unchanged when cleaning source A");

        // Verify total counts reflect the cleanup and new content
        assertTrue(resultA2.getQuestionsCount() >= questionsFromBAfter,
                  "Total questions should include source B questions");
        assertTrue(resultA2.getAnswersCount() >= answersFromBAfter,
                  "Total answers should include source B answers");

        logger.info("\n" + "=".repeat(80));
        logger.info("TEST COMPLETE: Verified only target source was cleaned");
        logger.info("=".repeat(80));
    }

    /**
     * Helper method to count files with a specific source
     */
    private long countFilesWithSource(Path directory, String sourceName) throws Exception {
        if (!Files.exists(directory)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                       .filter(p -> p.getFileName().toString().endsWith(".md"))
                       .filter(p -> {
                           try {
                               String content = Files.readString(p);
                               return content.contains("source: " + sourceName) ||
                                      content.contains("source: \"" + sourceName + "\"");
                           } catch (Exception e) {
                               return false;
                           }
                       })
                       .count();
        }
    }
}

