package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for RelatedTestCaseAgent
 */
public class RelatedTestCaseAgentTest extends BaseAgentTest<RelatedTestCaseAgent.Params, Boolean, RelatedTestCaseAgent> {

    @Override
    protected RelatedTestCaseAgent createAgent() {
        return new RelatedTestCaseAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/related_test_case";
    }

    @Override
    protected RelatedTestCaseAgent.Params createTestParams() {
        return new RelatedTestCaseAgent.Params(
            "New user login story",
            "Existing test case for authentication",
            "Extra validation rules"
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "true";
    }

    @Override
    protected void verifyResult(Boolean result) {
        assertNotNull(result);
        assertTrue(result);
    }

    @Test
    void testParamsGetters() {
        RelatedTestCaseAgent.Params params = createTestParams();
        assertEquals("New user login story", params.getNewStory());
        assertEquals("Existing test case for authentication", params.getExistingTestCase());
        assertEquals("Extra validation rules", params.getExtraRules());
    }

    @Test
    void testTransformAIResponse_True() throws Exception {
        RelatedTestCaseAgent.Params params = createTestParams();
        Boolean result = agent.transformAIResponse(params, "true");
        assertTrue(result);
    }

    @Test
    void testTransformAIResponse_False() throws Exception {
        RelatedTestCaseAgent.Params params = createTestParams();
        Boolean result = agent.transformAIResponse(params, "false");
        assertFalse(result);
    }
}
