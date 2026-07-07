// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import java.util.ArrayList;
import java.util.Collections;
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
            org.json.JSONObject json = CliFormatterUtils.extractJSONObject(result);
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
        sb.append("| ").append(CliFormatterUtils.padRight("Key", keyWidth))
                .append(" | ").append(CliFormatterUtils.padRight("Value", valueWidth)).append(" |\n");
        sb.append("| ").append(CliFormatterUtils.repeat('-', keyWidth))
                .append(" | ").append(CliFormatterUtils.repeat('-', valueWidth)).append(" |\n");
        for (String key : keys) {
            String value = String.valueOf(map.get(key));
            if (value.length() > valueWidth) {
                value = value.substring(0, valueWidth - 3) + "...";
            }
            sb.append("| ").append(CliFormatterUtils.padRight(key, keyWidth))
                    .append(" | ").append(CliFormatterUtils.padRight(value, valueWidth)).append(" |\n");
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
                widths.put(col, Math.max(widths.get(col), Math.min(cellText(row, col).length(), 80)));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (String col : columns) {
            sb.append(CliFormatterUtils.padRight(col, widths.get(col))).append(" | ");
        }
        sb.append('\n');
        sb.append("| ");
        for (String col : columns) {
            sb.append(CliFormatterUtils.repeat('-', widths.get(col))).append(" | ");
        }
        sb.append('\n');
        for (Map<String, Object> row : list) {
            sb.append("| ");
            for (String col : columns) {
                sb.append(CliFormatterUtils.padRight(cellText(row, col), widths.get(col))).append(" | ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String cellText(Map<String, Object> row, String col) {
        Object val = row.get(col);
        String text = val == null ? "" : String.valueOf(val);
        if (text.length() > 100) {
            text = text.substring(0, 97) + "...";
        }
        return text.replace("|", "\\|");
    }

    @Override
    public String formatList(Map<String, Object> toolsList) {
        return CliFormatterUtils.formatToolList(
                toolsList,
                this::formatHeader,
                this::formatRow,
                null
        );
    }

    private String formatHeader(List<CliFormatterUtils.ToolLine> lines) {
        int integrationWidth = Math.max("Integration".length(), lines.stream()
                .mapToInt(t -> t.integration.length()).max().orElse(0));
        int nameWidth = Math.max("Tool".length(), lines.stream()
                .mapToInt(t -> t.name.length()).max().orElse(0));
        int descWidth = Math.max("Description".length(), lines.stream()
                .mapToInt(t -> t.description.length()).max().orElse(0));

        return "| " + CliFormatterUtils.padRight("Integration", integrationWidth)
                + " | " + CliFormatterUtils.padRight("Tool", nameWidth)
                + " | " + CliFormatterUtils.padRight("Description", descWidth) + " |\n"
                + "| " + CliFormatterUtils.repeat('-', integrationWidth)
                + " | " + CliFormatterUtils.repeat('-', nameWidth)
                + " | " + CliFormatterUtils.repeat('-', descWidth) + " |\n";
    }

    private String formatRow(List<CliFormatterUtils.ToolLine> lines, CliFormatterUtils.ToolLine line) {
        int integrationWidth = Math.max("Integration".length(), lines.stream()
                .mapToInt(t -> t.integration.length()).max().orElse(0));
        int nameWidth = Math.max("Tool".length(), lines.stream()
                .mapToInt(t -> t.name.length()).max().orElse(0));
        int descWidth = Math.max("Description".length(), lines.stream()
                .mapToInt(t -> t.description.length()).max().orElse(0));

        return "| " + CliFormatterUtils.padRight(line.integration, integrationWidth)
                + " | " + CliFormatterUtils.padRight(line.name, nameWidth)
                + " | " + CliFormatterUtils.padRight(line.description, descWidth) + " |\n";
    }

    @Override
    public String formatError(String message) {
        return "Error: " + message;
    }
}
