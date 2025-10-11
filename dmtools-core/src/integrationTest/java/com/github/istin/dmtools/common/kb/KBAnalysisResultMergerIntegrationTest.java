package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.common.kb.model.*;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.prompt.PromptManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KBAnalysisResultMerger with real AI
 * 
 * Tests the merger's ability to combine multiple AnalysisResult objects
 * using ContentMergeAgent to intelligently deduplicate and merge content.
 * 
 * To run this test, you need:
 * 1. Valid AI configuration in dmtools.env
 * 2. Environment variable DMTOOLS_INTEGRATION_TESTS=true
 */
public class KBAnalysisResultMergerIntegrationTest {
    
    private static final Logger logger = LogManager.getLogger(KBAnalysisResultMergerIntegrationTest.class);
    
    private KBAnalysisResultMerger merger;
    private AI ai;
    
    @BeforeEach
    void setUp() throws Exception {
        // Initialize real AI client
        PropertyReader propertyReader = new PropertyReader();
        ConversationObserver observer = new ConversationObserver();
        ai = BasicGeminiAI.create(observer, propertyReader);
        
        // Create ContentMergeAgent with proper DI
        ContentMergeAgent contentMergeAgent = new ContentMergeAgent(ai, new PromptManager());
        
        // Create merger
        merger = new KBAnalysisResultMerger(contentMergeAgent);
    }
    
    @Test
    void testMergeTwoResults() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBAnalysisResultMerger - Two Results");
        logger.info("=".repeat(80));
        
        // Create first result
        AnalysisResult result1 = createTestResult(1, "chunk1", 
                Arrays.asList("Cursor AI", "Agent Configuration"),
                Arrays.asList("John Doe", "Sarah Smith"));
        
        // Create second result
        AnalysisResult result2 = createTestResult(2, "chunk2",
                Arrays.asList("Prompt Engineering", "Agent Configuration"),
                Arrays.asList("Sarah Smith", "Mike Johnson"));
        
        logger.info("Result 1: {} questions, {} answers, {} notes",
                result1.getQuestions().size(),
                result1.getAnswers().size(),
                result1.getNotes().size());
        
        logger.info("Result 2: {} questions, {} answers, {} notes",
                result2.getQuestions().size(),
                result2.getAnswers().size(),
                result2.getNotes().size());
        
        // Merge results
        logger.info("Merging results using AI...");
        List<AnalysisResult> results = Arrays.asList(result1, result2);
        AnalysisResult merged = merger.mergeResults(results);
        
        // Print merged result
        logger.info("=".repeat(80));
        logger.info("MERGED RESULT");
        logger.info("=".repeat(80));
        logger.info("Questions: {}", merged.getQuestions().size());
        logger.info("Answers: {}", merged.getAnswers().size());
        logger.info("Notes: {}", merged.getNotes().size());
        logger.info("=".repeat(80));
        
        // Assertions
        assertNotNull(merged);
        assertNotNull(merged.getQuestions());
        assertNotNull(merged.getAnswers());
        assertNotNull(merged.getNotes());
        
        // Should have combined content (AI may deduplicate similar items)
        assertTrue(merged.getQuestions().size() > 0, "Should have at least one question");
        
        logger.info("✓ Two results merge test passed successfully");
        logger.info("=".repeat(80));
    }
    
    @Test
    void testMergeThreeResults() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBAnalysisResultMerger - Three Results");
        logger.info("=".repeat(80));
        
        // Create three results with some overlapping content
        AnalysisResult result1 = createTestResult(1, "chunk1",
                Arrays.asList("AI Development"),
                Arrays.asList("John Doe"));
        
        AnalysisResult result2 = createTestResult(2, "chunk2",
                Arrays.asList("Testing Strategies"),
                Arrays.asList("Sarah Smith"));
        
        AnalysisResult result3 = createTestResult(3, "chunk3",
                Arrays.asList("Deployment Practices"),
                Arrays.asList("Mike Johnson"));
        
        logger.info("Created 3 test results with different topics");
        
        // Merge results
        logger.info("Merging 3 results using AI...");
        List<AnalysisResult> results = Arrays.asList(result1, result2, result3);
        AnalysisResult merged = merger.mergeResults(results);
        
        // Print merged result
        logger.info("=".repeat(80));
        logger.info("MERGED RESULT (3 chunks)");
        logger.info("=".repeat(80));
        logger.info("Questions: {}", merged.getQuestions().size());
        logger.info("Answers: {}", merged.getAnswers().size());
        logger.info("Notes: {}", merged.getNotes().size());
        logger.info("=".repeat(80));
        
        // Assertions
        assertNotNull(merged);
        assertTrue(merged.getQuestions().size() >= 1, "Should have merged questions");
        
        logger.info("✓ Three results merge test passed successfully");
        logger.info("=".repeat(80));
    }
    
    @Test
    void testMergeSingleResult() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBAnalysisResultMerger - Single Result");
        logger.info("=".repeat(80));
        
        // Create single result
        AnalysisResult result = createTestResult(1, "single",
                Arrays.asList("Single Topic"),
                Arrays.asList("John Doe"));
        
        logger.info("Created single test result");
        
        // Merge single result (should return as-is without AI call)
        AnalysisResult merged = merger.mergeResults(Arrays.asList(result));
        
        // Should be same object
        assertSame(result, merged, "Single result should be returned as-is");
        
        logger.info("✓ Single result merge test passed (no AI call needed)");
        logger.info("=".repeat(80));
    }
    
    /**
     * Helper method to create test AnalysisResult
     */
    private AnalysisResult createTestResult(int chunkId, String chunkName, 
                                            List<String> topicNames, List<String> authors) {
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        // Create question with temporary ID
        if (!authors.isEmpty()) {
            Question question = new Question();
            question.setId("q_" + chunkId);
            question.setAuthor(authors.get(0));
            question.setText("Question from " + chunkName + ": How to implement " + topicNames.get(0) + "?");
            question.setDate("2024-10-10T12:00:00Z");
            question.setArea(topicNames.get(0)); // First topic as area
            question.setTopics(topicNames);
            question.setTags(Arrays.asList("test", chunkName));
            question.setAnsweredBy("a_" + chunkId); // Link to answer
            question.setLinks(new ArrayList<>());
            result.getQuestions().add(question);
        }
        
        // Create answer with temporary ID
        if (authors.size() > 1 || !authors.isEmpty()) {
            Answer answer = new Answer();
            answer.setId("a_" + chunkId);
            answer.setAuthor(authors.size() > 1 ? authors.get(1) : authors.get(0));
            answer.setText("Answer from " + chunkName + ": Here's how to implement " + topicNames.get(0) + "...");
            answer.setDate("2024-10-10T12:30:00Z");
            answer.setArea(topicNames.get(0)); // First topic as area
            answer.setTopics(topicNames);
            answer.setTags(Arrays.asList("solution", chunkName));
            answer.setAnswersQuestion("q_" + chunkId); // Link to question
            answer.setQuality(0.8);
            answer.setLinks(new ArrayList<>());
            result.getAnswers().add(answer);
        }
        
        // Create note with temporary ID
        Note note = new Note();
        note.setId("n_" + chunkId);
        note.setText("Note from " + chunkName + ": Important insight about " + topicNames.get(0));
        note.setArea(topicNames.get(0)); // First topic as area
        note.setTopics(topicNames);
        note.setTags(Arrays.asList("insight", chunkName));
        note.setAuthor(!authors.isEmpty() ? authors.get(0) : "Unknown");
        note.setDate("2024-10-10T13:00:00Z");
        note.setLinks(new ArrayList<>());
        result.getNotes().add(note);
        
        return result;
    }
}

