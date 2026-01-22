package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.common.model.ToText;
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

    @Getter
    public static class Params {
        private String priorities;
        private String existingTestCases;
        private String storyDescription;
        private String extraRules;
        private boolean overridePromptExamples = false;
        private String examples = "";
        private String customFieldsRules = "";

        public Params(String priorities, String existingTestCases, String storyDescription, String extraRules) {
            this.priorities = priorities;
            this.existingTestCases = existingTestCases;
            this.storyDescription = storyDescription;
            this.extraRules = extraRules;
        }

        public Params(String priorities, String existingTestCases, String storyDescription, String extraRules, boolean overridePromptExamples, String examples, String customFieldsRules) {
            this.priorities = priorities;
            this.existingTestCases = existingTestCases;
            this.storyDescription = storyDescription;
            this.extraRules = extraRules;
            this.overridePromptExamples = overridePromptExamples;
            this.examples = examples;
            this.customFieldsRules = customFieldsRules != null ? customFieldsRules : "";
        }
    }

    @Getter
    @ToString
    @Data
    public static class TestCase implements ToText {
        private String key;
        private String priority;
        private String summary;
        private String description;
        private JSONObject customFields;

        public TestCase(String priority, String summary, String description) {
            this.priority = priority;
            this.summary = summary;
            this.description = description;
            this.customFields = new JSONObject();
        }

        public TestCase(String priority, String summary, String description, JSONObject customFields) {
            this.priority = priority;
            this.summary = summary;
            this.description = description;
            this.customFields = customFields != null ? customFields : new JSONObject();
        }

        @Override
        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("Priority: ").append(priority).append("\n");
            sb.append("Summary: ").append(summary).append("\n");
            sb.append("Description: ").append(description).append("\n");
            if (customFields != null && customFields.length() > 0) {
                sb.append("Custom Fields: ").append(customFields.toString()).append("\n");
            }
            return sb.toString();
        }
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
            TestCase testCase = parseTestCaseFromJson(jsonObject);
            testCases.add(testCase);
        }

        return testCases;
    }

    private TestCase parseTestCaseFromJson(JSONObject jsonObject) {
        String priority = extractPriority(jsonObject);
        JSONObject customFields = extractCustomFields(jsonObject);

        return new TestCase(
                priority,
                jsonObject.getString("summary"),
                jsonObject.getString("description"),
                customFields
        );
    }

    private String extractPriority(JSONObject jsonObject) {
        // Handle priority as either string or number (ADO uses numeric priorities)
        if (jsonObject.has("priority")) {
            Object priorityObj = jsonObject.get("priority");
            if (priorityObj instanceof Number) {
                return String.valueOf(priorityObj);
            } else {
                return jsonObject.getString("priority");
            }
        }
        return "";
    }

    private JSONObject extractCustomFields(JSONObject jsonObject) {
        // Extract customFields if present
        if (jsonObject.has("customFields") && !jsonObject.isNull("customFields")) {
            Object customFieldsObj = jsonObject.get("customFields");
            if (customFieldsObj instanceof JSONObject) {
                return jsonObject.getJSONObject("customFields");
            }
        }
        return new JSONObject();
    }

    public static JSONObject createTestCase(String priority, String summary, String description) {
        return createTestCase(priority, summary, description, null);
    }

    public static JSONObject createTestCase(String priority, String summary, String description, JSONObject customFields) {
        JSONObject testCase = new JSONObject()
                .put("priority", priority)
                .put("summary", summary)
                .put("description", description);
        if (customFields != null && customFields.length() > 0) {
            testCase.put("customFields", customFields);
        }
        return testCase;
    }
}