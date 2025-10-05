package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for SourceImpactAssessmentAgent
 */
public class SourceImpactAssessmentAgentTest extends BaseAgentTest<SourceImpactAssessmentAgent.Params, Boolean, SourceImpactAssessmentAgent> {

    @Override
    protected SourceImpactAssessmentAgent createAgent() {
        return new SourceImpactAssessmentAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/source_impact_assessment";
    }

    @Override
    protected SourceImpactAssessmentAgent.Params createTestParams() {
        return new SourceImpactAssessmentAgent.Params(
            "backend",
            "Analyze impact of API changes"
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
        SourceImpactAssessmentAgent.Params params = createTestParams();
        assertEquals("backend", params.getSourceType());
        assertEquals("Analyze impact of API changes", params.getTask());
    }

    @Test
    void testTransformAIResponse_True() throws Exception {
        SourceImpactAssessmentAgent.Params params = createTestParams();
        Boolean result = agent.transformAIResponse(params, "true");
        assertTrue(result);
    }

    @Test
    void testTransformAIResponse_False() throws Exception {
        SourceImpactAssessmentAgent.Params params = createTestParams();
        Boolean result = agent.transformAIResponse(params, "false");
        assertFalse(result);
    }
}
