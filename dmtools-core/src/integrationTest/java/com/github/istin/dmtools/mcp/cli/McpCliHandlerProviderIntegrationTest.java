package com.github.istin.dmtools.mcp.cli;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for McpCliHandler with AI provider-specific tools.
 * These tests make real network calls to AI providers (Ollama, Bedrock, Gemini, etc.).
 * They require the respective services to be running and configured.
 *
 * Run these tests with: ./gradlew :dmtools-core:integrationTest
 *
 * NOTE: These tests are intentionally separated from unit tests because they:
 * - Make real network/API calls
 * - Require external services (Ollama on localhost:11434, AWS credentials for Bedrock, etc.)
 * - Are slow (may hang/timeout if services are unavailable)
 * - Should NOT run in regular CI/CD pipeline
 */
class McpCliHandlerProviderIntegrationTest {

    private McpCliHandler mcpCliHandler;

    @BeforeEach
    void setUp() {
        mcpCliHandler = new McpCliHandler();
    }

    @Test
    @DisplayName("Integration: Should not throw ClassCastException when calling provider-specific AI tools")
    void testProviderSpecificToolsNoClassCastException() {
        // Test provider-specific tools to ensure they don't cause ClassCastException
        // If the provider is configured, tool should work
        // If not configured, should get clear error (not ClassCastException)
        String[][] testCases = {
            {"mcp", "bedrock_ai_chat", "hello"},
            {"mcp", "ollama_ai_chat", "hello"}
        };

        for (String[] args : testCases) {
            String toolName = args[1];

            try {
                String result = mcpCliHandler.processMcpCommand(args);

                assertNotNull(result, "Result should not be null for " + toolName);
                assertFalse(result.isEmpty(), "Result should not be empty for " + toolName);

                // Parse as JSON to verify it's well-formed
                JSONObject response = new JSONObject(result);
                assertNotNull(response, "Response should be valid JSON for " + toolName);

                // The critical assertion: NO ClassCastException should occur
                String responseStr = result.toLowerCase();
                assertFalse(responseStr.contains("classcastexception"),
                    "Tool " + toolName + " should NOT throw ClassCastException. Response: " + result);
                assertFalse(responseStr.contains("cannot be cast"),
                    "Tool " + toolName + " should NOT have casting error. Response: " + result);

                // If it's an error response, verify it's a proper error (connection errors are OK)
                if (response.has("error") && response.getBoolean("error")) {
                    String message = response.getString("message");
                    // Error message should be informative, not a stack trace
                    assertTrue(message.length() > 10,
                        "Error message should be informative for " + toolName);

                    // Connection errors are acceptable (service not running in test environment)
                    boolean isConnectionError = message.toLowerCase().contains("connection") ||
                                               message.toLowerCase().contains("connect") ||
                                               message.toLowerCase().contains("network") ||
                                               message.toLowerCase().contains("refused");

                    // If it's a connection error, test passes (no ClassCastException occurred)
                    if (isConnectionError) {
                        // This is expected when Ollama/Bedrock is not running
                        System.out.println("INFO: Connection error is acceptable in integration tests for " + toolName);
                    }
                }
            } catch (Exception e) {
                // Catch any runtime exceptions to verify they're not ClassCastException
                String exceptionClass = e.getClass().getSimpleName();
                assertFalse("ClassCastException".equals(exceptionClass),
                    "Should NOT throw ClassCastException for " + toolName + ". Got: " + exceptionClass);

                // Connection errors are acceptable in integration tests
                boolean isConnectionError = e.getMessage() != null &&
                    (e.getMessage().toLowerCase().contains("connection") ||
                     e.getMessage().toLowerCase().contains("connect") ||
                     e.getMessage().toLowerCase().contains("refused"));

                if (isConnectionError) {
                    // Connection errors are OK - test passes
                    System.out.println("INFO: Connection error is acceptable in integration tests for " + toolName);
                } else {
                    // If it's not a connection error, rethrow to fail the test
                    throw e;
                }
            }
        }
    }

    @Test
    @DisplayName("Integration: Should provide clear error messages for misconfigured AI providers")
    void testProviderSpecificToolErrorMessages() {
        // Test that when a provider-specific tool can't be executed,
        // the error message is clear and helpful (not a technical exception)
        String[] args = {"mcp", "gemini_ai_chat", "test"};

        try {
            String result = mcpCliHandler.processMcpCommand(args);

            // Should not crash - should return a response
            assertNotNull(result);
            JSONObject response = new JSONObject(result);

            // If it's an error (provider not configured), message should be clear
            if (response.has("error") && response.getBoolean("error")) {
                String message = response.getString("message");

                // Connection errors are acceptable in integration tests
                boolean isConnectionError = message.toLowerCase().contains("connection") ||
                                           message.toLowerCase().contains("connect") ||
                                           message.toLowerCase().contains("refused");

                if (!isConnectionError) {
                    // Should NOT contain Java exception class names for non-connection errors
                    assertFalse(message.contains("java.lang."),
                        "Error message should not contain Java exception names. Message: " + message);
                }

                // Error message should be informative
                assertTrue(message.length() > 10,
                    "Error message should be informative");
            }
            // If it succeeds, that's also fine (provider is configured in test environment)
        } catch (Exception e) {
            // Connection errors are acceptable in integration tests - test passes
            boolean isConnectionError = e.getMessage() != null &&
                (e.getMessage().toLowerCase().contains("connection") ||
                 e.getMessage().toLowerCase().contains("connect") ||
                 e.getMessage().toLowerCase().contains("refused"));

            if (isConnectionError) {
                // Connection error - test passes (external service not available is OK)
                System.out.println("INFO: Connection error is acceptable - external service not available");
            } else {
                // Rethrow if it's not a connection error
                throw e;
            }
        }
    }
}
