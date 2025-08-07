package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

public class RequestDecompositionAgent extends AbstractSimpleAgent<RequestDecompositionAgent.Params, RequestDecompositionAgent.Result> {

    @AllArgsConstructor
    @Getter
    public static class Params implements AbstractSimpleAgent.GetFiles, AbstractSimpleAgent.GetChunks {
        private String userRequest;
        private String rawData;
        private List<File> files;
        private List<ChunkPreparation.Chunk> chunks;
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class Result {
        private String aiRole;
        private String request;
        private String[] questions;
        private String[] tasks;
        private String[] instructions;
        private String knownInfo;
        private String formattingRules;
        private String fewShots;

        @Override
        public String toString() {
            JSONObject json = new JSONObject();
            json.put("aiRole", aiRole);
            json.put("request", request);
            if (questions != null) {
                json.put("questions", new JSONArray(questions));
            }
            if (tasks != null) {
                json.put("tasks", new JSONArray(tasks));
            }
            if (instructions != null) {
                json.put("instructions", new JSONArray(instructions));
            }
            json.put("knownInfo", knownInfo);
            json.put("formattingRules", formattingRules);
            json.put("fewShots", fewShots);
            return json.toString(1);
        }
    }

    @Inject
    public RequestDecompositionAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/request_decomposition");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public Result transformAIResponse(Params params, String response) throws Exception {
        JSONObject jsonResponse = AIResponseParser.parseResponseAsJSONObject(response);
        return new Result(
                jsonResponse.getString("aiRole"),
                jsonResponse.getString("request"),
                jsonResponse.has("questions") ? jsonResponse.getJSONArray("questions").toList().stream().map(Object::toString).toArray(String[]::new) : new String[0],
                jsonResponse.has("tasks") ? jsonResponse.getJSONArray("tasks").toList().stream().map(Object::toString).toArray(String[]::new) : new String[0],
                jsonResponse.has("instructions") ? jsonResponse.getJSONArray("instructions").toList().stream().map(Object::toString).toArray(String[]::new) : new String[0],
                jsonResponse.optString("knownInfo"),
                jsonResponse.optString("formattingRules"),
                jsonResponse.optString("fewShots")
        );
    }
}