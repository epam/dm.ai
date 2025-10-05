package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for PresentationContentGeneratorAgent
 */
public class PresentationContentGeneratorAgentTest extends BaseAgentTest<PresentationContentGeneratorAgent.Params, JSONArray, PresentationContentGeneratorAgent> {

    @Override
    protected PresentationContentGeneratorAgent createAgent() {
        return new PresentationContentGeneratorAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/presentation_content_generator";
    }

    @Override
    protected PresentationContentGeneratorAgent.Params createTestParams() {
        return new PresentationContentGeneratorAgent.Params(
            "AI in Software Development",
            "Technical Team",
            "Create engaging presentation",
            "Additional context about AI"
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "[{\"slide\":1,\"title\":\"Introduction\"},{\"slide\":2,\"title\":\"AI Benefits\"}]";
    }

    @Override
    protected void verifyResult(JSONArray result) {
        assertNotNull(result);
        assertEquals(2, result.length());
    }

    @Test
    void testParamsGetters() {
        PresentationContentGeneratorAgent.Params params = createTestParams();
        assertEquals("AI in Software Development", params.getTopic());
        assertEquals("Technical Team", params.getAudience());
        assertEquals("Create engaging presentation", params.getUserRequest());
        assertEquals("Additional context about AI", params.getAdditionalData());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        PresentationContentGeneratorAgent.Params params = createTestParams();
        String response = "[{\"title\":\"Slide 1\"},{\"title\":\"Slide 2\"},{\"title\":\"Slide 3\"}]";

        JSONArray result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals(3, result.length());
    }
}
