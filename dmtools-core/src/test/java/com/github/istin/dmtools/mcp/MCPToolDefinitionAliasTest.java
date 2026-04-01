// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for tool-level alias support in {@link MCPToolDefinition}.
 */
class MCPToolDefinitionAliasTest {

    @Test
    @DisplayName("Tool without aliases returns empty array")
    void testNoAliases() {
        MCPToolDefinition tool = buildTool("github_list_prs", "github", new String[0]);
        assertArrayEquals(new String[0], tool.getToolAliases());
    }

    @Test
    @DisplayName("Tool with null aliases defaults to empty array")
    void testNullAliasesDefaultsToEmpty() {
        MCPToolDefinition tool = buildTool("github_list_prs", "github", null);
        assertNotNull(tool.getToolAliases());
        assertEquals(0, tool.getToolAliases().length);
    }

    @Test
    @DisplayName("Tool with one alias stores it correctly")
    void testSingleAlias() {
        MCPToolDefinition tool = buildTool("github_list_prs", "github",
                new String[]{"source_code_list_prs"});
        assertArrayEquals(new String[]{"source_code_list_prs"}, tool.getToolAliases());
    }

    @Test
    @DisplayName("Tool with multiple aliases stores all of them")
    void testMultipleAliases() {
        MCPToolDefinition tool = buildTool("gitlab_list_mrs", "gitlab",
                new String[]{"source_code_list_prs", "list_prs"});
        assertEquals(2, tool.getToolAliases().length);
        assertEquals("source_code_list_prs", tool.getToolAliases()[0]);
        assertEquals("list_prs", tool.getToolAliases()[1]);
    }

    @Test
    @DisplayName("Legacy constructor (no aliases) produces empty toolAliases")
    void testLegacyConstructor() {
        MCPToolDefinition tool = new MCPToolDefinition(
                "github_list_prs", "desc", "github", "pull_requests",
                "com.example.GitHub", "listPullRequests", "java.util.List",
                Collections.emptyList()
        );
        assertNotNull(tool.getToolAliases());
        assertEquals(0, tool.getToolAliases().length);
    }

    @Test
    @DisplayName("Two tools with the same alias can coexist as separate definitions")
    void testTwoToolsSameAlias() {
        MCPToolDefinition github = buildTool("github_list_prs", "github",
                new String[]{"source_code_list_prs"});
        MCPToolDefinition gitlab = buildTool("gitlab_list_mrs", "gitlab",
                new String[]{"source_code_list_prs"});

        assertNotEquals(github.getName(), gitlab.getName());
        assertEquals(github.getToolAliases()[0], gitlab.getToolAliases()[0]);
    }

    @Test
    @DisplayName("equals() is based on name, not aliases")
    void testEqualityIgnoresAliases() {
        MCPToolDefinition a = buildTool("github_list_prs", "github", new String[]{"alias_a"});
        MCPToolDefinition b = buildTool("github_list_prs", "github", new String[]{"alias_b"});
        assertEquals(a, b);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private MCPToolDefinition buildTool(String name, String integration, String[] toolAliases) {
        return new MCPToolDefinition(
                name, "description", integration, "category",
                "com.example.Client", "method", "void",
                Collections.emptyList(),
                toolAliases
        );
    }
}
