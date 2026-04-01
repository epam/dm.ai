// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

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