// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import java.util.Map;

/**
 * Contract for serialising MCP CLI output to a target format.
 *
 * <p>Implementations exist for the three supported formats:
 * {@link JsonCliOutputFormatter}, {@link ToonCliOutputFormatter}, and
 * {@link MiniCliOutputFormatter}.</p>
 */
public interface CliOutputFormatter {

    /**
     * Formats a successfully executed tool result.
     *
     * @param result raw Java object returned by the tool executor
     * @return formatted string ready to print on stdout
     */
    String formatResult(Object result);

    /**
     * Formats the list-tools response.
     *
     * @param toolsList map produced by {@code MCPSchemaGenerator.generateToolsListResponse()}
     * @return formatted string ready to print on stdout
     */
    String formatList(Map<String, Object> toolsList);

    /**
     * Formats an error response.
     *
     * @param message human-readable error description
     * @return formatted string ready to print on stdout
     */
    String formatError(String message);
}
