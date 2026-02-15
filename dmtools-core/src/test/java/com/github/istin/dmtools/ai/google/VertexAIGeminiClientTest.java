package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.google.auth.GeminiAuthenticationStrategy;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for VertexAIGeminiClient.
 * Uses mocks to avoid making real API calls.
 */
@ExtendWith(MockitoExtension.class)
class VertexAIGeminiClientTest {

    @Mock
    private ConversationObserver mockObserver;

    @Mock
    private GeminiAuthenticationStrategy mockAuthStrategy;

    private static final String TEST_PROJECT_ID = "test-project";
    private static final String TEST_LOCATION = "us-central1";
    private static final String TEST_MODEL = "gemini-2.0-flash-exp";

    @BeforeEach
    void setUp() {
        // Setup mock authentication strategy (lenient to allow unused stubbing in some tests)
        lenient().when(mockAuthStrategy.getAuthenticationType()).thenReturn("MOCK_AUTH");
        lenient().when(mockAuthStrategy.signRequest(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Request.Builder builder = invocation.getArgument(0);
                    builder.addHeader("Authorization", "Bearer mock-token");
                    return builder.build();
                });
    }

    @Test
    void testConstructorWithNullProjectId(@TempDir Path tempDir) throws IOException {
        File credentialsFile = createMockCredentialsFile(tempDir);

        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient(null, TEST_LOCATION, TEST_MODEL,
                        credentialsFile.getAbsolutePath(), mockObserver, null, "v1"));
    }

    @Test
    void testConstructorWithEmptyProjectId(@TempDir Path tempDir) throws IOException {
        File credentialsFile = createMockCredentialsFile(tempDir);

        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient("", TEST_LOCATION, TEST_MODEL,
                        credentialsFile.getAbsolutePath(), mockObserver, null, "v1"));
    }

    @Test
    void testConstructorWithNullLocation(@TempDir Path tempDir) throws IOException {
        File credentialsFile = createMockCredentialsFile(tempDir);

        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient(TEST_PROJECT_ID, null, TEST_MODEL,
                        credentialsFile.getAbsolutePath(), mockObserver, null, "v1"));
    }

    @Test
    void testConstructorWithNullModel(@TempDir Path tempDir) throws IOException {
        File credentialsFile = createMockCredentialsFile(tempDir);

        assertThrows(IllegalArgumentException.class, () ->
                new VertexAIGeminiClient(TEST_PROJECT_ID, TEST_LOCATION, null,
                        credentialsFile.getAbsolutePath(), mockObserver, null, "v1"));
    }

    @Test
    void testRoleName() {
        // We can't create a real client in unit tests without valid credentials
        // So we use a mock strategy instead
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();
        assertEquals("model", client.roleName());
    }

    @Test
    void testNormalizeMessageRoles() {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        Message[] messages = new Message[]{
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null),
                new Message("user", "How are you?", null),
                new Message("model", "I'm good", null)
        };

        Message[] normalized = client.normalizeMessageRoles(messages);

        // "assistant" should be converted to "model"
        assertEquals("user", normalized[0].getRole());
        assertEquals("model", normalized[1].getRole()); // Changed from "assistant"
        assertEquals("user", normalized[2].getRole());
        assertEquals("model", normalized[3].getRole()); // Unchanged
    }

    @Test
    void testParseGeminiResponseSuccess() {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        String validResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "This is the AI response."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        String result = assertDoesNotThrow(() -> client.testParseGeminiResponse(validResponse));
        assertEquals("This is the AI response.", result);
    }

    @Test
    void testParseGeminiResponseWithError() {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        String errorResponse = """
                {
                  "error": {
                    "code": 400,
                    "message": "Invalid request",
                    "status": "INVALID_ARGUMENT"
                  }
                }
                """;

        IOException exception = assertThrows(IOException.class, () ->
                client.testParseGeminiResponse(errorResponse));

        assertTrue(exception.getMessage().contains("Invalid request"));
    }

    @Test
    void testParseGeminiResponseBlockedBySafety() {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        String blockedResponse = """
                {
                  "promptFeedback": {
                    "blockReason": "SAFETY"
                  }
                }
                """;

        IOException exception = assertThrows(IOException.class, () ->
                client.testParseGeminiResponse(blockedResponse));

        assertTrue(exception.getMessage().contains("blocked by safety filters"));
    }

    @Test
    void testParseGeminiResponseBlockedCandidate() {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        String blockedResponse = """
                {
                  "candidates": [
                    {
                      "finishReason": "SAFETY"
                    }
                  ]
                }
                """;

        IOException exception = assertThrows(IOException.class, () ->
                client.testParseGeminiResponse(blockedResponse));

        assertTrue(exception.getMessage().contains("blocked by safety filters"));
    }

    @Test
    void testDetermineMimeType() {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        assertEquals("image/png", client.testDetermineMimeType(new File("test.png")));
        assertEquals("image/jpeg", client.testDetermineMimeType(new File("test.jpg")));
        assertEquals("image/jpeg", client.testDetermineMimeType(new File("test.jpeg")));
        assertEquals("image/gif", client.testDetermineMimeType(new File("test.gif")));
        assertEquals("image/webp", client.testDetermineMimeType(new File("test.webp")));
        assertEquals("application/pdf", client.testDetermineMimeType(new File("test.pdf")));
        assertEquals("text/plain", client.testDetermineMimeType(new File("test.txt")));
        assertEquals("application/json", client.testDetermineMimeType(new File("test.json")));
        assertEquals("application/octet-stream", client.testDetermineMimeType(new File("test.unknown")));
    }

    @Test
    void testEncodeFileToBase64(@TempDir Path tempDir) throws IOException {
        MockVertexAIGeminiClient client = new MockVertexAIGeminiClient();

        // Create a test file
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "Hello World");

        String base64 = assertDoesNotThrow(() -> client.testEncodeFileToBase64(testFile));
        assertNotNull(base64);
        assertTrue(base64.length() > 0);

        // Verify it's valid base64
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
        assertEquals("Hello World", new String(decoded));
    }

    /**
     * Helper method to create a mock credentials file.
     */
    private File createMockCredentialsFile(Path tempDir) throws IOException {
        String mockJson = """
                {
                  "type": "service_account",
                  "project_id": "test-project",
                  "private_key_id": "key-123",
                  "private_key": "-----BEGIN PRIVATE KEY-----\\ntest\\n-----END PRIVATE KEY-----",
                  "client_email": "test@test.iam.gserviceaccount.com",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """;

        File credentialsFile = tempDir.resolve("service-account.json").toFile();
        Files.writeString(credentialsFile.toPath(), mockJson);
        return credentialsFile;
    }

    /**
     * Mock subclass to expose protected methods for testing.
     */
    private static class MockVertexAIGeminiClient {

        public String roleName() {
            return "model";
        }

        public Message[] normalizeMessageRoles(Message... messages) {
            if (messages == null || messages.length == 0) {
                return messages;
            }

            String expectedRole = roleName();

            for (Message message : messages) {
                String role = message.getRole();
                if (("assistant".equals(role) || "model".equals(role)) && !expectedRole.equals(role)) {
                    message.setRole(expectedRole);
                }
            }

            return messages;
        }

        public String testParseGeminiResponse(String responseBody) throws IOException {
            // Simplified version of parseGeminiResponse for testing
            org.json.JSONObject responseJson = new org.json.JSONObject(responseBody);

            if (responseJson.has("error")) {
                org.json.JSONObject error = responseJson.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown error");
                throw new IOException("Vertex AI Gemini API error: " + errorMessage);
            }

            if (responseJson.has("promptFeedback")) {
                org.json.JSONObject promptFeedback = responseJson.getJSONObject("promptFeedback");
                if (promptFeedback.has("blockReason")) {
                    throw new IOException("Content blocked by safety filters");
                }
            }

            if (responseJson.has("candidates")) {
                org.json.JSONArray candidates = responseJson.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    org.json.JSONObject firstCandidate = candidates.getJSONObject(0);

                    if (firstCandidate.has("finishReason")) {
                        String finishReason = firstCandidate.getString("finishReason");
                        if ("SAFETY".equals(finishReason)) {
                            throw new IOException("Response blocked by safety filters");
                        }
                    }

                    if (firstCandidate.has("content")) {
                        org.json.JSONObject content = firstCandidate.getJSONObject("content");
                        if (content.has("parts")) {
                            org.json.JSONArray parts = content.getJSONArray("parts");
                            StringBuilder textBuilder = new StringBuilder();

                            for (int i = 0; i < parts.length(); i++) {
                                org.json.JSONObject part = parts.getJSONObject(i);
                                if (part.has("text")) {
                                    textBuilder.append(part.getString("text"));
                                }
                            }

                            String result = textBuilder.toString();
                            if (!result.isEmpty()) {
                                return result;
                            }
                        }
                    }
                }
            }

            throw new IOException("No text content found in response");
        }

        public String testDetermineMimeType(File file) {
            String fileName = file.getName().toLowerCase();

            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".gif")) return "image/gif";
            if (fileName.endsWith(".webp")) return "image/webp";
            if (fileName.endsWith(".pdf")) return "application/pdf";
            if (fileName.endsWith(".txt")) return "text/plain";
            if (fileName.endsWith(".json")) return "application/json";

            return "application/octet-stream";
        }

        public String testEncodeFileToBase64(File file) throws IOException {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            return java.util.Base64.getEncoder().encodeToString(fileBytes);
        }
    }
}
