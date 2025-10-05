package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionTest {

    @Test
    void testDefaultConstructor() {
        Resolution resolution = new Resolution();
        assertNotNull(resolution);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"id\":\"1\",\"name\":\"Fixed\"}";
        Resolution resolution = new Resolution(json);
        
        assertNotNull(resolution);
        assertEquals("1", resolution.getId());
        assertEquals("Fixed", resolution.getName());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("id", "2");
        json.put("name", "Won't Fix");
        
        Resolution resolution = new Resolution(json);
        
        assertNotNull(resolution);
        assertEquals("2", resolution.getId());
        assertEquals("Won't Fix", resolution.getName());
    }

    @Test
    void testGetId() {
        JSONObject json = new JSONObject();
        json.put("id", "10");
        
        Resolution resolution = new Resolution(json);
        
        assertEquals("10", resolution.getId());
    }

    @Test
    void testGetName() {
        JSONObject json = new JSONObject();
        json.put("name", "Done");
        
        Resolution resolution = new Resolution(json);
        
        assertEquals("Done", resolution.getName());
    }

    @Test
    void testSetId() {
        Resolution resolution = new Resolution();
        JSONObject result = resolution.setId("123");
        
        assertNotNull(result);
        assertEquals("123", resolution.getId());
    }

    @Test
    void testIsFixed_True() {
        JSONObject json = new JSONObject();
        json.put("name", "Fixed");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isFixed());
    }

    @Test
    void testIsFixed_CaseInsensitive() {
        JSONObject json = new JSONObject();
        json.put("name", "FIXED");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isFixed());
    }

    @Test
    void testIsFixed_PartialMatch() {
        JSONObject json = new JSONObject();
        json.put("name", "Resolved/Fixed");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isFixed());
    }

    @Test
    void testIsFixed_False() {
        JSONObject json = new JSONObject();
        json.put("name", "Won't Fix");
        
        Resolution resolution = new Resolution(json);
        
        assertFalse(resolution.isFixed());
    }

    @Test
    void testIsRejected_NotAnIssue() {
        JSONObject json = new JSONObject();
        json.put("name", "Not an issue");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_WontFix() {
        JSONObject json = new JSONObject();
        json.put("name", "Won't fix");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_NotABug() {
        JSONObject json = new JSONObject();
        json.put("name", "Not a bug");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_NotValid() {
        JSONObject json = new JSONObject();
        json.put("name", "Not valid");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_CannotReproduce() {
        JSONObject json = new JSONObject();
        json.put("name", "Cannot reproduce");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_Duplicate() {
        JSONObject json = new JSONObject();
        json.put("name", "Duplicate");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_NotImplemented() {
        JSONObject json = new JSONObject();
        json.put("name", "Not implemented");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }

    @Test
    void testIsRejected_False() {
        JSONObject json = new JSONObject();
        json.put("name", "Fixed");
        
        Resolution resolution = new Resolution(json);
        
        assertFalse(resolution.isRejected());
    }

    @Test
    void testIsRejected_CaseInsensitive() {
        JSONObject json = new JSONObject();
        json.put("name", "WON'T FIX");
        
        Resolution resolution = new Resolution(json);
        
        assertTrue(resolution.isRejected());
    }
}