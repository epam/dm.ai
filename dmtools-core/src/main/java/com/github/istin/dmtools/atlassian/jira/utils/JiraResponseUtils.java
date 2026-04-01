// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JiraResponseUtils {

    /**
     * Transforms JSON object by replacing custom field IDs with human-readable names.
     * 
     * @param jsonObject The JSON object to transform
     * @param reverseMapping Map of custom field ID to human-friendly name
     */
    public static void transformJson(JSONObject jsonObject, Map<String, String> reverseMapping) {
        // Look for "fields" object
        if (jsonObject.has("fields")) {
            Object fieldsObj = jsonObject.get("fields");
            if (fieldsObj instanceof JSONObject) {
                JSONObject fields = (JSONObject) fieldsObj;
                List<String> keysToReplace = new ArrayList<>();
                for (String key : fields.keySet()) {
                    if (key.startsWith("customfield_")) {
                        keysToReplace.add(key);
                    }
                }
                for (String key : keysToReplace) {
                    String humanName = reverseMapping.get(key);
                    if (humanName != null) {
                        Object value = fields.get(key);
                        fields.remove(key);
                        fields.put(humanName, value);
                    }
                }
            }
        }

        // Also handle "issues" array in SearchResult
        if (jsonObject.has("issues")) {
            Object issuesObj = jsonObject.get("issues");
            if (issuesObj instanceof JSONArray) {
                JSONArray issues = (JSONArray) issuesObj;
                for (int i = 0; i < issues.length(); i++) {
                    Object issue = issues.get(i);
                    if (issue instanceof JSONObject) {
                        transformJson((JSONObject) issue, reverseMapping);
                    }
                }
            }
        }

        // Handle "parent" object
        if (jsonObject.has("parent")) {
            Object parentObj = jsonObject.get("parent");
            if (parentObj instanceof JSONObject) {
                transformJson((JSONObject) parentObj, reverseMapping);
            }
        }
    }
}

