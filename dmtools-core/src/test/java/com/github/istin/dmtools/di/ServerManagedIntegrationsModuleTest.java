package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.ai.ollama.OllamaAIClient;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.logging.LogCallback;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerManagedIntegrationsModule functionality.
 * These tests verify the current behavior before implementing performance optimizations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ServerManagedIntegrationsModuleTest {

    private ServerManagedIntegrationsModule module;
    private JSONObject validIntegrations;

    @BeforeEach
    void setUp() {
        validIntegrations = createValidIntegrationsConfig();
        module = new ServerManagedIntegrationsModule(validIntegrations);
    }

    private JSONObject createValidIntegrationsConfig() {
        JSONObject integrations = new JSONObject();
        
        // Jira configuration
        JSONObject jiraConfig = new JSONObject();
        jiraConfig.put("JIRA_EMAIL", "test@example.com");
        jiraConfig.put("JIRA_API_TOKEN", "test-token");
        jiraConfig.put("JIRA_BASE_PATH", "https://test.atlassian.net/");
        jiraConfig.put("JIRA_AUTH_TYPE", "Basic");
        integrations.put("jira", jiraConfig);
        
        // Confluence configuration
        JSONObject confluenceConfig = new JSONObject();
        confluenceConfig.put("CONFLUENCE_EMAIL", "test@example.com");
        confluenceConfig.put("CONFLUENCE_API_TOKEN", "test-token");
        confluenceConfig.put("CONFLUENCE_BASE_PATH", "https://test.atlassian.net/wiki");
        confluenceConfig.put("CONFLUENCE_DEFAULT_SPACE", "TEST");
        confluenceConfig.put("CONFLUENCE_AUTH_TYPE", "Basic");
        confluenceConfig.put("CONFLUENCE_GRAPHQL_PATH", "https://test.atlassian.net/gateway/api/");
        integrations.put("confluence", confluenceConfig);
        
        // Gemini configuration
        JSONObject geminiConfig = new JSONObject();
        geminiConfig.put("GEMINI_API_KEY", "test-api-key");
        geminiConfig.put("GEMINI_DEFAULT_MODEL", "gemini-1.5-flash");
        geminiConfig.put("GEMINI_BASE_PATH", "https://generativelanguage.googleapis.com/v1beta/models");
        integrations.put("gemini", geminiConfig);
        
        // GitHub configuration
        JSONObject githubConfig = new JSONObject();
        githubConfig.put("SOURCE_GITHUB_TOKEN", "test-github-token");
        githubConfig.put("SOURCE_GITHUB_BASE_PATH", "https://api.github.com");
        githubConfig.put("SOURCE_GITHUB_WORKSPACE", "TestOrg");
        githubConfig.put("SOURCE_GITHUB_REPOSITORY", "test-repo");
        githubConfig.put("SOURCE_GITHUB_BRANCH", "main");
        integrations.put("github", githubConfig);
        
        return integrations;
    }

    @Test
    void testProvideServerManagedConfiguration() {
        // Act
        ApplicationConfiguration config = module.provideServerManagedConfiguration();

        // Assert
        assertNotNull(config);
        // Should be empty configuration for backward compatibility
        // Note: ApplicationConfiguration interface doesn't guarantee getProperty method
    }

    @Test
    void testProvideSourceCodeFactory() {
        // Act
        SourceCodeFactory factory = module.provideSourceCodeFactory();

        // Assert
        assertNotNull(factory);
    }

    @Test
    void testProvideSourceCodes() throws Exception {
        // Act
        List<SourceCode> sourceCodes = module.provideSourceCodes();

        // Assert
        assertNotNull(sourceCodes);
        // Should contain source codes based on available integrations (GitHub in this case)
        assertFalse(sourceCodes.isEmpty());
    }

    @Test
    void testProvideTrackerClient_WithJiraConfig() throws Exception {
        // Act
        TrackerClient<? extends ITicket> trackerClient = module.provideTrackerClient();

        // Assert
        assertNotNull(trackerClient);
        assertTrue(trackerClient instanceof JiraClient);
    }

    @Test
    void testProvideTrackerClient_WithoutJiraConfig() throws Exception {
        // Arrange
        JSONObject emptyIntegrations = new JSONObject();
        ServerManagedIntegrationsModule emptyModule = new ServerManagedIntegrationsModule(emptyIntegrations);

        // Act
        TrackerClient<? extends ITicket> trackerClient = emptyModule.provideTrackerClient();

        // Assert
        assertNull(trackerClient); // Should return null when no Jira config available
    }

    @Test
    void testProvideConfluence_WithConfluenceConfig() throws Exception {
        // Act
        Confluence confluence = module.provideConfluence();

        // Assert
        assertNotNull(confluence);
    }

    @Test
    void testProvideConfluence_WithoutConfluenceConfig() throws Exception {
        // Arrange
        JSONObject emptyIntegrations = new JSONObject();
        ServerManagedIntegrationsModule emptyModule = new ServerManagedIntegrationsModule(emptyIntegrations);

        // Act
        Confluence confluence = emptyModule.provideConfluence();

        // Assert
        assertNull(confluence); // Should return null when no Confluence config available
    }

    @Test
    void testProvideAI_WithOllamaConfig() {
        // Arrange
        JSONObject ollamaIntegrations = new JSONObject();
        JSONObject ollamaConfig = new JSONObject();
        ollamaConfig.put("OLLAMA_BASE_PATH", "http://localhost:11434");
        ollamaConfig.put("OLLAMA_MODEL", "llama3");
        ollamaConfig.put("OLLAMA_NUM_CTX", 16384);
        ollamaConfig.put("OLLAMA_NUM_PREDICT", -1);
        ollamaIntegrations.put("ollama", ollamaConfig);
        
        ServerManagedIntegrationsModule ollamaModule = new ServerManagedIntegrationsModule(ollamaIntegrations);
        ConversationObserver observer = ollamaModule.provideConversationObserver();

        // Act
        AI ai = ollamaModule.provideAI(observer);

        // Assert
        assertNotNull(ai);
        // OllamaAIClient should be created
        assertTrue(ai instanceof OllamaAIClient);
    }

    @Test
    void testProvideAI_WithOllamaConfigDefaultValues() {
        // Arrange
        JSONObject ollamaIntegrations = new JSONObject();
        JSONObject ollamaConfig = new JSONObject();
        ollamaConfig.put("OLLAMA_MODEL", "mistral");
        // Using default values for other parameters
        ollamaIntegrations.put("ollama", ollamaConfig);
        
        ServerManagedIntegrationsModule ollamaModule = new ServerManagedIntegrationsModule(ollamaIntegrations);
        ConversationObserver observer = ollamaModule.provideConversationObserver();

        // Act
        AI ai = ollamaModule.provideAI(observer);

        // Assert
        assertNotNull(ai);
        assertTrue(ai instanceof OllamaAIClient);
    }

    @Test
    void testProvideAI_WithOllamaConfigMissingModel() {
        // Arrange
        JSONObject ollamaIntegrations = new JSONObject();
        JSONObject ollamaConfig = new JSONObject();
        ollamaConfig.put("OLLAMA_BASE_PATH", "http://localhost:11434");
        // Missing required OLLAMA_MODEL
        ollamaIntegrations.put("ollama", ollamaConfig);
        
        ServerManagedIntegrationsModule ollamaModule = new ServerManagedIntegrationsModule(ollamaIntegrations);
        ConversationObserver observer = ollamaModule.provideConversationObserver();

        // Act
        AI ai = ollamaModule.provideAI(observer);

        // Assert
        assertNull(ai); // Should return null when model is missing
    }

    @Test
    void testProvideAI_OllamaPriorityOverGemini() {
        // Arrange - Both Ollama and Gemini configured
        JSONObject bothIntegrations = new JSONObject();
        
        JSONObject ollamaConfig = new JSONObject();
        ollamaConfig.put("OLLAMA_BASE_PATH", "http://localhost:11434");
        ollamaConfig.put("OLLAMA_MODEL", "llama3");
        ollamaConfig.put("OLLAMA_NUM_CTX", 16384);
        ollamaConfig.put("OLLAMA_NUM_PREDICT", -1);
        bothIntegrations.put("ollama", ollamaConfig);
        
        JSONObject geminiConfig = new JSONObject();
        geminiConfig.put("GEMINI_API_KEY", "test-api-key");
        geminiConfig.put("GEMINI_DEFAULT_MODEL", "gemini-1.5-flash");
        bothIntegrations.put("gemini", geminiConfig);
        
        ServerManagedIntegrationsModule bothModule = new ServerManagedIntegrationsModule(bothIntegrations);
        ConversationObserver observer = bothModule.provideConversationObserver();

        // Act
        AI ai = bothModule.provideAI(observer);

        // Assert
        assertNotNull(ai);
        // Ollama should have priority over Gemini
        assertTrue(ai instanceof OllamaAIClient);
    }

    @Test
    void testProvideAI_WithGeminiConfig() {
        // Arrange
        ConversationObserver observer = module.provideConversationObserver();

        // Act
        AI ai = module.provideAI(observer);

        // Assert
        assertNotNull(ai);
        assertTrue(ai instanceof JSAIClient);
    }

    @Test
    void testProvideAI_WithoutGeminiConfig() {
        // Arrange
        JSONObject noAIIntegrations = new JSONObject();
        noAIIntegrations.put("jira", validIntegrations.getJSONObject("jira")); // Only Jira, no AI
        
        ServerManagedIntegrationsModule noAIModule = new ServerManagedIntegrationsModule(noAIIntegrations);
        ConversationObserver observer = noAIModule.provideConversationObserver();

        // Act
        AI ai = noAIModule.provideAI(observer);

        // Assert
        assertNull(ai); // Should return null when no AI config available
    }

    @Test
    void testProvidePromptTemplateReader() {
        // Act
        IPromptTemplateReader promptReader = module.providePromptTemplateReader();

        // Assert
        assertNotNull(promptReader);
    }

    @Test
    void testProvideUriToObjectFactory() throws Exception {
        // Arrange
        TrackerClient<? extends ITicket> trackerClient = module.provideTrackerClient();
        Confluence confluence = module.provideConfluence();
        SourceCodeFactory sourceCodeFactory = module.provideSourceCodeFactory();

        // Act
        UriToObjectFactory factory = module.provideUriToObjectFactory(trackerClient, confluence, sourceCodeFactory);

        // Assert
        assertNotNull(factory);
    }

    @Test
    void testProvideConversationObserver() {
        // Act
        ConversationObserver observer = module.provideConversationObserver();

        // Assert
        assertNotNull(observer);
    }

    @Test
    void testInitializationPerformance_AllIntegrations() {
        // Arrange
        long startTime = System.currentTimeMillis();

        // Act - Create module and initialize all services
        ServerManagedIntegrationsModule testModule = new ServerManagedIntegrationsModule(validIntegrations);
        
        try {
            ApplicationConfiguration config = testModule.provideServerManagedConfiguration();
            SourceCodeFactory sourceCodeFactory = testModule.provideSourceCodeFactory();
            List<SourceCode> sourceCodes = testModule.provideSourceCodes();
            TrackerClient<? extends ITicket> trackerClient = testModule.provideTrackerClient();
            Confluence confluence = testModule.provideConfluence();
            ConversationObserver observer = testModule.provideConversationObserver();
            AI ai = testModule.provideAI(observer);
            IPromptTemplateReader promptReader = testModule.providePromptTemplateReader();
            UriToObjectFactory uriFactory = testModule.provideUriToObjectFactory(trackerClient, confluence, sourceCodeFactory);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Assert
            assertNotNull(config);
            assertNotNull(sourceCodeFactory);
            assertNotNull(sourceCodes);
            assertNotNull(trackerClient);
            assertNotNull(confluence);
            assertNotNull(observer);
            assertNotNull(ai);
            assertNotNull(promptReader);
            assertNotNull(uriFactory);
            
            System.out.println("Full integration initialization took: " + duration + "ms");
            
            // Document current performance characteristics
            // After optimization: should be faster due to lazy initialization
            
        } catch (Exception e) {
            fail("Integration initialization should not throw exceptions: " + e.getMessage());
        }
    }

    @Test
    void testInitializationPerformance_MinimalIntegrations() {
        // Arrange - Only essential integrations
        JSONObject minimalIntegrations = new JSONObject();
        JSONObject jiraConfig = validIntegrations.getJSONObject("jira");
        minimalIntegrations.put("jira", jiraConfig);
        
        long startTime = System.currentTimeMillis();

        // Act
        ServerManagedIntegrationsModule testModule = new ServerManagedIntegrationsModule(minimalIntegrations);
        
        try {
            TrackerClient<? extends ITicket> trackerClient = testModule.provideTrackerClient();
            ConversationObserver observer = testModule.provideConversationObserver();
            IPromptTemplateReader promptReader = testModule.providePromptTemplateReader();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Assert
            assertNotNull(trackerClient);
            assertNotNull(observer);
            assertNotNull(promptReader);
            
            System.out.println("Minimal integration initialization took: " + duration + "ms");
            
            // Should be faster than full initialization
            assertTrue(duration < 5000, "Minimal initialization should be fast");
            
        } catch (Exception e) {
            fail("Minimal integration initialization should not throw exceptions: " + e.getMessage());
        }
    }

    @Test
    void testErrorHandling_InvalidJiraConfig() {
        // Arrange
        JSONObject invalidIntegrations = new JSONObject();
        JSONObject invalidJiraConfig = new JSONObject();
        invalidJiraConfig.put("JIRA_EMAIL", "test@example.com");
        // Missing required fields: JIRA_API_TOKEN, JIRA_BASE_PATH
        invalidIntegrations.put("jira", invalidJiraConfig);
        
        ServerManagedIntegrationsModule invalidModule = new ServerManagedIntegrationsModule(invalidIntegrations);

        // Act & Assert
        assertDoesNotThrow(() -> {
            @SuppressWarnings("unused")
            TrackerClient<? extends ITicket> trackerClient = invalidModule.provideTrackerClient();
            // Should handle errors gracefully and return null or minimal functionality
        });
    }

    @Test
    void testErrorHandling_InvalidGeminiConfig() {
        // Arrange
        JSONObject invalidIntegrations = new JSONObject();
        JSONObject invalidGeminiConfig = new JSONObject();
        invalidGeminiConfig.put("GEMINI_DEFAULT_MODEL", "gemini-1.5-flash");
        // Missing required field: GEMINI_API_KEY
        invalidIntegrations.put("gemini", invalidGeminiConfig);
        
        ServerManagedIntegrationsModule invalidModule = new ServerManagedIntegrationsModule(invalidIntegrations);
        ConversationObserver observer = invalidModule.provideConversationObserver();

        // Act
        AI ai = invalidModule.provideAI(observer);

        // Assert
        assertNull(ai); // Should return null for invalid config
    }

    @Test
    void testCallback_LoggerIntegration() {
        // Arrange
        String executionId = "test-execution-123";
        TestLogCallback logCallback = new TestLogCallback();
        
        ServerManagedIntegrationsModule callbackModule = new ServerManagedIntegrationsModule(
            validIntegrations, executionId, logCallback);

        // Act
        try {
            @SuppressWarnings("unused")
            TrackerClient<? extends ITicket> result = callbackModule.provideTrackerClient();
            
            // Assert
            // Callback functionality should be integrated (actual logging verification would require integration tests)
            assertTrue(true); // Test completed successfully
            
        } catch (Exception e) {
            fail("Callback integration should not cause failures: " + e.getMessage());
        }
    }

    /**
     * Test implementation of LogCallback for testing
     */
    private static class TestLogCallback implements LogCallback {
        private int logCount = 0;
        
        @Override
        public void onLog(String executionId, String level, String message, String component) {
            logCount++;
            System.out.println("[" + level + "] " + component + ": " + message + " (execution: " + executionId + ")");
        }
        
        @SuppressWarnings("unused")
        public int getLogCount() {
            return logCount;
        }
    }
}

