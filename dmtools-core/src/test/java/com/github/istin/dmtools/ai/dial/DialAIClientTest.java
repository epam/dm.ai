package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DialAIClientTest {

    private DialAIClient dialAIClient;
    private static final String BASE_PATH = "http://example.com";
    private static final String AUTHORIZATION = "auth-token";
    private static final String MODEL = "test-model";

    @Before
    public void setUp() throws IOException {
        dialAIClient = new DialAIClient(BASE_PATH, AUTHORIZATION, MODEL);
    }

    @Test
    public void testGetName() {
        assertEquals(MODEL, dialAIClient.getName());
    }

    @Test
    public void testPath() {
        String path = "test/path";
        assertEquals(BASE_PATH + path, dialAIClient.path(path));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = dialAIClient.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testGetTimeout() {
        assertEquals(700, dialAIClient.getTimeout());
    }

    @Test
    public void testChat() throws Exception {
        DialAIClient spyClient = Mockito.spy(dialAIClient);
        doReturn("response").when(spyClient).chat(MODEL, "message", (File) null);
        String response = spyClient.chat("message");
        assertEquals("response", response);
    }

    @Test
    public void testChatWithModel() throws Exception {
        DialAIClient spyClient = Mockito.spy(dialAIClient);
        doReturn("response").when(spyClient).chat(MODEL, "message", (File) null);
        String response = spyClient.chat(MODEL, "message");
        assertEquals("response", response);
    }

    @Test
    public void testChatAsJSONArray() throws Exception {
        DialAIClient spyClient = Mockito.spy(dialAIClient);
        JSONArray jsonArray = new JSONArray();
        doReturn(jsonArray.toString()).when(spyClient).chat(MODEL, "message", (File) null);
        String response = spyClient.chat("message");
        assertEquals(jsonArray.toString(), response);
    }

    @Test
    public void testChatAsJSONObject() throws Exception {
        DialAIClient spyClient = Mockito.spy(dialAIClient);
        JSONObject jsonObject = new JSONObject();
        doReturn(jsonObject.toString()).when(spyClient).chat(MODEL, "message", (File) null);
        String response = spyClient.chat("message");
        assertEquals(jsonObject.toString(), response);
    }

    @Test
    public void testChatAsBoolean() throws Exception {
        DialAIClient spyClient = Mockito.spy(dialAIClient);
        doReturn("true").when(spyClient).chat(MODEL, "message", (File) null);
        boolean response = Boolean.parseBoolean(spyClient.chat("message"));
        assertTrue(response);
    }

    @Test
    public void testBuildHashForPostRequest() {
        GenericRequest genericRequest = mock(GenericRequest.class);
        when(genericRequest.getBody()).thenReturn("body");
        String url = "http://example.com";
        String hash = dialAIClient.buildHashForPostRequest(genericRequest, url);
        assertEquals(url + "body", hash);
    }
}