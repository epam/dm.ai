package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerTestCaseGeneratorAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TestCaseGeneratorAgent extends AbstractSimpleAgent<TestCaseGeneratorAgent.Params, List<TestCaseGeneratorAgent.TestCase>> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String priorities;
        private String existingTestCases;
        private String storyDescription;
        private String extraRules;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    @Data
    public static class TestCase {
        private String priority;
        private String summary;
        private String description;
    }

    public TestCaseGeneratorAgent() {
        super("agents/test_case_generator");
        DaggerTestCaseGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public List<TestCase> transformAIResponse(Params params, String response) throws Exception {
        JSONArray jsonArray = AIResponseParser.parseResponseAsJSONArray(response);
        List<TestCase> testCases = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            TestCase testCase = new TestCase(
                    jsonObject.getString("priority"),
                    jsonObject.getString("summary"),
                    jsonObject.getString("description")
            );
            testCases.add(testCase);
        }

        return testCases;
    }
}