// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for RelatedTestCaseAgent
 */
public class RelatedTestCaseAgentTest extends BaseAgentTest<RelatedTestCaseAgent.Params, RelatedTestCaseAgent.Result, RelatedTestCaseAgent> {

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
            "Extra validation rules",
            null
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "true";
    }

    @Override
    protected void verifyResult(RelatedTestCaseAgent.Result result) {
        assertNotNull(result);
        assertTrue(result.isRelated());
    }

    @Test
    void testParamsGetters() {
        RelatedTestCaseAgent.Params params = createTestParams();
        assertEquals("New user login story", params.getNewStory());
        assertEquals("Existing test case for authentication", params.getExistingTestCase());
        assertEquals("Extra validation rules", params.getExtraRules());
        assertNull(params.getExplanationPrompt());
    }

    @Test
    void testParamsWithExplanationPrompt() {
        RelatedTestCaseAgent.Params params = new RelatedTestCaseAgent.Params(
            "story", "test case", "rules", "Explain why it is related"
        );
        assertEquals("Explain why it is related", params.getExplanationPrompt());
    }

    @Test
    void testTransformAIResponse_True() throws Exception {
        RelatedTestCaseAgent.Params params = createTestParams();
        RelatedTestCaseAgent.Result result = agent.transformAIResponse(params, "true");
        assertTrue(result.isRelated());
        assertNull(result.getExplanation());
    }

    @Test
    void testTransformAIResponse_TrueWithExplanation() throws Exception {
        RelatedTestCaseAgent.Params params = createTestParams();
        RelatedTestCaseAgent.Result result = agent.transformAIResponse(params, "true, because it covers the same auth flow");
        assertTrue(result.isRelated());
        assertEquals("because it covers the same auth flow", result.getExplanation());
    }

    @Test
    void testTransformAIResponse_TrueDeprecationReason() throws Exception {
        RelatedTestCaseAgent.Params params = createTestParams();
        RelatedTestCaseAgent.Result result = agent.transformAIResponse(params, "true, This test case needs to be deprecated once this story is delivered.");
        assertTrue(result.isRelated());
        assertEquals("This test case needs to be deprecated once this story is delivered.", result.getExplanation());
    }

    @Test
    void testTransformAIResponse_False() throws Exception {
        RelatedTestCaseAgent.Params params = createTestParams();
        RelatedTestCaseAgent.Result result = agent.transformAIResponse(params, "false");
        assertFalse(result.isRelated());
        assertNull(result.getExplanation());
    }
}
