package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.dial.DialAIClient;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.ai.ollama.OllamaAIClient;
import com.github.istin.dmtools.ai.anthropic.AnthropicAIClient;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.xray.XrayClient;
import com.github.istin.dmtools.broadcom.rally.RallyClient;
import com.github.istin.dmtools.broadcom.rally.model.RallyFields;
import com.github.istin.dmtools.common.utils.SecurityUtils;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient;
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
import java.util.Base64;

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

            // Check if DEFAULT_TRACKER is set
            String defaultTracker = null;
            if (resolvedIntegrations.has("DEFAULT_TRACKER")) {
                defaultTracker = resolvedIntegrations.optString("DEFAULT_TRACKER", null);
                System.out.println("🔧 [ServerManagedIntegrationsModule] DEFAULT_TRACKER set to: " + defaultTracker);
            }
            
            // Helper method to create clients
            TrackerClient<? extends ITicket> client = null;
            
            // If DEFAULT_TRACKER is set, try to initialize that specific provider first
            if (defaultTracker != null && !defaultTracker.isEmpty()) {
                if ("jira".equalsIgnoreCase(defaultTracker)) {
                    client = createJiraClient(resolvedIntegrations);
                } else if ("jira_xray".equalsIgnoreCase(defaultTracker)) {
                    client = createXrayClient(resolvedIntegrations);
                } else if ("ado".equalsIgnoreCase(defaultTracker)) {
                    client = createAdoClient(resolvedIntegrations);
                } else if ("rally".equalsIgnoreCase(defaultTracker)) {
                    client = createRallyClient(resolvedIntegrations);
                }
                
                if (client != null) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Successfully created preferred tracker: " + defaultTracker);
                    return client;
                } else {
                    System.err.println("⚠️ [ServerManagedIntegrationsModule] Failed to create preferred tracker: " + defaultTracker + ". Falling back to auto-detection.");
                }
            }

            // Auto-detection fallback (original logic)
            
            // Check for X-ray configuration first (requires both jira and xray configs)
            if (resolvedIntegrations.has("jira") && resolvedIntegrations.has("xray")) {
                client = createXrayClient(resolvedIntegrations);
                if (client != null) return client;
            }
            
            // Create a custom JiraClient instance using resolved credentials with JSON parameter names
            if (resolvedIntegrations.has("jira")) {
                client = createJiraClient(resolvedIntegrations);
                if (client != null) return client;
            }
            
            // Create an ADO client instance using resolved credentials
            if (resolvedIntegrations.has("ado")) {
                client = createAdoClient(resolvedIntegrations);
                if (client != null) return client;
            }

            // Create a Rally client instance using resolved credentials
            if (resolvedIntegrations.has("rally")) {
                client = createRallyClient(resolvedIntegrations);
                if (client != null) return client;
            }
            
            System.err.println("❌ [ServerManagedIntegrationsModule] No valid TrackerClient integration (Jira or ADO or Rally) available - returning null TrackerClient");
            return null;
        } catch (Exception e) {
            System.err.println("❌ [ServerManagedIntegrationsModule] Failed to create TrackerClient instance with resolved credentials: " + e.getMessage());
            return null;
        }
    }

    private TrackerClient<? extends ITicket> createXrayClient(JSONObject resolvedIntegrations) throws IOException {
        // X-ray requires both Jira and X-ray configuration
        if (!resolvedIntegrations.has("jira")) {
            System.err.println("❌ [ServerManagedIntegrationsModule] X-ray configuration requires Jira configuration");
            return null;
        }
        if (!resolvedIntegrations.has("xray")) {
            System.err.println("❌ [ServerManagedIntegrationsModule] X-ray configuration not found in resolved integrations");
            return null;
        }

        JSONObject jiraConfig = resolvedIntegrations.getJSONObject("jira");
        JSONObject xrayConfig = resolvedIntegrations.getJSONObject("xray");

        // Get Jira configuration
        String jiraBasePath = jiraConfig.optString("JIRA_BASE_PATH", "");
        String jiraAuthType = jiraConfig.optString("JIRA_AUTH_TYPE", "Basic");
        String jiraExtraFieldsProject = jiraConfig.optString("JIRA_EXTRA_FIELDS_PROJECT", "");
        String jiraExtraFields = jiraConfig.optString("JIRA_EXTRA_FIELDS", "");
        int maxSearchResults = jiraConfig.optInt("JIRA_MAX_SEARCH_RESULTS", -1);

        // Handle Jira authentication
        String jiraToken = "";
        if (jiraConfig.has("JIRA_EMAIL") && jiraConfig.has("JIRA_API_TOKEN")) {
            String email = jiraConfig.optString("JIRA_EMAIL", "");
            String apiToken = jiraConfig.optString("JIRA_API_TOKEN", "");
            if (!email.isEmpty() && !apiToken.isEmpty()) {
                String credentials = email.trim() + ":" + apiToken.trim();
                jiraToken = Base64.getEncoder().encodeToString(credentials.getBytes());
                System.out.println("✅ [ServerManagedIntegrationsModule] Combined JIRA_EMAIL + JIRA_API_TOKEN for authentication");
            }
        } else if (jiraConfig.has("JIRA_LOGIN_PASS_TOKEN")) {
            jiraToken = jiraConfig.optString("JIRA_LOGIN_PASS_TOKEN", "");
        }

        // Get X-ray configuration
        String xrayBasePath = xrayConfig.optString("XRAY_BASE_PATH", "");
        String xrayClientId = xrayConfig.optString("XRAY_CLIENT_ID", "");
        String xrayClientSecret = xrayConfig.optString("XRAY_CLIENT_SECRET", "");

        if (!jiraBasePath.isEmpty() && !jiraToken.isEmpty() && 
            !xrayBasePath.isEmpty() && !xrayClientId.isEmpty() && !xrayClientSecret.isEmpty()) {
            System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedXrayClient with resolved credentials");
            return new CustomServerManagedXrayClient(jiraBasePath, jiraToken, jiraAuthType, 
                    jiraExtraFieldsProject, jiraExtraFields, maxSearchResults,
                    xrayBasePath, xrayClientId, xrayClientSecret);
        } else {
            System.err.println("❌ [ServerManagedIntegrationsModule] X-ray configuration missing required parameters");
            return null;
        }
    }

    private TrackerClient<? extends ITicket> createJiraClient(JSONObject resolvedIntegrations) throws IOException {
        if (resolvedIntegrations.has("jira")) {
            JSONObject jiraConfig = resolvedIntegrations.getJSONObject("jira");
            String basePath = jiraConfig.optString("JIRA_BASE_PATH", "");
            String authType = jiraConfig.optString("JIRA_AUTH_TYPE", "Basic");
            String extraFields = jiraConfig.optString("JIRA_EXTRA_FIELDS_PROJECT", "");
            int maxSearchResults = jiraConfig.optInt("JIRA_MAX_SEARCH_RESULTS", -1);
            
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
                System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedJiraClient with resolved credentials (maxSearchResults=" + maxSearchResults + ")");
                return new CustomServerManagedJiraClient(basePath, token, authType, extraFields, maxSearchResults);
            } else {
                System.err.println("❌ [ServerManagedIntegrationsModule] Jira configuration missing required parameters (JIRA_BASE_PATH=" + 
                    (basePath.isEmpty() ? "empty" : basePath) + ", token=" + (token.isEmpty() ? "empty" : "[SENSITIVE]") + ")");
            }
        }
        return null;
    }

    private TrackerClient<? extends ITicket> createAdoClient(JSONObject resolvedIntegrations) throws IOException {
        if (resolvedIntegrations.has("ado")) {
            JSONObject adoConfig = resolvedIntegrations.getJSONObject("ado");
            String organization = adoConfig.optString("ADO_ORGANIZATION", "");
            String project = adoConfig.optString("ADO_PROJECT", "");
            String patToken = adoConfig.optString("ADO_PAT_TOKEN", "");
            
            if (!organization.isEmpty() && !project.isEmpty() && !patToken.isEmpty()) {
                System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedAzureDevOpsClient with resolved credentials");
                return new CustomServerManagedAzureDevOpsClient(organization, project, patToken);
            } else {
                System.err.println("❌ [ServerManagedIntegrationsModule] ADO configuration missing required parameters (ADO_ORGANIZATION=" + 
                    (organization.isEmpty() ? "empty" : organization) + ", ADO_PROJECT=" + 
                    (project.isEmpty() ? "empty" : project) + ", ADO_PAT_TOKEN=" + 
                    (patToken.isEmpty() ? "empty" : "[SENSITIVE]") + ")");
            }
        }
        return null;
    }

    private TrackerClient<? extends ITicket> createRallyClient(JSONObject resolvedIntegrations) throws IOException {
        if (resolvedIntegrations.has("rally")) {
            JSONObject rallyConfig = resolvedIntegrations.getJSONObject("rally");
            String basePath = rallyConfig.optString("RALLY_PATH", "https://rally1.rallydev.com");
            String token = rallyConfig.optString("RALLY_TOKEN", "");

            if (!token.isEmpty()) {
                System.out.println("✅ [ServerManagedIntegrationsModule] Creating CustomServerManagedRallyClient with resolved credentials");
                return new CustomServerManagedRallyClient(basePath, token);
            } else {
                System.err.println("❌ [ServerManagedIntegrationsModule] Rally configuration missing required parameters (token=" +
                        (token.isEmpty() ? "empty" : "[SENSITIVE]") + ")");
            }
        }
        return null;
    }
    
    /**
     * Custom JiraClient implementation that replicates BasicJiraClient functionality
     * but uses resolved credentials instead of static properties from PropertyReader
     */
    private static class CustomServerManagedJiraClient extends JiraClient<Ticket> {
        private final String[] defaultJiraFields;
        private final String[] extendedJiraFields;
        
        public CustomServerManagedJiraClient(String basePath, String token, String authType, String extraFields, int maxSearchResults) throws IOException {
            // Call parent constructor with resolved credentials and maxSearchResults
            super(basePath, token, maxSearchResults);
            
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
            System.out.println("✅ [CustomServerManagedJiraClient] Max search results configured: " + 
                (maxSearchResults == -1 ? "unlimited" : String.valueOf(maxSearchResults)));
            
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
        public String getTextFieldsOnly(ITicket ticket) {
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

    /**
     * Custom XrayClient implementation that uses resolved credentials
     * instead of static properties from PropertyReader
     */
    private static class CustomServerManagedXrayClient extends XrayClient {

        public CustomServerManagedXrayClient(
                String jiraBasePath,
                String jiraToken,
                String jiraAuthType,
                String extraFieldsProject,
                String extraFields,
                int maxSearchResults,
                String xrayBasePath,
                String xrayClientId,
                String xrayClientSecret
        ) throws IOException {
            // Use protected constructor with resolved credentials and reasonable defaults
            // Following the same pattern as CustomServerManagedJiraClient
            super(jiraBasePath, jiraToken, jiraAuthType, maxSearchResults,
                    xrayBasePath, xrayClientId, xrayClientSecret,
                    true, // isLoggingEnabled - default to true
                    true, // isClearCache - default to true to clean cache
                    true, // isWaitBeforePerform - default to true
                    100L, // sleepTimeRequest - default to 100ms
                    extraFieldsProject, // extraFieldsProject
                    extraFields != null && !extraFields.trim().isEmpty() ? extraFields.split(",") : null // extraFields
            );

            // Performance optimization: Enable caching (following CustomServerManagedJiraClient pattern)
            setCacheGetRequestsEnabled(true);
            System.out.println("✅ [CustomServerManagedXrayClient] Cache cleaned and enabled for performance optimization");
            System.out.println("✅ [CustomServerManagedXrayClient] Max search results configured: " +
                    (maxSearchResults == -1 ? "unlimited" : String.valueOf(maxSearchResults)));
        }
    }
    
    /**
     * Custom AzureDevOpsClient implementation that uses resolved credentials
     * instead of static properties from PropertyReader
     */
    private static class CustomServerManagedAzureDevOpsClient extends AzureDevOpsClient {
        
        public CustomServerManagedAzureDevOpsClient(String organization, String project, String patToken) throws IOException {
            // Note: AzureDevOpsClient constructor only takes organization, project, patToken
            // basePath is hardcoded to https://dev.azure.com/{organization} in the constructor
            super(organization, project, patToken);
            
            // Replicate BasicAzureDevOpsClient configuration with reasonable defaults
            setLogEnabled(true);
            
            // Performance optimization: Clean cache folder first, then enable caching
            setClearCache(true);  // This cleans the cache folder
            setCacheGetRequestsEnabled(true);  // Enable caching for better performance
            System.out.println("✅ [CustomServerManagedAzureDevOpsClient] Cache cleaned and enabled for performance optimization");
        }
        
        @Override
        public TrackerClient.TextType getTextType() {
            return TrackerClient.TextType.HTML;
        }
    }

    /**
     * Custom RallyClient implementation that uses resolved credentials
     * instead of static properties from PropertyReader
     */
    private static class CustomServerManagedRallyClient extends RallyClient {

        public CustomServerManagedRallyClient(String basePath, String token) throws IOException {
            super(basePath, token);
            setLogEnabled(true);
            setClearCache(true);
            setCacheGetRequestsEnabled(true);
            System.out.println("✅ [CustomServerManagedRallyClient] Cache cleaned and enabled for performance optimization");
        }

        @Override
        public TrackerClient.TextType getTextType() {
            return TrackerClient.TextType.HTML;
        }

        @Override
        public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {

        }

        @Override
        public String[] getDefaultQueryFields() {
            return RallyFields.DEFAULT;
        }

        @Override
        public String getTextFieldsOnly(ITicket ticket) {
            try {
                return ticket.getTicketTitle() + "\n" + ticket.getTicketDescription();
            } catch (IOException e) {
                return "";
            }
        }

        @Override
        public List<? extends ITicket> getTestCases(ITicket ticket) throws IOException {
            return Collections.emptyList();
        }

        @Override
        public String buildUrlToSearch(String query) {
            return getBasePath() + "/#/?keywords=" + query;
        }

        @Override
        public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
            throw new UnsupportedOperationException("Creation not supported in server managed mode yet");
        }

        @Override
        public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
            throw new UnsupportedOperationException("Update not supported in server managed mode yet");
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
                        token = Base64.getEncoder().encodeToString(credentials.getBytes());
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
            
            // First check for Ollama
            if (resolvedIntegrations.has("ollama")) {
                JSONObject ollamaConfig = resolvedIntegrations.getJSONObject("ollama");
                System.out.println("🔍 [ServerManagedIntegrationsModule] Found Ollama configuration: " + ollamaConfig.length() + " parameters");
                
                String basePath = ollamaConfig.optString("OLLAMA_BASE_PATH", "http://localhost:11434");
                String model = ollamaConfig.optString("OLLAMA_MODEL", null);
                int numCtx = ollamaConfig.optInt("OLLAMA_NUM_CTX", 16384);
                int numPredict = ollamaConfig.optInt("OLLAMA_NUM_PREDICT", -1);
                
                // Parse custom headers
                Map<String, String> customHeaders = parseCustomHeaders(
                    ollamaConfig.optString("OLLAMA_CUSTOM_HEADER_NAMES", null),
                    ollamaConfig.optString("OLLAMA_CUSTOM_HEADER_VALUES", null)
                );
                
                if (model != null && !model.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating OllamaAIClient with resolved credentials");
                    return new OllamaAIClient(basePath, model, numCtx, numPredict, observer, customHeaders);
                } else {
                    System.out.println("⚠️ [ServerManagedIntegrationsModule] Ollama configuration missing OLLAMA_MODEL, skipping");
                }
            }
            
            // Then check for Anthropic
            if (resolvedIntegrations.has("anthropic")) {
                JSONObject anthropicConfig = resolvedIntegrations.getJSONObject("anthropic");
                System.out.println("🔍 [ServerManagedIntegrationsModule] Found Anthropic configuration: " + anthropicConfig.length() + " parameters");
                
                String basePath = anthropicConfig.optString("ANTHROPIC_BASE_PATH", "https://api.anthropic.com/v1/messages");
                String model = anthropicConfig.optString("ANTHROPIC_MODEL", null);
                int maxTokens = anthropicConfig.optInt("ANTHROPIC_MAX_TOKENS", 4096);
                
                // Parse custom headers
                Map<String, String> customHeaders = parseCustomHeaders(
                    anthropicConfig.optString("ANTHROPIC_CUSTOM_HEADER_NAMES", null),
                    anthropicConfig.optString("ANTHROPIC_CUSTOM_HEADER_VALUES", null)
                );
                
                if (model != null && !model.isEmpty()) {
                    System.out.println("✅ [ServerManagedIntegrationsModule] Creating AnthropicAIClient with resolved credentials");
                    return new AnthropicAIClient(basePath, model, maxTokens, observer, customHeaders);
                } else {
                    System.out.println("⚠️ [ServerManagedIntegrationsModule] Anthropic configuration missing ANTHROPIC_MODEL, skipping");
                }
            }
            
            // Then check for Gemini
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
            
            String basePath = figmaConfig.optString("FIGMA_BASE_PATH", "https://api.figma.com");
            String token = figmaConfig.optString("FIGMA_TOKEN", null);
            
            if (token == null) {
                System.err.println("❌ [ServerManagedIntegrationsModule] Figma configuration missing required token parameter");
                return null;
            }
            
            // Normalize base path using FigmaClient's static method
            basePath = FigmaClient.normalizeBasePath(basePath);
            
            System.out.println("✅ [ServerManagedIntegrationsModule] Creating FigmaClient with normalized basePath=" + basePath);
            
            FigmaClient figmaClient = new FigmaClient(basePath, token);
            
            // Clear cache to ensure fresh data for server-managed execution
            figmaClient.setClearCache(true);
            System.out.println("✅ [ServerManagedIntegrationsModule] FigmaClient configured with cache clearing enabled for fresh data");
            
            return figmaClient;
            
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
     * Parses comma-separated header names and values into a Map.
     * If names and values count don't match, logs a warning and returns null.
     * 
     * @param headerNames Comma-separated header names
     * @param headerValues Comma-separated header values (must match names by index)
     * @return Map of header names to values, or null if parsing fails
     */
    private Map<String, String> parseCustomHeaders(String headerNames, String headerValues) {
        if (headerNames == null || headerNames.trim().isEmpty() || 
            headerValues == null || headerValues.trim().isEmpty()) {
            return null;
        }
        
        String[] names = headerNames.split(",");
        String[] values = headerValues.split(",");
        
        // Trim whitespace from all elements
        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].trim();
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }
        
        if (names.length != values.length) {
            System.err.println("⚠️ [ServerManagedIntegrationsModule] Custom header names and values count mismatch. " +
                "Names: " + names.length + ", Values: " + values.length + ". Skipping custom headers.");
            return null;
        }
        
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (!names[i].isEmpty() && !values[i].isEmpty()) {
                headers.put(names[i], values[i]);
            }
        }
        
        return headers.isEmpty() ? null : headers;
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