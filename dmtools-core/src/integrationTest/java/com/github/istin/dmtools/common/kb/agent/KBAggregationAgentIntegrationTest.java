package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.common.kb.params.AggregationParams;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KBAggregationAgent with real AI
 * 
 * This agent generates narrative descriptions for KB entities:
 * - Person profiles
 * - Topic overviews
 * 
 * To run this test, you need:
 * 1. Valid AI configuration in dmtools.env
 * 2. Environment variable DMTOOLS_INTEGRATION_TESTS=true
 */
public class KBAggregationAgentIntegrationTest {
    
    private static final Logger logger = LogManager.getLogger(KBAggregationAgentIntegrationTest.class);
    
    private KBAggregationAgent agent;
    
    @BeforeEach
    void setUp() throws Exception {
        // Initialize agent via Dagger to use default AI from configuration
        KnowledgeBaseComponent component = DaggerKnowledgeBaseComponent.create();
        agent = component.kbAggregationAgent();
    }
    
    @Test
    void testAggregatePersonProfile() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBAggregationAgent - Person Profile");
        logger.info("=".repeat(80));
        logger.info("");
        
        // Prepare test data for person
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("name", "John Doe");
        entityData.put("questionsAsked", 12);
        entityData.put("answersProvided", 45);
        entityData.put("notesContributed", 8);
        entityData.put("topics", "AI Agents, Cursor Configuration, DMTools Development");
        entityData.put("sample_contributions", "High-quality answers on AI agent architecture, detailed solutions for Cursor setup issues");
        
        String entityDataText = """
                Name: John Doe
                Questions Asked: 12
                Answers Provided: 45
                Notes Contributed: 8
                Primary Topics: AI Agents, Cursor Configuration, DMTools Development
                Sample Contributions: High-quality answers on AI agent architecture, detailed solutions for Cursor setup issues
                """;
        
        // Prepare params
        AggregationParams params = new AggregationParams();
        params.setEntityType("person");
        params.setEntityId("John_Doe");
        params.setKbPath(Paths.get("test_kb"));
        params.setEntityData(Map.of("content", entityDataText));
        
        logger.info("Entity Type: " + params.getEntityType());
        logger.info("Entity ID: " + params.getEntityId());
        logger.info("");
        logger.info("Entity Data:");
        logger.info("-".repeat(80));
        logger.info(entityDataText);
        logger.info("-".repeat(80));
        logger.info("");
        
        // Run aggregation
        logger.info("Running AI aggregation...");
        logger.info("");
        
        String description = agent.run(params);
        
        // Print result
        logger.info("=".repeat(80));
        logger.info("GENERATED DESCRIPTION");
        logger.info("=".repeat(80));
        logger.info(description);
        logger.info("=".repeat(80));
        logger.info("");
        
        // Assertions
        assertNotNull(description);
        assertFalse(description.isEmpty(), "Description should not be empty");
        assertTrue(description.length() > 50, "Description should be substantial");
        
        // Check for expected content (person name, topics)
        // Note: AI might format these differently, so check loosely
        assertTrue(description.toLowerCase().contains("john") || 
                   description.toLowerCase().contains("contributor"),
                "Description should mention the person or their role");
        
        logger.info("✓ Person profile aggregation test passed successfully");
        logger.info("=".repeat(80));
    }
    
    @Test
    void testAggregateTopic() throws Exception {
        logger.info("=".repeat(80));
        logger.info("INTEGRATION TEST: KBAggregationAgent - Topic Overview");
        logger.info("=".repeat(80));
        logger.info("");
        
        // Prepare test data for topic
        String entityDataText = """
                Title: AI Agents
                Questions: 23
                Answers: 45
                Notes: 12
                Top Contributors: John Doe, Sarah Smith, Mike Johnson
                Common Themes: agent configuration, model selection, prompt engineering, error handling
                """;
        
        // Prepare params
        AggregationParams params = new AggregationParams();
        params.setEntityType("topic");
        params.setEntityId("ai-agents");
        params.setKbPath(Paths.get("test_kb"));
        params.setEntityData(Map.of("content", entityDataText));
        
        logger.info("Entity Type: " + params.getEntityType());
        logger.info("Entity ID: " + params.getEntityId());
        logger.info("");
        logger.info("Entity Data:");
        logger.info("-".repeat(80));
        logger.info(entityDataText);
        logger.info("-".repeat(80));
        logger.info("");
        
        // Run aggregation
        logger.info("Running AI aggregation...");
        logger.info("");
        
        String description = agent.run(params);
        
        // Print result
        logger.info("=".repeat(80));
        logger.info("GENERATED DESCRIPTION");
        logger.info("=".repeat(80));
        logger.info(description);
        logger.info("=".repeat(80));
        logger.info("");
        
        // Assertions
        assertNotNull(description);
        assertFalse(description.isEmpty(), "Description should not be empty");
        assertTrue(description.length() > 50, "Description should be substantial");
        
        // Check for expected content
        assertTrue(description.toLowerCase().contains("agent") ||
                   description.toLowerCase().contains("ai"),
                "Description should mention agents or AI");
        
        logger.info("✓ Topic overview aggregation test passed successfully");
        logger.info("=".repeat(80));
    }
}

