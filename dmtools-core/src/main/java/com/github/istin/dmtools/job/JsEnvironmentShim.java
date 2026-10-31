// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The {@code java.lang.System.getenv(name)} entry point that agent scripts use to read runtime
 * configuration, exposed to the restricted JavaScript context without granting host access.
 *
 * <p>The context is created with {@code allowAllAccess(false)}, so a script cannot reach the real
 * {@code java.*} classes: {@code java.lang.System.getenv('DEFAULT_TRACKER')} threw, the script's
 * {@code try/catch} swallowed it, and {@code js/common/trackers.js} silently fell back to Jira.
 * This shim answers only for {@link #READABLE_NAMES}, which are non-secret selectors, and returns
 * {@code null} for every other name — the same result a script gets for an unset variable.
 */
final class JsEnvironmentShim {

    /** Configuration names a script may read. Never add a credential here. */
    static final Set<String> READABLE_NAMES = Set.of("DEFAULT_TRACKER", "ADO_PROJECT", "JIRA_BASE_PATH");

    private JsEnvironmentShim() {
    }

    /**
     * Builds the object bound as the global {@code java}.
     *
     * @param lookup resolves a configuration name to its value, or {@code null} when it is unset
     */
    static ProxyObject create(Function<String, String> lookup) {
        ProxyExecutable getenv = arguments -> {
            if (arguments.length == 0) {
                return null;
            }
            Value name = arguments[0];
            if (name == null || !name.isString() || !READABLE_NAMES.contains(name.asString())) {
                return null;
            }
            return lookup.apply(name.asString());
        };
        ProxyObject system = ProxyObject.fromMap(Map.of("getenv", getenv));
        ProxyObject lang = ProxyObject.fromMap(Map.of("System", system));
        return ProxyObject.fromMap(Map.of("lang", lang));
    }
}
