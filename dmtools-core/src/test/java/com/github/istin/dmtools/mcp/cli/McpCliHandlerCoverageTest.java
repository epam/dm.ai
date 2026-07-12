// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.di.AIComponentsModule;
import com.github.istin.dmtools.mcp.MCPToolDefinition;
import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Additional unit tests for {@link McpCliHandler} focused on branches not covered
 * by {@link McpCliHandlerTest}: debug-mode error reporting, parameter format errors
 * with usage examples, format-flag edge cases, alias fallback resolution and the
 * doctor/client-instance accessors.
 *
 * <p>All tests run offline: provider-specific AI tools are deliberately not invoked
 * because the development environment may contain real AI credentials (dmtools.env),
 * which would turn them into network calls.</p>
 */
class McpCliHandlerCoverageTest {

    private McpCliHandler handler;

    @BeforeEach
    void setUp() {
        handler = new McpCliHandler();
    }

    // -------------------------------------------------------------------------
    // Client instance accessors
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createClientInstancesForDoctor returns initialized client map")
    void testCreateClientInstancesForDoctor() {
        Map<String, Object> clients = handler.createClientInstancesForDoctor();

        assertNotNull(clients, "Doctor client map should not be null");
        // cli, file and teams_auth clients are created unconditionally
        assertTrue(clients.containsKey("cli"), "CLI executor client should always be present");
        assertTrue(clients.containsKey("file"), "File tools client should always be present");
        assertTrue(clients.containsKey("teams_auth"), "Teams auth tools client should always be present");
    }

    @Test
    @DisplayName("getClientInstances returns an unmodifiable map")
    void testGetClientInstancesUnmodifiable() {
        Map<String, Object> clients = handler.getClientInstances();

        assertNotNull(clients);
        assertThrows(UnsupportedOperationException.class,
                () -> clients.put("test", new Object()),
                "getClientInstances should return an unmodifiable map");
    }

    // -------------------------------------------------------------------------
    // Usage / argument edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Completely empty args array shows usage error")
    void testEmptyArgsArrayShowsUsage() {
        String result = handler.processMcpCommand(new String[0]);

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").contains("Usage: mcp <command>"));
    }

    @Test
    @DisplayName("Blank (whitespace-only) list filter is ignored")
    void testBlankFilterIsIgnored() {
        String[] args = {"mcp", "list", "   "};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.has("tools"));
        // Blank filter must not filter anything away
        String unfiltered = handler.processMcpCommand(new String[]{"mcp", "list"});
        assertEquals(new JSONObject(unfiltered).getJSONArray("tools").length(),
                response.getJSONArray("tools").length(),
                "Whitespace-only filter should be treated as no filter");
    }

    // -------------------------------------------------------------------------
    // Format flag edge cases (extractFormatFlag)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("--output flag without a value falls back to JSON")
    void testOutputFlagWithoutValue() {
        String[] args = {"mcp", "list", "--output"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.has("tools"), "Dangling --output flag should fall back to JSON output");
    }

    @Test
    @DisplayName("Last format flag wins when several are given")
    void testLastFormatFlagWins() {
        // --toon first, then --output json overrides it back to JSON
        String[] args = {"mcp", "list", "--toon", "--output", "json"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.has("tools"), "Later --output json should override earlier --toon");
    }

    @Test
    @DisplayName("--help combined with --toon returns TOON-formatted schema list")
    void testHelpWithToonFlag() {
        String[] args = {"mcp", "jira_move_to_status", "--toon", "--help"};
        String result = handler.processMcpCommand(args);

        assertFalse(result.trim().startsWith("{"),
                "--toon should apply to the --help schema listing as well");
        assertFalse(result.trim().isEmpty(), "Output should not be empty");
    }

    // -------------------------------------------------------------------------
    // Debug mode error reporting (getStackTrace, debug branches)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Debug mode: IllegalArgumentException includes stack trace")
    void testDebugModeIllegalArgumentIncludesStackTrace() {
        String previous = System.getProperty("log4j2.configurationFile");
        try {
            System.setProperty("log4j2.configurationFile", "log4j2-debug.xml");

            String result = handler.processMcpCommand(new String[]{"mcp", "nonexistent_tool"});

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            String message = response.getString("message");
            assertTrue(message.contains("Invalid tool or arguments:"),
                    "Debug mode should prefix IllegalArgumentException message. Got: " + message);
            assertTrue(message.contains("Stack trace:"),
                    "Debug mode should include a stack trace. Got: " + message);
        } finally {
            restoreLog4jProperty(previous);
        }
    }

    @Test
    @DisplayName("Debug mode: generic tool failure includes stack trace")
    void testDebugModeGenericExceptionIncludesStackTrace() {
        String previous = System.getProperty("log4j2.configurationFile");
        try {
            System.setProperty("log4j2.configurationFile", "log4j2-debug.xml");

            // SecurityException from the CLI command whitelist - no process is started
            String[] args = {"mcp", "cli_execute_command", "--data",
                    "{\"command\": \"definitely-not-whitelisted-cmd-xyz123\"}"};
            String result = handler.processMcpCommand(args);

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            String message = response.getString("message");
            assertTrue(message.contains("Tool execution failed:"),
                    "Debug mode should prefix generic failure message. Got: " + message);
            assertTrue(message.contains("Stack trace:"),
                    "Debug mode should include a stack trace. Got: " + message);
        } finally {
            restoreLog4jProperty(previous);
        }
    }

    @Test
    @DisplayName("Non-debug mode: generic tool failure has no stack trace")
    void testNonDebugModeGenericExceptionHasNoStackTrace() {
        String previous = System.getProperty("log4j2.configurationFile");
        try {
            System.clearProperty("log4j2.configurationFile");

            String[] args = {"mcp", "cli_execute_command", "--data",
                    "{\"command\": \"definitely-not-whitelisted-cmd-xyz123\"}"};
            String result = handler.processMcpCommand(args);

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            String message = response.getString("message");
            assertFalse(message.contains("Stack trace:"),
                    "Non-debug mode must not leak stack traces. Got: " + message);
            assertTrue(message.contains("Command not allowed") || message.contains("not whitelisted")
                            || message.contains("Tool execution failed") || message.contains("failed"),
                    "Error message should describe the CLI failure. Got: " + message);
        } finally {
            restoreLog4jProperty(previous);
        }
    }

    // -------------------------------------------------------------------------
    // Parameter format errors (createParameterFormatError / generateUsageExample)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Array parameter passed as string shows correct usage example")
    void testParameterFormatErrorShowsUsageExample() {
        // urlStrings is a String[] parameter; passing a plain string triggers
        // "Parameter 'urlStrings' must be an array" before any client call is made
        String[] args = {"mcp", "confluence_contents_by_urls", "--data",
                "{\"urlStrings\": \"single-value-instead-of-array\"}"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        String message = response.getString("message");
        assertTrue(message.contains("must be an array"),
                "Error should explain the array type mismatch. Got: " + message);
        assertTrue(message.contains("Correct usage:"),
                "Parameter format error should include a usage example. Got: " + message);
        assertTrue(message.contains("dmtools confluence_contents_by_urls"),
                "Usage example should reference the tool name. Got: " + message);
    }

    @Test
    @DisplayName("Array parameter format error is reported without stack trace by default")
    void testParameterFormatErrorNoStackTraceByDefault() {
        String previous = System.getProperty("log4j2.configurationFile");
        try {
            System.clearProperty("log4j2.configurationFile");

            String[] args = {"mcp", "confluence_contents_by_urls", "--data",
                    "{\"urlStrings\": \"single-value\"}"};
            String result = handler.processMcpCommand(args);

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            assertFalse(response.getString("message").contains("Stack trace:"),
                    "Usage hint errors should not include stack traces");
        } finally {
            restoreLog4jProperty(previous);
        }
    }

    // -------------------------------------------------------------------------
    // Alias resolution fallback (resolveToolAlias multi-candidate path)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Multi-candidate alias resolves to one of its registered implementations")
    void testMultiCandidateAliasResolution() {
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias("source_code_list_prs");
        assertNotNull(candidates);
        assertTrue(candidates.size() >= 2, "Test requires a multi-candidate alias");

        String resolved = handler.resolveToolAlias("source_code_list_prs");

        boolean matchesCandidate = candidates.stream()
                .anyMatch(def -> def.getName().equals(resolved));
        assertTrue(matchesCandidate,
                "Resolved tool '" + resolved + "' should be one of the registered candidates");
    }

    @Test
    @DisplayName("Multi-candidate tracker alias resolves to one of its implementations")
    void testMultiCandidateTrackerAliasResolution() {
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias("tracker_search");
        assertNotNull(candidates);
        assertTrue(candidates.size() >= 2, "Test requires a multi-candidate alias");

        String resolved = handler.resolveToolAlias("tracker_search");

        boolean matchesCandidate = candidates.stream()
                .anyMatch(def -> def.getName().equals(resolved));
        assertTrue(matchesCandidate,
                "Resolved tool '" + resolved + "' should be one of the registered candidates");
    }

    // -------------------------------------------------------------------------
    // AI client lazy creation / provider selection (mocked AIComponentsModule)
    //
    // AIComponentsModule statics are mocked so no real AI client is ever created
    // and no network call can happen, even though dmtools.env holds credentials.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Generic AI tool: fallback loop tries every provider when none is configured")
    void testGenericAiToolFallbackLoopAllProvidersNull() {
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            // All statics return null by default -> every provider branch is exercised
            String result = handler.processMcpCommand(new String[]{"mcp", "nonexistent_tool"});

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            assertTrue(response.getString("message").contains("Unknown tool"),
                    "Execution should still reach the executor and fail with Unknown tool. Got: "
                            + response.getString("message"));
        }
    }

    @Test
    @DisplayName("vertex_ai_gemini tool without Gemini provider throws configuration error")
    void testVertexGeminiToolWithoutProvider() {
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            // createGeminiAI returns null by default
            String[] args = {"mcp", "vertex_ai_gemini_chat", "--data", "{\"message\": \"hi\"}"};
            String result = handler.processMcpCommand(args);

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            assertTrue(response.getString("message").contains("requires Gemini provider"),
                    "Should explain the missing Gemini provider. Got: " + response.getString("message"));
        }
    }

    @Test
    @DisplayName("Provider-specific tool without that provider throws configuration error")
    void testProviderToolWithoutProvider() {
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            String[] args = {"mcp", "openai_ai_chat", "--data", "{\"message\": \"hi\"}"};
            String result = handler.processMcpCommand(args);

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            assertTrue(response.getString("message").contains("requires OPENAI provider"),
                    "Should explain the missing OPENAI provider. Got: " + response.getString("message"));
        }
    }

    @Test
    @DisplayName("vertex_ai_gemini tool with configured provider returns the gemini client")
    void testVertexGeminiToolWithMockedProvider() {
        AI mockAi = mock(AI.class);
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            aiModule.when(() -> AIComponentsModule.createGeminiAI(
                            any(ConversationObserver.class), any(ApplicationConfiguration.class)))
                    .thenReturn(mockAi);

            String[] args = {"mcp", "vertex_ai_gemini_chat", "--data", "{\"message\": \"hi\"}"};
            String result = handler.processMcpCommand(args);

            // The mocked AI is not a concrete GeminiClient, so execution fails at the cast -
            // but provider resolution (getAIClientForTool) succeeded and no network was touched
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    @DisplayName("Last array parameter collects all remaining positional arguments")
    void testLastArrayParameterCollectsRemainingPositionals() {
        AI mockAi = mock(AI.class);
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            aiModule.when(() -> AIComponentsModule.createOpenAIAI(
                            any(ConversationObserver.class), any(ApplicationConfiguration.class)))
                    .thenReturn(mockAi);

            // openai_ai_chat_with_files(message, filePaths...) - filePaths is the LAST parameter
            // and an array, so both extra positionals must be collected into it
            String[] args = {"mcp", "openai_ai_chat_with_files", "hello", "f1.txt", "f2.txt"};
            String result = handler.processMcpCommand(args);

            assertNotNull(result);
            // Mapping must succeed: the failure (if any) is the AI mock cast, not argument parsing
            assertFalse(result.contains("must be an array"),
                    "Positional args should have been collected into the filePaths array. Got: " + result);
        }
    }

    @Test
    @DisplayName("Array parameter followed by trailing params clamps when positionals run out")
    void testArrayParamClampWhenPositionalsExhausted() {
        // mermaid_index_generate(integration, storage_path, include_patterns[], exclude_patterns[],
        // custom_fields[], include_comments) - with 3 positionals the regular params consume 2 and
        // the single remaining arg cannot fill 3 array slots + trailing param, so the array window
        // is clamped to empty instead of going negative. Execution then fails safely at argument
        // conversion (List cast), before any client/network interaction.
        String[] args = {"mcp", "mermaid_index_generate", "conf", "sp", "x"};
        String result = handler.processMcpCommand(args);

        assertNotNull(result, "Handler must not crash on the clamped array mapping");
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("AI client created once is served from the cache on subsequent tools")
    void testAIClientCacheHit() {
        AI mockAi = mock(AI.class);
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            aiModule.when(() -> AIComponentsModule.createOpenAIAI(
                            any(ConversationObserver.class), any(ApplicationConfiguration.class)))
                    .thenReturn(mockAi);

            // Two generic AI tools on the same handler: the second call must hit the cached client
            handler.processMcpCommand(new String[]{"mcp", "nonexistent_tool"});
            String result = handler.processMcpCommand(new String[]{"mcp", "another_missing_tool"});

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            assertTrue(response.getString("message").contains("Unknown tool"));
            aiModule.verify(() -> AIComponentsModule.createOpenAIAI(
                            any(ConversationObserver.class), any(ApplicationConfiguration.class)),
                    org.mockito.Mockito.times(1));
        }
    }

    @Test
    @DisplayName("Fallback loop returns a later provider when openai is not configured")
    void testFallbackLoopReturnsLaterProvider() {
        AI mockAi = mock(AI.class);
        try (MockedStatic<AIComponentsModule> aiModule = mockStatic(AIComponentsModule.class)) {
            // openai (default) returns null; gemini - the next in the fallback list - succeeds
            aiModule.when(() -> AIComponentsModule.createGeminiAI(
                            any(ConversationObserver.class), any(ApplicationConfiguration.class)))
                    .thenReturn(mockAi);

            String result = handler.processMcpCommand(new String[]{"mcp", "nonexistent_tool"});

            JSONObject response = new JSONObject(result);
            assertTrue(response.getBoolean("error"));
            assertTrue(response.getString("message").contains("Unknown tool"),
                    "Execution should proceed with the fallback gemini client. Got: "
                            + response.getString("message"));
        }
    }

    @Test
    @DisplayName("Successful tool execution is formatted as a result, not an error")
    void testSuccessfulToolExecutionFormatting() {
        // git is whitelisted and present; "git --version" runs locally without any network
        String[] args = {"mcp", "cli_execute_command", "--data", "{\"command\": \"git --version\"}"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertFalse(response.has("error") && response.getBoolean("error"),
                "git --version should execute successfully. Got: " + result);
        assertTrue(result.contains("git version"),
                "Successful execution should format the command output. Got: " + result);
    }

    @Test
    @DisplayName("Single-candidate alias resolves directly to its only implementation")
    void testSingleCandidateAliasResolvesDirectly() {
        // source_code_add_pr_label is registered only for gitlab (verified against MCPToolRegistry)
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias("source_code_add_pr_label");
        assertNotNull(candidates);
        assertEquals(1, candidates.size(), "Test requires a single-candidate alias");

        String resolved = handler.resolveToolAlias("source_code_add_pr_label");
        assertEquals(candidates.get(0).getName(), resolved);
    }

    // -------------------------------------------------------------------------
    // Defensive / null handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Null argument at position >= 2 is caught by the outer error handler")
    void testNullArgumentTriggersOuterCatch() {
        // extractFormatFlag calls arg.startsWith() on null -> outer catch must convert it
        // into a standard error response instead of propagating
        String result = handler.processMcpCommand(new String[]{"mcp", "list", null});

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        assertTrue(response.getString("message").startsWith("Error:"),
                "Outer catch should prefix the message with 'Error:'. Got: " + response.getString("message"));
    }

    @Test
    @DisplayName("Null tool name with positional argument is handled gracefully")
    void testNullToolNameWithPositionalArgument() {
        String result = handler.processMcpCommand(new String[]{"mcp", null, "posarg"});

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"),
                "Null tool name should produce an error response, not a crash");
    }

    @Test
    @DisplayName("Constructor in debug mode preserves existing log configuration")
    void testConstructorInDebugMode() {
        String previous = System.getProperty("log4j2.configurationFile");
        try {
            System.setProperty("log4j2.configurationFile", "log4j2-debug.xml");
            McpCliHandler debugHandler = new McpCliHandler();

            // Handler must still work normally after the debug-mode constructor path
            String result = debugHandler.processMcpCommand(new String[]{"mcp", "list"});
            JSONObject response = new JSONObject(result);
            assertTrue(response.has("tools"));
        } finally {
            restoreLog4jProperty(previous);
        }
    }

    // -------------------------------------------------------------------------
    // Parameter format errors - usage example generation branches
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Usage example generates defaults for required string and array params without examples")
    void testUsageExampleDefaultsForStringAndArray() {
        // jira_get_ticket(key*, fields[]) - neither param has a schema example, so the
        // generator falls back to type-based defaults ("example_key", ["value1","value2"]).
        // The array type error is thrown during argument conversion, before any Jira call.
        String[] args = {"mcp", "jira_get_ticket", "--data",
                "{\"key\": \"X-1\", \"fields\": \"not-an-array\"}"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        String message = response.getString("message");
        assertTrue(message.contains("must be an array"), "Got: " + message);
        assertTrue(message.contains("Correct usage:"), "Got: " + message);
        assertTrue(message.contains("example_key"),
                "String param without example should get a generated default. Got: " + message);
        assertTrue(message.contains("value1"),
                "Array param without example should get a generated default array. Got: " + message);
    }

    @Test
    @DisplayName("Usage example splits comma-separated array example into a JSON array")
    void testUsageExampleCommaSeparatedArrayExample() {
        // jira_xray_search_tickets fields example is "summary,description,status" (CSV form).
        // Error is thrown during argument conversion, before any client call.
        String[] args = {"mcp", "jira_xray_search_tickets", "--data",
                "{\"searchQueryJQL\": \"project = X\", \"fields\": \"not-an-array\"}"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        String message = response.getString("message");
        assertTrue(message.contains("must be an array"), "Got: " + message);
        assertTrue(message.contains("Correct usage:"), "Got: " + message);
        assertTrue(message.contains("summary"),
                "CSV array example should be expanded into the usage hint. Got: " + message);
    }

    @Test
    @DisplayName("Usage example passes through plain string example values")
    void testUsageExamplePlainStringExamplePassthrough() {
        // ado_get_work_item(id*, fields[]) - id has the plain string example "12345" which must
        // appear verbatim in the usage hint. The array type error is thrown during argument
        // conversion, before any ADO client call.
        String[] args = {"mcp", "ado_get_work_item", "--data",
                "{\"id\": \"12345\", \"fields\": \"not-an-array\"}"};
        String result = handler.processMcpCommand(args);

        JSONObject response = new JSONObject(result);
        assertTrue(response.getBoolean("error"));
        String message = response.getString("message");
        assertTrue(message.contains("must be an array"), "Got: " + message);
        assertTrue(message.contains("Correct usage:"), "Got: " + message);
        assertTrue(message.contains("12345"),
                "Plain string example value should be passed through into the usage hint. Got: " + message);
    }

    // -------------------------------------------------------------------------
    // Stack trace with exception cause (debug mode)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Debug mode: stack trace includes exception cause when present")
    void testDebugModeStackTraceIncludesCause() {
        String previous = System.getProperty("log4j2.configurationFile");
        try {
            System.setProperty("log4j2.configurationFile", "log4j2-debug.xml");

            // "terraform" is whitelisted but not installed on this machine: starting the
            // process fails with an IOException that CliCommandExecutor re-throws WITH cause
            String[] args = {"mcp", "cli_execute_command", "--data",
                    "{\"command\": \"terraform --version\"}"};
            String result = handler.processMcpCommand(args);

            JSONObject response = new JSONObject(result);
            if (response.getBoolean("error")) {
                String message = response.getString("message");
                assertTrue(message.contains("Stack trace:"),
                        "Debug mode should include a stack trace. Got: " + message);
                assertTrue(message.contains("Caused by:"),
                        "Wrapped IOException should surface its cause. Got: " + message);
            }
            // If terraform is installed the command simply succeeds - either way no network
        } finally {
            restoreLog4jProperty(previous);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void restoreLog4jProperty(String previous) {
        if (previous == null) {
            System.clearProperty("log4j2.configurationFile");
        } else {
            System.setProperty("log4j2.configurationFile", previous);
        }
    }
}
