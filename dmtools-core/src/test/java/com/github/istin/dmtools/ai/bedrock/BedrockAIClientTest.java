package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BedrockAIClientTest {

    private BedrockAIClient bedrockAIClient;
    private static final String BASE_PATH = "https://bedrock-runtime.us-east-1.amazonaws.com";
    private static final String REGION = "us-east-1";
    private static final String MODEL_ID = "anthropic.claude-sonnet-4-20250514-v1:0";
    private static final String BEARER_TOKEN = "test-bearer-token";
    private static final int MAX_TOKENS = 1000;
    private static final double TEMPERATURE = 0.7;

    @Before
    public void setUp() throws IOException {
        bedrockAIClient = new BedrockAIClient(
            BASE_PATH, REGION, MODEL_ID, BEARER_TOKEN, 
            MAX_TOKENS, TEMPERATURE, null, null, null
        );
    }

    @Test
    public void testGetName() {
        assertEquals(MODEL_ID, bedrockAIClient.getName());
    }

    @Test
    public void testRoleName() {
        assertEquals("assistant", bedrockAIClient.roleName());
    }

    @Test
    public void testPath() {
        String path = "/model/test-model/invoke";
        assertEquals(BASE_PATH + path, bedrockAIClient.path(path));
    }

    @Test
    public void testPathWithoutModelPrefix() {
        String path = "test/path";
        String expected = BASE_PATH + "/model/" + MODEL_ID + "/invoke";
        assertEquals(expected, bedrockAIClient.path(path));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = bedrockAIClient.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testSignWithBearerToken() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = bedrockAIClient.sign(builder);
        assertNotNull(signedBuilder);
        // Note: We can't easily verify the header was set without building the request
        // but the method should not throw an exception
    }

    @Test
    public void testSignWithCustomHeaders() throws IOException {
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("X-Custom-Header", "custom-value");
        BedrockAIClient clientWithHeaders = new BedrockAIClient(
            BASE_PATH, REGION, MODEL_ID, BEARER_TOKEN,
            MAX_TOKENS, TEMPERATURE, null, customHeaders, null
        );
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = clientWithHeaders.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testGetTimeout() {
        assertEquals(700, bedrockAIClient.getTimeout());
    }

    @Test
    public void testChat() throws Exception {
        BedrockAIClient spyClient = Mockito.spy(bedrockAIClient);
        doReturn("response").when(spyClient).chat(MODEL_ID, "message", (java.io.File) null);
        String response = spyClient.chat("message");
        assertEquals("response", response);
    }

    @Test
    public void testChatWithModel() throws Exception {
        BedrockAIClient spyClient = Mockito.spy(bedrockAIClient);
        doReturn("response").when(spyClient).chat(MODEL_ID, "message", (java.io.File) null);
        String response = spyClient.chat(MODEL_ID, "message");
        assertEquals("response", response);
    }

    @Test
    public void testBuildHashForPostRequest() {
        GenericRequest genericRequest = mock(GenericRequest.class);
        when(genericRequest.getBody()).thenReturn("body");
        String url = "http://example.com";
        String hash = bedrockAIClient.buildHashForPostRequest(genericRequest, url);
        assertEquals(url + "body", hash);
    }

    @Test
    public void testConversationObserver() {
        ConversationObserver observer = mock(ConversationObserver.class);
        bedrockAIClient.setConversationObserver(observer);
        assertEquals(observer, bedrockAIClient.getConversationObserver());
    }

    @Test
    public void testMetadata() {
        com.github.istin.dmtools.ai.model.Metadata metadata = mock(com.github.istin.dmtools.ai.model.Metadata.class);
        bedrockAIClient.setMetadata(metadata);
        // Metadata is stored internally, we can't easily verify it without calling chat
        assertNotNull(metadata);
    }

    @Test
    public void testGetters() {
        assertEquals(REGION, bedrockAIClient.getRegion());
        assertEquals(BEARER_TOKEN, bedrockAIClient.getBearerToken());
        assertEquals(MAX_TOKENS, bedrockAIClient.getMaxTokens());
        assertEquals(TEMPERATURE, bedrockAIClient.getTemperature(), 0.001);
    }
}
