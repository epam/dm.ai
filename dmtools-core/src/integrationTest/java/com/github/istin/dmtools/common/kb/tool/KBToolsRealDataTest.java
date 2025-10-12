package com.github.istin.dmtools.common.kb.tool;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KBTools with real Teams data
 */
public class KBToolsRealDataTest {
    
    private static final Logger logger = LogManager.getLogger(KBToolsRealDataTest.class);
    
    private KBTools kbTools;
    private com.github.istin.dmtools.common.kb.agent.KBOrchestrator orchestrator;
    private Path testDataPath;
    private Path outputPath;
    
    @BeforeEach
    void setUp() throws Exception {
        logger.info("=".repeat(80));
        logger.info("Setting up KBToolsRealDataTest");
        logger.info("=".repeat(80));
        
        // Initialize KBTools and Orchestrator using Dagger
        KnowledgeBaseComponent component = DaggerKnowledgeBaseComponent.create();
        kbTools = component.kbTools();
        orchestrator = component.kbOrchestrator();
        
        // Define paths (project root is parent of dmtools-core/)
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        testDataPath = projectRoot.resolve("temp/teams_chat/chunk_002.json");
        outputPath = projectRoot.resolve("temp/teams_chat_output");  // Separate output directory
        
        // Clean output directory (NOT the input directory!)
        //cleanDirectory(outputPath);
        //logger.info("Cleaned output directory: {}", outputPath);
        
        // Verify test data exists
        if (!Files.exists(testDataPath)) {
            throw new RuntimeException("Test data file not found: " + testDataPath);
        }
        
        logger.info("Test data file: {}", testDataPath);
        logger.info("Output path: {}", outputPath);
        logger.info("Test data size: {} bytes", Files.size(testDataPath));
    }
    
    @Test
    void testProcessRealTeamsData() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Process Real Teams Data with Timing");
        logger.info("=".repeat(80));
        
        long testStartTime = System.currentTimeMillis();
        
        // Step 1: Check initial status
        logger.info("\n--- Step 1: Check initial status ---");
        String statusBefore = kbTools.kbGet("teams_chat", outputPath.toString());
        logger.info("Status before: {}", statusBefore);
        
        // Step 2: Process data (PROCESS_ONLY mode - without AI aggregation)
        logger.info("\n--- Step 2: Process data (PROCESS_ONLY mode) ---");
        long processStartTime = System.currentTimeMillis();
        
        // Use orchestrator directly to specify PROCESS_ONLY mode
        com.github.istin.dmtools.common.kb.params.KBOrchestratorParams params = 
                new com.github.istin.dmtools.common.kb.params.KBOrchestratorParams();
        params.setSourceName("teams_chat");
        params.setInputFile(testDataPath.toString());
        params.setDateTime("2024-10-11T23:40:00Z");
        params.setOutputPath(outputPath.toString());
        params.setProcessingMode(com.github.istin.dmtools.common.kb.model.KBProcessingMode.PROCESS_ONLY);
        
        com.github.istin.dmtools.common.kb.model.KBResult kbResult = orchestrator.run(params);
        
        long processEndTime = System.currentTimeMillis();
        double processTime = (processEndTime - processStartTime) / 1000.0;
        
        String result = String.format(
                "{\"success\": %s, \"message\": \"%s\", \"topics\": %d, \"themes\": %d, \"questions\": %d, \"answers\": %d, \"notes\": %d, \"people\": %d}",
                kbResult.isSuccess(), kbResult.getMessage(), kbResult.getTopicsCount(), kbResult.getThemesCount(),
                kbResult.getQuestionsCount(), kbResult.getAnswersCount(), kbResult.getNotesCount(), kbResult.getPeopleCount()
        );
        
        logger.info("Process result: {}", result);
        logger.info("⏱️  PROCESS TIME: {} seconds", String.format("%.2f", processTime));
        
        // Step 3: Verify output structure
        logger.info("\n--- Step 3: Verify output structure ---");
        verifyOutputStructure(outputPath);
        
        // Step 4: Check final status
        logger.info("\n--- Step 4: Check final status ---");
        String statusAfter = kbTools.kbGet("teams_chat", outputPath.toString());
        logger.info("Status after: {}", statusAfter);
        
        long testEndTime = System.currentTimeMillis();
        double totalTime = (testEndTime - testStartTime) / 1000.0;
        
        logger.info("\n" + "=".repeat(80));
        logger.info("⏱️  TOTAL TEST TIME: {} seconds", String.format("%.2f", totalTime));
        logger.info("⏱️  PROCESS ONLY TIME: {} seconds ({}%)", 
                   String.format("%.2f", processTime), 
                   String.format("%.1f", (processTime / totalTime) * 100));
        logger.info("=".repeat(80));
        
        // Assertions
        assertNotNull(result);
        assertTrue(result.contains("success") || result.contains("questions"), 
                  "Result should indicate success");
    }
    
    private void verifyOutputStructure(Path outputPath) throws IOException {
        logger.info("Verifying output structure...");
        
        // Check main directories
        assertTrue(Files.exists(outputPath.resolve("questions")), "questions/ should exist");
        assertTrue(Files.exists(outputPath.resolve("answers")), "answers/ should exist");
        assertTrue(Files.exists(outputPath.resolve("topics")), "topics/ should exist");
        assertTrue(Files.exists(outputPath.resolve("people")), "people/ should exist");
        assertTrue(Files.exists(outputPath.resolve("inbox/raw")), "inbox/raw/ should exist");
        assertTrue(Files.exists(outputPath.resolve("inbox/analyzed")), "inbox/analyzed/ should exist");
        
        // Count files
        long questionsCount = countFiles(outputPath.resolve("questions"));
        long answersCount = countFiles(outputPath.resolve("answers"));
        long notesCount = countFiles(outputPath.resolve("notes"));
        long topicsCount = countTopicFiles(outputPath.resolve("topics"));
        long peopleCount = countDirectories(outputPath.resolve("people"));
        
        logger.info("Output statistics:");
        logger.info("  Questions: {}", questionsCount);
        logger.info("  Answers: {}", answersCount);
        logger.info("  Notes: {}", notesCount);
        logger.info("  Topics: {}", topicsCount);
        logger.info("  People: {}", peopleCount);
        
        // Check analyzed JSON exists
        Path analyzedDir = outputPath.resolve("inbox/analyzed");
        if (Files.exists(analyzedDir)) {
            try (Stream<Path> files = Files.list(analyzedDir)) {
                long analyzedCount = files.filter(p -> p.toString().endsWith("_analyzed.json")).count();
                logger.info("  Analyzed JSON files: {}", analyzedCount);
                assertTrue(analyzedCount > 0, "Should have at least one analyzed JSON file");
            }
        }
        
        assertTrue(questionsCount > 0 || answersCount > 0 || notesCount > 0, 
                  "Should have at least some Q/A/N");
    }
    
    private long countFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                       .filter(p -> p.toString().endsWith(".md"))
                       .count();
        }
    }
    
    private long countTopicFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                       .filter(p -> p.toString().endsWith(".md"))
                       .filter(p -> !p.toString().endsWith("-desc.md"))
                       .count();
        }
    }
    
    private long countDirectories(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isDirectory).count();
        }
    }
    
    private void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            logger.info("Directory doesn't exist, nothing to clean: {}", directory);
            return;
        }
        
        logger.info("Cleaning directory: {}", directory);
        
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         if (!path.equals(directory)) {
                             Files.delete(path);
                         }
                     } catch (IOException e) {
                         logger.warn("Failed to delete: {}", path, e);
                     }
                 });
        }
        
        logger.info("✓ Directory cleaned");
    }
}

