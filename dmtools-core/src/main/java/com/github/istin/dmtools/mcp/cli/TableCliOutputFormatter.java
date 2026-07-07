// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Markdown table formatter for tool lists.
 *
 * <p>Example output:
 * <pre>
 * | Integration | Tool | Description |
 * |-------------|------|-------------|
 * | jira | jira_get_ticket | Get Jira ticket by key |
 * </pre>
 * For arbitrary results falls back to pretty-printed JSON.
 */
public class TableCliOutputFormatter implements CliOutputFormatter {

    @Override
    public String formatResult(Object result) {
        if (result == null) {
            return formatFallback(result);
        }
        if (result instanceof String) {
            return (String) result;
        }

        Object data = result;
        if (result instanceof org.json.JSONObject) {
            org.json.JSONObject json = (org.json.JSONObject) result;
            if (json.has("fields") || json.has("key") || json.has("id")) {
                data = json.toMap();
            } else {
                return formatFallback(result);
            }
        } else if (result instanceof org.json.JSONArray) {
            data = ((org.json.JSONArray) result).toList();
        } else {
            // JSONModel and similar wrappers: try getJSONObject()/toString -> parse
            org.json.JSONObject json = extractJSONObject(result);
            if (json != null) {
                data = json.toMap();
            }
        }

        if (data instanceof Map) {
            return formatMapAsTable((Map<String, Object>) data);
        }
        if (data instanceof List) {
            return formatListAsTable((List<Object>) data);
        }
        return formatFallback(result);
    }

    private org.json.JSONObject extractJSONObject(Object result) {
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

    private String formatFallback(Object result) {
        return new JsonCliOutputFormatter().formatResult(result);
    }

    private String formatMapAsTable(Map<String, Object> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        int keyWidth = Math.max("Key".length(), keys.stream().mapToInt(String::length).max().orElse(0));
        int valueWidth = Math.max("Value".length(), keys.stream()
                .mapToInt(k -> String.valueOf(map.get(k)).length())
                .max().orElse(0));
        valueWidth = Math.min(valueWidth, 120);

        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(padRight("Key", keyWidth))
                .append(" | ").append(padRight("Value", valueWidth)).append(" |\n");
        sb.append("| ").append(repeat('-', keyWidth))
                .append(" | ").append(repeat('-', valueWidth)).append(" |\n");
        for (String key : keys) {
            String value = String.valueOf(map.get(key));
            if (value.length() > valueWidth) {
                value = value.substring(0, valueWidth - 3) + "...";
            }
            sb.append("| ").append(padRight(key, keyWidth))
                    .append(" | ").append(padRight(value, valueWidth)).append(" |\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatListAsTable(List<Object> list) {
        if (list.isEmpty()) {
            return "Empty result.";
        }
        if (list.get(0) instanceof Map) {
            return formatListOfMapsAsTable((List<Map<String, Object>>) (List<?>) list);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| # | Value |\n");
        sb.append("|---|-------|\n");
        for (int i = 0; i < list.size(); i++) {
            String value = String.valueOf(list.get(i));
            if (value.length() > 100) {
                value = value.substring(0, 97) + "...";
            }
            sb.append("| ").append(i + 1).append(" | ").append(value.replace("|", "\\|")).append(" |\n");
        }
        return sb.toString();
    }

    private String formatListOfMapsAsTable(List<Map<String, Object>> list) {
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, Object> row : list) {
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);
        Map<String, Integer> widths = new LinkedHashMap<>();
        for (String col : columns) {
            widths.put(col, Math.max(col.length(), 10));
        }
        for (Map<String, Object> row : list) {
            for (String col : columns) {
                Object val = row.get(col);
                String text = val == null ? "" : String.valueOf(val);
                if (text.length() > 100) {
                    text = text.substring(0, 97) + "...";
                }
                widths.put(col, Math.max(widths.get(col), Math.min(text.length(), 80)));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (String col : columns) {
            sb.append(padRight(col, widths.get(col))).append(" | ");
        }
        sb.append('\n');
        sb.append("| ");
        for (String col : columns) {
            sb.append(repeat('-', widths.get(col))).append(" | ");
        }
        sb.append('\n');
        for (Map<String, Object> row : list) {
            sb.append("| ");
            for (String col : columns) {
                Object val = row.get(col);
                String text = val == null ? "" : String.valueOf(val);
                if (text.length() > 100) {
                    text = text.substring(0, 97) + "...";
                }
                text = text.replace("|", "\\|");
                sb.append(padRight(text, widths.get(col))).append(" | ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String formatList(Map<String, Object> toolsList) {
        List<Map<String, Object>> tools = toToolList(toolsList.get("tools"));
        if (tools == null) {
            return new JsonCliOutputFormatter().formatList(toolsList);
        }
        if (tools.isEmpty()) {
            return "No tools available.";
        }

        List<ToolLine> lines = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            String name = stringValue(tool.get("name"));
            String integration = stringValue(tool.get("integration"));
            if (integration.isEmpty()) {
                integration = "-";
            }
            String description = firstSentence(stringValue(tool.get("description")));
            lines.add(new ToolLine(integration, name, description));
        }

        lines.sort(Comparator.comparing((ToolLine t) -> t.integration)
                .thenComparing(t -> t.name));

        int integrationWidth = Math.max("Integration".length(), lines.stream()
                .mapToInt(t -> t.integration.length()).max().orElse(0));
        int nameWidth = Math.max("Tool".length(), lines.stream()
                .mapToInt(t -> t.name.length()).max().orElse(0));
        int descWidth = Math.max("Description".length(), lines.stream()
                .mapToInt(t -> t.description.length()).max().orElse(0));

        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(padRight("Integration", integrationWidth))
                .append(" | ").append(padRight("Tool", nameWidth))
                .append(" | ").append(padRight("Description", descWidth)).append(" |\n");
        sb.append("| ").append(repeat('-', integrationWidth))
                .append(" | ").append(repeat('-', nameWidth))
                .append(" | ").append(repeat('-', descWidth)).append(" |\n");

        for (ToolLine line : lines) {
            sb.append("| ").append(padRight(line.integration, integrationWidth))
                    .append(" | ").append(padRight(line.name, nameWidth))
                    .append(" | ").append(padRight(line.description, descWidth)).append(" |\n");
        }
        sb.append('\n');
        sb.append("Total: ").append(lines.size()).append(" tools");
        return sb.toString();
    }

    @Override
    public String formatError(String message) {
        return "Error: " + message;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstSentence(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        String trimmed = description.trim().replace("|", "\\|");
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

    private static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        char[] arr = new char[count];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    private static final class ToolLine {
        final String integration;
        final String name;
        final String description;

        ToolLine(String integration, String name, String description) {
            this.integration = integration;
            this.name = name;
            this.description = description;
        }
    }

    /**
     * Normalises the tools payload (List or JSONArray) into a list of maps.
     * Returns {@code null} when the payload cannot be interpreted as a tool list.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toToolList(Object toolsObj) {
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
}
