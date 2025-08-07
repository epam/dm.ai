package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerUserRequestToPresentationScriptParamsAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class UserRequestToPresentationScriptParamsAgent extends AbstractSimpleAgent<UserRequestToPresentationScriptParamsAgent.Params, JSONObject> {

    @AllArgsConstructor
    @Getter
    public static class Params implements GetFiles {
        private String userRequest;
        private List<File> files;
    }

    public UserRequestToPresentationScriptParamsAgent() {
        super("agents/user_request_to_presentation_script_params");
        DaggerUserRequestToPresentationScriptParamsAgentComponent.create().inject(this);
    }

    @Override
    public JSONObject transformAIResponse(Params params, String response) throws Exception {
        // Parse the response to extract JSON from markdown code blocks if present
        return AIResponseParser.parseResponseAsJSONObject(response);
    }
} 