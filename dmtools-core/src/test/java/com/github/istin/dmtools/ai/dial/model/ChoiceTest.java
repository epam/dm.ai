package com.github.istin.dmtools.ai.dial.model;

import com.github.istin.dmtools.ai.dial.model.model.Choice;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class ChoiceTest {

    private Choice choice;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        choice = new Choice(mockJsonObject);
    }

    @Test
    public void testChoiceConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"message\": {}}";
        Choice choiceWithJsonString = new Choice(jsonString);
        assertNotNull(choiceWithJsonString);
    }

    @Test
    public void testChoiceConstructorWithJsonObject() {
        assertNotNull(choice);
    }

}