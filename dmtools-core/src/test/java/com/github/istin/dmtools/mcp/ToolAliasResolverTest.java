// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ToolAliasResolver} — the shared {@code tracker_*}/{@code source_code_*}
 * alias router used by the CLI and the JS agent bridge.
 *
 * <p>Resolution depends on the generated
 * {@code MCPToolRegistry} (annotation processor output), so these tests assert on
 * the alias registrations that must exist after a successful build.
 */
class ToolAliasResolverTest {

    @AfterEach
    void clearOverrides() {
        PropertyReader.clearOverrides();
    }

    private static void defaultTracker(String value) {
        PropertyReader.setOverrides(Map.of(PropertyReader.DEFAULT_TRACKER, value));
    }

    /** Simulates DEFAULT_TRACKER being unset, shielding the test from the ambient shell env. */
    private static void noDefaultTracker() {
        PropertyReader.setOverrides(Map.of(PropertyReader.DEFAULT_TRACKER, ""));
    }

    // -----------------------------------------------------------------------
    // Passthrough
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("null input returns null")
    void testNullInput() {
        assertNull(ToolAliasResolver.resolve(null));
    }

    @Test
    @DisplayName("Known direct tool name is returned unchanged")
    void testDirectToolPassthrough() {
        assertEquals("jira_get_ticket", ToolAliasResolver.resolve("jira_get_ticket"));
    }

    @Test
    @DisplayName("Unknown name that is not a registered alias is returned unchanged")
    void testUnknownNamePassthrough() {
        assertEquals("totally_unknown_tool_xyz", ToolAliasResolver.resolve("totally_unknown_tool_xyz"));
    }

    // -----------------------------------------------------------------------
    // DEFAULT_TRACKER routing
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tracker_get_ticket routes to Jira when DEFAULT_TRACKER=jira")
    void testRoutesToJiraViaDefaultTracker() {
        defaultTracker("jira");
        assertEquals("jira_get_ticket", ToolAliasResolver.resolve("tracker_get_ticket", null, new PropertyReader()));
    }

    @Test
    @DisplayName("tracker_get_ticket routes to ADO when DEFAULT_TRACKER=ado")
    void testRoutesToAdoViaDefaultTracker() {
        defaultTracker("ado");
        assertEquals("ado_get_work_item", ToolAliasResolver.resolve("tracker_get_ticket", null, new PropertyReader()));
    }

    @Test
    @DisplayName("tracker_get_ticket routes to GitHub when DEFAULT_TRACKER=github")
    void testRoutesToGithubViaDefaultTracker() {
        defaultTracker("github");
        assertEquals("github_get_issue", ToolAliasResolver.resolve("tracker_get_ticket", null, new PropertyReader()));
    }

    @Test
    @DisplayName("DEFAULT_TRACKER value is case-insensitive and trimmed")
    void testDefaultTrackerCaseInsensitive() {
        defaultTracker("  ADO  ");
        assertEquals("ado_get_work_item", ToolAliasResolver.resolve("tracker_get_ticket", null, new PropertyReader()));
    }

    // -----------------------------------------------------------------------
    // Key-format detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("gh-prefixed key routes to GitHub even when DEFAULT_TRACKER=jira")
    void testGhPrefixKeyBeatsDefaultTracker() {
        defaultTracker("jira");
        assertEquals("github_get_issue", ToolAliasResolver.resolve("tracker_get_ticket", "gh-42", new PropertyReader()));
    }

    @Test
    @DisplayName("Composite owner/repo#N key routes to GitHub")
    void testCompositeKeyRoutesToGithub() {
        defaultTracker("jira");
        assertEquals("github_get_issue",
                ToolAliasResolver.resolve("tracker_get_ticket", "IstiN/dmtools#42", new PropertyReader()));
    }

    @Test
    @DisplayName("gh-prefixed key detection is case-insensitive")
    void testGhPrefixCaseInsensitive() {
        assertEquals("github_get_issue",
                ToolAliasResolver.resolve("tracker_get_ticket", "GH-42", new PropertyReader()));
    }

    @Test
    @DisplayName("Jira-style key routes to Jira when DEFAULT_TRACKER is unset")
    void testJiraKeyFormatDetection() {
        noDefaultTracker();
        assertEquals("jira_get_ticket",
                ToolAliasResolver.resolve("tracker_get_ticket", "PROJ-123", new PropertyReader()));
    }

    @Test
    @DisplayName("Bare integer id routes to ADO when DEFAULT_TRACKER is unset")
    void testAdoIntegerKeyDetection() {
        noDefaultTracker();
        assertEquals("ado_get_work_item",
                ToolAliasResolver.resolve("tracker_get_ticket", "12345", new PropertyReader()));
    }

    @Test
    @DisplayName("Key hint wins over DEFAULT_TRACKER for an explicit other-vendor format")
    void testKeyHintExplicitWins() {
        defaultTracker("ado");
        // gh-123 is not a valid ADO id — explicit GitHub format wins
        assertEquals("github_get_issue", ToolAliasResolver.resolve("tracker_get_ticket", "gh-7", new PropertyReader()));
    }

    // -----------------------------------------------------------------------
    // Fallback
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Multi-candidate alias without any signal falls back to first candidate")
    void testFallbackToFirstCandidate() {
        // No routing signal — deterministic first-candidate fallback
        noDefaultTracker();
        String resolved = ToolAliasResolver.resolve("tracker_get_ticket", null, new PropertyReader());
        // First registered candidate for tracker_get_ticket is ado_get_work_item
        // (insertion order of the generated registry)
        assertEquals("ado_get_work_item", resolved);
    }

    @Test
    @DisplayName("DEFAULT_TRACKER with unknown value falls back to first candidate")
    void testUnknownDefaultTrackerFallback() {
        defaultTracker("nonexistent");
        assertEquals("ado_get_work_item",
                ToolAliasResolver.resolve("tracker_get_ticket", null, new PropertyReader()));
    }

    // -----------------------------------------------------------------------
    // Label/assign aliases registration
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tracker_add_label is registered for jira, ado, and github")
    void testTrackerAddLabelRegistered() {
        assertEquals("jira_add_label",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_add_label", "jira").getName());
        assertEquals("ado_add_work_item_label",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_add_label", "ado").getName());
        assertEquals("github_add_labels",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_add_label", "github").getName());
    }

    @Test
    @DisplayName("tracker_remove_label is registered for jira, ado, and github")
    void testTrackerRemoveLabelRegistered() {
        assertEquals("jira_remove_label",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_remove_label", "jira").getName());
        assertEquals("ado_remove_work_item_label",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_remove_label", "ado").getName());
        assertEquals("github_remove_label",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_remove_label", "github").getName());
    }

    @Test
    @DisplayName("tracker_assign is registered alongside tracker_assign_ticket")
    void testTrackerAssignRegistered() {
        assertEquals("jira_assign_ticket_to",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_assign", "jira").getName());
        assertEquals("ado_assign_work_item",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_assign", "ado").getName());
        assertEquals("github_assign_issue",
                com.github.istin.dmtools.mcp.generated.MCPToolRegistry
                        .getToolByAliasAndIntegration("tracker_assign", "github").getName());
    }

    @Test
    @DisplayName("tracker_assign routes via DEFAULT_TRACKER")
    void testTrackerAssignRouting() {
        defaultTracker("github");
        assertEquals("github_assign_issue",
                ToolAliasResolver.resolve("tracker_assign", null, new PropertyReader()));
        defaultTracker("jira");
        assertEquals("jira_assign_ticket_to",
                ToolAliasResolver.resolve("tracker_assign", null, new PropertyReader()));
    }
}
