// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.PropertyReaderConfiguration;
import com.github.istin.dmtools.di.AIComponentsModule;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.xray.XrayClient;
import com.github.istin.dmtools.cli.CliCommandExecutor;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import com.github.istin.dmtools.di.DaggerMermaidIndexComponent;
import com.github.istin.dmtools.di.MermaidIndexComponent;
import com.github.istin.dmtools.figma.BasicFigmaClient;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.gitlab.BasicGitLab;
import com.github.istin.dmtools.file.FileTools;
import com.github.istin.dmtools.microsoft.teams.BasicTeamsClient;
import com.github.istin.dmtools.microsoft.teams.TeamsAuthTools;
import com.github.istin.dmtools.microsoft.sharepoint.BasicSharePointClient;
import com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import com.github.istin.dmtools.mcp.MCPToolDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Handles MCP CLI commands for standalone execution.
 * Supports two modes:
 * 1. mcp list - Returns JSON of available MCP tools
 * 2. mcp <tool_name> [args] - Executes specific MCP tool and returns results
 */
public class McpCliHandler {

    private static final Logger logger = LogManager.getLogger(McpCliHandler.class);

    private volatile Map<String, Object> clientInstances;
    private volatile Map<String, AI> availableAIClients;
    private volatile Map<String, Object> cachedToolsResponse;

    /** Active output formatter – resolved per {@link #processMcpCommand(String[])} call. */
    private CliOutputFormatter formatter = CliOutputFormatterFactory.create();

    public McpCliHandler() {
        // Configure logging for CLI usage - suppress all logs except errors
        configureCLILogging();
        // Don't initialize clients in constructor - use lazy initialization
        // This significantly improves startup time for list commands
    }

    /**
     * Ensure client instances are initialized (lazy initialization).
     * Clients are only created when needed for tool execution.
     * AI clients are created separately on demand via getOrCreateAIClient().
     */
    private void ensureClientInstances() {
        if (clientInstances == null) {
            synchronized (this) {
                if (clientInstances == null) {
                    logger.debug("🚀 [PERFORMANCE] Lazy initialization of non-AI client instances");
                    long startTime = System.currentTimeMillis();
                    // Don't create AI clients here - they're created on demand
                    ensureAIClientsMap();
                    this.clientInstances = createClientInstances();
                    long duration = System.currentTimeMillis() - startTime;
                    logger.debug("✅ [PERFORMANCE] Non-AI client instances initialized in {}ms ({} clients)", duration, clientInstances.size());
                }
            }
        }
    }

    /**
     * Ensure AI clients map is initialized (creates empty map, not clients).
     */
    private void ensureAIClientsMap() {
        if (availableAIClients == null) {
            synchronized (this) {
                if (availableAIClients == null) {
                    this.availableAIClients = new HashMap<>();
                }
            }
        }
    }

    /**
     * Get or create a specific AI client by type (true lazy initialization).
     * Only creates the requested client, not all clients.
     *
     * @param clientType Type of AI client (openai, gemini, anthropic, etc.)
     * @return AI client instance or null if not available
     */
    private AI getOrCreateAIClient(String clientType) {
        ensureAIClientsMap();

        // Return cached client if already created
        if (availableAIClients.containsKey(clientType)) {
            return availableAIClients.get(clientType);
        }

        // Create client on demand
        synchronized (this) {
            // Double-check after acquiring lock
            if (availableAIClients.containsKey(clientType)) {
                return availableAIClients.get(clientType);
            }

            logger.debug("🔧 [LAZY] Creating AI client on demand: {}", clientType);
            long startTime = System.currentTimeMillis();

            ConversationObserver observer = new ConversationObserver();
            ApplicationConfiguration configuration = new PropertyReaderConfiguration();
            AI client = null;

            switch (clientType.toLowerCase()) {
                case "ollama":
                    client = AIComponentsModule.createOllamaAI(observer, configuration);
                    break;
                case "anthropic":
                    client = AIComponentsModule.createAnthropicAI(observer, configuration);
                    break;
                case "gemini":
                    client = AIComponentsModule.createGeminiAI(observer, configuration);
                    break;
                case "bedrock":
                    client = AIComponentsModule.createBedrockAI(observer, configuration);
                    break;
                case "openai":
                    client = AIComponentsModule.createOpenAIAI(observer, configuration);
                    break;
                case "dial":
                    client = AIComponentsModule.createDialAI(observer, configuration);
                    break;
                default:
                    logger.warn("Unknown AI client type: {}", clientType);
                    return null;
            }

            if (client != null) {
                availableAIClients.put(clientType, client);
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("✅ [LAZY] AI client '{}' created in {}ms", clientType, duration);
            } else {
                logger.warn("Failed to create AI client: {}", clientType);
            }

            return client;
        }
    }

    /**
     * Processes MCP CLI commands.
     * 
     * @param args Command line arguments starting with "mcp"
     * @return Command result as string
     */
    public String processMcpCommand(String[] args) {
        try {
            if (args.length < 2) {
                return createErrorResponse("Usage: mcp <command> [args...]\nCommands: list [filter], <tool_name>");
            }

            // Resolve output format from flags and strip them from the args passed downstream
            FormatExtractionResult extracted = extractFormatFlag(args);
            this.formatter = CliOutputFormatterFactory.create(extracted.formatFlag);
            String[] cleanedArgs = extracted.args;

            String command = cleanedArgs[1];

            if ("list".equals(command)) {
                String filter = cleanedArgs.length > 2 ? cleanedArgs[2] : null;
                return handleListCommand(filter);
            } else {
                // Check if --help or -h flag is present → show tool schema instead of executing
                for (int i = 2; i < cleanedArgs.length; i++) {
                    if ("--help".equals(cleanedArgs[i]) || "-h".equals(cleanedArgs[i])) {
                        return handleListCommand(command);
                    }
                }
                return handleToolExecutionCommand(cleanedArgs);
            }

        } catch (Exception e) {
            logger.error("Error processing MCP command", e);
            return createErrorResponse("Error: " + e.getMessage());
        }
    }

    /**
     * Scans {@code args} for output-format flags ({@code --output <format>}, {@code --toon},
     * {@code --mini}) and returns the detected format string plus a cleaned args array with
     * those flags removed.
     *
     * <p>Only positions ≥ 2 are inspected so that {@code args[0]} ("mcp") and
     * {@code args[1]} (command/tool name) are always preserved.</p>
     */
    private FormatExtractionResult extractFormatFlag(String[] args) {
        String formatFlag = null;
        java.util.List<String> cleaned = new java.util.ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (i >= 2) {
                if ("--toon".equals(arg)) {
                    formatFlag = "toon";
                    continue;
                }
                if ("--mini".equals(arg)) {
                    formatFlag = "mini";
                    continue;
                }
                if ("--output".equals(arg) && i + 1 < args.length) {
                    formatFlag = args[i + 1];
                    i++; // skip next token (the format value)
                    continue;
                }
                if (arg.startsWith("--output=")) {
                    formatFlag = arg.substring("--output=".length());
                    continue;
                }
            }
            cleaned.add(arg);
        }

        return new FormatExtractionResult(formatFlag, cleaned.toArray(new String[0]));
    }

    /** Carrier returned by {@link #extractFormatFlag(String[])}. */
    private static final class FormatExtractionResult {
        final String formatFlag;
        final String[] args;

        FormatExtractionResult(String formatFlag, String[] args) {
            this.formatFlag = formatFlag;
            this.args = args;
        }
    }

    /**
     * Handles 'mcp list' command - returns available tools as JSON.
     * 
     * @param filter Optional filter to show only tools containing this text (case-insensitive)
     */
    private String handleListCommand(String filter) {
        try {
            Set<String> integrationTypes = getAvailableIntegrations();
            Map<String, Object> toolsList = MCPSchemaGenerator.generateToolsListResponse(integrationTypes);
            
            // Apply filter if provided
            if (filter != null && !filter.trim().isEmpty()) {
                toolsList = filterToolsList(toolsList, filter.toLowerCase());
            }
            
            return formatter.formatList(toolsList);
        } catch (Exception e) {
            logger.error("Error generating tools list", e);
            return createErrorResponse("Failed to generate tools list: " + e.getMessage());
        }
    }

    /**
     * Handles tool execution commands - executes the specified tool and returns results.
     */
    private String handleToolExecutionCommand(String[] args) {
        String toolName = null;
        try {
            toolName = args[1];
            toolName = resolveToolAlias(toolName);
            Map<String, Object> arguments = parseToolArguments(args, toolName);

            logger.info("Executing MCP tool: {} with arguments: {}", toolName, arguments);

            // Lazy initialization: Ensure client instances are created before execution
            ensureClientInstances();

            // Set the appropriate AI client for this tool
            AI appropriateAIClient = getAIClientForTool(toolName);
            if (appropriateAIClient != null) {
                clientInstances.put("ai", appropriateAIClient);
            }

            Object result = MCPToolExecutor.executeTool(toolName, arguments, clientInstances);
            return formatter.formatResult(result);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid tool or arguments", e);
            // Check if this is a parameter format error
            if (e.getMessage() != null &&
                (e.getMessage().contains("must be an array") ||
                 e.getMessage().contains("cannot be converted") ||
                 e.getMessage().contains("incorrect type"))) {
                // Parameter format error - show tool schema
                return createParameterFormatError(toolName, e);
            }
            // Check if debug mode is enabled
            String debugFlag = System.getProperty("log4j2.configurationFile");
            boolean isDebugMode = (debugFlag != null && debugFlag.contains("debug"));

            if (isDebugMode) {
                return createErrorResponse("Invalid tool or arguments: " + e.getMessage() + "\n" + getStackTrace(e));
            } else {
                return createErrorResponse(e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error executing tool", e);
            // Check if debug mode is enabled
            String debugFlag = System.getProperty("log4j2.configurationFile");
            boolean isDebugMode = (debugFlag != null && debugFlag.contains("debug"));

            if (isDebugMode) {
                return createErrorResponse("Tool execution failed: " + e.getMessage() + "\n" + getStackTrace(e));
            } else {
                return createErrorResponse(e.getMessage());
            }
        }
    }
    
    /**
     * Parses tool arguments from command line.
     * Supports various formats:
     * - Simple positional arguments
     * - --data JSON_STRING
     * - --stdin-data JSON_STRING (for wrapper script)
     */
    private Map<String, Object> parseToolArguments(String[] args, String resolvedToolName) {
        Map<String, Object> arguments = new HashMap<>();
        List<String> positionalArgs = new ArrayList<>();
        String toolName = resolvedToolName;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];

            if (("--data".equals(arg) || "--stdin-data".equals(arg)) && i + 1 < args.length) {
                parseJsonIntoArguments(args[i + 1], arg.substring(2), arguments);
                i++; // Skip next argument as it was consumed
            } else if ("--verbose".equals(arg) || "--debug".equals(arg)) {
                // Shell-level flags handled by dmtools.sh; ignored here
            } else if (arg.startsWith("--")) {
                logger.debug("Ignoring unknown flag: {}", arg);
            } else {
                // Positional argument - collect for now
                // Only treat as key=value if it matches pattern: paramName=value (where paramName is valid identifier)
                if (arg.contains("=") && arg.matches("^[a-zA-Z_][a-zA-Z0-9_]*=.*")) {
                    String[] parts = arg.split("=", 2);
                    arguments.put(parts[0], parts[1]);
                } else {
                    positionalArgs.add(arg);
                }
            }
        }
        
        // Smartly map positional arguments to schema parameters
        if (!positionalArgs.isEmpty()) {
            mapPositionalArguments(toolName, positionalArgs, arguments);
        }

        return arguments;
    }

    /**
     * Parses a JSON string and merges its keys into the arguments map.
     */
    private void parseJsonIntoArguments(String jsonData, String source, Map<String, Object> arguments) {
        try {
            JSONObject jsonObj = new JSONObject(jsonData);
            jsonObj.keys().forEachRemaining(key -> arguments.put(key, jsonObj.get(key)));
        } catch (Exception e) {
            logger.warn("Failed to parse {} data, treating as string: {}", source, jsonData);
            arguments.put("data", jsonData);
        }
    }

    /**
     * Maps positional arguments to named parameters based on the tool's schema.
     * Uses parameter order from method declaration (via annotation processor).
     * Handles varargs/array parameters by collecting all remaining args into an array.
     */
    private void mapPositionalArguments(String toolName, List<String> positionalArgs, Map<String, Object> arguments) {
        try {
            // Get all parameter names in declaration order from generated schema
            List<String> paramNames = MCPSchemaGenerator.getAllParameterNames(toolName);
            
            if (paramNames.isEmpty()) {
                logger.warn("No parameters found for tool '{}'. Using indexed fallback.", toolName);
                // Fallback for when schema cannot be retrieved
                for (int i = 0; i < positionalArgs.size(); i++) {
                    arguments.put("arg" + i, positionalArgs.get(i));
                }
                return;
            }
            
            // Get tool schema to check parameter types
            Map<String, Object> toolSchema = getToolSchema(toolName);
            
            // Map positional args to parameter names in declaration order
            int numParams = paramNames.size();
            int numArgs = positionalArgs.size();
            
            for (int i = 0; i < numParams; i++) {
                String paramName = paramNames.get(i);
                boolean isArrayParam = isArrayParameter(toolSchema, paramName);
                boolean isLastParam = (i == numParams - 1);
                
                if (isArrayParam && isLastParam && i < numArgs) {
                    // Varargs/array parameter: collect all remaining positional args into an array
                    List<String> remainingArgs = positionalArgs.subList(i, numArgs);
                    String[] arrayValue = remainingArgs.toArray(new String[0]);
                    arguments.put(paramName, arrayValue);
                    logger.debug("Mapped {} positional args to array parameter '{}' for tool '{}'", 
                               remainingArgs.size(), paramName, toolName);
                    break; // All remaining args consumed
                } else if (i < numArgs) {
                    // Regular parameter: map single value
                    String paramValue = positionalArgs.get(i);
                    Object convertedValue = convertParameterValue(paramName, paramValue);
                    arguments.put(paramName, convertedValue);
                }
            }
            
            // If there are more positional args than parameters (and last param is not array), log warning
            if (numArgs > numParams) {
                String lastParamName = paramNames.get(numParams - 1);
                boolean isLastArray = isArrayParameter(toolSchema, lastParamName);
                if (!isLastArray) {
                    logger.warn("Tool '{}' has {} parameters but {} positional arguments provided. Extra arguments ignored.", 
                        toolName, numParams, numArgs);
                }
            }
        } catch (Exception e) {
            logger.warn("Error mapping parameters for tool '{}': {}. Using indexed fallback.", 
                toolName, e.getMessage(), e);
            // Fallback for any errors
            for (int i = 0; i < positionalArgs.size(); i++) {
                arguments.put("arg" + i, positionalArgs.get(i));
            }
        }
    }
    
    /**
     * Get tool schema from cache or generate it.
     */
    private Map<String, Object> getToolSchema(String toolName) {
        try {
            if (cachedToolsResponse == null) {
                synchronized (this) {
                    if (cachedToolsResponse == null) {
                        cachedToolsResponse = MCPSchemaGenerator.generateToolsListResponse(getAvailableIntegrations());
                    }
                }
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) cachedToolsResponse.get("tools");

            for (Map<String, Object> tool : tools) {
                if (toolName.equals(tool.get("name"))) {
                    return tool;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve schema for tool '{}': {}", toolName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if a parameter is expected to be an array based on tool schema.
     */
    private boolean isArrayParameter(Map<String, Object> toolSchema, String paramName) {
        if (toolSchema == null) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) toolSchema.get("inputSchema");
        if (inputSchema == null) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        if (properties == null) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paramSchema = (Map<String, Object>) properties.get(paramName);
        if (paramSchema == null) {
            return false;
        }
        
        String type = (String) paramSchema.get("type");
        return "array".equals(type);
    }

    /**
     * Converts parameter value from String to appropriate type based on parameter name.
     */
    private Object convertParameterValue(String paramName, String value) {
        // MCPToolExecutor handles type conversion based on schema; pass values as strings
        return value;
    }
    
    /**
     * Creates client instances for MCP tool execution.
     * Uses environment variables with Basic client fallback.
     */
    private Map<String, Object> createClientInstances() {
        Map<String, Object> clients = new HashMap<>();

        try {
            // Create Jira client
            clients.put("jira", BasicJiraClient.getInstance());
            logger.debug("Created BasicJiraClient instance");
        } catch (IOException e) {
            logger.warn("Failed to create BasicJiraClient: {}", e.getMessage());
        }

        try {
            // Create Xray client (extends JiraClient, provides X-ray specific functionality)
            TrackerClient<?> xrayClient = XrayClient.getInstance();
            if (xrayClient != null) {
                clients.put("jira_xray", xrayClient);
                logger.debug("Created XrayClient instance");
            }
        } catch (IOException e) {
            logger.debug("XrayClient not initialized: {}. X-ray tools will not be available.", e.getMessage());
        }

        try {
            // Create Confluence client
            clients.put("confluence", BasicConfluence.getInstance());
            logger.debug("Created BasicConfluence instance");
        } catch (IOException e) {
            logger.warn("Failed to create BasicConfluence: {}", e.getMessage());
        }

        try {
            // Create GitHub client
            clients.put("github", BasicGithub.getInstance());
            logger.debug("Created BasicGithub instance");
        } catch (IOException e) {
            logger.warn("Failed to create BasicGithub: {}", e.getMessage());
        }

        try {
            // Create GitLab client
            clients.put("gitlab", BasicGitLab.getInstance());
            logger.debug("Created BasicGitLab instance");
        } catch (IOException e) {
            logger.warn("Failed to create BasicGitLab: {}", e.getMessage());
        }

        try {
            // Create Figma client
            clients.put("figma", BasicFigmaClient.getInstance());
            logger.debug("Created BasicFigmaClient instance");
        } catch (Exception e) {
            logger.warn("Failed to create BasicFigmaClient: {}", e.getMessage());
        }

        // AI clients are now created on demand via getOrCreateAIClient()
        // No need to create default AI client here - it will be created when needed
        // See getAIClientForTool() and handleToolExecutionCommand()
        logger.debug("AI clients will be created on demand when tools are executed");

        try {
            // Create CLI executor
            clients.put("cli", new CliCommandExecutor());
            logger.debug("Created CliCommandExecutor instance");
        } catch (Exception e) {
            logger.warn("Failed to create CliCommandExecutor: {}", e.getMessage());
        }

        try {
            // Create File tools for reading files from working directory
            clients.put("file", new FileTools());
            logger.debug("Created FileTools instance");
        } catch (Exception e) {
            logger.warn("Failed to create FileTools: {}", e.getMessage());
        }

        try {
            // Create Teams Auth tools (separate from main client, always available)
            clients.put("teams_auth", new TeamsAuthTools());
            logger.debug("Created TeamsAuthTools instance");
        } catch (Exception e) {
            logger.warn("Failed to create TeamsAuthTools: {}", e.getMessage());
        }

        try {
            // Create Teams client (only if authentication is configured)
            clients.put("teams", BasicTeamsClient.getInstance());
            logger.debug("Created BasicTeamsClient instance");
        } catch (Exception e) {
            logger.debug("BasicTeamsClient not initialized: {}. Use teams_auth_start to authenticate.", e.getMessage());
        }

        try {
            // Create SharePoint client (uses same auth as Teams)
            clients.put("sharepoint", BasicSharePointClient.getInstance());
            logger.debug("Created BasicSharePointClient instance");
        } catch (Exception e) {
            logger.debug("BasicSharePointClient not initialized: {}. SharePoint uses same auth as Teams.", e.getMessage());
        }

        try {
            // Create TestRail client
            clients.put("testrail", com.github.istin.dmtools.testrail.TestRailClient.getInstance());
            logger.debug("Created TestRailClient instance");
        } catch (Exception e) {
            logger.debug("TestRailClient not initialized: {}. Configure TESTRAIL_BASE_PATH, TESTRAIL_USERNAME, and TESTRAIL_API_KEY.", e.getMessage());
        }

        try {
            // Create KB Tools using Dagger
            KnowledgeBaseComponent kbComponent = DaggerKnowledgeBaseComponent.create();
            clients.put("kb", kbComponent.kbTools());
            logger.debug("Created KBTools instance");
        } catch (Exception e) {
            logger.warn("Failed to create KBTools: {}", e.getMessage());
        }

        try {
            // Create Mermaid Index Tools using Dagger
            MermaidIndexComponent mermaidComponent = DaggerMermaidIndexComponent.create();
            clients.put("mermaid", mermaidComponent.mermaidIndexTools());
            logger.debug("Created MermaidIndexTools instance");
        } catch (Exception e) {
            logger.warn("Failed to create MermaidIndexTools: {}", e.getMessage());
        }

        logger.info("Created {} client instances for MCP CLI", clients.size());
        return clients;
    }
    
    /**
     * Resolves an abstract tool alias to the canonical tool name for the configured default integration.
     *
     * <p>If the given name is already a known tool, it is returned unchanged.
     * If it matches an alias shared by multiple integrations (e.g. "source_code_list_prs"),
     * the correct concrete tool is chosen via:
     * <ul>
     *   <li>{@code DEFAULT_SOURCE_CODE} env var for {@code source_code_*} aliases</li>
     *   <li>{@code DEFAULT_TRACKER} env var for {@code tracker_*} aliases</li>
     * </ul>
     *
     * @param toolName the raw tool name (may be an alias)
     * @return the resolved canonical tool name, or the original if no alias matched
     */
    String resolveToolAlias(String toolName) {
        if (toolName == null) {
            return null;
        }
        // Already a direct tool — no resolution needed
        if (MCPToolRegistry.hasTool(toolName)) {
            return toolName;
        }
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias(toolName);
        if (candidates == null || candidates.isEmpty()) {
            return toolName;
        }
        if (candidates.size() == 1) {
            return candidates.get(0).getName();
        }
        // Multiple implementations — pick by configured default integration
        String defaultIntegration = resolveDefaultIntegrationForAlias(toolName);
        if (defaultIntegration != null) {
            MCPToolDefinition matched = MCPToolRegistry.getToolByAliasAndIntegration(toolName, defaultIntegration);
            if (matched != null) {
                logger.info("Resolved alias '{}' -> '{}' via DEFAULT integration '{}'",
                        toolName, matched.getName(), defaultIntegration);
                return matched.getName();
            }
        }
        // Fallback: first candidate
        String resolved = candidates.get(0).getName();
        logger.warn("No default integration configured for alias '{}'. Falling back to first candidate: '{}'",
                toolName, resolved);
        return resolved;
    }

    private String resolveDefaultIntegrationForAlias(String alias) {
        if (alias.startsWith("source_code_")) {
            String val = System.getenv("DEFAULT_SOURCE_CODE");
            return val != null ? val.trim().toLowerCase() : null;
        }
        if (alias.startsWith("tracker_")) {
            String val = System.getenv("DEFAULT_TRACKER");
            return val != null ? val.trim().toLowerCase() : null;
        }
        return null;
    }

    /**
     * Gets the appropriate AI client for a given tool name.
     * Returns the client that matches the tool's expected type.
     */
    private AI getAIClientForTool(String toolName) {
        if (toolName == null) {
            return null;
        }

        String toolLower = toolName.toLowerCase();

        // Special case: vertex_ai_gemini tools should use gemini client
        // which will automatically select Vertex AI or API key mode based on config
        if (toolLower.startsWith("vertex_ai_gemini_")) {
            AI client = getOrCreateAIClient("gemini");
            if (client == null) {
                String errorMsg = String.format(
                    "Tool '%s' requires Gemini provider (Vertex AI or API key), but it's not configured. " +
                    "For Vertex AI: set GEMINI_VERTEX_ENABLED=true and credentials. " +
                    "For API key: set GEMINI_API_KEY.",
                    toolName
                );
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            return client;
        }

        String[] parts = toolLower.split("_");

        // Provider-specific AI tools (e.g., bedrock_ai_chat, openai_ai_chat)
        Set<String> knownProviders = new HashSet<>(Arrays.asList(
            "bedrock", "openai", "gemini", "anthropic", "ollama", "dial"
        ));

        if (parts.length > 0) {
            String providerName = parts[0];

            // Check if this is a provider-specific tool
            if (knownProviders.contains(providerName)) {
                AI client = getOrCreateAIClient(providerName);
                if (client == null) {
                    // Provider-specific tool but provider not configured
                    String errorMsg = String.format(
                        "Tool '%s' requires %s provider, but it's not configured. " +
                        "Please check your credentials in dmtools.env",
                        toolName, providerName.toUpperCase()
                    );
                    logger.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
                return client;
            }
        }

        // Non-AI tools don't need an AI client
        Set<String> nonAIIntegrations = Set.of(
            "jira", "confluence", "ado", "figma", "file", "cli",
            "teams", "sharepoint", "testrail", "kb", "mermaid", "github",
            "gitlab", "source", "tracker"
        );
        if (parts.length > 0 && nonAIIntegrations.contains(parts[0])) {
            return null;
        }

        // Generic AI tools - use fallback logic
        // Fallback: try to get or create default client (openai)
        AI defaultClient = getOrCreateAIClient("openai");
        if (defaultClient != null) {
            return defaultClient;
        }

        // Last resort: try other providers
        for (String providerType : new String[]{"gemini", "anthropic", "ollama", "bedrock", "dial"}) {
            AI client = getOrCreateAIClient(providerType);
            if (client != null) {
                return client;
            }
        }

        return null;
    }

    /**
     * Gets available integrations based on configuration, WITHOUT creating clients.
     * This is important for fast 'list' command execution.
     */
    private Set<String> getAvailableIntegrations() {
        Set<String> integrations = new HashSet<>();

        // Check environment variable first
        String envIntegrations = System.getenv("DMTOOLS_INTEGRATIONS");
        if (envIntegrations != null && !envIntegrations.trim().isEmpty()) {
            String[] parts = envIntegrations.split(",");
            for (String part : parts) {
                integrations.add(part.trim());
            }
        }

        // If no environment variable, return all possible integrations
        // Do NOT create clients just to check - this is for listing only
        if (integrations.isEmpty()) {
            // Return all known integration types without creating clients
            integrations.addAll(Arrays.asList(
                "jira", "jira_xray", "ado", "confluence", "figma",
                "teams", "teams_auth", "sharepoint", "testrail", "ai", "cli",
                "file", "kb", "mermaid", "github", "gitlab"
            ));
        }
        logger.debug("Available integrations: {}", integrations);
        return integrations;
    }

    /**
     * Creates a standardized error response using the active output formatter.
     */
    private String createErrorResponse(String message) {
        return formatter.formatError(message);
    }

    /**
     * Converts exception stack trace to string for error reporting.
     */
    private String getStackTrace(Throwable e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Stack trace:\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
            // Limit stack trace to first 10 elements to avoid overwhelming output
            if (sb.length() > 2000) {
                sb.append("  ... (truncated)\n");
                break;
            }
        }
        if (e.getCause() != null) {
            sb.append("Caused by: ").append(e.getCause().getClass().getName())
              .append(": ").append(e.getCause().getMessage()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Creates a detailed error response for parameter format errors.
     * Shows the tool schema and correct usage format.
     */
    private String createParameterFormatError(String toolName, IllegalArgumentException e) {
        StringBuilder errorMsg = new StringBuilder();

        // If toolName is null, just return the error
        if (toolName == null) {
            return createErrorResponse(e.getMessage());
        }

        // Extract parameter name from error message
        String errorParamName = null;
        String errMsg = e.getMessage();
        if (errMsg != null && errMsg.contains("Parameter '")) {
            int start = errMsg.indexOf("Parameter '") + 11;
            int end = errMsg.indexOf("'", start);
            if (end > start) {
                errorParamName = errMsg.substring(start, end);
            }
        }

        // Get tool schema to show correct format
        Map<String, Object> toolSchema = getToolSchema(toolName);
        if (toolSchema != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) toolSchema.get("inputSchema");
            if (inputSchema != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
                @SuppressWarnings("unchecked")
                List<String> required = (List<String>) inputSchema.get("required");

                if (properties != null && !properties.isEmpty()) {
                    // Show usage example
                    errorMsg.append("Correct usage:\n\n");
                    errorMsg.append("dmtools ").append(toolName).append(" <<EOF\n");
                    errorMsg.append(generateUsageExample(toolName, properties, required, errorParamName));
                    errorMsg.append("\nEOF\n");
                }
            }
        }

        return createErrorResponse(e.getMessage() + "\n\n" + errorMsg.toString());
    }

    /**
     * Generates a usage example JSON for a tool based on its schema.
     */
    private String generateUsageExample(String toolName, Map<String, Object> properties, List<String> required, String errorParamName) {
        JSONObject example = new JSONObject();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> paramSchema = (Map<String, Object>) entry.getValue();
            String type = (String) paramSchema.get("type");
            String exampleValue = (String) paramSchema.get("example");

            // Show required parameters AND the parameter that caused the error
            boolean isRequired = (required != null && required.contains(paramName));
            boolean isErrorParam = (errorParamName != null && errorParamName.equals(paramName));

            if (isRequired || isErrorParam) {
                if (exampleValue != null && !exampleValue.isEmpty()) {
                    if ("array".equals(type)) {
                        // Parse example as array if it looks like one
                        if (exampleValue.startsWith("[")) {
                            try {
                                example.put(paramName, new JSONArray(exampleValue));
                            } catch (Exception e) {
                                // Fallback to simple array
                                JSONArray arr = new JSONArray();
                                arr.put(exampleValue);
                                example.put(paramName, arr);
                            }
                        } else {
                            // Create array from comma-separated values
                            JSONArray arr = new JSONArray();
                            for (String val : exampleValue.split(",")) {
                                arr.put(val.trim());
                            }
                            example.put(paramName, arr);
                        }
                    } else if ("object".equals(type)) {
                        try {
                            example.put(paramName, new JSONObject(exampleValue));
                        } catch (Exception e) {
                            example.put(paramName, exampleValue);
                        }
                    } else {
                        example.put(paramName, exampleValue);
                    }
                } else {
                    // Generate default example based on type
                    if ("array".equals(type)) {
                        example.put(paramName, new JSONArray().put("value1").put("value2"));
                    } else if ("object".equals(type)) {
                        example.put(paramName, new JSONObject().put("key", "value"));
                    } else if ("number".equals(type) || "integer".equals(type)) {
                        example.put(paramName, 10);
                    } else if ("boolean".equals(type)) {
                        example.put(paramName, true);
                    } else {
                        example.put(paramName, "example_" + paramName);
                    }
                }
            }
        }

        return example.toString(2);
    }

    /**
     * Configures logging for CLI usage - suppresses debug/info logs.
     * Skips configuration if debug mode is enabled (log4j2-debug.xml).
     */
    private void configureCLILogging() {
        try {
            // Check if we're in debug mode (--debug flag)
            String configFile = System.getProperty("log4j2.configurationFile");
            if (configFile != null && configFile.contains("debug")) {
                // Debug mode enabled - don't override log configuration
                logger.error("[DEBUG] Debug mode enabled, preserving log configuration");
                return;
            }
            
            // Set all loggers to OFF level to completely suppress output
            Configurator.setAllLevels("com.github.istin.dmtools", org.apache.logging.log4j.Level.OFF);
            Configurator.setAllLevels("org.apache", org.apache.logging.log4j.Level.OFF);
            Configurator.setAllLevels("okhttp3", org.apache.logging.log4j.Level.OFF);
            Configurator.setAllLevels("com.github.istin", org.apache.logging.log4j.Level.OFF);
            Configurator.setAllLevels("", org.apache.logging.log4j.Level.OFF); // Root logger
            Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
            
            // Also try to set specific problematic loggers
            Configurator.setLevel("com.github.istin.dmtools.atlassian.jira.JiraClient", org.apache.logging.log4j.Level.OFF);
            Configurator.setLevel("com.github.istin.dmtools.networking.AbstractRestClient", org.apache.logging.log4j.Level.OFF);
            Configurator.setLevel("com.github.istin.dmtools.mcp.cli.McpCliHandler", org.apache.logging.log4j.Level.OFF);
        } catch (Exception e) {
            // Ignore logging configuration errors
        }
    }

    /**
     * Filters the tools list to only include tools whose names contain the filter text.
     * 
     * @param toolsList Original tools list from MCPSchemaGenerator
     * @param filter Filter text (already lowercase)
     * @return Filtered tools list
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> filterToolsList(Map<String, Object> toolsList, String filter) {
        Map<String, Object> filteredList = new HashMap<>(toolsList);
        
        if (toolsList.containsKey("tools") && toolsList.get("tools") instanceof List) {
            List<Object> tools = (List<Object>) toolsList.get("tools");
            List<Object> filteredTools = new ArrayList<>();
            
            for (Object tool : tools) {
                if (tool instanceof Map) {
                    Map<String, Object> toolMap = (Map<String, Object>) tool;
                    Object nameObj = toolMap.get("name");
                    Object descObj = toolMap.get("description");
                    if (nameObj instanceof String) {
                        String toolName = ((String) nameObj).toLowerCase();
                        String toolDesc = descObj instanceof String ? ((String) descObj).toLowerCase() : "";
                        if (toolName.contains(filter) || toolDesc.contains(filter)) {
                            filteredTools.add(tool);
                        }
                    }
                }
            }
            
            filteredList.put("tools", filteredTools);
        }
        
        return filteredList;
    }

    /**
     * Gets the client instances map (for testing).
     * Ensures clients are initialized before returning.
     */
    public Map<String, Object> getClientInstances() {
        ensureClientInstances();
        return Collections.unmodifiableMap(clientInstances);
    }
}
