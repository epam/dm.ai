package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;

public class TaskExecutionAgent extends AbstractSimpleAgent<TaskExecutionAgent.Params, TaskExecutionAgent.Result> {

    @AllArgsConstructor
    @Getter
    @Data
    public static class Params {
        private String taskDescription;
    }

    @AllArgsConstructor
    @Getter
    @Data
    public static class Result {
        private JSONArray steps;
        private JSONObject knownData;
    }

    @Inject
    public TaskExecutionAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/task_execution");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }

    @Override
    public Result transformAIResponse(Params params, String response) throws Exception {
        JSONObject jsonResponse = AIResponseParser.parseResponseAsJSONObject(response);
        return new Result(
                jsonResponse.getJSONArray("steps"),
                jsonResponse.getJSONObject("knownData")
        );
    }
}