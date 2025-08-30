package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.dial.DialAIClient;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.utils.SecurityUtils;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.InMemoryConfiguration;

import com.github.istin.dmtools.github.GitHub;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.logging.CallbackLogger;
import com.github.istin.dmtools.logging.LogCallback;
import com.github.istin.dmtools.prompt.PromptManager;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Module;
import dagger.Provides;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;

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
    
    /**
     * Provides a minimal ApplicationConfiguration for backward compatibility.
     * Server-managed integrations should NOT rely on this configuration as it's empty.
     * All integration parameters are resolved directly from the integrations JSON.
     */
    @Provides
    @Singleton
    ApplicationConfiguration provideServerManagedConfiguration() {
        System.out.println("⚠️ [ServerManagedIntegrationsModule] Providing empty ApplicationConfiguration for backward compatibility only");
        return new InMemoryConfiguration(); // Empty configuration
    }

    @Provides
    @Singleton
    public SourceCodeFactory provideSourceCodeFactory() {
        return new SourceCodeFactory();
    }

    @Provides
    @Singleton
    public List<SourceCode> provideSourceCodes() {
        try {
            System.out.println("🔧 [ServerManagedIntegrationsModule] Providing SourceCode integrations...");
            List<SourceCode> sourceCodes = new ArrayList<>();
            
            // Check if GitHub is configured and add it
            if (resolvedIntegrations.has("github")) {
                JSONObject githubConfig = resolvedIntegrations.getJSONObject("github");
                String url = githubConfig.optString("SOURCE_GITHUB_BASE_PATH", null);
                String token = githubConfig.optString("SOURCE_GITHUB_TOKEN", null);
                String workspace = githubConfig.optString("SOURCE_GITHUB_WORKSPACE", null);
                String repository = githubConfig.optString("SOURCE_GITHUB_REPOSITORY", null);
                
                if (url != null && token != null) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedGitHub with resolved credentials");
                    SourceCode githubSourceCode = new CustomServerManagedGitHub(url, token, workspace, repository);
                    sourceCodes.add(githubSourceCode);
                } else {
                    System.err.println("❌ [ServerManagedIntegrationsModule] GitHub configuration missing required parameters (url=" + 
                        (url != null ? url : "null") + ", token=" + (token != null ? "[SENSITIVE]" : "null") + ")");
                }
            }
            
            return sourceCodes;
        } catch (IOException e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to create SourceCode instances: " + e.getMessage());
            throw new RuntimeException("Failed to create SourceCode instances", e);
        }
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
            
            // Create a custom JiraClient instance using resolved credentials with JSON parameter names
            if (resolvedIntegrations.has("jira")) {
                JSONObject jiraConfig = resolvedIntegrations.getJSONObject("jira");
                String basePath = jiraConfig.optString("JIRA_BASE_PATH", "");
                String authType = jiraConfig.optString("JIRA_AUTH_TYPE", "Basic");
                String extraFields = jiraConfig.optString("JIRA_EXTRA_FIELDS_PROJECT", "");
                
                // Handle authentication - priority: email+token combination > legacy token
                String token = "";
                if (jiraConfig.has("JIRA_EMAIL") && jiraConfig.has("JIRA_API_TOKEN")) {
                    String email = jiraConfig.optString("JIRA_EMAIL", "");
                    String apiToken = jiraConfig.optString("JIRA_API_TOKEN", "");
                    if (!email.isEmpty() && !apiToken.isEmpty()) {
                        // Combine email:token and base64 encode
                        String credentials = email.trim() + ":" + apiToken.trim();
                        token = Base64.getEncoder().encodeToString(credentials.getBytes());
                        System.out.println("✅ [ServerManagedIntegrationsModule] Combined JIRA_EMAIL + JIRA_API_TOKEN for authentication");
                    }
                } else if (jiraConfig.has("JIRA_LOGIN_PASS_TOKEN")) {
                    token = jiraConfig.optString("JIRA_LOGIN_PASS_TOKEN", "");
                }
                
                if (!basePath.isEmpty() && !token.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedJiraClient with resolved credentials");
                    return new CustomServerManagedJiraClient(basePath, token, authType, extraFields);
                } else {
                    System.err.println("❌ [ServerManagedIntegrationsModule] Jira configuration missing required parameters (JIRA_BASE_PATH=" + 
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
    private static class CustomServerManagedJiraClient extends JiraClient<Ticket> {
        private final String[] defaultJiraFields;
        private final String[] extendedJiraFields;
        
        public CustomServerManagedJiraClient(String basePath, String token, String authType, String extraFields) throws IOException {
            // Call parent constructor with resolved credentials
            super(basePath, token);
            
            // Set auth type if provided
            if (authType != null) {
                setAuthType(authType);
            }
            
            // Replicate BasicJiraClient configuration with reasonable defaults
            setLogEnabled(true);
            setWaitBeforePerform(true);
            setSleepTimeRequest(100L);
            
            // Performance optimization: Clean cache folder first, then enable caching
            setClearCache(true);  // This cleans the cache folder
            setCacheGetRequestsEnabled(true);  // Enable caching for better performance
            System.out.println("✅ [CustomServerManagedJiraClient] Cache cleaned and enabled for performance optimization");
            
            // Initialize field arrays exactly like BasicJiraClient
            List<String> defaultFields = new ArrayList<>(Arrays.asList(BasicJiraClient.DEFAULT_QUERY_FIELDS));
            defaultJiraFields = defaultFields.toArray(new String[0]);
            
            List<String> extendedFields = new ArrayList<>();
            extendedFields.addAll(Arrays.asList(BasicJiraClient.EXTENDED_QUERY_FIELDS));
            extendedFields.addAll(defaultFields);
            
            // Add extra fields if configured
            if (extraFields != null && !extraFields.trim().isEmpty()) {
                String[] additionalFields = extraFields.split(",");
                for (String field : additionalFields) {
                    String trimmedField = field.trim();
                    if (!trimmedField.isEmpty()) {
                        extendedFields.add(trimmedField);
                    }
                }
                System.out.println("✅ [CustomServerManagedJiraClient] Added extra fields: " + extraFields);
            }
            
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
        public List<? extends ITicket> getTestCases(ITicket ticket) throws IOException {
            return Collections.emptyList();
        }
        
        @Override
        public TrackerClient.TextType getTextType() {
            return TrackerClient.TextType.MARKDOWN;
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
            
            String url = confluenceConfig.optString("CONFLUENCE_BASE_PATH", null);
            String defaultSpace = confluenceConfig.optString("CONFLUENCE_DEFAULT_SPACE", null);
            String authType = confluenceConfig.optString("CONFLUENCE_AUTH_TYPE", null);
            
            // Handle authentication - priority: email+token combination > legacy token
            String token = null;
            if (confluenceConfig.has("CONFLUENCE_EMAIL") && confluenceConfig.has("CONFLUENCE_API_TOKEN")) {
                String email = confluenceConfig.optString("CONFLUENCE_EMAIL", "");
                String apiToken = confluenceConfig.optString("CONFLUENCE_API_TOKEN", "");
                if (!email.isEmpty() && !apiToken.isEmpty()) {
                    if ("Bearer".equalsIgnoreCase(authType)) {
                        // For Bearer auth, use token directly
                        token = apiToken.trim();
                    } else {
                        // For Basic auth, combine email:token and base64 encode
                        String credentials = email.trim() + ":" + apiToken.trim();
                        token = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
                    }
                    System.out.println("✅ [ServerManagedIntegrationsModule] Combined CONFLUENCE_EMAIL + CONFLUENCE_API_TOKEN for authentication");
                }
            } else if (confluenceConfig.has("CONFLUENCE_LOGIN_PASS_TOKEN")) {
                token = confluenceConfig.optString("CONFLUENCE_LOGIN_PASS_TOKEN", null);
            }
            
            if (url == null || token == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] Confluence configuration missing required parameters (url=" + 
                    (url != null ? url : "null") + ", token=" + (token != null ? "[SENSITIVE]" : "null") + ") - returning null");
                return null;
            }
            
            // Ensure URL ends with /wiki for proper URL parsing by parseUris method
//            if (!url.endsWith("/wiki")) {
//                url = url + "/wiki";
//            }
            
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
    AI provideAI(ConversationObserver observer) {
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
                
                String apiKey = geminiConfig.optString("GEMINI_API_KEY", null);
                String model = geminiConfig.optString("GEMINI_DEFAULT_MODEL", "gemini-1.5-flash");
                String basePath = geminiConfig.optString("GEMINI_BASE_PATH", null);
                
                if (apiKey != null && !apiKey.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating custom Gemini JSAIClient with resolved credentials");
                    return createCustomGeminiAI(apiKey, model, basePath, observer);
                } else {
                    System.out.println("⚠️ [ServerManagedIntegrationsModule] Gemini configuration missing GEMINI_API_KEY, skipping");
                }
            }
            
            // Then check for Dial
            if (resolvedIntegrations.has("dial")) {
                JSONObject dialConfig = resolvedIntegrations.getJSONObject("dial");
                System.out.println("🔍 [ServerManagedIntegrationsModule] Found Dial configuration: " + dialConfig.length() + " parameters");
                
                String apiKey = dialConfig.optString("DIAL_AI_API_KEY", null);
                String model = dialConfig.optString("DIAL_AI_MODEL", "gpt-4");
                String basePath = dialConfig.optString("DIAL_AI_BATH_PATH", "https://api.openai.com/v1");
                
                if (apiKey != null && !apiKey.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating custom DialAIClient with resolved credentials");
                    return new DialAIClient(basePath, apiKey, model, observer);
                } else {
                    System.out.println("⚠️ [ServerManagedIntegrationsModule] Dial configuration missing DIAL_AI_API_KEY, skipping");
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
    
    /**
     * Custom GitHub implementation that replicates BasicGithub functionality
     * but uses resolved credentials instead of static properties from PropertyReader
     */
    private static class CustomServerManagedGitHub extends GitHub {
        private final SourceCodeConfig config;
        
        public CustomServerManagedGitHub(String basePath, String token, String workspace, String repository) throws IOException {
            super(basePath, token);
            setClearCache(true);
            setCacheGetRequestsEnabled(false);
            setCachePostRequestsEnabled(false);
            this.config = SourceCodeConfig.builder()
                    .path(basePath)
                    .auth(token)
                    .workspaceName(workspace)
                    .repoName(repository)
                    .branchName("main") // Default branch
                    .type(SourceCodeConfig.Type.GITHUB)
                    .build();
        }
        
        @Override
        public String getDefaultRepository() {
            return config.getRepoName();
        }
        
        @Override
        public String getDefaultBranch() {
            return config.getBranchName();
        }
        
        @Override
        public String getDefaultWorkspace() {
            return config.getWorkspaceName();
        }
        
        @Override
        public boolean isConfigured() {
            return config.isConfigured();
        }
        
        @Override
        public SourceCodeConfig getDefaultConfig() {
            return config;
        }
    }
    
    /**
     * Creates a custom Gemini AI client directly with resolved credentials, bypassing ApplicationConfiguration.
     * This replicates BasicGeminiAI.create() logic but uses resolved credentials directly.
     */
    private AI createCustomGeminiAI(String apiKey, String model, String basePath, ConversationObserver observer) {
        try {
            JSONObject configJson = new JSONObject();
            
            // Use the same script path as BasicGeminiAI
            configJson.put("jsScriptPath", "js/geminiChatViaJs.js");
            
            // Use a specific client name for server-managed Gemini
            configJson.put("clientName", "GeminiJSAIClientViaServerManagedIntegrationsModule");
            
            // Use resolved model
            configJson.put("defaultModel", model);
            
            // Use resolved base path
            if (basePath != null && !basePath.trim().isEmpty()) {
                configJson.put("basePath", basePath);
            }
            
            // Set up secrets with resolved API key
            JSONObject secretsJson = new JSONObject();
            secretsJson.put("GEMINI_API_KEY", apiKey);
            configJson.put("secrets", secretsJson);
            
            // Log the masked version to protect sensitive information
            JSONObject maskedConfig = SecurityUtils.maskSensitiveInformation(configJson);
            System.out.println("✅ [ServerManagedIntegrationsModule] Initializing custom Gemini JSAIClient with config: " + maskedConfig.toString(2));
            
            return new JSAIClient(configJson, observer);
            
        } catch (Exception e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to create custom Gemini AI: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create custom Gemini AI", e);
        }
    }
    
    /**
     * Public method to create AI instance directly from the module.
     * This allows external services to use the module's AI creation logic
     * without duplicating the implementation.
     * 
     * @return AI instance created using the module's logic
     */
    public AI createAI() {
        ConversationObserver observer = provideConversationObserver();
        return provideAI(observer);
    }
} 