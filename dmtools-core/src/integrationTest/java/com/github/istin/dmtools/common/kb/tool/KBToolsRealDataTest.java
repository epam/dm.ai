package com.github.istin.dmtools.common.kb.tool;

import com.github.istin.dmtools.common.kb.model.KBProcessingMode;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private Path teamsDataDir;
    private Path outputPath;
    
    @BeforeEach
    void setUp() throws Exception {
        logger.info("=".repeat(80));
        logger.info("Setting up KBToolsRealDataTest with Ollama AI");
        logger.info("=".repeat(80));
        
        // Initialize KBTools and Orchestrator using Dagger
        // AI client (Ollama) is automatically injected based on dmtools.env configuration
        KnowledgeBaseComponent component = DaggerKnowledgeBaseComponent.create();
        kbTools = component.kbTools();
        orchestrator = component.kbOrchestrator();
        
        // Define paths (project root is parent of dmtools-core/)
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        teamsDataDir = projectRoot.resolve("temp/teams_chat");
        // Output directly to the git repository
        outputPath = Paths.get("/Users/Uladzimir_Klyshevich/notes/ai-kb");
        
        // Verify teams_chat directory exists
        if (!Files.exists(teamsDataDir)) {
            throw new RuntimeException("Teams data directory not found: " + teamsDataDir);
        }
        
        logger.info("Teams data directory: {}", teamsDataDir);
        logger.info("Output path: {}", outputPath);
    }
    
    /**
     * Process multiple chunk files in sequence
     * @param startNumber Starting chunk number (e.g., 1 for chunk_001)
     * @param amount Number of files to process (e.g., 10 to process 10 files)
     */
    @Test
    void testProcessMultipleChunks() throws Exception {
        // CONFIGURE HERE: Set your start number and amount
        int startNumber = 20;
        int amount = 170;
        
        processChunks(startNumber, amount);
    }
    
    /**
     * Helper method to process chunk files
     * @param startNumber Starting chunk number (e.g., 1 for chunk_001)
     * @param amount Number of files to process
     */
    private void processChunks(int startNumber, int amount) throws Exception {
        String sourceName = "teams_chat_llm_ru";
        
        logger.info("=".repeat(80));
        logger.info("TEST: Process Multiple Chunks (start={}, amount={})", startNumber, amount);
        logger.info("=".repeat(80));
        
        long testStartTime = System.currentTimeMillis();
        
        // Process each chunk file
        for (int i = 0; i < amount; i++) {
            int chunkNumber = startNumber + i;
            String chunkFileName = String.format("chunk_%03d.json", chunkNumber);
            Path chunkFilePath = teamsDataDir.resolve(chunkFileName);
            
            if (!Files.exists(chunkFilePath)) {
                logger.warn("Chunk file not found, skipping: {}", chunkFileName);
                continue;
            }
            
            logger.info("\n" + "=".repeat(80));
            logger.info("Processing chunk {}/{}: {}", i + 1, amount, chunkFileName);
            logger.info("=".repeat(80));
            
            // Extract datetime from JSON file
            String dateTime = extractNewestDateTime(chunkFilePath);
            logger.info("Extracted datetime: {}", dateTime);
            logger.info("File size: {} bytes", Files.size(chunkFilePath));
            
            long chunkStartTime = System.currentTimeMillis();
            
            // Process chunk
            KBOrchestratorParams params = new KBOrchestratorParams();
            params.setSourceName(sourceName);
            params.setInputFile(chunkFilePath.toString());
            params.setDateTime(dateTime);
            params.setOutputPath(outputPath.toString());
            params.setProcessingMode(KBProcessingMode.PROCESS_ONLY);
            
            KBResult kbResult = orchestrator.run(params);
            
            long chunkEndTime = System.currentTimeMillis();
            double chunkTime = (chunkEndTime - chunkStartTime) / 1000.0;
            
            logger.info("Chunk result: success={}, questions={}, answers={}, notes={}, topics={}, people={}", 
                       kbResult.isSuccess(),
                       kbResult.getQuestionsCount(),
                       kbResult.getAnswersCount(),
                       kbResult.getNotesCount(),
                       kbResult.getTopicsCount(),
                       kbResult.getPeopleCount());
            logger.info("⏱️  Chunk processing time: {} seconds", String.format("%.2f", chunkTime));
            
            // Verify success
            assertTrue(kbResult.isSuccess(), "Chunk processing should succeed");
        }
        
        long testEndTime = System.currentTimeMillis();
        double totalTime = (testEndTime - testStartTime) / 1000.0;
        
        logger.info("\n" + "=".repeat(80));
        logger.info("SUMMARY");
        logger.info("=".repeat(80));
        logger.info("Processed {} chunks (from {} to {})", amount, startNumber, startNumber + amount - 1);
        logger.info("⏱️  TOTAL TIME: {} seconds", String.format("%.2f", totalTime));
        logger.info("⏱️  AVERAGE per chunk: {} seconds", String.format("%.2f", totalTime / amount));
        
        // Verify final output structure
        logger.info("\n--- Final output structure verification ---");
        verifyOutputStructure(outputPath);
        
        logger.info("=".repeat(80));
    }
    
    /**
     * Extract the newest datetime from a chunk JSON file
     * @param chunkFile Path to the chunk JSON file
     * @return The newest datetime from date_range.newest
     */
    private String extractNewestDateTime(Path chunkFile) throws IOException {
        String jsonContent = Files.readString(chunkFile);
        JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
        
        if (jsonObject.has("date_range") && jsonObject.getAsJsonObject("date_range").has("newest")) {
            return jsonObject.getAsJsonObject("date_range").get("newest").getAsString();
        }
        
        // Fallback if date_range not found
        logger.warn("date_range.newest not found in {}, using default", chunkFile.getFileName());
        return "2024-10-11T23:40:00Z";
    }

    /**
     * Clean up duplicate Q/A/N from chunk_002.json second processing
     */
    @Test
    void cleanDuplicateChunk002() throws Exception {
        logger.info("Cleaning duplicates from chunk_002 (second processing)...");

        // Delete Q/A/N that match this analyzed JSON
        cleanDuplicatesFromAnalyzedJson("1760298330927_analyzed.json");

        logger.info("Cleanup complete! Review git diff to see what was removed.");
    }

    /**
     * Clean up duplicate Q/A/N files based on an analyzed JSON file.
     * This is useful when a chunk was processed twice and you need to remove the duplicates.
     * 
     * IMPORTANT: This method searches for files by text content and deletes them.
     * Make sure you're cleaning the CORRECT analyzed JSON file (usually the second/duplicate processing).
     * 
     * @param analyzedJsonFileName Name of the analyzed JSON file to clean (e.g., "1760298330927_analyzed.json")
     * @throws IOException if file operations fail
     */
    private void cleanDuplicatesFromAnalyzedJson(String analyzedJsonFileName) throws IOException {
        Path analyzedJsonPath = outputPath.resolve("inbox/analyzed").resolve(analyzedJsonFileName);
        
        if (!Files.exists(analyzedJsonPath)) {
            logger.error("Analyzed JSON file not found: {}", analyzedJsonPath);
            return;
        }
        
        logger.info("=".repeat(80));
        logger.info("CLEANING DUPLICATES FROM: {}", analyzedJsonFileName);
        logger.info("=".repeat(80));
        
        // Read analyzed JSON
        String jsonContent = Files.readString(analyzedJsonPath);
        JsonObject analysisResult = JsonParser.parseString(jsonContent).getAsJsonObject();
        
        int deletedQuestions = 0;
        int deletedAnswers = 0;
        int deletedNotes = 0;
        
        // Clean questions
        if (analysisResult.has("questions")) {
            for (var questionElement : analysisResult.getAsJsonArray("questions")) {
                JsonObject question = questionElement.getAsJsonObject();
                String text = question.get("text").getAsString();
                String author = question.get("author").getAsString();
                
                Path questionsDir = outputPath.resolve("questions");
                if (Files.exists(questionsDir)) {
                    try (Stream<Path> files = Files.list(questionsDir)) {
                        for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                            String content = Files.readString(file);
                            if (content.contains(text) && content.contains("author: \"" + author + "\"")) {
                                Files.delete(file);
                                logger.info("Deleted question: {}", file.getFileName());
                                deletedQuestions++;
                                break; // Found and deleted, move to next question
                            }
                        }
                    }
                }
            }
        }
        
        // Clean answers
        if (analysisResult.has("answers")) {
            for (var answerElement : analysisResult.getAsJsonArray("answers")) {
                JsonObject answer = answerElement.getAsJsonObject();
                String text = answer.get("text").getAsString();
                String author = answer.get("author").getAsString();
                
                Path answersDir = outputPath.resolve("answers");
                if (Files.exists(answersDir)) {
                    try (Stream<Path> files = Files.list(answersDir)) {
                        for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                            String content = Files.readString(file);
                            if (content.contains(text) && content.contains("author: \"" + author + "\"")) {
                                Files.delete(file);
                                logger.info("Deleted answer: {}", file.getFileName());
                                deletedAnswers++;
                                break; // Found and deleted, move to next answer
                            }
                        }
                    }
                }
            }
        }
        
        // Clean notes
        if (analysisResult.has("notes")) {
            for (var noteElement : analysisResult.getAsJsonArray("notes")) {
                JsonObject note = noteElement.getAsJsonObject();
                String text = note.get("text").getAsString();
                String author = note.get("author").getAsString();
                
                Path notesDir = outputPath.resolve("notes");
                if (Files.exists(notesDir)) {
                    try (Stream<Path> files = Files.list(notesDir)) {
                        for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                            String content = Files.readString(file);
                            if (content.contains(text) && content.contains("author: \"" + author + "\"")) {
                                Files.delete(file);
                                logger.info("Deleted note: {}", file.getFileName());
                                deletedNotes++;
                                break; // Found and deleted, move to next note
                            }
                        }
                    }
                }
            }
        }
        
        logger.info("=".repeat(80));
        logger.info("CLEANUP SUMMARY");
        logger.info("=".repeat(80));
        logger.info("Deleted {} questions", deletedQuestions);
        logger.info("Deleted {} answers", deletedAnswers);
        logger.info("Deleted {} notes", deletedNotes);
        logger.info("Total deleted: {}", deletedQuestions + deletedAnswers + deletedNotes);
        logger.info("=".repeat(80));
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

