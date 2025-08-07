package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

public class AutomationTestingGeneratorAgent extends AbstractSimpleAgent<AutomationTestingGeneratorAgent.Params, String> {

    @AllArgsConstructor
    @Getter
    public static class Params implements AbstractSimpleAgent.GetFiles {
        private String currentUri;
        private String screenSource;
        private String task;
        private String lastError;
        private String previousJavascript;
        private List<File> files;
    }

    @Inject
    public AutomationTestingGeneratorAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/automation_testing_js_generator");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseCodeResponse(response);
    }
}