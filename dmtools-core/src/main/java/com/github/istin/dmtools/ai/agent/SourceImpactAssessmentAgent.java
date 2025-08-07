package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.inject.Inject;

public class SourceImpactAssessmentAgent extends AbstractSimpleAgent<SourceImpactAssessmentAgent.Params, Boolean> {

    @AllArgsConstructor
    @Getter
    public static class Params {

        private String sourceType;
        private String task;

    }

    @Inject
    public SourceImpactAssessmentAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/source_impact_assessment");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public Boolean transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseBooleanResponse(response);
    }

}
