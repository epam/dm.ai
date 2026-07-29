// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Utility for constructing and normalizing Atlassian Document Format (ADF) payloads
 * used by the Jira REST API v3 for rich-text fields such as description, comment and
 * environment.
 */
public class AtlassianDocumentFormat {

    private AtlassianDocumentFormat() {
        // utility class
    }

    /**
     * Wraps a plain-text string into a minimal ADF document ({@code {version:1, type:doc,
     * content:[{type:paragraph, content:[{type:text, text:...}]}]}).
     *
     * @param text plain text to wrap
     * @return ADF document as {@link JSONObject}
     */
    public static JSONObject wrapPlainText(String text) {
        JSONObject document = new JSONObject();
        document.put("version", 1);
        document.put("type", "doc");

        JSONArray content = new JSONArray();
        content.put(paragraph(text));

        document.put("content", content);
        return document;
    }

    /**
     * Builds a paragraph node containing the supplied plain text.
     */
    public static JSONObject paragraph(String text) {
        JSONObject paragraph = new JSONObject();
        paragraph.put("type", "paragraph");
        paragraph.put("content", new JSONArray().put(textNode(text)));
        return paragraph;
    }

    /**
     * Builds a text node.
     */
    public static JSONObject textNode(String text) {
        JSONObject node = new JSONObject();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    /**
     * Normalizes a value that should be sent to a Jira v3 rich-text field.
     * <ul>
     *   <li>If the value is already a {@link JSONObject}, it is returned as-is (assumed ADF).</li>
     *   <li>If the value is a {@link JSONArray}, it is wrapped into an ADF document content array.</li>
     *   <li>If the value is a string that parses as a JSON object/array, it is parsed and treated as ADF.</li>
     *   <li>Otherwise the string is wrapped into a plain-text ADF document.</li>
     * </ul>
     *
     * @param value user-provided value
     * @return ADF {@link JSONObject} ready to be sent to Jira v3
     */
    public static JSONObject normalize(Object value) {
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }

        if (value instanceof JSONArray) {
            JSONObject document = new JSONObject();
            document.put("version", 1);
            document.put("type", "doc");
            document.put("content", value);
            return document;
        }

        String str = String.valueOf(value);
        if (looksLikeJson(str)) {
            try {
                JSONTokener tokener = new JSONTokener(str);
                Object parsed = tokener.nextValue();
                if (parsed instanceof JSONObject) {
                    return (JSONObject) parsed;
                }
                if (parsed instanceof JSONArray) {
                    JSONObject document = new JSONObject();
                    document.put("version", 1);
                    document.put("type", "doc");
                    document.put("content", parsed);
                    return document;
                }
            } catch (JSONException ignored) {
                // fall through to plain text wrapping
            }
        }

        return wrapPlainText(str);
    }

    private static boolean looksLikeJson(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
