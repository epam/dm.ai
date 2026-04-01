// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.common.utils.PropertyReader;

/**
 * Resolves and creates the effective {@link CliOutputFormatter} for a CLI invocation.
 *
 * <h3>Precedence (highest → lowest)</h3>
 * <ol>
 *   <li>Explicit CLI flag ({@code --output &lt;format&gt;}, {@code --toon}, {@code --mini})</li>
 *   <li>Environment variable / dmtools.env / config.properties ({@code CLI_OUTPUT})</li>
 *   <li>Hard-coded default: {@link OutputFormat#JSON}</li>
 * </ol>
 */
public class CliOutputFormatterFactory {

    private CliOutputFormatterFactory() {}

    /**
     * Returns the formatter for the given format, with no explicit CLI override (only config/default).
     */
    public static CliOutputFormatter create() {
        return create(null);
    }

    /**
     * Returns the formatter for an explicit {@code cliFlag} value (may be {@code null} to fall back
     * to config / default).
     *
     * @param cliFlag value of {@code --output} or alias; {@code null} / empty = not provided
     */
    public static CliOutputFormatter create(String cliFlag) {
        OutputFormat format = resolve(cliFlag);
        return fromFormat(format);
    }

    /**
     * Resolves the effective format applying precedence rules.
     *
     * @param cliFlag explicit CLI flag value ({@code null} or empty means "not provided")
     */
    public static OutputFormat resolve(String cliFlag) {
        // 1. CLI flag takes highest priority
        if (cliFlag != null && !cliFlag.trim().isEmpty()) {
            return OutputFormat.fromString(cliFlag);
        }

        // 2. PropertyReader-backed config (env var > dmtools.env > config.properties)
        String configValue = new PropertyReader().getCliOutput();
        if (configValue != null && !configValue.trim().isEmpty()) {
            return OutputFormat.fromString(configValue);
        }

        // 3. Default
        return OutputFormat.JSON;
    }

    /**
     * Instantiates the formatter corresponding to the given format.
     */
    public static CliOutputFormatter fromFormat(OutputFormat format) {
        switch (format) {
            case TOON: return new ToonCliOutputFormatter();
            case MINI: return new MiniCliOutputFormatter();
            case JSON:
            default:  return new JsonCliOutputFormatter();
        }
    }
}
