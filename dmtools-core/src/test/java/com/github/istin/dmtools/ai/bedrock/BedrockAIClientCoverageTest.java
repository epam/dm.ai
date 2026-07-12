// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Additional unit tests for {@link BedrockAIClient} aimed at covering request building,
 * response parsing for all supported model types (Claude, Qwen, Nova, Mistral), image
 * handling, caching behavior, AWS4 GET signing and error handling. The HTTP layer is
 * mocked via Mockito spies - no real network calls are made.
 */
public class BedrockAIClientCoverageTest {

    private static final String BASE_PATH = "https://bedrock-runtime.us-east-1.amazonaws.com";
    private static final String REGION = "us-east-1";
    private static final String CLAUDE_MODEL = "anthropic.claude-sonnet-4-20250514-v1:0";
    private static final String QWEN_MODEL = "qwen.qwen3-coder-480b-a35b-v1:0";
    private static final String NOVA_MODEL = "eu.amazon.nova-lite-v1:0";
    private static final String MISTRAL_MODEL = "mistral.mistral-large-2407-v1:0";
    private static final String BEARER_TOKEN = "test-bearer-token";

    private static final String CLAUDE_RESPONSE =
            "{\"content\":[{\"text\":\"Hello from Claude\"}],\"usage\":{\"input_tokens\":10,\"output_tokens\":5},\"stop_reason\":\"end_turn\"}";
    private static final String QWEN_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"Hello from Qwen\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}";
    private static final String QWEN_OUTPUT_RESPONSE =
            "{\"output\":{\"choices\":[{\"message\":{\"content\":\"Hello from Qwen output\"}}]}}";
    private static final String NOVA_RESPONSE =
            "{\"output\":{\"message\":{\"content\":[{\"text\":\"Hello from Nova\"}]}},\"usage\":{\"inputTokens\":2,\"outputTokens\":3,\"totalTokens\":5}}";
    private static final String MISTRAL_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"Hello from Mistral\"}}]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private BedrockAIClient claudeClient;

    @Before
    public void setUp() throws IOException {
        claudeClient = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, BEARER_TOKEN,
                1000, 0.7, null, null, null
        );
    }

    @After
    public void tearDown() {
        // best-effort cleanup of the cache folder created by cache-related tests
        File cacheDir = new File("cacheTestableBedrockAIClient");
        File[] files = cacheDir.listFiles();
        if (files != null && files.length == 0) {
            cacheDir.delete();
        }
    }

    /**
     * Test subclass exposing protected cache helpers of AbstractRestClient.
     */
    static class TestableBedrockAIClient extends BedrockAIClient {
        TestableBedrockAIClient(String basePath, String region, String modelId, String bearerToken,
                                int maxTokens, double temperature) throws IOException {
            super(basePath, region, modelId, bearerToken, maxTokens, temperature, null, null, null);
        }

        String cacheFileName(GenericRequest request) {
            return getCacheFileName(request);
        }

        String cacheFolder() {
            return getCacheFolderName();
        }
    }

    private BedrockAIClient clientForModel(String modelId) throws IOException {
        return new BedrockAIClient(BASE_PATH, REGION, modelId, BEARER_TOKEN, 1000, 0.7, null, null, null);
    }

    private BedrockAIClient spyWithResponse(BedrockAIClient client, String response) throws IOException {
        BedrockAIClient spy = Mockito.spy(client);
        doReturn(response).when(spy).post(any(GenericRequest.class));
        return spy;
    }

    private File createImage(String name, String format) throws IOException {
        File file = temporaryFolder.newFile(name);
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        image.setRGB(50, 50, 0xFF0000);
        ImageIO.write(image, format, file);
        return file;
    }

    // ------------------------------------------------------------------
    // Constructors / basic accessors
    // ------------------------------------------------------------------

    @Test
    public void testBearerTokenConstructor() {
        assertEquals(CLAUDE_MODEL, claudeClient.getModelId());
        assertEquals(CLAUDE_MODEL, claudeClient.getName());
        assertEquals(REGION, claudeClient.getRegion());
        assertEquals(BEARER_TOKEN, claudeClient.getBearerToken());
        assertEquals(1000, claudeClient.getMaxTokens());
        assertEquals(0.7, claudeClient.getTemperature(), 0.001);
        assertEquals("BEARER_TOKEN", claudeClient.getAuthenticationStrategy().getAuthenticationType());
        assertNull(claudeClient.getCustomHeaders());
        assertEquals("assistant", claudeClient.roleName());
        assertEquals(700, claudeClient.getTimeout());
    }

    @Test
    public void testBearerTokenConstructorWithCustomHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "value");
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, BEARER_TOKEN, 100, 0.1, null, headers, null);
        assertNotNull(client.getCustomHeaders());
        assertEquals("value", client.getCustomHeaders().get("X-Test"));
        // ensure the map was copied
        headers.put("X-Other", "other");
        assertNull(client.getCustomHeaders().get("X-Other"));
    }

    @Test
    public void testIamKeysConstructor() throws IOException {
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL,
                "AKIAIOSFODNN7EXAMPLE", "secretAccessKeyValue", "sessionTokenValue",
                500, 0.5, null, null, null);
        assertNull(client.getBearerToken());
        assertEquals("IAM_KEYS", client.getAuthenticationStrategy().getAuthenticationType());
        assertEquals(500, client.getMaxTokens());
        assertEquals(0.5, client.getTemperature(), 0.001);
    }

    @Test
    public void testIamKeysConstructorShortAccessKey() throws IOException {
        // access key shorter than 8 chars -> "N/A" logging branch
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL,
                "short", "secretAccessKeyValue", null,
                500, 0.5, null, null, null);
        assertEquals("IAM_KEYS", client.getAuthenticationStrategy().getAuthenticationType());
    }

    @Test
    public void testDefaultCredentialsConstructor() throws IOException {
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, 500, 0.5, null, null, null);
        assertNull(client.getBearerToken());
        assertEquals("DEFAULT_CREDENTIALS", client.getAuthenticationStrategy().getAuthenticationType());
    }

    @Test
    public void testDelegationConstructor() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "value");
        ConversationObserver observer = mock(ConversationObserver.class);
        BedrockAIClient delegate = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, BEARER_TOKEN, 1000, 0.7, observer, headers, null);
        BedrockAIClient client = new BedrockAIClient(delegate);
        assertEquals(CLAUDE_MODEL, client.getModelId());
        assertEquals(REGION, client.getRegion());
        assertEquals(BEARER_TOKEN, client.getBearerToken());
        assertEquals(1000, client.getMaxTokens());
        assertEquals(observer, client.getConversationObserver());
        assertEquals("value", client.getCustomHeaders().get("X-Test"));
        assertEquals("BEARER_TOKEN", client.getAuthenticationStrategy().getAuthenticationType());
    }

    @Test
    public void testPathVariants() {
        assertEquals(BASE_PATH + "/model/x/invoke", claudeClient.path("/model/x/invoke"));
        assertEquals(BASE_PATH + "/converse", claudeClient.path("/converse"));
        assertEquals(BASE_PATH + "/model/" + CLAUDE_MODEL + "/invoke", claudeClient.path("something/else"));
    }

    @Test
    public void testSignDelegatesToStrategy() {
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request.Builder signed = claudeClient.sign(builder);
        assertNotNull(signed);
        assertEquals("Bearer " + BEARER_TOKEN, signed.build().header("Authorization"));
    }

    // ------------------------------------------------------------------
    // chat() - text only, per model type
    // ------------------------------------------------------------------

    @Test
    public void testChatClaudeTextOnly() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        claudeClient.setConversationObserver(observer);
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);

        String result = spy.chat("Hello");

        assertEquals("Hello from Claude", result);
        verify(observer, atLeast(2)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"anthropic_version\":\"bedrock-2023-05-31\""));
        assertTrue(body.contains("\"max_tokens\":1000"));
        assertTrue(captor.getValue().url().contains("/model/" + CLAUDE_MODEL + "/invoke"));
    }

    @Test
    public void testChatNullModelFallsBackToDefault() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(null, "Hi"));
    }

    @Test
    public void testChatWithMetadata() throws Exception {
        claudeClient.setMetadata(new Metadata("agent-1", "context-1"));
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);

        assertEquals("Hello from Claude", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"metadata\""));
        assertTrue(captor.getValue().getBody().contains("agent-1"));
    }

    @Test
    public void testChatQwenRootChoices() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), QWEN_RESPONSE);
        assertEquals("Hello from Qwen", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"max_tokens\""));
        // Qwen content is a plain string, not a block array
        assertTrue(body.contains("\"content\":\"Hi\""));
    }

    @Test
    public void testChatQwenOutputChoicesFallback() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), QWEN_OUTPUT_RESPONSE);
        assertEquals("Hello from Qwen output", spy.chat("Hi"));
    }

    @Test
    public void testChatQwenChoiceWithoutMessage() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), "{\"choices\":[{}]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatNovaTextOnly() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        assertEquals("Hello from Nova", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"schemaVersion\":\"messages-v1\""));
        assertTrue(body.contains("\"inferenceConfig\""));
        assertTrue(body.contains("\"topP\":0.9"));
    }

    @Test
    public void testChatNovaEmptyContent() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL),
                "{\"output\":{\"message\":{\"content\":[]}}}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatMistralTextOnly() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(MISTRAL_MODEL), MISTRAL_RESPONSE);
        assertEquals("Hello from Mistral", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"max_tokens\""));
    }

    @Test
    public void testChatMistralOutputChoicesFallback() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(MISTRAL_MODEL),
                "{\"output\":{\"choices\":[{\"message\":{\"content\":\"Mistral output\"}}]}}");
        assertEquals("Mistral output", spy.chat("Hi"));
    }

    @Test
    public void testChatUnknownModelDefaultsToClaudeFormat() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel("some.unknown-model-v1:0"), CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"anthropic_version\""));
    }

    @Test
    public void testChatWithInferenceProfileArn() throws Exception {
        String arn = "arn:aws:bedrock:us-east-1:123456789012:inference-profile/anthropic.claude-3-sonnet";
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(arn, "Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        // ARN is URL-encoded in the request path (':' -> %3A)
        assertTrue(captor.getValue().url().contains("inference-profile"));
        assertTrue(captor.getValue().url().contains("%3A"));
    }

    // ------------------------------------------------------------------
    // chat() - error / edge responses
    // ------------------------------------------------------------------

    @Test
    public void testChatErrorResponseWithOutputType() throws Exception {
        String errorResponse = "{\"Output\":{\"__type\":\"ValidationException\"}}";
        BedrockAIClient spy = spyWithResponse(claudeClient, errorResponse);
        assertEquals(errorResponse, spy.chat("Hi"));
    }

    @Test
    public void testChatErrorResponseWithErrorField() throws Exception {
        String errorResponse = "{\"error\":\"something went wrong\"}";
        BedrockAIClient spy = spyWithResponse(claudeClient, errorResponse);
        assertEquals(errorResponse, spy.chat("Hi"));
    }

    @Test
    public void testChatErrorResponseWithMessageField() throws Exception {
        String errorResponse = "{\"message\":\"throttled\"}";
        BedrockAIClient spy = spyWithResponse(claudeClient, errorResponse);
        assertEquals(errorResponse, spy.chat("Hi"));
    }

    @Test
    public void testChatInvalidJsonResponseReturnsEmpty() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, "this is not json");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatUnparseableContentFallsBackToEmpty() throws Exception {
        // valid JSON, but content[0] is not an object -> JSONException inside processResponse
        BedrockAIClient spy = spyWithResponse(claudeClient, "{\"content\":[\"just a string\"]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatClaudeEmptyContentArray() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, "{\"content\":[]}");
        assertEquals("", spy.chat("Hi"));
    }

    // ------------------------------------------------------------------
    // chat() with a single image file
    // ------------------------------------------------------------------

    @Test
    public void testChatWithImageClaude() throws Exception {
        File png = createImage("claude-image.png", "png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);

        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, "Describe this", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image\""));
        assertTrue(body.contains("\"media_type\":\"image/png\""));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatWithImageClaudeNullMessage() throws Exception {
        File png = createImage("claude-image-null.png", "png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, null, png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        // no text part, only image
        assertFalse(captor.getValue().getBody().contains("\"type\":\"text\""));
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image\""));
    }

    @Test
    public void testChatWithImageNovaPng() throws Exception {
        File png = createImage("nova-image.png", "png");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);

        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL, "Describe this", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"format\":\"png\""));
        assertTrue(body.contains("\"bytes\""));
        assertTrue(body.contains("\"text\":\"Describe this\""));
    }

    @Test
    public void testChatWithImageNovaGif() throws Exception {
        File gif = createImage("nova-image.gif", "gif");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL, "Describe this", gif));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"format\":\"gif\""));
    }

    @Test
    public void testChatWithImageNovaJpeg() throws Exception {
        File jpg = createImage("nova-image.jpg", "jpeg");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL, "", jpg));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"format\":\"jpeg\""));
        // empty message -> no text part
        assertFalse(body.contains("\"text\""));
    }

    @Test
    public void testChatWithImageQwenIgnoresImage() throws Exception {
        File png = createImage("qwen-image.png", "png");
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), QWEN_RESPONSE);

        assertEquals("Hello from Qwen", spy.chat(QWEN_MODEL, "Hi", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        // image ignored for Qwen, plain string content
        assertTrue(body.contains("\"content\":\"Hi\""));
        assertFalse(body.contains("image"));
    }

    @Test
    public void testChatWithNonexistentImageFallsBackToText() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "does-not-exist.png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        // AIFileFilter drops nonexistent files -> text only path
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, "Hi", missing));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("image"));
    }

    @Test
    public void testChatWithWebpImageClaudeFails() throws Exception {
        File webp = temporaryFolder.newFile("broken.webp");
        Files.write(webp.toPath(), "not a real webp".getBytes(StandardCharsets.UTF_8));
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        try {
            spy.chat(CLAUDE_MODEL, "Hi", webp);
            // If the JDK supports WebP, conversion succeeds and chat completes
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("WebP image conversion failed"));
        }
    }

    // ------------------------------------------------------------------
    // chat(String, Message...)
    // ------------------------------------------------------------------

    @Test
    public void testChatMessagesTextOnlyClaude() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        String result = spy.chat(CLAUDE_MODEL,
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null));
        assertEquals("Hello from Claude", result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
    }

    @Test
    public void testChatMessagesTextOnlyNova() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL, new Message("user", "Hello", null)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"text\":\"Hello\""));
        assertFalse(body.contains("\"type\""));
    }

    @Test
    public void testChatMessagesTextOnlyQwen() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), QWEN_RESPONSE);
        assertEquals("Hello from Qwen", spy.chat(QWEN_MODEL, new Message("user", "Hello", null)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"content\":\"Hello\""));
    }

    @Test
    public void testChatMessagesVarargsUsesDefaultModel() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesNullModelUsesDefault() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat((String) null, new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesWithFilesClaude() throws Exception {
        File png = createImage("msg-claude.png", "png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);

        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL,
                new Message("user", "What is this?", Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image\""));
        assertTrue(body.contains("\"media_type\":\"image/png\""));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatMessagesWithFilesNova() throws Exception {
        File png = createImage("msg-nova.png", "png");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);

        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL,
                new Message("user", "What is this?", Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"format\":\"png\""));
        assertTrue(body.contains("\"bytes\""));
        assertTrue(body.contains("\"text\":\"What is this?\""));
    }

    @Test
    public void testChatMessagesWithFilesNovaNullText() throws Exception {
        File png = createImage("msg-nova-nulltext.png", "png");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL,
                new Message("user", null, Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("\"text\""));
    }

    @Test
    public void testChatMessagesQwenFilesIgnored() throws Exception {
        File png = createImage("msg-qwen.png", "png");
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), QWEN_RESPONSE);
        assertEquals("Hello from Qwen", spy.chat(QWEN_MODEL,
                new Message("user", "Hi", Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"content\":\"Hi\""));
        assertFalse(body.contains("image"));
    }

    @Test
    public void testChatMessagesNovaSkipsBrokenImage() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.png");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        // broken image is skipped with a warning, text still sent
        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL,
                new Message("user", "Hi", Collections.singletonList(missing))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertFalse(body.contains("bytes"));
        assertTrue(body.contains("\"text\":\"Hi\""));
    }

    @Test
    public void testChatMessagesClaudeSkipsBrokenImage() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL,
                new Message("user", "Hi", Collections.singletonList(missing))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("base64"));
    }

    @Test
    public void testChatMessagesNovaWebpFails() throws Exception {
        File webp = temporaryFolder.newFile("msg-broken.webp");
        Files.write(webp.toPath(), "not a real webp".getBytes(StandardCharsets.UTF_8));
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        try {
            spy.chat(NOVA_MODEL, new Message("user", "Hi", Collections.singletonList(webp)));
            // If the JDK supports WebP, the broken file is simply skipped
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("WebP image conversion failed"));
        }
    }

    // ------------------------------------------------------------------
    // chat(String, String, List<File>)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFileList() throws Exception {
        File png = createImage("list-image.png", "png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, "Hi", Collections.singletonList(png)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image\""));
    }

    @Test
    public void testChatWithNullFileList() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, "Hi", (List<File>) null));
    }

    @Test
    public void testChatWithEmptyFileList() throws Exception {
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, "Hi", new ArrayList<>()));
    }

    @Test
    public void testChatWithFilteredOutFileList() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.png");
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, "Hi", Collections.singletonList(missing)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("image"));
    }

    // ------------------------------------------------------------------
    // buildHashForPostRequest / post()
    // ------------------------------------------------------------------

    @Test
    public void testBuildHashForPostRequestWithAndWithoutBody() {
        GenericRequest withBody = mock(GenericRequest.class);
        when(withBody.getBody()).thenReturn("body");
        assertEquals("http://xbody", claudeClient.buildHashForPostRequest(withBody, "http://x"));

        GenericRequest noBody = mock(GenericRequest.class);
        when(noBody.getBody()).thenReturn(null);
        assertEquals("http://x", claudeClient.buildHashForPostRequest(noBody, "http://x"));
    }

    @Test
    public void testPostNullRequestReturnsEmpty() throws IOException {
        assertEquals("", claudeClient.post(null));
    }

    @Test
    public void testPostNullBodyThrows() throws IOException {
        TestableBedrockAIClient client = new TestableBedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, BEARER_TOKEN, 1000, 0.7);
        GenericRequest request = new GenericRequest(client,
                "https://example.com/covtest-null-body-" + UUID.randomUUID());
        try {
            client.post(request);
            fail("Expected IllegalArgumentException for null request body");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Request body cannot be null"));
        }
    }

    @Test
    public void testPostReturnsCachedResponse() throws IOException {
        TestableBedrockAIClient client = new TestableBedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, BEARER_TOKEN, 1000, 0.7);
        GenericRequest request = new GenericRequest(client,
                "https://example.com/covtest-cache-" + UUID.randomUUID());
        request.setBody("covtest-body-" + UUID.randomUUID());

        // Replicate the cache file the client would read: folder + md5-based name
        File cacheDir = new File(client.cacheFolder());
        cacheDir.mkdirs();
        File cachedFile = new File(cacheDir, client.cacheFileName(request));
        Files.write(cachedFile.toPath(), "cached-response-content".getBytes(StandardCharsets.UTF_8));

        try {
            assertEquals("cached-response-content", client.post(request));
        } finally {
            cachedFile.delete();
        }
    }

    // ------------------------------------------------------------------
    // getAvailableModels
    // ------------------------------------------------------------------

    @Test
    public void testGetAvailableModelsWithoutRegionThrows() throws IOException {
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, null, CLAUDE_MODEL, BEARER_TOKEN, 1000, 0.7, null, null, null);
        try {
            client.getAvailableModels();
            fail("Expected IOException for missing region");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("region is not configured"));
        }
    }

    @Test
    public void testGetAvailableModelsBearerToken() throws IOException {
        BedrockAIClient spy = Mockito.spy(claudeClient);
        doReturn("{\"models\":[]}").when(spy).execute(any(GenericRequest.class));

        assertEquals("{\"models\":[]}", spy.getAvailableModels());

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        assertEquals("https://bedrock.us-east-1.amazonaws.com/foundation-models", captor.getValue().url());
    }

    // ------------------------------------------------------------------
    // AWS4 GET signing (local operation, no network)
    // ------------------------------------------------------------------

    private Request invokeSignGetRequest(BedrockAIClient client, String url) throws Exception {
        Method method = BedrockAIClient.class.getDeclaredMethod(
                "signGetRequestWithAWS4", String.class, Request.Builder.class);
        method.setAccessible(true);
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", "DMTools").get();
        try {
            return (Request) method.invoke(client, url, builder);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void testSignGetRequestWithIamSessionCredentials() throws Exception {
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL,
                "AKIAIOSFODNN7EXAMPLE", "secretAccessKeyValue", "sessionTokenValue",
                500, 0.5, null, null, null);
        Request request = invokeSignGetRequest(client, "https://bedrock.us-east-1.amazonaws.com/foundation-models");
        assertNotNull(request.header("Authorization"));
        assertTrue(request.header("Authorization").contains("AWS4-HMAC-SHA256"));
        assertNotNull(request.header("X-Amz-Security-Token"));
    }

    @Test
    public void testSignGetRequestWithIamBasicCredentials() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom-Header", "custom-value");
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL,
                "AKIAIOSFODNN7EXAMPLE", "secretAccessKeyValue", null,
                500, 0.5, null, headers, null);
        Request request = invokeSignGetRequest(client, "https://bedrock.us-east-1.amazonaws.com/foundation-models");
        assertNotNull(request.header("Authorization"));
        assertNull(request.header("X-Amz-Security-Token"));
        assertEquals("custom-value", request.header("X-Custom-Header"));
    }

    @Test
    public void testSignGetRequestWithDefaultCredentials() throws Exception {
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, CLAUDE_MODEL, 500, 0.5, null, null, null);
        try {
            Request request = invokeSignGetRequest(client, "https://bedrock.us-east-1.amazonaws.com/foundation-models");
            // environment has AWS credentials configured - signing succeeds locally
            assertNotNull(request.header("Authorization"));
        } catch (IOException e) {
            // no credentials in the environment - wrapped in IOException
            assertTrue(e.getMessage().contains("Failed to sign GET request"));
        }
    }

    // ------------------------------------------------------------------
    // parseAWSErrorMessage
    // ------------------------------------------------------------------

    private String invokeParseAWSErrorMessage(String errorBody) throws Exception {
        Method method = BedrockAIClient.class.getDeclaredMethod("parseAWSErrorMessage", String.class);
        method.setAccessible(true);
        return (String) method.invoke(claudeClient, errorBody);
    }

    @Test
    public void testParseAWSErrorMessageVariants() throws Exception {
        assertEquals("Access denied", invokeParseAWSErrorMessage("{\"Message\":\"Access denied\"}"));
        assertEquals("throttled", invokeParseAWSErrorMessage("{\"message\":\"throttled\"}"));
        assertEquals("bad request", invokeParseAWSErrorMessage("{\"error\":\"bad request\"}"));
        assertEquals("{\"other\":\"fallback\"}", invokeParseAWSErrorMessage("{\"other\":\"fallback\"}"));
        assertEquals("not json", invokeParseAWSErrorMessage("not json"));

        StringBuilder longBody = new StringBuilder("{not json ");
        for (int i = 0; i < 600; i++) {
            longBody.append('x');
        }
        String truncated = invokeParseAWSErrorMessage(longBody.toString());
        assertEquals(503, truncated.length());
        assertTrue(truncated.endsWith("..."));
    }

    // ------------------------------------------------------------------
    // hasImagesInMessages
    // ------------------------------------------------------------------

    private boolean invokeHasImagesInMessages(JSONArray messages) throws Exception {
        Method method = BedrockAIClient.class.getDeclaredMethod("hasImagesInMessages", JSONArray.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(claudeClient, messages);
    }

    // ------------------------------------------------------------------
    // Additional response-parsing branches
    // ------------------------------------------------------------------

    @Test
    public void testChatQwenEmptyChoices() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(QWEN_MODEL), "{\"choices\":[]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatNovaOutputWithoutMessage() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), "{\"output\":{}}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatNovaWithoutOutput() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), "{\"stopReason\":\"end_turn\"}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatMistralChoiceWithoutMessage() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(MISTRAL_MODEL), "{\"choices\":[{}]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatMistralEmptyChoices() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(MISTRAL_MODEL), "{\"choices\":[]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testChatMistralWithUsage() throws Exception {
        BedrockAIClient spy = spyWithResponse(clientForModel(MISTRAL_MODEL),
                "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}");
        assertEquals("Hi", spy.chat("Hi"));
    }

    @Test
    public void testChatMessagesWithConversationObserver() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        claudeClient.setConversationObserver(observer);
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(CLAUDE_MODEL, new Message("user", "Hello", null)));
        verify(observer, atLeast(2)).addMessage(any(ConversationObserver.Message.class));
    }

    @Test
    public void testChatMessagesNovaWithGif() throws Exception {
        File gif = createImage("msg-nova.gif", "gif");
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        assertEquals("Hello from Nova", spy.chat(NOVA_MODEL,
                new Message("user", "Hi", Collections.singletonList(gif))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"format\":\"gif\""));
    }

    @Test
    public void testChatMessagesClaudeWebpFails() throws Exception {
        File webp = temporaryFolder.newFile("msg-claude-broken.webp");
        Files.write(webp.toPath(), "not a real webp".getBytes(StandardCharsets.UTF_8));
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        try {
            spy.chat(CLAUDE_MODEL, new Message("user", "Hi", Collections.singletonList(webp)));
            // If the JDK supports WebP, the broken file is simply skipped
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("WebP image conversion failed"));
        }
    }

    @Test
    public void testChatWithNullModelIdClient() throws Exception {
        // detectModelType(null) falls back to CLAUDE
        BedrockAIClient client = new BedrockAIClient(
                BASE_PATH, REGION, null, BEARER_TOKEN, 1000, 0.7, null, null, null);
        BedrockAIClient spy = spyWithResponse(client, CLAUDE_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(null, "Hi"));
    }

    @Test
    public void testChatWithImageNovaWebpFails() throws Exception {
        File webp = temporaryFolder.newFile("nova-broken.webp");
        Files.write(webp.toPath(), "not a real webp".getBytes(StandardCharsets.UTF_8));
        BedrockAIClient spy = spyWithResponse(clientForModel(NOVA_MODEL), NOVA_RESPONSE);
        try {
            spy.chat(NOVA_MODEL, "Hi", webp);
            // If the JDK supports WebP, conversion of junk bytes may still succeed
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("WebP image conversion failed"));
        }
    }

    @Test
    public void testChatWithCorruptImageClaudeRethrows() throws Exception {
        File corrupt = temporaryFolder.newFile("corrupt.png");
        Files.write(corrupt.toPath(), "not a real png".getBytes(StandardCharsets.UTF_8));
        BedrockAIClient spy = spyWithResponse(claudeClient, CLAUDE_RESPONSE);
        // non-webp conversion failure is rethrown as-is (not wrapped)
        assertThrows(Exception.class, () -> spy.chat(CLAUDE_MODEL, "Hi", corrupt));
    }

    // ------------------------------------------------------------------
    // sanitizeJsonObject edge branches (private, invoked via reflection)
    // ------------------------------------------------------------------

    private Object invokeSanitizeJsonObject(Object value) throws Exception {
        Method method = BedrockAIClient.class.getDeclaredMethod("sanitizeJsonObject", Object.class);
        method.setAccessible(true);
        return method.invoke(claudeClient, value);
    }

    @Test
    public void testSanitizeJsonObjectBranches() throws Exception {
        // short base64 string is kept as-is
        JSONObject shortBytes = new JSONObject().put("bytes", "short-value");
        Object sanitizedShort = invokeSanitizeJsonObject(shortBytes);
        assertEquals("short-value", ((JSONObject) sanitizedShort).getString("bytes"));

        // non-string value under a base64 key is sanitized recursively
        JSONObject nonStringData = new JSONObject().put("data", new JSONObject().put("nested", "value"));
        Object sanitizedNonString = invokeSanitizeJsonObject(nonStringData);
        assertEquals("value", ((JSONObject) sanitizedNonString).getJSONObject("data").getString("nested"));

        // plain scalar passes through untouched
        assertEquals("plain", invokeSanitizeJsonObject("plain"));
    }

    @Test
    public void testHasImagesInMessages() throws Exception {
        JSONArray textOnly = new JSONArray();
        textOnly.put(new JSONObject().put("role", "user").put("content", "plain text"));
        assertFalse(invokeHasImagesInMessages(textOnly));

        JSONArray novaStyle = new JSONArray();
        JSONArray novaContent = new JSONArray();
        novaContent.put(new JSONObject().put("image", new JSONObject().put("format", "png")));
        novaStyle.put(new JSONObject().put("role", "user").put("content", novaContent));
        assertTrue(invokeHasImagesInMessages(novaStyle));

        JSONArray claudeStyle = new JSONArray();
        JSONArray claudeContent = new JSONArray();
        claudeContent.put(new JSONObject().put("type", "image"));
        claudeStyle.put(new JSONObject().put("role", "user").put("content", claudeContent));
        assertTrue(invokeHasImagesInMessages(claudeStyle));

        JSONArray blockText = new JSONArray();
        JSONArray textContent = new JSONArray();
        textContent.put(new JSONObject().put("type", "text").put("text", "hi"));
        blockText.put(new JSONObject().put("role", "user").put("content", textContent));
        assertFalse(invokeHasImagesInMessages(blockText));
    }
}
