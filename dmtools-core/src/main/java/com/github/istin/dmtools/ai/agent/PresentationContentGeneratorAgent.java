package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerPresentationContentGeneratorAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONArray;

public class PresentationContentGeneratorAgent extends AbstractSimpleAgent<PresentationContentGeneratorAgent.Params, JSONArray> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String topic;
        private String audience;
        private String userRequest;
        private String additionalData;
    }

    public PresentationContentGeneratorAgent() {
        super("agents/presentation_content_generator");
        DaggerPresentationContentGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public JSONArray transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseResponseAsJSONArray(response);
    }
}