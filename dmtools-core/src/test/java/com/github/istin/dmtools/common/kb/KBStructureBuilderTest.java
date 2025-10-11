package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KBStructureBuilder standalone answer logic
 */
public class KBStructureBuilderTest {

    @TempDir
    Path tempDir;

    private KBStructureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new KBStructureBuilder();
    }

    /**
     * Test: Answer and Question in SAME topic → Answer should NOT appear as standalone
     */
    @Test
    void testAnswerAndQuestionInSameTopic_NotStandalone() throws IOException {
        // Arrange: Create Q and A in same topic "docker"
        AnalysisResult analysis = new AnalysisResult();
        
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setText("How to optimize Docker?");
        q1.setDate("2024-10-10T10:00:00Z");
        q1.setArea("docker");
        q1.setTopics(Arrays.asList("docker", "optimization"));
        q1.setTags(Arrays.asList("docker"));
        q1.setAnsweredBy("a_0001");
        q1.setLinks(Collections.emptyList());
        
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Bob");
        a1.setText("Use multi-stage builds");
        a1.setDate("2024-10-10T10:05:00Z");
        a1.setArea("docker");
        a1.setTopics(Arrays.asList("docker", "optimization"));
        a1.setTags(Arrays.asList("multi-stage"));
        a1.setAnswersQuestion("q_0001");
        a1.setQuality(0.9);
        a1.setLinks(Collections.emptyList());
        
        analysis.setQuestions(Arrays.asList(q1));
        analysis.setAnswers(Arrays.asList(a1));
        analysis.setNotes(Collections.emptyList());

        // Act: Build topics
        builder.buildTopicFiles(analysis, tempDir, "test");

        // Assert: Check "docker" topic file
        Path dockerTopicFile = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopicFile), "Docker topic file should exist");
        
        String content = Files.readString(dockerTopicFile);
        
        // Should have "Questions with Answers" section
        assertTrue(content.contains("## Questions with Answers"), "Should have Questions with Answers section");
        assertTrue(content.contains("![[q_0001]]"), "Should embed question");
        
        // Should NOT have "Additional Answers" section (answer is embedded in question)
        assertFalse(content.contains("## Additional Answers"), "Should NOT have Additional Answers section");
        assertFalse(content.contains("![[a_0001]]") && content.contains("Additional Answers"), 
                "Answer should not appear as standalone in Additional Answers");
    }

    /**
     * Test: Answer in topic X, Question in topic Y → Answer SHOULD appear as standalone in topic X
     */
    @Test
    void testAnswerInTopicX_QuestionInTopicY_IsStandalone() throws IOException {
        // Arrange: Q in "performance", A in "docker"
        AnalysisResult analysis = new AnalysisResult();
        
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setText("How to improve performance?");
        q1.setDate("2024-10-10T10:00:00Z");
        q1.setArea("performance");
        q1.setTopics(Arrays.asList("performance", "optimization"));
        q1.setTags(Arrays.asList("performance"));
        q1.setAnsweredBy("a_0001");
        q1.setLinks(Collections.emptyList());
        
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Bob");
        a1.setText("Use Docker multi-stage builds");
        a1.setDate("2024-10-10T10:05:00Z");
        a1.setArea("docker");
        a1.setTopics(Arrays.asList("docker", "best-practices")); // Different topics!
        a1.setTags(Arrays.asList("multi-stage"));
        a1.setAnswersQuestion("q_0001");
        a1.setQuality(0.9);
        a1.setLinks(Collections.emptyList());
        
        analysis.setQuestions(Arrays.asList(q1));
        analysis.setAnswers(Arrays.asList(a1));
        analysis.setNotes(Collections.emptyList());

        // Act: Build topics
        builder.buildTopicFiles(analysis, tempDir, "test");

        // Assert: Check "docker" topic file - should have answer as standalone
        Path dockerTopicFile = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopicFile), "Docker topic file should exist");
        
        String dockerContent = Files.readString(dockerTopicFile);
        
        // Should have "Additional Answers" section
        assertTrue(dockerContent.contains("## Additional Answers"), 
                "Docker topic should have Additional Answers section");
        assertTrue(dockerContent.contains("![[a_0001]]"), 
                "Answer should appear as standalone in docker topic");
        
        // Assert: Check "performance" topic file - should have question
        Path perfTopicFile = tempDir.resolve("topics/performance.md");
        assertTrue(Files.exists(perfTopicFile), "Performance topic file should exist");
        
        String perfContent = Files.readString(perfTopicFile);
        
        // Should have question with embedded answer
        assertTrue(perfContent.contains("## Questions with Answers"), 
                "Performance topic should have Questions with Answers section");
        assertTrue(perfContent.contains("![[q_0001]]"), 
                "Question should be in performance topic");
    }

    /**
     * Test: Answer without Question → Should appear as standalone
     */
    @Test
    void testAnswerWithoutQuestion_IsStandalone() throws IOException {
        // Arrange: A without Q
        AnalysisResult analysis = new AnalysisResult();
        
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Bob");
        a1.setText("Best practice: use .dockerignore");
        a1.setDate("2024-10-10T10:05:00Z");
        a1.setArea("docker");
        a1.setTopics(Arrays.asList("docker", "best-practices"));
        a1.setTags(Arrays.asList("dockerignore"));
        a1.setAnswersQuestion(""); // No question!
        a1.setQuality(0.9);
        a1.setLinks(Collections.emptyList());
        
        analysis.setQuestions(Collections.emptyList());
        analysis.setAnswers(Arrays.asList(a1));
        analysis.setNotes(Collections.emptyList());

        // Act: Build topics
        builder.buildTopicFiles(analysis, tempDir, "test");

        // Assert: Check "docker" topic file
        Path dockerTopicFile = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopicFile), "Docker topic file should exist");
        
        String content = Files.readString(dockerTopicFile);
        
        // Should have "Additional Answers" section
        assertTrue(content.contains("## Additional Answers"), 
                "Should have Additional Answers section for standalone answer");
        assertTrue(content.contains("![[a_0001]]"), 
                "Standalone answer should appear in Additional Answers");
    }

    /**
     * Test: Incremental update - existing answer file should be read correctly
     */
    @Test
    void testIncrementalUpdate_ExistingAnswerFile() throws IOException {
        // Arrange: Create existing answer file in "docker" topic
        Path answersDir = tempDir.resolve("answers");
        Files.createDirectories(answersDir);
        
        String existingAnswer = """
                ---
                id: "a_0001"
                type: "answer"
                author: "Bob"
                date: "2024-10-10T10:05:00Z"
                area: "docker"
                topics: ["docker", "base-image"]
                quality: 0.9
                answersQuestion: "q_0001"
                source: "test"
                tags: ["#answer", "#test"]
                ---
                
                # Answer: a_0001
                
                Use FROM python:3.11-slim
                """;
        
        Files.writeString(answersDir.resolve("a_0001.md"), existingAnswer);
        
        // Create new analysis WITHOUT q_0001 (it's in different topic)
        AnalysisResult analysis = new AnalysisResult();
        
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setAuthor("Charlie");
        q2.setText("How to optimize builds?");
        q2.setDate("2024-10-11T10:00:00Z");
        q2.setArea("performance");
        q2.setTopics(Arrays.asList("performance", "builds")); // Different topic!
        q2.setTags(Arrays.asList("optimization"));
        q2.setAnsweredBy("");
        q2.setLinks(Collections.emptyList());
        
        analysis.setQuestions(Arrays.asList(q2));
        analysis.setAnswers(Collections.emptyList());
        analysis.setNotes(Collections.emptyList());

        // Act: Build topics (incremental - will read existing files)
        builder.buildTopicFiles(analysis, tempDir, "test");

        // Assert: Check "docker" topic file
        Path dockerTopicFile = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopicFile), "Docker topic file should exist");
        
        String dockerContent = Files.readString(dockerTopicFile);
        
        // Should have "Additional Answers" section because q_0001 is NOT in docker topic
        assertTrue(dockerContent.contains("## Additional Answers"), 
                "Docker topic should have Additional Answers section");
        assertTrue(dockerContent.contains("![[a_0001]]"), 
                "Existing answer should appear as standalone in docker topic");
    }

    /**
     * Test: Multiple answers to same question in same topic → None should be standalone
     */
    @Test
    void testMultipleAnswersToSameQuestion_NoneStandalone() throws IOException {
        // Arrange: Q with 2 answers in same topic
        AnalysisResult analysis = new AnalysisResult();
        
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setText("How to optimize Docker?");
        q1.setDate("2024-10-10T10:00:00Z");
        q1.setArea("docker");
        q1.setTopics(Arrays.asList("docker", "optimization"));
        q1.setTags(Arrays.asList("docker"));
        q1.setAnsweredBy("a_0001");
        q1.setLinks(Collections.emptyList());
        
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Bob");
        a1.setText("Use multi-stage builds");
        a1.setDate("2024-10-10T10:05:00Z");
        a1.setArea("docker");
        a1.setTopics(Arrays.asList("docker", "optimization"));
        a1.setTags(Arrays.asList("multi-stage"));
        a1.setAnswersQuestion("q_0001");
        a1.setQuality(0.9);
        a1.setLinks(Collections.emptyList());
        
        Answer a2 = new Answer();
        a2.setId("a_0002");
        a2.setAuthor("Charlie");
        a2.setText("Use layer caching");
        a2.setDate("2024-10-10T10:10:00Z");
        a2.setArea("docker");
        a2.setTopics(Arrays.asList("docker", "optimization"));
        a2.setTags(Arrays.asList("caching"));
        a2.setAnswersQuestion("q_0001");
        a2.setQuality(0.85);
        a2.setLinks(Collections.emptyList());
        
        analysis.setQuestions(Arrays.asList(q1));
        analysis.setAnswers(Arrays.asList(a1, a2));
        analysis.setNotes(Collections.emptyList());

        // Act: Build topics
        builder.buildTopicFiles(analysis, tempDir, "test");

        // Assert: Check "docker" topic file
        Path dockerTopicFile = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopicFile), "Docker topic file should exist");
        
        String content = Files.readString(dockerTopicFile);
        
        // Should have "Questions with Answers" section
        assertTrue(content.contains("## Questions with Answers"), "Should have Questions with Answers section");
        assertTrue(content.contains("![[q_0001]]"), "Should embed question");
        
        // Should NOT have "Additional Answers" section (both answers are embedded in question)
        assertFalse(content.contains("## Additional Answers"), "Should NOT have Additional Answers section");
    }

    /**
     * Test: Mixed scenario - some answers standalone, some not
     */
    @Test
    void testMixedScenario_SomeStandaloneSomeNot() throws IOException {
        // Arrange: Complex scenario
        AnalysisResult analysis = new AnalysisResult();
        
        // Q1 in "docker" topic
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setText("Docker question");
        q1.setDate("2024-10-10T10:00:00Z");
        q1.setArea("docker");
        q1.setTopics(Arrays.asList("docker"));
        q1.setTags(Arrays.asList("docker"));
        q1.setAnsweredBy("a_0001");
        q1.setLinks(Collections.emptyList());
        
        // A1 in "docker" topic, answers Q1 → NOT standalone
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Bob");
        a1.setText("Docker answer");
        a1.setDate("2024-10-10T10:05:00Z");
        a1.setArea("docker");
        a1.setTopics(Arrays.asList("docker"));
        a1.setTags(Arrays.asList("docker"));
        a1.setAnswersQuestion("q_0001");
        a1.setQuality(0.9);
        a1.setLinks(Collections.emptyList());
        
        // A2 in "docker" topic, no question → standalone
        Answer a2 = new Answer();
        a2.setId("a_0002");
        a2.setAuthor("Charlie");
        a2.setText("Docker tip");
        a2.setDate("2024-10-10T10:10:00Z");
        a2.setArea("docker");
        a2.setTopics(Arrays.asList("docker", "best-practices"));
        a2.setTags(Arrays.asList("tip"));
        a2.setAnswersQuestion(""); // No question
        a2.setQuality(0.85);
        a2.setLinks(Collections.emptyList());
        
        // A3 in "docker" topic, answers Q2 (in different topic) → standalone in docker
        Answer a3 = new Answer();
        a3.setId("a_0003");
        a3.setAuthor("Dave");
        a3.setText("Performance answer with Docker context");
        a3.setDate("2024-10-10T10:15:00Z");
        a3.setArea("docker");
        a3.setTopics(Arrays.asList("docker", "performance"));
        a3.setTags(Arrays.asList("performance"));
        a3.setAnswersQuestion("q_0002"); // Question is NOT in docker topic
        a3.setQuality(0.8);
        a3.setLinks(Collections.emptyList());
        
        // Q2 in "performance" topic (not docker)
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setAuthor("Eve");
        q2.setText("Performance question");
        q2.setDate("2024-10-10T10:12:00Z");
        q2.setArea("performance");
        q2.setTopics(Arrays.asList("performance"));
        q2.setTags(Arrays.asList("performance"));
        q2.setAnsweredBy("a_0003");
        q2.setLinks(Collections.emptyList());
        
        analysis.setQuestions(Arrays.asList(q1, q2));
        analysis.setAnswers(Arrays.asList(a1, a2, a3));
        analysis.setNotes(Collections.emptyList());

        // Act: Build topics
        builder.buildTopicFiles(analysis, tempDir, "test");

        // Assert: Check "docker" topic file
        Path dockerTopicFile = tempDir.resolve("topics/docker.md");
        assertTrue(Files.exists(dockerTopicFile), "Docker topic file should exist");
        
        String dockerContent = Files.readString(dockerTopicFile);
        
        // Should have "Questions with Answers" with q_0001 (a_0001 is embedded)
        assertTrue(dockerContent.contains("## Questions with Answers"), 
                "Should have Questions with Answers section");
        assertTrue(dockerContent.contains("![[q_0001]]"), "Should have q_0001");
        
        // Should have "Additional Answers" with a_0002 and a_0003
        assertTrue(dockerContent.contains("## Additional Answers"), 
                "Should have Additional Answers section");
        assertTrue(dockerContent.contains("![[a_0002]]"), 
                "a_0002 (no question) should be standalone");
        assertTrue(dockerContent.contains("![[a_0003]]"), 
                "a_0003 (question in different topic) should be standalone in docker");
        
        // a_0001 should NOT be in Additional Answers
        int additionalAnswersIndex = dockerContent.indexOf("## Additional Answers");
        int a0001Index = dockerContent.indexOf("![[a_0001]]");
        assertTrue(a0001Index < additionalAnswersIndex || a0001Index == -1, 
                "a_0001 should NOT be in Additional Answers section");
    }
}
