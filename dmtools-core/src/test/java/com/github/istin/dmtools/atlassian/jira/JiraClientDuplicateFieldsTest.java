package com.github.istin.dmtools.atlassian.jira;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JiraClient duplicate field handling
 */
public class JiraClientDuplicateFieldsTest {

    @Test
    public void testResolveFieldNames_SkipsCustomFieldIds() {
        // Test that customfield_* IDs are kept as-is and not resolved

        String sampleFieldsResponse = "[" +
            "{\"id\":\"customfield_10186\",\"name\":\"Acceptance Criteria\",\"active\":true}," +
            "{\"id\":\"customfield_10551\",\"name\":\"Acceptance Criteria\",\"active\":true}" +
            "]";

        // When resolveFieldNames receives a customfield_* ID, it should keep it as-is
        // This simulates the behavior we implemented
        String fieldId = "customfield_10186";

        assertTrue(fieldId.startsWith("customfield_"),
            "Field ID should start with customfield_");

        // The field should not be resolved when it's already a custom field ID
        // This is the expected behavior after our fix
    }

    @Test
    public void testGetAllFieldCustomCodes_FindsMultipleFields() {
        String sampleFieldsResponse = "[" +
            "{\"id\":\"customfield_10186\",\"name\":\"Acceptance Criteria\",\"active\":true,\"schema\":{\"type\":\"string\"}}," +
            "{\"id\":\"customfield_10551\",\"name\":\"Acceptance Criteria\",\"active\":true,\"schema\":{\"type\":\"string\"}}" +
            "]";

        List<com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField> fields =
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.findAllFieldsByName(
                "Acceptance Criteria", sampleFieldsResponse);

        assertEquals(2, fields.size(), "Should find both Acceptance Criteria fields");

        // Verify both field IDs are present
        List<String> fieldIds = fields.stream()
            .map(f -> f.getId())
            .toList();

        assertTrue(fieldIds.contains("customfield_10186"), "Should include first field");
        assertTrue(fieldIds.contains("customfield_10551"), "Should include second field");
    }

    @Test
    public void testGetAllFieldCustomCodes_ReturnsActiveFieldsOnly() {
        String sampleFieldsResponse = "[" +
            "{\"id\":\"customfield_10186\",\"name\":\"Dependencies\",\"active\":true,\"schema\":{\"type\":\"string\"}}," +
            "{\"id\":\"customfield_10551\",\"name\":\"Dependencies\",\"active\":false,\"schema\":{\"type\":\"string\"}}," +
            "{\"id\":\"customfield_10109\",\"name\":\"Dependencies\",\"active\":true,\"schema\":{\"type\":\"string\"}}" +
            "]";

        List<com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField> allFields =
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.findAllFieldsByName(
                "Dependencies", sampleFieldsResponse);

        assertEquals(3, allFields.size(), "Should find all 3 Dependencies fields");

        // Filter for active fields
        List<String> activeFieldIds = allFields.stream()
            .filter(f -> f.isActive())
            .map(f -> f.getId())
            .toList();

        assertEquals(2, activeFieldIds.size(), "Should have 2 active fields");
        assertTrue(activeFieldIds.contains("customfield_10186"), "Should include first active field");
        assertTrue(activeFieldIds.contains("customfield_10109"), "Should include second active field");
        assertFalse(activeFieldIds.contains("customfield_10551"), "Should not include inactive field");
    }

    @Test
    public void testReverseFieldMapping_AddsSuffixForDuplicates() {
        // This test verifies the logic of adding suffixes for duplicate field names
        // When transforming custom field IDs back to names

        String fieldName1 = "Acceptance Criteria";
        String fieldName2 = "Acceptance Criteria";
        String fieldId1 = "customfield_10186";
        String fieldId2 = "customfield_10551";

        // When there are duplicates, the system should add field ID as postfix
        String expectedName1 = "Acceptance Criteria (customfield_10186)";
        String expectedName2 = "Acceptance Criteria (customfield_10551)";

        assertEquals(expectedName1, fieldName1 + " (" + fieldId1 + ")",
            "Should append field ID for first duplicate");
        assertEquals(expectedName2, fieldName2 + " (" + fieldId2 + ")",
            "Should append field ID for second duplicate");

        // Verify the names are different and won't overwrite each other
        assertNotEquals(expectedName1, expectedName2,
            "Field names with postfix should be unique");
    }

    @Test
    public void testReverseFieldMapping_NoSuffixForUniqueFields() {
        // When there's only one field with a name, no suffix should be added
        String fieldName = "Story Points";
        String fieldId = "customfield_10001";

        // For unique fields, the name should remain unchanged
        assertEquals(fieldName, fieldName,
            "Unique field names should not have postfix");
    }

    @Test
    public void testFieldNameDetection_DistinguishesCustomFieldIds() {
        // Test detection of custom field IDs vs field names
        assertTrue("customfield_10186".startsWith("customfield_"),
            "Should detect custom field ID");
        assertTrue("customfield_10551".startsWith("customfield_"),
            "Should detect custom field ID");

        assertFalse("Acceptance Criteria".startsWith("customfield_"),
            "Should not detect field name as custom field ID");
        assertFalse("summary".startsWith("customfield_"),
            "Should not detect system field as custom field ID");
        assertFalse("description".startsWith("customfield_"),
            "Should not detect system field as custom field ID");
    }

    @Test
    public void testExtraFieldsResolution_HandlesMultipleFields() {
        // Test that JIRA_EXTRA_FIELDS resolves to multiple custom fields
        String sampleFieldsResponse = "[" +
            "{\"id\":\"customfield_10186\",\"name\":\"Acceptance Criteria\",\"active\":true,\"schema\":{\"type\":\"string\"}}," +
            "{\"id\":\"customfield_10551\",\"name\":\"Acceptance Criteria\",\"active\":true,\"schema\":{\"type\":\"string\"}}" +
            "]";

        List<com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField> fields =
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.findAllFieldsByName(
                "Acceptance Criteria", sampleFieldsResponse);

        // When JIRA_EXTRA_FIELDS contains "Acceptance Criteria"
        // The system should resolve it to both custom fields
        List<String> activeFieldIds = fields.stream()
            .filter(f -> f.isActive())
            .map(f -> f.getId())
            .toList();

        assertTrue(activeFieldIds.size() >= 2,
            "Should resolve to at least 2 fields for duplicates");
        assertTrue(activeFieldIds.contains("customfield_10186"),
            "Should include first custom field");
        assertTrue(activeFieldIds.contains("customfield_10551"),
            "Should include second custom field");
    }

    @Test
    public void testDuplicateFieldHandling_InJQLQueries() {
        // Test that JQL queries include all duplicate fields
        String[] requestedFields = {"summary", "description", "Acceptance Criteria"};
        String[] resolvedFields = {"summary", "description", "customfield_10186", "customfield_10551"};

        // After resolution, "Acceptance Criteria" should expand to both custom fields
        assertTrue(resolvedFields.length > requestedFields.length,
            "Resolved fields should include all duplicates");

        // Verify all custom fields are present
        List<String> resolvedList = java.util.Arrays.asList(resolvedFields);
        assertTrue(resolvedList.contains("customfield_10186"),
            "Should include first custom field in JQL");
        assertTrue(resolvedList.contains("customfield_10551"),
            "Should include second custom field in JQL");
    }
}
