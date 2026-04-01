// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

public class SummaryContextAgentTest {

    SummaryContextAgent agent;
    AI mockAI;
    IPromptTemplateReader mockPromptTemplateReader;

    @Before
    public void setUp() throws Exception {
        mockAI = mock(AI.class);
        mockPromptTemplateReader = mock(IPromptTemplateReader.class);
        
        // Mock the AI response
        when(mockAI.chat(anyString())).thenReturn("Mock summary response");
        when(mockPromptTemplateReader.read(anyString(), any())).thenReturn("mock prompt");
        
        agent = new SummaryContextAgent(mockAI, mockPromptTemplateReader);
        agent.ai = mockAI;
        agent.promptTemplateReader = mockPromptTemplateReader;
    }

    @Test
    public void testDemoPageSetValue() throws Exception {
        String result = agent.run(new SummaryContextAgent.Params(
                "Some task Details",
                "any raw data to assess\n",
                null
        ));
        assertFalse(result.isEmpty());
    }

}
