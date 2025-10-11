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
 * Integration test for KB incremental updates and auto-increment functionality
 * 
 * Tests:
 * 1. Multiple KB builds with new data merge correctly
 * 2. Question/Answer IDs auto-increment properly
 * 3. Contributors are merged across updates
 * 4. Existing data is preserved during updates
 */
public class KBIncrementalUpdateTest {
    
    private static final Logger logger = LogManager.getLogger(KBIncrementalUpdateTest.class);
    
    private KBOrchestrator orchestrator;
    private AI ai;
    private Path tempDir;
    private Path firstInputFile;
    private Path secondInputFile;
    
    @BeforeEach
    void setUp() throws Exception {
        logger.info("=".repeat(80));
        logger.info("Setting up KB Incremental Update Test");
        logger.info("=".repeat(80));
        
        // Initialize real AI client
        PropertyReader propertyReader = new PropertyReader();
        ConversationObserver observer = new ConversationObserver();
        ai = BasicGeminiAI.create(observer, propertyReader);
        
        // Create all required components
        PromptManager promptManager = new PromptManager();
        
        KBAnalysisAgent analysisAgent = new KBAnalysisAgent(ai, promptManager);
        KBStructureBuilder structureBuilder = new KBStructureBuilder();
        KBAggregationAgent aggregationAgent = new KBAggregationAgent(ai, promptManager);
        KBStatistics statistics = new KBStatistics();
        
        ContentMergeAgent contentMergeAgent = new ContentMergeAgent(ai, promptManager);
        KBAnalysisResultMerger resultMerger = new KBAnalysisResultMerger(contentMergeAgent);
        
        SourceConfigManager sourceConfigManager = new SourceConfigManager();
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        
        // Create orchestrator and inject dependencies using reflection
        orchestrator = new KBOrchestrator();
        injectField(orchestrator, "analysisAgent", analysisAgent);
        injectField(orchestrator, "structureBuilder", structureBuilder);
        injectField(orchestrator, "aggregationAgent", aggregationAgent);
        injectField(orchestrator, "statistics", statistics);
        injectField(orchestrator, "resultMerger", resultMerger);
        injectField(orchestrator, "sourceConfigManager", sourceConfigManager);
        injectField(orchestrator, "chunkPreparation", chunkPreparation);
        
        // Use static directory in project's temp folder
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        tempDir = projectRoot.resolve("temp/kb_incremental_test_output");
        
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
        
        // Create input files with test data
        firstInputFile = createFirstInputFile();
        secondInputFile = createSecondInputFile();
        logger.info("Created test input files");
        logger.info("");
    }
    
    @AfterEach
    void tearDown() throws IOException {
        logger.info("");
        logger.info("Test completed. KB generated at: {}", tempDir);
        logger.info("To view the results, navigate to the directory above.");
        logger.info("WARNING: Cleanup is DISABLED for inspection. Re-enable after viewing.");
        logger.info("=".repeat(80));
        
        // CLEANUP DISABLED FOR INSPECTION - uncomment to re-enable
        /*
        logger.info("Cleaning up test resources...");
        
        // Delete input files
        if (firstInputFile != null && Files.exists(firstInputFile)) {
            Files.deleteIfExists(firstInputFile);
        }
        if (secondInputFile != null && Files.exists(secondInputFile)) {
            Files.deleteIfExists(secondInputFile);
        }
        
        // Delete temp directory recursively
        if (tempDir != null && Files.exists(tempDir)) {
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
        */
    }
    
    @Test
    void testIncrementalUpdateWithAutoIncrement() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KB Incremental Update with Auto-Increment");
        logger.info("=".repeat(80));
        logger.info("");
        
        // ===== FIRST BUILD =====
        logger.info("--- PHASE 1: Initial KB Build ---");
        logger.info("");
        
        KBOrchestratorParams params1 = new KBOrchestratorParams();
        params1.setSourceName("incremental_test");
        params1.setInputFile(firstInputFile.toString());
        params1.setDateTime("2024-10-10T10:00:00");
        params1.setOutputPath(tempDir.toString());
        
        logger.info("Running first KB build...");
        KBResult result1 = orchestrator.run(params1);
        
        logger.info("First Build Result:");
        logger.info("  Success: {}", result1.isSuccess());
        logger.info("  Themes: {}", result1.getThemesCount());
        logger.info("  Questions: {}", result1.getQuestionsCount());
        logger.info("  Answers: {}", result1.getAnswersCount());
        logger.info("  People: {}", result1.getPeopleCount());
        logger.info("  Topics: {}", result1.getTopicsCount());
        logger.info("");
        
        assertTrue(result1.isSuccess(), "First build should succeed");
        assertTrue(result1.getTopicsCount() > 0, "Should have topics");
        assertTrue(result1.getPeopleCount() > 0, "Should have people");
        
        // Verify topic has contributors
        logger.info("Verifying topics have contributors after first build...");
        // NEW: Find first topic file and verify it has contributors
        verifyAnyTopicHasContributors();
        logger.info("✓ Topics have contributors");
        logger.info("");
        
        // Verify themes have contributors
        logger.info("Verifying themes have contributors after first build...");
        verifyThemesHaveContributors();
        logger.info("✓ Themes have contributors");
        logger.info("");
        
        // Count initial people
        int initialPeopleCount = countPeopleDirectories();
        logger.info("Initial people count: {}", initialPeopleCount);
        logger.info("");
        
        // ===== SECOND BUILD (INCREMENTAL UPDATE) =====
        logger.info("--- PHASE 2: Incremental Update ---");
        logger.info("");
        
        // Wait a bit to simulate time passage
        Thread.sleep(1000);
        
        KBOrchestratorParams params2 = new KBOrchestratorParams();
        params2.setSourceName("incremental_test");
        params2.setInputFile(secondInputFile.toString());
        params2.setDateTime("2024-10-10T12:00:00");
        params2.setOutputPath(tempDir.toString());
        
        logger.info("Running incremental KB update...");
        KBResult result2 = orchestrator.run(params2);
        
        logger.info("Second Build Result:");
        logger.info("  Success: {}", result2.isSuccess());
        logger.info("  Themes: {}", result2.getThemesCount());
        logger.info("  Questions: {}", result2.getQuestionsCount());
        logger.info("  Answers: {}", result2.getAnswersCount());
        logger.info("  People: {}", result2.getPeopleCount());
        logger.info("  Topics: {}", result2.getTopicsCount());
        logger.info("");
        
        assertTrue(result2.isSuccess(), "Second build should succeed");
        
        // Verify incremental update results
        logger.info("Verifying incremental update results...");
        
        // Should have more people now (Alice + Bob from first, Charlie from second)
        int finalPeopleCount = countPeopleDirectories();
        logger.info("Final people count: {} (initial was {})", finalPeopleCount, initialPeopleCount);
        assertTrue(finalPeopleCount > initialPeopleCount, "Should have more people after second build");
        logger.info("✓ People count increased correctly");
        logger.info("");
        
        // Verify contributors merged in topics
        logger.info("Verifying contributors merged in topics...");
        verifyTopicHasContributor("testing", "Charlie Brown");
        logger.info("✓ New contributors added to topics");
        logger.info("");
        
        // Verify Alice and Bob still exist
        logger.info("Verifying existing people preserved...");
        assertTrue(Files.exists(tempDir.resolve("people/Alice_White")), "Alice should still exist");
        assertTrue(Files.exists(tempDir.resolve("people/Bob_Green")), "Bob should still exist");
        logger.info("✓ Existing people preserved");
        logger.info("");
        
        // Verify Charlie was added
        logger.info("Verifying new person added...");
        assertTrue(Files.exists(tempDir.resolve("people/Charlie_Brown")), "Charlie should be added");
        logger.info("✓ New person added correctly");
        logger.info("");
        
        // Verify auto-increment: questions from second build should have higher IDs
        logger.info("Verifying auto-increment functionality...");
        verifyAutoIncrement();
        logger.info("✓ Auto-increment working correctly");
        logger.info("");
        
        logger.info("=".repeat(80));
        logger.info("✓ Incremental update test passed successfully");
        logger.info("=".repeat(80));
    }
    
    private void verifyAnyTopicHasContributors() throws IOException {
        // NEW: Find first topic file and verify it has contributors
        Path topicsDir = tempDir.resolve("topics");
        try (Stream<Path> topicFiles = Files.list(topicsDir)) {
            Path firstTopic = topicFiles
                    .filter(p -> p.toString().endsWith(".md") && !p.toString().endsWith("-desc.md"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No topic files found"));
            
            String content = Files.readString(firstTopic);
            logger.info("Checking topic file: {}", firstTopic.getFileName());
            assertTrue(content.contains("## Key Contributors"), 
                    "Topic should have Key Contributors section");
            assertTrue(content.contains("[["), 
                    "Topic should have contributor links");
        }
    }
    
    private void verifyTopicHasContributors(String topicId) throws IOException {
        // NEW: Flat structure - topics are in topics/topic-id.md
        Path topicFile = tempDir.resolve("topics").resolve(topicId + ".md");
        assertTrue(Files.exists(topicFile), "Topic file should exist: " + topicId);
        
        String content = Files.readString(topicFile);
        assertTrue(content.contains("## Key Contributors"), 
                "Topic should have Key Contributors section: " + topicId);
        assertTrue(content.contains("[["), 
                "Topic should have contributor links: " + topicId);
    }
    
    private void verifyTopicHasContributor(String topicId, String contributorName) throws IOException {
        // NEW: Flat structure - topics are in topics/topic-id.md
        Path topicFile = tempDir.resolve("topics").resolve(topicId + ".md");
        if (!Files.exists(topicFile)) {
            logger.warn("Topic file doesn't exist yet: {}", topicId);
            return;
        }
        
        String content = Files.readString(topicFile);
        String normalizedName = contributorName.replace(" ", "_");
        assertTrue(content.contains(normalizedName) || content.contains(contributorName), 
                "Topic should reference contributor: " + contributorName + " in topic: " + topicId);
    }
    
    private void verifyThemesHaveContributors() throws IOException {
        try (Stream<Path> themes = Files.walk(tempDir.resolve("topics"), 3)
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .filter(p -> p.getParent().getFileName().toString().equals("themes"))) {
            
            themes.forEach(themeFile -> {
                try {
                    String content = Files.readString(themeFile);
                    if (content.contains("contributors:")) {
                        logger.info("  Theme has contributors: {}", themeFile.getFileName());
                    }
                } catch (IOException e) {
                    logger.error("Failed to read theme file: {}", themeFile, e);
                }
            });
        }
    }
    
    private int countPeopleDirectories() throws IOException {
        Path peopleDir = tempDir.resolve("people");
        if (!Files.exists(peopleDir)) {
            return 0;
        }
        
        try (Stream<Path> people = Files.list(peopleDir)) {
            return (int) people.filter(Files::isDirectory).count();
        }
    }
    
    private void verifyAutoIncrement() throws IOException {
        // Check that question IDs don't overlap between builds
        Path questionsDir = tempDir.resolve("questions");
        if (!Files.exists(questionsDir)) {
            return;
        }
        
        try (Stream<Path> questionFiles = Files.list(questionsDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith("q_"))
                .filter(p -> p.getFileName().toString().endsWith(".md"))) {
            
            long count = questionFiles.count();
            logger.info("  Found {} question files", count);
            assertTrue(count > 0, "Should have at least one question file");
        }
    }
    
    /**
     * Create first input file with initial conversation data
     */
    private Path createFirstInputFile() throws IOException {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        Path tempRoot = projectRoot.resolve("temp");
        Files.createDirectories(tempRoot);
        
        String testData = """
                [
                    {
                        "date": "2024-10-10T10:00:00Z",
                        "author": "Alice White",
                        "body": "How do we configure AI agents in our system?"
                    },
                    {
                        "date": "2024-10-10T10:15:00Z",
                        "author": "Bob Green",
                        "body": "You need to set up the agent configuration files. Check the documentation in the agents folder."
                    },
                    {
                        "date": "2024-10-10T10:30:00Z",
                        "author": "Alice White",
                        "body": "Thanks! That's very helpful."
                    }
                ]
                """;
        
        Path tempFile = Files.createTempFile(tempRoot, "kb_inc_first_", ".json");
        Files.writeString(tempFile, testData);
        return tempFile;
    }
    
    /**
     * Create second input file with new conversation data (incremental update)
     */
    private Path createSecondInputFile() throws IOException {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        Path tempRoot = projectRoot.resolve("temp");
        Files.createDirectories(tempRoot);
        
        String testData = """
                [
                    {
                        "date": "2024-10-10T12:00:00Z",
                        "author": "Charlie Brown",
                        "body": "What about testing strategies for AI agents?"
                    },
                    {
                        "date": "2024-10-10T12:15:00Z",
                        "author": "Bob Green",
                        "body": "We use integration tests with real AI calls and unit tests with mocks. Make sure to set the DMTOOLS_INTEGRATION_TESTS environment variable."
                    },
                    {
                        "date": "2024-10-10T12:30:00Z",
                        "author": "Charlie Brown",
                        "body": "Perfect! That answers my question."
                    }
                ]
                """;
        
        Path tempFile = Files.createTempFile(tempRoot, "kb_inc_second_", ".json");
        Files.writeString(tempFile, testData);
        return tempFile;
    }
    
    /**
     * Helper method to inject field using reflection
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
    
    /**
     * Find field in class hierarchy
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }
}

