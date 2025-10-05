package com.github.istin.dmtools.ai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JSBridgeScriptGeneratorAgent
 */
public class JSBridgeScriptGeneratorAgentTest extends BaseAgentTest<JSBridgeScriptGeneratorAgent.Params, String, JSBridgeScriptGeneratorAgent> {

    @Override
    protected JSBridgeScriptGeneratorAgent createAgent() {
        return new JSBridgeScriptGeneratorAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/js_bridge_script_generator";
    }

    @Override
    protected JSBridgeScriptGeneratorAgent.Params createTestParams() {
        return new JSBridgeScriptGeneratorAgent.Params(
            "Generate report",
            "API description",
            "Requirements",
            null,
            "Examples"
        );
    }

    @Override
    protected String getMockAIResponse() {
        return "```javascript\nfunction generateReport() { return 'report'; }\n```";
    }

    @Override
    protected void verifyResult(String result) {
        assertNotNull(result);
        assertTrue(result.contains("generateReport"));
    }

    @Test
    void testParamsGetters() {
        JSBridgeScriptGeneratorAgent.Params params = createTestParams();
        assertEquals("Generate report", params.getTask());
        assertEquals("API description", params.getApiDescription());
        assertEquals("Requirements", params.getAdditionalRequirements());
        assertEquals("Examples", params.getExamples());
        assertNull(params.getFiles());
    }

    @Test
    void testParamsConstructorWithoutExamples() {
        JSBridgeScriptGeneratorAgent.Params params = new JSBridgeScriptGeneratorAgent.Params(
            "task", "api", "framework", "format", "requirements", null
        );
        
        assertEquals("task", params.getTask());
        assertEquals("api", params.getApiDescription());
        assertEquals("requirements", params.getAdditionalRequirements());
        assertNull(params.getExamples());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        JSBridgeScriptGeneratorAgent.Params params = createTestParams();
        String response = "```javascript\nconst code = 'test';\n```";

        String result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertTrue(result.contains("code"));
    }
}
