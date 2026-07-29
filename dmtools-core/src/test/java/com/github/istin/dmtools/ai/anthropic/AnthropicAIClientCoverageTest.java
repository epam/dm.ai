// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.anthropic;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
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
 * Unit tests for {@link AnthropicAIClient} covering constructors, request signing,
 * request body building, all response parsing branches (OpenAI-compatible and native
 * Anthropic formats), image handling, chatWithFiles validation and model listing.
 * The HTTP layer is mocked via Mockito spies - no real network calls are made.
 */
public class AnthropicAIClientCoverageTest {

    private static final String BASE_PATH = "https://api.anthropic.com/v1/chat/completions";
    private static final String MODEL = "claude-sonnet-4-20250514";

    private static final String ANTHROPIC_RESPONSE =
            "{\"content\":[{\"type\":\"text\",\"text\":\"Hello from Claude\"}]}";
    private static final String OPENAI_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"Hello from OpenAI\"}}]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private AnthropicAIClient newClient() throws IOException {
        return new AnthropicAIClient(BASE_PATH, MODEL);
    }

    private AnthropicAIClient spyWithResponse(AnthropicAIClient client, String response) throws IOException {
        AnthropicAIClient spy = Mockito.spy(client);
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
        AnthropicAIClient client = newClient();
        assertEquals(MODEL, client.getModel());
        assertEquals(MODEL, client.getName());
        assertEquals(4096, client.getMaxTokens());
        assertNull(client.getConversationObserver());
        assertNull(client.getCustomHeaders());
        assertEquals("assistant", client.roleName());
        assertEquals(700, client.getTimeout());
    }

    @Test
    public void testConstructorWithObserver() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        AnthropicAIClient client = new AnthropicAIClient(BASE_PATH, MODEL, observer);
        assertEquals(observer, client.getConversationObserver());
        assertEquals(4096, client.getMaxTokens());
    }

    @Test
    public void testConstructorWithMaxTokens() throws IOException {
        AnthropicAIClient client = new AnthropicAIClient(BASE_PATH, MODEL, 1000, null);
        assertEquals(1000, client.getMaxTokens());
    }

    @Test
    public void testConstructorWithCustomHeadersCopiesMap() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "test-key");
        AnthropicAIClient client = new AnthropicAIClient(BASE_PATH, MODEL, 100, null, headers);
        assertNotNull(client.getCustomHeaders());
        assertEquals("test-key", client.getCustomHeaders().get("x-api-key"));
        // ensure the map was copied
        headers.put("X-Other", "other");
        assertNull(client.getCustomHeaders().get("X-Other"));
    }

    @Test
    public void testConstructorWithNullLoggerFallsBackToDefault() throws IOException {
        AnthropicAIClient client = new AnthropicAIClient(BASE_PATH, MODEL, 100, null, null, null);
        assertEquals(MODEL, client.getModel());
        assertNull(client.getCustomHeaders());
    }

    @Test
    public void testPath() throws IOException {
        AnthropicAIClient client = newClient();
        assertEquals(BASE_PATH + "/extra", client.path("/extra"));
    }

    @Test
    public void testSignAddsContentTypeOnly() throws IOException {
        AnthropicAIClient client = newClient();
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertNull(signed.header("x-api-key"));
    }

    @Test
    public void testSignAddsCustomHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", "test-key");
        headers.put("anthropic-version", "2023-06-01");
        AnthropicAIClient client = new AnthropicAIClient(BASE_PATH, MODEL, 100, null, headers);
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertEquals("test-key", signed.header("x-api-key"));
        assertEquals("2023-06-01", signed.header("anthropic-version"));
    }

    @Test
    public void testBuildHashForPostRequest() throws IOException {
        AnthropicAIClient client = newClient();
        GenericRequest request = mock(GenericRequest.class);
        when(request.getBody()).thenReturn("body");
        assertEquals("http://xbody", client.buildHashForPostRequest(request, "http://x"));
    }

    // ------------------------------------------------------------------
    // chat(String) and chat(String model, String message)
    // ------------------------------------------------------------------

    @Test
    public void testChatSimpleMessageNativeResponse() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        AnthropicAIClient client = newClient();
        client.setConversationObserver(observer);
        AnthropicAIClient spy = spyWithResponse(client, ANTHROPIC_RESPONSE);

        assertEquals("Hello from Claude", spy.chat("Hi"));

        verify(observer, atLeast(2)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"model\":\"" + MODEL + "\""));
        assertTrue(body.contains("\"max_tokens\":4096"));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatOpenAICompatibleStringContent() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat("Hi"));
    }

    @Test
    public void testChatNullModelFallsBackToDefault() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(null, "Hi"));
    }

    @Test
    public void testChatWithMetadata() throws Exception {
        AnthropicAIClient client = newClient();
        client.setMetadata(new Metadata("agent-1", "context-1"));
        AnthropicAIClient spy = spyWithResponse(client, ANTHROPIC_RESPONSE);

        assertEquals("Hello from Claude", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"metadata\""));
        assertTrue(captor.getValue().getBody().contains("agent-1"));
    }

    // ------------------------------------------------------------------
    // Response parsing branches
    // ------------------------------------------------------------------

    @Test
    public void testResponseChoicesWithContentArray() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"content\":[{\"text\":\"Array content\"}]}}]}";
        AnthropicAIClient spy = spyWithResponse(newClient(), response);
        assertEquals("Array content", spy.chat("Hi"));
    }

    @Test
    public void testResponseChoicesWithEmptyContentArray() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"content\":[]}}]}";
        AnthropicAIClient spy = spyWithResponse(newClient(), response);
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseChoicesWithNullContent() throws Exception {
        String response = "{\"choices\":[{\"message\":{}}]}";
        AnthropicAIClient spy = spyWithResponse(newClient(), response);
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseChoicesWithoutMessage() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), "{\"choices\":[{}]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseNativeEmptyContentArray() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), "{\"content\":[]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseNoContentWithErrorReturnsRawResponse() throws Exception {
        String response = "{\"error\":\"something went wrong\"}";
        AnthropicAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testResponseNoContentNoErrorReturnsEmpty() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), "{\"usage\":{\"tokens\":5}}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testInvalidJsonWithErrorReturnsRawResponse() throws Exception {
        String response = "gateway error: upstream failed";
        AnthropicAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testInvalidJsonWithoutErrorReturnsEmpty() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), "this is not json");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testUnparseableContentFallsBackToEmpty() throws Exception {
        // valid JSON, but content[0] is not an object -> exception inside processResponse
        AnthropicAIClient spy = spyWithResponse(newClient(), "{\"content\":[\"just a string\"]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testObserverReceivesResponseWithModelName() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        AnthropicAIClient client = newClient();
        client.setConversationObserver(observer);
        AnthropicAIClient spy = spyWithResponse(client, ANTHROPIC_RESPONSE);

        spy.chat("Hi");

        ArgumentCaptor<ConversationObserver.Message> captor =
                ArgumentCaptor.forClass(ConversationObserver.Message.class);
        verify(observer, atLeast(2)).addMessage(captor.capture());
        ConversationObserver.Message last = captor.getValue();
        assertEquals(MODEL, last.getAuthor());
        assertEquals("Hello from Claude", last.getText());
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, File imageFile)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithImage() throws Exception {
        File png = createImage("claude-image.png", "png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);

        assertEquals("Hello from Claude", spy.chat(MODEL, "Describe this", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image\""));
        assertTrue(body.contains("\"media_type\":\"image/png\""));
        assertTrue(body.contains("\"type\":\"base64\""));
        assertTrue(body.contains("\"filename\":\"claude-image.png\""));
    }

    @Test
    public void testChatWithNonexistentImageFallsBackToText() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "does-not-exist.png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        // AIFileFilter drops nonexistent files -> text only path
        assertEquals("Hello from Claude", spy.chat(MODEL, "Hi", missing));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("image"));
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, List<File>)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFileList() throws Exception {
        File png = createImage("list-image.png", "png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(MODEL, "Hi", Collections.singletonList(png)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image\""));
    }

    @Test
    public void testChatWithNullFileList() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(MODEL, "Hi", (List<File>) null));
    }

    @Test
    public void testChatWithEmptyFileList() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(MODEL, "Hi", new ArrayList<>()));
    }

    // ------------------------------------------------------------------
    // chatWithFiles(String, String[])
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFilesNullMessage() throws Exception {
        AnthropicAIClient client = newClient();
        try {
            client.chatWithFiles(null, new String[]{"file.png"});
            fail("Expected IllegalArgumentException for null message");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Message cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesEmptyMessage() throws Exception {
        AnthropicAIClient client = newClient();
        try {
            client.chatWithFiles("   ", new String[]{"file.png"});
            fail("Expected IllegalArgumentException for blank message");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Message cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesNullPaths() throws Exception {
        AnthropicAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", null);
            fail("Expected IllegalArgumentException for null file paths");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File paths array cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesEmptyPaths() throws Exception {
        AnthropicAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", new String[0]);
            fail("Expected IllegalArgumentException for empty file paths");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File paths array cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesBlankPathsOnly() throws Exception {
        AnthropicAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", new String[]{"  ", ""});
            fail("Expected IllegalArgumentException when no valid files found");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No valid files found"));
        }
    }

    @Test
    public void testChatWithFilesNonexistentPathStillChats() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        // missing file is kept in the list (only a warning is logged), then filtered
        // by AIFileFilter inside chat -> text-only request
        assertEquals("Hello from Claude", spy.chatWithFiles("Hi",
                new String[]{missing.getAbsolutePath()}));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("image"));
    }

    @Test
    public void testChatWithFilesValidImage() throws Exception {
        File png = createImage("chatfiles-image.png", "png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chatWithFiles("Describe this",
                new String[]{" " + png.getAbsolutePath() + " "}));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image\""));
    }

    // ------------------------------------------------------------------
    // chat(String model, Message... messages) and chat(Message...)
    // ------------------------------------------------------------------

    @Test
    public void testChatMessagesTextOnly() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        AnthropicAIClient client = newClient();
        client.setConversationObserver(observer);
        AnthropicAIClient spy = spyWithResponse(client, ANTHROPIC_RESPONSE);

        String result = spy.chat(MODEL,
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null));
        assertEquals("Hello from Claude", result);

        verify(observer, atLeast(3)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatMessagesNullModelUsesDefault() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chat((String) null, new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesVarargsUsesDefaultModel() throws Exception {
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);
        assertEquals("Hello from Claude", spy.chat(new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesWithImageFile() throws Exception {
        File png = createImage("msg-image.png", "png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);

        assertEquals("Hello from Claude", spy.chat(MODEL,
                new Message("user", "What is this?", Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image\""));
        assertTrue(body.contains("\"media_type\":\"image/png\""));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatMessagesWithImageFileNullText() throws Exception {
        File png = createImage("msg-image-nulltext.png", "png");
        AnthropicAIClient spy = spyWithResponse(newClient(), ANTHROPIC_RESPONSE);

        assertEquals("Hello from Claude", spy.chat(MODEL,
                new Message("user", null, Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image\""));
        // null text -> no text part, only image
        assertFalse(body.contains("\"type\":\"text\""));
    }

    // ------------------------------------------------------------------
    // getAvailableModels
    // ------------------------------------------------------------------

    @Test
    public void testGetAvailableModels() throws IOException {
        AnthropicAIClient spy = Mockito.spy(newClient());
        doReturn("{\"models\":[]}").when(spy).execute(any(GenericRequest.class));

        assertEquals("{\"models\":[]}", spy.getAvailableModels());

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        assertEquals("https://api.anthropic.com/v1/models", captor.getValue().url());
    }
}
