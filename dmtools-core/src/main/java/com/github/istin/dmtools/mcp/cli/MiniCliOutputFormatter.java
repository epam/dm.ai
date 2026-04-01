// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.JSONUtils;
import com.github.istin.dmtools.common.utils.LLMOptimizedJson;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * DMTools-specific LLM-optimised formatter.
 *
 * <p>Format resolution for the result object:</p>
 * <ol>
 *   <li>If the result implements {@link ToText}, calls {@link ToText#toText()} directly.</li>
 *   <li>If the result is a {@code List} whose first element implements {@link ToText},
 *       calls {@link ToText.Utils#toText(List)} on the whole list.</li>
 *   <li>Otherwise serialises to JSON and converts via {@link LLMOptimizedJson#format(String)}.</li>
 * </ol>
 */
public class MiniCliOutputFormatter implements CliOutputFormatter {

    @Override
    public String formatResult(Object result) {
        if (result == null) {
            JSONObject response = new JSONObject();
            response.put("success", true);
            return safeFormat(response.toString());
        }

        // 1. Direct ToText support
        if (result instanceof ToText) {
            try {
                return ((ToText) result).toText();
            } catch (Exception e) {
                // fall through to JSON path
            }
        }

        // 2. List of ToText items
        if (result instanceof List) {
            List<?> list = (List<?>) result;
            if (!list.isEmpty() && list.get(0) instanceof ToText) {
                try {
                    @SuppressWarnings("unchecked")
                    List<ToText> toTextList = (List<ToText>) list;
                    return ToText.Utils.toText(toTextList);
                } catch (Exception e) {
                    // fall through to JSON path
                }
            }
        }

        // 3. LLMOptimizedJson fallback
        String serialized = JSONUtils.serializeResult(result);
        if (isJsonStructure(serialized)) {
            return safeFormat(serialized);
        }
        JSONObject response = new JSONObject();
        if (serialized.trim().startsWith("[")) {
            response.put("result", new JSONArray(serialized));
        } else {
            response.put("result", result);
        }
        return safeFormat(response.toString());
    }

    @Override
    public String formatList(Map<String, Object> toolsList) {
        return safeFormat(new JSONObject(toolsList).toString());
    }

    @Override
    public String formatError(String message) {
        JSONObject error = new JSONObject();
        error.put("error", true);
        error.put("message", message);
        return safeFormat(error.toString());
    }

    private static boolean isJsonStructure(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static String safeFormat(String json) {
        try {
            return LLMOptimizedJson.format(json);
        } catch (Exception e) {
            return json;
        }
    }
}
