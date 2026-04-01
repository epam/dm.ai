// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.testrail;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import com.github.istin.dmtools.testrail.model.TestCase;
import com.github.istin.dmtools.testrail.model.TestCaseFields;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TestRail client implementing TrackerClient interface.
 * Provides integration with TestRail test management system.
 *
 * <p>Configuration via environment variables:</p>
 * <ul>
 *   <li>TESTRAIL_BASE_PATH - TestRail instance URL (e.g., https://example.testrail.com)</li>
 *   <li>TESTRAIL_USERNAME - TestRail username/email</li>
 *   <li>TESTRAIL_API_KEY - TestRail API key (from My Settings)</li>
 *   <li>TESTRAIL_PROJECT - Default project name</li>
 *   <li>TESTRAIL_LOGGING_ENABLED - Enable debug logging (true/false)</li>
 * </ul>
 */
public class TestRailClient extends AbstractRestClient implements TrackerClient<TestCase> {

    private static final Logger logger = LogManager.getLogger(TestRailClient.class);

    public static final String BASE_PATH;
    public static final String USERNAME;
    public static final String API_KEY;
    public static final String PROJECT_NAME;
    private static final boolean IS_LOGGING_ENABLED;

    static {
        PropertyReader propertyReader = new PropertyReader();
        BASE_PATH = propertyReader.getTestRailBasePath();
        USERNAME = propertyReader.getTestRailUsername();
        API_KEY = propertyReader.getTestRailApiKey();
        PROJECT_NAME = propertyReader.getTestRailProject();
        IS_LOGGING_ENABLED = propertyReader.isTestRailLoggingEnabled();
    }

    private static TestRailClient instance;

    private boolean isLogEnabled = IS_LOGGING_ENABLED;
    private final Map<String, Integer> projectIdCache = new HashMap<>();
    private final Map<Integer, Integer> defaultSectionCache = new HashMap<>();

    public static synchronized TrackerClient<? extends ITicket> getInstance() throws IOException {
        if (instance == null) {
            if (BASE_PATH == null || BASE_PATH.isEmpty()) {
                logger.debug("TestRail configuration not found. Set TESTRAIL_BASE_PATH, TESTRAIL_USERNAME, and TESTRAIL_API_KEY.");
                return null;
            }
            if (USERNAME == null || USERNAME.isEmpty() || API_KEY == null || API_KEY.isEmpty()) {
                logger.warn("TESTRAIL_BASE_PATH is set, but authentication is missing. Set TESTRAIL_USERNAME and TESTRAIL_API_KEY.");
                return null;
            }
            instance = new TestRailClient();
        }
        return instance;
    }

    public TestRailClient() throws IOException {
        this(BASE_PATH, USERNAME, API_KEY);
    }

    public TestRailClient(String basePath, String username, String apiKey) throws IOException {
        super(basePath, encodeAuth(username, apiKey));
    }

    private static String encodeAuth(String username, String apiKey) {
        String credentials = username + ":" + apiKey;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("Authorization", "Basic " + authorization)
                .header("Content-Type", "application/json");
    }

    @Override
    public String path(String path) {
        // Remove trailing slash from basePath if present to avoid double slashes
        String basePathClean = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        return basePathClean + "/index.php?/api/v2" + path;
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    @Override
    public String getCacheFolderName() {
        return "cacheTestRail";
    }

    private void log(String message) {
        if (isLogEnabled) {
            logger.info(sanitizeUrl(message));
        }
    }

    // ========== MCP Tools ==========

    @MCPTool(
            name = "testrail_get_projects",
            description = "Get list of all projects in TestRail",
            integration = "testrail",
            category = "projects"
    )
    public String getProjects() throws IOException {
        JSONArray allProjects = new JSONArray();
        int limit = 250;
        String nextPagePath = buildPagedPath("/get_projects", limit, 0);

        while (nextPagePath != null) {
            JSONObject responseObj = new JSONObject(executeGet(nextPagePath));
            JSONArray projects = responseObj.optJSONArray("projects");
            if (projects == null || projects.length() == 0) {
                break;
            }

            appendAll(allProjects, projects);
            nextPagePath = getNextPagePath(responseObj, "/get_projects", limit, projects.length());
        }

        JSONObject response = new JSONObject();
        response.put("offset", 0);
        response.put("limit", limit);
        response.put("size", allProjects.length());
        response.put("projects", allProjects);
        response.put("_links", new JSONObject().put("next", JSONObject.NULL).put("prev", JSONObject.NULL));
        log("Retrieved projects list");
        return response.toString();
    }

    @MCPTool(
            name = "testrail_get_case",
            description = "Get a TestRail test case by ID",
            integration = "testrail",
            category = "test_cases"
    )
    public TestCase getCase(
            @MCPParam(name = "case_id", description = "The test case ID (numeric, without 'C' prefix)", required = true, example = "123")
            String caseId
    ) throws IOException {
        return performTicket(caseId, null);
    }

    @MCPTool(
            name = "testrail_get_all_cases",
            description = "Get ALL test cases in a project (uses pagination to retrieve all cases)",
            integration = "testrail",
            category = "test_cases"
    )
    public List<TestCase> getAllCases(
            @MCPParam(name = "project_name", description = "Project name to get all cases from", required = true, example = "My Project")
            String projectName
    ) throws Exception {
        int projectId = getProjectId(projectName);
        log("Retrieving all test cases for project: " + projectName);
        return getCasesByProjectId(projectId, null, null, null);
    }

    @MCPTool(
            name = "testrail_search_cases",
            description = "Search TestRail test cases by project and optional filters",
            integration = "testrail",
            category = "test_cases"
    )
    public List<TestCase> searchCases(
            @MCPParam(name = "project_name", description = "Project name to search in", required = true, example = "My Project")
            String projectName,
            @MCPParam(name = "suite_id", description = "Suite ID to filter by (optional)", required = false, example = "1")
            String suiteId,
            @MCPParam(name = "section_id", description = "Section ID to filter by (optional)", required = false, example = "10")
            String sectionId
    ) throws Exception {
        int projectId = getProjectId(projectName);
        return getCasesByProjectId(projectId, suiteId, sectionId, null);
    }

    /**
     * Get test cases filtered by label name across a project.
     * Resolves the label name to its ID, then filters via the TestRail API.
     */
    public List<TestCase> getCasesByLabel(String projectName, String labelName) throws Exception {
        int projectId = getProjectId(projectName);
        String labelId = resolveLabelIdsByNames(projectName, new String[]{labelName});
        log("Retrieving test cases with label '" + labelName + "' for project: " + projectName);
        return getCasesByProjectId(projectId, null, null, labelId);
    }

    /** ID-based variant — bypasses project name resolution. */
    List<TestCase> getAllCasesByProjectId(int projectId) throws Exception {
        log("Retrieving all test cases for project ID: " + projectId);
        return getCasesByProjectId(projectId, null, null, null);
    }

    /** ID-based variant — bypasses project name resolution. */
    List<TestCase> getCasesByLabelByProjectId(int projectId, String labelName) throws Exception {
        String labelId = resolveLabelIdsByProjectId(projectId, new String[]{labelName});
        log("Retrieving test cases with label '" + labelName + "' for project ID: " + projectId);
        return getCasesByProjectId(projectId, null, null, labelId);
    }

    /**
     * Get test cases by project ID with optional filters.
     * TestRail API endpoint: GET /get_cases/:project_id
     */
    List<TestCase> getCasesByProjectId(int projectId, String suiteId, String sectionId, String labelId) throws Exception {
        List<TestCase> results = new ArrayList<>();
        int limit = 250; // TestRail max per request
        String baseApiPath = buildCasesPath(projectId, suiteId, sectionId, labelId);
        String nextPagePath = buildPagedPath(baseApiPath, limit, 0);

        while (nextPagePath != null) {
            String response = executeGet(nextPagePath);

            JSONObject responseObj = new JSONObject(response);
            JSONArray cases = responseObj.optJSONArray("cases");

            if (cases == null || cases.length() == 0) {
                break;
            }

            for (int i = 0; i < cases.length(); i++) {
                TestCase testCase = new TestCase(basePath, cases.getJSONObject(i));
                results.add(testCase);
            }

            nextPagePath = getNextPagePath(responseObj, baseApiPath, limit, cases.length());
        }

        log("Retrieved " + results.size() + " test cases for project " + projectId);
        return results;
    }

    @MCPTool(
            name = "testrail_get_cases_by_refs",
            description = "Get test cases linked to a requirement/story via refs field",
            integration = "testrail",
            category = "test_cases"
    )
    public List<TestCase> getCasesByRefs(
            @MCPParam(name = "refs", description = "Reference ID (e.g., JIRA ticket key)", required = true, example = "PROJ-123")
            String refs,
            @MCPParam(name = "project_name", description = "Project name to search in", required = true, example = "My Project")
            String projectName
    ) throws Exception {
        int projectId = getProjectId(projectName);
        List<TestCase> results = new ArrayList<>();
        int limit = 250;
        String baseApiPath = buildCasesByRefsPath(projectId, refs);
        String nextPagePath = buildPagedPath(baseApiPath, limit, 0);

        while (nextPagePath != null) {
            String response = executeGet(nextPagePath);

            JSONObject responseObj = new JSONObject(response);
            JSONArray cases = responseObj.optJSONArray("cases");

            if (cases == null || cases.length() == 0) {
                break;
            }

            for (int i = 0; i < cases.length(); i++) {
                TestCase testCase = new TestCase(basePath, cases.getJSONObject(i));
                results.add(testCase);
            }

            nextPagePath = getNextPagePath(responseObj, baseApiPath, limit, cases.length());
        }

        log("Found " + results.size() + " test cases with refs: " + refs);
        return results;
    }

    /** ID-based variant of {@link #getCasesByRefs} — bypasses project name resolution. */
    List<TestCase> getCasesByRefsByProjectId(String refs, int projectId) throws Exception {
        List<TestCase> results = new ArrayList<>();
        int limit = 250;
        String baseApiPath = buildCasesByRefsPath(projectId, refs);
        String nextPagePath = buildPagedPath(baseApiPath, limit, 0);

        while (nextPagePath != null) {
            String response = executeGet(nextPagePath);

            JSONObject responseObj = new JSONObject(response);
            JSONArray cases = responseObj.optJSONArray("cases");

            if (cases == null || cases.length() == 0) {
                break;
            }

            for (int i = 0; i < cases.length(); i++) {
                results.add(new TestCase(basePath, cases.getJSONObject(i)));
            }

            nextPagePath = getNextPagePath(responseObj, baseApiPath, limit, cases.length());
        }

        log("Found " + results.size() + " test cases with refs: " + refs + " in project ID: " + projectId);
        return results;
    }

    @MCPTool(
            name = "testrail_create_case",
            description = "Create a new test case in TestRail",
            integration = "testrail",
            category = "test_cases"
    )
    public String createCase(
            @MCPParam(name = "project_name", description = "Project name", required = true, example = "My Project")
            String projectName,
            @MCPParam(name = "title", description = "Test case title/summary", required = true, example = "Verify login functionality")
            String title,
            @MCPParam(name = "description", description = "Test case description/steps (optional)", required = false, example = "1. Navigate to login page\n2. Enter credentials\n3. Click login")
            String description,
            @MCPParam(name = "priority_id", description = "Priority ID: 1=Low, 2=Medium, 3=High, 4=Critical (optional, default=2)", required = false, example = "3")
            String priorityId,
            @MCPParam(name = "refs", description = "Reference to requirement (e.g., JIRA key)", required = false, example = "PROJ-123")
            String refs
    ) throws IOException {
        return createTicketInProject(projectName, "Test Case", title, description, fields -> {
            if (priorityId != null && !priorityId.isEmpty()) {
                try {
                    fields.set("priority_id", Integer.parseInt(priorityId));
                } catch (NumberFormatException e) {
                    fields.set("priority_id", 2); // Default to Medium
                }
            } else {
                fields.set("priority_id", 2); // Default to Medium
            }

            if (refs != null && !refs.isEmpty()) {
                fields.set("refs", refs);
            }
        });
    }

    @MCPTool(
            name = "testrail_create_case_detailed",
            description = "Create a new test case in TestRail with detailed fields (preconditions, steps, expected results, labels, type). " +
                    "Note: TestRail uses its own table format in text fields: |||:Col 1|:Col 2|:Col 3\\n||val1|val2|val3. " +
                    "Standard Markdown tables (| Col | Col |) will be auto-converted to TestRail format.",
            integration = "testrail",
            category = "test_cases"
    )
    public String createCaseDetailed(
            @MCPParam(name = "project_name", description = "Project name", required = true, example = "My Project")
            String projectName,
            @MCPParam(name = "title", description = "Test case title/summary", required = true, example = "Verify login functionality")
            String title,
            @MCPParam(name = "preconditions", description = "Preconditions (optional). For tables use TestRail format: |||:Col1|:Col2\\n||val1|val2", required = false, example = "User is logged out")
            String preconditions,
            @MCPParam(name = "steps", description = "Test steps separated by double newline (optional)", required = false, example = "Navigate to login page.\\n\\nEnter username %Username%.\\n\\nClick Login button.")
            String steps,
            @MCPParam(name = "expected", description = "Expected results (optional)", required = false, example = "User is logged in and redirected to dashboard")
            String expected,
            @MCPParam(name = "priority_id", description = "Priority ID: 1=Low, 2=Medium, 3=High, 4=Critical (optional, default=2)", required = false, example = "3")
            String priorityId,
            @MCPParam(name = "type_id", description = "Case type ID (optional). Use testrail_get_case_types to get available types.", required = false, example = "1")
            String typeId,
            @MCPParam(name = "refs", description = "Reference to requirement (e.g., JIRA key)", required = false, example = "PROJ-123")
            String refs,
            @MCPParam(name = "label_ids", description = "Comma-separated label IDs (optional). Use testrail_get_labels to find IDs.", required = false, example = "7,8")
            String labelIds
    ) throws IOException {
        return createCaseDetailedByProjectId(getProjectId(projectName), title, preconditions, steps, expected, priorityId, typeId, refs, labelIds);
    }

    /** ID-based variant of {@link #createCaseDetailed} — bypasses project name resolution. */
    String createCaseDetailedByProjectId(int projectId, String title, String preconditions,
            String steps, String expected, String priorityId, String typeId,
            String refs, String labelIds) throws IOException {
        int sectionId = getDefaultSectionId(projectId);

        JSONObject caseData = new JSONObject();
        caseData.put("title", title);

        // Set preconditions - convert Markdown tables to TestRail format
        if (preconditions != null && !preconditions.isEmpty()) {
            caseData.put("custom_preconds", convertMarkdownTablesToTestRailFormat(preconditions));
        }

        // Set test steps
        if (steps != null && !steps.isEmpty()) {
            caseData.put("custom_steps", convertMarkdownTablesToTestRailFormat(steps));
        }

        // Set expected results
        if (expected != null && !expected.isEmpty()) {
            caseData.put("custom_expected", convertMarkdownTablesToTestRailFormat(expected));
        }

        // Set priority
        if (priorityId != null && !priorityId.isEmpty()) {
            try {
                caseData.put("priority_id", Integer.parseInt(priorityId));
            } catch (NumberFormatException e) {
                caseData.put("priority_id", 2); // Default to Medium
            }
        } else {
            caseData.put("priority_id", 2); // Default to Medium
        }

        // Set type
        if (typeId != null && !typeId.isEmpty()) {
            try {
                caseData.put("type_id", Integer.parseInt(typeId));
            } catch (NumberFormatException e) {
                log("Invalid type_id: " + typeId);
            }
        }

        // Set refs
        if (refs != null && !refs.isEmpty()) {
            caseData.put("refs", refs);
        }

        // Set labels (array of label IDs)
        if (labelIds != null && !labelIds.isEmpty()) {
            String[] ids = labelIds.split(",");
            JSONArray labelsArray = new JSONArray();
            for (String id : ids) {
                try {
                    labelsArray.put(Integer.parseInt(id.trim()));
                } catch (NumberFormatException e) {
                    log("Invalid label ID: " + id);
                }
            }
            if (labelsArray.length() > 0) {
                caseData.put("labels", labelsArray);
            }
        }

        // Create case
        GenericRequest request = new GenericRequest(this, path("/add_case/" + sectionId));
        request.setBody(caseData.toString());
        String response = request.post();

        JSONObject responseObj = new JSONObject(response);
        Integer caseId = responseObj.optInt("id");

        log("Created test case: C" + caseId);
        return response;
    }

    @MCPTool(
            name = "testrail_create_case_steps",
            description = "Create a TestRail test case using the 'Test Case (Steps)' template (template_id=2). " +
                    "Steps are provided as a JSON array: [{\"content\":\"step text\",\"expected\":\"expected result\"}, ...]. " +
                    "Markdown tables in step content or expected are auto-converted to HTML tables. " +
                    "Use testrail_get_case_types for type_id, testrail_get_labels for label_ids.",
            integration = "testrail",
            category = "test_cases"
    )
    public String createCaseSteps(
            @MCPParam(name = "project_name", description = "Project name", required = true, example = "My Project")
            String projectName,
            @MCPParam(name = "title", description = "Test case title/summary", required = true, example = "Verify login functionality")
            String title,
            @MCPParam(name = "preconditions", description = "Preconditions text (optional)", required = false, example = "User is logged out")
            String preconditions,
            @MCPParam(name = "steps_json", description = "JSON array of step objects: [{\"content\":\"step\",\"expected\":\"result\"}, ...]. Markdown tables are auto-converted to HTML.", required = true, example = "[{\"content\":\"Open login page\",\"expected\":\"Login form is displayed\"},{\"content\":\"Enter credentials\",\"expected\":\"Fields populated\"}]")
            String stepsJson,
            @MCPParam(name = "priority_id", description = "Priority ID: 1=Low, 2=Medium, 3=High, 4=Critical (optional, default=2)", required = false, example = "3")
            String priorityId,
            @MCPParam(name = "type_id", description = "Case type ID (optional). Use testrail_get_case_types to get available types.", required = false, example = "1")
            String typeId,
            @MCPParam(name = "refs", description = "Reference to requirement (e.g., JIRA key)", required = false, example = "PROJ-123")
            String refs,
            @MCPParam(name = "label_ids", description = "Comma-separated label IDs (optional). Use testrail_get_labels to find IDs.", required = false, example = "7,8")
            String labelIds
    ) throws IOException {
        return createCaseStepsByProjectId(getProjectId(projectName), title, preconditions, stepsJson, priorityId, typeId, refs, labelIds);
    }

    /** ID-based variant of {@link #createCaseSteps} — bypasses project name resolution. */
    String createCaseStepsByProjectId(int projectId, String title, String preconditions,
            String stepsJson, String priorityId, String typeId,
            String refs, String labelIds) throws IOException {
        int sectionId = getDefaultSectionId(projectId);

        JSONObject caseData = new JSONObject();
        caseData.put("title", title);
        caseData.put("template_id", 2); // Test Case (Steps) template

        // Set preconditions (convert Markdown tables to HTML for Steps template)
        if (preconditions != null && !preconditions.isEmpty()) {
            caseData.put("custom_preconds", convertMarkdownTablesToHtml(preconditions));
        }

        // Parse and set steps
        try {
            JSONArray inputSteps = new JSONArray(stepsJson);
            JSONArray stepsSeparated = new JSONArray();
            for (int i = 0; i < inputSteps.length(); i++) {
                JSONObject inputStep = inputSteps.getJSONObject(i);
                JSONObject step = new JSONObject();
                step.put("content", convertMarkdownTablesToHtml(inputStep.optString("content", "")));
                step.put("expected", convertMarkdownTablesToHtml(inputStep.optString("expected", "")));
                step.put("additional_info", inputStep.optString("additional_info", ""));
                step.put("refs", inputStep.optString("refs", ""));
                step.put("markdown_editor_id", 1);
                stepsSeparated.put(step);
            }
            caseData.put("custom_steps_separated", stepsSeparated);
        } catch (Exception e) {
            throw new IOException("Invalid steps_json format. Expected JSON array: [{\"content\":\"...\",\"expected\":\"...\"}, ...]", e);
        }

        // Set priority
        if (priorityId != null && !priorityId.isEmpty()) {
            try {
                caseData.put("priority_id", Integer.parseInt(priorityId));
            } catch (NumberFormatException e) {
                caseData.put("priority_id", 2);
            }
        } else {
            caseData.put("priority_id", 2);
        }

        // Set type
        if (typeId != null && !typeId.isEmpty()) {
            try {
                caseData.put("type_id", Integer.parseInt(typeId));
            } catch (NumberFormatException e) {
                log("Invalid type_id: " + typeId);
            }
        }

        // Set refs
        if (refs != null && !refs.isEmpty()) {
            caseData.put("refs", refs);
        }

        // Set labels
        if (labelIds != null && !labelIds.isEmpty()) {
            String[] ids = labelIds.split(",");
            JSONArray labelsArray = new JSONArray();
            for (String id : ids) {
                try {
                    labelsArray.put(Integer.parseInt(id.trim()));
                } catch (NumberFormatException e) {
                    log("Invalid label ID: " + id);
                }
            }
            if (labelsArray.length() > 0) {
                caseData.put("labels", labelsArray);
            }
        }

        GenericRequest request = new GenericRequest(this, path("/add_case/" + sectionId));
        request.setBody(caseData.toString());
        String response = request.post();

        JSONObject responseObj = new JSONObject(response);
        Integer caseId = responseObj.optInt("id");
        log("Created steps test case: C" + caseId);
        return response;
    }

    @MCPTool(
            name = "testrail_update_case",
            description = "Update a test case in TestRail",
            integration = "testrail",
            category = "test_cases"
    )
    public String updateCase(
            @MCPParam(name = "case_id", description = "The test case ID to update", required = true, example = "123")
            String caseId,
            @MCPParam(name = "title", description = "New title (optional)", required = false, example = "Updated title")
            String title,
            @MCPParam(name = "priority_id", description = "New priority ID (optional)", required = false, example = "3")
            String priorityId,
            @MCPParam(name = "refs", description = "New references (optional)", required = false, example = "PROJ-123")
            String refs
    ) throws IOException {
        return updateTicket(caseId, fields -> {
            if (title != null && !title.isEmpty()) {
                fields.set("title", title);
            }
            if (priorityId != null && !priorityId.isEmpty()) {
                try {
                    fields.set("priority_id", Integer.parseInt(priorityId));
                } catch (NumberFormatException e) {
                    // Ignore invalid priority
                }
            }
            if (refs != null && !refs.isEmpty()) {
                fields.set("refs", refs);
            }
        });
    }

    @MCPTool(
            name = "testrail_delete_case",
            description = "Delete a test case in TestRail by case ID",
            integration = "testrail",
            category = "test_cases"
    )
    public String deleteCase(
            @MCPParam(name = "case_id", description = "The numeric test case ID to delete (without the C prefix)", required = true, example = "123")
            String caseId
    ) throws IOException {
        GenericRequest request = new GenericRequest(this, path("/delete_case/" + caseId));
        request.setBody("{}");
        return request.post();
    }

    @MCPTool(
            name = "testrail_link_to_requirement",
            description = "Link a test case to a requirement by updating refs field",
            integration = "testrail",
            category = "test_cases"
    )
    public String linkToRequirement(
            @MCPParam(name = "case_id", description = "The test case ID", required = true, example = "123")
            String caseId,
            @MCPParam(name = "requirement_key", description = "Requirement key (e.g., JIRA ticket)", required = true, example = "PROJ-123")
            String requirementKey
    ) throws IOException {
        // Get existing refs
        TestCase testCase = performTicket(caseId, null);
        String existingRefs = testCase.getString("refs");

        // Append new ref if not already present
        Set<String> refs = new HashSet<>();
        if (existingRefs != null && !existingRefs.isEmpty()) {
            refs.addAll(Arrays.asList(existingRefs.split(",")));
        }
        refs.add(requirementKey.trim());

        String newRefs = String.join(",", refs);
        return updateTicket(caseId, fields -> fields.set("refs", newRefs));
    }

    // ========== Labels API ==========

    @MCPTool(
            name = "testrail_get_labels",
            description = "Get all labels for a project in TestRail",
            integration = "testrail",
            category = "labels"
    )
    public String getLabels(
            @MCPParam(name = "project_name", description = "Project name", required = true, example = "My Project")
            String projectName
    ) throws IOException {
        return getLabelsById(getProjectId(projectName));
    }

    /** ID-based variant — bypasses project name resolution. */
    String getLabelsById(int projectId) throws IOException {
        List<JSONObject> allLabels = new ArrayList<>();
        int limit = 250;
        String baseApiPath = "/get_labels/" + projectId;
        String nextPagePath = buildPagedPath(baseApiPath, limit, 0);

        while (nextPagePath != null) {
            String response = executeGet(nextPagePath);

            JSONObject responseObj = new JSONObject(response);
            JSONArray labels = responseObj.optJSONArray("labels");

            if (labels == null || labels.length() == 0) {
                break;
            }

            for (int i = 0; i < labels.length(); i++) {
                allLabels.add(labels.getJSONObject(i));
            }

            nextPagePath = getNextPagePath(responseObj, baseApiPath, limit, labels.length());
        }

        JSONArray result = new JSONArray();
        for (JSONObject label : allLabels) {
            result.put(label);
        }
        log("Retrieved " + allLabels.size() + " labels for project ID " + projectId);
        return result.toString(2);
    }

    @MCPTool(
            name = "testrail_get_label",
            description = "Get a single label by ID",
            integration = "testrail",
            category = "labels"
    )
    public String getLabel(
            @MCPParam(name = "label_id", description = "The label ID", required = true, example = "7")
            String labelId
    ) throws IOException {
        GenericRequest request = new GenericRequest(this, path("/get_label/" + labelId));
        String response = request.execute();
        log("Retrieved label: " + labelId);
        return response;
    }

    @MCPTool(
            name = "testrail_update_label",
            description = "Update a label title in TestRail. Maximum 20 characters allowed.",
            integration = "testrail",
            category = "labels"
    )
    public String updateLabel(
            @MCPParam(name = "label_id", description = "The label ID to update", required = true, example = "7")
            String labelId,
            @MCPParam(name = "project_name", description = "Project name", required = true, example = "My Project")
            String projectName,
            @MCPParam(name = "title", description = "New label title (max 20 characters)", required = true, example = "Release 2.0")
            String title
    ) throws IOException {
        int projectId = getProjectId(projectName);

        JSONObject updateData = new JSONObject();
        updateData.put("project_id", projectId);
        updateData.put("title", title);

        GenericRequest request = new GenericRequest(this, path("/update_label/" + labelId));
        request.setBody(updateData.toString());
        String response = request.post();

        log("Updated label " + labelId + " to: " + title);
        return response;
    }

    // ========== Case Types API ==========

    @MCPTool(
            name = "testrail_get_case_types",
            description = "Get all available case types in TestRail (e.g., Automated, Functionality, Other)",
            integration = "testrail",
            category = "case_types"
    )
    public String getCaseTypes() throws IOException {
        GenericRequest request = new GenericRequest(this, path("/get_case_types"));
        String response = request.execute();
        log("Retrieved case types");
        return response;
    }

    /**
     * Resolves a case type name to its numeric ID.
     * Example: "Functional" -> "6"
     */
    public String resolveTypeIdByName(String typeName) throws IOException {
        String response = getCaseTypes();
        JSONArray types = new JSONArray(response);
        for (int i = 0; i < types.length(); i++) {
            JSONObject type = types.getJSONObject(i);
            if (typeName.trim().equalsIgnoreCase(type.optString("name"))) {
                return String.valueOf(type.getInt("id"));
            }
        }
        throw new IOException("Case type not found: " + typeName);
    }

    /**
     * Resolves an array of label names to comma-separated numeric IDs for a project.
     * Example: ["ai_generated", "Login"] -> "12,7"
     */
    public String resolveLabelIdsByNames(String projectName, String[] labelNames) throws IOException {
        return resolveLabelIdsByProjectId(getProjectId(projectName), labelNames);
    }

    /** ID-based variant — bypasses project name resolution. */
    String resolveLabelIdsByProjectId(int projectId, String[] labelNames) throws IOException {
        String response = getLabelsById(projectId);
        JSONArray labels = new JSONArray(response);
        List<String> ids = new ArrayList<>();
        for (String name : labelNames) {
            String trimmed = name.trim();
            for (int i = 0; i < labels.length(); i++) {
                JSONObject label = labels.getJSONObject(i);
                if (trimmed.equalsIgnoreCase(label.optString("title"))) {
                    ids.add(String.valueOf(label.getInt("id")));
                    break;
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IOException("No labels found for names: " + String.join(", ", labelNames) + " in project ID: " + projectId);
        }
        return String.join(",", ids);
    }

    // ========== TrackerClient Implementation ==========

    @Override
    public TestCase performTicket(String ticketKey, String[] fields) throws IOException {
        // Remove 'C' prefix if present
        String caseId = ticketKey.startsWith("C") ? ticketKey.substring(1) : ticketKey;

        GenericRequest request = new GenericRequest(this, path("/get_case/" + caseId));
        String response = request.execute();

        log("Retrieved test case: " + ticketKey);
        return createTicket(response);
    }

    @Override
    public TestCase createTicket(String body) {
        return new TestCase(basePath, body);
    }

    @Override
    public List<TestCase> searchAndPerform(String searchQuery, String[] fields) throws Exception {
        List<TestCase> results = new ArrayList<>();
        searchAndPerform(ticket -> {
            results.add(ticket);
            return true;
        }, searchQuery, fields);
        return results;
    }

    @Override
    public void searchAndPerform(JiraClient.Performer<TestCase> performer, String searchQuery, String[] fields) throws Exception {
        int limit = 250; // TestRail max per request
        String baseApiPath = "/get_cases&" + searchQuery;
        String nextPagePath = buildPagedPath(baseApiPath, limit, 0);

        while (nextPagePath != null) {
            String response = executeGet(nextPagePath);

            JSONObject responseObj = new JSONObject(response);
            JSONArray cases = responseObj.optJSONArray("cases");

            if (cases == null || cases.length() == 0) {
                break;
            }

            for (int i = 0; i < cases.length(); i++) {
                TestCase testCase = new TestCase(basePath, cases.getJSONObject(i));
                if (!performer.perform(testCase)) {
                    return; // Stop if performer returns false
                }
            }

            nextPagePath = getNextPagePath(responseObj, baseApiPath, limit, cases.length());
        }

        log("Search completed: " + searchQuery);
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary,
                                       String description, FieldsInitializer fieldsInitializer) throws IOException {
        int projectId = getProjectId(project);
        int sectionId = getDefaultSectionId(projectId);

        JSONObject caseData = new JSONObject();
        caseData.put("title", summary);

        // Add description to custom field
        if (description != null && !description.isEmpty()) {
            caseData.put("custom_preconds", description);
        }

        // Apply custom fields
        if (fieldsInitializer != null) {
            fieldsInitializer.init((key, object) -> caseData.put(key, object));
        }

        GenericRequest request = new GenericRequest(this, path("/add_case/" + sectionId));
        request.setBody(caseData.toString());
        String response = request.post();

        JSONObject responseObj = new JSONObject(response);
        Integer caseId = responseObj.optInt("id");

        log("Created test case: C" + caseId);
        return response;
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
        String caseId = key.startsWith("C") ? key.substring(1) : key;

        JSONObject updateData = new JSONObject();

        if (fieldsInitializer != null) {
            fieldsInitializer.init((fieldKey, object) -> updateData.put(fieldKey, object));
        }

        GenericRequest request = new GenericRequest(this, path("/update_case/" + caseId));
        request.setBody(updateData.toString());
        String response = request.post();

        log("Updated test case: " + key);
        return response;
    }

    @Override
    public String updateDescription(String key, String description) throws IOException {
        return updateTicket(key, fields -> fields.set("custom_preconds", description));
    }

    @Override
    public String moveToStatus(String ticketKey, String statusName) throws IOException {
        // TestRail test cases don't have status
        // This would apply to test runs/results instead
        throw new UnsupportedOperationException("Test cases don't have status. Use test runs/results for status.");
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        // TestRail test cases don't have assignee
        // This would apply to test runs instead
        throw new UnsupportedOperationException("Test cases don't have assignee. Use test runs for assignment.");
    }

    @Override
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
        String ticketKey = ticket.getTicketKey();
        try {
            return getCasesByRefs(ticketKey, PROJECT_NAME);
        } catch (Exception e) {
            throw new IOException("Failed to get test cases for " + ticketKey, e);
        }
    }

    @Override
    public String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) throws IOException {
        // Link by adding anotherKey to sourceKey's refs field
        return linkToRequirement(sourceKey, anotherKey);
    }

    @Override
    public void postComment(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException("TestRail test cases don't support comments. Use test results for comments.");
    }

    @Override
    public void postCommentIfNotExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException("TestRail test cases don't support comments.");
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException("TestRail test cases don't support comments.");
    }

    @Override
    public List<? extends IComment> getComments(String ticketKey, ITicket ticket) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void addLabelIfNotExists(ITicket ticket, String label) throws IOException {
        // TestRail labels are managed via label API, not refs
        // For TrackerClient compatibility, we update the label_ids on the case
        // This requires knowing the label ID - for now use updateTicket with label_ids
        throw new UnsupportedOperationException(
                "Use testrail_get_labels to find label IDs, then update case with label_ids. " +
                "Direct label name assignment is not supported by TestRail API.");
    }

    @Override
    public void deleteLabelInTicket(TestCase ticket, String label) throws IOException {
        // TestRail labels are managed via label API, not refs
        throw new UnsupportedOperationException(
                "Use testrail_get_labels to find label IDs, then update case with label_ids. " +
                "Direct label name removal is not supported by TestRail API.");
    }

    @Override
    public String tag(String initiator) {
        return "@" + initiator;
    }

    @Override
    public String getTextFieldsOnly(ITicket ticket) {
        StringBuilder text = new StringBuilder();
        try {
            text.append(ticket.getTicketTitle()).append("\n");
        } catch (IOException e) {
            // Ignore
        }
        text.append(ticket.getTicketDescription());
        return text.toString();
    }

    @Override
    public String buildUrlToSearch(String query) {
        return basePath + "/index.php?/cases/overview";
    }

    @Override
    public String getTicketBrowseUrl(String ticketKey) {
        String caseId = ticketKey.startsWith("C") ? ticketKey.substring(1) : ticketKey;
        return basePath + "/index.php?/cases/view/" + caseId;
    }

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException {
        // TestRail doesn't expose changelog via API
        return null;
    }

    @Override
    public String[] getDefaultQueryFields() {
        return new String[]{
                TestCaseFields.ID,
                TestCaseFields.TITLE,
                TestCaseFields.PRIORITY_ID,
                TestCaseFields.TYPE_ID,
                TestCaseFields.REFS,
                TestCaseFields.CREATED_ON,
                TestCaseFields.UPDATED_ON
        };
    }

    @Override
    public String[] getExtendedQueryFields() {
        return new String[]{
                TestCaseFields.ID,
                TestCaseFields.TITLE,
                TestCaseFields.PRIORITY_ID,
                TestCaseFields.TYPE_ID,
                TestCaseFields.REFS,
                TestCaseFields.CREATED_ON,
                TestCaseFields.UPDATED_ON,
                TestCaseFields.CUSTOM_PRECONDS,
                TestCaseFields.CUSTOM_STEPS,
                TestCaseFields.CUSTOM_EXPECTED,
                TestCaseFields.CUSTOM_STEPS_SEPARATED
        };
    }

    @Override
    public String getDefaultStatusField() {
        return ""; // Test cases don't have status
    }

    @Override
    public String resolveFieldName(String ticketKey, String fieldName) throws IOException {
        // Map user-friendly names to TestRail field names
        switch (fieldName.toLowerCase()) {
            case "title":
            case "summary":
                return "title";
            case "priority":
                return "priority_id";
            case "type":
                return "type_id";
            case "template":
                return "template_id";
            case "milestone":
                return "milestone_id";
            case "refs":
            case "references":
                return "refs";
            case "estimate":
                return "estimate";
            case "description":
            case "preconditions":
                return "custom_preconds";
            case "steps":
                return "custom_steps";
            case "expected":
            case "expected_result":
                return "custom_expected";
            default:
                // If already in custom_ format, return as-is
                if (fieldName.startsWith("custom_")) {
                    return fieldName;
                }
                // Otherwise, assume it's a custom field
                return "custom_" + fieldName.toLowerCase();
        }
    }

    @Override
    public void setLogEnabled(boolean isLogEnabled) {
        this.isLogEnabled = isLogEnabled;
    }

    public void setCacheGetRequestsEnabled(boolean isCacheOfGetRequestsEnabled) {
        // Handled by AbstractRestClient
    }

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException {
        // TestRail uses milestones instead of fix versions
        return Collections.emptyList();
    }

    @Override
    public TextType getTextType() {
        return TextType.MARKDOWN;
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException {
        // TestRail supports attachments via add_attachment_to_case/{case_id}
        // But requires multipart/form-data which needs different handling
        throw new UnsupportedOperationException("File attachments not yet implemented for TestRail");
    }

    @Override
    public File convertUrlToFile(String url) throws Exception {
        throw new UnsupportedOperationException("Image download not implemented for TestRail");
    }

    @Override
    public boolean isValidImageUrl(String url) throws IOException {
        return false;
    }

    // ========== Helper Methods ==========

    /**
     * Get project ID by name, with caching.
     */
    private int getProjectId(String projectName) throws IOException {
        if (projectIdCache.containsKey(projectName)) {
            return projectIdCache.get(projectName);
        }

        int limit = 250;
        String nextPagePath = buildPagedPath("/get_projects", limit, 0);

        while (nextPagePath != null) {
            String response = executeGet(nextPagePath);

            JSONObject responseObj = new JSONObject(response);
            JSONArray projects = responseObj.optJSONArray("projects");

            if (projects == null || projects.length() == 0) {
                break;
            }

            for (int i = 0; i < projects.length(); i++) {
                JSONObject project = projects.getJSONObject(i);
                String name = project.getString("name");
                int id = project.getInt("id");

                projectIdCache.put(name, id);

                if (projectName.equals(name)) {
                    log("Resolved project '" + projectName + "' to ID " + id);
                    return id;
                }
            }

            nextPagePath = getNextPagePath(responseObj, "/get_projects", limit, projects.length());
        }

        throw new IOException("Project not found: " + projectName);
    }

    String executeGet(String apiPath) throws IOException {
        GenericRequest request = new GenericRequest(this, path(apiPath));
        return request.execute();
    }

    private String buildCasesPath(int projectId, String suiteId, String sectionId, String labelId) {
        StringBuilder path = new StringBuilder("/get_cases/" + projectId);
        if (suiteId != null && !suiteId.isEmpty()) {
            path.append("&suite_id=").append(suiteId);
        }
        if (sectionId != null && !sectionId.isEmpty()) {
            path.append("&section_id=").append(sectionId);
        }
        if (labelId != null && !labelId.isEmpty()) {
            path.append("&label_ids=").append(labelId);
        }
        return path.toString();
    }

    private String buildCasesByRefsPath(int projectId, String refs) {
        return "/get_cases/" + projectId + "&refs=" + URLEncoder.encode(refs, StandardCharsets.UTF_8);
    }

    private String buildPagedPath(String baseApiPath, int limit, int offset) {
        return baseApiPath + "&limit=" + limit + "&offset=" + offset;
    }

    private String getNextPagePath(JSONObject responseObj, String baseApiPath, int limit, int currentPageItemCount) {
        if (responseObj.has("_links")) {
            String nextLink = extractNextPageLink(responseObj);
            return nextLink == null ? null : normalizePagedApiPath(nextLink);
        }
        if (currentPageItemCount < limit) {
            return null;
        }
        return buildPagedPath(baseApiPath, limit, responseObj.optInt("offset", 0) + limit);
    }

    private String extractNextPageLink(JSONObject responseObj) {
        JSONObject links = responseObj.optJSONObject("_links");
        if (links == null) {
            return null;
        }

        Object nextValue = links.opt("next");
        if (nextValue == null || JSONObject.NULL.equals(nextValue)) {
            return null;
        }

        String nextLink = links.optString("next", null);
        if (nextLink == null || nextLink.isBlank() || "null".equalsIgnoreCase(nextLink)) {
            return null;
        }
        return nextLink;
    }

    private String normalizePagedApiPath(String pathOrUrl) {
        String normalized = pathOrUrl.trim();
        String absolutePrefix = "/index.php?/api/v2";
        String relativePrefix = "/api/v2";

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            int apiIndex = normalized.indexOf(relativePrefix);
            if (apiIndex >= 0) {
                normalized = normalized.substring(apiIndex + relativePrefix.length());
            }
        } else if (normalized.startsWith(absolutePrefix)) {
            normalized = normalized.substring(absolutePrefix.length());
        } else if (normalized.startsWith(relativePrefix)) {
            normalized = normalized.substring(relativePrefix.length());
        }

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private void appendAll(JSONArray target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            target.put(source.get(i));
        }
    }

    /**
     * Get default section ID for a project (creates "Test Cases" section if needed).
     */
    int getDefaultSectionId(int projectId) throws IOException {
        if (defaultSectionCache.containsKey(projectId)) {
            return defaultSectionCache.get(projectId);
        }

        // Get sections for project
        GenericRequest request = new GenericRequest(this, path("/get_sections/" + projectId));
        String response = request.execute();

        // Parse response object which contains "sections" array
        JSONObject responseObj = new JSONObject(response);
        JSONArray sections = responseObj.optJSONArray("sections");

        if (sections != null && sections.length() > 0) {
            // Return first section
            int sectionId = sections.getJSONObject(0).getInt("id");
            defaultSectionCache.put(projectId, sectionId);
            log("Using section ID " + sectionId + " for project " + projectId);
            return sectionId;
        }

        // Create default section if none exist
        JSONObject sectionData = new JSONObject();
        sectionData.put("name", "Test Cases");

        GenericRequest createRequest = new GenericRequest(this, path("/add_section/" + projectId));
        createRequest.setBody(sectionData.toString());
        String createResponse = createRequest.post();

        JSONObject newSection = new JSONObject(createResponse);
        int sectionId = newSection.getInt("id");
        defaultSectionCache.put(projectId, sectionId);

        log("Created default section for project ID " + projectId);
        return sectionId;
    }

    /**
     * Converts standard Markdown tables to TestRail's custom table format.
     * <p>
     * Markdown format:
     * <pre>
     * | Col 1 | Col 2 | Col 3 |
     * |-------|-------|-------|
     * | val1  | val2  | val3  |
     * </pre>
     * <p>
     * TestRail format:
     * <pre>
     * |||:Col 1|:Col 2|:Col 3
     * ||val1|val2|val3
     * </pre>
     * <p>
     * If no Markdown tables are found, the text is returned as-is.
     * Text already in TestRail format (starting with |||) is not modified.
     */
    static String convertMarkdownTablesToTestRailFormat(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // If text already contains TestRail table format, return as-is
        if (text.contains("|||")) {
            return text;
        }

        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i].trim();

            // Detect Markdown table: line starts with | and contains at least 2 |
            if (isMarkdownTableRow(line)) {
                // Collect all table lines
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && isMarkdownTableRow(lines[i].trim())) {
                    tableLines.add(lines[i].trim());
                    i++;
                }

                // Convert collected table
                result.append(convertSingleMarkdownTable(tableLines));
            } else {
                result.append(lines[i]);
                if (i < lines.length - 1) {
                    result.append("\n");
                }
                i++;
            }
        }

        return result.toString();
    }

    private static boolean isMarkdownTableRow(String line) {
        return line.startsWith("|") && line.endsWith("|") && line.length() > 2;
    }

    private static String convertSingleMarkdownTable(List<String> tableLines) {
        if (tableLines.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean isFirstDataRow = true;

        for (String line : tableLines) {
            // Skip separator lines (|---|---|---|)
            if (line.matches("\\|[\\s\\-:|]+\\|")) {
                continue;
            }

            // Parse cells: split by | and trim
            String[] cells = line.split("\\|");
            List<String> cleanCells = new ArrayList<>();
            for (String cell : cells) {
                String trimmed = cell.trim();
                if (!trimmed.isEmpty()) {
                    cleanCells.add(trimmed);
                }
            }

            if (cleanCells.isEmpty()) {
                continue;
            }

            if (isFirstDataRow) {
                // Header row: |||:Col 1|:Col 2|:Col 3
                result.append("||");
                for (String cell : cleanCells) {
                    result.append("|:").append(cell);
                }
                result.append("\n");
                isFirstDataRow = false;
            } else {
                // Data row: ||val1|val2|val3
                result.append("||");
                for (int j = 0; j < cleanCells.size(); j++) {
                    result.append(cleanCells.get(j));
                    if (j < cleanCells.size() - 1) {
                        result.append("|");
                    }
                }
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Converts Markdown tables to HTML table format for use in TestRail Steps template.
     * <p>
     * The Steps template (template_id=2) stores content as HTML, so all content must be
     * properly wrapped: plain text in {@code <p>} tags, tables as
     * {@code <table><thead>...</thead><tbody>...</tbody></table>}.
     * <p>
     * Markdown format:
     * <pre>
     * Some text
     * | Col 1 | Col 2 |
     * |-------|-------|
     * | val1  | val2  |
     * </pre>
     * <p>
     * HTML output:
     * <pre>
     * &lt;p&gt;Some text&lt;/p&gt;&lt;table&gt;&lt;thead&gt;&lt;tr&gt;&lt;th&gt;Col 1&lt;/th&gt;...&lt;/tr&gt;&lt;/thead&gt;
     * &lt;tbody&gt;&lt;tr&gt;&lt;td&gt;val1&lt;/td&gt;...&lt;/tr&gt;&lt;/tbody&gt;&lt;/table&gt;
     * </pre>
     */
    static String convertMarkdownTablesToHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        List<String> pendingTextLines = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i].trim();

            if (isMarkdownTableRow(line)) {
                // Flush pending text as <p> paragraphs before the table
                for (String textLine : pendingTextLines) {
                    if (!textLine.isEmpty()) {
                        result.append("<p>").append(textLine).append("</p>");
                    }
                }
                pendingTextLines.clear();

                // Collect contiguous table rows
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && isMarkdownTableRow(lines[i].trim())) {
                    tableLines.add(lines[i].trim());
                    i++;
                }
                result.append(convertSingleMarkdownTableToHtml(tableLines));
            } else {
                pendingTextLines.add(line);
                i++;
            }
        }

        // Flush remaining text as <p> paragraphs
        for (String textLine : pendingTextLines) {
            if (!textLine.isEmpty()) {
                result.append("<p>").append(textLine).append("</p>");
            }
        }

        return result.toString();
    }

    private static String convertSingleMarkdownTableToHtml(List<String> tableLines) {
        if (tableLines.isEmpty()) {
            return "";
        }

        List<List<String>> parsedRows = new ArrayList<>();
        for (String line : tableLines) {
            // Skip separator rows
            if (line.matches("\\|[\\s\\-:|]+\\|")) {
                continue;
            }
            String[] cells = line.split("\\|");
            List<String> cleanCells = new ArrayList<>();
            for (String cell : cells) {
                String trimmed = cell.trim();
                if (!trimmed.isEmpty()) {
                    cleanCells.add(trimmed);
                }
            }
            if (!cleanCells.isEmpty()) {
                parsedRows.add(cleanCells);
            }
        }

        if (parsedRows.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder("<table>");

        // First row → <thead>
        html.append("<thead><tr>");
        for (String cell : parsedRows.get(0)) {
            html.append("<th>").append(cell).append("</th>");
        }
        html.append("</tr></thead>");

        // Remaining rows → <tbody>
        html.append("<tbody>");
        for (int i = 1; i < parsedRows.size(); i++) {
            html.append("<tr>");
            for (String cell : parsedRows.get(i)) {
                html.append("<td>").append(cell).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }
}
