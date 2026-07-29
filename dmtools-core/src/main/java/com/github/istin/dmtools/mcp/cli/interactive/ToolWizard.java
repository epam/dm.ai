// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guides the user through entering parameters for an MCP tool one by one.
 *
 * <p>The wizard loads the tool schema, prints the tool description, then asks
 * for each parameter.  Required parameters are asked first.  The user can
 * cancel at any prompt by pressing Esc.</p>
 */
public class ToolWizard {

    private final Terminal terminal;

    public ToolWizard(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Result of a wizard session.
     */
    public static final class Result {
        private final boolean cancelled;
        private final String toolName;
        private final Map<String, Object> params;

        private Result(boolean cancelled, String toolName, Map<String, Object> params) {
            this.cancelled = cancelled;
            this.toolName = toolName;
            this.params = params;
        }

        public static Result cancelled() {
            return new Result(true, null, null);
        }

        public static Result ok(String toolName, Map<String, Object> params) {
            return new Result(false, toolName, Collections.unmodifiableMap(new LinkedHashMap<>(params)));
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public String getToolName() {
            return toolName;
        }

        public Map<String, Object> getParams() {
            return params;
        }
    }

    /**
     * Runs the wizard for the given tool.  Returns {@link Result#cancelled()} if
     * the user presses Esc at any prompt.
     */
    public Result run(String toolName) {
        Map<String, Object> schema = loadToolSchema(toolName);
        if (schema == null) {
            terminal.write("\nCould not load schema for " + toolName + ". Falling back to direct execution.\n");
            return Result.ok(toolName, Collections.emptyMap());
        }

        terminal.write("\nTool: " + toolName + "\n");
        terminal.write("Description: " + stringValue(schema.get("description")) + "\n\n");

        Map<String, Object> inputSchema = mapValue(schema.get("inputSchema"));
        Map<String, Object> properties = mapValue(inputSchema.get("properties"));
        List<String> required = listValue(inputSchema.get("required"));
        Set<String> requiredSet = required == null ? Collections.emptySet() : new java.util.HashSet<>(required);

        List<Map.Entry<String, Object>> ordered = new ArrayList<>(properties.entrySet());
        ordered.sort((a, b) -> {
            boolean ar = requiredSet.contains(a.getKey());
            boolean br = requiredSet.contains(b.getKey());
            if (ar && !br) return -1;
            if (!ar && br) return 1;
            return a.getKey().compareTo(b.getKey());
        });

        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ordered) {
            String paramName = entry.getKey();
            Map<String, Object> paramSchema = mapValue(entry.getValue());
            boolean isRequired = requiredSet.contains(paramName);
            PromptResult value = promptParam(paramName, paramSchema, isRequired);
            if (value.cancelled) {
                terminal.write("\nCancelled.\n");
                return Result.cancelled();
            }
            if (value.value != null) {
                params.put(paramName, convertValue(value.value, stringValue(paramSchema.get("type"))));
            }
        }

        return Result.ok(toolName, params);
    }

    private PromptResult promptParam(String paramName, Map<String, Object> paramSchema, boolean required) {
        String desc = stringValue(paramSchema.get("description"));
        String example = stringValue(paramSchema.get("example"));
        String type = stringValue(paramSchema.get("type"));

        StringBuilder prompt = new StringBuilder();
        prompt.append("Parameter: ").append(paramName).append("\n");
        if (!desc.isEmpty()) {
            prompt.append("  ").append(desc).append("\n");
        }
        if (!example.isEmpty()) {
            prompt.append("  Example: ").append(example).append("\n");
        }
        if (required) {
            prompt.append("  (required)\n");
        } else {
            prompt.append("  (optional, press Enter to skip)\n");
        }
        if ("boolean".equals(type)) {
            prompt.append("  [y/n]\n");
        }
        prompt.append("> ");

        terminal.write(prompt.toString());
        String line = terminal.readLine();
        if (line == null) {
            return PromptResult.cancelled();
        }
        if (line.startsWith("\u001b")) {
            return PromptResult.cancelled();
        }
        line = line.trim();
        if (line.isEmpty() && !required) {
            return PromptResult.skip();
        }
        if (line.isEmpty() && required) {
            terminal.write("Required. Please provide a value.\n");
            return promptParam(paramName, paramSchema, required);
        }
        return PromptResult.value(line);
    }

    private static final class PromptResult {
        final boolean cancelled;
        final String value;

        private PromptResult(boolean cancelled, String value) {
            this.cancelled = cancelled;
            this.value = value;
        }

        static PromptResult cancelled() {
            return new PromptResult(true, null);
        }

        static PromptResult skip() {
            return new PromptResult(false, null);
        }

        static PromptResult value(String value) {
            return new PromptResult(false, value);
        }
    }

    private Object convertValue(String raw, String type) {
        if ("array".equals(type)) {
            List<String> list = new ArrayList<>();
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
            return list;
        }
        if ("boolean".equals(type)) {
            String lower = raw.toLowerCase();
            return lower.equals("true") || lower.equals("1") || lower.equals("yes") || lower.equals("y");
        }
        if ("integer".equals(type)) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                return raw;
            }
        }
        if ("number".equals(type)) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return raw;
            }
        }
        return raw;
    }

    private Map<String, Object> loadToolSchema(String toolName) {
        try {
            Map<String, Object> response = MCPSchemaGenerator.generateToolsListResponse(CommandSource.defaultIntegrations());
            Object toolsObj = response.get("tools");
            if (toolsObj instanceof List) {
                for (Object o : (List<?>) toolsObj) {
                    if (o instanceof Map && toolName.equals(((Map<?, ?>) o).get("name"))) {
                        return (Map<String, Object>) o;
                    }
                }
            } else if (toolsObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) toolsObj;
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof JSONObject && toolName.equals(((JSONObject) o).optString("name"))) {
                        return ((JSONObject) o).toMap();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value instanceof JSONObject) {
            return ((JSONObject) value).toMap();
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> listValue(Object value) {
        if (value instanceof List) {
            return (List<String>) value;
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.get(i).toString());
            }
            return result;
        }
        return null;
    }
}
