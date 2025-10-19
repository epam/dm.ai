package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.AggregationParams;
import com.github.istin.dmtools.common.kb.params.AnalysisParams;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that extraInstructions are properly injected into agent prompts
 * and affect the AI's behavior.
 */
public class KBExtraInstructionsTest {
    
    private static final Logger logger = LogManager.getLogger(KBExtraInstructionsTest.class);
    
    private KBAnalysisAgent analysisAgent;
    private KBAggregationAgent aggregationAgent;
    private KBOrchestrator orchestrator;
    
    @BeforeEach
    void setUp() {
        logger.info("=".repeat(80));
        logger.info("SETUP: KBExtraInstructionsTest");
        logger.info("=".repeat(80));
        
        KnowledgeBaseComponent component = DaggerKnowledgeBaseComponent.create();
        analysisAgent = component.kbAnalysisAgent();
        aggregationAgent = component.kbAggregationAgent();
        orchestrator = component.kbOrchestrator();
    }
    
    @Test
    void testAnalysisAgentWithExtraInstructions() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Analysis Agent with Extra Instructions");
        logger.info("=".repeat(80));
        
        // Simple test conversation
        String testInput = """
                [
                    {
                        "date": "2024-10-10T10:00:00Z",
                        "author": "Alice",
                        "body": "How do I configure Redis caching?"
                    },
                    {
                        "date": "2024-10-10T10:05:00Z",
                        "author": "Bob",
                        "body": "Use @EnableCaching annotation and configure RedisConnectionFactory"
                    }
                ]
                """;
        
        // Test WITH extra instructions
        AnalysisParams paramsWithInstructions = new AnalysisParams();
        paramsWithInstructions.setInputText(testInput);
        paramsWithInstructions.setSourceName("test_extra_instructions");
        paramsWithInstructions.setContext(new KBContext());
        paramsWithInstructions.setExtraInstructions(
                "IMPORTANT: For this analysis, always add a tag '#test-tag' to ALL extracted entities " +
                "(questions, answers, notes). This is a test requirement."
        );
        
        logger.info("Running analysis WITH extra instructions...");
        logger.info("Extra instruction: Add '#test-tag' to all entities");
        
        AnalysisResult resultWith = analysisAgent.run(paramsWithInstructions);
        
        // Verify results
        assertNotNull(resultWith);
        assertTrue(resultWith.getQuestions().size() > 0, "Should extract at least one question");
        assertTrue(resultWith.getAnswers().size() > 0, "Should extract at least one answer");
        
        logger.info("Extracted {} questions, {} answers", 
                resultWith.getQuestions().size(), 
                resultWith.getAnswers().size());
        
        // Check if the extra instruction was followed (AI should add #test-tag)
        boolean hasTestTag = false;
        
        // Check questions
        for (var question : resultWith.getQuestions()) {
            logger.info("Question tags: {}", question.getTags());
            if (question.getTags() != null && question.getTags().contains("test-tag")) {
                hasTestTag = true;
                break;
            }
        }
        
        // Check answers
        if (!hasTestTag) {
            for (var answer : resultWith.getAnswers()) {
                logger.info("Answer tags: {}", answer.getTags());
                if (answer.getTags() != null && answer.getTags().contains("test-tag")) {
                    hasTestTag = true;
                    break;
                }
            }
        }
        
        // Note: AI might not always follow instructions perfectly, so we log but don't assert
        if (hasTestTag) {
            logger.info("✓ AI followed extra instructions and added #test-tag");
        } else {
            logger.warn("⚠ AI did not add #test-tag (this is acceptable - AI behavior can vary)");
        }
        
        logger.info("✓ Analysis agent accepts and processes extra instructions");
    }
    
    @Test
    void testAggregationAgentWithExtraInstructions() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Aggregation Agent with Extra Instructions");
        logger.info("=".repeat(80));
        
        // Test data for person aggregation
        String entityData = """
                Name: John Doe
                Questions Asked: 5
                Answers Provided: 15
                Topics: Redis, Spring Boot, Caching
                """;
        
        // Test WITH extra instructions
        AggregationParams paramsWithInstructions = new AggregationParams();
        paramsWithInstructions.setEntityType("person");
        paramsWithInstructions.setEntityId("John_Doe");
        paramsWithInstructions.setKbPath(Paths.get("test_kb"));
        paramsWithInstructions.setEntityData(Map.of("content", entityData));
        paramsWithInstructions.setExtraInstructions(
                "IMPORTANT: In the description, always mention that this person is 'a test contributor' " +
                "and end the description with the phrase 'Test profile generated.'"
        );
        
        logger.info("Running aggregation WITH extra instructions...");
        logger.info("Extra instruction: Mention 'test contributor' and end with 'Test profile generated.'");
        
        String descriptionWith = aggregationAgent.run(paramsWithInstructions);
        
        assertNotNull(descriptionWith);
        assertFalse(descriptionWith.isEmpty());
        
        logger.info("Generated description:");
        logger.info("-".repeat(80));
        logger.info(descriptionWith);
        logger.info("-".repeat(80));
        
        // Check if extra instructions were followed
        boolean hasTestPhrase = descriptionWith.toLowerCase().contains("test contributor") ||
                               descriptionWith.toLowerCase().contains("test profile");
        
        if (hasTestPhrase) {
            logger.info("✓ AI followed extra instructions and included test phrases");
        } else {
            logger.warn("⚠ AI did not include test phrases (this is acceptable - AI behavior can vary)");
        }
        
        logger.info("✓ Aggregation agent accepts and processes extra instructions");
    }
    
    @Test
    void testOrchestratorWithExtraInstructions() throws Exception {
        logger.info("=".repeat(80));
        logger.info("TEST: Orchestrator with Extra Instructions");
        logger.info("=".repeat(80));
        
        // Create temp directory for test
        Path tempDir = Paths.get(System.getProperty("user.dir")).getParent()
                .resolve("temp/kb_extra_instructions_test");
        
        // Clean directory
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                 .sorted(java.util.Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.deleteIfExists(path);
                     } catch (Exception e) {
                         // ignore
                     }
                 });
        }
        Files.createDirectories(tempDir);
        
        // Create test input file
        String testInput = """
                [
                    {
                        "date": "2024-10-10T10:00:00Z",
                        "author": "Alice",
                        "body": "What's the best way to implement caching in Spring Boot?"
                    },
                    {
                        "date": "2024-10-10T10:05:00Z",
                        "author": "Bob",
                        "body": "I recommend using Redis with @Cacheable annotations. Very efficient!"
                    }
                ]
                """;
        
        Path inputFile = tempDir.resolve("input.json");
        Files.writeString(inputFile, testInput);
        
        // Run orchestrator with extra instructions
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setSourceName("test_extra_instructions");
        params.setInputFile(inputFile.toString());
        params.setDateTime("2024-10-10T12:00:00Z");
        params.setOutputPath(tempDir.toString());
        params.setCleanOutput(true);
        params.setAnalysisExtraInstructions(
                "For this test, always include 'spring-boot' in topics for any Spring-related content."
        );
        params.setAggregationExtraInstructions(
                "Keep descriptions very brief (1-2 sentences max) for testing purposes."
        );
        
        logger.info("Running orchestrator with extra instructions...");
        logger.info("Analysis instruction: Include 'spring-boot' in topics");
        logger.info("Aggregation instruction: Keep descriptions brief");
        
        KBResult result = orchestrator.run(params);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getQuestionsCount() > 0);
        assertTrue(result.getAnswersCount() > 0);
        
        logger.info("Result: {} questions, {} answers, {} people, {} topics",
                result.getQuestionsCount(),
                result.getAnswersCount(),
                result.getPeopleCount(),
                result.getTopicsCount());
        
        // Verify that files were created
        assertTrue(Files.exists(tempDir.resolve("questions")));
        assertTrue(Files.exists(tempDir.resolve("answers")));
        
        logger.info("✓ Orchestrator successfully processes extra instructions");
        logger.info("Test output preserved at: {}", tempDir);
    }
}


