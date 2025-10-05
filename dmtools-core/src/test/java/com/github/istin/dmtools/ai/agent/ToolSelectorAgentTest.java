package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.dto.ToolCallRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ToolSelectorAgent
 */
public class ToolSelectorAgentTest extends BaseAgentTest<ToolSelectorAgent.Params, List<ToolCallRequest>, ToolSelectorAgent> {

    @Override
    protected ToolSelectorAgent createAgent() {
        return new ToolSelectorAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/tool-selector";
    }

    @Override
    protected ToolSelectorAgent.Params createTestParams() {
        ToolSelectorAgent.Params params = new ToolSelectorAgent.Params();
        params.setUserMessage("Select appropriate tools");
        params.setAvailableTools("tool1, tool2, tool3");
        return params;
    }

    @Override
    protected String getMockAIResponse() {
        return "[{\"toolName\":\"tool1\",\"arguments\":{\"param\":\"value\"},\"reason\":\"test reason\"}]";
    }

    @Override
    protected void verifyResult(List<ToolCallRequest> result) {
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).getToolName());
    }

    @Test
    void testParamsGettersAndSetters() {
        ToolSelectorAgent.Params params = new ToolSelectorAgent.Params();
        params.setUserMessage("User message");
        params.setAvailableTools("tools");
        
        assertEquals("User message", params.getUserMessage());
        assertEquals("tools", params.getAvailableTools());
    }

    @Test
    void testParamsConstructor() {
        ToolSelectorAgent.Params params = new ToolSelectorAgent.Params("message", "tools");
        
        assertEquals("message", params.getUserMessage());
        assertEquals("tools", params.getAvailableTools());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        ToolSelectorAgent.Params params = createTestParams();
        String response = "[{\"toolName\":\"tool2\",\"arguments\":{},\"reason\":\"test\"}]";

        List<ToolCallRequest> result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("tool2", result.get(0).getToolName());
    }

    @Test
    void testConstructorWithoutDependencies() {
        ToolSelectorAgent newAgent = new ToolSelectorAgent();
        assertNotNull(newAgent);
        assertEquals("agents/tool-selector", newAgent.getPromptName());
    }
}
