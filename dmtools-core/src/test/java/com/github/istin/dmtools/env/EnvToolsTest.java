// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.env;

import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvToolsTest {

    private static final String SECRET_VALUE = "s3cr3t-value-that-must-never-leak";

    private EnvTools tools(Map<String, String> values) {
        return new EnvTools(values::get);
    }

    private Map<String, String> config() {
        Map<String, String> m = new HashMap<>();
        m.put("ADO_PROJECT", "ai-native-sdlc-blueprint");
        m.put("DEFAULT_TRACKER", "ado");
        m.put("ADO_PAT_TOKEN", SECRET_VALUE);
        m.put("JIRA_API_TOKEN", SECRET_VALUE);
        m.put("MY_TEAM", "platform");
        m.put("OPENAI_MAX_TOKENS", "4096");
        return m;
    }

    @Test
    void anAllowedNameReturnsItsValue() {
        JSONObject r = new JSONObject(tools(config()).envGet("ADO_PROJECT"));
        assertEquals("ai-native-sdlc-blueprint", r.getString("value"));
        assertEquals("ADO_PROJECT", r.getString("name"));
        assertFalse(r.has("error"));
    }

    @Test
    void nameMatchingIsCaseInsensitive() {
        assertEquals("ado", new JSONObject(tools(config()).envGet("default_tracker")).getString("value"));
    }

    @Test
    void aTokenLimitIsReadableBecauseItIsConfigurationNotACredential() {
        assertEquals("4096", new JSONObject(tools(config()).envGet("OPENAI_MAX_TOKENS")).getString("value"));
    }

    @Test
    void aSecretIsRefusedAndTheValueNeverAppearsAnywhereInTheAnswer() {
        for (String name : new String[]{"ADO_PAT_TOKEN", "JIRA_API_TOKEN", "ado_pat_token"}) {
            String answer = tools(config()).envGet(name);
            JSONObject r = new JSONObject(answer);
            assertTrue(r.has("error"), name);
            assertFalse(r.has("value"), name);
            assertFalse(answer.contains(SECRET_VALUE), "the secret value leaked for " + name + ": " + answer);
        }
    }

    @Test
    void allowlistingASecretDoesNotMakeItReadable() {
        Map<String, String> m = config();
        m.put("JS_ENV_ALLOWLIST", "ADO_PAT_TOKEN, MY_TEAM");
        String answer = tools(m).envGet("ADO_PAT_TOKEN");
        assertFalse(answer.contains(SECRET_VALUE), answer);
        assertTrue(new JSONObject(answer).has("error"));
        assertEquals("platform", new JSONObject(tools(m).envGet("MY_TEAM")).getString("value"), "the non-secret name in the same list works");
    }

    @Test
    void aNameOutsideTheListIsRefusedUntilAllowlisted() {
        Map<String, String> m = config();
        JSONObject refused = new JSONObject(tools(m).envGet("MY_TEAM"));
        assertTrue(refused.getString("error").contains("JS_ENV_ALLOWLIST"));
        assertFalse(refused.has("value"));
        m.put("JS_ENV_ALLOWLIST", "MY_TEAM");
        assertEquals("platform", new JSONObject(tools(m).envGet("MY_TEAM")).getString("value"));
    }

    @Test
    void anUnsetAllowedNameIsNullNotAnError() {
        JSONObject r = new JSONObject(tools(new HashMap<>()).envGet("ADO_PROJECT"));
        assertTrue(r.isNull("value"));
        assertFalse(r.has("error"));
    }

    @Test
    void invalidNamesAreRejectedWithoutALookup() {
        boolean[] looked = {false};
        EnvTools t = new EnvTools(n -> {
            looked[0] = true;
            return null;
        });
        for (String bad : new String[]{"", null, "a b", "A;B", "../x", "$(id)"}) {
            JSONObject r = new JSONObject(t.envGet(bad));
            assertTrue(r.getString("error").startsWith("invalid name"), String.valueOf(bad));
        }
        // the only lookup allowed is JS_ENV_ALLOWLIST itself (to evaluate the policy), never the requested name
        assertTrue(looked[0]);
    }

    @Test
    void listReturnsNamesOnlyNeverValues_andReportsIgnoredSecretLikeEntries() {
        Map<String, String> m = config();
        m.put("JS_ENV_ALLOWLIST", "MY_TEAM, MY_API_KEY");
        String answer = tools(m).envList();
        JSONObject r = new JSONObject(answer);
        JSONArray names = r.getJSONArray("names");
        assertTrue(names.toList().contains("ADO_PROJECT"));
        assertTrue(names.toList().contains("MY_TEAM"));
        assertFalse(names.toList().contains("ADO_PAT_TOKEN"));
        assertFalse(names.toList().contains("MY_API_KEY"));
        assertEquals("MY_API_KEY", r.getJSONArray("ignoredSecretLikeNames").getString(0));
        assertFalse(answer.contains("ai-native-sdlc-blueprint"), "list must not contain any value");
        assertFalse(answer.contains(SECRET_VALUE));
    }

    /** Sweep: for every credential name PropertyReader knows, asking for it never yields the value. */
    @Test
    void sweepEveryCredentialConstantNeverLeaks() throws Exception {
        Map<String, String> m = new HashMap<>();
        java.util.List<String> secrets = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : com.github.istin.dmtools.common.utils.PropertyReader.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isPublic(f.getModifiers()) && java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String v = (String) f.get(null);
                if (v != null && JsEnvPolicy.looksSecret(v) && v.matches("[A-Z][A-Z0-9_]*")) {
                    secrets.add(v);
                    m.put(v, SECRET_VALUE);
                }
            }
        }
        m.put("JS_ENV_ALLOWLIST", String.join(",", secrets)); // the worst case: everything allowlisted
        assertTrue(secrets.size() >= 20);
        for (String s : secrets) {
            String answer = tools(m).envGet(s);
            assertFalse(answer.contains(SECRET_VALUE), "leaked " + s);
        }
        assertFalse(tools(m).envList().contains(SECRET_VALUE));
    }

    @Test
    void toolsAreRegisteredUnderTheEnvIntegration() {
        assertNotNull(MCPToolRegistry.getTool("env_get"));
        assertNotNull(MCPToolRegistry.getTool("env_list"));
        assertEquals("env", MCPToolRegistry.getTool("env_get").getIntegration());
    }
}
