package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for BusinessAreaAssessmentAgent
 */
public class BusinessAreaAssessmentAgentTest extends BaseAgentTest<BusinessAreaAssessmentAgent.Params, String, BusinessAreaAssessmentAgent> {

    @Override
    protected BusinessAreaAssessmentAgent createAgent() {
        return new BusinessAreaAssessmentAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/business_area_assessment";
    }

    @Override
    protected BusinessAreaAssessmentAgent.Params createTestParams() {
        return new BusinessAreaAssessmentAgent.Params("Story about user authentication");
    }

    @Override
    protected String getMockAIResponse() {
        return "  Security & Authentication  ";
    }

    @Override
    protected void verifyResult(String result) {
        assertNotNull(result);
        assertEquals("Security & Authentication", result);
    }

    @Test
    void testParamsGetter() {
        BusinessAreaAssessmentAgent.Params params = createTestParams();
        assertEquals("Story about user authentication", params.getStoryDescription());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        BusinessAreaAssessmentAgent.Params params = createTestParams();
        String response = "  Product Management  ";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals("Product Management", result);
    }
}
