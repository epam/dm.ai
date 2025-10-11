package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.PersonContributions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KBStructureBuilder
 */
class KBStructureBuilderTest {

    private KBStructureBuilder builder;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        builder = new KBStructureBuilder();
    }
    
    @Test
    void testBuildPersonProfile_InitialCreation() throws IOException {
        // Given: A person with 2 questions, 1 answer, 0 notes
        String personName = "Alice Brown";
        String sourceName = "test_source";
        int questions = 2;
        int answers = 1;
        int notes = 0;
        
        PersonContributions contributions = new PersonContributions();
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "docker", "2024-10-10"));
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0002", "kubernetes", "2024-10-11"));
        contributions.getAnswers().add(new PersonContributions.ContributionItem("a_0001", "docker", "2024-10-12"));
        
        // When: Building person profile
        builder.buildPersonProfile(personName, tempDir, sourceName, questions, answers, notes, contributions);
        
        // Then: File should be created with correct counts in frontmatter
        Path personFile = tempDir.resolve("people/Alice_Brown/Alice_Brown.md");
        assertTrue(Files.exists(personFile), "Person file should be created");
        
        String content = Files.readString(personFile);
        
        // Verify frontmatter contains correct counts
        assertTrue(content.contains("questionsAsked: 2"), "Should have questionsAsked: 2");
        assertTrue(content.contains("answersProvided: 1"), "Should have answersProvided: 1");
        assertTrue(content.contains("notesContributed: 0"), "Should have notesContributed: 0");
        
        // Verify detailed contributions are present
        assertTrue(content.contains("[[../../questions/q_0001|q_0001]]"), "Should contain q_0001 link");
        assertTrue(content.contains("[[../../questions/q_0002|q_0002]]"), "Should contain q_0002 link");
        assertTrue(content.contains("[[../../answers/a_0001|a_0001]]"), "Should contain a_0001 link");
    }
    
    @Test
    void testBuildPersonProfile_IncrementalUpdate() throws IOException {
        // Given: A person profile already exists with 1 question
        String personName = "Alice Brown";
        String sourceName = "test_source";
        
        // Step 1: Create initial profile with 1 question
        PersonContributions initialContributions = new PersonContributions();
        initialContributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "docker", "2024-10-10"));
        
        builder.buildPersonProfile(personName, tempDir, sourceName, 1, 0, 0, initialContributions);
        
        Path personFile = tempDir.resolve("people/Alice_Brown/Alice_Brown.md");
        String initialContent = Files.readString(personFile);
        assertTrue(initialContent.contains("questionsAsked: 1"), "Initial profile should have 1 question");
        
        // Step 2: Update profile with 2 questions (incremental update)
        PersonContributions updatedContributions = new PersonContributions();
        updatedContributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "docker", "2024-10-10"));
        updatedContributions.getQuestions().add(new PersonContributions.ContributionItem("q_0002", "kubernetes", "2024-10-11"));
        
        // When: Building person profile again with updated counts
        builder.buildPersonProfile(personName, tempDir, sourceName, 2, 0, 0, updatedContributions);
        
        // Then: File should be updated with new counts
        String updatedContent = Files.readString(personFile);
        
        // Verify frontmatter was updated
        assertTrue(updatedContent.contains("questionsAsked: 2"), 
                   "After incremental update, should have questionsAsked: 2");
        assertFalse(updatedContent.contains("questionsAsked: 1"), 
                    "Old count should be replaced");
        
        // Verify both questions are present in contributions
        assertTrue(updatedContent.contains("[[../../questions/q_0001|q_0001]]"), 
                   "Should contain q_0001 link");
        assertTrue(updatedContent.contains("[[../../questions/q_0002|q_0002]]"), 
                   "Should contain q_0002 link");
    }
    
    @Test
    void testBuildPersonProfile_MultipleIncrementalUpdates() throws IOException {
        // Given: A person profile that will be updated multiple times
        String personName = "Bob Smith";
        String sourceName = "test_source";
        Path personFile = tempDir.resolve("people/Bob_Smith/Bob_Smith.md");
        
        // Step 1: Create initial profile with 1 answer
        PersonContributions batch1 = new PersonContributions();
        batch1.getAnswers().add(new PersonContributions.ContributionItem("a_0001", "docker", "2024-10-10"));
        builder.buildPersonProfile(personName, tempDir, sourceName, 0, 1, 0, batch1);
        
        String content1 = Files.readString(personFile);
        assertTrue(content1.contains("answersProvided: 1"), "Batch 1: Should have 1 answer");
        
        // Step 2: Add 1 more answer (total 2)
        PersonContributions batch2 = new PersonContributions();
        batch2.getAnswers().add(new PersonContributions.ContributionItem("a_0001", "docker", "2024-10-10"));
        batch2.getAnswers().add(new PersonContributions.ContributionItem("a_0002", "kubernetes", "2024-10-11"));
        builder.buildPersonProfile(personName, tempDir, sourceName, 0, 2, 0, batch2);
        
        String content2 = Files.readString(personFile);
        assertTrue(content2.contains("answersProvided: 2"), "Batch 2: Should have 2 answers");
        
        // Step 3: Add 1 more answer and 1 note (total 3 answers, 1 note)
        PersonContributions batch3 = new PersonContributions();
        batch3.getAnswers().add(new PersonContributions.ContributionItem("a_0001", "docker", "2024-10-10"));
        batch3.getAnswers().add(new PersonContributions.ContributionItem("a_0002", "kubernetes", "2024-10-11"));
        batch3.getAnswers().add(new PersonContributions.ContributionItem("a_0003", "python", "2024-10-12"));
        batch3.getNotes().add(new PersonContributions.ContributionItem("n_0001", "docker", "2024-10-13"));
        builder.buildPersonProfile(personName, tempDir, sourceName, 0, 3, 1, batch3);
        
        String content3 = Files.readString(personFile);
        assertTrue(content3.contains("answersProvided: 3"), "Batch 3: Should have 3 answers");
        assertTrue(content3.contains("notesContributed: 1"), "Batch 3: Should have 1 note");
        
        // Verify all contributions are present
        assertTrue(content3.contains("[[../../answers/a_0001|a_0001]]"), "Should contain a_0001");
        assertTrue(content3.contains("[[../../answers/a_0002|a_0002]]"), "Should contain a_0002");
        assertTrue(content3.contains("[[../../answers/a_0003|a_0003]]"), "Should contain a_0003");
        assertTrue(content3.contains("[[../../notes/n_0001|n_0001]]"), "Should contain n_0001");
    }
    
    @Test
    void testBuildPersonProfile_UpdateWithoutContributions() throws IOException {
        // Given: A person profile exists
        String personName = "Charlie White";
        String sourceName = "test_source";
        
        // Step 1: Create initial profile
        builder.buildPersonProfile(personName, tempDir, sourceName, 1, 0, 0);
        
        Path personFile = tempDir.resolve("people/Charlie_White/Charlie_White.md");
        String initialContent = Files.readString(personFile);
        assertTrue(initialContent.contains("questionsAsked: 1"), "Should have 1 question");
        
        // Step 2: Update with different counts
        builder.buildPersonProfile(personName, tempDir, sourceName, 2, 1, 1);
        
        String updatedContent = Files.readString(personFile);
        assertTrue(updatedContent.contains("questionsAsked: 2"), "Should update to 2 questions");
        assertTrue(updatedContent.contains("answersProvided: 1"), "Should update to 1 answer");
        assertTrue(updatedContent.contains("notesContributed: 1"), "Should update to 1 note");
    }
}
