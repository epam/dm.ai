// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.testrail;

import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.qa.CustomTestCasesTrackerParams;
import com.github.istin.dmtools.qa.TestCasesGeneratorParams;
import com.github.istin.dmtools.qa.TestCasesTrackerAdapter;
import com.github.istin.dmtools.testrail.model.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TestCasesTrackerAdapter implementation for TestRail.
 * Wraps TestRailClient to provide test case operations needed by TestCasesGenerator.
 * All project/label/type configuration is encapsulated here via TestRailAdapterParams.
 */
public class TestRailTestCasesAdapter implements TestCasesTrackerAdapter {

    private final TestRailClient client;
    private final TestRailAdapterParams config;

    public TestRailTestCasesAdapter(TestRailClient client, CustomTestCasesTrackerParams p) {
        this.client = client;
        this.config = new TestRailAdapterParams(p.getParams());
    }

    @Override
    public List<ITicket> getExistingCases() throws Exception {
        List<ITicket> result = new ArrayList<>();
        for (String projectName : config.getProjectNames()) {
            List<TestCase> cases = client.getAllCases(projectName);
            System.out.println("[DEBUG-LINKING] getExistingCases project='" + projectName + "' returned " + cases.size() + " cases");
            if (!cases.isEmpty()) {
                System.out.println("[DEBUG-LINKING] getExistingCases sample keys (first 5): " + cases.stream().limit(5).map(c -> { try { return c.getKey() + "(refs=" + c.getString("refs") + ")"; } catch (Exception e) { return c.getKey(); } }).collect(java.util.stream.Collectors.joining(", ")));
            }
            result.addAll(cases);
        }
        for (int projectId : config.getProjectIds()) {
            List<TestCase> cases = client.getAllCasesByProjectId(projectId);
            System.out.println("[DEBUG-LINKING] getExistingCases projectId=" + projectId + " returned " + cases.size() + " cases");
            result.addAll(cases);
        }
        return result;
    }

    @Override
    public List<ITicket> getLinkedCases(String sourceTicketKey) throws Exception {
        List<ITicket> result = new ArrayList<>();
        for (String projectName : config.getProjectNames()) {
            List<TestCase> cases = client.getCasesByRefs(sourceTicketKey, projectName);
            System.out.println("[DEBUG-LINKING] getLinkedCases key='" + sourceTicketKey + "' project='" + projectName + "' returned " + cases.size() + " cases");
            if (!cases.isEmpty()) {
                System.out.println("[DEBUG-LINKING] getLinkedCases keys: " + cases.stream().map(c -> c.getKey()).collect(java.util.stream.Collectors.joining(", ")));
            }
            result.addAll(cases);
        }
        for (int projectId : config.getProjectIds()) {
            List<TestCase> cases = client.getCasesByRefsByProjectId(sourceTicketKey, projectId);
            System.out.println("[DEBUG-LINKING] getLinkedCases key='" + sourceTicketKey + "' projectId=" + projectId + " returned " + cases.size() + " cases");
            result.addAll(cases);
        }
        return result;
    }

    @Override
    public List<ITicket> searchCases(String query) throws Exception {
        List<ITicket> result = new ArrayList<>();
        for (String projectName : config.getProjectNames()) {
            List<TestCase> cases = client.getCasesByLabel(projectName, query);
            result.addAll(cases);
        }
        for (int projectId : config.getProjectIds()) {
            List<TestCase> cases = client.getCasesByLabelByProjectId(projectId, query);
            result.addAll(cases);
        }
        return result;
    }

    @Override
    public ITicket createTestCase(TestCaseGeneratorAgent.TestCase testCase,
                                  String sourceTicketKey,
                                  TestCasesGeneratorParams params) throws IOException {
        String mode = config.getCreationMode();

        String typeId = config.getTypeId();
        if (typeId == null || typeId.isEmpty()) {
            String typeName = config.getTypeName();
            if (typeName != null && !typeName.isEmpty()) {
                typeId = client.resolveTypeIdByName(typeName);
            }
        }

        String priorityId = convertPriorityToId(testCase.getPriority());
        JSONObject customFields = testCase.getCustomFields() != null ? testCase.getCustomFields() : new JSONObject();

        Integer targetProjectId = resolveTargetProjectId();
        if (targetProjectId != null) {
            String labelIds = config.getLabelIds();
            if (labelIds == null || labelIds.isEmpty()) {
                String[] labelNames = config.getLabelNames();
                if (labelNames.length > 0) {
                    labelIds = client.resolveLabelIdsByProjectId(targetProjectId, labelNames);
                }
            }
            String response = createByProjectId(targetProjectId, testCase, customFields, priorityId, typeId, sourceTicketKey, labelIds);
            return client.createTicket(response);
        }

        String projectName = resolveTargetProjectName();
        String labelIds = config.getLabelIds();
        if (labelIds == null || labelIds.isEmpty()) {
            String[] labelNames = config.getLabelNames();
            if (labelNames.length > 0) {
                labelIds = client.resolveLabelIdsByNames(projectName, labelNames);
            }
        }

        String response;
        switch (mode.toLowerCase()) {
            case "detailed":
                response = client.createCaseDetailed(
                        projectName,
                        testCase.getSummary(),
                        customFields.optString("custom_preconds", null),
                        customFields.optString("custom_steps", null),
                        customFields.optString("custom_expected", null),
                        priorityId,
                        typeId,
                        sourceTicketKey,
                        labelIds
                );
                break;
            case "steps":
                String stepsJson = customFields.optString("custom_steps_json", null);
                if (stepsJson == null || stepsJson.isEmpty()) {
                    JSONArray stepsArray = customFields.optJSONArray("custom_steps_separated");
                    if (stepsArray != null) {
                        stepsJson = stepsArray.toString();
                    }
                }
                if (stepsJson == null || stepsJson.isEmpty()) {
                    stepsJson = new JSONArray()
                            .put(new JSONObject()
                                    .put("content", testCase.getDescription() != null ? testCase.getDescription() : "")
                                    .put("expected", ""))
                            .toString();
                }
                response = client.createCaseSteps(
                        projectName,
                        testCase.getSummary(),
                        customFields.optString("custom_preconds", null),
                        stepsJson,
                        priorityId,
                        typeId,
                        sourceTicketKey,
                        labelIds
                );
                break;
            default: // "simple"
                response = client.createCaseDetailed(
                        projectName,
                        testCase.getSummary(),
                        testCase.getDescription(),
                        null,
                        null,
                        priorityId,
                        typeId,
                        sourceTicketKey,
                        labelIds
                );
                break;
        }
        return client.createTicket(response);
    }

    @Override
    public void linkToSource(String testCaseId, String sourceTicketKey, String relationship) throws IOException {
        String numericId = stripCPrefix(testCaseId);
        client.linkToRequirement(numericId, sourceTicketKey);
    }

    /**
     * Normalizes a raw key returned by the AI to TestRail's canonical "C{id}" format.
     * The AI often returns bare numeric IDs (e.g. {@code 1}) while {@code getKey()} returns {@code "C1"}.
     */
    @Override
    public String normalizeKeyFromAI(String raw) {
        try {
            Integer.parseInt(raw.trim());
            return "C" + raw.trim();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /**
     * Maps TestRail internal field names to the config keys expected by the AI generator.
     * Specifically converts {@code custom_steps_separated} (TestRail's JSONArray) to
     * {@code custom_steps_json} (the virtual key used in job configs).
     */
    @Override
    public JSONObject extractCustomFieldsForExample(ITicket testCase, String[] customFieldNames) {
        JSONObject result = new JSONObject();
        if (customFieldNames == null) return result;
        JSONObject fields = testCase.getFieldsAsJSON();
        if (fields == null) return result;
        for (String name : customFieldNames) {
            if ("custom_steps_json".equals(name)) {
                // TestRail stores steps as custom_steps_separated — convert to content/expected JSON string
                JSONArray separated = fields.optJSONArray("custom_steps_separated");
                if (separated != null && separated.length() > 0) {
                    JSONArray simplified = new JSONArray();
                    for (int i = 0; i < separated.length(); i++) {
                        JSONObject step = separated.getJSONObject(i);
                        simplified.put(new JSONObject()
                                .put("content", step.optString("content", ""))
                                .put("expected", step.optString("expected", "")));
                    }
                    result.put("custom_steps_json", simplified.toString());
                }
            } else if (fields.has(name)) {
                Object value = fields.opt(name);
                if (value != null && !JSONObject.NULL.equals(value)) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }

    // ---- private helpers ----

    private String resolveTargetProjectName() {
        String target = config.getTargetProject();
        if (target != null && !target.isEmpty()) return target;
        String[] names = config.getProjectNames();
        if (names.length > 0) return names[0];
        throw new IllegalStateException("No TestRail project name configured. Use 'projectNames' or 'targetProject', or use 'projectIds' / 'targetProjectId' for ID-based access.");
    }

    private Integer resolveTargetProjectId() {
        Integer id = config.getTargetProjectId();
        if (id != null) return id;
        int[] ids = config.getProjectIds();
        if (ids.length > 0) return ids[0];
        return null;
    }

    private String createByProjectId(int projectId, TestCaseGeneratorAgent.TestCase testCase,
                                     JSONObject customFields, String priorityId, String typeId,
                                     String sourceTicketKey, String labelIds) throws IOException {
        String mode = config.getCreationMode();
        switch (mode.toLowerCase()) {
            case "steps":
                String stepsJson = customFields.optString("custom_steps_json", null);
                if (stepsJson == null || stepsJson.isEmpty()) {
                    JSONArray stepsArray = customFields.optJSONArray("custom_steps_separated");
                    if (stepsArray != null) stepsJson = stepsArray.toString();
                }
                if (stepsJson == null || stepsJson.isEmpty()) {
                    stepsJson = new JSONArray()
                            .put(new JSONObject()
                                    .put("content", testCase.getDescription() != null ? testCase.getDescription() : "")
                                    .put("expected", ""))
                            .toString();
                }
                return client.createCaseStepsByProjectId(projectId, testCase.getSummary(),
                        customFields.optString("custom_preconds", null), stepsJson,
                        priorityId, typeId, sourceTicketKey, labelIds);
            case "detailed":
                return client.createCaseDetailedByProjectId(projectId, testCase.getSummary(),
                        customFields.optString("custom_preconds", null),
                        customFields.optString("custom_steps", null),
                        customFields.optString("custom_expected", null),
                        priorityId, typeId, sourceTicketKey, labelIds);
            default: // "simple"
                return client.createCaseDetailedByProjectId(projectId, testCase.getSummary(),
                        testCase.getDescription(), null, null,
                        priorityId, typeId, sourceTicketKey, labelIds);
        }
    }

    /**
     * Converts a priority string to a TestRail priority ID.
     * TestRail default priorities: 1=Low, 2=Medium, 3=High, 4=Critical.
     */
    private String convertPriorityToId(String priority) {
        if (priority == null) return "2";
        switch (priority.trim().toLowerCase()) {
            case "critical":
            case "highest":
                return "4";
            case "high":
                return "3";
            case "low":
            case "lowest":
                return "1";
            default:
                return "2";
        }
    }

    /**
     * Strips the "C" prefix from a TestRail case key (e.g. "C123" -> "123").
     */
    private String stripCPrefix(String testCaseId) {
        if (testCaseId != null && testCaseId.startsWith("C")) {
            return testCaseId.substring(1);
        }
        return testCaseId;
    }
}
