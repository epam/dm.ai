package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerSnippetExtensionAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class SnippetExtensionAgent extends AbstractSimpleAgent<SnippetExtensionAgent.Params, Boolean> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String fileSnippet;
        private String task;
    }

    public SnippetExtensionAgent() {
        super("agents/snippet_extension");
        DaggerSnippetExtensionAgentComponent.create().inject(this);
    }

    @Override
    public Boolean transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseBooleanResponse(response);
    }
}