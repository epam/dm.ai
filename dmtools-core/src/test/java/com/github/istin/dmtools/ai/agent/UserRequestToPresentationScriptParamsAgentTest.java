// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserRequestToPresentationScriptParamsAgentTest {

    @Mock
    private AI ai;

    @Mock
    private IPromptTemplateReader promptTemplateReader;

    @InjectMocks
    private UserRequestToPresentationScriptParamsAgent agent;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() throws Exception {
        when(promptTemplateReader.read(anyString(), any())).thenReturn("mocked prompt");
        String mockJsonResponse = "{\"task\": \"some task\", \"jsFramework\": \"graaljs\", \"outputFormat\": \"function\", \"additionalRequirements\": \"none\"}";
        when(ai.chat(any(), anyString(), any(JSONObject.class))).thenReturn(mockJsonResponse);
    }

    @Test
    public void testDashboardRequest() throws Exception {
        // Arrange
        UserRequestToPresentationScriptParamsAgent.Params params = new UserRequestToPresentationScriptParamsAgent.Params(
                "Create a dashboard showing sales performance with charts",
                Collections.emptyList()
        );

        // Act
        JSONObject result = agent.run(params);

        // Assert
        assertNotNull(result);
        JsonNode jsonNode = objectMapper.readTree(result.toString());

        // Verify required fields are present
        assertTrue(jsonNode.has("task"));
        assertTrue(jsonNode.has("jsFramework"));
        assertTrue(jsonNode.has("outputFormat"));
        assertTrue(jsonNode.has("additionalRequirements"));

        // Verify framework is graaljs
        assertEquals("graaljs", jsonNode.get("jsFramework").asText());

        // Verify task is not empty
        assertFalse(jsonNode.get("task").asText().trim().isEmpty());

        // Verify output format is valid
        String outputFormat = jsonNode.get("outputFormat").asText();
        assertTrue(outputFormat.equals("function") || outputFormat.equals("module") || outputFormat.equals("complete script"));

        System.out.println("Dashboard Request Result:");
        System.out.println(result);
    }

    @Test
    public void testReportRequest() throws Exception {
        // Arrange
        UserRequestToPresentationScriptParamsAgent.Params params = new UserRequestToPresentationScriptParamsAgent.Params(
                "Generate a team progress report presentation for this quarter",
                Collections.emptyList()
        );

        // Act
        JSONObject result = agent.run(params);

        // Assert
        assertNotNull(result);
        JsonNode jsonNode = objectMapper.readTree(result.toString());

        // Verify required fields
        assertEquals("graaljs", jsonNode.get("jsFramework").asText());
        assertFalse(jsonNode.get("task").asText().trim().isEmpty());
        assertFalse(jsonNode.get("additionalRequirements").asText().trim().isEmpty());

        System.out.println("Report Request Result:");
        System.out.println(result);
    }

    @Test
    public void testAnalysisRequest() throws Exception {
        // Arrange
        UserRequestToPresentationScriptParamsAgent.Params params = new UserRequestToPresentationScriptParamsAgent.Params(
                "Build a tool to analyze project risks and show them in slides",
                Collections.emptyList()
        );

        // Act
        JSONObject result = agent.run(params);

        // Assert
        assertNotNull(result);
        JsonNode jsonNode = objectMapper.readTree(result.toString());

        // Verify all required fields are present and valid
        assertEquals("graaljs", jsonNode.get("jsFramework").asText());
        assertNotNull(jsonNode.get("task"));
        assertNotNull(jsonNode.get("outputFormat"));
        assertNotNull(jsonNode.get("additionalRequirements"));

        String outputFormat = jsonNode.get("outputFormat").asText();
        assertTrue(outputFormat.equals("function") || outputFormat.equals("module") || outputFormat.equals("complete script"));

        System.out.println("Analysis Request Result:");
        System.out.println(result);
    }

    @Test
    public void testGenericRequest() throws Exception {
        // Arrange
        UserRequestToPresentationScriptParamsAgent.Params params = new UserRequestToPresentationScriptParamsAgent.Params(
                "Help me create a presentation to track project status",
                Collections.emptyList()
        );

        // Act
        JSONObject result = agent.run(params);

        // Assert
        assertNotNull(result);
        JsonNode jsonNode = objectMapper.readTree(result.toString());

        // Verify JSON structure is complete
        assertNotNull(jsonNode.get("task"));
        assertNotNull(jsonNode.get("jsFramework"));
        assertNotNull(jsonNode.get("outputFormat"));
        assertNotNull(jsonNode.get("additionalRequirements"));

        assertEquals("graaljs", jsonNode.get("jsFramework").asText());

        System.out.println("Generic Request Result:");
        System.out.println(result);
    }
} 