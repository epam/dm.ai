// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.common.utils.JSONUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Default formatter – produces pretty-printed JSON identical to the previous hardcoded behaviour.
 */
public class JsonCliOutputFormatter implements CliOutputFormatter {

    @Override
    public String formatResult(Object result) {
        if (result == null) {
            JSONObject response = new JSONObject();
            response.put("success", true);
            return response.toString(2);
        }

        String serialized = JSONUtils.serializeResult(result);
        if (serialized.trim().startsWith("{")) {
            return serialized;
        }
        JSONObject response = new JSONObject();
        if (serialized.trim().startsWith("[")) {
            response.put("result", new JSONArray(serialized));
        } else {
            response.put("result", result);
        }
        return response.toString(2);
    }

    @Override
    public String formatList(Map<String, Object> toolsList) {
        return new JSONObject(toolsList).toString(2);
    }

    @Override
    public String formatError(String message) {
        JSONObject error = new JSONObject();
        error.put("error", true);
        error.put("message", message);
        return error.toString(2);
    }
}
