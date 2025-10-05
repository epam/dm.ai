package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for PresentationSlideFormatterAgent
 */
public class PresentationSlideFormatterAgentTest extends BaseAgentTest<PresentationSlideFormatterAgent.Params, JSONArray, PresentationSlideFormatterAgent> {

    @Override
    protected PresentationSlideFormatterAgent createAgent() {
        return new PresentationSlideFormatterAgent();
    }

    @Override
    protected String getExpectedPromptName() {
        return "agents/presentation_slide_formatter";
    }

    @Override
    protected PresentationSlideFormatterAgent.Params createTestParams() {
        JSONArray slides = new JSONArray();
        slides.put("{\"title\":\"Slide 1\"}");
        slides.put("{\"title\":\"Slide 2\"}");
        return new PresentationSlideFormatterAgent.Params(slides);
    }

    @Override
    protected String getMockAIResponse() {
        return "[{\"formatted\":\"Slide 1\"},{\"formatted\":\"Slide 2\"}]";
    }

    @Override
    protected void verifyResult(JSONArray result) {
        assertNotNull(result);
        assertEquals(2, result.length());
    }

    @Test
    void testParamsGetters() {
        PresentationSlideFormatterAgent.Params params = createTestParams();
        assertNotNull(params.getSlidesContent());
        assertEquals(2, params.getSlidesContent().length());
    }

    @Test
    void testParamsSetters() {
        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params();
        JSONArray slides = new JSONArray();
        slides.put("Test");
        
        params.setSlidesContent(slides);
        
        assertEquals(1, params.getSlidesContent().length());
    }

    @Test
    void testTransformAIResponse() throws Exception {
        PresentationSlideFormatterAgent.Params params = createTestParams();
        String response = "[{\"slide\":1},{\"slide\":2},{\"slide\":3}]";

        JSONArray result = agent.transformAIResponse(params, response);
        
        assertNotNull(result);
        assertEquals(3, result.length());
    }
}
