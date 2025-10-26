package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Note;
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
 * Tests for KBStructureBuilder area structure fixes:
 * 1. Areas should preserve contributors from previous sources
 * 2. Areas should preserve topics from previous sources  
 * 3. Area sources should only be added if there are contributions from that source
 */
class KBStructureBuilderAreasFixTest {

    @TempDir
    Path tempDir;

    private KBStructureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new KBStructureBuilder();
    }

    /**
     * Test that area contributors accumulate across multiple sources
     */
    @Test
    void testAreaContributorsAccumulateAcrossSourcesNotOverwrite() throws IOException {
        // GIVEN: First analysis with area "ai" from session_1
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("ai");
        q1.setAuthor("Maksim Karaban");
        q1.setTopics(Arrays.asList("llm-pricing"));
        analysis1.setQuestions(Arrays.asList(q1));
        
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setArea("ai");
        a1.setAuthor("Tom Bradley");
        a1.setTopics(Arrays.asList("llm-capabilities"));
        analysis1.setAnswers(Arrays.asList(a1));
        
        Note n1 = new Note();
        n1.setId("n_0001");
        n1.setArea("ai");
        n1.setAuthor("Uladzimir Klyshevich");
        n1.setTopics(Arrays.asList("ai-native-development"));
        analysis1.setNotes(Arrays.asList(n1));
        
        // WHEN: Build areas with source1
        builder.buildAreaStructure(analysis1, tempDir, "session_1");
        
        // THEN: Area should contain all three contributors
        Path areaFile = tempDir.resolve("areas/ai/ai.md");
        assertTrue(Files.exists(areaFile), "Area file should exist");
        String content1 = Files.readString(areaFile);
        assertTrue(content1.contains("Maksim Karaban"), "Should contain Maksim Karaban");
        assertTrue(content1.contains("Tom Bradley"), "Should contain Tom Bradley");
        assertTrue(content1.contains("Uladzimir Klyshevich"), "Should contain Uladzimir Klyshevich");
        assertTrue(content1.contains("session_1"), "Should contain source session_1");
        
        // AND: Should have all three topics
        assertTrue(content1.contains("llm-pricing"), "Should contain llm-pricing");
        assertTrue(content1.contains("llm-capabilities"), "Should contain llm-capabilities");
        assertTrue(content1.contains("ai-native-development"), "Should contain ai-native-development");
        
        // GIVEN: Second analysis with area "ai" from session_2 (DIFFERENT contributors and topics)
        AnalysisResult analysis2 = new AnalysisResult();
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("ai");
        q2.setAuthor("Aliaksandr Raukuts");
        q2.setTopics(Arrays.asList("jira-ai-integration"));
        analysis2.setQuestions(Arrays.asList(q2));
        
        Answer a2 = new Answer();
        a2.setId("a_0002");
        a2.setArea("ai");
        a2.setAuthor("Aliaksandr Tarasevich");
        a2.setTopics(Arrays.asList("ai-agent-design"));
        analysis2.setAnswers(Arrays.asList(a2));
        
        // Note: Uladzimir Klyshevich appears in both sources
        Note n2 = new Note();
        n2.setId("n_0002");
        n2.setArea("ai");
        n2.setAuthor("Uladzimir Klyshevich");
        n2.setTopics(Arrays.asList("model-selection"));
        analysis2.setNotes(Arrays.asList(n2));
        
        // WHEN: Build areas with source2
        builder.buildAreaStructure(analysis2, tempDir, "session_2");
        
        // THEN: Area should contain ALL contributors from both sources (NOT overwritten)
        String content2 = Files.readString(areaFile);
        assertTrue(content2.contains("Maksim Karaban"), "Should STILL contain Maksim Karaban (from session_1)");
        assertTrue(content2.contains("Tom Bradley"), "Should STILL contain Tom Bradley (from session_1)");
        assertTrue(content2.contains("Uladzimir Klyshevich"), "Should contain Uladzimir Klyshevich (appears in both)");
        assertTrue(content2.contains("Aliaksandr Raukuts"), "Should contain NEW contributor Aliaksandr Raukuts");
        assertTrue(content2.contains("Aliaksandr Tarasevich"), "Should contain NEW contributor Aliaksandr Tarasevich");
        
        // AND: Should have both sources
        assertTrue(content2.contains("session_1"), "Should STILL contain source session_1");
        assertTrue(content2.contains("session_2"), "Should contain NEW source session_2");
        
        // AND: Should have ALL topics from both sources
        assertTrue(content2.contains("llm-pricing"), "Should contain llm-pricing from session_1");
        assertTrue(content2.contains("llm-capabilities"), "Should contain llm-capabilities from session_1");
        assertTrue(content2.contains("ai-native-development"), "Should contain ai-native-development from session_1");
        assertTrue(content2.contains("jira-ai-integration"), "Should contain NEW topic jira-ai-integration");
        assertTrue(content2.contains("ai-agent-design"), "Should contain NEW topic ai-agent-design");
        assertTrue(content2.contains("model-selection"), "Should contain NEW topic model-selection");
    }

    /**
     * Test that area sources are only added when there are actual contributions
     */
    @Test
    void testAreaSourceOnlyAddedWhenHasContributions() throws IOException {
        // GIVEN: Analysis with area "ai"
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("ai");
        q1.setAuthor("Alice");
        q1.setTopics(Arrays.asList("topic1"));
        analysis1.setQuestions(Arrays.asList(q1));
        
        // WHEN: Build areas with source1
        builder.buildAreaStructure(analysis1, tempDir, "source1");
        
        // THEN: Area should contain source1
        Path areaFile = tempDir.resolve("areas/ai/ai.md");
        String content1 = Files.readString(areaFile);
        assertTrue(content1.contains("source1"), "Should contain source1");
        
        // GIVEN: Second analysis with DIFFERENT area (not "ai")
        AnalysisResult analysis2 = new AnalysisResult();
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("platform"); // Different area!
        q2.setAuthor("Bob");
        q2.setTopics(Arrays.asList("topic2"));
        analysis2.setQuestions(Arrays.asList(q2));
        
        // WHEN: Build areas with source2
        builder.buildAreaStructure(analysis2, tempDir, "source2");
        
        // THEN: "ai" area should NOT contain source2 (no contributions from source2)
        String content2 = Files.readString(areaFile);
        assertTrue(content2.contains("source1"), "Should still contain source1");
        assertFalse(content2.contains("source2"), "Should NOT contain source2 (no contributions to ai area)");
        
        // AND: "platform" area should exist with source2
        Path platformFile = tempDir.resolve("areas/platform/platform.md");
        assertTrue(Files.exists(platformFile), "Platform area should exist");
        String platformContent = Files.readString(platformFile);
        assertTrue(platformContent.contains("source2"), "Platform should contain source2");
        assertFalse(platformContent.contains("source1"), "Platform should NOT contain source1");
    }

    /**
     * Test that created timestamp is preserved across updates
     */
    @Test
    void testAreaCreatedTimestampIsPreserved() throws IOException {
        // GIVEN: First analysis with area "ai"
        AnalysisResult analysis1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setArea("ai");
        q1.setAuthor("Alice");
        q1.setTopics(Arrays.asList("topic1"));
        analysis1.setQuestions(Arrays.asList(q1));
        
        // WHEN: Build areas
        builder.buildAreaStructure(analysis1, tempDir, "source1");
        
        // THEN: Get created timestamp
        Path areaFile = tempDir.resolve("areas/ai/ai.md");
        String content1 = Files.readString(areaFile);
        String createdLine1 = extractLine(content1, "created:");
        assertNotNull(createdLine1, "Should have created timestamp");
        assertFalse(createdLine1.contains("\"\""), "Should not have double quotes");
        
        // Wait a bit to ensure different timestamp would be generated
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // ignore
        }
        
        // GIVEN: Second analysis with same area
        AnalysisResult analysis2 = new AnalysisResult();
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setArea("ai");
        q2.setAuthor("Bob");
        q2.setTopics(Arrays.asList("topic2"));
        analysis2.setQuestions(Arrays.asList(q2));
        
        // WHEN: Build areas again
        builder.buildAreaStructure(analysis2, tempDir, "source2");
        
        // THEN: Created timestamp should be SAME
        String content2 = Files.readString(areaFile);
        String createdLine2 = extractLine(content2, "created:");
        assertEquals(createdLine1, createdLine2, "Created timestamp should not change");
        assertFalse(createdLine2.contains("\"\""), "Should not have double quotes");
    }

    private String extractLine(String content, String prefix) {
        for (String line : content.split("\n")) {
            if (line.trim().startsWith(prefix)) {
                return line.trim();
            }
        }
        return null;
    }
}

