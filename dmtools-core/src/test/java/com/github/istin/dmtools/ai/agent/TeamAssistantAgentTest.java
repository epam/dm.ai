package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TeamAssistantAgent
 */
public class TeamAssistantAgentTest extends BaseAgentTest<TeamAssistantAgent.Params, String, TeamAssistantAgent> {

    @Mock
    private RequestDecompositionAgent.Result mockRequest;

    @Override
    protected TeamAssistantAgent createAgent() {
        return new TeamAssistantAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/team_assistant";
    }

    @Override
    protected TeamAssistantAgent.Params createTestParams() {
        RequestDecompositionAgent.Result result = new RequestDecompositionAgent.Result(
            "Assistant", "Request", new String[]{}, new String[]{}, 
            new String[]{}, "Known", "Rules", "Shots"
        );
        return new TeamAssistantAgent.Params(result);
    }

    @Override
    protected String getMockAIResponse() {
        return "<html><h1>Team Report</h1><p>Content</p></html>";
    }

    @Override
    protected void verifyResult(String result) {
        assertNotNull(result);
        assertTrue(result.contains("Team Report"));
    }

    @Test
    void testParamsConstructors() {
        RequestDecompositionAgent.Result result = new RequestDecompositionAgent.Result(
            "Role", "Req", null, null, null, null, null, null
        );
        
        TeamAssistantAgent.Params params1 = new TeamAssistantAgent.Params(result);
        assertNotNull(params1.getRequest());
        
        TeamAssistantAgent.Params params2 = new TeamAssistantAgent.Params(result, null);
        assertNotNull(params2.getRequest());
        
        TeamAssistantAgent.Params params3 = new TeamAssistantAgent.Params(result, null, null);
        assertNotNull(params3.getRequest());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        TeamAssistantAgent.Params params = createTestParams();
        String response = "<html>Response</html>";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals(response, result);
    }

    @Test
    void testConstructorDefault() {
        TeamAssistantAgent agent = new TeamAssistantAgent();
        assertNotNull(agent);
        assertEquals("agents/team_assistant", agent.getPromptName());
    }
}
