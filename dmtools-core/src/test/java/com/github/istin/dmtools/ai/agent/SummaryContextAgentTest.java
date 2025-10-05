package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for SummaryContextAgent
 */
public class SummaryContextAgentTest extends BaseAgentTest<SummaryContextAgent.Params, String, SummaryContextAgent> {

    @Override
    protected SummaryContextAgent createAgent() {
        return new SummaryContextAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/summary_context";
    }

    @Override
    protected SummaryContextAgent.Params createTestParams() {
        return new SummaryContextAgent.Params(
            "Summarize task",
            "Raw data to summarize",
            null  // No files for prompt rendering test
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "Summary of the content";
    }

    @Override
    protected void verifyResult(String result) {
        assertNotNull(result);
        assertEquals("Summary of the content", result);
    }

    @Test
    void testParamsGetters() {
        SummaryContextAgent.Params params = new SummaryContextAgent.Params(
            "Summarize task",
            "Raw data to summarize",
            List.of(new File("test.txt"))
        );
        
        assertEquals("Summarize task", params.getTask());
        assertEquals("Raw data to summarize", params.getRawData());
        assertNotNull(params.getFiles());
        assertEquals(1, params.getFiles().size());
    }

    @Test
    void testTransformAIResponse_LongResponse() throws Exception {
        SummaryContextAgent.Params params = createTestParams();
        String response = "This is a long response with more than 10 characters";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals(response, result);
    }

    @Test
    void testTransformAIResponse_ShortTrueResponse() throws Exception {
        SummaryContextAgent.Params params = createTestParams();
        String response = "true";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals("true", result);
    }

    @Test
    void testTransformAIResponse_ShortFalseResponse() throws Exception {
        SummaryContextAgent.Params params = createTestParams();
        String response = "false";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals("", result);
    }

    @Test
    void testTransformAIResponse_ShortInvalidResponse() throws Exception {
        SummaryContextAgent.Params params = createTestParams();
        String response = "xyz";

        String result = agent.transformAIResponse(params, response);
        
        assertEquals("", result);
    }
}
