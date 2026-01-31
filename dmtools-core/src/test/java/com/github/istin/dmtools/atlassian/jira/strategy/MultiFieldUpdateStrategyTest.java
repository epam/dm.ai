package com.github.istin.dmtools.atlassian.jira.strategy;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MultiFieldUpdateStrategy
 */
public class MultiFieldUpdateStrategyTest {

    private String sampleFieldsResponse;

    @BeforeEach
    public void setUp() {
        // Create sample Jira fields response with multiple "Dependencies" fields
        JSONArray fields = new JSONArray();

        // Add first Dependencies field (older, inactive)
        fields.put(new JSONObject()
                .put("id", "customfield_10109")
                .put("name", "Dependencies")
                .put("active", false)
                .put("schema", new JSONObject().put("type", "string")));

        // Add second Dependencies field (newer, active, text type)
        fields.put(new JSONObject()
                .put("id", "customfield_11448")
                .put("name", "Dependencies")
                .put("active", true)
                .put("schema", new JSONObject().put("type", "text")));

        // Add third Dependencies field (active but not text)
        fields.put(new JSONObject()
                .put("id", "customfield_10500")
                .put("name", "Dependencies")
                .put("active", true)
                .put("schema", new JSONObject().put("type", "array")));

        // Add some other fields
        fields.put(new JSONObject()
                .put("id", "customfield_10001")
                .put("name", "Story Points")
                .put("active", true));

        sampleFieldsResponse = fields.toString();
    }

    @Test
    public void testFindAllFieldsByName() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("Dependencies", sampleFieldsResponse);

        assertEquals(3, fields.size(), "Should find 3 Dependencies fields");

        // Verify all fields have the correct name
        for (MultiFieldUpdateStrategy.CustomField field : fields) {
            assertEquals("Dependencies", field.getName());
        }
    }

    @Test
    public void testFindAllFieldsByNameCaseInsensitive() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("dependencies", sampleFieldsResponse);

        assertEquals(3, fields.size(), "Should find fields regardless of case");
    }

    @Test
    public void testFindAllFieldsByNameNotFound() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("NonExistentField", sampleFieldsResponse);

        assertTrue(fields.isEmpty(), "Should return empty list for non-existent field");
    }

    @Test
    public void testSelectBestField() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("Dependencies", sampleFieldsResponse);

        MultiFieldUpdateStrategy.CustomField bestField =
                MultiFieldUpdateStrategy.selectBestField(fields);

        assertNotNull(bestField);
        assertEquals("customfield_11448", bestField.getId(),
                "Should select the active text field with higher number");
        assertTrue(bestField.isActive(), "Selected field should be active");
    }

    @Test
    public void testSelectBestFieldWithSingleField() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("Story Points", sampleFieldsResponse);

        MultiFieldUpdateStrategy.CustomField bestField =
                MultiFieldUpdateStrategy.selectBestField(fields);

        assertNotNull(bestField);
        assertEquals("customfield_10001", bestField.getId());
    }

    @Test
    public void testSelectBestFieldWithEmptyList() {
        List<MultiFieldUpdateStrategy.CustomField> emptyList = List.of();
        MultiFieldUpdateStrategy.CustomField bestField =
                MultiFieldUpdateStrategy.selectBestField(emptyList);

        assertNull(bestField, "Should return null for empty list");
    }

    @Test
    public void testCreateUpdateStrategyUpdateAll() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("Dependencies", sampleFieldsResponse);

        String testValue = "Test dependency value";
        Map<String, Object> updates =
                MultiFieldUpdateStrategy.createUpdateStrategy(fields, testValue, true);

        // Should update only active fields
        assertEquals(2, updates.size(), "Should update 2 active fields");
        assertTrue(updates.containsKey("customfield_11448"));
        assertTrue(updates.containsKey("customfield_10500"));
        assertFalse(updates.containsKey("customfield_10109"), "Should not update inactive field");

        // All values should be the same
        for (Object value : updates.values()) {
            assertEquals(testValue, value);
        }
    }

    @Test
    public void testCreateUpdateStrategyUpdateBestOnly() {
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("Dependencies", sampleFieldsResponse);

        String testValue = "Test dependency value";
        Map<String, Object> updates =
                MultiFieldUpdateStrategy.createUpdateStrategy(fields, testValue, false);

        // Should update only the best field
        assertEquals(1, updates.size(), "Should update only 1 field");
        assertTrue(updates.containsKey("customfield_11448"), "Should update the best field");
        assertEquals(testValue, updates.get("customfield_11448"));
    }

    @Test
    public void testExtractFieldNumber() {
        // Test via the strategy methods
        JSONArray fields = new JSONArray();
        fields.put(new JSONObject()
                .put("id", "customfield_11448")
                .put("name", "Test1")
                .put("active", true));
        fields.put(new JSONObject()
                .put("id", "customfield_10109")
                .put("name", "Test1")
                .put("active", true));

        List<MultiFieldUpdateStrategy.CustomField> fieldList =
                MultiFieldUpdateStrategy.findAllFieldsByName("Test1", fields.toString());

        MultiFieldUpdateStrategy.CustomField bestField =
                MultiFieldUpdateStrategy.selectBestField(fieldList);

        // Should prefer higher number
        assertEquals("customfield_11448", bestField.getId(),
                "Should prefer field with higher number");
    }

    @Test
    public void testHandleMalformedJson() {
        String malformedJson = "not a valid json";
        List<MultiFieldUpdateStrategy.CustomField> fields =
                MultiFieldUpdateStrategy.findAllFieldsByName("Test", malformedJson);

        assertTrue(fields.isEmpty(), "Should return empty list for malformed JSON");
    }

    @Test
    public void testHandleFieldsWithoutSchema() {
        JSONArray fields = new JSONArray();
        fields.put(new JSONObject()
                .put("id", "customfield_10001")
                .put("name", "TestField")
                .put("active", true));
        // No schema field

        List<MultiFieldUpdateStrategy.CustomField> fieldList =
                MultiFieldUpdateStrategy.findAllFieldsByName("TestField", fields.toString());

        assertEquals(1, fieldList.size());
        assertEquals("", fieldList.get(0).getSchema(), "Should handle missing schema gracefully");
    }
}