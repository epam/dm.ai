package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ContentMergeAgent
 */
public class ContentMergeAgentTest extends BaseAgentTest<ContentMergeAgent.Params, String, ContentMergeAgent> {

    @Override
    protected ContentMergeAgent createAgent() {
        return new ContentMergeAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/content_merge";
    }

    @Override
    protected ContentMergeAgent.Params createTestParams() {
        return new ContentMergeAgent.Params(
            "Merge task",
            "Source content here",
            "New content to merge",
            "html"
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "Merged content result";
    }

    @Override
    protected void verifyResult(String result) {
        assertNotNull(result);
        assertEquals("Merged content result", result);
    }

    @Test
    void testParamsGetters() {
        ContentMergeAgent.Params params = createTestParams();
        
        assertEquals("Merge task", params.getTask());
        assertEquals("Source content here", params.getSourceContent());
        assertEquals("New content to merge", params.getNewContent());
        assertEquals("html", params.getContentType());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        ContentMergeAgent.Params params = createTestParams();
        String response = "AI response";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals(response, result);
    }
}
