// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.dto.ToolCallRequest;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ToolSelectorAgentIntegrationTest {

    @Test
    public void testRun() throws Exception {
        ToolSelectorAgent agent = new ToolSelectorAgent();
        // Prepare agent parameters
        String userMessage = "Show me ticket PROJ-123";
        String availableTools = "- Name: dmtools_jira_get_ticket\n  Description: Get JIRA ticket by key";
        ToolSelectorAgent.Params params = new ToolSelectorAgent.Params(userMessage, availableTools);

        // Run the agent
        List<ToolCallRequest> result = agent.run(params);

        // Assertions
        assertNotNull(result);
        assertFalse(result.isEmpty());
        ToolCallRequest toolCallRequest = result.get(0);
        assertEquals("dmtools_jira_get_ticket", toolCallRequest.getToolName());
        assertNotNull(toolCallRequest.getArguments());
        assertEquals("PROJ-123", toolCallRequest.getArguments().get("ticketKey"));
    }
}