package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.SourceConfigManager;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for incremental KB updates.
 * Tests that data is correctly created, updated, and preserved across multiple processing batches.
 */
public class KBIncrementalTest {
    
    private static final Logger logger = LogManager.getLogger(KBIncrementalTest.class);
    
    private KBOrchestrator orchestrator;
    private Path tempDir;
    private Path batch1File;
    private Path batch2File;
    private Path batch3File;
    
    @BeforeEach
    void setUp() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KB Incremental Updates");
        logger.info("=".repeat(80));
        
        // Initialize components
        PropertyReader propertyReader = new PropertyReader();
        ConversationObserver observer = new ConversationObserver();
        AI ai = BasicGeminiAI.create(observer, propertyReader);
        
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
        tempDir = projectRoot.resolve("temp/kb_incremental_test");
        
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
        logger.info("Using test directory: {}", tempDir);
        
        // Create input files for 3 batches
        batch1File = createBatch1InputFile();
        batch2File = createBatch2InputFile();
        batch3File = createBatch3InputFile();
        
        logger.info("Created 3 batch input files");
        logger.info("");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        // Keep directory for inspection
        logger.info("Test output preserved at: {}", tempDir);
    }
    
    @Test
    void testIncrementalUpdates() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Three-batch incremental updates");
        logger.info("=".repeat(80));
        
        // BATCH 1: Initial build
        logger.info("\n" + "=".repeat(80));
        logger.info("BATCH 1: Initial build");
        logger.info("=".repeat(80));
        
        KBOrchestratorParams params1 = new KBOrchestratorParams();
        params1.setSourceName("source_incremental_test");
        params1.setInputFile(batch1File.toString());
        params1.setOutputPath(tempDir.toString());
        
        orchestrator.run(params1);
        
        logger.info("\n--- Verifying Batch 1 results ---");
        verifyBatch1(tempDir);
        
        // BATCH 2: Add more content from same people + new person
        logger.info("\n" + "=".repeat(80));
        logger.info("BATCH 2: Incremental update (same people + new person)");
        logger.info("=".repeat(80));
        
        KBOrchestratorParams params2 = new KBOrchestratorParams();
        params2.setSourceName("source_incremental_test");
        params2.setInputFile(batch2File.toString());
        params2.setOutputPath(tempDir.toString());
        
        orchestrator.run(params2);
        
        logger.info("\n--- Verifying Batch 2 results ---");
        verifyBatch2(tempDir);
        
        // BATCH 3: Answer to old question + new topic
        logger.info("\n" + "=".repeat(80));
        logger.info("BATCH 3: Incremental update (answer old question + new topic)");
        logger.info("=".repeat(80));
        
        KBOrchestratorParams params3 = new KBOrchestratorParams();
        params3.setSourceName("source_incremental_test");
        params3.setInputFile(batch3File.toString());
        params3.setOutputPath(tempDir.toString());
        
        orchestrator.run(params3);
        
        logger.info("\n--- Verifying Batch 3 results ---");
        verifyBatch3(tempDir);
        
        logger.info("\n" + "=".repeat(80));
        logger.info("✓ ALL INCREMENTAL TESTS PASSED");
        logger.info("✓ Inspect results at: {}", tempDir);
        logger.info("=".repeat(80));
    }
    
    private Path createBatch1InputFile() throws IOException {
        String content = """
                [2024-10-10T10:00:00Z] Alice Brown: "How do I create a Dockerfile for a Python application?"
                [2024-10-10T10:15:00Z] Bob Smith: "Start with FROM python:3.11-slim, then COPY your requirements.txt and RUN pip install -r requirements.txt. Finally, COPY your app and use CMD to run it."
                """;
        
        Path file = tempDir.resolve("batch1_input.txt");
        Files.writeString(file, content);
        logger.info("Created batch 1: Alice asks question, Bob answers");
        return file;
    }
    
    private Path createBatch2InputFile() throws IOException {
        String content = """
                [2024-10-11T09:00:00Z] Alice Brown: "What's the best way to deploy a containerized app to Kubernetes?"
                [2024-10-11T09:30:00Z] Charlie White: "Important: Always use .dockerignore to exclude __pycache__ and .git directories from your Docker build context. This significantly reduces build time and image size."
                """;
        
        Path file = tempDir.resolve("batch2_input.txt");
        Files.writeString(file, content);
        logger.info("Created batch 2: Alice asks new question, Charlie adds note");
        return file;
    }
    
    private Path createBatch3InputFile() throws IOException {
        String content = """
                [2024-10-11T14:00:00Z] David Green: "For the first question about Dockerfile, I'd also recommend using multi-stage builds to keep your final image small. Here's an example: use one stage for building dependencies and another for the runtime."
                [2024-10-11T14:30:00Z] Alice Brown: "How do I optimize Python application startup time in Docker?"
                """;
        
        Path file = tempDir.resolve("batch3_input.txt");
        Files.writeString(file, content);
        logger.info("Created batch 3: David answers old question, Alice asks about Python");
        return file;
    }
    
    private void verifyBatch1(Path kbPath) throws IOException {
        logger.info("Verifying Batch 1: Initial build");
        
        // Check questions
        Path questionsDir = kbPath.resolve("questions");
        assertTrue(Files.exists(questionsDir), "Questions directory should exist");
        List<Path> questions = listFiles(questionsDir, "q_*.md");
        assertEquals(1, questions.size(), "Should have 1 question after batch 1");
        logger.info("✓ Questions: {}", questions.size());
        
        Path q1 = questions.get(0);
        String q1Content = Files.readString(q1);
        assertTrue(q1Content.contains("Alice"), "Question should be from Alice");
        assertTrue(q1Content.contains("Dockerfile"), "Question should be about Dockerfile");
        logger.info("✓ Question q_0001: from Alice about Dockerfile");
        
        // Check answers
        Path answersDir = kbPath.resolve("answers");
        assertTrue(Files.exists(answersDir), "Answers directory should exist");
        List<Path> answers = listFiles(answersDir, "a_*.md");
        assertEquals(1, answers.size(), "Should have 1 answer after batch 1");
        logger.info("✓ Answers: {}", answers.size());
        
        Path a1 = answers.get(0);
        String a1Content = Files.readString(a1);
        assertTrue(a1Content.contains("Bob"), "Answer should be from Bob");
        assertTrue(a1Content.contains("python:3.11-slim"), "Answer should mention base image");
        logger.info("✓ Answer a_0001: from Bob with Python setup");
        
        // Check notes
        Path notesDir = kbPath.resolve("notes");
        if (Files.exists(notesDir)) {
            List<Path> notes = listFiles(notesDir, "n_*.md");
            assertEquals(0, notes.size(), "Should have 0 notes after batch 1");
            logger.info("✓ Notes: {}", notes.size());
        }
        
        // Check people
        Path peopleDir = kbPath.resolve("people");
        assertTrue(Files.exists(peopleDir), "People directory should exist");
        List<Path> people = listDirectories(peopleDir);
        assertEquals(2, people.size(), "Should have 2 people after batch 1");
        logger.info("✓ People: {} (Alice_Brown, Bob_Smith)", people.size());
        
        assertTrue(Files.exists(peopleDir.resolve("Alice_Brown/Alice_Brown.md")), "Alice_Brown profile should exist");
        assertTrue(Files.exists(peopleDir.resolve("Bob_Smith/Bob_Smith.md")), "Bob_Smith profile should exist");
        
        // Check Alice's profile
        String aliceContent = Files.readString(peopleDir.resolve("Alice_Brown/Alice_Brown.md"));
        assertTrue(aliceContent.contains("questionsAsked: 1"), "Alice should have 1 question");
        logger.info("✓ Alice_Brown profile: 1 question");
        
        // Check Bob's profile
        String bobContent = Files.readString(peopleDir.resolve("Bob_Smith/Bob_Smith.md"));
        assertTrue(bobContent.contains("answersProvided: 1"), "Bob should have 1 answer");
        logger.info("✓ Bob_Smith profile: 1 answer");
        
        // Check topics
        Path topicsDir = kbPath.resolve("topics");
        assertTrue(Files.exists(topicsDir), "Topics directory should exist");
        List<Path> topics = listFiles(topicsDir, "*.md").stream()
                .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                .collect(Collectors.toList());
        assertTrue(topics.size() > 0, "Should have topics after batch 1");
        logger.info("✓ Topics: {}", topics.size());
        
        logger.info("✓ Batch 1 verification PASSED\n");
    }
    
    private void verifyBatch2(Path kbPath) throws IOException {
        logger.info("Verifying Batch 2: Incremental update");
        
        // Check questions (should have 2 now: q_0001 from batch1, q_0002 from batch2)
        Path questionsDir = kbPath.resolve("questions");
        List<Path> questions = listFiles(questionsDir, "q_*.md");
        assertEquals(2, questions.size(), "Should have 2 questions after batch 2");
        logger.info("✓ Questions: {} (q_0001 + q_0002)", questions.size());
        
        // Check that q_0001 still exists (not lost)
        assertTrue(Files.exists(questionsDir.resolve("q_0001.md")), "q_0001 should still exist");
        logger.info("✓ q_0001 preserved from batch 1");
        
        // Check new question q_0002
        assertTrue(Files.exists(questionsDir.resolve("q_0002.md")), "q_0002 should exist");
        String q2Content = Files.readString(questionsDir.resolve("q_0002.md"));
        assertTrue(q2Content.contains("Alice"), "q_0002 should be from Alice");
        assertTrue(q2Content.contains("Kubernetes"), "q_0002 should be about Kubernetes");
        logger.info("✓ q_0002: from Alice about Kubernetes");
        
        // Check answers (should have 2 now: a_0001 + a_0002)
        // Note: Charlie's note was converted to answer a_0002 via Q→A mapping
        Path answersDir = kbPath.resolve("answers");
        List<Path> answers = listFiles(answersDir, "a_*.md");
        assertEquals(2, answers.size(), "Should have 2 answers after batch 2 (a_0001 + a_0002 from note)");
        logger.info("✓ Answers: {} (a_0001 preserved, a_0002 from Charlie's note via Q→A mapping)", answers.size());
        
        // Verify a_0002 exists and is from Charlie
        assertTrue(Files.exists(answersDir.resolve("a_0002.md")), "a_0002 should exist");
        String a2Content = Files.readString(answersDir.resolve("a_0002.md"));
        assertTrue(a2Content.contains("Charlie"), "a_0002 should be from Charlie");
        assertTrue(a2Content.contains(".dockerignore"), "a_0002 should mention .dockerignore");
        assertTrue(a2Content.contains("answersQuestion: \"q_0001\""), "a_0002 should answer q_0001");
        logger.info("✓ a_0002: from Charlie about .dockerignore, answers q_0001");
        
        // Check notes (should have 0 now: Charlie's note was converted to answer)
        Path notesDir = kbPath.resolve("notes");
        if (Files.exists(notesDir)) {
            List<Path> notes = listFiles(notesDir, "n_*.md");
            assertEquals(0, notes.size(), "Should have 0 notes after batch 2 (Charlie's note converted to answer)");
            logger.info("✓ Notes: {} (Charlie's note was converted to answer a_0002)", notes.size());
        }
        
        // Check people (should have 3 now: Alice_Brown, Bob_Smith, Charlie_White)
        Path peopleDir = kbPath.resolve("people");
        List<Path> people = listDirectories(peopleDir);
        assertEquals(3, people.size(), "Should have 3 people after batch 2");
        logger.info("✓ People: {} (Alice_Brown, Bob_Smith, Charlie_White)", people.size());
        
        assertTrue(Files.exists(peopleDir.resolve("Charlie_White/Charlie_White.md")), "Charlie_White profile should exist");
        
        // Check Alice's profile (should be updated with 2 questions)
        String aliceContent = Files.readString(peopleDir.resolve("Alice_Brown/Alice_Brown.md"));
        assertTrue(aliceContent.contains("questionsAsked: 2"), "Alice should have 2 questions");
        assertTrue(aliceContent.contains("q_0001"), "Alice should have link to q_0001");
        assertTrue(aliceContent.contains("q_0002"), "Alice should have link to q_0002");
        logger.info("✓ Alice_Brown profile updated: 2 questions");
        
        // Check Bob's profile (should be unchanged)
        String bobContent = Files.readString(peopleDir.resolve("Bob_Smith/Bob_Smith.md"));
        assertTrue(bobContent.contains("answersProvided: 1"), "Bob should still have 1 answer");
        logger.info("✓ Bob_Smith profile preserved: 1 answer");
        
        // Check Charlie's profile (note was converted to answer)
        String charlieContent = Files.readString(peopleDir.resolve("Charlie_White/Charlie_White.md"));
        assertTrue(charlieContent.contains("answersProvided: 1"), "Charlie should have 1 answer (converted from note)");
        logger.info("✓ Charlie_White profile: 1 answer (converted from note via Q→A mapping)");
        
        // Check topics (should include docker-related topics with Charlie's note)
        Path topicsDir = kbPath.resolve("topics");
        List<Path> topics = listFiles(topicsDir, "*.md").stream()
                .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                .collect(Collectors.toList());
        assertTrue(topics.size() > 0, "Should have topics after batch 2");
        logger.info("✓ Topics: {}", topics.size());
        
        // Find a docker-related topic and verify it includes Charlie's answer (converted from note)
        boolean dockerTopicFound = false;
        for (Path topic : topics) {
            String topicContent = Files.readString(topic);
            if (topicContent.contains("docker") || topicContent.contains("best-practices")) {
                if (topicContent.contains("![[a_0002]]")) {
                    dockerTopicFound = true;
                    logger.info("✓ Docker-related topic includes Charlie's answer (a_0002, converted from note)");
                    break;
                }
            }
        }
        assertTrue(dockerTopicFound, "Docker topic should include Charlie's answer (a_0002)");
        
        logger.info("✓ Batch 2 verification PASSED\n");
    }
    
    private void verifyBatch3(Path kbPath) throws IOException {
        logger.info("Verifying Batch 3: Answer to old question + new topic");
        
        // Check questions (should have 3 now)
        Path questionsDir = kbPath.resolve("questions");
        List<Path> questions = listFiles(questionsDir, "q_*.md");
        assertEquals(3, questions.size(), "Should have 3 questions after batch 3");
        logger.info("✓ Questions: {} (q_0001, q_0002, q_0003)", questions.size());
        
        // Check that all previous questions still exist
        assertTrue(Files.exists(questionsDir.resolve("q_0001.md")), "q_0001 should still exist");
        assertTrue(Files.exists(questionsDir.resolve("q_0002.md")), "q_0002 should still exist");
        logger.info("✓ q_0001 and q_0002 preserved from previous batches");
        
        // Check new question q_0003
        assertTrue(Files.exists(questionsDir.resolve("q_0003.md")), "q_0003 should exist");
        String q3Content = Files.readString(questionsDir.resolve("q_0003.md"));
        assertTrue(q3Content.contains("Alice"), "q_0003 should be from Alice");
        assertTrue(q3Content.contains("Python"), "q_0003 should be about Python");
        logger.info("✓ q_0003: from Alice about Python optimization");
        
        // Check answers (should have 3 now: a_0001, a_0002 from Charlie, a_0003 from David)
        // Note: David's note was also converted to answer a_0003 via Q→A mapping
        Path answersDir = kbPath.resolve("answers");
        List<Path> answers = listFiles(answersDir, "a_*.md");
        assertEquals(3, answers.size(), "Should have 3 answers after batch 3 (a_0001, a_0002, a_0003 from David's note)");
        logger.info("✓ Answers: {} (a_0001, a_0002, a_0003)", answers.size());
        
        // Check new answer a_0003 (converted from David's note)
        assertTrue(Files.exists(answersDir.resolve("a_0003.md")), "a_0003 should exist");
        String a3Content = Files.readString(answersDir.resolve("a_0003.md"));
        assertTrue(a3Content.contains("David"), "a_0003 should be from David");
        assertTrue(a3Content.contains("multi-stage"), "a_0003 should mention multi-stage builds");
        assertTrue(a3Content.contains("answersQuestion: \"q_0001\""), "a_0003 should answer q_0001");
        logger.info("✓ a_0003: from David about multi-stage builds, answers q_0001");
        
        // Check notes (should have 0: David's note was also converted to answer)
        Path notesDir = kbPath.resolve("notes");
        if (Files.exists(notesDir)) {
            List<Path> notes = listFiles(notesDir, "n_*.md");
            assertEquals(0, notes.size(), "Should have 0 notes after batch 3 (David's note converted to answer)");
            logger.info("✓ Notes: {} (David's note was converted to answer a_0003)", notes.size());
        }
        
        // Check people (should have 4 now: Alice_Brown, Bob_Smith, Charlie_White, David_Green)
        Path peopleDir = kbPath.resolve("people");
        List<Path> people = listDirectories(peopleDir);
        assertEquals(4, people.size(), "Should have 4 people after batch 3");
        logger.info("✓ People: {} (Alice_Brown, Bob_Smith, Charlie_White, David_Green)", people.size());
        
        assertTrue(Files.exists(peopleDir.resolve("David_Green/David_Green.md")), "David_Green profile should exist");
        
        // Check Alice's profile (should be updated with 3 questions)
        String aliceContent = Files.readString(peopleDir.resolve("Alice_Brown/Alice_Brown.md"));
        assertTrue(aliceContent.contains("questionsAsked: 3"), "Alice should have 3 questions");
        assertTrue(aliceContent.contains("q_0001"), "Alice should have link to q_0001");
        assertTrue(aliceContent.contains("q_0002"), "Alice should have link to q_0002");
        assertTrue(aliceContent.contains("q_0003"), "Alice should have link to q_0003");
        logger.info("✓ Alice_Brown profile updated: 3 questions");
        
        // Check Bob's profile (should be unchanged)
        String bobContent = Files.readString(peopleDir.resolve("Bob_Smith/Bob_Smith.md"));
        assertTrue(bobContent.contains("answersProvided: 1"), "Bob should still have 1 answer");
        logger.info("✓ Bob_Smith profile preserved: 1 answer");
        
        // Check Charlie's profile (note was converted to answer in batch 2)
        String charlieContent = Files.readString(peopleDir.resolve("Charlie_White/Charlie_White.md"));
        assertTrue(charlieContent.contains("answersProvided: 1"), "Charlie should have 1 answer (converted from note)");
        logger.info("✓ Charlie_White profile: 1 answer (converted from note via Q→A mapping)");
        
        // Check David's profile
        String davidContent = Files.readString(peopleDir.resolve("David_Green/David_Green.md"));
        assertTrue(davidContent.contains("answersProvided: 1"), "David should have 1 answer");
        logger.info("✓ David_Green profile: 1 answer");
        
        // Check topics
        Path topicsDir = kbPath.resolve("topics");
        List<Path> topics = listFiles(topicsDir, "*.md").stream()
                .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                .collect(Collectors.toList());
        assertTrue(topics.size() > 0, "Should have topics after batch 3");
        logger.info("✓ Topics: {}", topics.size());
        
        // Verify David's answer exists
        assertTrue(Files.exists(answersDir.resolve("a_0002.md")), "David's answer should exist");
        logger.info("✓ David's answer (a_0002) exists");
        
        // Check INDEX.md statistics
        Path indexFile = kbPath.resolve("INDEX.md");
        if (Files.exists(indexFile)) {
            String indexContent = Files.readString(indexFile);
            logger.info("✓ INDEX.md exists and updated");
            assertTrue(indexContent.contains("Total Questions"), "INDEX should have questions count");
            assertTrue(indexContent.contains("Total Answers"), "INDEX should have answers count");
            assertTrue(indexContent.contains("Total Notes"), "INDEX should have notes count");
            assertTrue(indexContent.contains("Total Contributors"), "INDEX should have contributors count");
        }
        
        logger.info("✓ Batch 3 verification PASSED\n");
    }
    
    private List<Path> listFiles(Path dir, String pattern) throws IOException {
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(pattern.replace("*", ".*")))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
    
    private List<Path> listDirectories(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}

