package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for SnippetExtensionAgent
 */
public class SnippetExtensionAgentTest extends BaseAgentTest<SnippetExtensionAgent.Params, Boolean, SnippetExtensionAgent> {

    @Override
    protected SnippetExtensionAgent createAgent() {
        return new SnippetExtensionAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/snippet_extension";
    }

    @Override
    protected SnippetExtensionAgent.Params createTestParams() {
        return new SnippetExtensionAgent.Params("public void test() {}", "Extend method");
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
        SnippetExtensionAgent.Params params = createTestParams();
        assertEquals("public void test() {}", params.getFileSnippet());
        assertEquals("Extend method", params.getTask());
    }

    @Test
    void testTransformAIResponse_True() throws Exception {
        SnippetExtensionAgent.Params params = createTestParams();
        Boolean result = agent.transformAIResponse(params, "true");
        assertTrue(result);
    }

    @Test
    void testTransformAIResponse_False() throws Exception {
        SnippetExtensionAgent.Params params = createTestParams();
        Boolean result = agent.transformAIResponse(params, "false");
        assertFalse(result);
    }
}
