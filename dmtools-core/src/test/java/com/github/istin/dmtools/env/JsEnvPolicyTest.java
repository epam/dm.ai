// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.env;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsEnvPolicyTest {

    @Test
    void credentialNamesAreSecret() {
        for (String n : new String[]{"ADO_PAT_TOKEN", "JIRA_API_TOKEN", "OPENAI_API_KEY", "FIGMA_CLIENT_SECRET", "XRAY_CLIENT_SECRET",
                "JIRA_LOGIN_PASS_TOKEN", "GITLAB_TOKEN", "BEDROCK_SECRET_ACCESS_KEY", "BEDROCK_ACCESS_KEY_ID", "AWS_BEARER_TOKEN_BEDROCK",
                "TEAMS_REFRESH_TOKEN", "JIRA_AUTH_TYPE", "SOME_PASSWORD", "MY_PASSPHRASE", "X_SESSION_COOKIE", "DB_PASS", "GITHUB_PAT",
                "CUSTOM_API_KEY", "FOO_SECRET", "SERVICE_CREDENTIALS"}) {
            assertTrue(JsEnvPolicy.looksSecret(n), n + " must be treated as a secret");
        }
    }

    @Test
    void limitsAndNonCredentialsThatMerelyContainTheWordTokenStayReadable() {
        for (String n : new String[]{"OPENAI_MAX_TOKENS", "ANTHROPIC_MAX_TOKENS", "BEDROCK_MAX_TOKENS", "PROMPT_CHUNK_TOKEN_LIMIT",
                "ADO_PROJECT", "ADO_ORGANIZATION", "DEFAULT_TRACKER", "JIRA_BASE_PATH", "JIRA_EMAIL", "FIGMA_CLIENT_ID", "TEAMS_TENANT_ID"}) {
            assertFalse(JsEnvPolicy.looksSecret(n), n + " is configuration, not a credential");
        }
    }

    @Test
    void aWordInsideAnotherWordDoesNotMakeAName_secret() {
        // matching is by whole _-separated words: KEYBOARD / MONKEY / SPATIAL are not secrets
        for (String n : new String[]{"KEYBOARD_LAYOUT", "MONKEY_MODE", "SPATIAL_INDEX", "PASSENGER_COUNT", "AUTHOR_NAME"}) {
            assertFalse(JsEnvPolicy.looksSecret(n), n);
        }
    }

    @Test
    void nullAndBlankAreTreatedAsSecret_failClosed() {
        assertTrue(JsEnvPolicy.looksSecret(null));
    }

    /** The invariant that matters: whatever PropertyReader declares, no credential name ends up readable by default. */
    @Test
    void noDefaultAllowedNameLooksSecret_andEveryCredentialConstantIsExcluded() throws Exception {
        Set<String> allowed = JsEnvPolicy.defaultAllowed();
        for (String n : allowed) {
            assertFalse(JsEnvPolicy.looksSecret(n), "default allowlist contains a secret-looking name: " + n);
        }
        List<String> credentialConstants = new ArrayList<>();
        for (Field f : PropertyReader.class.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String v = (String) f.get(null);
                if (v != null && JsEnvPolicy.looksSecret(v)) credentialConstants.add(v);
            }
        }
        assertTrue(credentialConstants.size() >= 20, "expected PropertyReader to declare many credential names, saw " + credentialConstants);
        for (String secret : credentialConstants) {
            assertFalse(allowed.contains(secret), secret + " must not be in the default allowlist");
            assertEquals(JsEnvPolicy.Decision.SECRET, JsEnvPolicy.decide(secret, null));
            assertEquals(JsEnvPolicy.Decision.SECRET, JsEnvPolicy.decide(secret, secret), "allowlisting a secret must not help: " + secret);
        }
    }

    @Test
    void defaultAllowlistIsDerivedFromPropertyReaderSoItCannotDrift() throws Exception {
        Set<String> allowed = JsEnvPolicy.defaultAllowed();
        for (String expected : new String[]{"ADO_PROJECT", "ADO_ORGANIZATION", "DEFAULT_TRACKER", "JIRA_BASE_PATH", "CONFLUENCE_BASE_PATH",
                "SOURCE_GITHUB_WORKSPACE", "GITLAB_WORKSPACE", "OPENAI_MODEL", "CLI_OUTPUT", JsEnvPolicy.ALLOWLIST_KEY}) {
            assertTrue(allowed.contains(expected), expected);
        }
        // every non-secret constant PropertyReader declares is there
        for (Field f : PropertyReader.class.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String v = (String) f.get(null);
                if (v != null && v.matches("[A-Z][A-Z0-9_]*") && !JsEnvPolicy.looksSecret(v)) {
                    assertTrue(allowed.contains(v), "PropertyReader declares " + v + " but the policy does not allow it");
                }
            }
        }
    }

    @Test
    void projectAllowlistAddsNamesButNeverSecrets() {
        assertEquals(Set.of("MY_TEAM_NAME", "REGION"), JsEnvPolicy.projectAllowed("my_team_name, REGION  MY_API_KEY,db_password"));
        assertEquals(Set.of("MY_API_KEY", "DB_PASSWORD"), JsEnvPolicy.rejectedFromAllowlist("MY_TEAM_NAME,MY_API_KEY, db_password"));
        assertTrue(JsEnvPolicy.projectAllowed(null).isEmpty());
        assertTrue(JsEnvPolicy.projectAllowed("   ").isEmpty());
        assertTrue(JsEnvPolicy.projectAllowed("1BAD,has-dash,ok_name").contains("OK_NAME"));
        assertFalse(JsEnvPolicy.projectAllowed("1BAD,has-dash").contains("1BAD"));
    }

    @Test
    void decisionTable() {
        assertEquals(JsEnvPolicy.Decision.ALLOWED, JsEnvPolicy.decide("ADO_PROJECT", null));
        assertEquals(JsEnvPolicy.Decision.ALLOWED, JsEnvPolicy.decide("ado_project", null), "case-insensitive");
        assertEquals(JsEnvPolicy.Decision.NOT_ALLOWLISTED, JsEnvPolicy.decide("MY_TEAM", null));
        assertEquals(JsEnvPolicy.Decision.ALLOWED, JsEnvPolicy.decide("MY_TEAM", "MY_TEAM"));
        assertEquals(JsEnvPolicy.Decision.SECRET, JsEnvPolicy.decide("ADO_PAT_TOKEN", null));
        assertEquals(JsEnvPolicy.Decision.INVALID_NAME, JsEnvPolicy.decide("a-b", null));
        assertEquals(JsEnvPolicy.Decision.INVALID_NAME, JsEnvPolicy.decide("", null));
        assertEquals(JsEnvPolicy.Decision.INVALID_NAME, JsEnvPolicy.decide(null, null));
        assertEquals(JsEnvPolicy.Decision.INVALID_NAME, JsEnvPolicy.decide("A B", null));
        assertEquals(JsEnvPolicy.Decision.INVALID_NAME, JsEnvPolicy.decide("PATH; rm -rf /", null));
    }
}
