// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mermaid diagram formatter for tool lists.
 *
 * <p>Produces a {@code mindmap} grouped by integration:
 * <pre>
 * mindmap
 *   root((DMtools Tools))
 *     jira
 *       jira_get_ticket
 *       jira_search_by_jql
 *     ado
 *       ado_get_work_item
 * </pre>
 * For arbitrary results falls back to pretty-printed JSON.
 */
public class MermaidCliOutputFormatter implements CliOutputFormatter {

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
            return formatMapAsMermaid((Map<String, Object>) data);
        }
        if (data instanceof List) {
            return formatListAsMermaid((List<Object>) data);
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

    private String formatMapAsMermaid(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("mindmap").append('\n');
        sb.append("  root((Result))").append('\n');
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append("    ").append(escapeMermaid(entry.getKey())).append('\n');
            Object value = entry.getValue();
            if (value instanceof Map) {
                for (Object k : ((Map<?, ?>) value).keySet()) {
                    sb.append("      ").append(escapeMermaid(String.valueOf(k))).append('\n');
                }
            } else if (value instanceof List && !((List<?>) value).isEmpty()) {
                for (Object item : ((List<?>) value)) {
                    sb.append("      ").append(escapeMermaid(firstSentence(String.valueOf(item)))).append('\n');
                }
            } else {
                sb.append("      ").append(escapeMermaid(firstSentence(String.valueOf(value)))).append('\n');
            }
        }
        return sb.toString();
    }

    private String formatListAsMermaid(List<Object> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("mindmap").append('\n');
        sb.append("  root((Result))").append('\n');
        for (int i = 0; i < list.size(); i++) {
            sb.append("    ").append("item_").append(i + 1).append('\n');
            Object item = list.get(i);
            if (item instanceof Map) {
                for (Object k : ((Map<?, ?>) item).keySet()) {
                    sb.append("      ").append(escapeMermaid(String.valueOf(k))).append('\n');
                }
            } else {
                sb.append("      ").append(escapeMermaid(firstSentence(String.valueOf(item)))).append('\n');
            }
        }
        return sb.toString();
    }

    private static String firstSentence(String description) {
        if (description == null || description.isEmpty()) {
            return "";
        }
        String trimmed = description.trim();
        int idx = trimmed.indexOf('\n');
        if (idx > 0 && idx < 80) {
            return trimmed.substring(0, idx).trim();
        }
        idx = trimmed.indexOf('.');
        if (idx > 0 && idx < 80) {
            return trimmed.substring(0, idx + 1).trim();
        }
        if (trimmed.length() > 80) {
            return trimmed.substring(0, 77).trim() + "...";
        }
        return trimmed;
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

        Map<String, List<String>> byIntegration = new TreeMap<>();
        for (Map<String, Object> tool : tools) {
            String name = stringValue(tool.get("name"));
            String integration = stringValue(tool.get("integration"));
            if (integration.isEmpty()) {
                integration = "other";
            }
            byIntegration.computeIfAbsent(integration, k -> new ArrayList<>()).add(name);
        }

        for (List<String> names : byIntegration.values()) {
            Collections.sort(names);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("mindmap").append('\n');
        sb.append("  root((DMtools Tools))").append('\n');
        for (Map.Entry<String, List<String>> entry : byIntegration.entrySet()) {
            sb.append("    ").append(escapeMermaid(entry.getKey())).append('\n');
            for (String name : entry.getValue()) {
                sb.append("      ").append(escapeMermaid(name)).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String formatError(String message) {
        return "Error: " + message;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String escapeMermaid(String input) {
        return input.replace("\"", "\\\"").replace("\n", " ");
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
