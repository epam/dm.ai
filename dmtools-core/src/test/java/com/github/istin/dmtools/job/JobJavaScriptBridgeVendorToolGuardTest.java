// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.kb.tool.KBTools;
import com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A jira_* tool called while another tracker is active must fail with an actionable message
 * (epam/dm.ai#661) instead of the raw ClassCastException from the generated executor.
 */
class JobJavaScriptBridgeVendorToolGuardTest {

    private JobJavaScriptBridge bridgeWith(com.github.istin.dmtools.common.tracker.TrackerClient<?> tracker) {
        return new JobJavaScriptBridge(tracker, mock(AI.class), mock(Confluence.class), mock(SourceCode.class), mock(KBTools.class));
    }

    @Test
    void jiraToolOnAdoTrackerFailsWithActionableMessage() {
        JobJavaScriptBridge bridge = bridgeWith(mock(AzureDevOpsClient.class));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeToolFromJS("jira_search_by_jql", Map.of("jql", "project = X")));

        String message = e.getMessage();
        assertTrue(message.contains("jira_search_by_jql is a Jira-only tool"), message);
        assertTrue(message.contains("active tracker is"), message);
        assertTrue(message.contains("Use the vendor-agnostic tracker_search instead"), message);
        assertTrue(message.contains("js/common/trackers.js"), message);
        assertFalse(message.contains("cannot be cast"), message);
    }

    @Test
    void jiraToolWithoutTrackerAliasStillGetsTheExplanation() {
        JobJavaScriptBridge bridge = bridgeWith(mock(AzureDevOpsClient.class));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> bridge.executeToolFromJS("jira_get_my_profile", Map.of()));

        assertTrue(e.getMessage().contains("Jira-only tool") || e.getMessage().contains("tracker_get_my_profile"), e.getMessage());
    }

    @Test
    void jiraToolOnJiraTrackerIsNotBlockedByTheGuard() {
        JobJavaScriptBridge bridge = bridgeWith(mock(JiraClient.class));

        try {
            bridge.executeToolFromJS("jira_search_by_jql", Map.of("jql", "project = X"));
        } catch (RuntimeException e) {
            // the call reaches the executor (the mock client answers/fails there) — never our guard
            assertFalse(String.valueOf(e.getMessage()).contains("Jira-only tool"), e.getMessage());
        }
    }

    @Test
    void adoToolOnAdoTrackerIsNotBlockedByTheGuard() {
        JobJavaScriptBridge bridge = bridgeWith(mock(AzureDevOpsClient.class));

        try {
            bridge.executeToolFromJS("ado_get_work_item", Map.of("id", "1"));
        } catch (RuntimeException e) {
            assertFalse(String.valueOf(e.getMessage()).contains("Jira-only tool"), e.getMessage());
        }
    }
}
