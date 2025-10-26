package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KBStructureBuilder area topics and contributors sorting.
 * 
 * PURPOSE: Ensure topics and contributors are sorted alphabetically to reduce git diffs.
 * When items are sorted consistently, the same content always produces the same file,
 * minimizing unnecessary changes in version control.
 */
class KBStructureBuilderAreaSortingTest {

    @TempDir
    Path tempDir;

    private KBStructureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new KBStructureBuilder();
    }

    /**
     * Test that topics in area are sorted alphabetically (not in random Set order)
     */
    @Test
    void testAreaTopics_SortedAlphabetically() throws IOException {
        // GIVEN: Analysis with topics in non-alphabetical order
        AnalysisResult analysis = new AnalysisResult();
        
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("ai");
        q1.setAuthor("Alice");
        q1.setTopics(Arrays.asList("zebra-topic"));  // Z - should be last
        
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("ai");
        q2.setAuthor("Bob");
        q2.setTopics(Arrays.asList("apple-topic"));  // A - should be first
        
        Question q3 = new Question();
        q3.setId("q_0003");
        q3.setArea("ai");
        q3.setAuthor("Charlie");
        q3.setTopics(Arrays.asList("middle-topic"));  // M - should be middle
        
        analysis.setQuestions(Arrays.asList(q1, q2, q3));
        
        // WHEN: Build area structure
        builder.buildAreaStructure(analysis, tempDir, "source1");
        
        // THEN: Topics should be sorted alphabetically (apple, middle, zebra)
        Path areaFile = tempDir.resolve("areas/ai/ai.md");
        assertTrue(Files.exists(areaFile), "Area file should exist");
        
        String content = Files.readString(areaFile);
        
        // Find positions of each topic
        int posApple = content.indexOf("apple-topic");
        int posMiddle = content.indexOf("middle-topic");
        int posZebra = content.indexOf("zebra-topic");
        
        assertTrue(posApple > 0, "Should contain apple-topic");
        assertTrue(posMiddle > 0, "Should contain middle-topic");
        assertTrue(posZebra > 0, "Should contain zebra-topic");
        
        // Verify alphabetical order
        assertTrue(posApple < posMiddle, "apple-topic should come before middle-topic");
        assertTrue(posMiddle < posZebra, "middle-topic should come before zebra-topic");
    }

    /**
     * Test that contributors in area are sorted alphabetically
     */
    @Test
    void testAreaContributors_SortedAlphabetically() throws IOException {
        // GIVEN: Analysis with contributors in non-alphabetical order
        AnalysisResult analysis = new AnalysisResult();
        
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("ai");
        q1.setAuthor("Zoe Wilson");  // Z - should be last
        q1.setTopics(Arrays.asList("topic1"));
        
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("ai");
        q2.setAuthor("Alice Johnson");  // A - should be first
        q2.setTopics(Arrays.asList("topic2"));
        
        Question q3 = new Question();
        q3.setId("q_0003");
        q3.setArea("ai");
        q3.setAuthor("Mike Brown");  // M - should be middle
        q3.setTopics(Arrays.asList("topic3"));
        
        analysis.setQuestions(Arrays.asList(q1, q2, q3));
        
        // WHEN: Build area structure
        builder.buildAreaStructure(analysis, tempDir, "source1");
        
        // THEN: Contributors should be sorted alphabetically
        Path areaFile = tempDir.resolve("areas/ai/ai.md");
        String content = Files.readString(areaFile);
        
        System.out.println("=== Area File Content ===");
        System.out.println(content);
        System.out.println("=== End Content ===");
        
        // Find positions of each contributor in "Key Contributors" section
        int posAlice = content.indexOf("Alice Johnson");
        int posMike = content.indexOf("Mike Brown");
        int posZoe = content.indexOf("Zoe Wilson");
        
        System.out.println("posAlice: " + posAlice);
        System.out.println("posMike: " + posMike);
        System.out.println("posZoe: " + posZoe);
        
        assertTrue(posAlice > 0, "Should contain Alice Johnson");
        assertTrue(posMike > 0, "Should contain Mike Brown");
        assertTrue(posZoe > 0, "Should contain Zoe Wilson");
        
        // Verify alphabetical order
        assertTrue(posAlice < posMike, "Alice Johnson should come before Mike Brown");
        assertTrue(posMike < posZoe, "Mike Brown should come before Zoe Wilson");
    }

    /**
     * Test that sorting is stable across multiple runs (same input = same output)
     */
    @Test
    void testAreaSorting_StableAcrossRuns() throws IOException {
        // GIVEN: Analysis with random order
        AnalysisResult analysis = new AnalysisResult();
        
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("ai");
        q1.setAuthor("Charlie");
        q1.setTopics(Arrays.asList("c-topic"));
        
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("ai");
        q2.setAuthor("Alice");
        q2.setTopics(Arrays.asList("a-topic"));
        
        Question q3 = new Question();
        q3.setId("q_0003");
        q3.setArea("ai");
        q3.setAuthor("Bob");
        q3.setTopics(Arrays.asList("b-topic"));
        
        analysis.setQuestions(Arrays.asList(q1, q2, q3));
        
        // WHEN: Build area twice
        builder.buildAreaStructure(analysis, tempDir, "source1");
        Path areaFile = tempDir.resolve("areas/ai/ai.md");
        String content1 = Files.readString(areaFile);
        
        // Delete and rebuild
        Files.delete(areaFile);
        builder.buildAreaStructure(analysis, tempDir, "source1");
        String content2 = Files.readString(areaFile);
        
        // THEN: Content should be IDENTICAL (stable sort)
        // Extract topics and contributors sections
        String topics1 = extractSection(content1, "## Topics", "## Key Contributors");
        String topics2 = extractSection(content2, "## Topics", "## Key Contributors");
        
        String contributors1 = extractSection(content1, "## Key Contributors", "<!-- AUTO_GENERATED_START -->");
        String contributors2 = extractSection(content2, "## Key Contributors", "<!-- AUTO_GENERATED_START -->");
        
        assertEquals(topics1, topics2, "Topics section should be identical across runs");
        assertEquals(contributors1, contributors2, "Contributors section should be identical across runs");
    }

    /**
     * Test that adding topics in different order still produces sorted output
     */
    @Test
    void testAreaTopics_DifferentInputOrder_SameSortedOutput() throws IOException {
        // GIVEN: Two analyses with same topics but different order
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1a = new Question();
        q1a.setId("q_0001");
        q1a.setArea("ai");
        q1a.setAuthor("Alice");
        q1a.setTopics(Arrays.asList("topic-a", "topic-b", "topic-c"));
        analysis1.setQuestions(Arrays.asList(q1a));
        
        AnalysisResult analysis2 = new AnalysisResult();
        Question q2a = new Question();
        q2a.setId("q_0001");
        q2a.setArea("ai");
        q2a.setAuthor("Alice");
        q2a.setTopics(Arrays.asList("topic-c", "topic-a", "topic-b"));  // Different order
        analysis2.setQuestions(Arrays.asList(q2a));
        
        // WHEN: Build both
        Path tempDir1 = tempDir.resolve("test1");
        Path tempDir2 = tempDir.resolve("test2");
        Files.createDirectories(tempDir1);
        Files.createDirectories(tempDir2);
        
        builder.buildAreaStructure(analysis1, tempDir1, "source1");
        builder.buildAreaStructure(analysis2, tempDir2, "source1");
        
        // THEN: Topics section should be identical (both sorted)
        String content1 = Files.readString(tempDir1.resolve("areas/ai/ai.md"));
        String content2 = Files.readString(tempDir2.resolve("areas/ai/ai.md"));
        
        String topics1 = extractSection(content1, "## Topics", "## Key Contributors");
        String topics2 = extractSection(content2, "## Topics", "## Key Contributors");
        
        assertEquals(topics1, topics2, "Topics should be sorted identically regardless of input order");
        
        // Verify sort order
        assertTrue(topics1.indexOf("topic-a") < topics1.indexOf("topic-b"), "topic-a before topic-b");
        assertTrue(topics1.indexOf("topic-b") < topics1.indexOf("topic-c"), "topic-b before topic-c");
    }

    /**
     * Helper to extract a section between two markers
     */
    private String extractSection(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        int end = content.indexOf(endMarker);
        if (start == -1 || end == -1 || start >= end) {
            return "";
        }
        return content.substring(start, end);
    }
}

