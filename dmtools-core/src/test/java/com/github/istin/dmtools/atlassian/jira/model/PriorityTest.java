package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PriorityTest {

    @Test
    void testDefaultConstructor() {
        Priority priority = new Priority();
        assertNotNull(priority);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"id\":\"1\",\"name\":\"High\"}";
        Priority priority = new Priority(json);
        
        assertNotNull(priority);
        assertEquals("High", priority.getName());
        assertEquals(1, priority.getId());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("id", "2");
        json.put("name", "Medium");
        
        Priority priority = new Priority(json);
        
        assertNotNull(priority);
        assertEquals("Medium", priority.getName());
        assertEquals(2, priority.getId());
    }

    @Test
    void testGetName() {
        JSONObject json = new JSONObject();
        json.put("name", "Critical");
        
        Priority priority = new Priority(json);
        
        assertEquals("Critical", priority.getName());
    }

    @Test
    void testGetId() {
        JSONObject json = new JSONObject();
        json.put("id", "3");
        
        Priority priority = new Priority(json);
        
        assertEquals(3, priority.getId());
    }

    @Test
    void testCreatePostObject() {
        JSONObject json = new JSONObject();
        json.put("id", "4");
        json.put("name", "Low");
        
        Priority priority = new Priority(json);
        JSONObject postObject = priority.createPostObject();
        
        assertNotNull(postObject);
        assertEquals("4", postObject.getString("id"));
    }

    @Test
    void testCreatePostObject_ContainsOnlyId() {
        JSONObject json = new JSONObject();
        json.put("id", "5");
        json.put("name", "Blocker");
        json.put("otherField", "otherValue");
        
        Priority priority = new Priority(json);
        JSONObject postObject = priority.createPostObject();
        
        assertNotNull(postObject);
        assertEquals(1, postObject.length());
        assertTrue(postObject.has("id"));
        assertFalse(postObject.has("name"));
        assertFalse(postObject.has("otherField"));
    }
}