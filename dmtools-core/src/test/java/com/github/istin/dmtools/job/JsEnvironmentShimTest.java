// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent scripts read their tracker selector with {@code java.lang.System.getenv}, which the
 * restricted JavaScript context must answer without exposing the real {@code java.*} classes.
 */
class JsEnvironmentShimTest {

    private static final Map<String, String> ENV = Map.of(
            "DEFAULT_TRACKER", "github",
            "ADO_PROJECT", "proj",
            "SOURCE_GITHUB_TOKEN", "secret-token");

    private Value eval(String script) {
        try (Context context = Context.newBuilder("js").allowAllAccess(false).build()) {
            context.getBindings("js").putMember("java", JsEnvironmentShim.create(ENV::get));
            Value result = context.eval("js", script);
            // detach the value from the closing context
            return Value.asValue(result.isString() ? result.asString() : result.isNull() ? null : result.toString());
        }
    }

    @Test
    void returnsAReadableSelector() {
        assertEquals("github", eval("java.lang.System.getenv('DEFAULT_TRACKER')").asString());
    }

    @Test
    void doesNotExposeACredential() {
        assertTrue(eval("java.lang.System.getenv('SOURCE_GITHUB_TOKEN')").isNull());
    }

    @Test
    void returnsNullForAnUnsetReadableName() {
        assertTrue(eval("java.lang.System.getenv('JIRA_BASE_PATH')").isNull());
    }

    @Test
    void isDetectableTheWayAgentScriptsProbeIt() {
        assertEquals("true", eval(
                "String(typeof java !== 'undefined' && java && java.lang && java.lang.System"
                        + " && typeof java.lang.System.getenv === 'function')").asString());
    }

    @Test
    void doesNotGrantAccessToOtherHostClasses() {
        assertEquals("undefined", eval("typeof java.lang.Runtime").asString());
    }
}
