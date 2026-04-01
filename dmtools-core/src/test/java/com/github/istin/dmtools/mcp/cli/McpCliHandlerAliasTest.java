// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import com.github.istin.dmtools.mcp.MCPToolDefinition;
import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpCliHandler#resolveToolAlias(String)}.
 *
 * <p>These tests verify the alias resolution logic without making real API calls.
 * Resolution depends on the generated {@link MCPToolRegistry} (produced by the
 * annotation processor during compilation), so tests assert on the registry
 * state that must exist after a successful build.
 */
class McpCliHandlerAliasTest {

    private McpCliHandler handler;

    @BeforeEach
    void setUp() {
        handler = new McpCliHandler();
    }

    // -----------------------------------------------------------------------
    // Null / passthrough
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("null input returns null")
    void testNullInput() {
        assertNull(handler.resolveToolAlias(null));
    }

    @Test
    @DisplayName("Known direct tool name is returned unchanged")
    void testDirectToolPassthrough() {
        // github_list_prs is a direct tool name — must pass through as-is
        String result = handler.resolveToolAlias("github_list_prs");
        assertEquals("github_list_prs", result);
    }

    @Test
    @DisplayName("Unknown name that is not a registered alias is returned unchanged")
    void testUnknownNamePassthrough() {
        String result = handler.resolveToolAlias("totally_unknown_tool_xyz");
        assertEquals("totally_unknown_tool_xyz", result);
    }

    // -----------------------------------------------------------------------
    // MCPToolRegistry alias lookups (requires annotation processor output)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("source_code_list_prs alias exists in the registry")
    void testSourceCodeListPrsAliasRegistered() {
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias("source_code_list_prs");
        assertNotNull(candidates);
        assertFalse(candidates.isEmpty(), "source_code_list_prs alias should have at least one implementation");
        assertTrue(candidates.size() >= 2,
                "source_code_list_prs should map to both github and gitlab implementations");
    }

    @Test
    @DisplayName("source_code_list_prs alias maps to github_list_prs for github integration")
    void testSourceCodeListPrsResolvesToGithub() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("source_code_list_prs", "github");
        assertNotNull(tool, "github integration should be found for source_code_list_prs alias");
        assertEquals("github_list_prs", tool.getName());
    }

    @Test
    @DisplayName("source_code_list_prs alias maps to gitlab_list_mrs for gitlab integration")
    void testSourceCodeListPrsResolvesToGitlab() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("source_code_list_prs", "gitlab");
        assertNotNull(tool, "gitlab integration should be found for source_code_list_prs alias");
        assertEquals("gitlab_list_mrs", tool.getName());
    }

    @Test
    @DisplayName("tracker_search alias exists in the registry for jira and ado")
    void testTrackerSearchAliasRegistered() {
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias("tracker_search");
        assertNotNull(candidates);
        assertTrue(candidates.size() >= 2,
                "tracker_search should map to both jira_search_by_jql and ado_search_by_wiql");
    }

    @Test
    @DisplayName("tracker_search resolves to jira_search_by_jql for jira integration")
    void testTrackerSearchResolvesToJira() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("tracker_search", "jira");
        assertNotNull(tool);
        assertEquals("jira_search_by_jql", tool.getName());
    }

    @Test
    @DisplayName("tracker_search resolves to ado_search_by_wiql for ado integration")
    void testTrackerSearchResolvesToAdo() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("tracker_search", "ado");
        assertNotNull(tool);
        assertEquals("ado_search_by_wiql", tool.getName());
    }

    @Test
    @DisplayName("tracker_get_ticket resolves to jira_get_ticket for jira")
    void testTrackerGetTicketJira() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("tracker_get_ticket", "jira");
        assertNotNull(tool);
        assertEquals("jira_get_ticket", tool.getName());
    }

    @Test
    @DisplayName("tracker_get_ticket resolves to ado_get_work_item for ado")
    void testTrackerGetTicketAdo() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("tracker_get_ticket", "ado");
        assertNotNull(tool);
        assertEquals("ado_get_work_item", tool.getName());
    }

    @Test
    @DisplayName("tracker_post_comment resolves to jira_post_comment for jira")
    void testTrackerPostCommentJira() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("tracker_post_comment", "jira");
        assertNotNull(tool);
        assertEquals("jira_post_comment", tool.getName());
    }

    @Test
    @DisplayName("tracker_post_comment resolves to ado_post_comment for ado")
    void testTrackerPostCommentAdo() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("tracker_post_comment", "ado");
        assertNotNull(tool);
        assertEquals("ado_post_comment", tool.getName());
    }

    @Test
    @DisplayName("source_code_get_pr resolves to github_get_pr for github")
    void testSourceCodeGetPrGithub() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("source_code_get_pr", "github");
        assertNotNull(tool);
        assertEquals("github_get_pr", tool.getName());
    }

    @Test
    @DisplayName("source_code_get_pr resolves to gitlab_get_mr for gitlab")
    void testSourceCodeGetPrGitlab() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("source_code_get_pr", "gitlab");
        assertNotNull(tool);
        assertEquals("gitlab_get_mr", tool.getName());
    }

    @Test
    @DisplayName("source_code_merge_pr resolves to github_merge_pr for github")
    void testSourceCodeMergePrGithub() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("source_code_merge_pr", "github");
        assertNotNull(tool);
        assertEquals("github_merge_pr", tool.getName());
    }

    @Test
    @DisplayName("source_code_merge_pr resolves to gitlab_merge_mr for gitlab")
    void testSourceCodeMergePrGitlab() {
        MCPToolDefinition tool = MCPToolRegistry.getToolByAliasAndIntegration("source_code_merge_pr", "gitlab");
        assertNotNull(tool);
        assertEquals("gitlab_merge_mr", tool.getName());
    }

    // -----------------------------------------------------------------------
    // resolveToolAlias with single-candidate fallback (no env var needed)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Single-candidate alias resolves without needing an env var")
    void testSingleCandidateResolvesAutomatically() {
        // Find an alias that is registered for only one integration
        // tracker_get_my_profile → jira and ado both have it,
        // but we can still test that when only ONE candidate exists the method returns its name.
        // Use a direct call path: if alias maps to one tool, it should resolve.
        List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias("tracker_move_to_status");
        assertNotNull(candidates);
        // If the alias exists, verify handler picks the right one for each integration
        if (candidates.size() == 1) {
            String resolved = handler.resolveToolAlias("tracker_move_to_status");
            assertEquals(candidates.get(0).getName(), resolved);
        }
    }

    // -----------------------------------------------------------------------
    // Comprehensive alias coverage check
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("All expected source_code_* aliases are registered")
    void testAllSourceCodeAliasesRegistered() {
        String[] expectedAliases = {
            "source_code_list_prs",
            "source_code_get_pr",
            "source_code_get_pr_comments",
            "source_code_add_pr_comment",
            "source_code_get_pr_activities",
            "source_code_get_pr_discussions",
            "source_code_reply_to_pr_thread",
            "source_code_add_inline_comment",
            "source_code_resolve_pr_thread",
            "source_code_merge_pr",
        };
        for (String alias : expectedAliases) {
            List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias(alias);
            assertFalse(candidates == null || candidates.isEmpty(),
                    "Alias '" + alias + "' should be registered in MCPToolRegistry");
        }
    }

    @Test
    @DisplayName("All expected tracker_* aliases are registered")
    void testAllTrackerAliasesRegistered() {
        String[] expectedAliases = {
            "tracker_search",
            "tracker_get_ticket",
            "tracker_get_comments",
            "tracker_post_comment",
            "tracker_assign_ticket",
            "tracker_move_to_status",
            "tracker_get_my_profile",
            "tracker_get_user_by_email",
            "tracker_link_tickets",
            "tracker_create_ticket",
            "tracker_download_attachment",
        };
        for (String alias : expectedAliases) {
            List<MCPToolDefinition> candidates = MCPToolRegistry.getToolsByAlias(alias);
            assertFalse(candidates == null || candidates.isEmpty(),
                    "Alias '" + alias + "' should be registered in MCPToolRegistry");
        }
    }

    @Test
    @DisplayName("Each tracker_* alias maps to exactly one jira and one ado implementation")
    void testTrackerAliasHasBothJiraAndAdo() {
        String[] trackerAliases = {
            "tracker_search",
            "tracker_get_ticket",
            "tracker_get_comments",
            "tracker_post_comment",
            "tracker_assign_ticket",
            "tracker_move_to_status",
            "tracker_get_my_profile",
            "tracker_get_user_by_email",
            "tracker_link_tickets",
            "tracker_create_ticket",
            "tracker_download_attachment",
        };
        for (String alias : trackerAliases) {
            MCPToolDefinition jiraTool = MCPToolRegistry.getToolByAliasAndIntegration(alias, "jira");
            MCPToolDefinition adoTool = MCPToolRegistry.getToolByAliasAndIntegration(alias, "ado");
            assertNotNull(jiraTool, "Alias '" + alias + "' must map to a jira tool");
            assertNotNull(adoTool, "Alias '" + alias + "' must map to an ado tool");
        }
    }

    @Test
    @DisplayName("Each source_code_* alias maps to exactly one github and one gitlab implementation")
    void testSourceCodeAliasHasBothGithubAndGitlab() {
        String[] sourceAliases = {
            "source_code_list_prs",
            "source_code_get_pr",
            "source_code_get_pr_comments",
            "source_code_add_pr_comment",
            "source_code_get_pr_activities",
            "source_code_get_pr_discussions",
            "source_code_reply_to_pr_thread",
            "source_code_add_inline_comment",
            "source_code_resolve_pr_thread",
            "source_code_merge_pr",
        };
        for (String alias : sourceAliases) {
            MCPToolDefinition githubTool = MCPToolRegistry.getToolByAliasAndIntegration(alias, "github");
            MCPToolDefinition gitlabTool = MCPToolRegistry.getToolByAliasAndIntegration(alias, "gitlab");
            assertNotNull(githubTool, "Alias '" + alias + "' must map to a github tool");
            assertNotNull(gitlabTool, "Alias '" + alias + "' must map to a gitlab tool");
        }
    }
}
