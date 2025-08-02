package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.InMemoryConfiguration;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.logging.CallbackLogger;
import com.github.istin.dmtools.logging.LogCallback;
import com.github.istin.dmtools.openai.BasicOpenAI;
import com.github.istin.dmtools.openai.PromptManager;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Module;
import dagger.Provides;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.IOException;

/**
 * Dagger module for server-managed job execution with pre-resolved integrations.
 * This module creates dependencies EXCLUSIVELY from credentials that have been resolved by the server.
 * If required integrations are not provided in resolvedIntegrations, methods will return null
 * instead of falling back to default implementations, ensuring consistent server-managed behavior.
 * Enhanced to support callback logger injection for execution monitoring.
 */
@Module
public class ServerManagedIntegrationsModule {
    
    private final JSONObject resolvedIntegrations;
    private final String executionId;
    private final LogCallback logCallback;
    
    // Original constructor for backward compatibility
    public ServerManagedIntegrationsModule(JSONObject resolvedIntegrations) {
        this(resolvedIntegrations, null, null);
    }
    
    // Enhanced constructor with callback logger support
    public ServerManagedIntegrationsModule(JSONObject resolvedIntegrations, String executionId, LogCallback logCallback) {
        this.resolvedIntegrations = resolvedIntegrations;
        this.executionId = executionId;
        this.logCallback = logCallback;
    }
    
    @Provides
    @Singleton
    ApplicationConfiguration provideConfiguration() {
        // Create an in-memory configuration from resolved integrations
        InMemoryConfiguration config = new InMemoryConfiguration();
        
        // Map resolved integrations to configuration properties using correct property names
        if (resolvedIntegrations.has("jira")) {
            JSONObject jiraConfig = resolvedIntegrations.getJSONObject("jira");
            if (jiraConfig.has("url")) {
                config.setProperty("JIRA_BASE_PATH", jiraConfig.getString("url"));
            }
            if (jiraConfig.has("token")) {
                config.setProperty("JIRA_LOGIN_PASS_TOKEN", jiraConfig.getString("token"));
            }
            if (jiraConfig.has("authType")) {
                config.setProperty("JIRA_AUTH_TYPE", jiraConfig.getString("authType"));
            }
            if (jiraConfig.has("username")) {
                config.setProperty("JIRA_USERNAME", jiraConfig.getString("username"));
            }
        }
        
        if (resolvedIntegrations.has("confluence")) {
            JSONObject confluenceConfig = resolvedIntegrations.getJSONObject("confluence");
            if (confluenceConfig.has("url")) {
                config.setProperty("CONFLUENCE_BASE_PATH", confluenceConfig.getString("url"));
            }
            if (confluenceConfig.has("token")) {
                config.setProperty("CONFLUENCE_LOGIN_PASS_TOKEN", confluenceConfig.getString("token"));
            }
            if (confluenceConfig.has("authType")) {
                config.setProperty("CONFLUENCE_AUTH_TYPE", confluenceConfig.getString("authType"));
            }
            if (confluenceConfig.has("defaultSpace")) {
                config.setProperty("CONFLUENCE_DEFAULT_SPACE", confluenceConfig.getString("defaultSpace"));
            }
        }
        
        if (resolvedIntegrations.has("openai")) {
            JSONObject openaiConfig = resolvedIntegrations.getJSONObject("openai");
            if (openaiConfig.has("apiKey")) {
                config.setProperty("OPEN_AI_API_KEY", openaiConfig.getString("apiKey"));
            }
            if (openaiConfig.has("model")) {
                config.setProperty("OPEN_AI_MODEL", openaiConfig.getString("model"));
            }
            if (openaiConfig.has("basePath")) {
                config.setProperty("OPEN_AI_BATH_PATH", openaiConfig.getString("basePath"));
            }
        }
        
        if (resolvedIntegrations.has("gemini")) {
            JSONObject geminiConfig = resolvedIntegrations.getJSONObject("gemini");
            if (geminiConfig.has("apiKey")) {
                config.setProperty("GEMINI_API_KEY", geminiConfig.getString("apiKey"));
            }
            if (geminiConfig.has("model")) {
                config.setProperty("GEMINI_DEFAULT_MODEL", geminiConfig.getString("model"));
            }
            if (geminiConfig.has("basePath")) {
                config.setProperty("GEMINI_BASE_PATH", geminiConfig.getString("basePath"));
            }
        }
        
        if (resolvedIntegrations.has("figma")) {
            JSONObject figmaConfig = resolvedIntegrations.getJSONObject("figma");
            if (figmaConfig.has("token")) {
                config.setProperty("FIGMA_TOKEN", figmaConfig.getString("token"));
            }
            if (figmaConfig.has("basePath")) {
                config.setProperty("FIGMA_BASE_PATH", figmaConfig.getString("basePath"));
            }
        }
        
        return config;
    }
    
    @Provides
    @Singleton
    public TrackerClient<? extends ITicket> provideTrackerClient() {
        try {
            System.out.println("🔧 [ServerManagedIntegrationsModule] Providing TrackerClient integration...");
            
            if (resolvedIntegrations == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] No resolved integrations found - returning null TrackerClient");
                return null;
            }
            
            // Create a custom JiraClient instance using resolved credentials
            if (resolvedIntegrations.has("jira")) {
                JSONObject jiraConfig = resolvedIntegrations.getJSONObject("jira");
                String basePath = jiraConfig.optString("url", "");
                String token = jiraConfig.optString("token", "");
                String authType = jiraConfig.optString("authType", "Basic");
                
                if (!basePath.isEmpty() && !token.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedJiraClient with resolved credentials");
                    return new CustomServerManagedJiraClient(basePath, token, authType);
                } else {
                    System.err.println("❌ [ServerManagedIntegrationsModule] Jira configuration missing required parameters (url=" + 
                        (basePath.isEmpty() ? "empty" : basePath) + ", token=" + (token.isEmpty() ? "empty" : "[SENSITIVE]") + ")");
                }
            } else {
                System.err.println("❌ [ServerManagedIntegrationsModule] No Jira integration found in resolved integrations");
            }
            
            System.err.println("❌ [ServerManagedIntegrationsModule] No valid Jira integration available - returning null TrackerClient");
            return null;
        } catch (IOException e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to create TrackerClient instance with resolved credentials: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Custom JiraClient implementation that replicates BasicJiraClient functionality
     * but uses resolved credentials instead of static properties from PropertyReader
     */
    private static class CustomServerManagedJiraClient extends JiraClient<com.github.istin.dmtools.atlassian.jira.model.Ticket> {
        private final String[] defaultJiraFields;
        private final String[] extendedJiraFields;
        
        public CustomServerManagedJiraClient(String basePath, String token, String authType) throws IOException {
            // Call parent constructor with resolved credentials
            super(basePath, token);
            
            // Set auth type if provided
            if (authType != null) {
                setAuthType(authType);
            }
            
            // Replicate BasicJiraClient configuration with reasonable defaults
            setLogEnabled(true);
            setWaitBeforePerform(true);
            setCacheGetRequestsEnabled(false);
            setSleepTimeRequest(100L);
            setClearCache(true);
            
            // Initialize field arrays exactly like BasicJiraClient
            java.util.List<String> defaultFields = new java.util.ArrayList<>();
            defaultFields.addAll(java.util.Arrays.asList(com.github.istin.dmtools.atlassian.jira.BasicJiraClient.DEFAULT_QUERY_FIELDS));
            defaultJiraFields = defaultFields.toArray(new String[0]);
            
            java.util.List<String> extendedFields = new java.util.ArrayList<>();
            extendedFields.addAll(java.util.Arrays.asList(com.github.istin.dmtools.atlassian.jira.BasicJiraClient.EXTENDED_QUERY_FIELDS));
            extendedFields.addAll(defaultFields);
            extendedJiraFields = extendedFields.toArray(new String[0]);
        }
        
        @Override
        public String getTextFieldsOnly(com.github.istin.dmtools.common.model.ITicket ticket) {
            StringBuilder ticketDescription = null;
            try {
                ticketDescription = new StringBuilder(ticket.getTicketTitle());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ticketDescription.append("\n").append(ticket.getTicketDescription());
            return ticketDescription.toString();
        }
        
        @Override
        public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
            // Empty implementation like BasicJiraClient
        }
        
        @Override
        public String[] getDefaultQueryFields() {
            return defaultJiraFields;
        }
        
        @Override
        public String[] getExtendedQueryFields() {
            return extendedJiraFields;
        }
        
        @Override
        public java.util.List<? extends com.github.istin.dmtools.common.model.ITicket> getTestCases(com.github.istin.dmtools.common.model.ITicket ticket) throws IOException {
            return java.util.Collections.emptyList();
        }
        
        @Override
        public com.github.istin.dmtools.common.tracker.TrackerClient.TextType getTextType() {
            return com.github.istin.dmtools.common.tracker.TrackerClient.TextType.MARKDOWN;
        }
    }
    
    @Provides
    @Singleton
    public Confluence provideConfluence() {
        try {
            System.out.println("🔧 [ServerManagedIntegrationsModule] Providing Confluence integration...");
            
            if (resolvedIntegrations == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] No resolved integrations found - returning null Confluence");
                return null;
            }
            
            if (!resolvedIntegrations.has("confluence")) {
                System.err.println("❌ [ServerManagedIntegrationsModule] No Confluence integration found in resolved integrations - returning null");
                return null;
            }
            
            JSONObject confluenceConfig = resolvedIntegrations.getJSONObject("confluence");
            System.out.println("🔧 [ServerManagedIntegrationsModule] Found Confluence configuration: " + confluenceConfig.length() + " parameters");
            
            // Log configuration details (without sensitive data)
            for (String key : confluenceConfig.keySet()) {
                Object value = confluenceConfig.get(key);
                System.out.println("  📋 Confluence config: " + key + "=" + 
                    (key.toLowerCase().contains("token") || key.toLowerCase().contains("password") ? "[SENSITIVE]" : value));
            }
            
            String url = confluenceConfig.optString("url", null);
            String token = confluenceConfig.optString("token", null);
            String defaultSpace = confluenceConfig.optString("defaultSpace", null);
            String authType = confluenceConfig.optString("authType", null);
            
            if (url == null || token == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] Confluence configuration missing required parameters (url=" + 
                    (url != null ? url : "null") + ", token=" + (token != null ? "[SENSITIVE]" : "null") + ") - returning null");
                return null;
            }
            
            // Ensure URL ends with /wiki for proper URL parsing by parseUris method
            if (!url.endsWith("/wiki")) {
                url = url + "/wiki";
            }
            
            System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedConfluence with url=" + url + 
                ", defaultSpace=" + (defaultSpace != null ? defaultSpace : "null") +
                ", authType=" + (authType != null ? authType : "null") +
                ", executionTracking=" + (executionId != null && logCallback != null ? "enabled" : "disabled"));
            
            return new CustomServerManagedConfluence(url, token, defaultSpace, authType, executionId, logCallback);
            
        } catch (Exception e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to provide Confluence integration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Custom Confluence implementation that replicates BasicConfluence functionality
     * but uses resolved credentials instead of static properties from PropertyReader
     */
    private static class CustomServerManagedConfluence extends Confluence {
        private final CallbackLogger callbackLogger;
        
        public CustomServerManagedConfluence(String basePath, String token, String defaultSpace, String authType, String executionId, LogCallback logCallback) throws IOException {
            super(basePath, token, LogManager.getLogger(CustomServerManagedConfluence.class), defaultSpace);
            setCacheGetRequestsEnabled(false);
            
            // Set auth type if provided
            if (authType != null && !authType.trim().isEmpty()) {
                setAuthType(authType);
            }
            
            this.callbackLogger = (executionId != null && logCallback != null) ? 
                new CallbackLogger(Confluence.class, executionId, logCallback) : null;
            System.out.println("🔧 [CustomServerManagedConfluence] Initialized with basePath=" + basePath + 
                ", defaultSpace=" + (defaultSpace != null ? defaultSpace : "null") +
                ", authType=" + (authType != null ? authType : "null") +
                ", callbackLogging=" + (callbackLogger != null ? "enabled" : "disabled"));
        }
    }
    
    @Provides
    @Singleton
    ConversationObserver provideConversationObserver() {
        return new ConversationObserver();
    }
    
    @Provides
    @Singleton
    AI provideAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        try {
            System.out.println("🤖 [ServerManagedIntegrationsModule] Providing AI integration...");
            
            if (resolvedIntegrations == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] No resolved integrations found - returning null AI");
                return null;
            }
            
            // Check available AI integrations in priority order
            System.out.println("🔍 [ServerManagedIntegrationsModule] Checking available AI integrations...");
            
            // First check for Gemini
            if (resolvedIntegrations.has("gemini")) {
                JSONObject geminiConfig = resolvedIntegrations.getJSONObject("gemini");
                System.out.println("🔍 [ServerManagedIntegrationsModule] Found Gemini configuration: " + geminiConfig.length() + " parameters");
                
                String apiKey = geminiConfig.optString("apiKey", null);
                if (apiKey != null && !apiKey.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Using resolved Gemini integration for AI provider");
                    return BasicGeminiAI.create(observer, configuration);
                } else {
                    System.out.println("⚠️ [ServerManagedIntegrationsModule] Gemini configuration missing apiKey, skipping");
                }
            }
            
            // Then check for OpenAI
            if (resolvedIntegrations.has("openai")) {
                JSONObject openaiConfig = resolvedIntegrations.getJSONObject("openai");
                System.out.println("🔍 [ServerManagedIntegrationsModule] Found OpenAI configuration: " + openaiConfig.length() + " parameters");
                
                String apiKey = openaiConfig.optString("apiKey", null);
                if (apiKey != null && !apiKey.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Using resolved OpenAI integration for AI provider");
                    return new BasicOpenAI(observer, configuration);
                } else {
                    System.out.println("⚠️ [ServerManagedIntegrationsModule] OpenAI configuration missing apiKey, skipping");
                }
            }
            
            System.err.println("❌ [ServerManagedIntegrationsModule] No valid AI integration found - returning null AI");
            return null;
            
        } catch (Exception e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to provide AI integration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    @Provides
    @Singleton
    IPromptTemplateReader providePromptTemplateReader() {
        return new PromptManager();
    }

    @Provides
    @Singleton
    UriToObjectFactory provideUriToObjectFactory(TrackerClient<? extends ITicket> trackerClient, 
                                                 Confluence confluence, 
                                                 SourceCodeFactory sourceCodeFactory) {
        System.out.println("🔧 [ServerManagedIntegrationsModule] Creating UriToObjectFactory with server-managed integrations");
        return new UriToObjectFactory(trackerClient, confluence, sourceCodeFactory);
    }

    @Provides
    @Singleton
    public FigmaClient provideFigmaClient() {
        try {
            System.out.println("🎨 [ServerManagedIntegrationsModule] Providing Figma integration...");
            
            if (resolvedIntegrations == null) {
                System.out.println("⚠️ [ServerManagedIntegrationsModule] No resolved integrations found, skipping Figma");
                return null;
            }
            
            if (!resolvedIntegrations.has("figma")) {
                System.out.println("⚠️ [ServerManagedIntegrationsModule] No Figma integration found in resolved integrations");
                return null;
            }
            
            JSONObject figmaConfig = resolvedIntegrations.getJSONObject("figma");
            System.out.println("🎨 [ServerManagedIntegrationsModule] Found Figma configuration: " + figmaConfig.length() + " parameters");
            
            // Log configuration details (without sensitive data)
            for (String key : figmaConfig.keySet()) {
                Object value = figmaConfig.get(key);
                System.out.println("  🎨 Figma config: " + key + "=" + 
                    (key.toLowerCase().contains("token") ? "[SENSITIVE]" : value));
            }
            
            String basePath = figmaConfig.optString("FIGMA_BASE_PATH", "https://api.figma.com/v1/");
            String token = figmaConfig.optString("FIGMA_TOKEN", null);
            
            if (token == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] Figma configuration missing required token parameter");
                return null;
            }
            
            System.out.println("✅ [ServerManagedIntegrationsModule] Creating FigmaClient with basePath=" + basePath);
            
            return new FigmaClient(basePath, token);
            
        } catch (Exception e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to provide Figma integration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
} 