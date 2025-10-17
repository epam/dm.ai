package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.agent.JSONFixAgent;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.params.AnalysisParams;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.prompt.PromptManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KBAnalysisAgent with real AI
 * 
 * Note: This agent processes single inputs without chunking.
 * Chunking is handled by KBOrchestrator, which processes each chunk separately
 * and merges results using ContentMergeAgent.
 * 
 * To run this test, you need:
 * 1. Valid AI configuration in dmtools.env
 * 2. Environment variable DMTOOLS_INTEGRATION_TESTS=true
 */
public class KBAnalysisAgentIntegrationTest {
    
    private static final Logger logger = LogManager.getLogger(KBAnalysisAgentIntegrationTest.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private KBAnalysisAgent agent;
    private AI ai;
    
    @BeforeEach
    void setUp() throws Exception {
        // Initialize real AI client
        PropertyReader propertyReader = new PropertyReader();
        ConversationObserver observer = new ConversationObserver();
        ai = BasicGeminiAI.create(observer, propertyReader);
        
        // Initialize agent with real dependencies via constructor (proper DI)
        PromptManager promptManager = new PromptManager();
        JSONFixAgent jsonFixAgent = new JSONFixAgent(ai, promptManager);
        agent = new KBAnalysisAgent(ai, promptManager, jsonFixAgent);
    }
    
    @Test
    void testAnalyzeSimpleConversation() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBAnalysisAgent");
        logger.info("=".repeat(80));
        
        // Read input from resources
        String inputContent = new String(
            getClass().getResourceAsStream("/kb/test_cursor_ai_agents.json").readAllBytes()
        );
        logger.info("Input content:");
        logger.info("-".repeat(80));
        logger.info(inputContent);
        logger.info("-".repeat(80));
        
        // Prepare params (no chunking - agent processes single input)
        AnalysisParams params = new AnalysisParams();
        params.setInputText(inputContent);
        params.setSourceName("integration_test");
        params.setContext(new KBContext());
        
        // Run analysis
        logger.info("Running AI analysis...");
        
        AnalysisResult result = agent.run(params);
        
        // Print results
        logger.info("=".repeat(80));
        logger.info("ANALYSIS RESULT");
        logger.info("=".repeat(80));
        
        logger.info("QUESTIONS ({})", result.getQuestions().size());
        logger.info("-".repeat(80));
        result.getQuestions().forEach(q -> {
            logger.info("  • [{}] by {}", q.getId(), q.getAuthor());
            logger.info("    {}", q.getText());
            logger.info("    Topics: {}", q.getTopics());
            logger.info("    Answered by: {}", q.getAnsweredBy());
        });
        
        logger.info("ANSWERS ({})", result.getAnswers().size());
        logger.info("-".repeat(80));
        result.getAnswers().forEach(a -> {
            logger.info("  • [{}] by {} (quality: {})", a.getId(), a.getAuthor(), a.getQuality());
            logger.info("    {}", a.getText());
            logger.info("    Topics: {}", a.getTopics());
            logger.info("    Answers question: {}", a.getAnswersQuestion());
        });
        
        logger.info("NOTES ({})", result.getNotes().size());
        logger.info("-".repeat(80));
        result.getNotes().forEach(n -> {
            logger.info("  • [{}] by {}", n.getId(), n.getAuthor());
            logger.info("    {}", n.getText());
            logger.info("    Topics: {}", n.getTopics());
        });
        
        logger.info("=".repeat(80));
        logger.info("FULL JSON RESULT:");
        logger.info("=".repeat(80));
        logger.info(GSON.toJson(result));
        logger.info("=".repeat(80));
        
        // Assertions
        assertNotNull(result);
        assertNotNull(result.getQuestions());
        assertNotNull(result.getAnswers());
        assertNotNull(result.getNotes());
        
        assertTrue(result.getQuestions().size() > 0 || 
                   result.getAnswers().size() > 0 || 
                   result.getNotes().size() > 0,
                "Expected at least some extracted content");
        
        logger.info("✓ Test passed successfully");
        logger.info("=".repeat(80));
    }
    
    @Test
    void testQuestionWithAnswer() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Question with Answer (Q→A mapping)");
        logger.info("=".repeat(80));
        
        String testData = new String(
            getClass().getResourceAsStream("/kb/test_question_with_answer.json").readAllBytes()
        );
        
        AnalysisParams params = new AnalysisParams();
        params.setInputText(testData);
        params.setSourceName("test_q_with_a");
        params.setContext(new KBContext());
        
        AnalysisResult result = agent.run(params);
        
        logger.info("Result: {} questions, {} answers", result.getQuestions().size(), result.getAnswers().size());
        logger.info(GSON.toJson(result));
        
        assertEquals(1, result.getQuestions().size(), "Expected 1 question");
        assertEquals(1, result.getAnswers().size(), "Expected 1 answer");
        
        // Check temporary IDs format
        assertTrue(result.getQuestions().get(0).getId().matches("q_\\d+"), "Question ID should be q_N format");
        assertTrue(result.getAnswers().get(0).getId().matches("a_\\d+"), "Answer ID should be a_N format");
        
        // Check Q→A mapping
        String questionId = result.getQuestions().get(0).getId();
        String answerId = result.getAnswers().get(0).getId();
        assertEquals(answerId, result.getQuestions().get(0).getAnsweredBy(), "Question should reference answer");
        assertEquals(questionId, result.getAnswers().get(0).getAnswersQuestion(), "Answer should reference question");
        
        logger.info("✓ Test passed");
    }
    
    @Test
    void testQuestionWithoutAnswer() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Question without Answer");
        logger.info("=".repeat(80));
        
        String testData = new String(
            getClass().getResourceAsStream("/kb/test_question_without_answer.json").readAllBytes()
        );
        
        AnalysisParams params = new AnalysisParams();
        params.setInputText(testData);
        params.setSourceName("test_q_no_a");
        params.setContext(new KBContext());
        
        AnalysisResult result = agent.run(params);
        
        logger.info("Result: {} questions, {} answers", result.getQuestions().size(), result.getAnswers().size());
        logger.info(GSON.toJson(result));
        
        assertTrue(result.getQuestions().size() >= 1, "Expected at least 1 question");
        assertTrue(result.getAnswers().size() == 0, "Expected no answers");
        
        // Check temporary ID format
        assertTrue(result.getQuestions().get(0).getId().matches("q_\\d+"), "Question ID should be q_N format");
        
        // Check no answer reference
        String answeredBy = result.getQuestions().get(0).getAnsweredBy();
        assertTrue(answeredBy == null || answeredBy.isEmpty(), "Question should not have answeredBy");
        
        logger.info("✓ Test passed");
    }
    
    @Test
    void testStandaloneNote() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Standalone Note (no Q/A)");
        logger.info("=".repeat(80));
        
        String testData = new String(
            getClass().getResourceAsStream("/kb/test_standalone_note.json").readAllBytes()
        );
        
        AnalysisParams params = new AnalysisParams();
        params.setInputText(testData);
        params.setSourceName("test_note");
        params.setContext(new KBContext());
        
        AnalysisResult result = agent.run(params);
        
        logger.info("Result: {} questions, {} answers, {} notes", 
                result.getQuestions().size(), result.getAnswers().size(), result.getNotes().size());
        logger.info(GSON.toJson(result));
        
        assertTrue(result.getNotes().size() >= 1, "Expected at least 1 note");
        
        // Check temporary ID format
        assertTrue(result.getNotes().get(0).getId().matches("n_\\d+"), "Note ID should be n_N format");
        
        // Check area/topics/tags
        assertNotNull(result.getNotes().get(0).getArea(), "Note should have area");
        assertFalse(result.getNotes().get(0).getArea().isEmpty(), "Note area should not be empty");
        assertNotNull(result.getNotes().get(0).getTopics(), "Note should have topics");
        assertFalse(result.getNotes().get(0).getTopics().isEmpty(), "Note topics should not be empty");
        
        logger.info("✓ Test passed");
    }
}
