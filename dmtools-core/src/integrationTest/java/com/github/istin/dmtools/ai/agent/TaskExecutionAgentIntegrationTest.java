// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TaskExecutionAgentIntegrationTest {

    private AI mockAI;
    private IPromptTemplateReader mockPromptTemplateReader;
    private TaskExecutionAgent agent;

    @Before
    public void setUp() {
        mockAI = mock(AI.class);
        mockPromptTemplateReader = mock(IPromptTemplateReader.class);
        agent = new TaskExecutionAgent(mockAI, mockPromptTemplateReader);
        agent.ai = mockAI;
        agent.promptTemplateReader = mockPromptTemplateReader;
    }

    @Test
    public void testBrowserSearch() throws Exception {
        String mockResponse = "{\"steps\": [\"Open browser\", \"Navigate to Wikipedia\", \"Search for Albert Einstein\"], \"knownData\": {\"url\": \"https://wikipedia.org\", \"searchTerm\": \"Albert Einstein\"}}";
        when(mockAI.chat(anyString())).thenReturn(mockResponse);
        when(mockPromptTemplateReader.read(anyString(), any())).thenReturn("mock prompt");
        
        TaskExecutionAgent.Result result = agent.run(new TaskExecutionAgent.Params(
                "Go to Wikipedia and search for Albert Einstein"
        ));

        JSONArray steps = result.getSteps();
        JSONObject knownData = result.getKnownData();

        assertNotNull(steps);
        assertNotNull(knownData);
        assertTrue(steps.length() > 0);
        // Verify steps are strings
        for (int i = 0; i < steps.length(); i++) {
            assertTrue(steps.get(i) instanceof String);
            assertFalse(steps.getString(i).isEmpty());
        }
        assertTrue(knownData.has("url"));
        assertTrue(knownData.has("searchTerm") || knownData.has("searchQuery"));
    }

    @Test
    public void testFileOperation() throws Exception {
        String mockResponse = "{\"steps\": [\"Open file manager\", \"Navigate to desktop\", \"Create text file\", \"Write content\"], \"knownData\": {\"fileName\": \"notes.txt\", \"content\": \"Remember meeting\"}}";
        when(mockAI.chat(anyString())).thenReturn(mockResponse);
        when(mockPromptTemplateReader.read(anyString(), any())).thenReturn("mock prompt");
        
        TaskExecutionAgent.Result result = agent.run(new TaskExecutionAgent.Params(
                "Create a new text file on desktop named 'notes.txt' and write 'Remember meeting' in it"
        ));

        JSONArray steps = result.getSteps();
        JSONObject knownData = result.getKnownData();

        assertNotNull(steps);
        assertNotNull(knownData);
        assertTrue(steps.length() > 0);
        // Verify steps format
        for (int i = 0; i < steps.length(); i++) {
            String step = steps.getString(i);
            assertTrue(step instanceof String);
            assertTrue(step.length() > 5);
        }
        assertTrue(knownData.has("fileName"));
        assertTrue(knownData.has("content") || knownData.has("fileContent"));
    }

    @Test
    public void testMultiApplicationTask() throws Exception {
        String mockResponse = "{\"steps\": [\"Take screenshot\", \"Open Google Drive\", \"Upload screenshot\"], \"knownData\": {\"applications\": [\"Screenshot tool\", \"Google Drive\"]}}";
        when(mockAI.chat(anyString())).thenReturn(mockResponse);
        when(mockPromptTemplateReader.read(anyString(), any())).thenReturn("mock prompt");
        
        TaskExecutionAgent.Result result = agent.run(new TaskExecutionAgent.Params(
                "Take a screenshot and upload it to Google Drive"
        ));

        JSONArray steps = result.getSteps();
        JSONObject knownData = result.getKnownData();

        assertNotNull(steps);
        assertNotNull(knownData);
        assertTrue(steps.length() > 0);
        // Verify steps are clear and actionable
        for (int i = 0; i < steps.length(); i++) {
            String step = steps.getString(i);
            assertTrue(step instanceof String);
            assertFalse(step.contains("{"));
            assertFalse(step.contains("}"));
        }
        assertTrue(knownData.has("applications") || knownData.has("application"));
    }
}