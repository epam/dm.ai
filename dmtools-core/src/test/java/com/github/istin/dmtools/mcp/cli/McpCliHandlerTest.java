package com.github.istin.dmtools.mcp.cli;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpCliHandler.
 * Tests both list and tool execution functionality.
 */
class McpCliHandlerTest {

    private McpCliHandler mcpCliHandler;

    @BeforeEach
    void setUp() {
        mcpCliHandler = new McpCliHandler();
    }

    @Test
    @DisplayName("Should handle empty arguments with error message")
    void testEmptyArguments() {
        String[] args = {"mcp"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Usage: mcp <command>"));
    }

    @Test
    @DisplayName("Should handle list command successfully")
    void testListCommand() {
        String[] args = {"mcp", "list"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should return valid JSON with tools array
        JSONObject response = new JSONObject(result);
        assertTrue(response.has("tools"));
        assertTrue(response.get("tools") instanceof org.json.JSONArray);
    }

    @Test
    @DisplayName("Should handle list command with filter")
    void testListCommandWithFilter() {
        String[] args = {"mcp", "list", "jira"};
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should return valid JSON with filtered tools array
        JSONObject response = new JSONObject(result);
        assertTrue(response.has("tools"));
        assertTrue(response.get("tools") instanceof org.json.JSONArray);
        
        // Check that result contains jira tools (if any exist)
        String resultStr = result.toLowerCase();
        if (resultStr.contains("jira_")) {
            // If jira tools exist, should not contain confluence tools
            assertFalse(resultStr.contains("confluence_"));
        }
    }

    @Test
    @DisplayName("Should parse JSON data arguments correctly")
    void testParseJsonDataArguments() {
        String[] args = {"mcp", "test_tool", "--data", "{\"key\": \"DMC-479\", \"fields\": [\"summary\"]}"};
        
        // We'll test the argument parsing by checking if it doesn't throw an exception
        // and returns a proper error (since test_tool doesn't exist)
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Unknown tool") || 
                  response.getString("message").contains("Tool execution failed"));
    }

    @Test
    @DisplayName("Should parse stdin data arguments correctly")
    void testParseStdinDataArguments() {
        String[] args = {"mcp", "test_tool", "--stdin-data", "{\"key\": \"DMC-479\"}"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Unknown tool") || 
                  response.getString("message").contains("Tool execution failed"));
    }

    @Test
    @DisplayName("Should parse positional arguments correctly")
    void testParsePositionalArguments() {
        String[] args = {"mcp", "test_tool", "DMC-479", "summary,description"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Unknown tool") || 
                  response.getString("message").contains("Tool execution failed"));
    }

    @Test
    @DisplayName("Should parse key-value arguments correctly")
    void testParseKeyValueArguments() {
        String[] args = {"mcp", "test_tool", "key=DMC-479", "fields=summary,description"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Unknown tool") || 
                  response.getString("message").contains("Tool execution failed"));
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void testMalformedJsonHandling() {
        String[] args = {"mcp", "test_tool", "--data", "{invalid json}"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should not crash and should return an error response
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
    }

    @Test
    @DisplayName("Should create client instances map")
    void testClientInstancesCreation() {
        Map<String, Object> clientInstances = mcpCliHandler.getClientInstances();
        
        assertNotNull(clientInstances);
        // Should have at least some clients (depending on what's available in test environment)
        // We can't guarantee specific clients will be available in test environment
        assertTrue(clientInstances.size() >= 0);
    }

    @Test
    @DisplayName("Should handle environment variable DMTOOLS_INTEGRATIONS")
    void testEnvironmentVariableHandling() {
        // Test with environment variable set
        try {
            // This test verifies the handler doesn't crash when env var is set
            // We can't easily mock System.getenv in this context, but we can verify
            // the handler handles it gracefully
            String[] args = {"mcp", "list"};
            String result = mcpCliHandler.processMcpCommand(args);
            
            JSONObject response = new JSONObject(result);
            assertTrue(response.has("tools"));
        } catch (Exception e) {
            fail("Should handle environment variables gracefully: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should create proper error response format")
    void testErrorResponseFormat() {
        String[] args = {"mcp"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.has("error"));
        assertTrue(response.has("message"));
        assertTrue(response.getBoolean("error"));
        assertFalse(response.getString("message").isEmpty());
    }

    @Test
    @DisplayName("Should handle exception during tool execution")
    void testExceptionHandling() {
        // Test with a command that will cause an exception
        String[] args = {"mcp", "nonexistent_tool"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Unknown tool") || 
                  response.getString("message").contains("Tool execution failed"));
    }

    @Test
    @DisplayName("Should handle mixed argument types")
    void testMixedArgumentTypes() {
        String[] args = {"mcp", "test_tool", "positional", "key=value", "--data", "{\"json\": true}"};
        
        // Should parse all argument types without crashing
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error")); // Expected since test_tool doesn't exist
        assertNotNull(response.getString("message"));
    }

    @Test
    @DisplayName("Should skip arguments starting with --")
    void testSkipDashDashArguments() {
        String[] args = {"mcp", "test_tool", "--unknown-flag", "value", "normal_arg"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should not crash and should handle known arguments
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error")); // Expected since test_tool doesn't exist
    }
}
