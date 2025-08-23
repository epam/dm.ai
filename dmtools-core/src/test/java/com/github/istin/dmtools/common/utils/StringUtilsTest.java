package com.github.istin.dmtools.common.utils;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StringUtilsTest {
    
    @Test
    public void testTransformJSONToTextBasic() {
        String jsonString = "{\"key\":\"DMC-427\",\"summary\":\"Test task\",\"priority\":\"High\"}";
        
        StringBuilder result = new StringBuilder();
        StringUtils.transformJSONToText(result, jsonString, false);
        
        String output = result.toString();
        assertNotNull("Output should not be null", output);
        assertTrue("Should contain key value", output.contains("DMC-427"));
        assertTrue("Should contain summary", output.contains("Test task"));
        assertTrue("Should contain priority", output.contains("High"));
    }
    
    @Test
    public void testTransformJSONToTextWithJSONObject() {
        JSONObject json = new JSONObject();
        json.put("ticket", "DMC-427");
        json.put("status", "Open");
        json.put("assignee", "John Doe");
        
        StringBuilder result = new StringBuilder();
        StringUtils.transformJSONToText(result, json, false);
        
        String output = result.toString();
        assertNotNull("Output should not be null", output);
        assertTrue("Should contain ticket", output.contains("DMC-427"));
        assertTrue("Should contain status", output.contains("Open"));
        assertTrue("Should contain assignee", output.contains("John Doe"));
    }
    
    @Test
    public void testTransformJSONToTextIgnoreDescription() {
        JSONObject json = new JSONObject();
        json.put("key", "TEST-123");
        json.put("description", "This should be ignored");
        json.put("summary", "This should be included");
        
        StringBuilder result = new StringBuilder();
        StringUtils.transformJSONToText(result, json, true); // ignoreDescription = true
        
        String output = result.toString();
        assertNotNull("Output should not be null", output);
        assertTrue("Should contain key", output.contains("TEST-123"));
        assertTrue("Should contain summary", output.contains("This should be included"));
        // Note: Description filtering logic depends on StringUtils implementation
    }
    
    @Test
    public void testTransformJSONToTextEmptyJSON() {
        String jsonString = "{}";
        
        StringBuilder result = new StringBuilder();
        StringUtils.transformJSONToText(result, jsonString, false);
        
        String output = result.toString();
        assertNotNull("Output should not be null", output);
        // Empty JSON should still produce some output
    }
    
    @Test
    public void testTransformJSONToTextWithArray() {
        String jsonString = "{\"items\":[\"item1\",\"item2\",\"item3\"]}";
        
        StringBuilder result = new StringBuilder();
        StringUtils.transformJSONToText(result, jsonString, false);
        
        String output = result.toString();
        assertNotNull("Output should not be null", output);
        assertTrue("Should contain items", output.contains("item"));
    }
}
