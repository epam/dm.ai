// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.ollama;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OllamaAIClient} covering all constructors, request signing
 * (API key and custom headers), request body building (num_ctx / max_tokens / metadata),
 * response parsing branches, image handling, chatWithFiles validation and the
 * Message-varargs chat overloads. The HTTP layer is mocked via Mockito spies -
 * no real network calls are made.
 */
public class OllamaAIClientCoverageTest {

    private static final String BASE_PATH = "http://localhost:11434";
    private static final String MODEL = "llama3";

    private static final String OLLAMA_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"Hello from Ollama\"}}]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private OllamaAIClient newClient() throws IOException {
        return new OllamaAIClient(BASE_PATH, MODEL);
    }

    private OllamaAIClient spyWithResponse(OllamaAIClient client, String response) throws IOException {
        OllamaAIClient spy = Mockito.spy(client);
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
    public void testDefaultConstructor() throws IOException {
        OllamaAIClient client = newClient();
        assertEquals(MODEL, client.getModel());
        assertEquals(MODEL, client.getName());
        assertEquals(16384, client.getNumCtx());
        assertEquals(-1, client.getNumPredict());
        assertNull(client.getConversationObserver());
        assertNull(client.getCustomHeaders());
        assertNull(client.getApiKey());
        assertEquals("assistant", client.roleName());
        assertEquals(700, client.getTimeout());
    }

    @Test
    public void testConstructorWithObserver() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, observer);
        assertEquals(observer, client.getConversationObserver());
        assertEquals(16384, client.getNumCtx());
        assertEquals(-1, client.getNumPredict());
    }

    @Test
    public void testConstructorWithNumCtxAndNumPredict() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, 4096, 512, observer);
        assertEquals(4096, client.getNumCtx());
        assertEquals(512, client.getNumPredict());
        assertEquals(observer, client.getConversationObserver());
    }

    @Test
    public void testConstructorWithCustomHeadersCopiesMap() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom", "value");
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, 4096, 512, null, headers);
        assertNotNull(client.getCustomHeaders());
        assertEquals("value", client.getCustomHeaders().get("X-Custom"));
        // ensure the map was copied
        headers.put("X-Other", "other");
        assertNull(client.getCustomHeaders().get("X-Other"));
    }

    @Test
    public void testConstructorWithApiKey() throws IOException {
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, "secret-key");
        assertEquals("secret-key", client.getApiKey());
        assertNull(client.getCustomHeaders());
        assertEquals(16384, client.getNumCtx());
    }

    @Test
    public void testConstructorWithObserverAndApiKey() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, observer, "secret-key");
        assertEquals("secret-key", client.getApiKey());
        assertEquals(observer, client.getConversationObserver());
    }

    @Test
    public void testFullConstructorWithApiKeyAndHeaders() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom", "value");
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, "secret-key", 2048, 256, observer, headers);
        assertEquals("secret-key", client.getApiKey());
        assertEquals(2048, client.getNumCtx());
        assertEquals(256, client.getNumPredict());
        assertEquals(observer, client.getConversationObserver());
        assertEquals("value", client.getCustomHeaders().get("X-Custom"));
    }

    @Test
    public void testConstructorWithLoggerInjection() throws IOException {
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, "secret-key", 2048, 256,
                null, null, LogManager.getLogger(OllamaAIClientCoverageTest.class));
        assertEquals("secret-key", client.getApiKey());
        assertEquals(2048, client.getNumCtx());
        assertNull(client.getCustomHeaders());
    }

    @Test
    public void testConstructorWithNullLoggerFallsBackToDefault() throws IOException {
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, 2048, 256, null, null, null);
        assertEquals(MODEL, client.getModel());
        assertNull(client.getApiKey());
        assertNull(client.getCustomHeaders());
    }

    @Test
    public void testPath() throws IOException {
        OllamaAIClient client = newClient();
        assertEquals(BASE_PATH + "/extra", client.path("/extra"));
    }

    // ------------------------------------------------------------------
    // sign(Request.Builder)
    // ------------------------------------------------------------------

    @Test
    public void testSignAddsContentTypeOnly() throws IOException {
        OllamaAIClient client = newClient();
        Request.Builder builder = new Request.Builder().url("http://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertNull(signed.header("Authorization"));
    }

    @Test
    public void testSignAddsApiKeyAuthorization() throws IOException {
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, "secret-key");
        Request.Builder builder = new Request.Builder().url("http://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertEquals("Bearer secret-key", signed.header("Authorization"));
    }

    @Test
    public void testSignSkipsBlankApiKey() throws IOException {
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, "   ");
        Request.Builder builder = new Request.Builder().url("http://example.com").get();
        Request signed = client.sign(builder).build();
        assertNull(signed.header("Authorization"));
    }

    @Test
    public void testSignAddsCustomHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom", "value");
        headers.put("X-Env", "test");
        OllamaAIClient client = new OllamaAIClient(BASE_PATH, MODEL, 4096, 512, null, headers);
        Request.Builder builder = new Request.Builder().url("http://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertEquals("value", signed.header("X-Custom"));
        assertEquals("test", signed.header("X-Env"));
    }

    @Test
    public void testBuildHashForPostRequest() throws IOException {
        OllamaAIClient client = newClient();
        GenericRequest request = mock(GenericRequest.class);
        when(request.getBody()).thenReturn("body");
        assertEquals("http://xbody", client.buildHashForPostRequest(request, "http://x"));
    }

    // ------------------------------------------------------------------
    // chat(String) and chat(String model, String message)
    // ------------------------------------------------------------------

    @Test
    public void testChatSimpleMessage() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        OllamaAIClient client = newClient();
        client.setConversationObserver(observer);
        OllamaAIClient spy = spyWithResponse(client, OLLAMA_RESPONSE);

        assertEquals("Hello from Ollama", spy.chat("Hi"));

        verify(observer, atLeast(2)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals(BASE_PATH + "/v1/chat/completions", captor.getValue().url());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"model\":\"" + MODEL + "\""));
        assertTrue(body.contains("\"temperature\":0.1"));
        assertTrue(body.contains("\"num_ctx\":16384"));
        // numPredict == -1 -> no max_tokens
        assertFalse(body.contains("max_tokens"));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"content\":\"Hi\""));
    }

    @Test
    public void testChatWithNumPredictAddsMaxTokens() throws Exception {
        OllamaAIClient spy = spyWithResponse(
                new OllamaAIClient(BASE_PATH, MODEL, 4096, 512, null), OLLAMA_RESPONSE);

        assertEquals("Hello from Ollama", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"max_tokens\":512"));
        assertTrue(body.contains("\"num_ctx\":4096"));
    }

    @Test
    public void testChatNullModelFallsBackToDefault() throws Exception {
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chat(null, "Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"model\":\"" + MODEL + "\""));
    }

    @Test
    public void testChatWithMetadata() throws Exception {
        OllamaAIClient client = newClient();
        client.setMetadata(new Metadata("agent-1", "context-1"));
        OllamaAIClient spy = spyWithResponse(client, OLLAMA_RESPONSE);

        assertEquals("Hello from Ollama", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"metadata\""));
        assertTrue(captor.getValue().getBody().contains("agent-1"));
    }

    // ------------------------------------------------------------------
    // Response parsing branches
    // ------------------------------------------------------------------

    @Test
    public void testResponseEmptyChoicesWithErrorReturnsRawResponse() throws Exception {
        String response = "{\"error\":\"something went wrong\"}";
        OllamaAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testResponseEmptyChoicesWithoutErrorReturnsEmpty() throws Exception {
        OllamaAIClient spy = spyWithResponse(newClient(), "{\"usage\":{\"tokens\":5}}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testObserverReceivesResponseWithModelName() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        OllamaAIClient client = newClient();
        client.setConversationObserver(observer);
        OllamaAIClient spy = spyWithResponse(client, OLLAMA_RESPONSE);

        spy.chat("Hi");

        ArgumentCaptor<ConversationObserver.Message> captor =
                ArgumentCaptor.forClass(ConversationObserver.Message.class);
        verify(observer, atLeast(2)).addMessage(captor.capture());
        ConversationObserver.Message last = captor.getValue();
        assertEquals(MODEL, last.getAuthor());
        assertEquals("Hello from Ollama", last.getText());
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, File imageFile)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithImage() throws Exception {
        File png = createImage("ollama-image.png", "png");
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);

        assertEquals("Hello from Ollama", spy.chat(MODEL, "Describe this", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("data:image/png;base64,"));
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, List<File>)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFileList() throws Exception {
        File png = createImage("list-image.png", "png");
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chat(MODEL, "Hi", Collections.singletonList(png)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image_url\""));
    }

    @Test
    public void testChatWithNullFileList() throws Exception {
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chat(MODEL, "Hi", (List<File>) null));
    }

    @Test
    public void testChatWithEmptyFileList() throws Exception {
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chat(MODEL, "Hi", new ArrayList<>()));
    }

    // ------------------------------------------------------------------
    // chatWithFiles(String, String[])
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFilesNullMessage() throws Exception {
        OllamaAIClient client = newClient();
        try {
            client.chatWithFiles(null, new String[]{"file.png"});
            fail("Expected IllegalArgumentException for null message");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Message cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesEmptyMessage() throws Exception {
        OllamaAIClient client = newClient();
        try {
            client.chatWithFiles("   ", new String[]{"file.png"});
            fail("Expected IllegalArgumentException for blank message");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Message cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesNullPaths() throws Exception {
        OllamaAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", null);
            fail("Expected IllegalArgumentException for null file paths");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File paths array cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesEmptyPaths() throws Exception {
        OllamaAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", new String[0]);
            fail("Expected IllegalArgumentException for empty file paths");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File paths array cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesBlankPathsOnly() throws Exception {
        OllamaAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", new String[]{"  ", ""});
            fail("Expected IllegalArgumentException when no valid files found");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No valid files found"));
        }
    }

    @Test
    public void testChatWithFilesValidImage() throws Exception {
        File png = createImage("chatfiles-image.png", "png");
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chatWithFiles("Describe this",
                new String[]{" " + png.getAbsolutePath() + " "}));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image_url\""));
    }

    // ------------------------------------------------------------------
    // chat(String model, Message... messages) and chat(Message...)
    // ------------------------------------------------------------------

    @Test
    public void testChatMessagesTextOnly() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        OllamaAIClient client = newClient();
        client.setConversationObserver(observer);
        OllamaAIClient spy = spyWithResponse(client, OLLAMA_RESPONSE);

        String result = spy.chat(MODEL,
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null));
        assertEquals("Hello from Ollama", result);

        verify(observer, atLeast(3)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"content\":\"Hello\""));
    }

    @Test
    public void testChatMessagesNullModelUsesDefault() throws Exception {
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chat((String) null, new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesVarargsUsesDefaultModel() throws Exception {
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);
        assertEquals("Hello from Ollama", spy.chat(new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesWithImageFile() throws Exception {
        File png = createImage("msg-image.png", "png");
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);

        assertEquals("Hello from Ollama", spy.chat(MODEL,
                new Message("user", "What is this?", Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("data:image/png;base64,"));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatMessagesWithImageFileNullText() throws Exception {
        File png = createImage("msg-image-nulltext.png", "png");
        OllamaAIClient spy = spyWithResponse(newClient(), OLLAMA_RESPONSE);

        assertEquals("Hello from Ollama", spy.chat(MODEL,
                new Message("user", null, Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image_url\""));
        // null text -> no text part, only image
        assertFalse(body.contains("\"type\":\"text\""));
    }
}
