package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerKeywordGeneratorAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONArray;

public class KeywordGeneratorAgent extends AbstractSimpleAgent<KeywordGeneratorAgent.Params, JSONArray> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String searchContext;
        private String task;
        private String blacklist;
    }

    public KeywordGeneratorAgent() {
        super("agents/keyword_generator");
        DaggerKeywordGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public JSONArray transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseResponseAsJSONArray(response);
    }
}