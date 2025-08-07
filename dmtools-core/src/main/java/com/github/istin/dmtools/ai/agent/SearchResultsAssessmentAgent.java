package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerSearchResultsAssessmentAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONArray;

public class SearchResultsAssessmentAgent extends AbstractSimpleAgent<SearchResultsAssessmentAgent.Params, JSONArray> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String sourceType; // e.g., "jira", "confluence", "files"
        private String keyField; // the field to extract (e.g., "key", "id", "path", "textMatch")
        private String taskDescription;
        private String searchResults; // JSON string containing search results
    }

    public SearchResultsAssessmentAgent() {
        super("agents/search_results_assessment");
        DaggerSearchResultsAssessmentAgentComponent.create().inject(this);
    }

    @Override
    public JSONArray transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseResponseAsJSONArray(response);
    }
}