package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.cli.CliCommandExecutor;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import com.github.istin.dmtools.figma.BasicFigmaClient;
import com.github.istin.dmtools.file.FileTools;
import com.github.istin.dmtools.microsoft.teams.BasicTeamsClient;
import com.github.istin.dmtools.microsoft.teams.TeamsAuthTools;
import com.github.istin.dmtools.microsoft.sharepoint.BasicSharePointClient;
import com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
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

    private final Map<String, Object> clientInstances;

    public McpCliHandler() {
        // Configure logging for CLI usage - suppress all logs except errors
        configureCLILogging();
        this.clientInstances = createClientInstances();
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

            String command = args[1];

            if ("list".equals(command)) {
                String filter = args.length > 2 ? args[2] : null;
                return handleListCommand(filter);
            } else {
                return handleToolExecutionCommand(args);
            }

        } catch (Exception e) {
            logger.error("Error processing MCP command", e);
            return createErrorResponse("Error: " + e.getMessage());
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
            
            return new JSONObject(toolsList).toString(2);
        } catch (Exception e) {
            logger.error("Error generating tools list", e);
            return createErrorResponse("Failed to generate tools list: " + e.getMessage());
        }
    }

    /**
     * Handles tool execution commands - executes the specified tool and returns results.
     */
    private String handleToolExecutionCommand(String[] args) {
        try {
            String toolName = args[1];
            Map<String, Object> arguments = parseToolArguments(args);

            logger.info("Executing MCP tool: {} with arguments: {}", toolName, arguments);

            Object result = MCPToolExecutor.executeTool(toolName, arguments, clientInstances);
            if (result == null) {
                return "Tool executed successfully but returned no result.";
            }

            return serializeResult(result);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid tool or arguments", e);
            return createErrorResponse("Invalid tool or arguments: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error executing tool", e);
            return createErrorResponse("Tool execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Serializes tool execution result to JSON string.
     * Handles various result types: JSONModel, List, primitives, etc.
     */
    private String serializeResult(Object result) {
        if (result == null) {
            return "null";
        }
        
        // Handle JSONModel objects
        if (result instanceof com.github.istin.dmtools.common.model.JSONModel) {
            return result.toString();
        }
        
        // Handle Lists (e.g., List<Chat>)
        if (result instanceof List) {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (Object item : (List<?>) result) {
                if (item instanceof com.github.istin.dmtools.common.model.JSONModel) {
                    jsonArray.put(new JSONObject(item.toString()));
                } else {
                    jsonArray.put(item);
                }
            }
            return jsonArray.toString(2); // Pretty print with 2-space indent
        }
        
        // Handle primitives and other types
        if (result instanceof String || result instanceof Number || result instanceof Boolean) {
            return result.toString();
        }
        
        // Try to convert to JSON object
        try {
            return new JSONObject(result.toString()).toString(2);
        } catch (Exception e) {
            // Fall back to plain toString
            return result.toString();
        }
    }

    /**
     * Parses tool arguments from command line.
     * Supports various formats:
     * - Simple positional arguments
     * - --data JSON_STRING
     * - --stdin-data JSON_STRING (for wrapper script)
     */
    private Map<String, Object> parseToolArguments(String[] args) {
        Map<String, Object> arguments = new HashMap<>();
        List<String> positionalArgs = new ArrayList<>();
        String toolName = args[1];

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];

            if ("--data".equals(arg) && i + 1 < args.length) {
                // Parse JSON data
                String jsonData = args[i + 1];
                try {
                    JSONObject jsonObj = new JSONObject(jsonData);
                    jsonObj.keys().forEachRemaining(key -> {
                        arguments.put(key, jsonObj.get(key));
                    });
                } catch (Exception e) {
                    logger.warn("Failed to parse JSON data, treating as string: {}", jsonData);
                    arguments.put("data", jsonData);
                }
                i++; // Skip next argument as it was consumed
            } else if ("--stdin-data".equals(arg) && i + 1 < args.length) {
                // Parse stdin data (from wrapper script)
                String stdinData = args[i + 1];
                try {
                    JSONObject jsonObj = new JSONObject(stdinData);
                    jsonObj.keys().forEachRemaining(key -> {
                        arguments.put(key, jsonObj.get(key));
                    });
                } catch (Exception e) {
                    logger.warn("Failed to parse stdin data, treating as string: {}", stdinData);
                    arguments.put("data", stdinData);
                }
                i++; // Skip next argument as it was consumed
            } else if (!arg.startsWith("--")) {
                // Positional argument - collect for now
                if (arg.contains("=")) {
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
     * Maps positional arguments to named parameters based on the tool's schema.
     * Now handles both required and optional parameters.
     */
    private void mapPositionalArguments(String toolName, List<String> positionalArgs, Map<String, Object> arguments) {
        try {
            // First try to get required parameters
            List<String> paramNames = new ArrayList<>(MCPSchemaGenerator.getRequiredParameterNames(toolName));
            
            // Add common optional parameters that are often provided positionally
            // This allows commands like: dmtools teams_get_messages_by_name "Chat Name" 0
            if (positionalArgs.size() > paramNames.size()) {
                // Common patterns for optional positional parameters
                if (toolName.equals("teams_get_recent_chats")) {
                    if (paramNames.size() < positionalArgs.size()) paramNames.add("limit");
                    if (paramNames.size() < positionalArgs.size()) paramNames.add("chatType");
                } else if (toolName.contains("_since")) {
                    // For _since commands: no extra optional params (smart pagination handles it)
                    // Do nothing - sinceDate is already required
                } else if (toolName.contains("_get_messages")) {
                    // Add "limit" as next expected parameter for message retrieval commands (but not for _since)
                    if (paramNames.size() < positionalArgs.size()) paramNames.add("limit");
                } else if (toolName.contains("_get_chats") || toolName.contains("_get_recent")) {
                    // Add "limit" for list commands
                    if (paramNames.size() < positionalArgs.size()) paramNames.add("limit");
                }
            }
            
            // Special handling for tools with only optional params (no required params)
            if (paramNames.isEmpty() && !positionalArgs.isEmpty()) {
                if (toolName.equals("teams_get_recent_chats")) {
                    paramNames.add("limit");
                    paramNames.add("chatType");
                } else if (toolName.contains("_get_recent") || toolName.contains("_get_messages") || toolName.contains("_get_chats")) {
                    paramNames.add("limit");  // Most get/list commands have limit as first optional param
                }
            }
            
            // Map positional args to parameter names in order
            int numToMap = Math.min(positionalArgs.size(), paramNames.size());
            for (int i = 0; i < numToMap; i++) {
                String paramValue = positionalArgs.get(i);
                // Try to convert to appropriate type (Integer for "limit" parameter)
                Object convertedValue = convertParameterValue(paramNames.get(i), paramValue);
                arguments.put(paramNames.get(i), convertedValue);
            }
            
            // If there are still leftover positional args, use indexed fallback
            if (positionalArgs.size() > paramNames.size()) {
                for (int i = paramNames.size(); i < positionalArgs.size(); i++) {
                    arguments.put("arg" + i, positionalArgs.get(i));
                }
            }
        } catch (Exception e) {
            logger.warn("Could not retrieve schema for tool '{}'. Falling back to indexed arguments.", toolName);
            // Fallback for when schema cannot be retrieved
            for (int i = 0; i < positionalArgs.size(); i++) {
                arguments.put("arg" + i, positionalArgs.get(i));
            }
        }
    }

    /**
     * Converts parameter value from String to appropriate type based on parameter name.
     */
    private Object convertParameterValue(String paramName, String value) {
        // Common parameter types
        if (paramName.equals("limit") || paramName.endsWith("Count") || paramName.endsWith("Size")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Failed to convert '{}' to Integer for parameter '{}'", value, paramName);
                return value;  // Return as string if conversion fails
            }
        }
        // Add more type conversions as needed
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
            // Create Confluence client
            clients.put("confluence", BasicConfluence.getInstance());
            logger.debug("Created BasicConfluence instance");
        } catch (IOException e) {
            logger.warn("Failed to create BasicConfluence: {}", e.getMessage());
        }

        try {
            // Create Figma client
            clients.put("figma", BasicFigmaClient.getInstance());
            logger.debug("Created BasicFigmaClient instance");
        } catch (Exception e) {
            logger.warn("Failed to create BasicFigmaClient: {}", e.getMessage());
        }

        try {
            // Create AI client
            clients.put("ai", BasicGeminiAI.create(new ConversationObserver(), new PropertyReader()));
            logger.debug("Created BasicGeminiAI instance");
        } catch (Exception e) {
            logger.warn("Failed to create BasicGeminiAI: {}", e.getMessage());
        }

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
            // Create KB Tools using Dagger
            KnowledgeBaseComponent kbComponent = DaggerKnowledgeBaseComponent.create();
            clients.put("kb", kbComponent.kbTools());
            logger.debug("Created KBTools instance");
        } catch (Exception e) {
            logger.warn("Failed to create KBTools: {}", e.getMessage());
        }

        logger.info("Created {} client instances for MCP CLI", clients.size());
        return clients;
    }

    /**
     * Gets available integrations based on successfully created clients.
     */
    private Set<String> getAvailableIntegrations() {
        Set<String> integrations = new HashSet<>();

        // Check environment variable first
        String envIntegrations = System.getenv("DMTOOLS_INTEGRATIONS");
        if (envIntegrations != null && !envIntegrations.trim().isEmpty()) {
            String[] parts = envIntegrations.split(",");
            for (String part : parts) {
                String integration = part.trim();
                if (clientInstances.containsKey(integration)) {
                    integrations.add(integration);
                }
            }
        }

        // If no environment variable or no valid integrations, use all available clients
        if (integrations.isEmpty()) {
            integrations.addAll(clientInstances.keySet());
        }
        logger.debug("Available integrations: {}", integrations);
        return integrations;
    }

    /**
     * Creates a standardized error response.
     */
    private String createErrorResponse(String message) {
        JSONObject error = new JSONObject();
        error.put("error", true);
        error.put("message", message);
        return error.toString(2);
    }

    /**
     * Configures logging for CLI usage - suppresses debug/info logs.
     */
    private void configureCLILogging() {
        try {
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
                    if (nameObj instanceof String) {
                        String toolName = ((String) nameObj).toLowerCase();
                        if (toolName.contains(filter)) {
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
     */
    public Map<String, Object> getClientInstances() {
        return Collections.unmodifiableMap(clientInstances);
    }
}
