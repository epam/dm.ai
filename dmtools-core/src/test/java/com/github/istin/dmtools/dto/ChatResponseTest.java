// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSuccessfulChatResponse() {
        // Given
        String content = "Hello! How can I help you today?";

        // When
        ChatResponse response = ChatResponse.success(content);

        // Then
        assertEquals(content, response.getContent());
        assertTrue(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testErrorChatResponse() {
        // Given
        String errorMessage = "AI service is temporarily unavailable";

        // When
        ChatResponse response = ChatResponse.error(errorMessage);

        // Then
        assertNull(response.getContent());
        assertFalse(response.isSuccess());
        assertEquals(errorMessage, response.getError());
    }

    @Test
    void testManualChatResponseCreation() {
        // Given
        String content = "Response content";
        boolean success = true;
        String error = null;

        // When
        ChatResponse response = new ChatResponse(content, success, error);

        // Then
        assertEquals(content, response.getContent());
        assertEquals(success, response.isSuccess());
        assertEquals(error, response.getError());
    }

    @Test
    void testJsonSerializationSuccess() throws JsonProcessingException {
        // Given
        ChatResponse response = ChatResponse.success("Test response content");

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertTrue(json.contains("\"content\":\"Test response content\""));
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"error\":null"));
    }

    @Test
    void testJsonSerializationError() throws JsonProcessingException {
        // Given
        ChatResponse response = ChatResponse.error("Service unavailable");

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertTrue(json.contains("\"content\":null"));
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"error\":\"Service unavailable\""));
    }

    @Test
    void testJsonDeserializationSuccess() throws JsonProcessingException {
        // Given
        String json = "{\"content\":\"Hello world\",\"success\":true,\"error\":null}";

        // When
        ChatResponse response = objectMapper.readValue(json, ChatResponse.class);

        // Then
        assertEquals("Hello world", response.getContent());
        assertTrue(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testJsonDeserializationError() throws JsonProcessingException {
        // Given
        String json = "{\"content\":null,\"success\":false,\"error\":\"Connection timeout\"}";

        // When
        ChatResponse response = objectMapper.readValue(json, ChatResponse.class);

        // Then
        assertNull(response.getContent());
        assertFalse(response.isSuccess());
        assertEquals("Connection timeout", response.getError());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        ChatResponse response1 = ChatResponse.success("Same content");
        ChatResponse response2 = ChatResponse.success("Same content");
        ChatResponse response3 = ChatResponse.success("Different content");
        ChatResponse response4 = ChatResponse.error("Error message");

        // Then
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
        assertNotEquals(response1, response4);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testToString() {
        // Given
        ChatResponse successResponse = ChatResponse.success("Test content");
        ChatResponse errorResponse = ChatResponse.error("Test error");

        // When
        String successString = successResponse.toString();
        String errorString = errorResponse.toString();

        // Then
        assertTrue(successString.contains("ChatResponse"));
        assertTrue(successString.contains("Test content"));
        assertTrue(successString.contains("success=true"));

        assertTrue(errorString.contains("ChatResponse"));
        assertTrue(errorString.contains("Test error"));
        assertTrue(errorString.contains("success=false"));
    }

    @Test
    void testEmptyContent() {
        // Given
        String emptyContent = "";

        // When
        ChatResponse response = ChatResponse.success(emptyContent);

        // Then
        assertEquals(emptyContent, response.getContent());
        assertTrue(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testNullContent() {
        // Given & When
        ChatResponse response = ChatResponse.success(null);

        // Then
        assertNull(response.getContent());
        assertTrue(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testEmptyError() {
        // Given
        String emptyError = "";

        // When
        ChatResponse response = ChatResponse.error(emptyError);

        // Then
        assertNull(response.getContent());
        assertFalse(response.isSuccess());
        assertEquals(emptyError, response.getError());
    }

    @Test
    void testNullError() {
        // Given & When
        ChatResponse response = ChatResponse.error(null);

        // Then
        assertNull(response.getContent());
        assertFalse(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testLongContent() {
        // Given
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is a very long response content. ");
        }
        String content = longContent.toString();

        // When
        ChatResponse response = ChatResponse.success(content);

        // Then
        assertEquals(content, response.getContent());
        assertTrue(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testSpecialCharacters() {
        // Given
        String specialContent = "Special chars: 你好 🚀 \"quotes\" 'apostrophes' \n\t\\/ €£¥";

        // When
        ChatResponse response = ChatResponse.success(specialContent);

        // Then
        assertEquals(specialContent, response.getContent());
        assertTrue(response.isSuccess());
        assertNull(response.getError());
    }

    @Test
    void testCompleteJsonRoundTrip() throws JsonProcessingException {
        // Given
        ChatResponse originalResponse = new ChatResponse(
            "Complex response content with special chars: 你好 🚀", 
            true, 
            null);

        // When
        String json = objectMapper.writeValueAsString(originalResponse);
        ChatResponse deserializedResponse = objectMapper.readValue(json, ChatResponse.class);

        // Then
        assertEquals(originalResponse.getContent(), deserializedResponse.getContent());
        assertEquals(originalResponse.isSuccess(), deserializedResponse.isSuccess());
        assertEquals(originalResponse.getError(), deserializedResponse.getError());
        assertEquals(originalResponse, deserializedResponse);
    }


}
