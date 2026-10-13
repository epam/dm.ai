// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.env;

import com.github.istin.dmtools.common.utils.PropertyReader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The single source of truth for WHICH configuration values a JS agent may read.
 *
 * <p>Rules, in this order:
 * <ol>
 *   <li>A name that looks like a secret is NEVER readable, whoever asks and whatever the allowlist says. The
 *       test is on whole {@code _}-separated words (TOKEN, KEY, SECRET, PASSWORD, PAT, AUTH, ...), so
 *       {@code OPENAI_MAX_TOKENS} and {@code PROMPT_CHUNK_TOKEN_LIMIT} stay readable while
 *       {@code JIRA_API_TOKEN} and {@code ADO_PAT_TOKEN} do not.</li>
 *   <li>The default allowlist is every configuration name {@link PropertyReader} itself declares (its
 *       {@code public static final String} constants, found by reflection), minus the secrets. A new property
 *       added to PropertyReader is therefore picked up without touching this class, and a new SECRET added there is
 *       blocked by rule 1 without touching it either.</li>
 *   <li>A project extends the list with {@code JS_ENV_ALLOWLIST=A,B,C} (env var or dmtools.env). Rule 1 still
 *       applies to those names, so a secret cannot be allowlisted by mistake.</li>
 * </ol>
 *
 * <p>This keeps accidents and careless scripts from leaking credentials. It is NOT a sandbox against a hostile
 * script: a script that can run arbitrary shell commands can read the environment another way, so
 * {@code CLI_ALLOWED_COMMANDS} must stay tight.
 */
public final class JsEnvPolicy {

    /** Name of the variable that extends the allowlist. Itself readable (it is not a secret). */
    public static final String ALLOWLIST_KEY = "JS_ENV_ALLOWLIST";

    private static final Set<String> SECRET_WORDS = Set.of(
            "TOKEN", "TOKENS", "PAT", "KEY", "KEYS", "SECRET", "SECRETS", "PASSWORD", "PASSWORDS", "PASSWD", "PASS",
            "CREDENTIAL", "CREDENTIALS", "AUTH", "AUTHORIZATION", "LOGIN", "PRIVATE", "CERT", "CERTIFICATE",
            "SIGNATURE", "COOKIE", "COOKIES", "SESSION", "BEARER", "PASSPHRASE", "APIKEY");

    private static volatile Set<String> defaults;

    private JsEnvPolicy() {
    }

    /** True when the name looks like a secret. Pure function of the name. */
    public static boolean looksSecret(String name) {
        if (name == null) {
            return true;
        }
        String[] words = name.trim().toUpperCase(Locale.ROOT).split("_");
        java.util.List<String> w = Arrays.asList(words);
        // limits and counters that merely contain the word "TOKEN(S)" are configuration, not credentials
        if (w.contains("MAX") && (w.contains("TOKENS") || w.contains("TOKEN"))) {
            return false;
        }
        if (w.contains("TOKEN") && (w.contains("LIMIT") || w.contains("LIMITS") || w.contains("COUNT"))) {
            return false;
        }
        for (String word : words) {
            if (SECRET_WORDS.contains(word)) {
                return true;
            }
            if (word.endsWith("TOKEN") || word.endsWith("SECRET") || word.endsWith("PASSWORD") || word.endsWith("APIKEY")) {
                return true;
            }
        }
        return false;
    }

    /** The names PropertyReader declares, minus the secrets: the built-in allowlist. */
    public static Set<String> defaultAllowed() {
        Set<String> local = defaults;
        if (local == null) {
            synchronized (JsEnvPolicy.class) {
                local = defaults;
                if (local == null) {
                    Set<String> names = new TreeSet<>();
                    for (Field f : PropertyReader.class.getDeclaredFields()) {
                        int m = f.getModifiers();
                        if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == String.class) {
                            try {
                                String value = (String) f.get(null);
                                if (value != null && value.matches("[A-Z][A-Z0-9_]*") && !looksSecret(value)) {
                                    names.add(value);
                                }
                            } catch (IllegalAccessException ignored) {
                                // not readable: not a configuration name we can list
                            }
                        }
                    }
                    names.add(ALLOWLIST_KEY);
                    defaults = Collections.unmodifiableSet(names);
                    local = defaults;
                }
            }
        }
        return local;
    }

    /** Names added by the project through JS_ENV_ALLOWLIST (comma/space separated). Secrets are dropped. */
    public static Set<String> projectAllowed(String rawAllowlist) {
        Set<String> out = new LinkedHashSet<>();
        if (rawAllowlist == null) {
            return out;
        }
        for (String part : rawAllowlist.split("[,\\s]+")) {
            String n = part.trim().toUpperCase(Locale.ROOT);
            if (n.matches("[A-Z][A-Z0-9_]*") && !looksSecret(n)) {
                out.add(n);
            }
        }
        return out;
    }

    /** The names from JS_ENV_ALLOWLIST that were rejected because they look like secrets (for a clear log line). */
    public static Set<String> rejectedFromAllowlist(String rawAllowlist) {
        Set<String> out = new LinkedHashSet<>();
        if (rawAllowlist == null) {
            return out;
        }
        for (String part : rawAllowlist.split("[,\\s]+")) {
            String n = part.trim().toUpperCase(Locale.ROOT);
            if (!n.isEmpty() && n.matches("[A-Z][A-Z0-9_]*") && looksSecret(n)) {
                out.add(n);
            }
        }
        return out;
    }

    /** Decision for one name given the project's JS_ENV_ALLOWLIST value. */
    public static Decision decide(String name, String rawProjectAllowlist) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            return Decision.INVALID_NAME;
        }
        String n = name.toUpperCase(Locale.ROOT);
        if (looksSecret(n)) {
            return Decision.SECRET;
        }
        if (defaultAllowed().contains(n) || projectAllowed(rawProjectAllowlist).contains(n)) {
            return Decision.ALLOWED;
        }
        return Decision.NOT_ALLOWLISTED;
    }

    /** Outcome of {@link #decide}. */
    public enum Decision {
        ALLOWED,
        /** looks like a credential: never readable */
        SECRET,
        /** a normal name that is neither built in nor added through JS_ENV_ALLOWLIST */
        NOT_ALLOWLISTED,
        INVALID_NAME
    }
}
