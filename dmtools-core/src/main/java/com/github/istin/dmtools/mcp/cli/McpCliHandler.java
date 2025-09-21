package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.figma.BasicFigmaClient;
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

            return result.toString();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid tool or arguments", e);
            return createErrorResponse("Invalid tool or arguments: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error executing tool", e);
            return createErrorResponse("Tool execution failed: " + e.getMessage());
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
     */
    private void mapPositionalArguments(String toolName, List<String> positionalArgs, Map<String, Object> arguments) {
        try {
            List<String> paramNames = MCPSchemaGenerator.getRequiredParameterNames(toolName);
            
            // If there's a 1-to-1 match of positional args to required params, map them
            if (positionalArgs.size() == paramNames.size()) {
                for (int i = 0; i < positionalArgs.size(); i++) {
                    arguments.put(paramNames.get(i), positionalArgs.get(i));
                }
            } else {
                // Fallback for ambiguity or mismatch
                for (int i = 0; i < positionalArgs.size(); i++) {
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
            // Set all loggers to ERROR level to suppress debug/info output
            Configurator.setAllLevels("com.github.istin.dmtools", org.apache.logging.log4j.Level.ERROR);
            Configurator.setAllLevels("org.apache", org.apache.logging.log4j.Level.ERROR);
            Configurator.setAllLevels("okhttp3", org.apache.logging.log4j.Level.ERROR);
            Configurator.setRootLevel(org.apache.logging.log4j.Level.ERROR);
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
