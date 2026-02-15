package com.github.istin.dmtools.ai.google.auth;

import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiKeyAuthenticationStrategy.
 * Tests API key query parameter authentication for Gemini public API.
 */
class ApiKeyAuthenticationStrategyTest {

    @Test
    void testConstructorWithNullApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiKeyAuthenticationStrategy(null));
    }

    @Test
    void testConstructorWithEmptyApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiKeyAuthenticationStrategy(""));

        assertThrows(IllegalArgumentException.class, () ->
                new ApiKeyAuthenticationStrategy("   "));
    }

    @Test
    void testConstructorWithValidApiKey() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("test-api-key");
        assertNotNull(strategy);
        assertEquals("API_KEY", strategy.getAuthenticationType());
    }

    @Test
    void testGetAuthenticationType() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("test-key");
        assertEquals("API_KEY", strategy.getAuthenticationType());
    }

    @Test
    void testSignRequestAddsApiKeyQueryParameter() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("my-secret-key");

        String baseUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent";
        Request.Builder builder = new Request.Builder().url(baseUrl);

        Request request = strategy.signRequest(builder, baseUrl, "{}", null);

        assertNotNull(request);
        String url = request.url().toString();
        assertTrue(url.contains("key=my-secret-key"), "URL should contain API key: " + url);
    }

    @Test
    void testSignRequestWithCustomHeaders() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("test-key");

        String baseUrl = "https://generativelanguage.googleapis.com/v1/test";
        Request.Builder builder = new Request.Builder().url(baseUrl);

        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("X-Custom-Header", "custom-value");
        customHeaders.put("User-Agent", "TestAgent/1.0");

        Request request = strategy.signRequest(builder, baseUrl, "{}", customHeaders);

        assertNotNull(request);
        assertEquals("custom-value", request.header("X-Custom-Header"));
        assertEquals("TestAgent/1.0", request.header("User-Agent"));

        // Verify API key is in URL
        assertTrue(request.url().toString().contains("key=test-key"));
    }

    @Test
    void testSignRequestWithInvalidUrl() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("test-key");

        String invalidUrl = "not-a-valid-url";
        Request.Builder builder = new Request.Builder();

        assertThrows(IllegalArgumentException.class, () ->
                strategy.signRequest(builder, invalidUrl, "{}", null));
    }

    @Test
    void testSignBuilderAddsCustomHeaders() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("test-key");

        Request.Builder builder = new Request.Builder()
                .url("https://example.com/test");

        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Content-Type", "application/json");
        customHeaders.put("Accept", "application/json");

        Request.Builder signedBuilder = strategy.sign(builder, customHeaders);

        assertNotNull(signedBuilder);
        Request request = signedBuilder.build();

        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("application/json", request.header("Accept"));
    }

    @Test
    void testSignBuilderWithNullCustomHeaders() {
        ApiKeyAuthenticationStrategy strategy = new ApiKeyAuthenticationStrategy("test-key");

        Request.Builder builder = new Request.Builder()
                .url("https://example.com/test");

        // Should not throw exception with null custom headers
        Request.Builder signedBuilder = strategy.sign(builder, null);
        assertNotNull(signedBuilder);
    }
}
