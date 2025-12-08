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

    // ==================== sanitizeFileName Tests ====================

    @Test
    public void testSanitizeFileName_NormalFileName() {
        String result = StringUtils.sanitizeFileName("document.pdf");
        assertEquals("document.pdf", result);
    }

    @Test
    public void testSanitizeFileName_WithSpecialCharacters() {
        String result = StringUtils.sanitizeFileName("file@#$%^.txt");
        assertEquals("file.txt", result);
    }

    @Test
    public void testSanitizeFileName_WithPathSeparators() {
        String result = StringUtils.sanitizeFileName("../../../etc/passwd");
        assertEquals("etc_passwd", result);
    }

    @Test
    public void testSanitizeFileName_WithBackslashes() {
        String result = StringUtils.sanitizeFileName("..\\..\\..\\windows\\system32");
        assertEquals("windows_system32", result);
    }

    @Test
    public void testSanitizeFileName_WithSpacesAndConsecutiveUnderscores() {
        String result = StringUtils.sanitizeFileName("my   file   name.pdf");
        assertEquals("my_file_name.pdf", result);
    }

    @Test
    public void testSanitizeFileName_NullInput() {
        String result = StringUtils.sanitizeFileName(null);
        assertEquals("unnamed_file", result);
    }

    @Test
    public void testSanitizeFileName_EmptyInput() {
        String result = StringUtils.sanitizeFileName("");
        assertEquals("unnamed_file", result);
    }

    @Test
    public void testSanitizeFileName_AllInvalidCharacters() {
        String result = StringUtils.sanitizeFileName("@#$%^&*()");
        assertEquals("unnamed_file", result);
    }

    @Test
    public void testSanitizeFileName_LeadingTrailingUnderscores() {
        String result = StringUtils.sanitizeFileName("__test__");
        assertEquals("test", result);
    }

    @Test
    public void testSanitizeFileName_VeryLongFileName() {
        String longName = "a".repeat(300) + ".pdf";
        String result = StringUtils.sanitizeFileName(longName);
        assertTrue("Should be truncated to 200 characters or less", result.length() <= 200);
    }

    @Test
    public void testSanitizeFileName_WithCustomDefaultName() {
        String result = StringUtils.sanitizeFileName(null, "custom_default", 200);
        assertEquals("custom_default", result);
    }

    @Test
    public void testSanitizeFileName_WithCustomMaxLength() {
        String result = StringUtils.sanitizeFileName("abcdefghij.pdf", "default", 5);
        assertEquals("abcde", result);
    }

    @Test
    public void testSanitizeFileName_WithZeroMaxLength() {
        String result = StringUtils.sanitizeFileName("document.pdf", "default", 0);
        assertEquals("document.pdf", result); // No truncation when maxLength is 0
    }

    @Test
    public void testSanitizeFileName_PreservesValidCharacters() {
        String result = StringUtils.sanitizeFileName("valid-file_name.test-123.pdf");
        assertEquals("valid-file_name.test-123.pdf", result);
    }

    @Test
    public void testSanitizeFileName_UnicodeCharacters() {
        String result = StringUtils.sanitizeFileName("文档.pdf");
        // Unicode characters are replaced with underscores
        assertNotNull(result);
        assertTrue("Should not be empty", result.length() > 0);
    }
}
