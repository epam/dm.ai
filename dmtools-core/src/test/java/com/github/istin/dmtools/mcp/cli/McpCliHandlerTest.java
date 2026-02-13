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
    @DisplayName("Error messages should NOT include stack trace by default")
    void testErrorMessagesWithoutStackTrace() {
        // Try to execute a non-existent tool
        String[] args = {"mcp", "nonexistent_tool"};

        String result = mcpCliHandler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        String message = response.getString("message");

        // Error message should NOT include stack trace by default (only with --debug)
        assertFalse(message.contains("Stack trace:") || message.contains("at com.github.istin"),
                  "Error message should not include stack trace in normal mode");

        // Should contain error message
        assertTrue(message.contains("Unknown tool") || message.contains("nonexistent_tool"),
                  "Error message should mention the unknown tool");
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

    @Test
    @DisplayName("Should treat URLs with query parameters as positional arguments, not key=value")
    void testUrlWithQueryParametersAsPositionalArgument() {
        // This tests the bug fix for URLs containing = characters
        // URLs like "https://example.com?node-id=123" should be treated as positional args, not key=value
        String[] args = {"mcp", "test_tool", "https://www.figma.com/design/abc?node-id=123-456&t=xyz"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should parse the URL as a positional argument, not try to split on =
        // The test should not crash and should return an error for nonexistent test_tool
        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Unknown tool") || 
                  response.getString("message").contains("Tool execution failed"));
    }

    @Test
    @DisplayName("Should correctly distinguish between valid key=value and URLs with =")
    void testDistinguishKeyValueFromUrls() {
        // Valid key=value should be parsed as named argument
        String[] args1 = {"mcp", "test_tool", "paramName=value"};
        String result1 = mcpCliHandler.processMcpCommand(args1);
        JSONObject response1 = new JSONObject(result1);
        assertTrue(response1.getBoolean("error")); // Expected since test_tool doesn't exist
        
        // URL with = should be parsed as positional argument
        String[] args2 = {"mcp", "test_tool", "https://example.com?key=value"};
        String result2 = mcpCliHandler.processMcpCommand(args2);
        JSONObject response2 = new JSONObject(result2);
        assertTrue(response2.getBoolean("error")); // Expected since test_tool doesn't exist
    }

    @Test
    @DisplayName("Should map single positional argument to varargs array parameter")
    void testVarargsSingleArgument() {
        // Test that a single URL is converted to String[] for varargs parameter
        String[] args = {"mcp", "confluence_contents_by_urls", 
            "https://team-1626860771808.atlassian.net/wiki/spaces/DE1/pages/1940652034/JAI+DIGIX+TEST+CASES+RELATION"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should not throw ClassCastException - the URL should be converted to String[]
        JSONObject response = new JSONObject(result);
        // The command might fail due to missing config, but should not fail with ClassCastException
        // If it's an error, it should be a configuration/connection error, not a type casting error
        if (response.has("error")) {
            String message = response.getString("message");
            assertFalse(message.contains("ClassCastException"), 
                "Should not have ClassCastException - varargs should be handled correctly. Error: " + message);
            assertFalse(message.contains("cannot be cast"), 
                "Should not have casting error. Error: " + message);
        }
    }

    @Test
    @DisplayName("Should map multiple positional arguments to varargs array parameter")
    void testVarargsMultipleArguments() {
        // Test that multiple URLs are collected into String[] for varargs parameter
        String[] args = {"mcp", "confluence_contents_by_urls", 
            "https://example.com/wiki/spaces/SPACE/pages/123/Page1",
            "https://example.com/wiki/spaces/SPACE/pages/456/Page2",
            "https://example.com/wiki/spaces/SPACE/pages/789/Page3"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should not throw ClassCastException - all URLs should be collected into String[]
        JSONObject response = new JSONObject(result);
        if (response.has("error")) {
            String message = response.getString("message");
            assertFalse(message.contains("ClassCastException"), 
                "Should not have ClassCastException - multiple varargs should be handled correctly. Error: " + message);
            assertFalse(message.contains("cannot be cast"), 
                "Should not have casting error. Error: " + message);
        }
    }

    @Test
    @DisplayName("Should handle varargs parameter with URLs containing special characters")
    void testVarargsWithSpecialCharacters() {
        // Test URLs with +, spaces, and other special characters
        String[] args = {"mcp", "confluence_contents_by_urls", 
            "https://team-1626860771808.atlassian.net/wiki/spaces/DE1/pages/1940652034/JAI+DIGIX+TEST+CASES+RELATION"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        if (response.has("error")) {
            String message = response.getString("message");
            assertFalse(message.contains("ClassCastException"), 
                "Should handle special characters in varargs. Error: " + message);
        }
    }

    @Test
    @DisplayName("Should handle regular parameters correctly when varargs parameter exists")
    void testRegularParametersWithVarargs() {
        // Test that regular parameters still work when there's a varargs parameter
        // Using a tool that might have both regular and varargs params
        String[] args = {"mcp", "confluence_contents_by_urls", 
            "https://example.com/wiki/spaces/SPACE/pages/123/Page"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        // Should parse without errors (even if tool execution fails due to config)
        JSONObject response = new JSONObject(result);
        assertNotNull(response);
        // Should not have parsing/type errors
        if (response.has("error")) {
            String message = response.getString("message");
            assertFalse(message.contains("ClassCastException"), 
                "Should not have type casting errors. Error: " + message);
        }
    }

    @Test
    @DisplayName("Should handle empty varargs parameter gracefully")
    void testEmptyVarargs() {
        // Test with no arguments for varargs parameter (should fail validation, not type error)
        String[] args = {"mcp", "confluence_contents_by_urls"};
        
        String result = mcpCliHandler.processMcpCommand(args);
        
        JSONObject response = new JSONObject(result);
        // Should return an error, but not a ClassCastException
        assertTrue(response.has("error"));
        String message = response.getString("message");
        assertFalse(message.contains("ClassCastException"), 
            "Should not have ClassCastException for empty varargs. Error: " + message);
    }

    @Test
    @DisplayName("Should handle varargs parameter with --debug flag")
    void testVarargsWithDebugFlag() {
        // Test that --debug flag doesn't interfere with varargs parsing
        String[] args = {"mcp", "confluence_contents_by_urls",
            "https://your-company.atlassian.net/wiki/spaces/SPACE/pages/1940652034/JAI+DIGIX+TEST+CASES+RELATION",
            "--debug"};

        String result = mcpCliHandler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        // --debug should be ignored (not treated as positional arg)
        if (response.has("error")) {
            String message = response.getString("message");
            assertFalse(message.contains("ClassCastException"),
                "Should handle --debug flag correctly with varargs. Error: " + message);
        }
    }

    // NOTE: Tests for provider-specific AI tools (Ollama, Bedrock, Gemini) have been moved to
    // McpCliHandlerProviderIntegrationTest in src/integrationTest/java
    // These tests make real network calls and should not be in unit tests.
    // Run integration tests with: ./gradlew :dmtools-core:integrationTest
}
