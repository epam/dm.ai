// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp;

import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared resolution of tool aliases ({@code tracker_*}, {@code source_code_*}) to
 * canonical vendor tool names. Used by both the CLI entry point
 * ({@code McpCliHandler}) and the JS agent bridge ({@code JobJavaScriptBridge}),
 * so agent scripts written against the vendor-agnostic family run unchanged on
 * Jira, ADO Boards, or GitHub Issues backends.
 *
 * <p>Routing semantics (in order):
 * <ol>
 *   <li>An explicit GitHub key format ({@code gh-123}, {@code owner/repo#123})
 *       always routes to GitHub — it is not a valid key anywhere else.</li>
 *   <li>A single registered candidate wins trivially.</li>
 *   <li>{@code DEFAULT_TRACKER} (for {@code tracker_*}) / {@code DEFAULT_SOURCE_CODE}
 *       (for {@code source_code_*}) from the property chain selects the vendor.</li>
 *   <li>Key-format detection: {@code PROJ-123} → Jira, bare integer → ADO.</li>
 *   <li>Fallback: the first registered candidate.</li>
 * </ol>
 */
public final class ToolAliasResolver {

    private static final Logger logger = LogManager.getLogger(ToolAliasResolver.class);

    /** {@code gh-123} or composite {@code owner/repo#123} — unambiguously GitHub. */
    private static final Pattern GITHUB_KEY = Pattern.compile(
            "(?:gh-\\d+|[\\w.-]+/[\\w.-]+#\\d+)", Pattern.CASE_INSENSITIVE);
    /** Classic Jira key: {@code PROJ-123}. */
    private static final Pattern JIRA_KEY = Pattern.compile("[A-Z][A-Z0-9]+-\\d+");
    /** Bare numeric id — ADO work item. */
    private static final Pattern ADO_KEY = Pattern.compile("\\d+");

    private ToolAliasResolver() {
    }

    /**
     * Resolves a tool alias to the canonical tool name.
     *
     * @param toolName the raw tool name (may be an alias)
     * @return the resolved canonical tool name, or the original if no alias matched
     */
    public static String resolve(String toolName) {
        return resolve(toolName, null, new PropertyReader());
    }

    /**
     * Resolves a tool alias to the canonical tool name, with an optional key hint
     * used for vendor detection (issue/ticket key carried by the call arguments).
     *
     * @param toolName the raw tool name (may be an alias)
     * @param keyHint  the issue/ticket key from the call arguments, or {@code null}
     * @param config   property source for DEFAULT_TRACKER / DEFAULT_SOURCE_CODE
     * @return the resolved canonical tool name, or the original if no alias matched
     */
    public static String resolve(String toolName, String keyHint, PropertyReader config) {
        if (toolName == null) {
            return null;
        }
        // Already a direct tool — no resolution needed
        if (MCPToolRegistry.hasTool(toolName)) {
            return toolName;
        }
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias(toolName);
        if (candidates == null || candidates.isEmpty()) {
            return toolName;
        }

        // 1. Explicit GitHub key format always routes to GitHub.
        if (keyHint != null && GITHUB_KEY.matcher(keyHint.trim()).matches()) {
            MCPToolDefinition gh = byIntegration(candidates, "github");
            if (gh != null) {
                logger.debug("Resolved alias '{}' -> '{}' via GitHub key format '{}'",
                        toolName, gh.getName(), keyHint);
                return gh.getName();
            }
        }

        // 2. Single candidate needs no disambiguation.
        if (candidates.size() == 1) {
            return candidates.get(0).getName();
        }

        // 3. DEFAULT_TRACKER / DEFAULT_SOURCE_CODE from the property chain.
        String defaultIntegration = defaultIntegrationFor(toolName, config);
        if (defaultIntegration != null) {
            MCPToolDefinition matched = byIntegration(candidates, defaultIntegration);
            if (matched != null) {
                logger.debug("Resolved alias '{}' -> '{}' via default integration '{}'",
                        toolName, matched.getName(), defaultIntegration);
                return matched.getName();
            }
        }

        // 4. Key-format detection: Jira-style key, then bare ADO integer id.
        if (keyHint != null) {
            String key = keyHint.trim();
            if (JIRA_KEY.matcher(key).matches()) {
                MCPToolDefinition jira = byIntegration(candidates, "jira");
                if (jira != null) {
                    logger.debug("Resolved alias '{}' -> '{}' via Jira key format '{}'",
                            toolName, jira.getName(), keyHint);
                    return jira.getName();
                }
            } else if (ADO_KEY.matcher(key).matches()) {
                MCPToolDefinition ado = byIntegration(candidates, "ado");
                if (ado != null) {
                    logger.debug("Resolved alias '{}' -> '{}' via ADO integer id '{}'",
                            toolName, ado.getName(), keyHint);
                    return ado.getName();
                }
            }
        }

        // 5. Fallback: first candidate (existing behavior).
        String resolved = candidates.get(0).getName();
        logger.debug("No routing signal for alias '{}'. Falling back to first candidate: '{}'",
                toolName, resolved);
        return resolved;
    }

    private static MCPToolDefinition byIntegration(List<MCPToolDefinition> candidates, String integration) {
        for (MCPToolDefinition candidate : candidates) {
            if (integration.equals(candidate.getIntegration())) {
                return candidate;
            }
        }
        return null;
    }

    private static String defaultIntegrationFor(String alias, PropertyReader config) {
        String key = null;
        if (alias.startsWith("tracker_")) {
            key = PropertyReader.DEFAULT_TRACKER;
        } else if (alias.startsWith("source_code_")) {
            key = "DEFAULT_SOURCE_CODE";
        }
        if (key == null || config == null) {
            return null;
        }
        String value = config.getValue(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
