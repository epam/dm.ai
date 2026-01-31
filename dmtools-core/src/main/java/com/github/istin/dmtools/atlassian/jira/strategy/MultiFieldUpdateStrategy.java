package com.github.istin.dmtools.atlassian.jira.strategy;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Strategy for handling multiple custom fields with the same name in Jira.
 * This situation often occurs in Jira when fields are created at different scopes
 * or when fields are duplicated across projects.
 */
public class MultiFieldUpdateStrategy {

    /**
     * Represents a custom field with its metadata
     */
    public static class CustomField {
        private final String id;
        private final String name;
        private final String schema;
        private final boolean active;
        private final JSONObject rawField;

        public CustomField(String id, String name, String schema, boolean active, JSONObject rawField) {
            this.id = id;
            this.name = name;
            this.schema = schema;
            this.active = active;
            this.rawField = rawField;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getSchema() {
            return schema;
        }

        public boolean isActive() {
            return active;
        }

        public JSONObject getRawField() {
            return rawField;
        }
    }

    /**
     * Find all custom fields with the given name from the fields response
     * @param fieldName The field name to search for
     * @param fieldsJsonResponse The JSON response from Jira fields endpoint
     * @return List of all matching custom fields
     */
    public static List<CustomField> findAllFieldsByName(String fieldName, String fieldsJsonResponse) {
        List<CustomField> matchingFields = new ArrayList<>();

        try {
            JSONArray fields = new JSONArray(fieldsJsonResponse);

            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String name = field.optString("name", "");

                if (name.equalsIgnoreCase(fieldName)) {
                    String id = field.getString("id");
                    String schema = field.has("schema") ? field.getJSONObject("schema").optString("type", "") : "";
                    boolean active = field.optBoolean("active", true);

                    matchingFields.add(new CustomField(id, name, schema, active, field));
                }
            }
        } catch (Exception e) {
            // Log error and return empty list
            System.err.println("Error parsing fields response: " + e.getMessage());
        }

        return matchingFields;
    }

    /**
     * Select the most appropriate field from multiple fields with the same name
     * Priority order:
     * 1. Active fields over inactive
     * 2. Project-specific fields (higher customfield number often indicates newer/project-specific)
     * 3. Text fields over other types for description/dependencies fields
     *
     * @param fields List of fields with the same name
     * @return The most appropriate field, or null if list is empty
     */
    public static CustomField selectBestField(List<CustomField> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }

        if (fields.size() == 1) {
            return fields.get(0);
        }

        // Sort by priority
        fields.sort((a, b) -> {
            // Prefer active fields
            if (a.isActive() != b.isActive()) {
                return a.isActive() ? -1 : 1;
            }

            // For dependencies/description fields, prefer text schema
            if (a.getName().toLowerCase().contains("depend") ||
                a.getName().toLowerCase().contains("description")) {
                boolean aIsText = a.getSchema().contains("string") || a.getSchema().contains("text");
                boolean bIsText = b.getSchema().contains("string") || b.getSchema().contains("text");
                if (aIsText != bIsText) {
                    return aIsText ? -1 : 1;
                }
            }

            // Prefer higher customfield numbers (usually newer/project-specific)
            int aNum = extractFieldNumber(a.getId());
            int bNum = extractFieldNumber(b.getId());
            return Integer.compare(bNum, aNum);
        });

        return fields.get(0);
    }

    /**
     * Extract the numeric part from a customfield ID
     * @param fieldId Field ID like "customfield_11448"
     * @return The numeric part, or 0 if cannot parse
     */
    private static int extractFieldNumber(String fieldId) {
        try {
            return Integer.parseInt(fieldId.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Strategy for updating multiple fields with the same name
     * @param fields List of fields with the same name
     * @param value The value to set
     * @param updateAll If true, update all matching fields; if false, update only the best match
     * @return Map of field IDs to update operations
     */
    public static Map<String, Object> createUpdateStrategy(List<CustomField> fields, Object value, boolean updateAll) {
        Map<String, Object> updates = new HashMap<>();

        if (fields == null || fields.isEmpty()) {
            return updates;
        }

        if (updateAll) {
            // Update all matching fields
            for (CustomField field : fields) {
                if (field.isActive()) {
                    updates.put(field.getId(), value);
                }
            }
        } else {
            // Update only the best matching field
            CustomField bestField = selectBestField(fields);
            if (bestField != null && bestField.isActive()) {
                updates.put(bestField.getId(), value);
            }
        }

        return updates;
    }

    /**
     * Log information about multiple fields with the same name
     * @param fieldName The field name
     * @param fields List of fields found
     * @param selectedField The field that was selected
     */
    public static void logFieldSelection(String fieldName, List<CustomField> fields, CustomField selectedField) {
        if (fields.size() > 1) {
            System.out.println("WARNING: Found " + fields.size() + " fields with name '" + fieldName + "':");
            for (CustomField field : fields) {
                String status = field.equals(selectedField) ? " [SELECTED]" : "";
                System.out.println("  - " + field.getId() + " (active: " + field.isActive() +
                                 ", schema: " + field.getSchema() + ")" + status);
            }
        }
    }
}