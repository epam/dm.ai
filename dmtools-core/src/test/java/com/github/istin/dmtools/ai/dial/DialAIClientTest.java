// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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
        assertEquals(BASE_PATH + "/" + path, dialAIClient.path(path));
    }

    @Test
    public void testPathWithTrailingSlashBase() throws IOException {
        DialAIClient client = new DialAIClient(BASE_PATH + "/", AUTHORIZATION, MODEL);
        assertEquals(BASE_PATH + "/test/path", client.path("test/path"));
    }

    @Test
    public void testPathWithLeadingSlashPath() {
        assertEquals(BASE_PATH + "/test/path", dialAIClient.path("/test/path"));
    }

    @Test
    public void testChatUrlWhenBasePathHasNoTrailingSlash() throws Exception {
        ApplicationConfiguration config = mock(ApplicationConfiguration.class);
        when(config.getDialBathPath()).thenReturn("https://api.dial.com");
        when(config.getDialApiKey()).thenReturn(AUTHORIZATION);
        when(config.getDialModel()).thenReturn(MODEL);
        when(config.getDialApiVersion()).thenReturn(null);

        BasicDialAI client = new BasicDialAI(null, config);
        DialAIClient spy = spy(client);
        doReturn("{\"choices\":[]}").when(spy).post(any(GenericRequest.class));

        spy.chat("Hi");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.dial.com/openai/deployments/" + MODEL + "/chat/completions",
                captor.getValue().url());
    }

    @Test
    public void testChatUrlWhenBasePathHasTrailingSlash() throws Exception {
        ApplicationConfiguration config = mock(ApplicationConfiguration.class);
        when(config.getDialBathPath()).thenReturn("https://api.dial.com/");
        when(config.getDialApiKey()).thenReturn(AUTHORIZATION);
        when(config.getDialModel()).thenReturn(MODEL);
        when(config.getDialApiVersion()).thenReturn(null);

        BasicDialAI client = new BasicDialAI(null, config);
        DialAIClient spy = spy(client);
        doReturn("{\"choices\":[]}").when(spy).post(any(GenericRequest.class));

        spy.chat("Hi");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.dial.com/openai/deployments/" + MODEL + "/chat/completions",
                captor.getValue().url());
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = dialAIClient.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testGetTimeout() {
        assertEquals(1400, dialAIClient.getTimeout());
    }

    // Skip network-dependent chat tests as they are difficult to mock properly
    // The actual chat functionality is tested through integration tests

    @Test
    public void testBuildHashForPostRequest() {
        GenericRequest genericRequest = mock(GenericRequest.class);
        when(genericRequest.getBody()).thenReturn("body");
        String url = "http://example.com";
        String hash = dialAIClient.buildHashForPostRequest(genericRequest, url);
        assertEquals(url + "body", hash);
    }
}