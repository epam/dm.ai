package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for SearchResultsAssessmentAgent
 */
public class SearchResultsAssessmentAgentTest extends BaseAgentTest<SearchResultsAssessmentAgent.Params, JSONArray, SearchResultsAssessmentAgent> {

    @Override
    protected SearchResultsAssessmentAgent createAgent() {
        return new SearchResultsAssessmentAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/search_results_assessment";
    }

    @Override
    protected SearchResultsAssessmentAgent.Params createTestParams() {
        return new SearchResultsAssessmentAgent.Params(
            "jira",
            "key",
            "Find relevant tickets",
            "[{\"key\":\"PROJ-1\"},{\"key\":\"PROJ-2\"}]"
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "[\"PROJ-1\",\"PROJ-2\"]";
    }

    @Override
    protected void verifyResult(JSONArray result) {
        assertNotNull(result);
        assertEquals(2, result.length());
        assertEquals("PROJ-1", result.getString(0));
    }

    @Test
    void testParamsGetters() {
        SearchResultsAssessmentAgent.Params params = createTestParams();
        assertEquals("jira", params.getSourceType());
        assertEquals("key", params.getKeyField());
        assertEquals("Find relevant tickets", params.getTaskDescription());
        assertTrue(params.getSearchResults().contains("PROJ-1"));
    }

    @Test
    void testTransformAIResponse() throws Exception {
        SearchResultsAssessmentAgent.Params params = createTestParams();
        String response = "[\"result1\",\"result2\",\"result3\"]";

        JSONArray result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals(3, result.length());
        assertEquals("result1", result.getString(0));
    }
}
