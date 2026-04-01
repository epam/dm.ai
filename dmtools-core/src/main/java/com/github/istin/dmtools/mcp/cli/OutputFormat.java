// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

/**
 * Supported CLI output formats.
 *
 * <ul>
 *   <li>{@link #JSON} – pretty-printed JSON (default)</li>
 *   <li>{@link #TOON} – LLM-optimized text format via {@link com.github.istin.dmtools.common.utils.LLMOptimizedJson}</li>
 *   <li>{@link #MINI} – minified/compact JSON without whitespace</li>
 * </ul>
 */
public enum OutputFormat {

    /** Pretty-printed JSON (current default behaviour). */
    JSON("json"),

    /** LLM-optimized "Next key1,key2" text format using LLMOptimizedJson. */
    TOON("toon"),

    /** Minified JSON – valid JSON with no extra whitespace. */
    MINI("mini");

    private final String id;

    OutputFormat(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Resolves a format from its string identifier (case-insensitive).
     * Returns {@link #JSON} for null/empty/unknown values.
     */
    public static OutputFormat fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return JSON;
        }
        for (OutputFormat f : values()) {
            if (f.id.equalsIgnoreCase(value.trim())) {
                return f;
            }
        }
        return JSON;
    }
}
