package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.PersonContributions;
import com.github.istin.dmtools.common.kb.model.Question;
import com.github.istin.dmtools.common.kb.model.Answer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KBStructureManager contributions merging across multiple processing runs.
 * 
 * REGRESSION TEST for issue where person contributions (questions, answers, notes)
 * from previous sessions were lost when processing new sessions.
 */
class KBStructureManagerContributionsMergeTest {

    private static final Logger logger = LogManager.getLogger(KBStructureManagerContributionsMergeTest.class);

    @TempDir
    Path tempDir;

    private KBStructureManager structureManager;
    private KBStructureBuilder structureBuilder;

    @BeforeEach
    void setUp() {
        KBFileParser fileParser = new KBFileParser();
        structureBuilder = new KBStructureBuilder();
        KBContextLoader contextLoader = new KBContextLoader(fileParser);
        PersonStatsCollector statsCollector = new PersonStatsCollector(fileParser, structureBuilder);
        KBStatistics statistics = new KBStatistics();
        structureManager = new KBStructureManager(structureBuilder, statsCollector, statistics, contextLoader);
    }

    /**
     * CRITICAL REGRESSION TEST: Verify that questions from session_1 are NOT lost when processing session_2
     * 
     * This test reproduces the bug reported by user:
     * - Session_1: Darina has question q_3
     * - Session_2: Darina has answer a_9
     * - Expected: Profile shows BOTH q_3 and a_9
     * - Actual (before fix): Profile shows ONLY a_9, q_3 is LOST
     */
    @Test
    void testPersonContributions_NotLostAcrossSessions() throws Exception {
        // GIVEN: Session_1 - Darina asks question q_0001
        AnalysisResult session1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Darina Danilovich");
        q1.setArea("ai");
        q1.setTopics(Arrays.asList("testing"));
        q1.setDate("2025-10-24T10:00:00Z");
        session1.setQuestions(Arrays.asList(q1));
        session1.setAnswers(Arrays.asList());  // Initialize empty lists to avoid NPE
        session1.setNotes(Arrays.asList());
        
        // WHEN: Process session_1 (contributions will be collected inside buildStructure after ID mapping)
        structureManager.buildStructure(session1, tempDir, "session_1", null, logger);
        
        // THEN: Darina's profile should have question q_0001
        Path darinaProfile = tempDir.resolve("people/Darina_Danilovich/Darina_Danilovich.md");
        assertTrue(Files.exists(darinaProfile), "Darina's profile should exist after session_1");
        String content1 = Files.readString(darinaProfile);
        assertTrue(content1.contains("## Questions Asked"), "Should have Questions Asked section");
        assertTrue(content1.contains("q_0001"), "Should have question q_0001 from session_1");
        
        // GIVEN: Session_2 - Darina provides answer a_0001 (no questions)
        AnalysisResult session2 = new AnalysisResult();
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Darina Danilovich");
        a1.setArea("ai");
        a1.setTopics(Arrays.asList("implementation"));
        a1.setDate("2025-10-24T11:00:00Z");
        session2.setQuestions(Arrays.asList());  // Initialize empty lists to avoid NPE
        session2.setAnswers(Arrays.asList(a1));
        session2.setNotes(Arrays.asList());
        
        // WHEN: Process session_2 (contributions will be collected inside buildStructure after ID mapping)
        structureManager.buildStructure(session2, tempDir, "session_2", null, logger);
        
        // THEN: Darina's profile should have BOTH question q_0001 (from session_1) AND answer a_0001 (from session_2)
        String content2 = Files.readString(darinaProfile);
        
        // DEBUG: Print profile content to see what went wrong
        System.out.println("=== Darina's Profile After Session_2 ===");
        System.out.println(content2);
        System.out.println("=== End Profile ===");
        
        // CRITICAL: Question from session_1 should NOT be lost
        assertTrue(content2.contains("## Questions Asked"), "Should STILL have Questions Asked section");
        assertTrue(content2.contains("q_0001"), "Should STILL have question q_0001 from session_1 (NOT LOST!)");
        
        // New answer from session_2 should be present
        assertTrue(content2.contains("## Answers Provided"), "Should have Answers Provided section");
        assertTrue(content2.contains("a_0001"), "Should have answer a_0001 from session_2");
        
        // Both sources should be tracked
        assertTrue(content2.contains("session_1"), "Should track session_1 source");
        assertTrue(content2.contains("session_2"), "Should track session_2 source");
    }

    /**
     * Test that contributions are merged without duplicates
     */
    @Test
    void testPersonContributions_MergedWithoutDuplicates() throws Exception {
        // GIVEN: Session_1 with question q_0001
        AnalysisResult session1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setArea("ai");
        q1.setTopics(Arrays.asList("topic1"));
        q1.setDate("2025-10-24T10:00:00Z");
        session1.setQuestions(Arrays.asList(q1));
        session1.setAnswers(Arrays.asList());  // Initialize empty lists to avoid NPE
        session1.setNotes(Arrays.asList());
        
        structureManager.buildStructure(session1, tempDir, "session_1", null, logger);
        
        // GIVEN: Session_2 with NEW question q_0002 (not duplicate)
        AnalysisResult session2 = new AnalysisResult();
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setAuthor("Alice");
        q2.setArea("ai");
        q2.setTopics(Arrays.asList("topic2"));
        q2.setDate("2025-10-24T11:00:00Z");
        session2.setQuestions(Arrays.asList(q2));
        session2.setAnswers(Arrays.asList());  // Initialize empty lists to avoid NPE
        session2.setNotes(Arrays.asList());
        
        // WHEN: Process session_2
        structureManager.buildStructure(session2, tempDir, "session_2", null, logger);
        
        // THEN: Alice's profile should have BOTH questions (no duplicates)
        Path aliceProfile = tempDir.resolve("people/Alice/Alice.md");
        String content = Files.readString(aliceProfile);
        
        assertTrue(content.contains("q_0001"), "Should have q_0001");
        assertTrue(content.contains("q_0002"), "Should have q_0002");
        
        // Count link occurrences (not substring) - each question link should appear exactly once
        // Links have format: [[../../questions/q_0001|q_0001]]
        int q0001LinkCount = countOccurrences(content, "[[../../questions/q_0001|q_0001]]");
        int q0002LinkCount = countOccurrences(content, "[[../../questions/q_0002|q_0002]]");
        
        assertEquals(1, q0001LinkCount, "q_0001 link should appear exactly once (no duplicates)");
        assertEquals(1, q0002LinkCount, "q_0002 link should appear exactly once (no duplicates)");
    }

    /**
     * Test that multiple people's contributions are all preserved
     */
    @Test
    void testMultiplePeople_AllContributionsPreserved() throws Exception {
        // GIVEN: Session_1 with Alice and Bob
        AnalysisResult session1 = new AnalysisResult();
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setArea("ai");
        q1.setTopics(Arrays.asList("topic1"));
        q1.setDate("2025-10-24T10:00:00Z");
        
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setAuthor("Bob");
        q2.setArea("ai");
        q2.setTopics(Arrays.asList("topic2"));
        q2.setDate("2025-10-24T10:30:00Z");
        
        session1.setQuestions(Arrays.asList(q1, q2));
        session1.setAnswers(Arrays.asList());  // Initialize empty lists to avoid NPE
        session1.setNotes(Arrays.asList());
        
        structureManager.buildStructure(session1, tempDir, "session_1", null, logger);
        
        // GIVEN: Session_2 with Charlie (new person)
        AnalysisResult session2 = new AnalysisResult();
        Question q3 = new Question();
        q3.setId("q_0003");
        q3.setAuthor("Charlie");
        q3.setArea("ai");
        q3.setTopics(Arrays.asList("topic3"));
        q3.setDate("2025-10-24T11:00:00Z");
        session2.setQuestions(Arrays.asList(q3));
        session2.setAnswers(Arrays.asList());  // Initialize empty lists to avoid NPE
        session2.setNotes(Arrays.asList());
        
        // WHEN: Process session_2
        structureManager.buildStructure(session2, tempDir, "session_2", null, logger);
        
        // THEN: All three people should have their contributions
        Path aliceProfile = tempDir.resolve("people/Alice/Alice.md");
        Path bobProfile = tempDir.resolve("people/Bob/Bob.md");
        Path charlieProfile = tempDir.resolve("people/Charlie/Charlie.md");
        
        assertTrue(Files.exists(aliceProfile), "Alice's profile should exist");
        assertTrue(Files.exists(bobProfile), "Bob's profile should exist");
        assertTrue(Files.exists(charlieProfile), "Charlie's profile should exist");
        
        String aliceContent = Files.readString(aliceProfile);
        String bobContent = Files.readString(bobProfile);
        String charlieContent = Files.readString(charlieProfile);
        
        assertTrue(aliceContent.contains("q_0001"), "Alice should still have q_0001");
        assertTrue(bobContent.contains("q_0002"), "Bob should still have q_0002");
        assertTrue(charlieContent.contains("q_0003"), "Charlie should have q_0003");
    }

    /**
     * Helper to count occurrences of a substring
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}

