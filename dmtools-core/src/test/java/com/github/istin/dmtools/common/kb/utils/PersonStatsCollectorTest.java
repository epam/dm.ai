package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.PersonContributions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PersonStatsCollectorTest {
    
    private PersonStatsCollector collector;
    private KBFileParser parser;
    private KBStructureBuilder structureBuilder;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        parser = new KBFileParser();
        structureBuilder = new KBStructureBuilder();
        collector = new PersonStatsCollector(parser, structureBuilder);
    }
    
    @Test
    void testCollectPersonStatsFromFiles_EmptyKB() throws IOException {
        Map<String, PersonStatsCollector.PersonStats> stats = 
            collector.collectPersonStatsFromFiles(tempDir);
        
        assertTrue(stats.isEmpty());
    }
    
    @Test
    void testCollectPersonStatsFromFiles_WithQuestions() throws IOException {
        // Create questions directory with test files
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        
        // Alice asks 2 questions
        createQuestionFile(questionsDir, "q_0001.md", "Alice");
        createQuestionFile(questionsDir, "q_0002.md", "Alice");
        
        // Bob asks 1 question
        createQuestionFile(questionsDir, "q_0003.md", "Bob");
        
        Map<String, PersonStatsCollector.PersonStats> stats = 
            collector.collectPersonStatsFromFiles(tempDir);
        
        assertEquals(2, stats.size());
        assertEquals(2, stats.get("Alice").questions);
        assertEquals(0, stats.get("Alice").answers);
        assertEquals(0, stats.get("Alice").notes);
        assertEquals(1, stats.get("Bob").questions);
    }
    
    @Test
    void testCollectPersonStatsFromFiles_WithAnswersAndNotes() throws IOException {
        // Create directories
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Path notesDir = tempDir.resolve("notes");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);
        
        // Alice: 1 question, 2 answers, 1 note
        createQuestionFile(questionsDir, "q_0001.md", "Alice");
        createAnswerFile(answersDir, "a_0001.md", "Alice");
        createAnswerFile(answersDir, "a_0002.md", "Alice");
        createNoteFile(notesDir, "n_0001.md", "Alice");
        
        // Bob: 0 questions, 1 answer, 2 notes
        createAnswerFile(answersDir, "a_0003.md", "Bob");
        createNoteFile(notesDir, "n_0002.md", "Bob");
        createNoteFile(notesDir, "n_0003.md", "Bob");
        
        Map<String, PersonStatsCollector.PersonStats> stats = 
            collector.collectPersonStatsFromFiles(tempDir);
        
        assertEquals(2, stats.size());
        
        assertEquals(1, stats.get("Alice").questions);
        assertEquals(2, stats.get("Alice").answers);
        assertEquals(1, stats.get("Alice").notes);
        
        assertEquals(0, stats.get("Bob").questions);
        assertEquals(1, stats.get("Bob").answers);
        assertEquals(2, stats.get("Bob").notes);
    }
    
    @Test
    void testCollectPersonContributionsFromFiles() throws IOException {
        // Create directories
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        
        // Alice contributes to Docker and Kubernetes
        createQuestionFileWithTopics(questionsDir, "q_0001.md", "Alice", "Docker", "Kubernetes");
        createAnswerFileWithTopics(answersDir, "a_0001.md", "Alice", "Docker");
        
        Map<String, PersonContributions> contributions = 
            collector.collectPersonContributionsFromFiles(tempDir);
        
        assertEquals(1, contributions.size());
        
        PersonContributions aliceContribs = contributions.get("Alice");
        assertNotNull(aliceContribs);
        
        // Alice has 1 question (with 2 topics = 2 contribution items)
        assertEquals(2, aliceContribs.getQuestions().size());
        
        // Alice has 1 answer (with 1 topic = 1 contribution item)
        assertEquals(1, aliceContribs.getAnswers().size());
        
        // Check topic contributions are calculated
        assertNotNull(aliceContribs.getTopics());
        assertTrue(aliceContribs.getTopics().size() > 0);
    }
    
    @Test
    void testCollectPersonContributionsFromFiles_EmptyKB() throws IOException {
        Map<String, PersonContributions> contributions = 
            collector.collectPersonContributionsFromFiles(tempDir);
        
        assertTrue(contributions.isEmpty());
    }
    
    // Helper methods to create test files
    
    private void createQuestionFile(Path dir, String filename, String author) throws IOException {
        String content = String.format("""
                ---
                author: "%s"
                date: "2025-01-15"
                area: "Technology"
                ---
                # Question
                Test question content
                """, author);
        Files.writeString(dir.resolve(filename), content);
    }
    
    private void createQuestionFileWithTopics(Path dir, String filename, String author, String... topics) throws IOException {
        String topicsStr = "[" + String.join(", ", topics) + "]";
        String content = String.format("""
                ---
                author: "%s"
                date: "2025-01-15"
                area: "Technology"
                topics: %s
                ---
                # Question
                Test question content
                """, author, topicsStr);
        Files.writeString(dir.resolve(filename), content);
    }
    
    private void createAnswerFile(Path dir, String filename, String author) throws IOException {
        String content = String.format("""
                ---
                author: "%s"
                date: "2025-01-15"
                area: "Technology"
                ---
                # Answer
                Test answer content
                """, author);
        Files.writeString(dir.resolve(filename), content);
    }
    
    private void createAnswerFileWithTopics(Path dir, String filename, String author, String... topics) throws IOException {
        String topicsStr = "[" + String.join(", ", topics) + "]";
        String content = String.format("""
                ---
                author: "%s"
                date: "2025-01-15"
                area: "Technology"
                topics: %s
                ---
                # Answer
                Test answer content
                """, author, topicsStr);
        Files.writeString(dir.resolve(filename), content);
    }
    
    private void createNoteFile(Path dir, String filename, String author) throws IOException {
        String content = String.format("""
                ---
                author: "%s"
                date: "2025-01-15"
                area: "Technology"
                ---
                # Note
                Test note content
                """, author);
        Files.writeString(dir.resolve(filename), content);
    }
}

