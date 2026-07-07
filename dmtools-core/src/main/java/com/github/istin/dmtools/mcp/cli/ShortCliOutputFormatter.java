// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compact human-readable formatter.
 *
 * <p>For tool lists produces one line per tool:
 * <pre>
 * jira      jira_get_ticket      Get Jira ticket by key
 * </pre>
 * For arbitrary results falls back to pretty-printed JSON.
 */
public class ShortCliOutputFormatter implements CliOutputFormatter {

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
            data = ((org.json.JSONObject) result).toMap();
        } else if (result instanceof org.json.JSONArray) {
            data = ((org.json.JSONArray) result).toList();
        } else {
            org.json.JSONObject json = extractJSONObject(result);
            if (json != null) {
                data = json.toMap();
            }
        }

        if (data instanceof Map) {
            return formatShortMap((Map<String, Object>) data);
        }
        if (data instanceof List) {
            return formatShortList((List<Object>) data);
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

    private String formatShortMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(firstSentence(String.valueOf(entry.getValue()))).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatShortList(List<Object> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i + 1).append(". ").append(firstSentence(String.valueOf(list.get(i)))).append('\n');
        }
        return sb.toString().trim();
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
            String description = stringValue(tool.get("description"));
            description = firstSentence(description);
            lines.add(new ToolLine(integration, name, description));
        }

        lines.sort(Comparator.comparing((ToolLine t) -> t.integration)
                .thenComparing(t -> t.name));

        int integrationWidth = Math.max(10, lines.stream()
                .mapToInt(t -> t.integration.length()).max().orElse(10));
        int nameWidth = Math.max(20, lines.stream()
                .mapToInt(t -> t.name.length()).max().orElse(20));

        StringBuilder sb = new StringBuilder();
        String header = padRight("INTEGRATION", integrationWidth) + "  "
                + padRight("TOOL", nameWidth) + "  DESCRIPTION";
        sb.append(header).append('\n');
        sb.append(repeat('-', header.length())).append('\n');

        for (ToolLine line : lines) {
            sb.append(padRight(line.integration, integrationWidth)).append("  ");
            sb.append(padRight(line.name, nameWidth)).append("  ");
            sb.append(line.description);
            sb.append('\n');
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
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    private static String firstSentence(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        String trimmed = description.trim();
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
