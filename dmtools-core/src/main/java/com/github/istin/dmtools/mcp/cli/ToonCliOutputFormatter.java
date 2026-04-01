// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.common.utils.JSONUtils;
import dev.toonformat.jtoon.JToon;
import org.json.JSONObject;

import java.util.Map;

/**
 * TOON-format formatter using the <a href="https://github.com/toon-format/toon-java">toon-java</a>
 * library ({@code dev.toonformat:jtoon}).
 *
 * <p>Produces compact, YAML-like TOON output with 30-60% token reduction compared to JSON,
 * making it ideal for passing results to LLM contexts.</p>
 *
 * <p>Example output:
 * <pre>
 * user:
 *   id: 123
 *   name: Ada
 *   tags[2]: reading,gaming
 * </pre>
 * </p>
 */
public class ToonCliOutputFormatter implements CliOutputFormatter {

    @Override
    public String formatResult(Object result) {
        if (result == null) {
            return "success: true";
        }

        String json = JSONUtils.serializeResult(result);
        if (isJsonStructure(json)) {
            return safeEncode(json);
        }
        return json;
    }

    @Override
    public String formatList(Map<String, Object> toolsList) {
        return safeEncode(new JSONObject(toolsList).toString());
    }

    @Override
    public String formatError(String message) {
        JSONObject error = new JSONObject();
        error.put("error", true);
        error.put("message", message);
        return safeEncode(error.toString());
    }

    private static boolean isJsonStructure(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static String safeEncode(String json) {
        try {
            return JToon.encodeJson(json);
        } catch (Exception e) {
            return json;
        }
    }
}
