package com.github.istin.dmtools.common.utils;

import com.github.istin.dmtools.common.model.ToText;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

public class StringUtils {

    // Pre-compiled field blacklist for performance optimization
    private static final Set<String> FIELD_BLACKLIST = new HashSet<>();
    static {
        // Initialize blacklist once to avoid repeated string operations
        FIELD_BLACKLIST.add("id");
        FIELD_BLACKLIST.add("url");
        FIELD_BLACKLIST.add("self");
        FIELD_BLACKLIST.add("accounttype");
        FIELD_BLACKLIST.add("statuscategory");
        FIELD_BLACKLIST.add("subtask");
        FIELD_BLACKLIST.add("timezone");
        FIELD_BLACKLIST.add("emailaddress");
        FIELD_BLACKLIST.add("mimetype");
        FIELD_BLACKLIST.add("expand");
        FIELD_BLACKLIST.add("created");
        FIELD_BLACKLIST.add("updated");
        FIELD_BLACKLIST.add("aggregatetimeestimate");
        FIELD_BLACKLIST.add("aggregatetimespent");
        FIELD_BLACKLIST.add("size");
        FIELD_BLACKLIST.add("hierarchylevel");
    }

    /**
     * Determines if a field should be blacklisted based on predefined criteria.
     */
    private static boolean isFieldBlacklisted(String field, boolean ignoreDescription) {
        if (field == null || field.isEmpty()) {
            return true;
        }

        // Exact match blacklist check
        if (FIELD_BLACKLIST.contains(field.toLowerCase())) {
            return true;
        }

        // Check for URL patterns
        String lowerField = field.toLowerCase();
        if (lowerField.contains("url") || lowerField.contains("uri")) {
            return true;
        }

        // ID field patterns
        if (lowerField.endsWith("id") && !lowerField.equals("id")) {
            return true;
        }

        // Conditional description filtering
        if (ignoreDescription && "description".equalsIgnoreCase(field)) {
            return true;
        }

        return false;
    }

    /**
     * JSON transform method that takes a JSONObject and converts it to structured text
     */
    public static StringBuilder transformJSONToText(StringBuilder textBuilder, JSONObject fields, boolean ignoreDescription) {
        if (fields == null || fields.length() == 0) {
            return textBuilder;
        }

        for (String key : fields.keySet()) {
            if (isFieldBlacklisted(key, ignoreDescription)) {
                continue;
            }

            Object value = fields.opt(key);
            if (value == null) {
                continue;
            }

            if (value instanceof JSONObject) {
                processJSONObject(textBuilder, key, (JSONObject) value);
            } else if (value instanceof JSONArray) {
                processJSONArray(textBuilder, key, (JSONArray) value);
            } else {
                String stringValue = value.toString().trim();
                if (!stringValue.isEmpty()) {
                    textBuilder.append(key).append(": ").append(stringValue).append("\n");
                }
            }
        }

        return textBuilder;
    }

    /**
     * Transform JSON string to text using YAML-style grouping and markdown tables for arrays
     * Eliminates prefix redundancy and supports multiline strings with TextStart/TextEnd markers
     */
    public static StringBuilder transformJSONToText(StringBuilder textBuilder, String jsonString, boolean ignoreDescription) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return textBuilder;
        }
        
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(jsonString);
            
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                if (array.size() > 0 && array.get(0).isJsonObject()) {
                    // Array of objects - use markdown table to reduce key duplication
                    return transformArrayToMarkdownTable(textBuilder, array, ignoreDescription);
                }
            }
            
            return transformJsonElementToYamlStyle(textBuilder, element, ignoreDescription, "");
        } catch (Exception e) {
            return textBuilder.append("Invalid JSON\n");
        }
    }

    /**
     * Optimized JSON object processing with reduced string operations
     */
    private static void processJSONObject(StringBuilder textBuilder, String field, JSONObject jsonObject) {
        textBuilder.append(field).append(": { \n");
        transformJSONToText(textBuilder, jsonObject, true);
        textBuilder.append("} \n");
    }

    /**
     * Optimized JSON array processing with reduced string operations
     */
    private static void processJSONArray(StringBuilder textBuilder, String field, JSONArray jsonArray) {
        textBuilder.append(field).append(": [");
        
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            Object arrayElement = jsonArray.opt(i);
            
            if (arrayElement instanceof JSONObject) {
                textBuilder.append('{');
                transformJSONToText(textBuilder, (JSONObject) arrayElement, true);
                textBuilder.append('}');
            } else if (arrayElement != null) {
                textBuilder.append(arrayElement);
            }
            
            // Avoid unnecessary comparison on last element
            if (i < length - 1) {
                textBuilder.append(", ");
            }
        }
        textBuilder.append("]\n");
    }

    /**
     * Checks if the input string matches the expected Confluence YAML format.
     * @param input The input string to check
     * @return true if the input matches the expected format, false otherwise
     */
    public static boolean isConfluenceYamlFormat(String input) {
        // Check if the content has the expected structure
        if (!input.startsWith("<ac:structured-macro")) {
            return false;
        }

        // Check if it's a YAML content by looking for the language parameter
        Pattern languagePattern = Pattern.compile("<ac:parameter ac:name=\"language\">(.*?)</ac:parameter>", Pattern.DOTALL);
        Matcher languageMatcher = languagePattern.matcher(input);

        if (!languageMatcher.find() || !languageMatcher.group(1).equalsIgnoreCase("yaml")) {
            return false;
        }

        // Check if it has CDATA content
        Pattern pattern = Pattern.compile("<ac:plain-text-body><!\\[CDATA\\[(.*?)\\]\\]></ac:plain-text-body>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        return matcher.find();
    }

    /**
     * Extracts YAML content from the input string if it has the expected structure.
     * @param input The input string that may contain YAML content in a specific structure
     * @return The extracted YAML content if found, otherwise the original input
     */
    public static String extractYamlContentFromConfluence(String input) {
        // First check if the input matches the expected format
        if (!isConfluenceYamlFormat(input)) {
            return input;
        }

        // Extract content between CDATA tags
        Pattern pattern = Pattern.compile("<ac:plain-text-body><!\\[CDATA\\[(.*?)\\]\\]></ac:plain-text-body>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        // We already checked that the pattern matches in isConfluenceYamlFormat,
        // so we can safely assume matcher.find() will return true
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            // This should never happen if isConfluenceYamlFormat returned true
            return input;
        }
    }

    /**
     * Removes URLs from text
     */
    public static String removeUrls(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Regex pattern to match URLs
        String urlPattern = "\\b(https?|ftp)://\\S+|\\bwww\\.\\S+";
        return input.replaceAll(urlPattern, "");
    }

    /**
     * Cleans and formats text for markdown tables
     */
    public static String cleanTextForMarkdown(String text) {
        if (text == null) {
            return "";
        }

        // Remove HTML tags, normalize whitespace, escape pipes
        return text.replaceAll("<[^>]*>", "")
                  .replaceAll("\\s+", " ")
                  .replace("|", "\\|")
                  .trim();
    }

    /**
     * Escape markdown special characters in field names and values
     */
    private static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        
        // Escape markdown special characters and limit length for readability
        String escaped = text.replace("|", "\\|")
                            .replace("\n", " ")
                            .replace("\r", " ")
                            .replace("\t", " ");
        
        // Limit length to keep table readable (LLMs can handle long content but tables look better compact)
        if (escaped.length() > 200) {
            escaped = escaped.substring(0, 197) + "...";
        }
        
        return escaped;
    }

    /**
     * Clean up field names for better readability in outputs
     */
    public static String cleanFieldName(String fieldName) {
        if (fieldName == null) {
            return "";
        }

        // Transform camelCase to readable format
        String cleaned = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
        
        // Capitalize first letter
        if (!cleaned.isEmpty()) {
            cleaned = cleaned.substring(0, 1).toUpperCase() + 
                     (cleaned.length() > 1 ? cleaned.substring(1).toLowerCase() : "");
        }
        
        return cleaned;
    }

    public static String convertToText(@Nullable Object obj) {
        if (obj == null) {
            return "";
        }
        
        if (obj instanceof ToText) {
            try {
                return ((ToText) obj).toText();
            } catch (Exception e) {
                return "Error converting to text: " + e.getMessage();
            }
        }
        
        return obj.toString();
    }

    public static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split("\\r?\\n");
    }

    /**
     * Concatenate strings with a delimiter
     */
    public static String concatenate(String delimiter, String[] strings) {
        if (strings == null || strings.length == 0) {
            return "";
        }
        return String.join(delimiter, strings);
    }

    /**
     * Concatenate collection with a delimiter
     */
    public static String concatenate(String delimiter, java.util.Collection<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return "";
        }
        return String.join(delimiter, strings);
    }

    /**
     * Convert text to markdown format (placeholder implementation)
     */
    public static String convertToMarkdown(String text) {
        if (text == null) {
            return "";
        }
        // Simple markdown conversion - can be enhanced later
        return text.replace("\n", "  \n"); // Markdown line break
    }

    /**
     * Extract URLs from text
     */
    public static List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return urls;
        }
        
        String urlPattern = "\\b(https?|ftp)://\\S+|\\bwww\\.\\S+";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        
        return urls;
    }

    /**
     * Sort comparison for two strings
     */
    public static Integer sortByTwoStrings(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return 0;
        }
        if (str1 == null) {
            return 1;
        }
        if (str2 == null) {
            return -1;
        }
        return str1.compareTo(str2);
    }

    /**
     * Helper method to transform JsonElement to key-value format
     */
    private static StringBuilder transformJsonElementToKeyValue(StringBuilder textBuilder, JsonElement element, boolean ignoreDescription, String prefix) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : obj.keySet()) {
                if (isFieldBlacklisted(key, ignoreDescription)) {
                    continue;
                }
                
                JsonElement value = obj.get(key);
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                
                if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        String str = primitive.getAsString();
                        if (!str.trim().isEmpty()) {
                            textBuilder.append(fullKey).append(": ").append(str).append("\n");
                        }
                    } else {
                        textBuilder.append(fullKey).append(": ").append(primitive.getAsString()).append("\n");
                    }
                } else if (value.isJsonArray()) {
                    JsonArray array = value.getAsJsonArray();
                    if (array.size() > 0) {
                        textBuilder.append(fullKey).append(": [");
                        for (int i = 0; i < Math.min(array.size(), 5); i++) { // Limit array items
                            if (i > 0) textBuilder.append(", ");
                            JsonElement item = array.get(i);
                            if (item.isJsonPrimitive()) {
                                textBuilder.append(item.getAsString());
                            } else {
                                textBuilder.append("...");
                            }
                        }
                        if (array.size() > 5) {
                            textBuilder.append(" and ").append(array.size() - 5).append(" more");
                        }
                        textBuilder.append("]\n");
                    }
                } else if (value.isJsonObject() && !key.equals("fields")) {
                    // Limited recursion for nested objects
                    transformJsonElementToKeyValue(textBuilder, value, ignoreDescription, fullKey);
                }
            }
        }
        
        return textBuilder;
    }
    
    /**
     * Context class to collect nested sections during transformation
     */
    private static class TransformContext {
        private final List<NestedSection> nestedSections = new ArrayList<>();
        
        void addNestedSection(String parentPath, String arrayName, JsonArray array, boolean ignoreDescription) {
            nestedSections.add(new NestedSection(parentPath, arrayName, array, ignoreDescription));
        }
        
        void appendNestedSections(StringBuilder textBuilder) {
            for (NestedSection section : nestedSections) {
                section.appendTo(textBuilder);
            }
        }
    }
    
    /**
     * Represents a nested section that will be output separately
     */
    private static class NestedSection {
        final String parentPath;
        final String arrayName; 
        final JsonArray array;
        final boolean ignoreDescription;
        
        NestedSection(String parentPath, String arrayName, JsonArray array, boolean ignoreDescription) {
            this.parentPath = parentPath;
            this.arrayName = arrayName;
            this.array = array;
            this.ignoreDescription = ignoreDescription;
        }
        
        void appendTo(StringBuilder textBuilder) {
            textBuilder.append("\n").append(getSectionTitle()).append(":\n");
            transformArrayToMarkdownTable(textBuilder, array, ignoreDescription);
        }
        
        String getSectionTitle() {
            return parentPath.isEmpty() ? arrayName : parentPath + "_" + arrayName;
        }
    }

    /**
     * Transform JsonElement to YAML-style format with grouping to reduce redundancy
     * Now preserves ALL nested information in separate sections
     */
    private static StringBuilder transformJsonElementToYamlStyle(StringBuilder textBuilder, JsonElement element, boolean ignoreDescription, String indent) {
        TransformContext context = new TransformContext();
        transformJsonElementToYamlStyleWithContext(textBuilder, element, ignoreDescription, indent, "", context);
        context.appendNestedSections(textBuilder);
        return textBuilder;
    }
    
    /**
     * Internal method that collects nested sections while transforming
     */
    private static StringBuilder transformJsonElementToYamlStyleWithContext(StringBuilder textBuilder, JsonElement element, boolean ignoreDescription, String indent, String currentPath, TransformContext context) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            
            // Group fields by their structure - simple vs complex
            Map<String, JsonElement> simpleFields = new LinkedHashMap<>();
            Map<String, JsonElement> complexFields = new LinkedHashMap<>();
            
            for (String key : obj.keySet()) {
                if (isFieldBlacklisted(key, ignoreDescription)) {
                    continue;
                }
                
                JsonElement value = obj.get(key);
                if (value.isJsonObject()) {
                    complexFields.put(key, value);
                } else {
                    simpleFields.put(key, value);
                }
            }
            
            // Output simple fields first (key-value pairs)
            for (Map.Entry<String, JsonElement> entry : simpleFields.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                String fieldPath = currentPath.isEmpty() ? key : currentPath + "_" + key;
                
                textBuilder.append(indent).append(key).append(": ");
                
                if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    String val = primitive.getAsString();
                    if (!val.trim().isEmpty()) {
                        if (val.contains("\n") || val.contains("\r")) {
                            // Multiline string - wrap with meta tags
                            textBuilder.append("TextStart\n").append(val).append("\nTextEnd");
                        } else {
                            textBuilder.append(val);
                        }
                    }
                } else if (value.isJsonArray()) {
                    JsonArray array = value.getAsJsonArray();
                    if (array.size() > 0) {
                        // Check if this is an array of objects
                        if (array.get(0).isJsonObject()) {
                            // For nested arrays of objects, show summary table + save for detailed section
                            if (currentPath.isEmpty()) {
                                // Top-level array - show full table with context for nested sections
                                textBuilder.append("\n");
                                transformArrayToMarkdownTable(textBuilder, array, ignoreDescription, key, context);
                            } else {
                                // Nested array - show summary and save detailed info
                                textBuilder.append(createArraySummary(array));
                                context.addNestedSection(currentPath, key, array, ignoreDescription);
                            }
                        } else {
                            // Simple array - show as list
                            textBuilder.append("[");
                            for (int i = 0; i < Math.min(array.size(), 3); i++) {
                                if (i > 0) textBuilder.append(", ");
                                JsonElement item = array.get(i);
                                if (item.isJsonPrimitive()) {
                                    textBuilder.append(item.getAsString());
                                } else {
                                    textBuilder.append("...");
                                }
                            }
                            if (array.size() > 3) {
                                textBuilder.append(" and ").append(array.size() - 3).append(" more");
                            }
                            textBuilder.append("]");
                        }
                    }
                }
                textBuilder.append("\n");
            }
            
            // Output complex fields with grouping
            for (Map.Entry<String, JsonElement> entry : complexFields.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                String fieldPath = currentPath.isEmpty() ? key : currentPath + "_" + key;
                
                if (value.isJsonObject()) {
                    JsonObject nestedObj = value.getAsJsonObject();
                    if (nestedObj.size() > 0) {
                        textBuilder.append(indent).append(key).append(":\n");
                        transformJsonElementToYamlStyleWithContext(textBuilder, value, ignoreDescription, indent + "  ", fieldPath, context);
                    }
                }
            }
        }
        
        return textBuilder;
    }
    
    /**
     * Transform JSON array to markdown table format
     */
    private static StringBuilder transformArrayToMarkdownTable(StringBuilder textBuilder, JsonArray array, boolean ignoreDescription) {
        return transformArrayToMarkdownTable(textBuilder, array, ignoreDescription, "", new TransformContext());
    }

    /**
     * Transform JSON array to markdown table format with context for nested information
     */
    private static StringBuilder transformArrayToMarkdownTable(StringBuilder textBuilder, JsonArray array, boolean ignoreDescription, String parentPath, TransformContext context) {
        if (array.size() == 0) {
            return textBuilder;
        }
        
        // Get all unique keys from objects
        Set<String> allKeys = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).isJsonObject()) {
                JsonObject obj = array.get(i).getAsJsonObject();
                for (String key : obj.keySet()) {
                    if (!isFieldBlacklisted(key, ignoreDescription)) {
                        allKeys.add(key);
                    }
                }
            }
        }
        
        if (allKeys.isEmpty()) {
            return textBuilder;
        }
        
        List<String> keyList = new ArrayList<>(allKeys);
        
        // Create table header
        textBuilder.append("|");
        for (String key : keyList) {
            textBuilder.append(" ").append(escapeMarkdown(key)).append(" |");
        }
        textBuilder.append("\n");
        
        // No separator lines needed for cleaner output
        
        // Create table rows
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).isJsonObject()) {
                JsonObject obj = array.get(i).getAsJsonObject();
                textBuilder.append("|");
                
                for (String key : keyList) {
                    textBuilder.append(" ");
                    if (obj.has(key)) {
                        JsonElement value = obj.get(key);
                        if (value.isJsonPrimitive()) {
                            String val = value.getAsString();
                            
                            // Handle multiline strings in table cells
                            if (val.contains("\n") || val.contains("\r")) {
                                val = "[TextStart...TextEnd]";
                            } else if (val.length() > 80) {
                                val = val.substring(0, 77) + "...";
                            }
                            
                            textBuilder.append(escapeMarkdown(val));
                        } else if (value.isJsonArray()) {
                            JsonArray nestedArray = value.getAsJsonArray();
                            if (nestedArray.size() > 0 && nestedArray.get(0).isJsonObject()) {
                                // Array of objects - show summary and save for detailed section
                                String arrayName = getArrayItemName(obj, i);
                                String sectionPath = parentPath.isEmpty() ? arrayName : parentPath + "_" + arrayName;
                                textBuilder.append(escapeMarkdown(createArraySummary(nestedArray)));
                                context.addNestedSection(sectionPath, key, nestedArray, ignoreDescription);
                            } else {
                                // Simple array - show as compact list
                                textBuilder.append(escapeMarkdown(createSimpleArraySummary(nestedArray)));
                            }
                        } else {
                            textBuilder.append("...");
                        }
                    } else {
                        textBuilder.append("-");
                    }
                    textBuilder.append(" |");
                }
                textBuilder.append("\n");
            }
        }
        
        return textBuilder;
    }
    
    /**
     * Create a brief summary of an array for display in main structure
     */
    private static String createArraySummary(JsonArray array) {
        if (array.size() == 0) {
            return "[]";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("[").append(array.size()).append(" items");
        
        // Try to show first item's key info if it's an object
        if (array.get(0).isJsonObject()) {
            JsonObject firstItem = array.get(0).getAsJsonObject();
            
            // Look for a name or title field to show
            String firstItemName = null;
            for (String key : firstItem.keySet()) {
                if (key.toLowerCase().contains("name") || key.toLowerCase().contains("title")) {
                    JsonElement nameElement = firstItem.get(key);
                    if (nameElement.isJsonPrimitive()) {
                        firstItemName = nameElement.getAsString();
                        break;
                    }
                }
            }
            
            if (firstItemName != null) {
                summary.append(": ").append(firstItemName);
                if (array.size() > 1) {
                    summary.append(", ...");
                }
            }
        }
        
        summary.append("]");
        return summary.toString();
    }
    
    /**
     * Get name/identifier of an array item for section naming
     */
    private static String getArrayItemName(JsonObject obj, int index) {
        // Try to find a name field
        for (String key : obj.keySet()) {
            if (key.toLowerCase().contains("name") || key.toLowerCase().contains("title")) {
                JsonElement nameElement = obj.get(key);
                if (nameElement.isJsonPrimitive()) {
                    return nameElement.getAsString().replaceAll("[^a-zA-Z0-9]", "_");
                }
            }
        }
        return "item_" + index;
    }
    
    /**
     * Create summary for simple arrays (non-object arrays)
     */
    private static String createSimpleArraySummary(JsonArray array) {
        if (array.size() == 0) {
            return "[]";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("[");
        
        for (int i = 0; i < Math.min(array.size(), 3); i++) {
            if (i > 0) summary.append(", ");
            JsonElement item = array.get(i);
            if (item.isJsonPrimitive()) {
                String val = item.getAsString();
                if (val.length() > 15) {
                    val = val.substring(0, 12) + "...";
                }
                summary.append(val);
            } else {
                summary.append("...");
            }
        }
        
        if (array.size() > 3) {
            summary.append(" +").append(array.size() - 3);
        }
        
        summary.append("]");
        return summary.toString();
    }
}
