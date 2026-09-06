// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Helper class for merging JSON configurations.
 * Implements deep merge strategy where encoded configuration takes precedence over file configuration.
 */
public class ConfigurationMerger {

    private static final Logger logger = LogManager.getLogger(ConfigurationMerger.class);

    /**
     * Merges JSON configuration from file with encoded JSON parameters.
     * The encoded JSON values override corresponding keys from file configuration using deep merge.
     *
     * @param fileJson The JSON string loaded from file
     * @param encodedJson The decoded JSON string from encoded parameter
     * @return The merged JSON string
     * @throws IllegalArgumentException if JSON parsing fails
     */
    public String mergeConfigurations(String fileJson, String encodedJson) {
        if (fileJson == null || fileJson.trim().isEmpty()) {
            throw new IllegalArgumentException("File JSON cannot be null or empty");
        }

        JSONObject baseConfig;
        try {
            baseConfig = new JSONObject(fileJson);
        } catch (JSONException e) {
            logger.error("JSON parsing failed for file configuration: {}. Invalid JSON: {}", e.getMessage(), fileJson);
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage() + ". File JSON content: " + fileJson, e);
        }

        // If no encoded JSON provided, return file configuration as-is
        if (encodedJson == null || encodedJson.trim().isEmpty()) {
            logger.info("No encoded parameter provided, using file configuration only");
            return baseConfig.toString();
        }

        JSONObject overrideConfig;
        try {
            overrideConfig = new JSONObject(encodedJson);
        } catch (JSONException e) {
            logger.error("JSON parsing failed for encoded configuration: {}. Invalid JSON: {}", e.getMessage(), encodedJson);
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage() + ". Encoded JSON content: " + encodedJson, e);
        }

        JSONObject mergedConfig = deepMerge(baseConfig, overrideConfig);

        logger.info("Successfully merged file configuration with encoded parameter using deep merge strategy");
        return mergedConfig.toString();
    }

    /**
     * Performs deep merge of two JSON objects.
     * Override values take precedence over base values for conflicting keys.
     * Nested objects are merged recursively, preserving non-conflicting properties.
     * Arrays in override completely replace arrays in base.
     *
     * @param base The base JSON object (from file)
     * @param override The override JSON object (from encoded parameter)
     * @return The deeply merged JSON object
     */
    public JSONObject deepMerge(JSONObject base, JSONObject override) {
        if (base == null && override == null) {
            return new JSONObject();
        }
        if (base == null) {
            return new JSONObject(override.toString());
        }
        if (override == null) {
            return new JSONObject(base.toString());
        }

        // Create a copy of base to avoid modifying the original
        JSONObject result = new JSONObject(base.toString());

        // Iterate through all keys in override object
        Iterator<String> keys = override.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object overrideValue = override.get(key);

            if (result.has(key)) {
                Object baseValue = result.get(key);

                // If both values are JSONObjects, perform recursive deep merge
                if (baseValue instanceof JSONObject && overrideValue instanceof JSONObject) {
                    JSONObject mergedNestedObject = deepMerge((JSONObject) baseValue, (JSONObject) overrideValue);
                    result.put(key, mergedNestedObject);
                } else {
                    // For all other types (including arrays), override completely replaces base
                    result.put(key, overrideValue);
                }
            } else {
                // Key doesn't exist in base, add it from override
                result.put(key, overrideValue);
            }
        }

        return result;
    }
}
