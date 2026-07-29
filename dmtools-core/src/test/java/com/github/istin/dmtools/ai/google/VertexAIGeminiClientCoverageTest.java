// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.google.auth.GeminiAuthenticationStrategy;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Additional unit tests for {@link VertexAIGeminiClient} aimed at covering the real
 * request-building, response-parsing and file-handling logic of the class. A test
 * subclass overrides {@link AbstractRestClient#post(GenericRequest)} so no network
 * calls are made; service account credentials are generated locally (an in-memory RSA
 * key pair) so the constructor's GoogleCredentials parsing succeeds offline.
 */
public class VertexAIGeminiClientCoverageTest {

    private static final String PROJECT_ID = "test-project";
    private static final String LOCATION = "us-central1";
    private static final String MODEL = "gemini-2.0-flash-exp";

    private static final String SUCCESS_RESPONSE =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello \"},{\"text\":\"World\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}";
    private static final String ERROR_RESPONSE =
            "{\"error\":{\"code\":400,\"message\":\"Invalid request\",\"status\":\"INVALID_ARGUMENT\"}}";
    private static final String BLOCKED_RESPONSE =
            "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
    private static final String SAFETY_FINISH_RESPONSE =
            "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}";
    private static final String EMPTY_CANDIDATES_RESPONSE =
            "{\"candidates\":[]}";
    private static final String NO_TEXT_RESPONSE =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{\"mime_type\":\"image/png\",\"data\":\"abc\"}}]},\"finishReason\":\"STOP\"}]}";

    private static String credentialsJson;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void generateCredentials() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        credentialsJson = new JSONObject()
                .put("type", "service_account")
                .put("project_id", PROJECT_ID)
                .put("private_key_id", "key-123")
                .put("private_key", pem)
                .put("client_email", "test@test.iam.gserviceaccount.com")
                .put("client_id", "123456789")
                .put("token_uri", "https://oauth2.googleapis.com/token")
                .toString();
    }

    // ------------------------------------------------------------------
    // Constructors / configuration
    // ------------------------------------------------------------------

    @Test
    public void testConstructorFromCredentialsFileDefaultsApiVersion() throws IOException {
        File credentialsFile = writeCredentialsFile();

        VertexAIGeminiClient client = new VertexAIGeminiClient(PROJECT_ID, LOCATION, MODEL,
                credentialsFile.getAbsolutePath(), null, null, null);

        assertEquals(PROJECT_ID, client.getProjectId());
        assertEquals(LOCATION, client.getLocation());
        assertEquals(MODEL, client.getModel());
        assertEquals("v1", client.getApiVersion());
        assertNull(client.getCustomHeaders());
        assertNotNull(client.getAuthenticationStrategy());
        assertEquals("https://us-central1-aiplatform.googleapis.com", client.getBasePath());
        assertTrue(client.isCachePostRequestsEnabled());
    }

    @Test
    public void testConstructorFromCredentialsJsonKeepsApiVersionAndHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom", "value");

        VertexAIGeminiClient client = new VertexAIGeminiClient(PROJECT_ID, LOCATION, MODEL,
                null, credentialsJson, headers, "v1beta1");

        assertEquals("v1beta1", client.getApiVersion());
        assertNotNull(client.getCustomHeaders());
        assertEquals("value", client.getCustomHeaders().get("X-Custom"));
        // verify defensive copy of the headers map
        headers.put("X-Other", "other");
        assertNull(client.getCustomHeaders().get("X-Other"));
    }

    @Test
    public void testConstructorGlobalLocationUsesRegionlessBasePath() throws IOException {
        VertexAIGeminiClient client = new VertexAIGeminiClient(PROJECT_ID, "global", MODEL,
                null, credentialsJson, null, "v1beta1");

        assertEquals("https://aiplatform.googleapis.com", client.getBasePath());
        assertEquals("global", client.getLocation());
    }

    @Test
    public void testConstructorRejectsBlankArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient("  ", LOCATION, MODEL, null, credentialsJson, null, "v1"));
        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient(PROJECT_ID, null, MODEL, null, credentialsJson, null, "v1"));
        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient(PROJECT_ID, LOCATION, "", null, credentialsJson, null, "v1"));
    }

    @Test
    public void testMetadataAndObserverSetters() throws IOException {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        ConversationObserver observer = mock(ConversationObserver.class);
        Metadata metadata = mock(Metadata.class);

        client.setConversationObserver(observer);
        client.setMetadata(metadata);

        assertSame(observer, client.getConversationObserver());
    }

    @Test
    public void testRoleName() throws IOException {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        assertEquals("model", client.roleName());
    }

    @Test
    public void testPathBuilding() throws IOException {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);

        String expectedEndpoint = "https://us-central1-aiplatform.googleapis.com/v1/projects/test-project"
                + "/locations/us-central1/publishers/google/models/gemini-2.0-flash-exp:generateContent";
        assertEquals(expectedEndpoint, client.path(null));
        assertEquals(expectedEndpoint, client.path(""));
        assertEquals("https://us-central1-aiplatform.googleapis.com/some/path", client.path("/some/path"));
    }

    @Test
    public void testSignDelegatesToAuthenticationStrategy() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom", "value");
        TestableClient client = new TestableClient(SUCCESS_RESPONSE, headers);

        GeminiAuthenticationStrategy mockStrategy = mock(GeminiAuthenticationStrategy.class);
        when(mockStrategy.sign(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        Field field = VertexAIGeminiClient.class.getDeclaredField("authenticationStrategy");
        field.setAccessible(true);
        field.set(client, mockStrategy);

        Request.Builder builder = new Request.Builder().url("https://example.com");
        Request.Builder signed = client.sign(builder);

        assertSame(builder, signed);
        verify(mockStrategy).sign(builder, client.getCustomHeaders());
    }

    // ------------------------------------------------------------------
    // chat (single message) flows
    // ------------------------------------------------------------------

    @Test
    public void testChatSimpleTextMessage() throws Exception {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        ConversationObserver observer = mock(ConversationObserver.class);
        client.setConversationObserver(observer);

        String response = client.chat("Say hi");

        assertEquals("Hello World", response);
        assertTrue(client.lastRequest.url().endsWith(
                "/v1/projects/test-project/locations/us-central1/publishers/google/models/gemini-2.0-flash-exp:generateContent"));

        JSONObject body = new JSONObject(client.lastRequest.getBody());
        JSONArray contents = body.getJSONArray("contents");
        assertEquals(1, contents.length());
        assertEquals("user", contents.getJSONObject(0).getString("role"));
        JSONArray parts = contents.getJSONObject(0).getJSONArray("parts");
        assertEquals(1, parts.length());
        assertEquals("Say hi", parts.getJSONObject(0).getString("text"));

        ArgumentCaptor<ConversationObserver.Message> captor =
                ArgumentCaptor.forClass(ConversationObserver.Message.class);
        verify(observer, times(2)).addMessage(captor.capture());
        assertEquals("DMTools", captor.getAllValues().get(0).getAuthor());
        assertEquals("Say hi", captor.getAllValues().get(0).getText());
        assertEquals("AI", captor.getAllValues().get(1).getAuthor());
        assertEquals("Hello World", captor.getAllValues().get(1).getText());
    }

    @Test
    public void testChatWithoutObserver() throws Exception {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        assertEquals("Hello World", client.chat(null, "Hi there"));
        // null model falls back to the configured model
        assertTrue(client.lastRequest.url().contains("/models/gemini-2.0-flash-exp:generateContent"));
    }

    @Test
    public void testChatWithModelOverride() throws Exception {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        client.chat("gemini-1.5-pro", "Hi");
        assertTrue(client.lastRequest.url().contains("/models/gemini-1.5-pro:generateContent"));
    }

    @Test
    public void testChatWithSingleImageFile() throws Exception {
        File image = temporaryFolder.newFile("image.png");
        Files.write(image.toPath(), new byte[]{1, 2, 3, 4});

        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        String response = client.chat(MODEL, "What is this?", image);

        assertEquals("Hello World", response);
        JSONArray parts = firstParts(client);
        assertEquals(2, parts.length());
        assertEquals("What is this?", parts.getJSONObject(0).getString("text"));
        JSONObject inlineData = parts.getJSONObject(1).getJSONObject("inline_data");
        assertEquals("image/png", inlineData.getString("mime_type"));
        assertArrayEquals(new byte[]{1, 2, 3, 4},
                Base64.getDecoder().decode(inlineData.getString("data")));
    }

    @Test
    public void testChatWithNullImageFileFallsBackToTextOnly() throws Exception {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        client.chat(MODEL, "Text only", (File) null);
        JSONArray parts = firstParts(client);
        assertEquals(1, parts.length());
        assertEquals("Text only", parts.getJSONObject(0).getString("text"));
    }

    @Test
    public void testChatWithFileListSkipsMissingFiles() throws Exception {
        File doc = temporaryFolder.newFile("doc.pdf");
        Files.write(doc.toPath(), "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        File missing = new File(temporaryFolder.getRoot(), "missing.png");

        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        client.chat(MODEL, "Analyze", Arrays.asList(doc, missing));

        JSONArray parts = firstParts(client);
        // text part + one file part; the missing file must be skipped
        assertEquals(2, parts.length());
        assertEquals("application/pdf",
                parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"));
    }

    @Test
    public void testChatWithEmptyMessageAndNoFilesBuildsEmptyParts() throws Exception {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        client.chat(MODEL, "   ", (List<File>) null);
        assertEquals(0, firstParts(client).length());
    }

    // ------------------------------------------------------------------
    // chatWithFiles MCP tool
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFilesFiltersInvalidPaths() throws Exception {
        File text = temporaryFolder.newFile("notes.txt");
        Files.write(text.toPath(), "some notes".getBytes(StandardCharsets.UTF_8));

        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        String response = client.chatWithFiles("Summarize", new String[]{
                text.getAbsolutePath(), "  ", "/nonexistent/file.png"});

        assertEquals("Hello World", response);
        JSONArray parts = firstParts(client);
        assertEquals(2, parts.length());
        assertEquals("text/plain",
                parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"));
    }

    @Test
    public void testChatWithFilesRejectsNullMessage() throws IOException {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        assertThrows(IllegalArgumentException.class, () ->
                client.chatWithFiles(null, new String[]{"some.txt"}));
    }

    @Test
    public void testChatWithFilesRejectsBlankMessage() throws IOException {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        assertThrows(IllegalArgumentException.class, () ->
                client.chatWithFiles("  ", new String[]{"some.txt"}));
    }

    @Test
    public void testChatWithFilesRejectsEmptyPaths() throws IOException {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        assertThrows(IllegalArgumentException.class, () -> client.chatWithFiles("Hi", null));
        assertThrows(IllegalArgumentException.class, () -> client.chatWithFiles("Hi", new String[0]));
    }

    // ------------------------------------------------------------------
    // chat (multi-turn) flows
    // ------------------------------------------------------------------

    @Test
    public void testChatMultiTurnNormalizesAssistantRole() throws Exception {
        TestableClient client = new TestableClient(SUCCESS_RESPONSE);

        String response = client.chat(
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null),
                new Message("user", "How are you?", null));

        assertEquals("Hello World", response);
        JSONArray contents = new JSONObject(client.lastRequest.getBody()).getJSONArray("contents");
        assertEquals(3, contents.length());
        assertEquals("user", contents.getJSONObject(0).getString("role"));
        // "assistant" must be normalized to Gemini's "model" role
        assertEquals("model", contents.getJSONObject(1).getString("role"));
        assertEquals("Hi there", contents.getJSONObject(1).getJSONArray("parts")
                .getJSONObject(0).getString("text"));
    }

    @Test
    public void testChatMultiTurnWithFilesAndEmptyText() throws Exception {
        File gif = temporaryFolder.newFile("anim.gif");
        Files.write(gif.toPath(), new byte[]{9, 9, 9});
        File missing = new File(temporaryFolder.getRoot(), "nope.txt");

        TestableClient client = new TestableClient(SUCCESS_RESPONSE);
        client.chat(MODEL,
                new Message("user", null, Arrays.asList(gif, missing)),
                new Message("model", "", Collections.emptyList()));

        JSONArray contents = new JSONObject(client.lastRequest.getBody()).getJSONArray("contents");
        JSONArray firstParts = contents.getJSONObject(0).getJSONArray("parts");
        // only the readable gif is attached, empty text and missing file are skipped
        assertEquals(1, firstParts.length());
        assertEquals("image/gif", firstParts.getJSONObject(0)
                .getJSONObject("inline_data").getString("mime_type"));
        // a message with empty text and no files yields zero parts
        assertEquals(0, contents.getJSONObject(1).getJSONArray("parts").length());
    }

    // ------------------------------------------------------------------
    // Response parsing / error handling (through chat)
    // ------------------------------------------------------------------

    @Test
    public void testChatPropagatesApiError() throws IOException {
        TestableClient client = new TestableClient(ERROR_RESPONSE);
        IOException e = assertThrows(IOException.class, () -> client.chat("Hi"));
        assertTrue(e.getMessage().contains("Invalid request"));
    }

    @Test
    public void testChatPropagatesBlockedContent() throws IOException {
        TestableClient client = new TestableClient(BLOCKED_RESPONSE);
        IOException e = assertThrows(IOException.class, () -> client.chat("Hi"));
        assertTrue(e.getMessage().contains("blocked by safety filters"));
    }

    @Test
    public void testChatPropagatesSafetyFinishReason() throws IOException {
        TestableClient client = new TestableClient(SAFETY_FINISH_RESPONSE);
        IOException e = assertThrows(IOException.class, () -> client.chat("Hi"));
        assertTrue(e.getMessage().contains("blocked by safety filters"));
    }

    @Test
    public void testChatFailsOnEmptyCandidates() throws IOException {
        TestableClient client = new TestableClient(EMPTY_CANDIDATES_RESPONSE);
        IOException e = assertThrows(IOException.class, () -> client.chat("Hi"));
        assertTrue(e.getMessage().contains("No text content found in response"));
    }

    @Test
    public void testChatFailsWhenNoTextParts() throws IOException {
        TestableClient client = new TestableClient(NO_TEXT_RESPONSE);
        IOException e = assertThrows(IOException.class, () -> client.chat("Hi"));
        assertTrue(e.getMessage().contains("No text content found in response"));
    }

    @Test
    public void testChatWrapsMalformedResponse() throws IOException {
        TestableClient client = new TestableClient("not a json");
        IOException e = assertThrows(IOException.class, () -> client.chat("Hi"));
        assertTrue(e.getMessage().contains("Failed to parse response"));
    }

    @Test
    public void testChatPropagatesPostIOException() throws IOException {
        TestableClient client = new TestableClient(null);
        assertThrows(IOException.class, () -> client.chat("Hi"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private File writeCredentialsFile() throws IOException {
        File credentialsFile = temporaryFolder.newFile("service-account.json");
        Files.write(credentialsFile.toPath(), credentialsJson.getBytes(StandardCharsets.UTF_8));
        return credentialsFile;
    }

    private static JSONArray firstParts(TestableClient client) {
        return new JSONObject(client.lastRequest.getBody())
                .getJSONArray("contents")
                .getJSONObject(0)
                .getJSONArray("parts");
    }

    /**
     * Test subclass that stubs the HTTP layer by overriding
     * {@link com.github.istin.dmtools.networking.AbstractRestClient#post(GenericRequest)}.
     * A {@code null} canned response simulates an IOException from the transport.
     */
    private static class TestableClient extends VertexAIGeminiClient {

        private final String cannedResponse;
        private GenericRequest lastRequest;

        TestableClient(String cannedResponse) throws IOException {
            this(cannedResponse, null);
        }

        TestableClient(String cannedResponse, Map<String, String> customHeaders) throws IOException {
            super(PROJECT_ID, LOCATION, MODEL, null, credentialsJson, customHeaders, "v1");
            this.cannedResponse = cannedResponse;
        }

        @Override
        public String post(GenericRequest genericRequest) throws IOException {
            this.lastRequest = genericRequest;
            if (cannedResponse == null) {
                throw new IOException("simulated transport failure");
            }
            return cannedResponse;
        }
    }
}
