package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TaskProgressAgent
 */
public class TaskProgressAgentTest extends BaseAgentTest<TaskProgressAgent.Params, TaskProgressAgent.Result, TaskProgressAgent> {

    @Override
    protected TaskProgressAgent createAgent() {
        return new TaskProgressAgent(mockAI, mockPromptTemplateReader);
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/task_progress";
    }

    @Override
    protected TaskProgressAgent.Params createTestParams() {
        return new TaskProgressAgent.Params("Step 1,Step 2", "Assessment text", true, null);
    }

    @Override
    protected String getMockAIResponse() {
        return "{\"completedSteps\":[\"Step 1\"],\"nextSteps\":[\"Step 2\"],\"explanation\":\"Progress made\"}";
    }

    @Override
    protected void verifyResult(TaskProgressAgent.Result result) {
        assertNotNull(result);
        assertNotNull(result.getCompletedSteps());
        assertNotNull(result.getNextSteps());
        assertEquals("Progress made", result.getExplanation());
    }

    @Test
    void testParamsGetters() {
        TaskProgressAgent.Params params = createTestParams();
        assertEquals("Step 1,Step 2", params.getSteps());
        assertEquals("Assessment text", params.getPreviousAssessment());
        assertTrue(params.getRequireExplanation());
        assertNull(params.getFiles());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        TaskProgressAgent.Params params = createTestParams();
        String response = "{\"completedSteps\":[\"A\",\"B\"],\"nextSteps\":[\"C\"],\"explanation\":\"All good\"}";

        TaskProgressAgent.Result result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals(2, result.getCompletedSteps().length());
        assertEquals(1, result.getNextSteps().length());
        assertEquals("All good", result.getExplanation());
    }
}
