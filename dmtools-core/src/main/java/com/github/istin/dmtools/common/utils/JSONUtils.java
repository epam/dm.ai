// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import com.github.istin.dmtools.common.model.JSONModel;
import com.google.gson.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility class for JSON serialization and deserialization.
 * Supports standard JSON types (JSONObject, JSONArray), JSONModel,
 * and arbitrary POJOs via Gson.
 */
public class JSONUtils {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(JSONModel.class, (JsonSerializer<JSONModel>) (src, typeOfSrc, context) ->
                JsonParser.parseString(src.toString()))
            .registerTypeHierarchyAdapter(JSONObject.class, (JsonSerializer<JSONObject>) (src, typeOfSrc, context) ->
                JsonParser.parseString(src.toString()))
            .registerTypeHierarchyAdapter(JSONArray.class, (JsonSerializer<JSONArray>) (src, typeOfSrc, context) ->
                JsonParser.parseString(src.toString()))
            .create();

    /**
     * Serializes any object to a JSON string.
     * Handles List, JSONModel, JSONObject, JSONArray, and POJOs.
     * 
     * @param result The object to serialize
     * @return JSON string representation
     */
    public static String serializeResult(Object result) {
        if (result == null) {
            return "null";
        }

        // Handle primitives and String
        if (result instanceof String || result instanceof Number || result instanceof Boolean) {
            return result.toString();
        }

        // Default: serialize via Gson which handles JSONModel, JSONObject, JSONArray, and POJOs
        try {
            return gson.toJson(result);
        } catch (Exception e) {
            return result.toString();
        }
    }
}

