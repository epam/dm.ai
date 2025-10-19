package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.di.DaggerContentMergeAgentComponent;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.inject.Inject;

public class ContentMergeAgent extends AbstractSimpleAgent<ContentMergeAgent.Params, String> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String task;
        private String sourceContent;
        private String newContent;
        private String contentType; // e.g., "html", "mermaid", "text"
    }

    public ContentMergeAgent() {
        super("agents/content_merge");
        DaggerContentMergeAgentComponent.create().inject(this);
    }

    @Inject
    public ContentMergeAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/content_merge");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        return response;
    }
}