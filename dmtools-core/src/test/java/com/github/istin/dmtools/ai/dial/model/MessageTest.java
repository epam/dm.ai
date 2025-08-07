package com.github.istin.dmtools.ai.dial.model;

import com.github.istin.dmtools.ai.dial.model.model.Message;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class MessageTest {


    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"content\":\"Hello, World!\"}";
        Message message = new Message(jsonString);
        assertEquals("Hello, World!", message.getContent());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("content", "Hello, JSON!");
        Message message = new Message(jsonObject);
        assertEquals("Hello, JSON!", message.getContent());
    }

    @Test
    public void testGetContent() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("content", "Test Content");
        Message message = new Message(jsonObject);
        assertEquals("Test Content", message.getContent());
    }

    @Test
    public void testConstructorWithInvalidJSONString() {
        String invalidJsonString = "{content:Hello, World!}";
        assertThrows(JSONException.class, () -> {
            new Message(invalidJsonString);
        });
    }
}