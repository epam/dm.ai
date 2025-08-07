package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

public class SummaryContextAgent extends AbstractSimpleAgent<SummaryContextAgent.Params, String> {

    @AllArgsConstructor
    @Getter
    public static class Params implements AbstractSimpleAgent.GetFiles {
        private String task;
        private String rawData;
        private List<File> files;
    }

    @Inject
    public SummaryContextAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/summary_context");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        if (response.length() < 10) {
            try {
                if (!AIResponseParser.parseBooleanResponse(response)) {
                    return "";
                }
            } catch (IllegalArgumentException e) {
                return "";
            }
        }
        return response;
    }
}