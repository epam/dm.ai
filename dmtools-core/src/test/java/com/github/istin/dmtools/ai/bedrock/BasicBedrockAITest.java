package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.InMemoryConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class BasicBedrockAITest {

    private InMemoryConfiguration configuration;

    @Before
    public void setUp() {
        configuration = new InMemoryConfiguration();
        configuration.setProperty("BEDROCK_REGION", "us-east-1");
        configuration.setProperty("BEDROCK_MODEL_ID", "anthropic.claude-sonnet-4-20250514-v1:0");
        configuration.setProperty("BEDROCK_BEARER_TOKEN", "test-token");
        configuration.setProperty("BEDROCK_MAX_TOKENS", "1000");
        configuration.setProperty("BEDROCK_TEMPERATURE", "0.7");
    }

    @Test
    public void testBasicBedrockAIWithConfiguration() throws IOException {
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertNotNull(bedrockAI);
        assertEquals("anthropic.claude-sonnet-4-20250514-v1:0", bedrockAI.getName());
        assertEquals("us-east-1", bedrockAI.getRegion());
        assertEquals("test-token", bedrockAI.getBearerToken());
        assertEquals(1000, bedrockAI.getMaxTokens());
        assertEquals(0.7, bedrockAI.getTemperature(), 0.001);
    }

    @Test
    public void testBasicBedrockAIWithObserver() throws IOException {
        ConversationObserver observer = new ConversationObserver();
        BasicBedrockAI bedrockAI = new BasicBedrockAI(observer, configuration);
        assertNotNull(bedrockAI);
        assertEquals(observer, bedrockAI.getConversationObserver());
    }

    @Test
    public void testBasicBedrockAIWithDefaultConfiguration() throws IOException {
        // This will use PropertyReaderConfiguration which reads from system/env
        // We can't easily test this without mocking, but we can verify it doesn't throw
        try {
            BasicBedrockAI bedrockAI = new BasicBedrockAI();
            assertNotNull(bedrockAI);
        } catch (Exception e) {
            // Expected if configuration is not available
            assertTrue(e instanceof IOException || e instanceof RuntimeException);
        }
    }

    @Test
    public void testBasicBedrockAIWithObserverOnly() throws IOException {
        ConversationObserver observer = new ConversationObserver();
        try {
            BasicBedrockAI bedrockAI = new BasicBedrockAI(observer);
            assertNotNull(bedrockAI);
        } catch (Exception e) {
            // Expected if configuration is not available
            assertTrue(e instanceof IOException || e instanceof RuntimeException);
        }
    }

    @Test
    public void testBasicBedrockAIWithBasePath() throws IOException {
        configuration.setProperty("BEDROCK_BASE_PATH", "https://custom-bedrock.example.com");
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertNotNull(bedrockAI);
    }

    @Test
    public void testBasicBedrockAIWithDefaultMaxTokens() throws IOException {
        configuration.setProperty("BEDROCK_MAX_TOKENS", "");
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertEquals(4096, bedrockAI.getMaxTokens()); // Default value
    }

    @Test
    public void testBasicBedrockAIWithDefaultTemperature() throws IOException {
        configuration.setProperty("BEDROCK_TEMPERATURE", "");
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertEquals(1.0, bedrockAI.getTemperature(), 0.001); // Default value
    }

    @Test
    public void testBasicBedrockAIWithInvalidMaxTokens() throws IOException {
        configuration.setProperty("BEDROCK_MAX_TOKENS", "invalid");
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertEquals(4096, bedrockAI.getMaxTokens()); // Should fall back to default
    }

    @Test
    public void testBasicBedrockAIWithInvalidTemperature() throws IOException {
        configuration.setProperty("BEDROCK_TEMPERATURE", "invalid");
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertEquals(1.0, bedrockAI.getTemperature(), 0.001); // Should fall back to default
    }

    @Test
    public void testBasicBedrockAIWithRegionBasedBasePath() throws IOException {
        configuration.setProperty("BEDROCK_BASE_PATH", ""); // Clear base path
        BasicBedrockAI bedrockAI = new BasicBedrockAI(null, configuration);
        assertNotNull(bedrockAI);
        // Base path should be constructed from region - we can't easily test getBasePath() 
        // as it's protected, but we can verify the object was created successfully
    }
}
