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
    
    @Test
    void testCollectPersonStatsFromFiles_NormalizesPersonNames() throws IOException {
        // REGRESSION TEST: Ensure person names with spaces are normalized to underscores
        // This prevents mismatch between peopleFromCurrentAnalysis and personStats keys
        
        // Create directories
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Path notesDir = tempDir.resolve("notes");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);
        
        // Create files with person names containing spaces
        createQuestionFile(questionsDir, "q_0001.md", "John Smith");
        createAnswerFile(answersDir, "a_0001.md", "Jane Doe");
        createNoteFile(notesDir, "n_0001.md", "Bob Johnson");
        
        Map<String, PersonStatsCollector.PersonStats> stats = 
            collector.collectPersonStatsFromFiles(tempDir);
        
        // Keys should be normalized (spaces replaced with underscores)
        assertEquals(3, stats.size());
        assertTrue(stats.containsKey("John_Smith"), "Should contain normalized key 'John_Smith'");
        assertTrue(stats.containsKey("Jane_Doe"), "Should contain normalized key 'Jane_Doe'");
        assertTrue(stats.containsKey("Bob_Johnson"), "Should contain normalized key 'Bob_Johnson'");
        
        // Should NOT contain keys with spaces
        assertFalse(stats.containsKey("John Smith"), "Should NOT contain unnormalized key 'John Smith'");
        assertFalse(stats.containsKey("Jane Doe"), "Should NOT contain unnormalized key 'Jane Doe'");
        assertFalse(stats.containsKey("Bob Johnson"), "Should NOT contain unnormalized key 'Bob Johnson'");
        
        // Stats should be correct
        assertEquals(1, stats.get("John_Smith").questions);
        assertEquals(1, stats.get("Jane_Doe").answers);
        assertEquals(1, stats.get("Bob_Johnson").notes);
    }
    
    @Test
    void testCollectPersonStatsFromFiles_MixedNormalizationConsistency() throws IOException {
        // REGRESSION TEST: Same person with different name formats should be counted as one person
        
        // Create directories
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        
        // "Aliaksandr Tarasevich" (with space in source file)
        createQuestionFile(questionsDir, "q_0001.md", "Aliaksandr Tarasevich");
        createAnswerFile(answersDir, "a_0001.md", "Aliaksandr Tarasevich");
        
        Map<String, PersonStatsCollector.PersonStats> stats = 
            collector.collectPersonStatsFromFiles(tempDir);
        
        // Should have exactly 1 person with normalized name
        assertEquals(1, stats.size());
        assertTrue(stats.containsKey("Aliaksandr_Tarasevich"));
        
        // Both question and answer should be attributed to the same normalized person
        PersonStatsCollector.PersonStats aliaksandrStats = stats.get("Aliaksandr_Tarasevich");
        assertEquals(1, aliaksandrStats.questions, "Should have 1 question");
        assertEquals(1, aliaksandrStats.answers, "Should have 1 answer");
        assertEquals(0, aliaksandrStats.notes, "Should have 0 notes");
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

