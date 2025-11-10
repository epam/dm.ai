package com.github.istin.dmtools.atlassian.common.networking;

import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.spy;

public class AtlassianRestClientTest {

    private AtlassianRestClient atlassianRestClient;

    @Before
    public void setUp() throws IOException {
        atlassianRestClient = spy(new AtlassianRestClient("http://example.com", "token") {
            @Override
            public String path(String path) {
                return "";
            }
        });
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder().url("http://example.com");
        Request.Builder signedBuilder = atlassianRestClient.sign(builder);

        assertNotNull(signedBuilder);
        assertEquals("Basic token", signedBuilder.build().header("Authorization"));
        assertEquals("nocheck", signedBuilder.build().header("X-Atlassian-Token"));
        assertEquals("application/json", signedBuilder.build().header("Content-Type"));
    }

    @Test
    public void testJiraException() {
        String message = "Error message";
        String body = "Error body";
        AtlassianRestClient.RestClientException exception = new AtlassianRestClient.RestClientException(message, body, -1);

        assertEquals(message, exception.getMessage());
        assertEquals(body, exception.getBody());
    }
}