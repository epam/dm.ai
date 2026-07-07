// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

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
            org.json.JSONObject json = CliFormatterUtils.extractJSONObject(result);
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

    private String formatFallback(Object result) {
        return new JsonCliOutputFormatter().formatResult(result);
    }

    private String formatShortMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(CliFormatterUtils.firstSentence(String.valueOf(entry.getValue()), false)).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatShortList(List<Object> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(i + 1).append(". ").append(CliFormatterUtils.firstSentence(String.valueOf(list.get(i)), false)).append('\n');
        }
        return sb.toString().trim();
    }

    @Override
    public String formatList(Map<String, Object> toolsList) {
        return CliFormatterUtils.formatToolList(
                toolsList,
                this::formatHeader,
                this::formatRow,
                lines -> "\nTotal: " + lines.size() + " tools"
        );
    }

    private String formatHeader(List<CliFormatterUtils.ToolLine> lines) {
        int integrationWidth = Math.max(10, lines.stream()
                .mapToInt(t -> t.integration.length()).max().orElse(10));
        int nameWidth = Math.max(20, lines.stream()
                .mapToInt(t -> t.name.length()).max().orElse(20));

        String header = CliFormatterUtils.padRight("INTEGRATION", integrationWidth) + "  "
                + CliFormatterUtils.padRight("TOOL", nameWidth) + "  DESCRIPTION";
        return header + '\n' + CliFormatterUtils.repeat('-', header.length()) + '\n';
    }

    private String formatRow(List<CliFormatterUtils.ToolLine> lines, CliFormatterUtils.ToolLine line) {
        int integrationWidth = Math.max(10, lines.stream()
                .mapToInt(t -> t.integration.length()).max().orElse(10));
        int nameWidth = Math.max(20, lines.stream()
                .mapToInt(t -> t.name.length()).max().orElse(20));

        return CliFormatterUtils.padRight(line.integration, integrationWidth) + "  "
                + CliFormatterUtils.padRight(line.name, nameWidth) + "  "
                + line.description + '\n';
    }

    @Override
    public String formatError(String message) {
        return "Error: " + message;
    }
}
