package com.github.istin.dmtools.atlassian.jira;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JiraClient field update behavior - simplified version
 * Full tests would require complex mocking setup
 */
public class JiraClientUpdateFieldTest {

    @Test
    public void testMultiFieldUpdateStrategyIntegration() {
        // This test verifies that the MultiFieldUpdateStrategy is properly integrated
        // More detailed testing is done in MultiFieldUpdateStrategyTest

        String sampleResponse = "[{\"id\":\"customfield_11448\",\"name\":\"Dependencies\",\"active\":true},"
                + "{\"id\":\"customfield_10109\",\"name\":\"Dependencies\",\"active\":false}]";

        List<com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField> fields =
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.findAllFieldsByName("Dependencies", sampleResponse);

        assertEquals(2, fields.size(), "Should find both Dependencies fields");

        // Verify best field selection
        com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField bestField =
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.selectBestField(fields);

        assertNotNull(bestField);
        assertEquals("customfield_11448", bestField.getId(), "Should select active field");
    }

    @Test
    public void testFieldNameDetection() {
        // Test that we can distinguish between field names and custom field IDs
        assertTrue("customfield_11448".startsWith("customfield_"), "Should detect custom field ID");
        assertFalse("Dependencies".startsWith("customfield_"), "Should detect field name");
        assertFalse("summary".startsWith("customfield_"), "System fields should not be detected as custom field IDs");
    }

    // --- coerceFieldValue tests ---

    @Test
    public void testCoerceFieldValue_integerString() {
        // CLI passes "8" for Story Points — must become Integer 8 to avoid Jira 400
        Object result = JiraClient.coerceFieldValue("8");
        assertEquals(Integer.valueOf(8), result);
        assertInstanceOf(Integer.class, result);
    }

    @Test
    public void testCoerceFieldValue_negativeInteger() {
        Object result = JiraClient.coerceFieldValue("-3");
        assertEquals(Integer.valueOf(-3), result);
    }

    @Test
    public void testCoerceFieldValue_largeNumber_becomesLong() {
        // Values that overflow int should become Long
        Object result = JiraClient.coerceFieldValue("3000000000");
        assertEquals(Long.valueOf(3000000000L), result);
        assertInstanceOf(Long.class, result);
    }

    @Test
    public void testCoerceFieldValue_doubleString() {
        Object result = JiraClient.coerceFieldValue("3.14");
        assertEquals(Double.valueOf(3.14), result);
        assertInstanceOf(Double.class, result);
    }

    @Test
    public void testCoerceFieldValue_booleanTrue() {
        assertEquals(Boolean.TRUE, JiraClient.coerceFieldValue("true"));
        assertEquals(Boolean.TRUE, JiraClient.coerceFieldValue("TRUE"));
    }

    @Test
    public void testCoerceFieldValue_booleanFalse() {
        assertEquals(Boolean.FALSE, JiraClient.coerceFieldValue("false"));
        assertEquals(Boolean.FALSE, JiraClient.coerceFieldValue("False"));
    }

    @Test
    public void testCoerceFieldValue_jsonObject() {
        Object result = JiraClient.coerceFieldValue("{\"name\":\"High\"}");
        assertInstanceOf(JSONObject.class, result);
        assertEquals("High", ((JSONObject) result).getString("name"));
    }

    @Test
    public void testCoerceFieldValue_jsonArray() {
        Object result = JiraClient.coerceFieldValue("[\"tag1\",\"tag2\"]");
        assertInstanceOf(JSONArray.class, result);
        assertEquals(2, ((JSONArray) result).length());
    }

    @Test
    public void testCoerceFieldValue_plainString_unchanged() {
        // Non-numeric strings must not be changed
        Object result = JiraClient.coerceFieldValue("In Progress");
        assertEquals("In Progress", result);
        assertInstanceOf(String.class, result);
    }

    @Test
    public void testCoerceFieldValue_emptyString_unchanged() {
        Object result = JiraClient.coerceFieldValue("");
        assertEquals("", result);
    }

    @Test
    public void testCoerceFieldValue_nonStringValue_unchanged() {
        // If the value is already typed (e.g. from JS), it must pass through untouched
        Integer already = 42;
        assertSame(already, JiraClient.coerceFieldValue(already));

        JSONObject obj = new JSONObject();
        assertSame(obj, JiraClient.coerceFieldValue(obj));
    }

    @Test
    public void testCoerceFieldValue_null_unchanged() {
        assertNull(JiraClient.coerceFieldValue(null));
    }
}