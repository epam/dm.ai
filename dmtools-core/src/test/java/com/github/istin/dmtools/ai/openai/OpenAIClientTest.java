// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.openai;

import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OpenAIClientTest {

    private OpenAIClient openAIClient;
    private static final String BASE_PATH = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "sk-test-key";
    private static final String MODEL = "gpt-4";

    @Before
    public void setUp() throws IOException {
        openAIClient = new OpenAIClient(BASE_PATH, API_KEY, MODEL);
    }

    @Test
    public void testGetName() {
        assertEquals(MODEL, openAIClient.getName());
    }

    @Test
    public void testPath() {
        String path = "test/path";
        assertEquals(BASE_PATH + path, openAIClient.path(path));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = openAIClient.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testGetTimeout() {
        assertEquals(700, openAIClient.getTimeout());
    }

    @Test
    public void testGetApiKey() {
        assertEquals(API_KEY, openAIClient.getApiKey());
    }

    @Test
    public void testGetModel() {
        assertEquals(MODEL, openAIClient.getModel());
    }

    @Test
    public void testGetMaxTokens() {
        assertEquals(4096, openAIClient.getMaxTokens());
    }

    @Test
    public void testGetTemperature() {
        // Default temperature is -1 (don't send temperature parameter)
        assertEquals(-1, openAIClient.getTemperature(), 0.001);
    }

    @Test
    public void testGetMaxTokensParamName() {
        // Default parameter name is max_completion_tokens for newer models
        assertEquals("max_completion_tokens", openAIClient.getMaxTokensParamName());
    }

    @Test
    public void testRoleName() {
        assertEquals("assistant", openAIClient.roleName());
    }

    @Test
    public void testCustomMaxTokensAndTemperature() throws IOException {
        OpenAIClient customClient = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 2048, 0.5, null);
        assertEquals(2048, customClient.getMaxTokens());
        assertEquals(0.5, customClient.getTemperature(), 0.001);
        assertEquals("max_completion_tokens", customClient.getMaxTokensParamName());
    }

    @Test
    public void testCustomMaxTokensParamName() throws IOException {
        OpenAIClient customClient = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 4096, 0.7, "max_tokens", null, null);
        assertEquals("max_tokens", customClient.getMaxTokensParamName());
    }

    @Test
    public void testEmptyMaxTokensParamName() throws IOException {
        OpenAIClient customClient = new OpenAIClient(BASE_PATH, API_KEY, MODEL, 4096, 0.7, "", null, null);
        assertEquals("", customClient.getMaxTokensParamName());
    }

    // Skip network-dependent chat tests as they are difficult to mock properly
    // The actual chat functionality is tested through integration tests

    @Test
    public void testBuildHashForPostRequest() {
        GenericRequest genericRequest = mock(GenericRequest.class);
        when(genericRequest.getBody()).thenReturn("body");
        String url = "https://api.openai.com/v1/chat/completions";
        String hash = openAIClient.buildHashForPostRequest(genericRequest, url);
        assertEquals(url + "body", hash);
    }
}
