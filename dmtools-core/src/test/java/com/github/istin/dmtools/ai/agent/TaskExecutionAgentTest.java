package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TaskExecutionAgent
 */
public class TaskExecutionAgentTest extends BaseAgentTest<TaskExecutionAgent.Params, TaskExecutionAgent.Result, TaskExecutionAgent> {

    @Override
    protected TaskExecutionAgent createAgent() {
        return new TaskExecutionAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/task_execution";
    }

    @Override
    protected TaskExecutionAgent.Params createTestParams() {
        return new TaskExecutionAgent.Params("Execute the deployment");
    }

    @Override
    protected String getMockAIResponse() {
        return "{\"steps\":[\"Step 1\",\"Step 2\"],\"knownData\":{\"env\":\"production\"}}";
    }

    @Override
    protected void verifyResult(TaskExecutionAgent.Result result) {
        assertNotNull(result);
        assertNotNull(result.getSteps());
        assertEquals(2, result.getSteps().length());
        assertNotNull(result.getKnownData());
    }

    @Test
    void testParamsGetters() {
        TaskExecutionAgent.Params params = createTestParams();
        assertEquals("Execute the deployment", params.getTaskDescription());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        TaskExecutionAgent.Params params = createTestParams();
        String response = "{\"steps\":[\"Build\",\"Test\",\"Deploy\"],\"knownData\":{\"version\":\"1.0\"}}";

        TaskExecutionAgent.Result result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals(3, result.getSteps().length());
        assertEquals("Build", result.getSteps().getString(0));
        assertEquals("1.0", result.getKnownData().getString("version"));
    }
}
