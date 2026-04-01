// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerBusinessAreaAssessmentAgentComponent;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class BusinessAreaAssessmentAgent extends AbstractSimpleAgent<BusinessAreaAssessmentAgent.Params, String> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String storyDescription;
    }

    public BusinessAreaAssessmentAgent() {
        super("agents/business_area_assessment");
        DaggerBusinessAreaAssessmentAgentComponent.create().inject(this);
    }

    @Override
    public String transformAIResponse(Params params, String response) {
        // Return the area name directly as it should be a simple string
        return response.trim();
    }
}