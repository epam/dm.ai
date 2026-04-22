// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerRelatedTestCaseAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class RelatedTestCaseAgent extends AbstractSimpleAgent<RelatedTestCaseAgent.Params, RelatedTestCaseAgent.Result> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String newStory;
        private String existingTestCase;
        private String extraRules;
        private String explanationPrompt;
    }

    @AllArgsConstructor
    @Getter
    public static class Result {
        private boolean isRelated;
        private String explanation;
    }

    public RelatedTestCaseAgent() {
        super("agents/related_test_case");
        DaggerRelatedTestCaseAgentComponent.create().inject(this);
    }

    @Override
    public Result transformAIResponse(Params params, String response) throws Exception {
        AIResponseParser.BooleanWithExplanation parsed = AIResponseParser.parseBooleanWithExplanation(response);
        return new Result(parsed.value, parsed.explanation);
    }
}