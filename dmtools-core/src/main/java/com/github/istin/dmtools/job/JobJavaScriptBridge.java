package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.cli.CliCommandExecutor;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.file.FileTools;
import com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import com.github.istin.dmtools.microsoft.teams.BasicTeamsClient;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JavaScript bridge for AI jobs using generated MCP infrastructure.
 * Provides sandboxed JavaScript execution with access to MCP-compatible methods
 * and support for loading JavaScript from remote source code URLs, resources, or inline code.
 */
@Singleton
public class JobJavaScriptBridge {

    private static final Logger logger = LogManager.getLogger(JobJavaScriptBridge.class);

    private final TrackerClient<?> trackerClient;
    private final AI ai;
    private final Confluence confluence;
    private final SourceCode sourceCode;
    private final com.github.istin.dmtools.common.kb.tool.KBTools kbTools;
    private final Map<String, String> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, Object> moduleCache = new ConcurrentHashMap<>();
    private Context jsContext;
    private final Map<String, Object> clientInstances;
    private String currentScriptDirectory;

    @Inject
    public JobJavaScriptBridge(TrackerClient<?> trackerClient, AI ai, Confluence confluence, SourceCode sourceCode, com.github.istin.dmtools.common.kb.tool.KBTools kbTools) {
        logger.info("🏗️  [PERFORMANCE] Creating JobJavaScriptBridge instance (lazy init mode)");
        this.trackerClient = trackerClient;
        this.ai = ai;
        this.confluence = confluence;
        this.sourceCode = sourceCode;
        this.kbTools = kbTools;

        // Prepare client instances for MCP executor
        // Note: The MCP system expects specific integration keys regardless of actual implementation
        this.clientInstances = new HashMap<>();
        this.clientInstances.put("jira", trackerClient);  // MCP expects "jira" key for any tracker implementation
        // Also register as "jira_xray" if it's an XrayClient
        if (trackerClient instanceof com.github.istin.dmtools.atlassian.jira.xray.XrayClient) {
            this.clientInstances.put("jira_xray", trackerClient);
        }
        this.clientInstances.put("ai", ai);
        this.clientInstances.put("confluence", confluence);
        this.clientInstances.put("file", new FileTools());  // File operations for reading files from working directory
        this.clientInstances.put("cli", new CliCommandExecutor());  // CLI command execution for automation workflows
        this.clientInstances.put("kb", kbTools);  // Knowledge Base tools for KB management

        // Initialize GitHub client if configured
        try {
            this.clientInstances.put("github", com.github.istin.dmtools.github.BasicGithub.getInstance());
            logger.debug("GitHub client initialized for JavaScript bridge");
        } catch (Exception e) {
            logger.debug("GitHub client not initialized: {}. GitHub tools will not be available.", e.getMessage());
        }

        // Initialize Mermaid Index Tools if available
        try {
            com.github.istin.dmtools.di.MermaidIndexComponent mermaidComponent =
                com.github.istin.dmtools.di.DaggerMermaidIndexComponent.create();
            this.clientInstances.put("mermaid", mermaidComponent.mermaidIndexTools());
            logger.debug("MermaidIndexTools initialized for JavaScript bridge");
        } catch (Exception e) {
            logger.debug("MermaidIndexTools not initialized: {}. Mermaid tools will not be available.", e.getMessage());
        }

        // Initialize Teams client if configured
        try {
            this.clientInstances.put("teams", BasicTeamsClient.getInstance());
            logger.debug("BasicTeamsClient initialized for JavaScript bridge");
        } catch (Exception e) {
            logger.debug("BasicTeamsClient not initialized: {}. Teams tools will not be available.", e.getMessage());
        }

        // Initialize Azure DevOps client if configured
        try {
            this.clientInstances.put("ado", BasicAzureDevOpsClient.getInstance());
            logger.debug("BasicAzureDevOpsClient initialized for JavaScript bridge");
        } catch (Exception e) {
            logger.debug("BasicAzureDevOpsClient not initialized: {}. ADO tools will not be available.", e.getMessage());
        }

        // Initialize TestRail client if configured
        try {
            this.clientInstances.put("testrail", com.github.istin.dmtools.testrail.TestRailClient.getInstance());
            logger.debug("TestRailClient initialized for JavaScript bridge");
        } catch (Exception e) {
            logger.debug("TestRailClient not initialized: {}. TestRail tools will not be available.", e.getMessage());
        }

        // Don't initialize JavaScript context in constructor - use lazy initialization instead
        // This significantly improves startup time for commands that don't need JS execution
        // initializeJavaScriptContext();
    }

    /**
     * Ensure JavaScript context is initialized (lazy initialization).
     * This method is called before any JS execution to avoid startup overhead.
     */
    private synchronized void ensureJavaScriptContext() {
        if (jsContext == null) {
            long startTime = System.currentTimeMillis();
            logger.info("🚀 [PERFORMANCE] Starting lazy JavaScript context initialization...");
            initializeJavaScriptContext();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ [PERFORMANCE] JavaScript context initialized in {}ms", duration);
        }
    }

    /**
     * Initialize JavaScript context with MCP method bindings
     */
    private void initializeJavaScriptContext() {
        try {
            logger.debug("Initializing JavaScript context (lazy initialization)");
            jsContext = Context.newBuilder("js")
                    .allowAllAccess(false) // Restricted access for security
                    .build();

            // Expose the Java bridge for tool execution using ProxyObject
            jsContext.getBindings("js").putMember("executeToolViaJava", new ExecuteToolProxy());

            // Expose require function for module loading
            jsContext.getBindings("js").putMember("require", new RequireProxy());

            // Expose MCP tools using generated infrastructure
            exposeMCPToolsUsingGenerated();

            logger.debug("JavaScript context initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize JavaScript context", e);
            throw new RuntimeException("JavaScript bridge initialization failed", e);
        }
    }
    
    /**
     * Convert Java objects to JavaScript-compatible format.
     * Builds the full JSON string in pure Java first (via toJsonString), then
     * evaluates it once in GraalJS.  This avoids the previous bug where
     * intermediate GraalVM Values were placed back into a JSONArray and then
     * serialised with JSONArray.toString(), which called Value.toString() and
     * produced JS object-literal syntax with unquoted keys instead of valid JSON.
     */
    private Object convertToJSCompatible(Object obj) {
        ensureJavaScriptContext();

        if (obj == null) {
            return null;
        }

        // Primitives need no conversion
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }

        // Build a valid JSON string entirely in Java, then evaluate once in GraalJS
        String jsonStr = toJsonString(obj);
        try {
            return jsContext.eval("js", "(" + jsonStr + ")");
        } catch (Exception e) {
            logger.warn("Failed to convert to JS compatible: {}", e.getMessage());
            return jsonStr;
        }
    }
    
    /**
     * Execute MCP tool from JavaScript
     */
    public Object executeToolFromJS(String toolName, Object jsArgs) {
        try {
            // Convert JavaScript object to Map
            Map<String, Object> argsMap = new HashMap<>();
            if (jsArgs != null) {
                logger.debug("Converting JS args for tool {}: {} (type: {})", toolName, jsArgs, jsArgs.getClass().getName());
                
                Value argsValue = Value.asValue(jsArgs);
                logger.debug("JS args as Value: hasMembers={}, isHostObject={}", argsValue.hasMembers(), argsValue.isHostObject());
                
                if (argsValue.hasMembers()) {
                    for (String key : argsValue.getMemberKeys()) {
                        Object memberValue = argsValue.getMember(key).as(Object.class);
                        // Convert PolyglotMap to JSONObject or PolyglotList to JSONArray for better compatibility
                        if (memberValue != null) {
                            String className = memberValue.getClass().getName();
                            if (className.contains("PolyglotMap")) {
                                memberValue = convertPolyglotValueToJSON(memberValue);
                            } else if (className.contains("PolyglotList")) {
                                memberValue = convertPolyglotValueToJSON(memberValue);
                            }
                        }
                        // Convert JSONArray to List<String> for array parameters
                        if (memberValue instanceof JSONArray) {
                            JSONArray jsonArray = (JSONArray) memberValue;
                            List<String> stringList = new ArrayList<>();
                            for (int i = 0; i < jsonArray.length(); i++) {
                                Object item = jsonArray.get(i);
                                if (item instanceof String) {
                                    stringList.add((String) item);
                                } else {
                                    stringList.add(item.toString());
                                }
                            }
                            memberValue = stringList;
                        } else if (memberValue instanceof String) {
                            String strValue = ((String) memberValue).trim();
                            // Only try to parse as JSON array if it looks like a valid JSON array
                            // (starts with [ and ends with ])
                            if (strValue.startsWith("[") && strValue.endsWith("]") && strValue.length() > 2) {
                                // Handle case where JavaScript passes array as JSON string
                                try {
                                    JSONArray jsonArray = new JSONArray(strValue);
                                    List<String> stringList = new ArrayList<>();
                                    for (int i = 0; i < jsonArray.length(); i++) {
                                        Object item = jsonArray.get(i);
                                        if (item instanceof String) {
                                            stringList.add((String) item);
                                        } else {
                                            stringList.add(item.toString());
                                        }
                                    }
                                    memberValue = stringList;
                                    logger.debug("Parsed JSON array string for {}: {} elements", key, stringList.size());
                                } catch (Exception e) {
                                    // If parsing fails, keep original value (it's just a string starting with [)
                                    logger.debug("Failed to parse as JSON array for {} (keeping as string): {}", key, e.getMessage());
                                }
                            }
                            // Otherwise, it's just a regular string - keep it as-is
                        }
                        argsMap.put(key, memberValue);
                        logger.debug("Converted JS arg: {} = {} (type: {})", key, memberValue, memberValue != null ? memberValue.getClass().getName() : "null");
                    }
                } else if (argsValue.isHostObject()) {
                    // Handle case where JS object is passed as host object
                    Object hostObject = argsValue.asHostObject();
                    if (hostObject instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hostMap = (Map<String, Object>) hostObject;
                        // Convert JSONArray values in the map to List<String>
                        for (Map.Entry<String, Object> entry : hostMap.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof JSONArray) {
                                JSONArray jsonArray = (JSONArray) value;
                                List<String> stringList = new ArrayList<>();
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    Object item = jsonArray.get(i);
                                    if (item instanceof String) {
                                        stringList.add((String) item);
                                    } else {
                                        stringList.add(item.toString());
                                    }
                                }
                                hostMap.put(entry.getKey(), stringList);
                            } else if (value instanceof String) {
                                String strValue = ((String) value).trim();
                                // Only try to parse as JSON array if it looks like a valid JSON array
                                // (starts with [ and ends with ])
                                if (strValue.startsWith("[") && strValue.endsWith("]") && strValue.length() > 2) {
                                    // Handle case where JavaScript passes array as JSON string
                                    try {
                                        JSONArray jsonArray = new JSONArray(strValue);
                                        List<String> stringList = new ArrayList<>();
                                        for (int i = 0; i < jsonArray.length(); i++) {
                                            Object item = jsonArray.get(i);
                                            if (item instanceof String) {
                                                stringList.add((String) item);
                                            } else {
                                                stringList.add(item.toString());
                                            }
                                        }
                                        hostMap.put(entry.getKey(), stringList);
                                        logger.debug("Parsed JSON array string for {}: {} elements", entry.getKey(), stringList.size());
                                    } catch (Exception e) {
                                        // If parsing fails, keep original value (it's just a string starting with [)
                                        logger.debug("Failed to parse as JSON array for {} (keeping as string): {}", entry.getKey(), e.getMessage());
                                    }
                                }
                                // Otherwise, it's just a regular string - keep it as-is
                            }
                        }
                        argsMap.putAll(hostMap);
                        logger.debug("Used host object as Map: {}", argsMap);
                    }
                }
            }
            
            logger.debug("Final args map for tool {}: {}", toolName, argsMap);
            
            // Get tool schema to check parameter types
            Map<String, Object> toolSchema = getToolSchema(toolName);
            
            // Convert ArrayList to String[] for MCP tools that expect array parameters
            // But keep as List<String> for parameters that require List (e.g., mermaid_index_generate patterns)
            Map<String, Object> convertedArgsMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : argsMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof ArrayList) {
                    @SuppressWarnings("unchecked")
                    ArrayList<Object> list = (ArrayList<Object>) value;
                    
                    // Check if this parameter should be kept as List<String> instead of String[]
                    // This is needed for tools like mermaid_index_generate that expect List<String>
                    boolean shouldKeepAsList = shouldKeepAsList(toolName, entry.getKey());
                    
                    if (shouldKeepAsList) {
                        // Convert to List<String> and keep as List
                        List<String> stringList = new ArrayList<>();
                        for (Object item : list) {
                            stringList.add(item != null ? item.toString() : null);
                        }
                        convertedArgsMap.put(entry.getKey(), stringList);
                        logger.debug("Kept ArrayList as List<String> for parameter {}: {} elements", entry.getKey(), stringList.size());
                    } else {
                        // Check if parameter is expected to be an array based on schema
                        boolean isArrayParameter = isArrayParameter(toolSchema, entry.getKey());
                        
                        if (isArrayParameter) {
                            // Convert ArrayList to String[] for array parameters
                            String[] array = new String[list.size()];
                            for (int i = 0; i < list.size(); i++) {
                                Object item = list.get(i);
                                array[i] = item != null ? item.toString() : null;
                            }
                            convertedArgsMap.put(entry.getKey(), array);
                            logger.debug("Converted ArrayList to String[] for parameter {}: {} elements", entry.getKey(), array.length);
                        } else if (list.size() == 1 && list.get(0) instanceof String) {
                            // Single string element in ArrayList for non-array parameter - extract as string
                            convertedArgsMap.put(entry.getKey(), list.get(0));
                            logger.debug("Extracted single string from ArrayList for parameter {}: {}", entry.getKey(), list.get(0));
                        } else {
                            // Multiple elements in ArrayList but parameter is NOT an array type in schema.
                            // This happens when a JSON array string was auto-parsed (e.g. file_write content
                            // that is valid JSON). Reconstruct as JSON array string to preserve original intent.
                            JSONArray reconstructed = new JSONArray(list);
                            String jsonStr = reconstructed.toString();
                            convertedArgsMap.put(entry.getKey(), jsonStr);
                            logger.debug("Reconstructed JSON string for non-array parameter {}: {} chars", entry.getKey(), jsonStr.length());
                        }
                    }
                } else {
                    convertedArgsMap.put(entry.getKey(), value);
                }
            }
            
            // Execute using generated MCP infrastructure
            Object result = MCPToolExecutor.executeTool(toolName, convertedArgsMap, clientInstances);
            
            // Convert result to JavaScript-compatible format
            return convertToJSCompatible(result);
        } catch (Exception e) {
            logger.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }

    // Cache for tool schemas
    private Map<String, Map<String, Object>> toolSchemasCache = null;

    /**
     * Expose MCP tools using generated MCPToolExecutor - much better than reflection!
     */
    private void exposeMCPToolsUsingGenerated() {
        // Get all available integrations dynamically based on what's actually configured
        Set<String> integrations = new java.util.HashSet<>(Set.of("jira", "ado", "ai", "confluence", "figma", "file", "cli", "teams", "sharepoint", "kb", "mermaid", "testrail", "github"));
        // Add jira_xray if XrayClient is available
        if (trackerClient instanceof com.github.istin.dmtools.atlassian.jira.xray.XrayClient) {
            integrations.add("jira_xray");
        }
        
        // Generate tool schemas using MCP infrastructure
        Map<String, Object> toolsResponse = MCPSchemaGenerator.generateToolsListResponse(integrations);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsResponse.get("tools");
        
        // Cache tool schemas for later use
        toolSchemasCache = new HashMap<>();
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            toolSchemasCache.put(toolName, tool);
        }
        
        // Ensure deterministic order by tool name
        tools.sort(Comparator.comparing(t -> (String) t.get("name")));

        // Expose each tool to JavaScript with generic parameter mapping
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            exposeToolToJS(toolName, tool);
            if (toolName.contains("mermaid")) {
                logger.info("Exposed Mermaid tool to JavaScript: {}", toolName);
            }
        }
        
        logger.info("Exposed {} MCP tools to JavaScript using generated infrastructure", tools.size());
    }

    /**
     * Get tool schema from cache
     */
    private Map<String, Object> getToolSchema(String toolName) {
        if (toolSchemasCache == null) {
            // Initialize cache if not already done
            exposeMCPToolsUsingGenerated();
        }
        return toolSchemasCache != null ? toolSchemasCache.get(toolName) : null;
    }

    /**
     * Check if a parameter is expected to be an array based on tool schema
     */
    private boolean isArrayParameter(Map<String, Object> toolSchema, String paramName) {
        if (toolSchema == null) {
            // If schema not available, check known array parameter names
            return paramName.equals("fields") || paramName.endsWith("s") && 
                   (paramName.contains("field") || paramName.contains("id") || paramName.contains("url"));
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
     * Check if a parameter should be kept as List<String> instead of converted to String[]
     * This is needed for tools that have List<String> parameters in their method signatures
     */
    private boolean shouldKeepAsList(String toolName, String paramName) {
        // Mermaid index tools expect List<String> for pattern parameters
        if (toolName != null && toolName.contains("mermaid") && 
            (paramName.equals("include_patterns") || paramName.equals("exclude_patterns"))) {
            return true;
        }
        return false;
    }

    /**
     * ProxyExecutable implementation for GraalVM Polyglot compatibility
     */
    private class ExecuteToolProxy implements ProxyExecutable {
        @Override
        public Object execute(Value... arguments) {
            if (arguments.length < 1) {
                throw new IllegalArgumentException("executeToolViaJava requires at least 1 argument: toolName");
            }
            
            String toolName = arguments[0].asString();
            Object jsArgs = arguments.length > 1 ? arguments[1] : null;
            
            return executeToolFromJS(toolName, jsArgs);
        }
    }

    /**
     * Expose a single MCP tool to JavaScript context using generated executor
     */
    private void exposeToolToJS(String toolName, Map<String, Object> toolSchema) {
        // Extract parameter information from the tool schema
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) toolSchema.get("inputSchema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = inputSchema != null ? 
            (Map<String, Object>) inputSchema.get("properties") : new HashMap<>();
        
        // Generate parameter mapping dynamically based on schema
        StringBuilder parameterMappingLogic = new StringBuilder();
        if (!properties.isEmpty()) {
            List<String> paramNames = new ArrayList<>(properties.keySet());
            for (int i = 0; i < paramNames.size(); i++) {
                String paramName = paramNames.get(i);
                parameterMappingLogic.append(String.format(
                    "if (arguments.length > %d) args.%s = arguments[%d];\n                    ", 
                    i, paramName, i));
            }
        }
        
        // Create a JavaScript function that calls the generated MCP executor
        String jsFunction = String.format("""
            function %s() {
                // Handle both object parameter and individual arguments
                var args = {};
                
                if (arguments.length === 1 && typeof arguments[0] === 'object' && arguments[0] !== null) {
                    // Single object parameter - use directly
                    args = arguments[0];
                } else if (arguments.length > 0) {
                    // Individual arguments - map to parameters based on schema
                    %s
                    // Special handling for AI chat tools
                    if ('%s'.includes('ai_chat') && arguments.length === 1 && typeof arguments[0] === 'string') {
                        args.message = arguments[0];
                    }
                }
                console.log('Calling tool %s with args:', JSON.stringify(args));
                return executeToolViaJava('%s', args);
            }
            """, toolName, parameterMappingLogic.toString(), toolName, toolName, toolName);
            
        try {
            jsContext.eval("js", jsFunction);
            logger.debug("Exposed MCP tool {} to JavaScript", toolName);
        } catch (Exception e) {
            logger.error("Failed to expose tool {} to JavaScript", toolName, e);
        }
    }

    /**
     * Execute JavaScript code with dynamic JSON parameters
     */
    public Object executeJavaScript(String jsSourceOrPath, JSONObject parameters) throws Exception {
        ensureJavaScriptContext();

        try {
            // Set current script directory for relative path resolution
            setCurrentScriptDirectory(jsSourceOrPath);

            String jsCode = loadJavaScriptCode(jsSourceOrPath);

            // Make the tool executor available to JavaScript using ProxyExecutable
            jsContext.getBindings("js").putMember("executeToolViaJava", new ExecuteToolProxy());

            // Ensure require function is available for this execution
            jsContext.getBindings("js").putMember("require", new RequireProxy());
            
            // Evaluate the JavaScript code
            jsContext.eval("js", jsCode);
            
            // Get the action function
            Value actionFunction = jsContext.getBindings("js").getMember("action");
            if (actionFunction == null || !actionFunction.canExecute()) {
                throw new IllegalArgumentException("JavaScript code must define an 'action' function");
            }
            
            // Convert JSONObject to JavaScript-compatible object
            Object jsCompatibleParams = convertToJSCompatible(parameters);
            
            // Execute the function with proper parameter passing
            Value result = actionFunction.execute(jsCompatibleParams);
            
            // Convert result back to Java object
            // First check if it's a Polyglot array/list and convert directly
            Object javaResult = null;
            try {
                // Check if result is a Polyglot array/list
                if (result.hasArrayElements()) {
                    // Convert PolyglotList directly to JSONArray
                    javaResult = convertPolyglotValueToJSON(result.as(Object.class));
                } else if (result.hasMembers()) {
                    // Convert PolyglotMap directly to JSONObject
                    javaResult = convertPolyglotValueToJSON(result.as(Object.class));
                } else {
                    // Use basic conversion for primitives
                    javaResult = convertJSResultToJava(result);
                }
            } catch (Exception e) {
                logger.warn("Failed to convert Polyglot value, using basic conversion: {}", e.getMessage());
                javaResult = convertJSResultToJava(result);
            }
            return javaResult;
            
        } catch (Exception e) {
            logger.error("JavaScript execution failed for source: {}", 
                         jsSourceOrPath.length() > 100 ? jsSourceOrPath.substring(0, 100) + "..." : jsSourceOrPath, e);
            throw new RuntimeException("JavaScript execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PolyglotMap to JSONObject or PolyglotList to JSONArray for better compatibility with MCP tools
     */
    private Object convertPolyglotValueToJSON(Object polyglotValue) {
        try {
            Value value = Value.asValue(polyglotValue);
            String className = polyglotValue.getClass().getName();
            
            if (className.contains("PolyglotMap") && value.hasMembers()) {
                // Convert PolyglotMap to JSONObject
                JSONObject jsonObject = new JSONObject();
                for (String key : value.getMemberKeys()) {
                    Object memberValue = value.getMember(key).as(Object.class);
                    // Recursively convert nested PolyglotMaps and PolyglotLists
                    if (memberValue != null) {
                        String memberClassName = memberValue.getClass().getName();
                        if (memberClassName.contains("PolyglotMap") || memberClassName.contains("PolyglotList")) {
                            memberValue = convertPolyglotValueToJSON(memberValue);
                        }
                    }
                    jsonObject.put(key, memberValue);
                }
                logger.debug("Converted PolyglotMap to JSONObject: {}", jsonObject.toString());
                return jsonObject;
                
            } else if (className.contains("PolyglotList") && value.hasArrayElements()) {
                // Convert PolyglotList to JSONArray
                JSONArray jsonArray = new JSONArray();
                long arraySize = value.getArraySize();
                for (long i = 0; i < arraySize; i++) {
                    Object elementValue = value.getArrayElement(i).as(Object.class);
                    // Recursively convert nested PolyglotMaps and PolyglotLists
                    if (elementValue != null) {
                        String elementClassName = elementValue.getClass().getName();
                        if (elementClassName.contains("PolyglotMap") || elementClassName.contains("PolyglotList")) {
                            elementValue = convertPolyglotValueToJSON(elementValue);
                        }
                    }
                    jsonArray.put(elementValue);
                }
                logger.debug("Converted PolyglotList to JSONArray: {}", jsonArray.toString());
                return jsonArray;
            }
            
            // If it's neither a PolyglotMap nor PolyglotList, return as-is
            return polyglotValue;
            
        } catch (Exception e) {
            logger.warn("Failed to convert Polyglot value to JSON: {}", e.getMessage());
            return polyglotValue; // Return original value on failure
        }
    }

    /**
     * Convert JavaScript result to Java object
     */
    private Object convertJSResultToJava(Value result) {
        if (result.isNull()) {
            return null;
        } else if (result.isString()) {
            return result.asString();
        } else if (result.isNumber()) {
            return result.asDouble();
        } else if (result.isBoolean()) {
            return result.asBoolean();
        } else {
            return result.toString();
        }
    }

    /**
     * Load JavaScript code from inline string, resource file, or remote source code URL
     */
    private String loadJavaScriptCode(String jsSourceOrPath) throws IOException {
        // Source code URL support (any HTTP/HTTPS URL)
        if (jsSourceOrPath.startsWith("http://") || jsSourceOrPath.startsWith("https://")) {
            return loadFromSourceCode(jsSourceOrPath);
        }
        
        // Better detection: check if it's actually JavaScript code
        if (jsSourceOrPath.trim().startsWith("function") || jsSourceOrPath.contains("action")) {
            return jsSourceOrPath; // Inline JavaScript code
        }
        
        // If it contains "/" or ends with ".js", treat as resource path
        if (jsSourceOrPath.contains("/") || jsSourceOrPath.endsWith(".js")) {
            return loadFromResources(jsSourceOrPath);
        } else {
            return jsSourceOrPath; // Fallback to inline
        }
    }

    /**
     * Load JavaScript from source code URL with caching
     */
    private String loadFromSourceCode(String sourceCodeUrl) throws IOException {
        return resourceCache.computeIfAbsent(sourceCodeUrl, url -> {
            try {
                if (sourceCode == null) {
                    throw new RuntimeException("SourceCode not configured - cannot load from remote source");
                }
                logger.info("Loading JavaScript from source code: {}", url);
                String content = sourceCode.getFileContent(url);
                logger.debug("Successfully loaded {} characters from source code", content.length());
                return content;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load JS from source code: " + url, e);
            }
        });
    }

    /**
     * Load JavaScript from classpath resources or filesystem with caching
     */
    private String loadFromResources(String resourcePath) throws IOException {
        return resourceCache.computeIfAbsent(resourcePath, path -> {
            try {
                // First try to load from classpath resources
                try (InputStream inputStream = getClass().getClassLoader()
                        .getResourceAsStream(path)) {
                    if (inputStream != null) {
                        logger.debug("Loading JavaScript from resource: {}", path);
                        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    }
                }
                
                // If not found in resources, try to load from filesystem
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.isFile()) {
                    logger.debug("Loading JavaScript from filesystem: {}", file.getAbsolutePath());
                    return java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
                }
                
                // If still not found, throw exception
                throw new RuntimeException("JavaScript file not found in resources or filesystem: " + path);
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to load JS file: " + path, e);
            }
        });
    }

    /**
     * Convert JSONObject to JavaScript-compatible object using GraalVM's Value system
     */
    private Object convertToJSCompatible(JSONObject jsonObject) {
        ensureJavaScriptContext();

        // Convert JSONObject to a simple string and parse it in JavaScript
        // This ensures proper conversion to JavaScript object
        String jsonString = jsonObject.toString();
        return jsContext.eval("js", "(" + jsonString + ")");
    }

    /**
     * Recursively converts a Java object to a valid JSON string.
     * All structural traversal is done at the Java level so that GraalVM Values
     * are never placed back into org.json containers (which would serialise them
     * via Value.toString() and produce unquoted-key JS object literals).
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof Boolean || obj instanceof Number) {
            return obj.toString();
        }
        if (obj instanceof String) {
            return JSONObject.quote((String) obj);
        }
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).toString();
        }
        if (obj instanceof JSONArray) {
            return ((JSONArray) obj).toString();
        }
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(JSONObject.quote(entry.getKey())).append(":").append(toJsonString(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJsonString(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        // For domain objects (e.g. Ticket extends JSONModel) whose toString() returns
        // valid JSON, use that directly. Fall back to a quoted string if it is not JSON.
        String str = obj.toString();
        try {
            if (str.startsWith("{")) {
                new JSONObject(str);  // validate
            } else if (str.startsWith("[")) {
                new JSONArray(str);   // validate
            } else {
                return JSONObject.quote(str);
            }
            return str;
        } catch (Exception e) {
            return JSONObject.quote(str);
        }
    }

    /**
     * Convert JSONObject to Map recursively (backup method)
     */
    private Map<String, Object> convertJSONObjectToMap(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.put(key, convertJSONObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, convertJSONArrayToList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }
    
    /**
     * Convert JSONArray to List recursively
     */
    private List<Object> convertJSONArrayToList(JSONArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                list.add(convertJSONObjectToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(convertJSONArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    /**
     * Set current script directory for relative path resolution
     */
    private void setCurrentScriptDirectory(String jsSourceOrPath) {
        if (jsSourceOrPath.contains("/")) {
            // Extract directory from path
            int lastSlash = jsSourceOrPath.lastIndexOf('/');
            if (lastSlash > 0) {
                currentScriptDirectory = jsSourceOrPath.substring(0, lastSlash);
            } else {
                currentScriptDirectory = "";
            }
        } else {
            currentScriptDirectory = "";
        }
        logger.debug("Set current script directory to: {}", currentScriptDirectory);
    }

    /**
     * Resolve module path relative to current script
     */
    private String resolveModulePath(String modulePath) {
        if (modulePath.startsWith("./") || modulePath.startsWith("../")) {
            // Relative path - resolve relative to current script directory
            if (currentScriptDirectory != null && !currentScriptDirectory.isEmpty()) {
                String resolvedPath = currentScriptDirectory + "/" + modulePath;
                // Normalize the path (handle ../ and ./)
                resolvedPath = java.nio.file.Paths.get(resolvedPath).normalize().toString();
                logger.debug("Resolved relative path {} to {}", modulePath, resolvedPath);
                return resolvedPath;
            }
        }
        return modulePath;
    }

    /**
     * Load and execute a JavaScript module, returning its exports
     */
    private Object loadModule(String modulePath) throws IOException {
        ensureJavaScriptContext();

        String resolvedPath = resolveModulePath(modulePath);

        // Check module cache first
        if (moduleCache.containsKey(resolvedPath)) {
            logger.debug("Returning cached module: {}", resolvedPath);
            return moduleCache.get(resolvedPath);
        }

        logger.debug("Loading module: {}", resolvedPath);
        
        // Save current script directory and update it for this module
        // This ensures relative paths within the module resolve correctly
        String savedScriptDirectory = currentScriptDirectory;
        try {
            // Update currentScriptDirectory to the directory of the module being loaded
            // This allows relative requires within this module to resolve correctly
            if (resolvedPath.contains("/")) {
                int lastSlash = resolvedPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    currentScriptDirectory = resolvedPath.substring(0, lastSlash);
                } else {
                    currentScriptDirectory = "";
                }
            } else {
                currentScriptDirectory = "";
            }
            logger.debug("Updated current script directory to: {} for module: {}", currentScriptDirectory, resolvedPath);
        } catch (Exception e) {
            // If updating fails, continue with saved directory
            logger.warn("Failed to update script directory for module: {}", resolvedPath, e);
        }
        
        // Put a placeholder in cache to prevent circular dependency loops
        // This is important for handling circular requires
        Object placeholder = new Object();
        moduleCache.put(resolvedPath, placeholder);
        
        try {
            String moduleCode = loadJavaScriptCode(resolvedPath);
            
            // Wrap module code to capture exports
            String wrappedCode = String.format("""
                (function() {
                    var module = { exports: {} };
                    var exports = module.exports;
                    
                    %s
                    
                    return module.exports;
                })()
                """, moduleCode);

            // Execute the wrapped module code
            Object moduleExports = jsContext.eval("js", wrappedCode);
            
            // Replace placeholder with actual exports
            moduleCache.put(resolvedPath, moduleExports);
            
            logger.debug("Successfully loaded module: {}", resolvedPath);
            return moduleExports;
            
        } catch (Exception e) {
            // Remove failed module from cache
            moduleCache.remove(resolvedPath);
            logger.error("Failed to load module: {}", resolvedPath, e);
            throw new RuntimeException("Failed to load module: " + resolvedPath, e);
        } finally {
            // Restore the original script directory
            currentScriptDirectory = savedScriptDirectory;
            logger.debug("Restored current script directory to: {}", currentScriptDirectory);
        }
    }

    /**
     * ProxyExecutable for require() function
     */
    private class RequireProxy implements ProxyExecutable {
        @Override
        public Object execute(Value... arguments) {
            if (arguments.length != 1) {
                throw new IllegalArgumentException("require() expects exactly one argument (module path)");
            }
            
            String modulePath = arguments[0].asString();
            logger.debug("require() called with: {}", modulePath);
            
            try {
                return loadModule(modulePath);
            } catch (Exception e) {
                logger.error("require() failed for module: {}", modulePath, e);
                throw new RuntimeException("Failed to require module: " + modulePath, e);
            }
        }
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (jsContext != null) {
            jsContext.close();
        }
        moduleCache.clear();
    }
}
