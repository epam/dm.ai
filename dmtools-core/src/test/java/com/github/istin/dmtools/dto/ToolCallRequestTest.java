// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testToolCallRequestCreation() {
        // Given
        String toolName = "jira-get-ticket";
        Map<String, Object> arguments = Map.of("ticketKey", "DMC-100");
        String reason = "Get ticket information for analysis";

        // When
        ToolCallRequest request = new ToolCallRequest(toolName, reason, arguments);

        // Then
        assertEquals(toolName, request.getToolName());
        assertEquals(arguments, request.getArguments());
        assertEquals(reason, request.getReason());
    }

    @Test
    void testJsonSerialization() throws JsonProcessingException {
        // Given
        String toolName = "jira-get-ticket";
        Map<String, Object> arguments = Map.of("ticketKey", "DMC-100", "fields", new String[]{"summary", "status"});
        String reason = "Get ticket information for analysis";
        ToolCallRequest request = new ToolCallRequest(toolName, reason, arguments);

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertTrue(json.contains("\"toolName\":\"jira-get-ticket\""));
        assertTrue(json.contains("\"reason\":\"Get ticket information for analysis\""));
        assertTrue(json.contains("\"arguments\":{"));
        assertTrue(json.contains("\"ticketKey\":\"DMC-100\""));
    }

    @Test
    void testJsonDeserialization() throws JsonProcessingException {
        // Given
        String json = "{\n" +
                "  \"toolName\": \"confluence-get-content\",\n" +
                "  \"arguments\": {\n" +
                "    \"contentId\": \"123456\",\n" +
                "    \"expand\": \"body.storage\"\n" +
                "  },\n" +
                "  \"reason\": \"Retrieve content for documentation update\"\n" +
                "}";

        // When
        ToolCallRequest request = objectMapper.readValue(json, ToolCallRequest.class);

        // Then
        assertEquals("confluence-get-content", request.getToolName());
        assertEquals("Retrieve content for documentation update", request.getReason());
        assertNotNull(request.getArguments());
        assertEquals("123456", request.getArguments().get("contentId"));
        assertEquals("body.storage", request.getArguments().get("expand"));
    }

    @Test
    void testJsonDeserializationWithoutReason() throws JsonProcessingException {
        // Given - JSON without reason field (backward compatibility)
        String json = "{\n" +
                "  \"toolName\": \"jira-get-ticket\",\n" +
                "  \"arguments\": {\n" +
                "    \"ticketKey\": \"DMC-100\"\n" +
                "  }\n" +
                "}";

        // When
        ToolCallRequest request = objectMapper.readValue(json, ToolCallRequest.class);

        // Then
        assertEquals("jira-get-ticket", request.getToolName());
        assertNull(request.getReason()); // Should be null if not provided
        assertNotNull(request.getArguments());
        assertEquals("DMC-100", request.getArguments().get("ticketKey"));
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        String toolName = "test-tool";
        Map<String, Object> arguments = Map.of("param", "value");
        String reason = "Test reason";

        ToolCallRequest request1 = new ToolCallRequest(toolName, reason, arguments);
        ToolCallRequest request2 = new ToolCallRequest(toolName, reason, arguments);
        ToolCallRequest request3 = new ToolCallRequest("different-tool", reason, arguments);

        // Then
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    void testToString() {
        // Given
        String toolName = "jira-search";
        Map<String, Object> arguments = Map.of("query", "project = DMC");
        String reason = "Search for relevant tickets";

        ToolCallRequest request = new ToolCallRequest(toolName, reason, arguments);

        // When
        String toString = request.toString();

        // Then
        assertTrue(toString.contains("ToolCallRequest"));
        assertTrue(toString.contains("jira-search"));
        assertTrue(toString.contains("Search for relevant tickets"));
    }

    @Test
    void testWithEmptyArguments() {
        // Given
        String toolName = "health-check";
        Map<String, Object> emptyArguments = Map.of();
        String reason = "Check system health";

        // When
        ToolCallRequest request = new ToolCallRequest(toolName, reason, emptyArguments);

        // Then
        assertEquals(toolName, request.getToolName());
        assertTrue(request.getArguments().isEmpty());
        assertEquals(reason, request.getReason());
    }

    @Test
    void testWithNullArguments() {
        // Given
        String toolName = "simple-tool";
        String reason = "Simple operation";

        // When
        ToolCallRequest request = new ToolCallRequest(toolName, reason, null);

        // Then
        assertEquals(toolName, request.getToolName());
        assertNull(request.getArguments());
        assertEquals(reason, request.getReason());
    }

    @Test
    void testWithNullReason() {
        // Given
        String toolName = "test-tool";
        Map<String, Object> arguments = Map.of("key", "value");

        // When
        ToolCallRequest request = new ToolCallRequest(toolName, null, arguments);

        // Then
        assertEquals(toolName, request.getToolName());
        assertEquals(arguments, request.getArguments());
        assertNull(request.getReason());
    }

    @Test
    void testComplexArgumentsStructure() {
        // Given
        String toolName = "complex-tool";
        Map<String, Object> complexArguments = Map.of(
            "stringParam", "value",
            "numberParam", 42,
            "arrayParam", new String[]{"a", "b", "c"},
            "objectParam", Map.of("nested", "value")
        );
        String reason = "Complex operation with various parameter types";

        // When
        ToolCallRequest request = new ToolCallRequest(toolName, reason, complexArguments);

        // Then
        assertEquals(toolName, request.getToolName());
        assertEquals(complexArguments, request.getArguments());
        assertEquals(reason, request.getReason());
        assertEquals("value", request.getArguments().get("stringParam"));
        assertEquals(42, request.getArguments().get("numberParam"));
        assertInstanceOf(String[].class, request.getArguments().get("arrayParam"));
        assertInstanceOf(Map.class, request.getArguments().get("objectParam"));
    }
}
