package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerTestCaseDeduplicationAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TestCaseDeduplicationAgent extends AbstractSimpleAgent<TestCaseDeduplicationAgent.Params, List<TestCaseGeneratorAgent.TestCase>> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String newTestCases;
        private String existingTestCases;
        private String existingGeneratedTestCases;
    }

    public TestCaseDeduplicationAgent() {
        super("agents/test_case_deduplication");
        DaggerTestCaseDeduplicationAgentComponent.create().inject(this);
    }

    @Override
    public List<TestCaseGeneratorAgent.TestCase> transformAIResponse(Params params, String response) throws Exception {
        JSONArray jsonArray = AIResponseParser.parseResponseAsJSONArray(response);
        List<TestCaseGeneratorAgent.TestCase> testCases = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
                    jsonObject.getString("priority"),
                    jsonObject.getString("summary"),
                    jsonObject.getString("description")
            );
            testCases.add(testCase);
        }

        return testCases;
    }
}