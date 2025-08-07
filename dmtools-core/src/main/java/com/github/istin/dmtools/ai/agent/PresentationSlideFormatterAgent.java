package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerPresentationSlideFormatterAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONArray;

public class PresentationSlideFormatterAgent extends AbstractSimpleAgent<PresentationSlideFormatterAgent.Params, JSONArray> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Params {
        private JSONArray slidesContent;
    }

    public PresentationSlideFormatterAgent() {
        super("agents/presentation_slide_formatter");
        DaggerPresentationSlideFormatterAgentComponent.create().inject(this);
    }

    @Override
    public JSONArray transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseResponseAsJSONArray(response);
    }
}