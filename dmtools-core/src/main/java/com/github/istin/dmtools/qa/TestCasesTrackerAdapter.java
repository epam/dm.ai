// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.common.model.ITicket;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Adapter interface for test cases tracker operations in TestCasesGenerator.
 * Implementations allow using a separate system (e.g., TestRail) for test case
 * read/create/link operations while the source stories remain in Jira/ADO.
 *
 * <p>The adapter owns all knowledge of its own configuration (project names,
 * creation mode, labels, etc.). The generator passes only what it naturally
 * knows: the source ticket key and the AI-generated test case data.</p>
 */
public interface TestCasesTrackerAdapter {

    /**
     * Fetch all test cases from the adapter's configured projects.
     */
    List<ITicket> getExistingCases() throws Exception;

    /**
     * Get cases already linked to a source ticket key.
     */
    List<ITicket> getLinkedCases(String sourceTicketKey) throws Exception;

    /**
     * Search test cases using a tracker-specific query string.
     * For Jira, {@code query} is a JQL fragment (handled directly by {@code trackerClient}).
     * For TestRail, {@code query} is a label name — returns all cases tagged with that label.
     */
    List<ITicket> searchCases(String query) throws Exception;

    /**
     * Create a single test case and return the created ITicket.
     *
     * @param testCase        AI-generated test case data
     * @param sourceTicketKey source story key used for initial linking (e.g. "PROJ-123")
     * @param params          generator params
     */
    ITicket createTestCase(TestCaseGeneratorAgent.TestCase testCase,
                           String sourceTicketKey,
                           TestCasesGeneratorParams params) throws IOException;

    /**
     * Link a test case to a source story/requirement.
     */
    void linkToSource(String testCaseId, String sourceTicketKey, String relationship) throws IOException;

    /**
     * Normalize a raw key returned by the AI to the adapter's canonical key format.
     * Default implementation returns the value unchanged (suitable for Jira-style keys).
     * Override for trackers whose AI responses differ from {@code ITicket.getKey()},
     * e.g. TestRail where the AI returns {@code 1} but {@code getKey()} returns {@code "C1"}.
     */
    default String normalizeKeyFromAI(String raw) {
        return raw;
    }

    /**
     * Extract custom fields from an existing test case for use as AI examples,
     * mapping the tracker's internal field names to the config keys defined in
     * {@code testCasesCustomFields}.
     *
     * <p>Default: reads each name directly from {@code ITicket.getFieldsAsJSON()}.
     * Override when the tracker's internal field names differ from the config keys,
     * e.g. TestRail stores steps as {@code custom_steps_separated} (JSONArray) but
     * the config expects {@code custom_steps_json} (JSON string).</p>
     */
    default JSONObject extractCustomFieldsForExample(ITicket testCase, String[] customFieldNames) {
        JSONObject result = new JSONObject();
        if (customFieldNames == null) return result;
        JSONObject fields = testCase.getFieldsAsJSON();
        if (fields == null) return result;
        for (String name : customFieldNames) {
            if (fields.has(name)) {
                Object value = fields.opt(name);
                if (value != null && !JSONObject.NULL.equals(value)) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }
}
