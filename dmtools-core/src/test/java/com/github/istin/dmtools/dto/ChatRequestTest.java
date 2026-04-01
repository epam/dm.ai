// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testBasicChatRequestCreation() {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Hello", null),
            new ChatMessage("assistant", "Hi there!", null)
        );
        String model = "gpt-4";
        String ai = "openai-integration";
        String mcpConfigId = "mcp-config-123";

        // When
        ChatRequest request = new ChatRequest(messages, model, ai, mcpConfigId);

        // Then
        assertEquals(messages, request.getMessages());
        assertEquals(model, request.getModel());
        assertEquals(ai, request.getAi());
        assertEquals(mcpConfigId, request.getMcpConfigId());
    }

    @Test
    void testChatRequestWithoutMcpConfig() {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Simple message", null)
        );
        String model = "gemini-1.5-flash";
        String ai = "gemini-integration";

        // When
        ChatRequest request = new ChatRequest(messages, model, ai, null);

        // Then
        assertEquals(messages, request.getMessages());
        assertEquals(model, request.getModel());
        assertEquals(ai, request.getAi());
        assertNull(request.getMcpConfigId());
    }

    @Test
    void testJsonSerializationWithMcpConfig() throws JsonProcessingException {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Get ticket DMC-100", null)
        );
        ChatRequest request = new ChatRequest(messages, "gpt-4", "openai", "mcp-jira-config");

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertTrue(json.contains("\"mcpConfigId\":\"mcp-jira-config\""));
        assertTrue(json.contains("\"model\":\"gpt-4\""));
        assertTrue(json.contains("\"ai\":\"openai\""));
        assertTrue(json.contains("\"messages\":["));
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("\"content\":\"Get ticket DMC-100\""));
    }

    @Test
    void testJsonSerializationWithoutMcpConfig() throws JsonProcessingException {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Hello", null)
        );
        ChatRequest request = new ChatRequest(messages, "gemini-1.5-flash", "gemini", null);

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertTrue(json.contains("\"mcpConfigId\":null"));
        assertTrue(json.contains("\"model\":\"gemini-1.5-flash\""));
        assertTrue(json.contains("\"ai\":\"gemini\""));
    }

    @Test
    void testJsonDeserializationWithMcpConfig() throws JsonProcessingException {
        // Given
        String json = "{\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": \"Search for tickets in project DMC\",\n" +
                "      \"fileNames\": null\n" +
                "    }\n" +
                "  ],\n" +
                "  \"model\": \"claude-3.5-sonnet\",\n" +
                "  \"ai\": \"claude-integration\",\n" +
                "  \"mcpConfigId\": \"mcp-full-stack-config\"\n" +
                "}";

        // When
        ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

        // Then
        assertNotNull(request.getMessages());
        assertEquals(1, request.getMessages().size());
        assertEquals("user", request.getMessages().get(0).getRole());
        assertEquals("Search for tickets in project DMC", request.getMessages().get(0).getContent());
        assertEquals("claude-3.5-sonnet", request.getModel());
        assertEquals("claude-integration", request.getAi());
        assertEquals("mcp-full-stack-config", request.getMcpConfigId());
    }

    @Test
    void testJsonDeserializationWithoutMcpConfig() throws JsonProcessingException {
        // Given
        String json = "{\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": \"Simple question\",\n" +
                "      \"fileNames\": null\n" +
                "    }\n" +
                "  ],\n" +
                "  \"model\": \"gpt-3.5-turbo\",\n" +
                "  \"ai\": \"openai\"\n" +
                "}";

        // When
        ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

        // Then
        assertNotNull(request.getMessages());
        assertEquals("Simple question", request.getMessages().get(0).getContent());
        assertEquals("gpt-3.5-turbo", request.getModel());
        assertEquals("openai", request.getAi());
        assertNull(request.getMcpConfigId());
    }



    @Test
    void testEmptyMessages() {
        // Given
        List<ChatMessage> emptyMessages = Collections.emptyList();

        // When
        ChatRequest request = new ChatRequest(emptyMessages, "gpt-4", "openai", null);

        // Then
        assertTrue(request.getMessages().isEmpty());
        assertEquals("gpt-4", request.getModel());
        assertEquals("openai", request.getAi());
        assertNull(request.getMcpConfigId());
    }

    @Test
    void testNullValues() {
        // Given & When
        ChatRequest request = new ChatRequest(null, null, null, null);

        // Then
        assertNull(request.getMessages());
        assertNull(request.getModel());
        assertNull(request.getAi());
        assertNull(request.getMcpConfigId());
    }

    @Test
    void testMultipleMessagesWithFiles() {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Analyze this file", Arrays.asList("document.pdf")),
            new ChatMessage("assistant", "I can see the document. What would you like me to analyze?", null),
            new ChatMessage("user", "Extract key points", null)
        );

        // When
        ChatRequest request = new ChatRequest(messages, "gpt-4-vision", "openai", "mcp-file-tools");

        // Then
        assertEquals(3, request.getMessages().size());
        assertEquals("Analyze this file", request.getMessages().get(0).getContent());
        assertNotNull(request.getMessages().get(0).getFileNames());
        assertEquals(1, request.getMessages().get(0).getFileNames().size());
        assertEquals("document.pdf", request.getMessages().get(0).getFileNames().get(0));
        assertEquals("mcp-file-tools", request.getMcpConfigId());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Test message", null)
        );
        
        ChatRequest request1 = new ChatRequest(messages, "gpt-4", "openai", "mcp-config");
        ChatRequest request2 = new ChatRequest(messages, "gpt-4", "openai", "mcp-config");
        ChatRequest request3 = new ChatRequest(messages, "gpt-4", "openai", "different-config");

        // Then
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void testToString() {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Test content", null)
        );
        ChatRequest request = new ChatRequest(messages, "gpt-4", "openai", "mcp-test-config");

        // When
        String toString = request.toString();

        // Then
        assertTrue(toString.contains("ChatRequest"));
        assertTrue(toString.contains("gpt-4"));
        assertTrue(toString.contains("openai"));
        assertTrue(toString.contains("mcp-test-config"));
    }

    @Test
    void testCompleteJsonRoundTrip() throws JsonProcessingException {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Complex message with 特殊字符 🚀", Arrays.asList("file1.txt", "file2.pdf")),
            new ChatMessage("assistant", "I understand", null),
            new ChatMessage("user", "Follow up question", null)
        );
        ChatRequest originalRequest = new ChatRequest(messages, "claude-3.5-sonnet", "claude", "mcp-complex-config");

        // When
        String json = objectMapper.writeValueAsString(originalRequest);
        ChatRequest deserializedRequest = objectMapper.readValue(json, ChatRequest.class);

        // Then
        assertEquals(originalRequest.getMessages().size(), deserializedRequest.getMessages().size());
        assertEquals(originalRequest.getModel(), deserializedRequest.getModel());
        assertEquals(originalRequest.getAi(), deserializedRequest.getAi());
        assertEquals(originalRequest.getMcpConfigId(), deserializedRequest.getMcpConfigId());
        
        // Check messages content
        for (int i = 0; i < originalRequest.getMessages().size(); i++) {
            ChatMessage original = originalRequest.getMessages().get(i);
            ChatMessage deserialized = deserializedRequest.getMessages().get(i);
            
            assertEquals(original.getRole(), deserialized.getRole());
            assertEquals(original.getContent(), deserialized.getContent());
            assertEquals(original.getFileNames(), deserialized.getFileNames());
        }
    }

    @Test
    void testMcpConfigIdValidation() {
        // Given
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("user", "Test", null)
        );

        // Test various mcpConfigId values
        ChatRequest withValidId = new ChatRequest(messages, "gpt-4", "openai", "valid-config-123");
        ChatRequest withEmptyId = new ChatRequest(messages, "gpt-4", "openai", "");
        ChatRequest withNullId = new ChatRequest(messages, "gpt-4", "openai", null);

        // Then
        assertEquals("valid-config-123", withValidId.getMcpConfigId());
        assertEquals("", withEmptyId.getMcpConfigId());
        assertNull(withNullId.getMcpConfigId());
    }

    @Test
    void testLargeMessageHistory() {
        // Given
        List<ChatMessage> largeHistory = Arrays.asList(
            new ChatMessage("user", "Initial question", null),
            new ChatMessage("assistant", "Initial response", null),
            new ChatMessage("user", "Follow up 1", null),
            new ChatMessage("assistant", "Response 1", null),
            new ChatMessage("user", "Follow up 2", null),
            new ChatMessage("assistant", "Response 2", null),
            new ChatMessage("user", "Final question", null)
        );

        // When
        ChatRequest request = new ChatRequest(largeHistory, "gpt-4", "openai", "mcp-history-config");

        // Then
        assertEquals(7, request.getMessages().size());
        assertEquals("Initial question", request.getMessages().get(0).getContent());
        assertEquals("Final question", request.getMessages().get(6).getContent());
        assertEquals("mcp-history-config", request.getMcpConfigId());
    }
}
