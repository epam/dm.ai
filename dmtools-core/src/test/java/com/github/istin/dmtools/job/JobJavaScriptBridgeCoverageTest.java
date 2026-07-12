// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.xray.XrayClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.kb.tool.KBTools;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import org.graalvm.polyglot.Value;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Additional coverage tests for JobJavaScriptBridge targeting constructor branches,
 * executeToolFromJS argument/result conversion paths, setEnvVariable, resource/module
 * loading, proxy argument validation and cleanup.
 */
class JobJavaScriptBridgeCoverageTest {

    @Mock
    private TrackerClient<?> mockTrackerClient;

    @Mock
    private AI mockAI;

    @Mock
    private Confluence mockConfluence;

    @Mock
    private SourceCode mockSourceCode;

    @Mock
    private KBTools mockKBTools;

    private JobJavaScriptBridge bridge;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bridge = new JobJavaScriptBridge(mockTrackerClient, mockAI, mockConfluence, mockSourceCode, mockKBTools);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> clientInstances(JobJavaScriptBridge b) throws Exception {
        Field f = JobJavaScriptBridge.class.getDeclaredField("clientInstances");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(b);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> propertyOverrides(JobJavaScriptBridge b) throws Exception {
        Field f = JobJavaScriptBridge.class.getDeclaredField("propertyOverrides");
        f.setAccessible(true);
        return (Map<String, String>) f.get(b);
    }

    @Test
    void constructorRegistersCoreClientInstances() throws Exception {
        Map<String, Object> clients = clientInstances(bridge);

        assertSame(mockTrackerClient, clients.get("jira"));
        assertSame(mockAI, clients.get("ai"));
        assertSame(mockConfluence, clients.get("confluence"));
        assertSame(mockKBTools, clients.get("kb"));
        assertNotNull(clients.get("file"));
        assertNotNull(clients.get("cli"));
        assertFalse(clients.containsKey("jira_xray"));
    }

    @Test
    void constructorRegistersXrayTrackerAsJiraXray() throws Exception {
        XrayClient xrayClient = mock(XrayClient.class);
        JobJavaScriptBridge xrayBridge = new JobJavaScriptBridge(xrayClient, mockAI, mockConfluence, mockSourceCode, mockKBTools);

        Map<String, Object> clients = clientInstances(xrayBridge);
        assertSame(xrayClient, clients.get("jira"));
        assertSame(xrayClient, clients.get("jira_xray"));
    }

    @Test
    void setEnvVariableRejectsInvalidEnvVarName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bridge.setEnvVariable("SOME_PROP", "not-valid-name!"));
        assertTrue(e.getMessage().contains("Invalid environment variable name"));
    }

    @Test
    void setEnvVariableRejectsUnsetEnvVar() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bridge.setEnvVariable("SOME_PROP", "DMTOOLS_DEFINITELY_UNSET_VAR_XYZ123"));
        assertTrue(e.getMessage().contains("is not set or is empty"));
    }

    @Test
    void setEnvVariableStoresOverrideForKnownEnvVar() throws Exception {
        String pathValue = System.getenv("PATH");
        assumeTrue(pathValue != null && !pathValue.isEmpty(), "PATH env var required for this test");

        bridge.setEnvVariable("SOME_CUSTOM_PROP", "PATH");

        assertEquals(pathValue, propertyOverrides(bridge).get("SOME_CUSTOM_PROP"));
    }

    @Test
    void setEnvVariableRefreshesGithubClientForGithubTokenProperty() throws Exception {
        String pathValue = System.getenv("PATH");
        assumeTrue(pathValue != null && !pathValue.isEmpty(), "PATH env var required for this test");

        BasicGithub refreshedGithub = mock(BasicGithub.class);
        try (MockedStatic<BasicGithub> githubMock = mockStatic(BasicGithub.class)) {
            githubMock.when(() -> BasicGithub.createWithToken(pathValue)).thenReturn(refreshedGithub);

            bridge.setEnvVariable("SOURCE_GITHUB_TOKEN", "PATH");

            githubMock.verify(() -> BasicGithub.createWithToken(pathValue));
            assertSame(refreshedGithub, clientInstances(bridge).get("github"));
        }
    }

    @Test
    void executeToolFromJSWithNullArgsPassesEmptyMap() {
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenAnswer(inv -> {
                        capturedArgs.set(inv.getArgument(1));
                        return "tool-result";
                    });

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertEquals("tool-result", result);
            assertNotNull(capturedArgs.get());
            assertTrue(capturedArgs.get().isEmpty());
        }
    }

    @Test
    void executeToolFromJSConvertsHostMapArguments() {
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenAnswer(inv -> {
                        capturedArgs.set(inv.getArgument(1));
                        return "ok";
                    });

            Map<String, Object> jsArgs = new HashMap<>();
            jsArgs.put("plain", "value");
            jsArgs.put("jsonArray", new JSONArray().put("a").put("b"));
            jsArgs.put("arrayString", "[1, 2]");
            jsArgs.put("invalidArrayString", "[\"unclosed]");

            bridge.executeToolFromJS("some_tool", jsArgs);

            Map<String, Object> args = capturedArgs.get();
            assertNotNull(args);
            assertEquals("value", args.get("plain"));

            // JSONArray values are converted to List and, since "jsonArray" is not an
            // array parameter for this unknown tool, reconstructed back to a JSON string
            Object jsonArrayArg = args.get("jsonArray");
            assertInstanceOf(String.class, jsonArrayArg);
            JSONArray converted = new JSONArray((String) jsonArrayArg);
            assertEquals(List.of("a", "b"),
                    List.of(converted.getString(0), converted.getString(1)));

            // JSON array strings are parsed to List and reconstructed faithfully
            Object arrayStringArg = args.get("arrayString");
            assertInstanceOf(String.class, arrayStringArg);
            JSONArray parsed = new JSONArray((String) arrayStringArg);
            assertEquals(2, parsed.length());
            assertEquals(1, parsed.getInt(0));
            assertEquals(2, parsed.getInt(1));

            // Invalid JSON array string (looks like an array but does not parse) must be kept as-is
            assertEquals("[\"unclosed]", args.get("invalidArrayString"));
        }
    }

    @Test
    void executeToolFromJSConvertsArrayParamsToStringArrayUsingFallbackSchema() {
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenAnswer(inv -> {
                        capturedArgs.set(inv.getArgument(1));
                        return "ok";
                    });

            Map<String, Object> jsArgs = new HashMap<>();
            jsArgs.put("fields", new JSONArray().put("summary").put("status"));
            jsArgs.put("ids", new JSONArray().put("1").put("2"));

            bridge.executeToolFromJS("unknown_tool_no_schema", jsArgs);

            Map<String, Object> args = capturedArgs.get();
            assertNotNull(args);

            Object fields = args.get("fields");
            assertInstanceOf(String[].class, fields);
            assertArrayEquals(new String[]{"summary", "status"}, (String[]) fields);

            Object ids = args.get("ids");
            assertInstanceOf(String[].class, ids);
            assertArrayEquals(new String[]{"1", "2"}, (String[]) ids);
        }
    }

    @Test
    void executeToolFromJSReconstructsJsonStringForNonArrayParamWithoutSchema() {
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenAnswer(inv -> {
                        capturedArgs.set(inv.getArgument(1));
                        return "ok";
                    });

            Map<String, Object> jsArgs = new HashMap<>();
            jsArgs.put("content", new JSONArray().put("x").put(1).put(true));

            bridge.executeToolFromJS("unknown_tool_no_schema", jsArgs);

            Object content = capturedArgs.get().get("content");
            assertInstanceOf(String.class, content);
            JSONArray reconstructed = new JSONArray((String) content);
            assertEquals(3, reconstructed.length());
            assertEquals("x", reconstructed.getString(0));
            assertEquals(1, reconstructed.getInt(1));
            assertTrue(reconstructed.getBoolean(2));
        }
    }

    @Test
    void executeToolFromJSReconstructsJsonStringForNonArrayParamWithSchema() {
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenAnswer(inv -> {
                        capturedArgs.set(inv.getArgument(1));
                        return "ok";
                    });

            Map<String, Object> jsArgs = new HashMap<>();
            jsArgs.put("comment", new JSONArray().put("line1").put("line2"));

            // jira_post_comment has a real schema where "comment" is a string parameter
            bridge.executeToolFromJS("jira_post_comment", jsArgs);

            Object comment = capturedArgs.get().get("comment");
            assertInstanceOf(String.class, comment);
            JSONArray reconstructed = new JSONArray((String) comment);
            assertEquals("line1", reconstructed.getString(0));
            assertEquals("line2", reconstructed.getString(1));
        }
    }

    @Test
    void executeToolFromJSKeepsMermaidPatternParamsAsList() {
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenAnswer(inv -> {
                        capturedArgs.set(inv.getArgument(1));
                        return "ok";
                    });

            Map<String, Object> jsArgs = new HashMap<>();
            jsArgs.put("include_patterns", new JSONArray().put("src/**"));
            jsArgs.put("exclude_patterns", new JSONArray().put("build/**"));

            bridge.executeToolFromJS("mermaid_index_generate", jsArgs);

            Map<String, Object> args = capturedArgs.get();
            assertNotNull(args);

            Object includePatterns = args.get("include_patterns");
            assertInstanceOf(List.class, includePatterns);
            assertEquals(List.of("src/**"), includePatterns);

            Object excludePatterns = args.get("exclude_patterns");
            assertInstanceOf(List.class, excludePatterns);
            assertEquals(List.of("build/**"), excludePatterns);
        }
    }

    @Test
    void executeToolFromJSReturnsNullAndPrimitivesUnchanged() {
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(null);
            assertNull(bridge.executeToolFromJS("some_tool", null));
        }

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(42);
            assertEquals(42, bridge.executeToolFromJS("some_tool", null));
        }

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(Boolean.TRUE);
            assertEquals(Boolean.TRUE, bridge.executeToolFromJS("some_tool", null));
        }
    }

    @Test
    void executeToolFromJSConvertsMapResultToJSObject() {
        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("key", "TEST-1");
        toolResult.put("nested", Map.of("name", "inner"));
        toolResult.put("list", List.of("a", "b"));

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(toolResult);

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            Value value = (Value) result;
            assertTrue(value.hasMembers());
            assertEquals("TEST-1", value.getMember("key").asString());
            assertEquals("inner", value.getMember("nested").getMember("name").asString());
            assertEquals("a", value.getMember("list").getArrayElement(0).asString());
        }
    }

    @Test
    void executeToolFromJSConvertsListResultToJSArray() {
        List<Object> toolResult = new ArrayList<>();
        toolResult.add("first");
        toolResult.add(Map.of("k", "v"));

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(toolResult);

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            Value value = (Value) result;
            assertTrue(value.hasArrayElements());
            assertEquals(2, value.getArraySize());
            assertEquals("first", value.getArrayElement(0).asString());
            assertEquals("v", value.getArrayElement(1).getMember("k").asString());
        }
    }

    @Test
    void executeToolFromJSConvertsJSONObjectAndJSONArrayResults() {
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(new JSONObject().put("status", "done"));

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            assertEquals("done", ((Value) result).getMember("status").asString());
        }

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(new JSONArray().put(1).put(2));

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            Value value = (Value) result;
            assertTrue(value.hasArrayElements());
            assertEquals(2, value.getArraySize());
        }
    }

    @Test
    void executeToolFromJSConvertsDomainObjectWithJsonToString() {
        Object jsonLike = new Object() {
            @Override
            public String toString() {
                return "{\"ticket\":\"ABC-1\"}";
            }
        };

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(jsonLike);

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            assertEquals("ABC-1", ((Value) result).getMember("ticket").asString());
        }
    }

    @Test
    void executeToolFromJSQuotesDomainObjectWithPlainToString() {
        Object plain = new Object() {
            @Override
            public String toString() {
                return "plain text value";
            }
        };

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(plain);

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            assertEquals("plain text value", ((Value) result).asString());
        }
    }

    @Test
    void executeToolFromJSQuotesDomainObjectWithBrokenJsonToString() {
        Object brokenJson = new Object() {
            @Override
            public String toString() {
                return "{not valid json";
            }
        };

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenReturn(brokenJson);

            Object result = bridge.executeToolFromJS("some_tool", null);

            assertInstanceOf(Value.class, result);
            assertEquals("{not valid json", ((Value) result).asString());
        }
    }

    @Test
    void executeToolFromJSWrapsExecutorFailure() {
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(anyString(), any(Map.class), any(Map.class)))
                    .thenThrow(new IllegalStateException("boom"));

            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> bridge.executeToolFromJS("some_tool", null));
            assertTrue(e.getMessage().contains("Tool execution failed"));
            assertTrue(e.getMessage().contains("boom"));
        }
    }

    @Test
    void executeJavaScriptRequiresActionFunction() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript("var x = 1;", new JSONObject()));
        assertTrue(e.getMessage().contains("must define an 'action' function"));
    }

    @Test
    void executeJavaScriptTruncatesLongSourceInErrorLog() {
        String longFailingJs = "function action(params) { /* " + "padding ".repeat(30)
                + "*/ return callToUndefinedFunction(); }";
        assertTrue(longFailingJs.length() > 100);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript(longFailingJs, new JSONObject()));
        assertTrue(e.getMessage().contains("JavaScript execution failed"));
    }

    @Test
    void executeJavaScriptConvertsPrimitiveResults() throws Exception {
        assertEquals(42.0, bridge.executeJavaScript(
                "function action(params) { return 42; }", new JSONObject()));
        assertEquals(Boolean.TRUE, bridge.executeJavaScript(
                "function action(params) { return true; }", new JSONObject()));
        assertNull(bridge.executeJavaScript(
                "function action(params) { return null; }", new JSONObject()));
    }

    @Test
    void executeJavaScriptConvertsArrayResultToJSONArray() throws Exception {
        Object result = bridge.executeJavaScript(
                "function action(params) { return [1, 'two', {three: 3}]; }", new JSONObject());

        assertInstanceOf(JSONArray.class, result);
        JSONArray array = (JSONArray) result;
        assertEquals(3, array.length());
        assertEquals(3, array.getJSONObject(2).getInt("three"));
    }

    @Test
    void executeJavaScriptConvertsObjectResultToJSONObject() throws Exception {
        Object result = bridge.executeJavaScript(
                "function action(params) { return {name: 'obj', tags: ['a', 'b']}; }", new JSONObject());

        assertInstanceOf(JSONObject.class, result);
        JSONObject object = (JSONObject) result;
        assertEquals("obj", object.getString("name"));
        assertEquals(2, object.getJSONArray("tags").length());
    }

    @Test
    void executeJavaScriptLoadsScriptFromFilesystem(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("script.js");
        Files.writeString(script, "function action(params) { return 'from-file ' + params.tag; }",
                StandardCharsets.UTF_8);

        Object result = bridge.executeJavaScript(script.toString(),
                new JSONObject().put("tag", "ok"));

        assertEquals("from-file ok", result.toString());
    }

    @Test
    void executeJavaScriptThrowsForMissingResourceAndFile() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript("no/such/script.js", new JSONObject()));
        assertTrue(e.getMessage().contains("JavaScript file not found"));
    }

    @Test
    void executeJavaScriptFromUrlFailsWithoutSourceCode() {
        JobJavaScriptBridge noSourceBridge = new JobJavaScriptBridge(
                mockTrackerClient, mockAI, mockConfluence, null, mockKBTools);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> noSourceBridge.executeJavaScript("https://example.com/script.js", new JSONObject()));
        assertTrue(e.getMessage().contains("SourceCode not configured"));
    }

    @Test
    void executeToolViaJavaProxyRequiresToolName() {
        String js = "function action(params) { return executeToolViaJava(); }";

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript(js, new JSONObject()));
        assertTrue(e.getMessage().contains("requires at least 1 argument"));
    }

    @Test
    void setEnvVariableProxyRequiresTwoArguments() {
        String js = "function action(params) { set_env_variable('ONLY_ONE'); return 'unreached'; }";

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript(js, new JSONObject()));
        assertTrue(e.getMessage().contains("requires 2 arguments"));
    }

    @Test
    void requireProxyRequiresExactlyOneArgument() {
        String js = "function action(params) { return require(); }";

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript(js, new JSONObject()));
        assertTrue(e.getMessage().contains("exactly one argument"));
    }

    @Test
    void requireLoadsModuleFromFilesystemRelativeToScript(@TempDir Path tempDir) throws Exception {
        Path module = tempDir.resolve("util.js");
        Files.writeString(module,
                "exports.greet = function(name) { return 'hi ' + name; };",
                StandardCharsets.UTF_8);
        Path main = tempDir.resolve("main.js");
        Files.writeString(main,
                "var util = require('./util.js');\n"
                        + "function action(params) { return util.greet(params.name); }",
                StandardCharsets.UTF_8);

        Object result = bridge.executeJavaScript(main.toString(),
                new JSONObject().put("name", "module"));

        assertEquals("hi module", result.toString());
    }

    @Test
    void requireCachesModules(@TempDir Path tempDir) throws Exception {
        Path module = tempDir.resolve("counter.js");
        Files.writeString(module,
                "globalThis.__loadCount = (globalThis.__loadCount || 0) + 1;\n"
                        + "exports.count = globalThis.__loadCount;",
                StandardCharsets.UTF_8);
        Path main = tempDir.resolve("main.js");
        Files.writeString(main,
                "var first = require('./counter.js');\n"
                        + "var second = require('./counter.js');\n"
                        + "function action(params) { return first.count + ',' + second.count; }",
                StandardCharsets.UTF_8);

        Object result = bridge.executeJavaScript(main.toString(), new JSONObject());

        assertEquals("1,1", result.toString());
    }

    @Test
    void requireFailsForMissingModule(@TempDir Path tempDir) throws Exception {
        Path main = tempDir.resolve("main.js");
        Files.writeString(main,
                "var missing = require('./does-not-exist.js');\n"
                        + "function action(params) { return 'unreached'; }",
                StandardCharsets.UTF_8);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeJavaScript(main.toString(), new JSONObject()));
        assertTrue(e.getMessage().contains("JavaScript file not found")
                || e.getMessage().contains("Failed to require module"));
    }

    @Test
    void closeReleasesContextAndModuleCache(@TempDir Path tempDir) throws Exception {
        // Initialize the context
        bridge.executeJavaScript("function action(params) { return 1; }", new JSONObject());

        bridge.close();
        // Closing twice must be a no-op
        bridge.close();

        Field moduleCacheField = JobJavaScriptBridge.class.getDeclaredField("moduleCache");
        moduleCacheField.setAccessible(true);
        assertTrue(((Map<?, ?>) moduleCacheField.get(bridge)).isEmpty());

        Field jsContextField = JobJavaScriptBridge.class.getDeclaredField("jsContext");
        jsContextField.setAccessible(true);
        assertNotNull(jsContextField.get(bridge));
    }

    @Test
    void convertJSONObjectToMapHandlesNestedStructuresViaReflection() throws Exception {
        Method method = JobJavaScriptBridge.class.getDeclaredMethod("convertJSONObjectToMap", JSONObject.class);
        method.setAccessible(true);

        JSONObject input = new JSONObject();
        input.put("name", "root");
        input.put("child", new JSONObject().put("name", "leaf"));
        input.put("items", new JSONArray()
                .put("plain")
                .put(new JSONObject().put("k", "v"))
                .put(new JSONArray().put(1).put(2)));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(bridge, input);

        assertEquals("root", result.get("name"));
        assertInstanceOf(Map.class, result.get("child"));
        assertEquals("leaf", ((Map<?, ?>) result.get("child")).get("name"));

        List<?> items = (List<?>) result.get("items");
        assertEquals(3, items.size());
        assertEquals("plain", items.get(0));
        assertInstanceOf(Map.class, items.get(1));
        assertInstanceOf(List.class, items.get(2));
    }
}
