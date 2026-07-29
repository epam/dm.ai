// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.openai;

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
 * Unit tests for {@link OpenAIClient} covering constructors, request signing,
 * request body building (temperature / max tokens / metadata branches), all
 * response parsing branches, image handling, chatWithFiles validation and the
 * multi-message chat overloads. The HTTP layer is mocked via Mockito spies -
 * no real network calls are made.
 */
public class OpenAIClientCoverageTest {

    private static final String BASE_PATH = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "sk-test-key";
    private static final String MODEL = "gpt-4";

    private static final String OPENAI_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"Hello from OpenAI\"}}]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private OpenAIClient newClient() throws IOException {
        return new OpenAIClient(BASE_PATH, API_KEY, MODEL);
    }

    private OpenAIClient spyWithResponse(OpenAIClient client, String response) throws IOException {
        OpenAIClient spy = Mockito.spy(client);
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
    public void testConstructorWithObserver() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, observer);
        assertEquals(observer, client.getConversationObserver());
        assertEquals(4096, client.getMaxTokens());
        assertEquals(-1, client.getTemperature(), 0.001);
    }

    @Test
    public void testConstructorWithCustomHeadersCopiesMap() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Org", "org-1");
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 1000, 0.5, null, headers);
        assertNotNull(client.getCustomHeaders());
        assertEquals("org-1", client.getCustomHeaders().get("X-Org"));
        // ensure the map was copied
        headers.put("X-Other", "other");
        assertNull(client.getCustomHeaders().get("X-Other"));
    }

    @Test
    public void testConstructorWithNullCustomHeadersAndNullLogger() throws IOException {
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 1000, 0.5,
                "max_tokens", null, null, null);
        assertNull(client.getCustomHeaders());
        assertEquals("max_tokens", client.getMaxTokensParamName());
        assertEquals(MODEL, client.getName());
    }

    // ------------------------------------------------------------------
    // sign / path / hash
    // ------------------------------------------------------------------

    @Test
    public void testSignAddsContentTypeAndAuthorization() throws IOException {
        OpenAIClient client = newClient();
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertEquals("Bearer " + API_KEY, signed.header("Authorization"));
    }

    @Test
    public void testSignAddsCustomHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Org", "org-1");
        headers.put("X-Env", "test");
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 100, 0.5, null, headers);
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("org-1", signed.header("X-Org"));
        assertEquals("test", signed.header("X-Env"));
    }

    @Test
    public void testSignWithEmptyCustomHeadersSkipsThem() throws IOException {
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 100, 0.5,
                null, new HashMap<>());
        Request.Builder builder = new Request.Builder().url("https://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals("application/json", signed.header("Content-Type"));
        assertNull(signed.header("X-Org"));
    }

    @Test
    public void testBuildHashForPostRequestNullBody() throws IOException {
        OpenAIClient client = newClient();
        GenericRequest request = mock(GenericRequest.class);
        when(request.getBody()).thenReturn(null);
        assertEquals("http://x", client.buildHashForPostRequest(request, "http://x"));
    }

    // ------------------------------------------------------------------
    // chat(String) / chat(String model, String message) and body building
    // ------------------------------------------------------------------

    @Test
    public void testChatSimpleMessage() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        OpenAIClient client = newClient();
        client.setConversationObserver(observer);
        OpenAIClient spy = spyWithResponse(client, OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat("Hi"));

        verify(observer, atLeast(2)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"model\":\"" + MODEL + "\""));
        assertTrue(body.contains("\"max_completion_tokens\":4096"));
        assertTrue(body.contains("\"role\":\"user\""));
        // default temperature is -1 -> parameter must be skipped
        assertFalse(body.contains("temperature"));
    }

    @Test
    public void testChatWithTemperatureAndCustomMaxTokensParam() throws Exception {
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 512, 0.7, "max_tokens", null, null);
        OpenAIClient spy = spyWithResponse(client, OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"temperature\":0.7"));
        assertTrue(body.contains("\"max_tokens\":512"));
        assertFalse(body.contains("max_completion_tokens"));
    }

    @Test
    public void testChatWithEmptyMaxTokensParamNameSkipsParam() throws Exception {
        OpenAIClient client = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 512, 0.7, "  ", null, null);
        OpenAIClient spy = spyWithResponse(client, OPENAI_RESPONSE);

        spy.chat("Hi");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertFalse(body.contains("max_tokens"));
        assertFalse(body.contains("max_completion_tokens"));
    }

    @Test
    public void testChatNullModelFallsBackToDefault() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat(null, "Hi"));
    }

    @Test
    public void testChatWithMetadata() throws Exception {
        OpenAIClient client = newClient();
        client.setMetadata(new Metadata("agent-1", "context-1"));
        OpenAIClient spy = spyWithResponse(client, OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"metadata\""));
        assertTrue(captor.getValue().getBody().contains("agent-1"));
    }

    // ------------------------------------------------------------------
    // Response parsing branches
    // ------------------------------------------------------------------

    @Test
    public void testResponseContentNotStringReturnsEmpty() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"content\":123}}]}";
        OpenAIClient spy = spyWithResponse(newClient(), response);
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseChoiceWithoutMessageReturnsEmpty() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), "{\"choices\":[{}]}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseNoChoicesWithErrorReturnsRawResponse() throws Exception {
        String response = "{\"error\":\"something went wrong\"}";
        OpenAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testResponseNoChoicesWithUsageLogsAndReturnsEmpty() throws Exception {
        String response = "{\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}";
        OpenAIClient spy = spyWithResponse(newClient(), response);
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testResponseEmptyChoicesWithErrorReturnsRawResponse() throws Exception {
        String response = "{\"choices\":[],\"error\":\"empty\"}";
        OpenAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testInvalidJsonWithErrorReturnsRawResponse() throws Exception {
        String response = "gateway error: upstream failed";
        OpenAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testInvalidJsonWithoutErrorReturnsEmpty() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), "this is not json");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testObserverReceivesResponseWithModelName() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        OpenAIClient client = newClient();
        client.setConversationObserver(observer);
        OpenAIClient spy = spyWithResponse(client, OPENAI_RESPONSE);

        spy.chat("Hi");

        ArgumentCaptor<ConversationObserver.Message> captor =
                ArgumentCaptor.forClass(ConversationObserver.Message.class);
        verify(observer, atLeast(2)).addMessage(captor.capture());
        ConversationObserver.Message last = captor.getValue();
        assertEquals(MODEL, last.getAuthor());
        assertEquals("Hello from OpenAI", last.getText());
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, File imageFile)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithImage() throws Exception {
        File png = createImage("openai-image.png", "png");
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat(MODEL, "Describe this", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("data:image/png;base64,"));
    }

    @Test
    public void testChatWithNullImageIsTextOnly() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat(MODEL, "Hi", (File) null));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().getBody().contains("image_url"));
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, List<File>)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFileList() throws Exception {
        File png = createImage("list-image.png", "png");
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat(MODEL, "Hi", Collections.singletonList(png)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("image_url"));
    }

    @Test
    public void testChatWithNullFileList() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat(MODEL, "Hi", (List<File>) null));
    }

    @Test
    public void testChatWithEmptyFileList() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat(MODEL, "Hi", new ArrayList<>()));
    }

    // ------------------------------------------------------------------
    // chatWithFiles(String, String[])
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFilesNullMessage() throws Exception {
        OpenAIClient client = newClient();
        try {
            client.chatWithFiles(null, new String[]{"file.png"});
            fail("Expected IllegalArgumentException for null message");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Message cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesEmptyMessage() throws Exception {
        OpenAIClient client = newClient();
        try {
            client.chatWithFiles("   ", new String[]{"file.png"});
            fail("Expected IllegalArgumentException for blank message");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Message cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesNullPaths() throws Exception {
        OpenAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", null);
            fail("Expected IllegalArgumentException for null file paths");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File paths array cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesEmptyPaths() throws Exception {
        OpenAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", new String[0]);
            fail("Expected IllegalArgumentException for empty file paths");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File paths array cannot be null or empty"));
        }
    }

    @Test
    public void testChatWithFilesBlankPathsOnly() throws Exception {
        OpenAIClient client = newClient();
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
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        // missing file is kept in the list (only a warning is logged); image
        // processing inside chat catches the IOException and sends text only
        assertEquals("Hello from OpenAI", spy.chatWithFiles("Hi",
                new String[]{missing.getAbsolutePath()}));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertFalse(body.contains("image_url"));
        assertTrue(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatWithFilesValidImage() throws Exception {
        File png = createImage("chatfiles-image.png", "png");
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chatWithFiles("Describe this",
                new String[]{" " + png.getAbsolutePath() + " "}));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("image_url"));
    }

    // ------------------------------------------------------------------
    // chat(String model, Message... messages) and chat(Message...)
    // ------------------------------------------------------------------

    @Test
    public void testChatMessagesTextOnly() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        OpenAIClient client = newClient();
        client.setConversationObserver(observer);
        OpenAIClient spy = spyWithResponse(client, OPENAI_RESPONSE);

        String result = spy.chat(MODEL,
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null));
        assertEquals("Hello from OpenAI", result);

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
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat((String) null, new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesVarargsUsesDefaultModel() throws Exception {
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);
        assertEquals("Hello from OpenAI", spy.chat(new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesWithImageFile() throws Exception {
        File png = createImage("msg-image.png", "png");
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat(MODEL,
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
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat(MODEL,
                new Message("user", null, Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image_url\""));
        // null text -> no text part, only image
        assertFalse(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatMessagesWithUnreadableImageLogsWarning() throws Exception {
        // nonexistent file -> IOException inside image processing is caught
        // and logged, the remaining text part is still sent
        File missing = new File(temporaryFolder.getRoot(), "broken.png");
        OpenAIClient spy = spyWithResponse(newClient(), OPENAI_RESPONSE);

        assertEquals("Hello from OpenAI", spy.chat(MODEL,
                new Message("user", "Hello", Collections.singletonList(missing))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"text\""));
        assertFalse(body.contains("image_url"));
    }
}
