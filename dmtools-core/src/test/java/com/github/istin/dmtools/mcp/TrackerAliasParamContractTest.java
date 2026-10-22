// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp;

import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One call shape for the unified {@code tracker_*} family (epam/dm.ai alias alignment): for every operation a
 * script can call {@code tracker_x} with ONE set of argument names and have it work on Jira and ADO, whichever
 * backend the alias resolves to. The test pins, per operation, the neutral argument names that BOTH backends
 * must accept (as the real name or as a declared alias), so a rename or a new tool cannot silently reopen the gap.
 */
class TrackerAliasParamContractTest {

    private static Set<String> acceptedNames(String tool) {
        MCPToolDefinition def = MCPToolRegistry.getTool(tool);
        assertNotNull(def, tool + " is not registered");
        Set<String> names = new HashSet<>();
        for (MCPParameterDefinition p : def.getParameters()) {
            names.add(p.getName());
            names.addAll(List.of(p.getAliases()));
        }
        return names;
    }

    private static Set<String> requiredNames(String tool) {
        Set<String> names = new HashSet<>();
        for (MCPParameterDefinition p : MCPToolRegistry.getTool(tool).getParameters()) {
            if (p.isRequired()) {
                names.add(p.getName());
            }
        }
        return names;
    }

    private static void assertBothAccept(String alias, String jiraTool, String adoTool, String... neutralNames) {
        Set<String> jira = acceptedNames(jiraTool);
        Set<String> ado = acceptedNames(adoTool);
        for (String n : neutralNames) {
            assertTrue(jira.contains(n), alias + ": " + jiraTool + " must accept '" + n + "' (accepts " + jira + ")");
            assertTrue(ado.contains(n), alias + ": " + adoTool + " must accept '" + n + "' (accepts " + ado + ")");
        }
    }

    @Test
    void tracker_move_to_status() {
        assertBothAccept("tracker_move_to_status", "jira_move_to_status", "ado_move_to_state", "key", "statusName", "state", "status");
    }

    @Test
    void tracker_add_and_remove_label() {
        assertBothAccept("tracker_add_label", "jira_add_label", "ado_add_work_item_label", "key", "label");
        assertBothAccept("tracker_remove_label", "jira_remove_label", "ado_remove_work_item_label", "key", "label");
    }

    @Test
    void tracker_post_comment() {
        assertBothAccept("tracker_post_comment", "jira_post_comment", "ado_add_work_item_comment", "key", "comment", "text", "body");
    }

    @Test
    void tracker_get_ticket_and_comments() {
        assertBothAccept("tracker_get_ticket", "jira_get_ticket", "ado_get_work_item", "key");
        assertBothAccept("tracker_get_comments", "jira_get_comments", "ado_get_work_item_comments", "key");
    }

    @Test
    void tracker_search() {
        assertBothAccept("tracker_search", "jira_search_by_jql", "ado_search_by_wiql", "jql", "wiql", "query", "searchQueryJQL");
    }

    @Test
    void tracker_assign() {
        assertBothAccept("tracker_assign", "jira_assign_ticket_to", "ado_assign_work_item", "key", "accountId", "userEmail");
    }

    @Test
    void tracker_create_ticket() {
        assertBothAccept("tracker_create_ticket", "jira_create_ticket_basic", "ado_create_work_item",
                "project", "issueType", "workItemType", "summary", "title", "description");
    }

    @Test
    void tracker_field_priority_description_updates() {
        assertBothAccept("tracker_update_field", "jira_update_field", "ado_update_field", "key", "field", "value");
        assertBothAccept("tracker_set_priority", "jira_set_priority", "ado_set_priority", "key", "priority");
        assertBothAccept("tracker_update_description", "jira_update_description", "ado_update_description", "key", "description");
    }

    @Test
    void tracker_link_and_attach() {
        assertBothAccept("tracker_link_tickets", "jira_link_issues", "ado_link_work_items", "sourceKey", "anotherKey", "sourceId", "targetId", "relationship");
        assertBothAccept("tracker_attach_file", "jira_attach_file_to_ticket", "ado_attach_file", "key", "id", "name", "filePath");
    }

    @Test
    void requiredParametersAreUnchangedByTheAliases() {
        // aliases are additive: nothing that was optional became required and vice versa
        assertEquals(Set.of("key", "statusName"), requiredNames("jira_move_to_status"));
        assertEquals(Set.of("id", "state"), requiredNames("ado_move_to_state"));
        assertEquals(Set.of("key", "comment"), requiredNames("jira_post_comment"));
        assertEquals(Set.of("id", "comment"), requiredNames("ado_add_work_item_comment"));
        assertEquals(Set.of("jql"), requiredNames("jira_search_by_jql"));
        assertEquals(Set.of("wiql"), requiredNames("ado_search_by_wiql"));
        assertEquals(Set.of("key"), requiredNames("jira_get_comments"));
        assertEquals(Set.of("id"), requiredNames("ado_get_work_item_comments"));
    }

    @Test
    void everyCanonicalNameStillWorksSoExistingCallersAreUnaffected() {
        assertTrue(acceptedNames("jira_move_to_status").containsAll(Set.of("key", "statusName")));
        assertTrue(acceptedNames("ado_move_to_state").containsAll(Set.of("id", "state", "key", "statusName")));
        assertTrue(acceptedNames("jira_search_by_jql").containsAll(Set.of("jql", "searchQueryJQL", "query")));
        assertTrue(acceptedNames("ado_search_by_wiql").containsAll(Set.of("wiql", "jql", "query")));
    }
}
