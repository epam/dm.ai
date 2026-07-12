// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.js;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.bridge.DMToolsBridge;
import com.github.istin.dmtools.common.networking.GenericRequest;
import freemarker.template.TemplateException;
import okhttp3.Request;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JSAIClient} covering constructor variants (inline script,
 * script path from filesystem, missing script, Freemarker secret substitution,
 * sanitized eval failures), all chat overloads, JSON parts creation with files,
 * role name resolution, request signing/hash building and DMToolsBridge HTTP
 * delegation. The real GraalJS engine evaluates small test scripts; the HTTP
 * layer is mocked via Mockito spies - no real network calls are made.
 */
public class JSAIClientCoverageTest {

    private static final String BASE_PATH = "https://js-ai.example.com/v1";
    private static final String DEFAULT_MODEL = "test-model";

    /** Returns the raw messages JSON passed to handleChat. */
    private static final String ECHO_SCRIPT =
            "function handleChat(messages, model, metadata, bridge) { return messages; }\n" +
            "function getRoleName() { return 'assistant'; }\n";

    /** Returns the model passed to handleChat. */
    private static final String MODEL_SCRIPT =
            "function handleChat(messages, model, metadata, bridge) { return String(model); }\n" +
            "function getRoleName() { return 'assistant'; }\n";

    /** Returns the metadata string passed to handleChat. */
    private static final String METADATA_SCRIPT =
            "function handleChat(messages, model, metadata, bridge) { return String(metadata); }\n";

    /** Returns null from handleChat. */
    private static final String NULL_SCRIPT =
            "function handleChat(messages, model, metadata, bridge) { return null; }\n";

    /** Calls back into Java through the bridge. */
    private static final String BRIDGE_SCRIPT =
            "function handleChat(messages, model, metadata, bridge) {\n" +
            "    bridge.jsLogInfo('handleChat invoked');\n" +
            "    return bridge.executePost('/chat/completions', '{\"q\":1}', null);\n" +
            "}\n";

    /**
     * Test subclass that stubs the HTTP layer. A Mockito spy cannot be used for the
     * bridge delegation tests because the bridge's HttpHandler captured the original
     * JSAIClient instance at construction time and bypasses the spy.
     */
    private static class StubbedClient extends JSAIClient {
        final List<GenericRequest> posts = new ArrayList<>();
        final List<GenericRequest> gets = new ArrayList<>();

        StubbedClient(JSONObject config) throws IOException, ScriptException, TemplateException {
            super(config, null);
        }

        @Override
        public String post(GenericRequest genericRequest) {
            posts.add(genericRequest);
            return "post-response";
        }

        @Override
        public String execute(GenericRequest genericRequest) {
            gets.add(genericRequest);
            return "get-response";
        }
    }

    private StubbedClient newStubbedClient(String script) throws IOException, ScriptException, TemplateException {
        JSONObject config = new JSONObject();
        config.put("clientName", "TestJSClient");
        config.put("basePath", BASE_PATH);
        config.put("defaultModel", DEFAULT_MODEL);
        config.put("jsScript", script);
        return new StubbedClient(config);
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private JSAIClient newClient(String script) throws IOException, ScriptException, TemplateException {
        return newClient(script, null, DEFAULT_MODEL, null);
    }

    private JSAIClient newClient(String script, ConversationObserver observer, String defaultModel,
                                 JSONObject secrets) throws IOException, ScriptException, TemplateException {
        JSONObject config = new JSONObject();
        config.put("clientName", "TestJSClient");
        config.put("basePath", BASE_PATH);
        if (defaultModel != null) {
            config.put("defaultModel", defaultModel);
        }
        if (secrets != null) {
            config.put("secrets", secrets);
        }
        config.put("jsScript", script);
        return new JSAIClient(config, observer);
    }

    private File newFile(String name, String content) throws IOException {
        File file = temporaryFolder.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    @Test
    public void testConstructorWithInlineScript() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        JSAIClient client = newClient(ECHO_SCRIPT, observer, DEFAULT_MODEL, null);

        assertEquals("JSAIClient:TestJSClient/" + DEFAULT_MODEL, client.getName());
        assertEquals(observer, client.getConversationObserver());
        assertEquals(700, client.getTimeout());
        assertTrue(client.isCachePostRequestsEnabled());
        assertNotNull(client.getBridge());
    }

    @Test
    public void testConstructorWithoutModelAndSecrets() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT, null, null, null);
        assertEquals("JSAIClient:TestJSClient", client.getName());
        assertNull(client.getConversationObserver());
    }

    @Test
    public void testConstructorWithSecrets() throws Exception {
        JSONObject secrets = new JSONObject().put("apiKey", "key-123");
        JSAIClient client = newClient(ECHO_SCRIPT, null, DEFAULT_MODEL, secrets);
        assertNotNull(client);
    }

    @Test
    public void testFreemarkerSecretSubstitution() throws Exception {
        String script = "var KEY = '${secrets.apiKey}';\n" +
                "function handleChat(messages, model, metadata, bridge) { return KEY; }\n";
        JSONObject secrets = new JSONObject().put("apiKey", "secret-value");
        JSAIClient client = newClient(script, null, DEFAULT_MODEL, secrets);
        assertEquals("secret-value", client.chat("hi"));
    }

    @Test
    public void testFreemarkerWithoutSecretsUsesEmptyMap() throws Exception {
        String script = "function handleChat(messages, model, metadata, bridge) { return 'ok'; }\n";
        JSAIClient client = newClient(script);
        assertEquals("ok", client.chat("hi"));
    }

    @Test
    public void testConstructorWithJsScriptPathFromFilesystem() throws Exception {
        File scriptFile = temporaryFolder.newFile("test-script.js");
        Files.write(scriptFile.toPath(), ECHO_SCRIPT.getBytes(StandardCharsets.UTF_8));

        JSONObject config = new JSONObject();
        config.put("clientName", "PathClient");
        config.put("basePath", BASE_PATH);
        config.put("jsScriptPath", scriptFile.getAbsolutePath());
        JSAIClient client = new JSAIClient(config, null);

        assertEquals("JSAIClient:PathClient", client.getName());
        assertTrue(client.chat("hello").contains("\"role\":\"user\""));
    }

    @Test
    public void testConstructorMissingScriptThrowsIOException() throws Exception {
        JSONObject config = new JSONObject();
        config.put("clientName", "NoScriptClient");
        try {
            new JSAIClient(config, null);
            fail("Expected IOException when neither jsScript nor jsScriptPath is provided");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("jsScript"));
            assertTrue(e.getMessage().contains("jsScriptPath"));
        }
    }

    @Test
    public void testConstructorScriptEvalFailureIsSanitized() throws Exception {
        JSONObject secrets = new JSONObject().put("apiKey", "super-secret-token");
        String brokenScript = "var key = '${secrets.apiKey}';\nthis is not valid javascript !!!\n";
        try {
            newClient(brokenScript, null, DEFAULT_MODEL, secrets);
            fail("Expected ScriptException for invalid script");
        } catch (ScriptException e) {
            assertTrue(e.getMessage().contains("TestJSClient"));
            assertFalse("Secret must not leak into the sanitized error message",
                    e.getMessage().contains("super-secret-token"));
        }
    }

    // ------------------------------------------------------------------
    // chat(String) / chat(String model, String message)
    // ------------------------------------------------------------------

    @Test
    public void testChatSimpleMessageUsesDefaultModel() throws Exception {
        JSAIClient client = newClient(MODEL_SCRIPT);
        assertEquals(DEFAULT_MODEL, client.chat("hi"));
    }

    @Test
    public void testChatWithExplicitModel() throws Exception {
        JSAIClient client = newClient(MODEL_SCRIPT);
        assertEquals("other-model", client.chat("other-model", "hi"));
    }

    @Test
    public void testChatNullAndBlankModelFallsBackToDefault() throws Exception {
        JSAIClient client = newClient(MODEL_SCRIPT);
        assertEquals(DEFAULT_MODEL, client.chat(null, "hi"));
        assertEquals(DEFAULT_MODEL, client.chat("   ", "hi"));
    }

    @Test
    public void testChatBuildsUserMessageParts() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        String messages = client.chat("hello world");
        assertTrue(messages.contains("\"role\":\"user\""));
        assertTrue(messages.contains("\"text\":\"hello world\""));
    }

    @Test
    public void testChatWithMetadata() throws Exception {
        JSAIClient client = newClient(METADATA_SCRIPT);
        client.setMetadata(new Metadata("agent-1", "context-1"));
        String metadata = client.chat("hi");
        assertTrue(metadata.contains("agent-1"));
        assertTrue(metadata.contains("context-1"));
    }

    @Test
    public void testChatWithoutMetadataPassesNull() throws Exception {
        JSAIClient client = newClient(METADATA_SCRIPT);
        assertEquals("null", client.chat("hi"));
    }

    @Test
    public void testChatObserverReceivesUserAndResponseMessages() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        JSAIClient client = newClient(ECHO_SCRIPT, observer, DEFAULT_MODEL, null);

        client.chat("hi");

        ArgumentCaptor<ConversationObserver.Message> captor =
                ArgumentCaptor.forClass(ConversationObserver.Message.class);
        verify(observer, times(2)).addMessage(captor.capture());
        assertEquals("DMToolsUser", captor.getAllValues().get(0).getAuthor());
        assertEquals("hi", captor.getAllValues().get(0).getText());
        assertEquals("JSAIClient:TestJSClient/" + DEFAULT_MODEL, captor.getAllValues().get(1).getAuthor());
    }

    @Test
    public void testChatJsReturningNullGivesEmptyString() throws Exception {
        JSAIClient client = newClient(NULL_SCRIPT);
        assertEquals("", client.chat("hi"));
    }

    // ------------------------------------------------------------------
    // chat with files
    // ------------------------------------------------------------------

    @Test
    public void testChatWithImageFile() throws Exception {
        File png = newFile("image.png", "fake-png-bytes");
        JSAIClient client = newClient(ECHO_SCRIPT);

        String messages = client.chat(DEFAULT_MODEL, "Describe this", png);
        assertTrue(messages.contains("\"inline_data\""));
        assertTrue(messages.contains("\"mime_type\":\"image/png\""));
        assertTrue(messages.contains("\"text\":\"Describe this\""));
    }

    @Test
    public void testChatWithNullImageFile() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        String messages = client.chat(DEFAULT_MODEL, "hi", (File) null);
        assertFalse(messages.contains("inline_data"));
    }

    @Test
    public void testChatWithFileList() throws Exception {
        File png = newFile("list-image.png", "png-bytes");
        JSAIClient client = newClient(ECHO_SCRIPT);
        String messages = client.chat(DEFAULT_MODEL, "hi", Collections.singletonList(png));
        assertTrue(messages.contains("\"inline_data\""));
    }

    @Test
    public void testChatWithNullAndEmptyFileList() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        assertFalse(client.chat(DEFAULT_MODEL, "hi", (List<File>) null).contains("inline_data"));
        assertFalse(client.chat(DEFAULT_MODEL, "hi", new ArrayList<>()).contains("inline_data"));
    }

    @Test
    public void testChatWithVariousFileExtensions() throws Exception {
        List<File> files = new ArrayList<>();
        files.add(newFile("photo.jpg", "jpg"));
        files.add(newFile("anim.gif", "gif"));
        files.add(newFile("modern.webp", "webp"));
        files.add(newFile("apple.heic", "heic"));
        files.add(newFile("doc.txt", "plain text"));
        JSAIClient client = newClient(ECHO_SCRIPT);

        String messages = client.chat(DEFAULT_MODEL, "hi", files);
        // every file produces an inline_data part (mime type detected or fallback)
        int count = 0;
        for (int idx = messages.indexOf("inline_data"); idx >= 0; idx = messages.indexOf("inline_data", idx + 1)) {
            count++;
        }
        assertEquals(5, count);
    }

    @Test
    public void testChatWithNonexistentFileAddsErrorPart() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.png");
        JSAIClient client = newClient(ECHO_SCRIPT);

        String messages = client.chat(DEFAULT_MODEL, "hi", missing);
        assertTrue(messages.contains("\"error\""));
        assertTrue(messages.contains("missing.png"));
    }

    // ------------------------------------------------------------------
    // chat(String model, Message... messages) / chat(Message...)
    // ------------------------------------------------------------------

    @Test
    public void testChatMessagesTextOnly() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        JSAIClient client = newClient(ECHO_SCRIPT, observer, DEFAULT_MODEL, null);

        String messages = client.chat(DEFAULT_MODEL,
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null));

        assertTrue(messages.contains("\"role\":\"user\""));
        assertTrue(messages.contains("\"role\":\"assistant\""));
        assertTrue(messages.contains("\"text\":\"Hello\""));
        // 2 input messages + 1 AI response
        verify(observer, times(3)).addMessage(any(ConversationObserver.Message.class));
    }

    @Test
    public void testChatMessagesNormalizesRoles() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        // "model" role must be normalized to the script's role name ("assistant")
        String messages = client.chat(DEFAULT_MODEL,
                new Message("user", "Hello", null),
                new Message("model", "Hi there", null));
        assertTrue(messages.contains("\"role\":\"assistant\""));
        assertFalse(messages.contains("\"role\":\"model\""));
    }

    @Test
    public void testChatMessagesNullModelUsesDefault() throws Exception {
        JSAIClient client = newClient(MODEL_SCRIPT);
        assertEquals(DEFAULT_MODEL, client.chat((String) null, new Message("user", "Hello", null)));
        assertEquals(DEFAULT_MODEL, client.chat("  ", new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesVarargsUsesDefaultModel() throws Exception {
        JSAIClient client = newClient(MODEL_SCRIPT);
        assertEquals(DEFAULT_MODEL, client.chat(new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesWithFile() throws Exception {
        File png = newFile("msg-image.png", "png");
        JSAIClient client = newClient(ECHO_SCRIPT);

        String messages = client.chat(DEFAULT_MODEL,
                new Message("user", "What is this?", Collections.singletonList(png)));
        assertTrue(messages.contains("\"inline_data\""));
        assertTrue(messages.contains("\"mime_type\":\"image/png\""));
    }

    // ------------------------------------------------------------------
    // roleName
    // ------------------------------------------------------------------

    @Test
    public void testRoleNameFromScript() throws Exception {
        String script = "function handleChat(m) { return ''; }\n" +
                "function getRoleName() { return 'model'; }\n";
        JSAIClient client = newClient(script);
        assertEquals("model", client.roleName());
    }

    @Test
    public void testRoleNameMissingFunctionFallsBackToAssistant() throws Exception {
        String script = "function handleChat(m) { return ''; }\n";
        JSAIClient client = newClient(script);
        assertEquals("assistant", client.roleName());
    }

    @Test
    public void testRoleNameNullFromScriptFallsBackToAssistant() throws Exception {
        String script = "function handleChat(m) { return ''; }\n" +
                "function getRoleName() { return null; }\n";
        JSAIClient client = newClient(script);
        assertEquals("assistant", client.roleName());
    }

    // ------------------------------------------------------------------
    // path / sign / buildHashForPostRequest
    // ------------------------------------------------------------------

    @Test
    public void testPathReturnsSubPathUnchanged() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        assertEquals("/extra/path", client.path("/extra/path"));
    }

    @Test
    public void testSignAddsContentTypeOnly() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
    }

    @Test
    public void testBuildHashForPostRequest() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        GenericRequest request = mock(GenericRequest.class);
        when(request.getBody()).thenReturn("body");
        assertEquals("http://xbody", client.buildHashForPostRequest(request, "http://x"));
    }

    @Test
    public void testBuildHashForPostRequestNullBody() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        GenericRequest request = mock(GenericRequest.class);
        when(request.getBody()).thenReturn(null);
        assertEquals("http://x", client.buildHashForPostRequest(request, "http://x"));
    }

    // ------------------------------------------------------------------
    // DMToolsBridge HTTP delegation
    // ------------------------------------------------------------------

    @Test
    public void testBridgeExposesBasePath() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        assertEquals(BASE_PATH, client.getBridge().getJsBasePath());
    }

    @Test
    public void testBridgeExecutePostDelegatesToClient() throws Exception {
        StubbedClient client = newStubbedClient(ECHO_SCRIPT);

        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Token", "abc");
        String longBase64 = String.join("", Collections.nCopies(120, "A"));
        String body = "{\"data\":\"" + longBase64 + "\"}";

        assertEquals("post-response", client.getBridge().executePost("/chat", body, headers));

        assertEquals(1, client.posts.size());
        GenericRequest request = client.posts.get(0);
        assertEquals(BASE_PATH + "/chat", request.url());
        assertEquals("abc", request.getHeaders().get("X-Token"));
        assertEquals(body, request.getBody());
    }

    @Test
    public void testBridgeExecutePostWithAbsoluteUrl() throws Exception {
        StubbedClient client = newStubbedClient(ECHO_SCRIPT);

        client.getBridge().executePost("https://other.example.com/endpoint", "{}", null);

        assertEquals(1, client.posts.size());
        assertEquals("https://other.example.com/endpoint", client.posts.get(0).url());
    }

    @Test
    public void testBridgeExecuteGetDelegatesToClient() throws Exception {
        StubbedClient client = newStubbedClient(ECHO_SCRIPT);

        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace", 42);
        assertEquals("get-response", client.getBridge().executeGet("models", headers));

        assertEquals(1, client.gets.size());
        GenericRequest request = client.gets.get(0);
        assertEquals(BASE_PATH + "models", request.url());
        assertEquals("42", request.getHeaders().get("X-Trace"));
    }

    @Test
    public void testBridgeExecuteGetWithAbsoluteUrl() throws Exception {
        StubbedClient client = newStubbedClient(ECHO_SCRIPT);

        client.getBridge().executeGet("http://absolute.example.com/x", null);

        assertEquals(1, client.gets.size());
        assertEquals("http://absolute.example.com/x", client.gets.get(0).url());
    }

    @Test
    public void testJsCallingBridgeExecutePost() throws Exception {
        StubbedClient client = newStubbedClient(BRIDGE_SCRIPT);

        assertEquals("post-response", client.chat("hi"));

        assertEquals(1, client.posts.size());
        GenericRequest request = client.posts.get(0);
        assertEquals(BASE_PATH + "/chat/completions", request.url());
        assertEquals("{\"q\":1}", request.getBody());
    }

    @Test
    public void testBridgeHasExpectedPermissions() throws Exception {
        JSAIClient client = newClient(ECHO_SCRIPT);
        DMToolsBridge bridge = client.getBridge();
        assertTrue(bridge.hasPermission(DMToolsBridge.Permission.HTTP_POST_REQUESTS.name()));
        assertTrue(bridge.hasPermission(DMToolsBridge.Permission.HTTP_GET_REQUESTS.name()));
        assertTrue(bridge.hasPermission(DMToolsBridge.Permission.HTTP_BASE_PATH_ACCESS.name()));
        assertTrue(bridge.hasPermission(DMToolsBridge.Permission.LOGGING_INFO.name()));
        assertFalse(bridge.hasPermission(DMToolsBridge.Permission.TRACKER_CLIENT_ACCESS.name()));
    }
}
