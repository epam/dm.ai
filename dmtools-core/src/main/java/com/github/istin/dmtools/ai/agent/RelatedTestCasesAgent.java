package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerRelatedTestCasesAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONArray;

public class RelatedTestCasesAgent extends AbstractSimpleAgent<RelatedTestCasesAgent.Params, JSONArray> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String newStory;
        private String existingTestCases;
        private String extraRules;
    }

    public RelatedTestCasesAgent() {
        super("agents/related_test_cases");
        DaggerRelatedTestCasesAgentComponent.create().inject(this);
    }

    @Override
    public JSONArray transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseResponseAsJSONArray(response);
    }
}