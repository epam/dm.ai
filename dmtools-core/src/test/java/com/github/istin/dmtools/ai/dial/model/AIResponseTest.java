package com.github.istin.dmtools.ai.dial.model;

import com.github.istin.dmtools.ai.dial.model.model.AIResponse;
import com.github.istin.dmtools.ai.dial.model.model.Choice;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AIResponseTest {

    @Test
    public void testDefaultConstructor() {
        AIResponse aiResponse = new AIResponse();
        assertNotNull(aiResponse);
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"choices\":[]}";
        AIResponse aiResponse = new AIResponse(jsonString);
        assertNotNull(aiResponse);
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        AIResponse aiResponse = new AIResponse(jsonObject);
        assertNotNull(aiResponse);
    }

    @Test
    public void testGetChoices() {
        AIResponse aiResponse = Mockito.spy(new AIResponse());
        List<Choice> choices = aiResponse.getChoices();
        assertNotNull(choices);
        assertTrue(choices.isEmpty());
    }
}