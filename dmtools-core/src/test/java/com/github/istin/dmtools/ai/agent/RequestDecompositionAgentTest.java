package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for RequestDecompositionAgent
 */
public class RequestDecompositionAgentTest extends BaseAgentTest<RequestDecompositionAgent.Params, RequestDecompositionAgent.Result, RequestDecompositionAgent> {

    @Override
    protected RequestDecompositionAgent createAgent() {
        return new RequestDecompositionAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/request_decomposition";
    }

    @Override
    protected RequestDecompositionAgent.Params createTestParams() {
        return new RequestDecompositionAgent.Params(
            "Analyze user authentication",
            "Raw authentication data",
            null,
            null
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "{\"aiRole\":\"Security Analyst\",\"request\":\"Analyze auth\",\"questions\":[\"Q1\",\"Q2\"],\"tasks\":[\"T1\"],\"instructions\":[\"I1\"],\"knownInfo\":\"Known\",\"formattingRules\":\"Rules\",\"fewShots\":\"Examples\"}";
    }

    @Override
    protected void verifyResult(RequestDecompositionAgent.Result result) {
        assertNotNull(result);
        assertEquals("Security Analyst", result.getAiRole());
        assertEquals("Analyze auth", result.getRequest());
        assertNotNull(result.getQuestions());
        assertEquals(2, result.getQuestions().length);
        assertEquals(1, result.getTasks().length);
    }

    @Test
    void testParamsGetters() {
        RequestDecompositionAgent.Params params = createTestParams();
        assertEquals("Analyze user authentication", params.getUserRequest());
        assertEquals("Raw authentication data", params.getRawData());
        assertNull(params.getFiles());
        assertNull(params.getChunks());
    }

    @Test
    void testResultToString() {
        RequestDecompositionAgent.Result result = new RequestDecompositionAgent.Result(
            "Analyst", "Request", new String[]{"Q1"}, new String[]{"T1"}, 
            new String[]{"I1"}, "Known", "Rules", "Examples"
        );
        
        String json = result.toString();
        assertNotNull(json);
        assertTrue(json.contains("Analyst"));
        assertTrue(json.contains("Request"));
    }

    @Test
    void testTransformAIResponse() throws Exception {
        RequestDecompositionAgent.Params params = createTestParams();
        String response = "{\"aiRole\":\"Developer\",\"request\":\"Build feature\"}";

        RequestDecompositionAgent.Result result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals("Developer", result.getAiRole());
        assertEquals("Build feature", result.getRequest());
    }
}
