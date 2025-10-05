package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentTest {

    @Test
    void testDefaultConstructor() {
        Component component = new Component();
        assertNotNull(component);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"id\":\"100\",\"name\":\"Backend\"}";
        Component component = new Component(json);
        
        assertNotNull(component);
        assertEquals("100", component.getId());
        assertEquals("Backend", component.getName());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("id", "200");
        json.put("name", "Frontend");
        
        Component component = new Component(json);
        
        assertNotNull(component);
        assertEquals("200", component.getId());
        assertEquals("Frontend", component.getName());
    }

    @Test
    void testGetId() {
        JSONObject json = new JSONObject();
        json.put("id", "300");
        
        Component component = new Component(json);
        
        assertEquals("300", component.getId());
    }

    @Test
    void testGetName() {
        JSONObject json = new JSONObject();
        json.put("name", "API");
        
        Component component = new Component(json);
        
        assertEquals("API", component.getName());
    }

    @Test
    void testSetName() {
        Component component = new Component();
        component.setName("Database");
        
        assertEquals("Database", component.getName());
    }

    @Test
    void testSetName_UpdateExisting() {
        JSONObject json = new JSONObject();
        json.put("name", "OldName");
        
        Component component = new Component(json);
        component.setName("NewName");
        
        assertEquals("NewName", component.getName());
    }

    @Test
    void testGetId_Null() {
        Component component = new Component();
        assertNull(component.getId());
    }

    @Test
    void testGetName_Null() {
        Component component = new Component();
        assertNull(component.getName());
    }
}