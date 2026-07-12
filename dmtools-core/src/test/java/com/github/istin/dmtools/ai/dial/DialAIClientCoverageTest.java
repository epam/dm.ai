// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
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
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DialAIClient} covering all constructors, request signing,
 * request body building (gpt-5 vs standard params, metadata, agent context),
 * response parsing branches, image handling, the api-version query parameter,
 * chatWithFiles path parsing and the Message-varargs chat overloads.
 * The HTTP layer is mocked via Mockito spies - no real network calls are made.
 */
public class DialAIClientCoverageTest {

    private static final String BASE_PATH = "http://example.com";
    private static final String AUTHORIZATION = "auth-token";
    private static final String MODEL = "test-model";

    private static final String DIAL_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"Hello from Dial\"}}]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DialAIClient newClient() throws IOException {
        return new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL);
    }

    private DialAIClient spyWithResponse(DialAIClient client, String response) throws IOException {
        DialAIClient spy = Mockito.spy(client);
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
        DialAIClient client = newClient();
        assertEquals(MODEL, client.getModel());
        assertEquals(MODEL, client.getName());
        assertNull(client.getApiVersion());
        assertNull(client.getConversationObserver());
        assertEquals("assistant", client.roleName());
        assertEquals(1400, client.getTimeout());
    }

    @Test
    public void testConstructorWithObserver() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        DialAIClient client = new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, observer);
        assertEquals(observer, client.getConversationObserver());
        assertNull(client.getApiVersion());
    }

    @Test
    public void testConstructorWithApiVersion() throws IOException {
        DialAIClient client = new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, "2024-02-01");
        assertEquals("2024-02-01", client.getApiVersion());
        assertNull(client.getConversationObserver());
    }

    @Test
    public void testConstructorWithApiVersionAndObserver() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        DialAIClient client = new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, "2024-02-01", observer);
        assertEquals("2024-02-01", client.getApiVersion());
        assertEquals(observer, client.getConversationObserver());
    }

    @Test
    public void testConstructorWithLoggerInjection() throws IOException {
        ConversationObserver observer = mock(ConversationObserver.class);
        DialAIClient client = new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, "2024-02-01",
                observer, LogManager.getLogger(DialAIClientCoverageTest.class));
        assertEquals("2024-02-01", client.getApiVersion());
        assertEquals(observer, client.getConversationObserver());
    }

    @Test
    public void testConstructorWithNullLoggerFallsBackToDefault() throws IOException {
        DialAIClient client = new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, null, null, null);
        assertEquals(MODEL, client.getModel());
        assertNull(client.getApiVersion());
        assertNull(client.getConversationObserver());
    }

    @Test
    public void testSetMetadataAndConversationObserver() throws IOException {
        DialAIClient client = newClient();
        ConversationObserver observer = mock(ConversationObserver.class);
        client.setConversationObserver(observer);
        assertEquals(observer, client.getConversationObserver());
    }

    @Test
    public void testPath() throws IOException {
        DialAIClient client = newClient();
        assertEquals(BASE_PATH + "/extra", client.path("/extra"));
    }

    // ------------------------------------------------------------------
    // sign(Request.Builder)
    // ------------------------------------------------------------------

    @Test
    public void testSignAddsApiKeyAndContentType() throws IOException {
        DialAIClient client = newClient();
        Request.Builder builder = new Request.Builder().url("http://example.com").get();
        Request signed = client.sign(builder).build();
        assertEquals(AUTHORIZATION, signed.header("api-key"));
        assertEquals("application/json", signed.header("Content-Type"));
    }

    @Test
    public void testBuildHashForPostRequest() throws IOException {
        DialAIClient client = newClient();
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
        DialAIClient client = newClient();
        client.setConversationObserver(observer);
        DialAIClient spy = spyWithResponse(client, DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat("Hi"));

        verify(observer, atLeast(2)).addMessage(any(ConversationObserver.Message.class));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals(BASE_PATH + "openai/deployments/" + MODEL + "/chat/completions",
                captor.getValue().url());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"temperature\":0.1"));
        assertTrue(body.contains("\"max_tokens\":65536"));
        assertFalse(body.contains("max_completion_tokens"));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"content\":\"Hi\""));
    }

    @Test
    public void testChatNullModelFallsBackToDefault() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        assertEquals("Hello from Dial", spy.chat(null, "Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().url().contains("/deployments/" + MODEL + "/"));
    }

    @Test
    public void testChatGpt5ModelUsesMaxCompletionTokens() throws Exception {
        DialAIClient spy = spyWithResponse(
                new DialAIClient(BASE_PATH, AUTHORIZATION, "gpt-5-mini"), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"max_completion_tokens\":65536"));
        assertFalse(body.contains("\"temperature\""));
    }

    @Test
    public void testChatWithMetadata() throws Exception {
        DialAIClient client = newClient();
        client.setMetadata(new Metadata("agent-1", "context-1"));
        DialAIClient spy = spyWithResponse(client, DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"metadata\""));
        assertTrue(captor.getValue().getBody().contains("agent-1"));
    }

    @Test
    public void testChatWithAgentContext() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        // AgentParams.apply resolves the prompt name via PropertyReader; with no matching
        // property configured nothing is added, but the agentContext branch is exercised
        JSONObject agentContext = new JSONObject().put(AI.AgentParams.AGENT_PROMPT, "Be helpful");

        assertEquals("Hello from Dial", spy.chat(MODEL, "Hi", agentContext));

        verify(spy).post(any(GenericRequest.class));
    }

    // ------------------------------------------------------------------
    // api-version query parameter
    // ------------------------------------------------------------------

    @Test
    public void testChatWithApiVersionAppendsQueryParameter() throws Exception {
        DialAIClient spy = spyWithResponse(
                new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, "2024-02-01"), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().url().contains("api-version=2024-02-01"));
    }

    @Test
    public void testChatWithBlankApiVersionSkipsQueryParameter() throws Exception {
        DialAIClient spy = spyWithResponse(
                new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL, "   "), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat("Hi"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertFalse(captor.getValue().url().contains("api-version"));
    }

    // ------------------------------------------------------------------
    // Response parsing branches
    // ------------------------------------------------------------------

    @Test
    public void testResponseEmptyChoicesWithErrorReturnsRawResponse() throws Exception {
        String response = "{\"error\":\"something went wrong\"}";
        DialAIClient spy = spyWithResponse(newClient(), response);
        assertEquals(response, spy.chat("Hi"));
    }

    @Test
    public void testResponseEmptyChoicesWithoutErrorReturnsEmpty() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), "{\"usage\":{\"tokens\":5}}");
        assertEquals("", spy.chat("Hi"));
    }

    @Test
    public void testObserverReceivesResponseWithModelName() throws Exception {
        ConversationObserver observer = mock(ConversationObserver.class);
        DialAIClient client = newClient();
        client.setConversationObserver(observer);
        DialAIClient spy = spyWithResponse(client, DIAL_RESPONSE);

        spy.chat("Hi");

        ArgumentCaptor<ConversationObserver.Message> captor =
                ArgumentCaptor.forClass(ConversationObserver.Message.class);
        verify(observer, atLeast(2)).addMessage(captor.capture());
        ConversationObserver.Message last = captor.getValue();
        assertEquals(MODEL, last.getAuthor());
        assertEquals("Hello from Dial", last.getText());
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, File imageFile)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithImage() throws Exception {
        File png = createImage("dial-image.png", "png");
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat(MODEL, "Describe this", png));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("data:image/png;base64,"));
    }

    @Test
    public void testChatWithFilteredImageFallsBackToTextOnly() throws Exception {
        // A non-existent file is rejected by AIFileFilter, so the text-only branch is used
        File missing = new File(temporaryFolder.getRoot(), "missing.png");
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat(MODEL, "Hi", missing));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertFalse(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("\"content\":\"Hi\""));
    }

    // ------------------------------------------------------------------
    // chat(String model, String message, List<File>)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFileList() throws Exception {
        File png = createImage("list-image.png", "png");
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        assertEquals("Hello from Dial", spy.chat(MODEL, "Hi", Collections.singletonList(png)));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("\"type\":\"image_url\""));
    }

    @Test
    public void testChatWithNullFileList() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        assertEquals("Hello from Dial", spy.chat(MODEL, "Hi", (List<File>) null));
    }

    @Test
    public void testChatWithEmptyFileList() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        assertEquals("Hello from Dial", spy.chat(MODEL, "Hi", new ArrayList<>()));
    }

    // ------------------------------------------------------------------
    // chatWithFiles(String, String)
    // ------------------------------------------------------------------

    @Test
    public void testChatWithFilesMissingFileThrows() throws Exception {
        DialAIClient client = newClient();
        try {
            client.chatWithFiles("Hi", "/nonexistent/path/image.png");
            fail("Expected IllegalArgumentException for missing file");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found"));
        }
    }

    @Test
    public void testChatWithFilesValidImage() throws Exception {
        File png = createImage("chatfiles-image.png", "png");
        File png2 = createImage("chatfiles-image2.png", "png");
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        // first file of the comma-separated, whitespace-padded list is used
        assertEquals("Hello from Dial", spy.chatWithFiles("Describe this",
                " " + png.getAbsolutePath() + " , " + png2.getAbsolutePath() + " "));

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
        DialAIClient client = newClient();
        client.setConversationObserver(observer);
        DialAIClient spy = spyWithResponse(client, DIAL_RESPONSE);

        String result = spy.chat(MODEL,
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null));
        assertEquals("Hello from Dial", result);

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
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        assertEquals("Hello from Dial", spy.chat((String) null, new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesVarargsUsesDefaultModel() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        assertEquals("Hello from Dial", spy.chat(new Message("user", "Hello", null)));
    }

    @Test
    public void testChatMessagesWithAgentContext() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);
        JSONObject agentContext = new JSONObject().put(AI.AgentParams.AGENT_PROMPT, "Be concise");
        assertEquals("Hello from Dial",
                spy.chat(agentContext, new Message("user", "Hello", null)));

        verify(spy).post(any(GenericRequest.class));
    }

    @Test
    public void testChatMessagesWithImageFile() throws Exception {
        File png = createImage("msg-image.png", "png");
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat(MODEL,
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
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);

        assertEquals("Hello from Dial", spy.chat(MODEL,
                new Message("user", null, Collections.singletonList(png))));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"type\":\"image_url\""));
        // null text -> no text part, only image
        assertFalse(body.contains("\"type\":\"text\""));
    }

    @Test
    public void testChatMessagesModelRoleIsNormalized() throws Exception {
        DialAIClient spy = spyWithResponse(newClient(), DIAL_RESPONSE);

        Message message = new Message("model", "Hi there", null);
        assertEquals("Hello from Dial", spy.chat(MODEL, message));
        // normalizeMessageRoles converts "model" to roleName() == "assistant" in place
        assertEquals("assistant", message.getRole());
    }
}
