package com.github.istin.dmtools.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    
    /**
     * Formatting modes for the output
     */
    public enum FormattingMode {
        MINIMIZED,  // Compact format without indentation (default)
        PRETTY      // Formatted with tab indentation
    }
    
    private final JsonElement rootElement;
    private final FormattingMode formattingMode;
    private final Gson gson = new Gson();
    
    /**
     * Create from JSONObject (org.json) with default MINIMIZED mode
     */
    public LLMOptimizedJson(JSONObject jsonObject) {
        this(jsonObject, FormattingMode.MINIMIZED);
    }
    
    /**
     * Create from JSONObject (org.json) with specified formatting mode
     */
    public LLMOptimizedJson(JSONObject jsonObject, FormattingMode mode) {
        this.rootElement = JsonParser.parseString(jsonObject.toString());
        this.formattingMode = mode;
    }
    
    /**
     * Create from JSON string with default MINIMIZED mode
     */
    public LLMOptimizedJson(String jsonString) {
        this(jsonString, FormattingMode.MINIMIZED);
    }
    
    /**
     * Create from JSON string with specified formatting mode
     */
    public LLMOptimizedJson(String jsonString, FormattingMode mode) {
        this.rootElement = JsonParser.parseString(jsonString);
        this.formattingMode = mode;
    }
    
    /**
     * Create from InputStream with default MINIMIZED mode
     */
    public LLMOptimizedJson(InputStream inputStream) {
        this(inputStream, FormattingMode.MINIMIZED);
    }
    
    /**
     * Create from InputStream with specified formatting mode
     */
    public LLMOptimizedJson(InputStream inputStream, FormattingMode mode) {
        this.rootElement = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.formattingMode = mode;
    }
    
    /**
     * Create from Gson JsonElement directly with default MINIMIZED mode
     */
    public LLMOptimizedJson(JsonElement jsonElement) {
        this(jsonElement, FormattingMode.MINIMIZED);
    }
    
    /**
     * Create from Gson JsonElement directly with specified formatting mode
     */
    public LLMOptimizedJson(JsonElement jsonElement, FormattingMode mode) {
        this.rootElement = jsonElement;
        this.formattingMode = mode;
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
        String indent = getIndent(indentLevel);
        
        if (element.isJsonObject()) {
            formatJsonObject(result, element.getAsJsonObject(), parentKey, keyPrefix, indentLevel);
        } else if (element.isJsonArray()) {
            formatJsonArray(result, element.getAsJsonArray(), parentKey, keyPrefix, indentLevel);
        } else if (element.isJsonPrimitive()) {
            formatPrimitive(result, element.getAsJsonPrimitive(), indent);
        }
    }
    
    /**
     * Get indent string based on formatting mode
     */
    private String getIndent(int indentLevel) {
        if (formattingMode == FormattingMode.PRETTY) {
            return "\t".repeat(indentLevel);
        } else {
            return ""; // MINIMIZED mode - no indentation
        }
    }
    
    /**
     * Format a JSON object with Next header
     */
    private void formatJsonObject(StringBuilder result, JsonObject jsonObject, String parentKey, String keyPrefix, int indentLevel) {
        String indent = getIndent(indentLevel);
        Set<String> keys = jsonObject.keySet();
        
        // Add Next header (no colon)
        result.append(indent).append("Next ");
        List<String> keyList = new ArrayList<>(keys);
        for (int i = 0; i < keyList.size(); i++) {
            result.append(keyList.get(i));
            if (i < keyList.size() - 1) {
                result.append(", ");
            }
        }
        result.append("\n");
        
        // Add values for each key
        for (String key : keyList) {
            JsonElement value = jsonObject.get(key);
            
            if (value.isJsonPrimitive()) {
                result.append(indent);
                formatPrimitive(result, value.getAsJsonPrimitive(), "");
                result.append("\n");
            } else if (value.isJsonArray()) {
                formatJsonArray(result, value.getAsJsonArray(), key, keyPrefix + key, indentLevel);
            } else if (value.isJsonObject()) {
                // For nested objects, concatenate keys
                String concatenatedKey = keyPrefix.isEmpty() ? key : keyPrefix + capitalize(key);
                formatJsonObject(result, value.getAsJsonObject(), key, concatenatedKey, indentLevel);
            }
        }
    }
    
    /**
     * Format a JSON array with simplified bracket notation
     */
    private void formatJsonArray(StringBuilder result, JsonArray jsonArray, String parentKey, String keyPrefix, int indentLevel) {
        String indent = getIndent(indentLevel);
        
        if (jsonArray.size() == 0) {
            result.append(indent).append("[ ]\n");
            return;
        }
        
        // Check if this is an array of primitives
        boolean allPrimitives = true;
        for (int i = 0; i < jsonArray.size(); i++) {
            if (!jsonArray.get(i).isJsonPrimitive()) {
                allPrimitives = false;
                break;
            }
        }
        
        if (allPrimitives) {
            // For arrays of primitives - use [ ] brackets with elements on separate lines
            result.append(indent).append("[\n");
            for (int i = 0; i < jsonArray.size(); i++) {
                formatPrimitive(result, jsonArray.get(i).getAsJsonPrimitive(), indent);
                result.append("\n");
            }
            result.append(indent).append("]\n");
        } else {
            // For arrays of objects - use [ Next keys format
            // First, get all unique keys from all objects
            Set<String> allKeys = new HashSet<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                if (jsonArray.get(i).isJsonObject()) {
                    JsonObject obj = jsonArray.get(i).getAsJsonObject();
                    allKeys.addAll(obj.keySet());
                }
            }
            
            // Output array header with keys
            result.append(indent).append("[ Next ");
            List<String> keyList = new ArrayList<>(allKeys);
            for (int i = 0; i < keyList.size(); i++) {
                result.append(keyList.get(i));
                if (i < keyList.size() - 1) {
                    result.append(", ");
                }
            }
            result.append("\n");
            
            // Output each object's values without Next headers
            for (int i = 0; i < jsonArray.size(); i++) {
                result.append(indent).append(i).append("\n");
                
                if (jsonArray.get(i).isJsonObject()) {
                    JsonObject obj = jsonArray.get(i).getAsJsonObject();
                    String valueIndent = getIndent(indentLevel + 1);
                    
                    // Output values in the same order as keys
                    for (String key : keyList) {
                        if (obj.has(key)) {
                            JsonElement value = obj.get(key);
                            if (value.isJsonPrimitive()) {
                                formatPrimitive(result, value.getAsJsonPrimitive(), valueIndent);
                                result.append("\n");
                            } else if (value.isJsonArray()) {
                                formatJsonArray(result, value.getAsJsonArray(), key, keyPrefix + key, indentLevel + 2);
                            } else if (value.isJsonObject()) {
                                formatJsonObject(result, value.getAsJsonObject(), key, keyPrefix + capitalize(key), indentLevel + 2);
                            }
                        } else {
                            // Key not present in this object
                            result.append(valueIndent).append("null\n");
                        }
                    }
                }
            }
            result.append(indent).append("]\n");
        }
    }
    
    /**
     * Format a primitive value, handling multiline strings
     */
    private void formatPrimitive(StringBuilder result, JsonPrimitive primitive, String indent) {
        if (primitive.isString()) {
            String value = primitive.getAsString();
            if (value.contains("\n") || value.contains("\r")) {
                result.append(indent).append("_\n");
                result.append(indent).append(value).append("\n");
                result.append(indent).append("_");
            } else {
                result.append(indent).append(value);
            }
        } else {
            result.append(indent).append(primitive.getAsString());
        }
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
     * Get the root JsonElement for testing purposes
     */
    public JsonElement getRootElement() {
        return rootElement;
    }
    
    /**
     * Static factory method for quick conversion (MINIMIZED mode)
     */
    public static String format(String jsonString) {
        return new LLMOptimizedJson(jsonString, FormattingMode.MINIMIZED).toString();
    }
    
    /**
     * Static factory method for quick conversion with formatting mode
     */
    public static String format(String jsonString, FormattingMode mode) {
        return new LLMOptimizedJson(jsonString, mode).toString();
    }
    
    /**
     * Static factory method for JSONObject (MINIMIZED mode)
     */
    public static String format(JSONObject jsonObject) {
        return new LLMOptimizedJson(jsonObject, FormattingMode.MINIMIZED).toString();
    }
    
    /**
     * Static factory method for JSONObject with formatting mode
     */
    public static String format(JSONObject jsonObject, FormattingMode mode) {
        return new LLMOptimizedJson(jsonObject, mode).toString();
    }
    
    /**
     * Static factory method for InputStream (MINIMIZED mode)
     */
    public static String format(InputStream inputStream) {
        return new LLMOptimizedJson(inputStream, FormattingMode.MINIMIZED).toString();
    }
    
    /**
     * Static factory method for InputStream with formatting mode
     */
    public static String format(InputStream inputStream, FormattingMode mode) {
        return new LLMOptimizedJson(inputStream, mode).toString();
    }
}
