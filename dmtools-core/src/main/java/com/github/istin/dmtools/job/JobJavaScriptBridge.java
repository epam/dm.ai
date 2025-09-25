package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
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
    private final Map<String, String> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, Object> moduleCache = new ConcurrentHashMap<>();
    private Context jsContext;
    private final Map<String, Object> clientInstances;
    private String currentScriptDirectory;

    @Inject
    public JobJavaScriptBridge(TrackerClient<?> trackerClient, AI ai, Confluence confluence, SourceCode sourceCode) {
        this.trackerClient = trackerClient;
        this.ai = ai;
        this.confluence = confluence;
        this.sourceCode = sourceCode;
        
        // Prepare client instances for MCP executor
        // Note: The MCP system expects specific integration keys regardless of actual implementation
        this.clientInstances = new HashMap<>();
        this.clientInstances.put("jira", trackerClient);  // MCP expects "jira" key for any tracker implementation
        this.clientInstances.put("ai", ai);
        this.clientInstances.put("confluence", confluence);
        
        initializeJavaScriptContext();
    }

    /**
     * Initialize JavaScript context with MCP method bindings
     */
    private void initializeJavaScriptContext() {
        try {
            jsContext = Context.newBuilder("js")
                    .allowAllAccess(false) // Restricted access for security
                    .build();

            // Expose the Java bridge for tool execution using ProxyObject
            jsContext.getBindings("js").putMember("executeToolViaJava", new ExecuteToolProxy());

            // Expose require function for module loading
            jsContext.getBindings("js").putMember("require", new RequireProxy());

            // Expose MCP tools using generated infrastructure
            exposeMCPToolsUsingGenerated();

        } catch (Exception e) {
            logger.error("Failed to initialize JavaScript context", e);
            throw new RuntimeException("JavaScript bridge initialization failed", e);
        }
    }
    
    /**
     * Convert Java objects to JavaScript-compatible format
     */
    private Object convertToJSCompatible(Object obj) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof Map) {
            // Convert Map to JavaScript object that can be accessed with dot notation
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            JSONObject jsonObj = new JSONObject();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                jsonObj.put(entry.getKey(), convertToJSCompatible(entry.getValue()));
            }
            // Parse JSONObject as JavaScript object
            try {
                return jsContext.eval("js", "(" + jsonObj.toString() + ")");
            } catch (Exception e) {
                logger.warn("Failed to parse JSON to JS object: {}", e.getMessage());
                return jsonObj;
            }
        } else if (obj instanceof List) {
            // Convert List to JavaScript array
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            JSONArray jsonArray = new JSONArray();
            for (Object item : list) {
                jsonArray.put(convertToJSCompatible(item));
            }
            try {
                return jsContext.eval("js", "(" + jsonArray.toString() + ")");
            } catch (Exception e) {
                logger.warn("Failed to parse JSON array to JS array: {}", e.getMessage());
                return jsonArray;
            }
        } else if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            // Primitive types are already JS-compatible
            return obj;
        } else {
            // For other objects, try to convert via JSON
            try {
                JSONObject jsonObj = new JSONObject(obj.toString());
                return jsContext.eval("js", "(" + jsonObj.toString() + ")");
            } catch (Exception e) {
                // Fallback to string representation
                return obj.toString();
            }
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
                        argsMap.put(key, memberValue);
                        logger.debug("Converted JS arg: {} = {} (type: {})", key, memberValue, memberValue != null ? memberValue.getClass().getName() : "null");
                    }
                } else if (argsValue.isHostObject()) {
                    // Handle case where JS object is passed as host object
                    Object hostObject = argsValue.asHostObject();
                    if (hostObject instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hostMap = (Map<String, Object>) hostObject;
                        argsMap.putAll(hostMap);
                        logger.debug("Used host object as Map: {}", argsMap);
                    }
                }
            }
            
            logger.debug("Final args map for tool {}: {}", toolName, argsMap);
            
            // Execute using generated MCP infrastructure
            Object result = MCPToolExecutor.executeTool(toolName, argsMap, clientInstances);
            
            // Convert result to JavaScript-compatible format
            return convertToJSCompatible(result);
        } catch (Exception e) {
            logger.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Expose MCP tools using generated MCPToolExecutor - much better than reflection!
     */
    private void exposeMCPToolsUsingGenerated() {
        // Get all available integrations dynamically based on what's actually configured
        Set<String> integrations = Set.of("jira", "ai", "confluence", "figma");
        
        // Generate tool schemas using MCP infrastructure
        Map<String, Object> toolsResponse = MCPSchemaGenerator.generateToolsListResponse(integrations);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsResponse.get("tools");
        
        // Ensure deterministic order by tool name
        tools.sort(Comparator.comparing(t -> (String) t.get("name")));

        // Expose each tool to JavaScript with generic parameter mapping
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            exposeToolToJS(toolName, tool);
        }
        
        logger.debug("Exposed {} MCP tools to JavaScript using generated infrastructure", tools.size());
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
            return convertJSResultToJava(result);
            
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
        // Convert JSONObject to a simple string and parse it in JavaScript
        // This ensures proper conversion to JavaScript object
        String jsonString = jsonObject.toString();
        return jsContext.eval("js", "(" + jsonString + ")");
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
        String resolvedPath = resolveModulePath(modulePath);
        
        // Check module cache first
        if (moduleCache.containsKey(resolvedPath)) {
            logger.debug("Returning cached module: {}", resolvedPath);
            return moduleCache.get(resolvedPath);
        }

        logger.debug("Loading module: {}", resolvedPath);
        
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
