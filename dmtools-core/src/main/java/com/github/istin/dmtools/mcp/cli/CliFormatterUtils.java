// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for CLI output formatters.
 *
 * <p>This class is intentionally package-private and final: it only exists to avoid
 * copy-pasting small utilities between the various {@link CliOutputFormatter} implementations.
 */
final class CliFormatterUtils {

    private CliFormatterUtils() {
        // utility class
    }

    /**
     * Normalises the tools payload (List or JSONArray) into a list of maps.
     * Returns {@code null} when the payload cannot be interpreted as a tool list.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> toToolList(Object toolsObj) {
        if (toolsObj instanceof List) {
            return (List<Map<String, Object>>) toolsObj;
        }
        if (toolsObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) toolsObj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.get(i);
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                } else if (item instanceof JSONObject) {
                    result.add(((JSONObject) item).toMap());
                }
            }
            return result;
        }
        return null;
    }

    static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Extracts the first sentence/line of a description, optionally escaping pipe characters.
     *
     * @param description raw description, may be null/empty
     * @param escapePipes whether to escape {@code |} as {@code \|} for Markdown tables
     */
    static String firstSentence(String description, boolean escapePipes) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        String trimmed = description.trim();
        if (escapePipes) {
            trimmed = trimmed.replace("|", "\\|");
        }
        int idx = trimmed.indexOf('\n');
        if (idx > 0 && idx < 120) {
            return trimmed.substring(0, idx).trim();
        }
        idx = trimmed.indexOf('.');
        if (idx > 0 && idx < 120) {
            return trimmed.substring(0, idx + 1).trim();
        }
        if (trimmed.length() > 120) {
            return trimmed.substring(0, 117).trim() + "...";
        }
        return trimmed;
    }

    static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    static String repeat(char c, int count) {
        char[] arr = new char[count];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    /**
     * Extracts a JSON object from JSONModel-like wrappers via getJSONObject(),
     * or parses the object's toString() when it looks like JSON.
     * Returns {@code null} when neither approach works.
     */
    static org.json.JSONObject extractJSONObject(Object result) {
        try {
            java.lang.reflect.Method m = result.getClass().getMethod("getJSONObject");
            Object jo = m.invoke(result);
            if (jo instanceof org.json.JSONObject) {
                return (org.json.JSONObject) jo;
            }
        } catch (Exception e) {
            // ignore
        }
        try {
            String s = result.toString();
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                return new org.json.JSONObject(s);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Formats a list of integration tools using a caller-provided row renderer.
     *
     * @param toolsList raw payload containing the "tools" key
     * @param headerRenderer produces the header line(s)
     * @param rowRenderer produces one formatted line per tool
     * @param footerRenderer produces the trailing line (may be null)
     * @return formatted string, or a JSON fallback if the payload is not a tool list
     */
    static String formatToolList(
            Map<String, Object> toolsList,
            java.util.function.Function<List<ToolLine>, String> headerRenderer,
            java.util.function.BiFunction<List<ToolLine>, ToolLine, String> rowRenderer,
            java.util.function.Function<List<ToolLine>, String> footerRenderer) {

        List<Map<String, Object>> tools = toToolList(toolsList.get("tools"));
        if (tools == null) {
            return new JsonCliOutputFormatter().formatList(toolsList);
        }
        if (tools.isEmpty()) {
            return "No tools available.";
        }

        boolean escapePipes = footerRenderer == null; // heuristic: table has no footer
        List<ToolLine> lines = normalizeAndSortTools(tools, escapePipes);

        StringBuilder sb = new StringBuilder();
        sb.append(headerRenderer.apply(lines));
        for (ToolLine line : lines) {
            sb.append(rowRenderer.apply(lines, line));
        }
        if (footerRenderer != null) {
            sb.append(footerRenderer.apply(lines));
        }
        return sb.toString();
    }

    static List<ToolLine> normalizeAndSortTools(List<Map<String, Object>> tools, boolean escapePipes) {
        List<ToolLine> lines = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            String name = stringValue(tool.get("name"));
            String integration = stringValue(tool.get("integration"));
            if (integration.isEmpty()) {
                integration = "-";
            }
            String description = firstSentence(stringValue(tool.get("description")), escapePipes);
            lines.add(new ToolLine(integration, name, description));
        }
        lines.sort(Comparator.comparing((ToolLine t) -> t.integration)
                .thenComparing(t -> t.name));
        return lines;
    }

    static final class ToolLine {
        final String integration;
        final String name;
        final String description;

        ToolLine(String integration, String name, String description) {
            this.integration = integration;
            this.name = name;
            this.description = description;
        }
    }
}
