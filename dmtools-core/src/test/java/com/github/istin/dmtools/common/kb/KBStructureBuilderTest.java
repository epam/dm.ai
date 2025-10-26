package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

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
    
    @Test
    void testBuildPersonProfile_SourceOnlyAddedWhenHasContributions() throws IOException {
        // Given: Initial profile from source1
        String personName = "David Jones";
        builder.buildPersonProfile(personName, tempDir, "source1", 1, 0, 0);
        
        Path personFile = tempDir.resolve("people/David_Jones/David_Jones.md");
        String content1 = Files.readString(personFile);
        assertTrue(content1.contains("source1"), "Should contain source1");
        
        // When: Update with source2 passing null (no contributions)
        builder.buildPersonProfile(personName, tempDir, null, 1, 0, 0);
        
        // Then: source2 should NOT be added
        String content2 = Files.readString(personFile);
        assertTrue(content2.contains("source1"), "Should still contain source1");
        assertFalse(content2.contains("source2"), "Should NOT contain source2");
        
        // When: Update with source3 (has contributions)
        builder.buildPersonProfile(personName, tempDir, "source3", 2, 1, 0);
        
        // Then: source3 should be added
        String content3 = Files.readString(personFile);
        assertTrue(content3.contains("source1"), "Should still contain source1");
        assertTrue(content3.contains("source3"), "Should contain source3");
    }
    
    @Test
    void testBuildTopicFiles_SourceOnlyAddedWhenHasContributions() throws IOException {
        // Given: Analysis with topics
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setTopics(Arrays.asList("docker", "kubernetes"));
        q1.setAuthor("Alice");
        q1.setArea("DevOps");
        analysis1.setQuestions(Arrays.asList(q1));
        
        // When: Build topics with source1
        builder.buildTopicFiles(analysis1, tempDir, "source1");
        
        // Then: Topics should contain source1
        Path dockerTopic = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopic), "Docker topic should exist");
        String dockerContent1 = Files.readString(dockerTopic);
        assertTrue(dockerContent1.contains("source1"), "Docker topic should contain source1");
        
        // When: Build topics with analysis that doesn't have "docker" topic (only kubernetes)
        AnalysisResult analysis2 = new AnalysisResult();
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setTopics(Arrays.asList("kubernetes")); // Only kubernetes, not docker
        q2.setAuthor("Bob");
        q2.setArea("DevOps");
        analysis2.setQuestions(Arrays.asList(q2));
        
        builder.buildTopicFiles(analysis2, tempDir, "source2");
        
        // Then: docker topic should NOT contain source2 (no contributions)
        String dockerContent2 = Files.readString(dockerTopic);
        assertTrue(dockerContent2.contains("source1"), "Docker topic should still contain source1");
        assertFalse(dockerContent2.contains("source2"), "Docker topic should NOT contain source2");
        
        // But kubernetes topic should contain both sources
        Path k8sTopic = tempDir.resolve("topics/kubernetes.md");
        String k8sContent = Files.readString(k8sTopic);
        assertTrue(k8sContent.contains("source1"), "Kubernetes topic should contain source1");
        assertTrue(k8sContent.contains("source2"), "Kubernetes topic should contain source2");
    }
    
    @Test
    void testBuildAreaStructure_SourceOnlyAddedWhenHasContributions() throws IOException {
        // Given: Analysis with area "DevOps"
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("DevOps");
        q1.setAuthor("Alice");
        analysis1.setQuestions(Arrays.asList(q1));
        
        // When: Build area with source1
        builder.buildAreaStructure(analysis1, tempDir, "source1");
        
        // Then: Area should contain source1
        Path devopsArea = tempDir.resolve("areas/devops/devops.md");
        assertTrue(Files.exists(devopsArea), "DevOps area should exist");
        String areaContent1 = Files.readString(devopsArea);
        assertTrue(areaContent1.contains("source1"), "DevOps area should contain source1");
        
        // When: Build area with analysis that has different area (not DevOps)
        AnalysisResult analysis2 = new AnalysisResult();
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("Frontend"); // Different area
        q2.setAuthor("Bob");
        analysis2.setQuestions(Arrays.asList(q2));
        
        builder.buildAreaStructure(analysis2, tempDir, "source2");
        
        // Then: DevOps area should NOT contain source2 (no contributions)
        String areaContent2 = Files.readString(devopsArea);
        assertTrue(areaContent2.contains("source1"), "DevOps area should still contain source1");
        assertFalse(areaContent2.contains("source2"), "DevOps area should NOT contain source2");
        
        // But Frontend area should exist with source2
        Path frontendArea = tempDir.resolve("areas/frontend/frontend.md");
        assertTrue(Files.exists(frontendArea), "Frontend area should exist");
        String frontendContent = Files.readString(frontendArea);
        assertTrue(frontendContent.contains("source2"), "Frontend area should contain source2");
    }
    
    @Test
    void testMultipleSourcesAccumulation() throws IOException {
        // Test that sources accumulate correctly over multiple processing runs
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setTopics(Arrays.asList("testing"));
        q1.setArea("QA");
        q1.setAuthor("Alice");
        analysis1.setQuestions(Arrays.asList(q1));
        
        // Process with source1
        builder.buildTopicFiles(analysis1, tempDir, "source1");
        builder.buildAreaStructure(analysis1, tempDir, "source1");
        builder.buildPersonProfile("Alice", tempDir, "source1", 1, 0, 0);
        
        // Process with source2 (same entities)
        AnalysisResult analysis2 = new AnalysisResult();
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setTopics(Arrays.asList("testing"));
        a1.setArea("QA");
        a1.setAuthor("Alice");
        analysis2.setAnswers(Arrays.asList(a1));
        
        builder.buildTopicFiles(analysis2, tempDir, "source2");
        builder.buildAreaStructure(analysis2, tempDir, "source2");
        builder.buildPersonProfile("Alice", tempDir, "source2", 1, 1, 0);
        
        // Process with source3 (same entities)
        AnalysisResult analysis3 = new AnalysisResult();
        Note n1 = new Note();
        n1.setId("n_0001");
        n1.setTopics(Arrays.asList("testing"));
        n1.setArea("QA");
        n1.setAuthor("Alice");
        analysis3.setNotes(Arrays.asList(n1));
        
        builder.buildTopicFiles(analysis3, tempDir, "source3");
        builder.buildAreaStructure(analysis3, tempDir, "source3");
        builder.buildPersonProfile("Alice", tempDir, "source3", 1, 1, 1);
        
        // Verify all sources are present in topic
        Path topicFile = tempDir.resolve("topics/testing.md");
        String topicContent = Files.readString(topicFile);
        assertTrue(topicContent.contains("source1"), "Topic should contain source1");
        assertTrue(topicContent.contains("source2"), "Topic should contain source2");
        assertTrue(topicContent.contains("source3"), "Topic should contain source3");
        
        // Verify all sources are present in area
        Path areaFile = tempDir.resolve("areas/qa/qa.md");
        String areaContent = Files.readString(areaFile);
        assertTrue(areaContent.contains("source1"), "Area should contain source1");
        assertTrue(areaContent.contains("source2"), "Area should contain source2");
        assertTrue(areaContent.contains("source3"), "Area should contain source3");
        
        // Verify all sources are present in person
        Path personFile = tempDir.resolve("people/Alice/Alice.md");
        String personContent = Files.readString(personFile);
        assertTrue(personContent.contains("source1"), "Person should contain source1");
        assertTrue(personContent.contains("source2"), "Person should contain source2");
        assertTrue(personContent.contains("source3"), "Person should contain source3");
    }
}
