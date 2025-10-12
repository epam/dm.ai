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
 * Integration test for KBOrchestrator with real AI
 * 
 * This test runs the complete KB building pipeline:
 * 1. Creates input data (JSON messages)
 * 2. Runs orchestrator to build KB
 * 3. Verifies output structure and content
 * 4. Cleans up temporary files
 * 
 * To run this test, you need:
 * 1. Valid AI configuration in dmtools.env
 * 2. Environment variable DMTOOLS_INTEGRATION_TESTS=true
 */
public class KBOrchestratorIntegrationTest {
    
    private static final Logger logger = LogManager.getLogger(KBOrchestratorIntegrationTest.class);
    
    private KBOrchestrator orchestrator;
    private AI ai;
    private Path tempDir;
    private Path inputFile;
    
    @BeforeEach
    void setUp() throws Exception {
        logger.info("=".repeat(80));
        logger.info("Setting up KBOrchestrator integration test");
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
        KBQuestionAnswerMappingAgent qaMappingAgent = new KBQuestionAnswerMappingAgent(ai, promptManager);
        KBStatistics statistics = new KBStatistics();
        
        ContentMergeAgent contentMergeAgent = new ContentMergeAgent(ai, promptManager);
        KBAnalysisResultMerger resultMerger = new KBAnalysisResultMerger(contentMergeAgent);
        
        SourceConfigManager sourceConfigManager = new SourceConfigManager();
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        
        // Create orchestrator with constructor injection
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
        tempDir = projectRoot.resolve("temp/kb_test_output");
        
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
        
        // Create input file with test data
        inputFile = createTestInputFile();
        logger.info("Created test input file: {}", inputFile);
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
        
        // Delete input file
        if (inputFile != null && Files.exists(inputFile)) {
            Files.deleteIfExists(inputFile);
            logger.info("Deleted input file: {}", inputFile);
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
            logger.info("Deleted temp directory: {}", tempDir);
        }
        */
    }
    
    @Test
    void testBuildCompleteKnowledgeBase() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBOrchestrator - Complete KB Build");
        logger.info("=".repeat(80));
        logger.info("");
        
        // Prepare orchestrator params
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("integration_test");
        params.setInputFile(inputFile.toString());
        params.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        params.setOutputPath(tempDir.toString());
        
        logger.info("Parameters:");
        logger.info("  Source: {}", params.getSourceName());
        logger.info("  Input: {}", params.getInputFile());
        logger.info("  Output: {}", params.getOutputPath());
        logger.info("  DateTime: {}", params.getDateTime());
        logger.info("");
        
        // Run orchestrator
        logger.info("Running KBOrchestrator...");
        logger.info("-".repeat(80));
        
        KBResult result = orchestrator.run(params);
        
        logger.info("-".repeat(80));
        logger.info("");
        
        // Print result
        logger.info("KB BUILD RESULT:");
        logger.info("  Success: {}", result.isSuccess());
        logger.info("  Message: {}", result.getMessage());
        logger.info("  Themes: {}", result.getThemesCount());
        logger.info("  Questions: {}", result.getQuestionsCount());
        logger.info("  Answers: {}", result.getAnswersCount());
        logger.info("  Notes: {}", result.getNotesCount());
        logger.info("  People: {}", result.getPeopleCount());
        logger.info("  Topics: {}", result.getTopicsCount());
        logger.info("");
        
        // Verify result
        assertNotNull(result);
        assertTrue(result.isSuccess(), "KB build should be successful");
        assertTrue(result.getThemesCount() > 0, "Should have at least one theme");
        assertTrue(result.getQuestionsCount() > 0, "Should have at least one question");
        
        // Verify directory structure
        logger.info("Verifying directory structure...");
        verifyDirectoryStructure();
        logger.info("✓ Directory structure verified");
        logger.info("");
        
        // Verify generated files
        logger.info("Verifying generated files...");
        verifyGeneratedFiles();
        logger.info("✓ Generated files verified");
        logger.info("");
        
        // Verify source config
        logger.info("Verifying source config...");
        verifySourceConfig();
        logger.info("✓ Source config verified");
        logger.info("");
        
        logger.info("=".repeat(80));
        logger.info("✓ Complete KB build test passed successfully");
        logger.info("=".repeat(80));
    }
    
    @Test
    void testBuildKnowledgeBaseWithSmallInput() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBOrchestrator - Small Input (Single Chunk)");
        logger.info("=".repeat(80));
        logger.info("");
        
        // Create small input file (should not trigger chunking)
        Path smallInput = createSmallTestInputFile();
        
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("small_test");
        params.setInputFile(smallInput.toString());
        params.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        params.setOutputPath(tempDir.toString());
        
        logger.info("Testing with small input (single chunk)...");
        
        KBResult result = orchestrator.run(params);
        
        logger.info("Result: {} themes, {} questions, {} answers",
                result.getThemesCount(),
                result.getQuestionsCount(),
                result.getAnswersCount());
        
        // Verify result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Clean up small input
        Files.deleteIfExists(smallInput);
        
        logger.info("✓ Small input test passed successfully");
        logger.info("=".repeat(80));
    }
    
    /**
     * Verify that all required directories are created
     */
    private void verifyDirectoryStructure() {
        assertTrue(Files.exists(tempDir.resolve("topics")), "topics/ directory should exist");
        assertTrue(Files.exists(tempDir.resolve("people")), "people/ directory should exist");
        assertTrue(Files.exists(tempDir.resolve("stats")), "stats/ directory should exist");
        assertTrue(Files.exists(tempDir.resolve("inbox")), "inbox/ directory should exist");
        
        assertTrue(Files.isDirectory(tempDir.resolve("topics")), "topics/ should be a directory");
        assertTrue(Files.isDirectory(tempDir.resolve("people")), "people/ should be a directory");
        assertTrue(Files.isDirectory(tempDir.resolve("stats")), "stats/ should be a directory");
        assertTrue(Files.isDirectory(tempDir.resolve("inbox")), "inbox/ should be a directory");
        
        logger.info("  ✓ All required directories exist");
    }
    
    /**
     * Verify that expected files are generated
     */
    private void verifyGeneratedFiles() throws IOException {
        // Check for at least one topic directory
        try (Stream<Path> topics = Files.list(tempDir.resolve("topics"))) {
            long topicCount = topics.filter(Files::isDirectory).count();
            assertTrue(topicCount > 0, "Should have at least one topic directory");
            logger.info("  ✓ Found {} topic directories", topicCount);
        }
        
        // Check for at least one person directory
        try (Stream<Path> people = Files.list(tempDir.resolve("people"))) {
            long peopleCount = people.filter(Files::isDirectory).count();
            assertTrue(peopleCount > 0, "Should have at least one person directory");
            logger.info("  ✓ Found {} people directories", peopleCount);
        }
        
        // Check for INDEX.md (if statistics generated it)
        Path indexFile = tempDir.resolve("INDEX.md");
        if (Files.exists(indexFile)) {
            logger.info("  ✓ INDEX.md exists");
            String content = Files.readString(indexFile);
            assertFalse(content.isEmpty(), "INDEX.md should not be empty");
            logger.info("  ✓ INDEX.md has content ({} bytes)", content.length());
        }
    }
    
    /**
     * Verify source config is created
     */
    private void verifySourceConfig() {
        Path configFile = tempDir.resolve("inbox/source_config.json");
        assertTrue(Files.exists(configFile), "source_config.json should exist");
        assertTrue(Files.isRegularFile(configFile), "source_config.json should be a file");
        
        try {
            String content = Files.readString(configFile);
            assertFalse(content.isEmpty(), "source_config.json should not be empty");
            assertTrue(content.contains("integration_test"), "Config should contain source name");
            logger.info("  ✓ source_config.json exists and contains source name");
        } catch (IOException e) {
            fail("Failed to read source_config.json: " + e.getMessage());
        }
    }
    
    /**
     * Create test input file with comprehensive conversation data in project temp folder
     */
    private Path createTestInputFile() throws IOException {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        Path tempRoot = projectRoot.resolve("temp");
        Files.createDirectories(tempRoot);
        
        String testData = """
                [
                    {
                        "date": "2024-10-10T10:00:00Z",
                        "author": "John Doe",
                        "body": "How do I configure Cursor AI agents? I'm trying to set up custom agent configurations but can't find the right directory structure."
                    },
                    {
                        "date": "2024-10-10T10:15:00Z",
                        "author": "Sarah Smith",
                        "body": "You need to create an agent config JSON file. Here's an example: {\\"agent\\": \\"cursor\\", \\"model\\": \\"claude-3.5-sonnet\\"}. Put it in the .cursor/agents/ directory in your project root. The file should follow the agent schema."
                    },
                    {
                        "date": "2024-10-10T10:20:00Z",
                        "author": "Mike Johnson",
                        "body": "Important note: Make sure to restart Cursor after adding the config! The agent won't be recognized until you restart. Also, check that your JSON is valid."
                    },
                    {
                        "date": "2024-10-10T10:25:00Z",
                        "author": "John Doe",
                        "body": "Thanks! That worked perfectly. Another question - what are the best practices for prompt engineering with AI agents?"
                    },
                    {
                        "date": "2024-10-10T10:30:00Z",
                        "author": "Sarah Smith",
                        "body": "For prompt engineering: 1) Be specific and clear about what you want, 2) Use XML tags to structure your prompts, 3) Provide examples when possible, 4) Include context about the task. For Cursor agents specifically, use the system role to define behavior and user role for actual queries."
                    },
                    {
                        "date": "2024-10-10T11:00:00Z",
                        "author": "Emily Davis",
                        "body": "I've been working with DMTools integration. How do we handle large input files that need to be chunked?"
                    },
                    {
                        "date": "2024-10-10T11:15:00Z",
                        "author": "Mike Johnson",
                        "body": "DMTools has a ChunkPreparation utility that handles this automatically. You can configure token limits and it will split your input intelligently while preserving context. The orchestrator handles this transparently."
                    },
                    {
                        "date": "2024-10-10T11:30:00Z",
                        "author": "Emily Davis",
                        "body": "That's really helpful! Does it also merge the results back together?"
                    },
                    {
                        "date": "2024-10-10T11:45:00Z",
                        "author": "Sarah Smith",
                        "body": "Yes, the KBAnalysisResultMerger uses AI to intelligently merge and deduplicate content from multiple chunks. It preserves all unique information while removing redundancies."
                    },
                    {
                        "date": "2024-10-10T12:00:00Z",
                        "author": "John Doe",
                        "body": "What about testing? How do we write integration tests for AI agents?"
                    },
                    {
                        "date": "2024-10-10T12:15:00Z",
                        "author": "Mike Johnson",
                        "body": "We use real AI in integration tests but mock it for unit tests. Integration tests verify the full pipeline with actual API calls. Make sure to set DMTOOLS_INTEGRATION_TESTS=true environment variable."
                    },
                    {
                        "date": "2024-10-10T12:30:00Z",
                        "author": "Emily Davis",
                        "body": "Important tip: Always use logger instead of System.out.println in production code. It gives better control and formatting."
                    }
                ]
                """;
        
        Path tempFile = Files.createTempFile(tempRoot, "kb_orchestrator_test_", ".json");
        Files.writeString(tempFile, testData);
        return tempFile;
    }
    
    /**
     * Create small test input file (won't trigger chunking) in project temp folder
     */
    private Path createSmallTestInputFile() throws IOException {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        Path tempRoot = projectRoot.resolve("temp");
        Files.createDirectories(tempRoot);
        
        String testData = """
                [
                    {
                        "date": "2024-10-10T10:00:00Z",
                        "author": "Alice Brown",
                        "body": "Quick question about knowledge base structure?"
                    },
                    {
                        "date": "2024-10-10T10:05:00Z",
                        "author": "Bob White",
                        "body": "The structure follows Obsidian vault format with topics, people, and stats directories."
                    }
                ]
                """;
        
        Path tempFile = Files.createTempFile(tempRoot, "kb_small_test_", ".json");
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

