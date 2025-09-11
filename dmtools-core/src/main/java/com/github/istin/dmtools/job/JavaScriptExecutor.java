package com.github.istin.dmtools.job;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.ai.AI;
import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles JavaScript execution for AI job post-processing.
 * Provides a fluent API for configuring and executing JavaScript code with MCP tool access.
 */
public class JavaScriptExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(JavaScriptExecutor.class);
    
    private final String jsCode;
    private final Map<String, Object> parameters = new HashMap<>();
    
    private TrackerClient<?> trackerClient;
    private AI ai;
    private Confluence confluence;
    private SourceCode sourceCode;
    
    public JavaScriptExecutor(String jsCode) {
        this.jsCode = jsCode;
    }
    
    /**
     * Configure MCP clients for JavaScript execution
     */
    public JavaScriptExecutor mcp(TrackerClient<?> trackerClient, AI ai, Confluence confluence, SourceCode sourceCode) {
        this.trackerClient = trackerClient;
        this.ai = ai;
        this.confluence = confluence;
        this.sourceCode = sourceCode;
        return this;
    }
    
    /**
     * Add job context parameters (jobParams, ticket, response)
     */
    public JavaScriptExecutor withJobContext(Object jobParams, Object ticket, Object response) {
        this.parameters.put("jobParams", jobParams);
        this.parameters.put("ticket", ticket);
        this.parameters.put("response", response);
        return this;
    }
    
    /**
     * Add a custom parameter
     */
    public JavaScriptExecutor with(String key, Object value) {
        this.parameters.put(key, value);
        return this;
    }
    
    /**
     * Execute the JavaScript code with configured parameters and MCP tools
     */
    public Object execute() throws Exception {
        if (jsCode == null || jsCode.trim().isEmpty()) {
            logger.debug("No JavaScript code provided, skipping execution");
            return null;
        }
        
        try {
            logger.info("Executing JavaScript post-processing");
            
            // Create JobJavaScriptBridge instance
            JobJavaScriptBridge jsBridge = new JobJavaScriptBridge(trackerClient, ai, confluence, sourceCode);
            
            // Convert parameters for JavaScript execution
            Map<String, Object> jsParams = convertParametersForJS();
            
            // Execute JavaScript - convert Map to JSONObject
            JSONObject jsParamsJson = new JSONObject();
            for (Map.Entry<String, Object> entry : jsParams.entrySet()) {
                jsParamsJson.put(entry.getKey(), entry.getValue());
            }
            Object result = jsBridge.executeJavaScript(jsCode, jsParamsJson);
            
            logger.info("JavaScript executed successfully: {}", result);
            return result;
            
        } catch (Exception e) {
            logger.error("JavaScript post-processing failed", e);
            // Don't throw - let job continue execution
            return createErrorResult(e);
        }
    }
    
    /**
     * Convert Java parameters to JavaScript-compatible format
     */
    private Map<String, Object> convertParametersForJS() {
        Map<String, Object> jsParams = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object value = entry.getValue();
            
            if (value == null ||
                value instanceof String ||
                value instanceof Number ||
                value instanceof Boolean ||
                value instanceof JSONObject ||
                value instanceof JSONArray) {
                jsParams.put(entry.getKey(), value);
            } else if (value instanceof ITicket) {
                // Special handling for ITicket - convert to JSON object for JavaScript access
                ITicket ticket = (ITicket) value;
                JSONObject ticketJson = new JSONObject();
                try {
                    ticketJson.put("key", ticket.getTicketKey());
                    ticketJson.put("title", ticket.getTicketTitle());
                    ticketJson.put("description", ticket.getTicketDescription());
                    ticketJson.put("status", ticket.getStatus());
                    ticketJson.put("issueType", ticket.getIssueType());
                    ticketJson.put("priority", ticket.getPriority());
                    ticketJson.put("created", ticket.getCreated());
                    ticketJson.put("labels", ticket.getTicketLabels());
                    
                    // Add creator if available
                    if (ticket.getCreator() != null) {
                        ticketJson.put("creator", ticket.getCreator().toString());
                    }
                    
                    // Add the raw JSON fields for full access
                    JSONObject fieldsJson = ticket.getFieldsAsJSON();
                    if (fieldsJson != null) {
                        ticketJson.put("fields", fieldsJson);
                    }
                } catch (Exception e) {
                    // If any field access fails, add basic info and raw data
                    ticketJson.put("key", ticket.getTicketKey());
                    ticketJson.put("rawData", ticket.toString());
                }
                jsParams.put(entry.getKey(), ticketJson);
            } else {
                // For other objects, try to put directly first, fallback to Gson serialization
                try {
                    jsParams.put(entry.getKey(), value);
                } catch (JSONException e) {
                    // Fallback for unsupported types - use Gson to serialize and parse
                    try {
                        Gson gson = new Gson();
                        String jsonString = gson.toJson(value);
                        Object parsedValue = gson.fromJson(jsonString, Object.class);
                        jsParams.put(entry.getKey(), parsedValue);
                    } catch (Exception fallbackException) {
                        // Final fallback to toString()
                        jsParams.put(entry.getKey(), value.toString());
                    }
                }
            }
        }
        
        return jsParams;
    }
    
    /**
     * Create error result object for JavaScript execution failures
     */
    private Object createErrorResult(Exception e) {
        JSONObject errorResult = new JSONObject();
        errorResult.put("success", false);
        errorResult.put("error", e.toString());
        errorResult.put("action", "error");
        return errorResult.toString();
    }
}
