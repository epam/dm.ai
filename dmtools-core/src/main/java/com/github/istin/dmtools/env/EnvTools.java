// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.env;

import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Read-only access to NON-SECRET configuration values for JS agents. The policy (what is readable) lives in one
 * place, {@link JsEnvPolicy}; this class only applies it. The value comes from {@link PropertyReader}, i.e. the
 * same chain every dmtools component uses (config.properties, dmtools.env, environment), so an agent sees exactly
 * what the Java side sees.
 *
 * <p>Both tools answer with a JSON string. A refusal never contains the value and never says whether the variable
 * exists: asking for a secret and asking for something unset look the same except for the reason.
 */
public class EnvTools {

    private final Function<String, String> lookup;

    public EnvTools() {
        this(name -> new PropertyReader().getValue(name));
    }

    /** Test seam: where values come from. */
    public EnvTools(Function<String, String> lookup) {
        this.lookup = lookup;
    }

    @MCPTool(
            name = "env_get",
            description = "Read one non-secret configuration value (for example ADO_PROJECT, DEFAULT_TRACKER, JIRA_BASE_PATH). "
                    + "Credentials (names containing TOKEN, KEY, SECRET, PASSWORD, PAT, AUTH, ...) are never returned. "
                    + "Names outside the built-in list can be allowed with JS_ENV_ALLOWLIST=NAME1,NAME2. "
                    + "Returns {\"name\":..,\"value\":..} or {\"name\":..,\"error\":..}; value is null when the variable is unset.",
            integration = "env"
    )
    public String envGet(
            @MCPParam(name = "name", description = "Configuration variable name", required = true, example = "ADO_PROJECT") String name
    ) {
        JsEnvPolicy.Decision decision = JsEnvPolicy.decide(name, lookup.apply(JsEnvPolicy.ALLOWLIST_KEY));
        JSONObject out = new JSONObject().put("name", name == null ? JSONObject.NULL : name);
        switch (decision) {
            case ALLOWED:
                String value = lookup.apply(name.toUpperCase(java.util.Locale.ROOT));
                return out.put("value", value == null || value.isEmpty() ? JSONObject.NULL : value).toString();
            case SECRET:
                return out.put("error", "not readable: the name looks like a credential").toString();
            case NOT_ALLOWLISTED:
                return out.put("error", "not allowed: add it to JS_ENV_ALLOWLIST to make it readable").toString();
            default:
                return out.put("error", "invalid name: expected letters, digits and underscores").toString();
        }
    }

    @MCPTool(
            name = "env_list",
            description = "List the configuration names env_get will answer for: the built-in non-secret names plus those added "
                    + "through JS_ENV_ALLOWLIST. Names only, never values.",
            integration = "env"
    )
    public String envList() {
        String raw = lookup.apply(JsEnvPolicy.ALLOWLIST_KEY);
        Set<String> all = new java.util.TreeSet<>(JsEnvPolicy.defaultAllowed());
        all.addAll(JsEnvPolicy.projectAllowed(raw));
        JSONObject out = new JSONObject().put("names", new JSONArray(all));
        Set<String> rejected = JsEnvPolicy.rejectedFromAllowlist(raw);
        if (!rejected.isEmpty()) {
            out.put("ignoredSecretLikeNames", new JSONArray(rejected));
        }
        return out.toString();
    }
}
