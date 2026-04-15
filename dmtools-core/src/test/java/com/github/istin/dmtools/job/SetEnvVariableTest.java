// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for set_env_variable() JS binding in JobJavaScriptBridge.
 *
 * The feature allows JS agents to redirect a dmtools property to a different
 * environment variable at runtime. The actual value stays in Java — JS only
 * provides the env var NAME, never the secret value.
 */
@ExtendWith(MockitoExtension.class)
class SetEnvVariableTest {

    @Mock private TrackerClient<?> mockTrackerClient;
    @Mock private AI mockAI;
    @Mock private Confluence mockConfluence;
    @Mock private SourceCode mockSourceCode;

    private JobJavaScriptBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new JobJavaScriptBridge(mockTrackerClient, mockAI, mockConfluence, mockSourceCode, null);
    }

    // ─── Input validation ──────────────────────────────────────────────────────

    @Test
    void rejectsEnvVarNameWithLowercase() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> bridge.setEnvVariable("SOURCE_GITHUB_TOKEN", "mytoken")
        );
        assertTrue(ex.getMessage().contains("only A-Z, 0-9 and _ are allowed"),
            "Error message must describe the constraint, not expose a secret");
    }

    @Test
    void rejectsEnvVarNameWithSpecialCharacters() {
        assertThrows(
            IllegalArgumentException.class,
            () -> bridge.setEnvVariable("SOURCE_GITHUB_TOKEN", "MY-TOKEN")
        );
    }

    @Test
    void rejectsEnvVarNameWithDollarSign() {
        assertThrows(
            IllegalArgumentException.class,
            () -> bridge.setEnvVariable("SOURCE_GITHUB_TOKEN", "$SECRET")
        );
    }

    @Test
    void rejectsEnvVarNameWithSpaces() {
        assertThrows(
            IllegalArgumentException.class,
            () -> bridge.setEnvVariable("SOURCE_GITHUB_TOKEN", "MY TOKEN")
        );
    }

    // ─── Missing / unset env var ───────────────────────────────────────────────

    @Test
    void throwsSafeErrorWhenEnvVarNotSet() {
        // DMTOOLS_NONEXISTENT_VAR_XYZ should not be set in any test environment
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> bridge.setEnvVariable("SOURCE_GITHUB_TOKEN", "DMTOOLS_NONEXISTENT_VAR_XYZ")
        );
        // Error message must mention the var name but must NOT contain any token value
        assertTrue(ex.getMessage().contains("DMTOOLS_NONEXISTENT_VAR_XYZ"),
            "Error should name the missing variable");
        assertFalse(ex.getMessage().toLowerCase().contains("ghp_"),
            "Error must not leak token values");
    }

    // ─── JS execution ─────────────────────────────────────────────────────────

    @Test
    void jsCallsSetEnvVariableWithBadNameAndReceivesError() throws Exception {
        // JS agent tries to pass a lowercase env var name → should throw
        String js = """
            function action(params) {
                try {
                    set_env_variable("SOURCE_GITHUB_TOKEN", "bad_name");
                    return "no_error";
                } catch (e) {
                    return "error:" + e.message;
                }
            }
            """;
        org.json.JSONObject jsParams = new org.json.JSONObject();
        Object result = bridge.executeJavaScript(js, jsParams);
        String resultStr = result != null ? result.toString() : "";
        assertTrue(resultStr.startsWith("error:"), "JS should catch the validation error");
        assertFalse(resultStr.toLowerCase().contains("ghp_"),
            "Error returned to JS must not contain token values");
    }

    @Test
    void jsCallsSetEnvVariableWithMissingVarAndReceivesError() throws Exception {
        String js = """
            function action(params) {
                try {
                    set_env_variable("SOURCE_GITHUB_TOKEN", "DMTOOLS_NONEXISTENT_VAR_XYZ");
                    return "no_error";
                } catch (e) {
                    return "error:" + e.message;
                }
            }
            """;
        org.json.JSONObject jsParams = new org.json.JSONObject();
        Object result = bridge.executeJavaScript(js, jsParams);
        String resultStr = result != null ? result.toString() : "";
        assertTrue(resultStr.startsWith("error:"), "JS should catch the missing-var error");
    }
}
