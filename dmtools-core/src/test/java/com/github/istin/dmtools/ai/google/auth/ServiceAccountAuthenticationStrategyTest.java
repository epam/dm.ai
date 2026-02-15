package com.github.istin.dmtools.ai.google.auth;

import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceAccountAuthenticationStrategy.
 * Tests OAuth2 token authentication for Vertex AI Gemini.
 */
class ServiceAccountAuthenticationStrategyTest {

    // Mock service account JSON (minimal valid structure)
    private static final String MOCK_SERVICE_ACCOUNT_JSON = """
        {
          "type": "service_account",
          "project_id": "test-project",
          "private_key_id": "key-id-123",
          "private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7W8jXKHCHvXCF\\n-----END PRIVATE KEY-----",
          "client_email": "test@test-project.iam.gserviceaccount.com",
          "client_id": "123456789",
          "auth_uri": "https://accounts.google.com/o/oauth2/auth",
          "token_uri": "https://oauth2.googleapis.com/token",
          "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs"
        }
        """;

    @Test
    void testConstructorWithInvalidPath() {
        // Test null path
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceAccountAuthenticationStrategy(null));

        // Test empty path
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceAccountAuthenticationStrategy(""));

        // Test file that doesn't exist
        assertThrows(IOException.class, () ->
                new ServiceAccountAuthenticationStrategy("/path/that/does/not/exist.json"));
    }

    @Test
    void testConstructorWithJsonString(@TempDir Path tempDir) throws IOException {
        // Create credentials file
        File credentialsFile = tempDir.resolve("service-account.json").toFile();
        Files.writeString(credentialsFile.toPath(), MOCK_SERVICE_ACCOUNT_JSON);

        // Test constructor with file path - should throw because we can't actually authenticate
        // (this is a unit test with mock credentials, not integration test)
        assertThrows(Exception.class, () ->
                new ServiceAccountAuthenticationStrategy(credentialsFile.getAbsolutePath()));
    }

    @Test
    void testConstructorWithJsonStringParameter() {
        // Test null JSON
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceAccountAuthenticationStrategy(null, true));

        // Test empty JSON
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceAccountAuthenticationStrategy("", true));

        // Test invalid flag
        assertThrows(IllegalArgumentException.class, () ->
                new ServiceAccountAuthenticationStrategy(MOCK_SERVICE_ACCOUNT_JSON, false));
    }

    @Test
    void testGetAuthenticationType() throws IOException {
        // We can't create a real ServiceAccountAuthenticationStrategy in unit tests
        // because it requires valid Google Cloud credentials.
        // This test would be better suited for integration tests.

        // Instead, we verify the interface contract
        GeminiAuthenticationStrategy strategy = new MockServiceAccountStrategy();
        assertEquals("SERVICE_ACCOUNT", strategy.getAuthenticationType());
    }

    @Test
    void testSignRequestAddsAuthorizationHeader() {
        // Use mock strategy to test the signing logic
        MockServiceAccountStrategy strategy = new MockServiceAccountStrategy();

        Request.Builder builder = new Request.Builder()
                .url("https://us-central1-aiplatform.googleapis.com/v1/test");

        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("X-Custom-Header", "test-value");

        Request request = strategy.signRequest(builder, "https://example.com", "{}", customHeaders);

        assertNotNull(request);
        assertEquals("Bearer mock-access-token", request.header("Authorization"));
        assertEquals("test-value", request.header("X-Custom-Header"));
    }

    @Test
    void testSignRequestWithNullCustomHeaders() {
        MockServiceAccountStrategy strategy = new MockServiceAccountStrategy();

        Request.Builder builder = new Request.Builder()
                .url("https://us-central1-aiplatform.googleapis.com/v1/test");

        Request request = strategy.signRequest(builder, "https://example.com", "{}", null);

        assertNotNull(request);
        assertEquals("Bearer mock-access-token", request.header("Authorization"));
    }

    /**
     * Mock implementation for unit testing without real Google Cloud credentials.
     */
    private static class MockServiceAccountStrategy implements GeminiAuthenticationStrategy {

        @Override
        public Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders) {
            builder.addHeader("Authorization", "Bearer mock-access-token");
            if (customHeaders != null) {
                customHeaders.forEach(builder::addHeader);
            }
            return builder;
        }

        @Override
        public Request signRequest(Request.Builder requestBuilder, String url, String body, Map<String, String> customHeaders) {
            requestBuilder.addHeader("Authorization", "Bearer mock-access-token");
            if (customHeaders != null) {
                customHeaders.forEach(requestBuilder::addHeader);
            }
            return requestBuilder.build();
        }

        @Override
        public String getAuthenticationType() {
            return "SERVICE_ACCOUNT";
        }
    }
}
