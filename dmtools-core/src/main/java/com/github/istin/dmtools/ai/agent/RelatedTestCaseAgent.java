package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerRelatedTestCaseAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class RelatedTestCaseAgent extends AbstractSimpleAgent<RelatedTestCaseAgent.Params, Boolean> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String newStory;
        private String existingTestCase;
        private String extraRules;
    }

    public RelatedTestCaseAgent() {
        super("agents/related_test_case");
        DaggerRelatedTestCaseAgentComponent.create().inject(this);
    }

    @Override
    public Boolean transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseBooleanResponse(response);
    }
}