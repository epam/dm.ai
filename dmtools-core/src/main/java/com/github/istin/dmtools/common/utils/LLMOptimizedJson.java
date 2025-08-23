package com.github.istin.dmtools.common.utils;

import com.google.gson.*;
import lombok.Getter;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for optimizing JSON representation for LLM consumption.
 * Creates a compact, hierarchical format that reduces redundancy and improves readability.
 * 
 * Format:
 * - Next key1, key2, key3 at start of each object
 * - Values on separate lines
 * - _ _ for multiline strings
 * - Array indexing: 0, 1, 2 with optional tab indentation
 * - Key concatenation for nested objects: ParentKeyChildKey
 * 
 * Formatting modes:
 * - MINIMIZED (default): No indentation, compact format
 * - PRETTY: Tab indentation for hierarchy visualization
 */
public class LLMOptimizedJson {

    public static final String NEXT = "Next";
    public static final String SPACE = " ";
    public static final String LINE_BREAK = "\n";
    public static final String COMMA = ",";

    /**
     * Formatting modes for the output
     */
    public enum FormattingMode {
        MINIMIZED,  // Compact format without indentation (default)
        PRETTY      // Formatted with tab indentation
    }

    /**
     * -- GETTER --
     *  Get the root JsonElement for testing purposes
     */
    @Getter
    private final JsonElement rootElement;
    private final FormattingMode formattingMode;
    private final boolean wellFormed;
    private final Gson gson = new Gson();
    
    // Cache indent strings to avoid repeated string creation
    private final String[] indentCache = new String[10]; // Support up to 10 levels
    private final String emptyString = "";
    
    /**
     * Create from JSONObject (org.json) with default MINIMIZED mode
     */
    public LLMOptimizedJson(JSONObject jsonObject) {
        this(jsonObject, FormattingMode.MINIMIZED, false);
    }
    
    /**
     * Create from JSONObject (org.json) with specified formatting mode
     */
    public LLMOptimizedJson(JSONObject jsonObject, FormattingMode mode) {
        this(jsonObject, mode, false);
    }
    
    /**
     * Create from JSONObject (org.json) with specified formatting mode and well-formed optimization
     */
    public LLMOptimizedJson(JSONObject jsonObject, FormattingMode mode, boolean wellFormed) {
        this.rootElement = JsonParser.parseString(jsonObject.toString());
        this.formattingMode = mode;
        this.wellFormed = wellFormed;
    }
    
    /**
     * Create from JSON string with default MINIMIZED mode
     */
    public LLMOptimizedJson(String jsonString) {
        this(jsonString, FormattingMode.MINIMIZED, false);
    }
    
    /**
     * Create from JSON string with specified formatting mode
     */
    public LLMOptimizedJson(String jsonString, FormattingMode mode) {
        this(jsonString, mode, false);
    }
    
    /**
     * Create from JSON string with specified formatting mode and well-formed optimization
     */
    public LLMOptimizedJson(String jsonString, FormattingMode mode, boolean wellFormed) {
        this.rootElement = JsonParser.parseString(jsonString);
        this.formattingMode = mode;
        this.wellFormed = wellFormed;
    }
    
    /**
     * Create from InputStream with default MINIMIZED mode
     */
    public LLMOptimizedJson(InputStream inputStream) {
        this(inputStream, FormattingMode.MINIMIZED, false);
    }
    
    /**
     * Create from InputStream with specified formatting mode
     */
    public LLMOptimizedJson(InputStream inputStream, FormattingMode mode) {
        this(inputStream, mode, false);
    }
    
    /**
     * Create from InputStream with specified formatting mode and well-formed optimization
     */
    public LLMOptimizedJson(InputStream inputStream, FormattingMode mode, boolean wellFormed) {
        this.rootElement = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.formattingMode = mode;
        this.wellFormed = wellFormed;
    }
    
    /**
     * Create from Gson JsonElement directly with default MINIMIZED mode
     */
    public LLMOptimizedJson(JsonElement jsonElement) {
        this(jsonElement, FormattingMode.MINIMIZED, false);
    }
    
    /**
     * Create from Gson JsonElement directly with specified formatting mode
     */
    public LLMOptimizedJson(JsonElement jsonElement, FormattingMode mode) {
        this(jsonElement, mode, false);
    }
    
    /**
     * Create from Gson JsonElement directly with specified formatting mode and well-formed optimization
     */
    public LLMOptimizedJson(JsonElement jsonElement, FormattingMode mode, boolean wellFormed) {
        this.rootElement = jsonElement;
        this.formattingMode = mode;
        this.wellFormed = wellFormed;
    }
    
    /**
     * Convert to LLM-optimized string format
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        formatElement(result, rootElement, "", "", 0);
        return result.toString();
    }
    
    /**
     * Format a JsonElement with given context
     * 
     * @param result StringBuilder to append to
     * @param element JsonElement to format
     * @param parentKey Parent key for concatenation
     * @param keyPrefix Current key prefix for nested objects
     * @param indentLevel Current indentation level
     */
    private void formatElement(StringBuilder result, JsonElement element, String parentKey, String keyPrefix, int indentLevel) {
        if (wellFormed) {
            formatElementWellFormed(result, element, parentKey, keyPrefix, indentLevel);
        } else {
            formatElementRegular(result, element, parentKey, keyPrefix, indentLevel);
        }
    }
    
    /**
     * Fast formatting for well-formed JSON (optimized for performance)
     */
    private void formatElementWellFormed(StringBuilder result, JsonElement element, String parentKey, String keyPrefix, int indentLevel) {
        if (element.isJsonObject()) {
            formatJsonObjectWellFormed(result, element.getAsJsonObject(), parentKey, keyPrefix, indentLevel);
        } else if (element.isJsonArray()) {
            formatJsonArrayWellFormed(result, element.getAsJsonArray(), parentKey, keyPrefix, indentLevel);
        } else if (element.isJsonPrimitive()) {
            String indent = getIndent(indentLevel);
            formatPrimitive(result, element.getAsJsonPrimitive(), indent);
        }
    }
    
    /**
     * Regular formatting for any JSON structure (comprehensive but slower)
     */
    private void formatElementRegular(StringBuilder result, JsonElement element, String parentKey, String keyPrefix, int indentLevel) {
        if (element.isJsonObject()) {
            formatJsonObjectRegular(result, element.getAsJsonObject(), parentKey, keyPrefix, indentLevel);
        } else if (element.isJsonArray()) {
            formatJsonArrayRegular(result, element.getAsJsonArray(), parentKey, keyPrefix, indentLevel);
        } else if (element.isJsonPrimitive()) {
            String indent = getIndent(indentLevel);
            formatPrimitive(result, element.getAsJsonPrimitive(), indent);
        }
    }
    
    /**
     * Get indent string based on formatting mode (cached for performance)
     */
    private String getIndent(int indentLevel) {
        if (formattingMode == FormattingMode.MINIMIZED) {
            return emptyString;
        }
        
        // Cache indent strings for PRETTY mode
        if (indentLevel < indentCache.length) {
            if (indentCache[indentLevel] == null) {
                indentCache[indentLevel] = "\t".repeat(indentLevel);
            }
            return indentCache[indentLevel];
        }
        
        // Fallback for deep nesting
        return "\t".repeat(indentLevel);
    }
    
    /**
     * Fast formatting for well-formed JSON objects (optimized single-pass with entrySet)
     */
    private void formatJsonObjectWellFormed(StringBuilder result, JsonObject jsonObject, String parentKey, String keyPrefix, int indentLevel) {
        String indent = getIndent(indentLevel);
        Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();

        if (entries.isEmpty()) {
            result.append(indent).append(NEXT).append(SPACE).append(LINE_BREAK);
            return;
        }

        printObjectNextHeaderWellFormatted(result, indent, entries);

        // Process values (single pass, already have entries)
        for (Map.Entry<String, JsonElement> entry : entries) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            if (value.isJsonPrimitive()) {
                result.append(indent);
                formatPrimitive(result, value.getAsJsonPrimitive(), emptyString);
                result.append(LINE_BREAK);
            } else if (value.isJsonArray()) {
                formatJsonArrayWellFormed(result, value.getAsJsonArray(), key, keyPrefix + key, indentLevel);
            } else if (value.isJsonObject()) {
                String concatenatedKey = keyPrefix.isEmpty() ? key : keyPrefix + capitalize(key);
                formatJsonObjectWellFormed(result, value.getAsJsonObject(), key, concatenatedKey, indentLevel);
            }
        }
    }

    /**
     * Regular formatting for any JSON objects (uses ArrayList for consistency)
     */
    private void formatJsonObjectRegular(StringBuilder result, JsonObject jsonObject, String parentKey, String keyPrefix, int indentLevel) {
        String indent = getIndent(indentLevel);
        Set<String> keys = jsonObject.keySet();
        
        // Add Next header (no colon) - using helper method
        printObjectNextHeader(result, indent, keys);
        
        // Add values for each key
        for (String key : keys) {
            JsonElement value = jsonObject.get(key);
            
            if (value.isJsonPrimitive()) {
                result.append(indent);
                formatPrimitive(result, value.getAsJsonPrimitive(), emptyString);
                result.append(LINE_BREAK);
            } else if (value.isJsonArray()) {
                formatJsonArrayRegular(result, value.getAsJsonArray(), key, keyPrefix + key, indentLevel);
            } else if (value.isJsonObject()) {
                String concatenatedKey = keyPrefix.isEmpty() ? key : keyPrefix + capitalize(key);
                formatJsonObjectRegular(result, value.getAsJsonObject(), key, concatenatedKey, indentLevel);
            }
        }
    }
    
    /**
     * Fast formatting for well-formed JSON arrays (optimized, no ArrayList/HashSet creation)
     */
    private void formatJsonArrayWellFormed(StringBuilder result, JsonArray jsonArray, String parentKey, String keyPrefix, int indentLevel) {
        String indent = getIndent(indentLevel);
        
        if (jsonArray.isEmpty()) {
            result.append(indent).append("[ ]").append(LINE_BREAK);
            return;
        }
        
        // For well-formed JSON, just check first element to determine type
        JsonElement firstJsonElement = jsonArray.get(0);
        boolean allPrimitives = firstJsonElement.isJsonPrimitive();

        int jsonArraySize = jsonArray.size();
        if (allPrimitives) {
            // For arrays of primitives - use [ ] brackets
            result.append(indent).append("[").append(LINE_BREAK);
            for (int i = 0; i < jsonArraySize; i++) {
                formatPrimitive(result, jsonArray.get(i).getAsJsonPrimitive(), indent);
                result.append(LINE_BREAK);
            }
            result.append(indent).append("]").append(LINE_BREAK);
        } else {
            // For arrays of objects - use first object's keys directly (no ArrayList)
            if (firstJsonElement.isJsonObject()) {
                JsonObject firstObj = firstJsonElement.getAsJsonObject();
                Set<String> keys = firstObj.keySet();
                
                // Output array header with keys directly from keySet
                result.append("[");
                printObjectNextHeader(result, indent, keys);
                
                // Output each object's values - one pass, no intermediate collections
                String valueIndent = getIndent(indentLevel + 1);
                for (int i = 0; i < jsonArraySize; i++) {
                    result.append(indent).append(i).append(LINE_BREAK);
                    
                    JsonObject obj = jsonArray.get(i).getAsJsonObject();

                    // Output values in the same order as first object's keys
                    for (String key : keys) {
                        if (obj.has(key)) {
                            JsonElement value = obj.get(key);
                            if (value.isJsonPrimitive()) {
                                formatPrimitive(result, value.getAsJsonPrimitive(), valueIndent);
                                result.append(LINE_BREAK);
                            } else if (value.isJsonArray()) {
                                formatJsonArrayWellFormed(result, value.getAsJsonArray(), key, keyPrefix + key, indentLevel + 2);
                            } else if (value.isJsonObject()) {
                                formatJsonObjectWellFormed(result, value.getAsJsonObject(), key, keyPrefix + capitalize(key), indentLevel + 2);
                            }
                        } else {
                            result.append(valueIndent).append("null").append(LINE_BREAK);
                        }
                    }
                }
            }
            result.append(indent).append("]").append(LINE_BREAK);
        }
    }
    
    /**
     * Regular formatting for any JSON arrays (comprehensive but slower)
     */
    private void formatJsonArrayRegular(StringBuilder result, JsonArray jsonArray, String parentKey, String keyPrefix, int indentLevel) {
        String indent = getIndent(indentLevel);
        
        if (jsonArray.isEmpty()) {
            result.append(indent).append("[ ]").append(LINE_BREAK);
            return;
        }
        
        // Check if this is an array of primitives (scan all elements)
        boolean allPrimitives = true;
        for (int i = 0; i < jsonArray.size(); i++) {
            if (!jsonArray.get(i).isJsonPrimitive()) {
                allPrimitives = false;
                break;
            }
        }
        
        if (allPrimitives) {
            // For arrays of primitives - use [ ] brackets
            result.append(indent).append("[").append(LINE_BREAK);
            for (int i = 0; i < jsonArray.size(); i++) {
                formatPrimitive(result, jsonArray.get(i).getAsJsonPrimitive(), indent);
                result.append(LINE_BREAK);
            }
            result.append(indent).append("]").append(LINE_BREAK);
        } else {
            // Collect all unique keys from all objects
            Set<String> allKeys = new HashSet<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                if (jsonArray.get(i).isJsonObject()) {
                    JsonObject obj = jsonArray.get(i).getAsJsonObject();
                    allKeys.addAll(obj.keySet());
                }
            }

            result.append("[");
            // Output array header with keys
            printObjectNextHeader(result, indent, allKeys);
            
            // Output each object's values
            String valueIndent = getIndent(indentLevel + 1);
            for (int i = 0; i < jsonArray.size(); i++) {
                result.append(indent).append(i).append(LINE_BREAK);
                
                if (jsonArray.get(i).isJsonObject()) {
                    JsonObject obj = jsonArray.get(i).getAsJsonObject();
                    
                    // Output values in the same order as keys
                    for (String key : allKeys) {
                        if (obj.has(key)) {
                            JsonElement value = obj.get(key);
                            if (value.isJsonPrimitive()) {
                                formatPrimitive(result, value.getAsJsonPrimitive(), valueIndent);
                                result.append(LINE_BREAK);
                            } else if (value.isJsonArray()) {
                                formatJsonArrayRegular(result, value.getAsJsonArray(), key, keyPrefix + key, indentLevel + 2);
                            } else if (value.isJsonObject()) {
                                formatJsonObjectRegular(result, value.getAsJsonObject(), key, keyPrefix + capitalize(key), indentLevel + 2);
                            }
                        } else {
                            result.append(valueIndent).append("null").append(LINE_BREAK);
                        }
                    }
                }
            }
            result.append(indent).append("]").append(LINE_BREAK);
        }
    }

    /**
     * Format a primitive value, handling multiline strings
     */
    private void formatPrimitive(StringBuilder result, JsonPrimitive primitive, String indent) {
        if (primitive.isString()) {
            String value = primitive.getAsString();
            if (value.contains(LINE_BREAK) || value.contains("\r")) {
                result.append(indent).append("_").append(LINE_BREAK);
                result.append(indent).append(value).append(LINE_BREAK);
                result.append(indent).append("_");
            } else {
                result.append(indent).append(value);
            }
        } else {
            result.append(indent).append(primitive.getAsString());
        }
    }

    private static void printObjectNextHeaderWellFormatted(StringBuilder result, String indent, Set<Map.Entry<String, JsonElement>> entries) {
        // Print Next header with keys (no intermediate collections)
        result.append(indent).append(NEXT).append(SPACE);
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : entries) {
            if (!first) {
                result.append(COMMA);
            }
            result.append(entry.getKey());
            first = false;
        }
        result.append(LINE_BREAK);
    }

    /**
     * Print Next header for object keys (compact format without spaces after commas)
     */
    private void printObjectNextHeader(StringBuilder result, String indent, Set<String> keys) {
        result.append(indent).append(NEXT).append(SPACE);
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                result.append(COMMA);
            }
            result.append(key);
            first = false;
        }
        result.append(LINE_BREAK);
    }


    /**
     * Capitalize first letter of a string for key concatenation
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Static factory method for quick conversion (MINIMIZED mode)
     */
    public static String format(String jsonString) {
        return new LLMOptimizedJson(jsonString, FormattingMode.MINIMIZED, false).toString();
    }
    
    /**
     * Static factory method for quick conversion with formatting mode
     */
    public static String format(String jsonString, FormattingMode mode) {
        return new LLMOptimizedJson(jsonString, mode, false).toString();
    }
    
    /**
     * Static factory method with well-formed optimization for performance
     */
    public static String format(String jsonString, FormattingMode mode, boolean wellFormed) {
        return new LLMOptimizedJson(jsonString, mode, wellFormed).toString();
    }
    
    /**
     * Static factory method for well-formed JSON with MINIMIZED mode (fastest)
     */
    public static String formatWellFormed(String jsonString) {
        return new LLMOptimizedJson(jsonString, FormattingMode.MINIMIZED, true).toString();
    }
    
    /**
     * Static factory method for JSONObject (MINIMIZED mode)
     */
    public static String format(JSONObject jsonObject) {
        return new LLMOptimizedJson(jsonObject, FormattingMode.MINIMIZED, false).toString();
    }
    
    /**
     * Static factory method for JSONObject with formatting mode
     */
    public static String format(JSONObject jsonObject, FormattingMode mode) {
        return new LLMOptimizedJson(jsonObject, mode, false).toString();
    }
    
    /**
     * Static factory method for JSONObject with well-formed optimization
     */
    public static String format(JSONObject jsonObject, FormattingMode mode, boolean wellFormed) {
        return new LLMOptimizedJson(jsonObject, mode, wellFormed).toString();
    }
    
    /**
     * Static factory method for InputStream (MINIMIZED mode)
     */
    public static String format(InputStream inputStream) {
        return new LLMOptimizedJson(inputStream, FormattingMode.MINIMIZED, false).toString();
    }
    
    /**
     * Static factory method for InputStream with formatting mode
     */
    public static String format(InputStream inputStream, FormattingMode mode) {
        return new LLMOptimizedJson(inputStream, mode, false).toString();
    }
    
    /**
     * Static factory method for InputStream with well-formed optimization
     */
    public static String format(InputStream inputStream, FormattingMode mode, boolean wellFormed) {
        return new LLMOptimizedJson(inputStream, mode, wellFormed).toString();
    }
}
