// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.jira.model.IssueTypeScheme;
import com.github.istin.dmtools.atlassian.jira.model.Project;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.model.WorkflowScheme;
import com.github.istin.dmtools.common.model.ITicket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Jira project structure methods:
 * getProjectDetails, getProjectIssueTypeScheme, getProjectWorkflowScheme,
 * assignIssueTypeSchemeToProject, assignWorkflowSchemeToProject, copyProjectStructure.
 */
public class JiraClientProjectStructureTest {

    private static final String BASE = "http://example.atlassian.net";

    private JiraClient<Ticket> jiraClient;

    @Before
    public void setUp() throws IOException {
        jiraClient = spy(new JiraClient<Ticket>(BASE, "auth") {
            @Override public String getTextFieldsOnly(ITicket ticket) { return ""; }
            @Override public String[] getDefaultQueryFields() { return new String[0]; }
            @Override public String[] getExtendedQueryFields() { return new String[0]; }
            @Override public List<? extends ITicket> getTestCases(ITicket t, String type) { return List.of(); }
            @Override public TextType getTextType() { return TextType.MARKDOWN; }
            @Override public Ticket createTicket(String body) { return new Ticket(new JSONObject()); }
        });
    }

    // ─── getProjectDetails ────────────────────────────────────────────────────

    @Test
    public void testGetProjectDetails_parsesIdKeyName() throws IOException {
        JSONObject projectJson = new JSONObject()
                .put("id", "10001").put("key", "TP").put("name", "Test Project");
        doReturn(projectJson.toString())
                .when(jiraClient).executeGet(contains("project/TP"));

        Project project = jiraClient.getProjectDetails("TP");

        assertEquals("10001", project.getId());
        assertEquals("TP", project.getKey());
        assertEquals("Test Project", project.getName());
    }

    // ─── getProjectIssueTypeScheme ─────────────────────────────────────────────

    @Test
    public void testGetProjectIssueTypeScheme_returnsSchemeName() throws IOException {
        stubProjectDetails("MYTUBE", "10001");
        JSONObject schemeJson = new JSONObject().put("id", "10050").put("name", "My Scheme");
        JSONObject apiResponse = new JSONObject()
                .put("values", new JSONArray().put(new JSONObject().put("issueTypeScheme", schemeJson)));
        doReturn(apiResponse.toString())
                .when(jiraClient).executeGet(contains("/rest/api/3/issuetypescheme/project?projectId=10001"));

        IssueTypeScheme scheme = jiraClient.getProjectIssueTypeScheme("MYTUBE");

        assertEquals("10050", scheme.getId());
        assertEquals("My Scheme", scheme.getName());
    }

    @Test(expected = IOException.class)
    public void testGetProjectIssueTypeScheme_throwsWhenNoValues() throws IOException {
        stubProjectDetails("MYTUBE", "10001");
        JSONObject apiResponse = new JSONObject().put("values", new JSONArray());
        doReturn(apiResponse.toString())
                .when(jiraClient).executeGet(contains("issuetypescheme"));
        jiraClient.getProjectIssueTypeScheme("MYTUBE");
    }

    // ─── getProjectWorkflowScheme ──────────────────────────────────────────────

    @Test
    public void testGetProjectWorkflowScheme_returnsScheme() throws IOException {
        stubProjectDetails("MYTUBE", "10001");
        JSONObject wsJson = new JSONObject()
                .put("id", "20050").put("name", "My Workflow Scheme").put("defaultWorkflow", "jira");
        JSONObject apiResponse = new JSONObject()
                .put("values", new JSONArray().put(new JSONObject().put("workflowScheme", wsJson)));
        doReturn(apiResponse.toString())
                .when(jiraClient).executeGet(contains("/rest/api/3/workflowscheme/project?projectId=10001"));

        WorkflowScheme scheme = jiraClient.getProjectWorkflowScheme("MYTUBE");

        assertEquals("20050", scheme.getId());
        assertEquals("My Workflow Scheme", scheme.getName());
        assertEquals("jira", scheme.getDefaultWorkflow());
    }

    @Test(expected = IOException.class)
    public void testGetProjectWorkflowScheme_throwsWhenNoValues() throws IOException {
        stubProjectDetails("MYTUBE", "10001");
        JSONObject apiResponse = new JSONObject().put("values", new JSONArray());
        doReturn(apiResponse.toString())
                .when(jiraClient).executeGet(contains("workflowscheme"));
        jiraClient.getProjectWorkflowScheme("MYTUBE");
    }

    // ─── assignIssueTypeSchemeToProject ───────────────────────────────────────

    @Test
    public void testAssignIssueTypeSchemeToProject_callsPutWithCorrectUrl() throws IOException {
        doNothing().when(jiraClient).executePut(anyString(), anyString());

        jiraClient.assignIssueTypeSchemeToProject("10050", "22222");

        verify(jiraClient).executePut(
                eq(BASE + "/rest/api/3/issuetypescheme/10050/project"),
                argThat(body -> new JSONObject(body).getJSONArray("projectIds").getString(0).equals("22222")));
    }

    // ─── assignWorkflowSchemeToProject ────────────────────────────────────────

    @Test
    public void testAssignWorkflowSchemeToProject_callsPutWithCorrectUrl() throws IOException {
        doNothing().when(jiraClient).executePut(anyString(), anyString());

        jiraClient.assignWorkflowSchemeToProject("20050", "22222");

        verify(jiraClient).executePut(
                eq(BASE + "/rest/api/3/workflowscheme/project"),
                argThat(body -> {
                    JSONObject b = new JSONObject(body);
                    return "20050".equals(b.getString("workflowSchemeId"))
                            && "22222".equals(b.getString("projectId"));
                }));
    }

    // ─── copyProjectStructure ─────────────────────────────────────────────────

    @Test
    public void testCopyProjectStructure_returnsJsonSummaryAndDelegates() throws IOException {
        IssueTypeScheme mockScheme = new IssueTypeScheme(
                new JSONObject().put("id", "10050").put("name", "My Scheme"));
        WorkflowScheme mockWorkflow = new WorkflowScheme(
                new JSONObject().put("id", "20050").put("name", "My Workflow"));

        doReturn(buildProject("11111", "MYTUBE")).when(jiraClient).getProjectDetails("MYTUBE");
        doReturn(buildProject("22222", "TP")).when(jiraClient).getProjectDetails("TP");
        doReturn(mockScheme).when(jiraClient).getProjectIssueTypeScheme("MYTUBE");
        doReturn(mockWorkflow).when(jiraClient).getProjectWorkflowScheme("MYTUBE");
        doNothing().when(jiraClient).assignIssueTypeSchemeToProject("10050", "22222");
        doNothing().when(jiraClient).assignWorkflowSchemeToProject("20050", "22222");

        String result = jiraClient.copyProjectStructure("MYTUBE", "TP");
        JSONObject json = new JSONObject(result);

        assertEquals("MYTUBE", json.getString("sourceProject"));
        assertEquals("TP", json.getString("targetProject"));
        assertEquals("10050", json.getString("issueTypeSchemeId"));
        assertEquals("My Scheme", json.getString("issueTypeSchemeName"));
        assertEquals("20050", json.getString("workflowSchemeId"));
        assertEquals("My Workflow", json.getString("workflowSchemeName"));

        verify(jiraClient).assignIssueTypeSchemeToProject("10050", "22222");
        verify(jiraClient).assignWorkflowSchemeToProject("20050", "22222");
    }

    // ─── Model unit tests ─────────────────────────────────────────────────────

    @Test
    public void testIssueTypeSchemeModel_getters() {
        JSONArray ids = new JSONArray().put("1").put("2");
        JSONObject json = new JSONObject()
                .put("id", "10050").put("name", "Scheme Name").put("description", "A description")
                .put("defaultIssueTypeId", "1").put("issueTypeIds", ids);
        IssueTypeScheme scheme = new IssueTypeScheme(json);
        assertEquals("10050", scheme.getId());
        assertEquals("Scheme Name", scheme.getName());
        assertEquals("A description", scheme.getDescription());
        assertEquals("1", scheme.getDefaultIssueTypeId());
        assertEquals(2, scheme.getIssueTypeIds().size());
        assertTrue(scheme.getIssueTypeIds().contains("1"));
        assertTrue(scheme.getIssueTypeIds().contains("2"));
    }

    @Test
    public void testWorkflowSchemeModel_getters() {
        JSONObject mappings = new JSONObject().put("10001", "Software Development Workflow");
        JSONObject json = new JSONObject()
                .put("id", "20050").put("name", "WF Scheme").put("description", "desc")
                .put("defaultWorkflow", "jira").put("issueTypeMappings", mappings);
        WorkflowScheme scheme = new WorkflowScheme(json);
        assertEquals("20050", scheme.getId());
        assertEquals("WF Scheme", scheme.getName());
        assertEquals("jira", scheme.getDefaultWorkflow());
        assertEquals(1, scheme.getIssueTypeMappings().size());
        assertEquals("Software Development Workflow", scheme.getIssueTypeMappings().get("10001"));
    }

    @Test
    public void testProjectModel_getters() {
        JSONObject json = new JSONObject().put("id", "10001").put("key", "TP").put("name", "Test Project");
        Project project = new Project(json);
        assertEquals("10001", project.getId());
        assertEquals("TP", project.getKey());
        assertEquals("Test Project", project.getName());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Project buildProject(String id, String key) {
        return new Project(new JSONObject().put("id", id).put("key", key).put("name", key + " Project"));
    }

    private void stubProjectDetails(String projectKey, String projectId) throws IOException {
        doReturn(buildProject(projectId, projectKey)).when(jiraClient).getProjectDetails(projectKey);
    }

    // ─── deleteProject ────────────────────────────────────────────────────────

    @Test
    public void testDeleteProject_requiresConfirmation() throws IOException {
        String result = jiraClient.deleteProject("TP", "false");
        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertTrue(json.getString("message").contains("confirmDelete"));
    }

    @Test
    public void testDeleteProject_callsDeleteEndpoint() throws IOException {
        doReturn("").when(jiraClient).executeDelete(contains("project/TP"));
        String result = jiraClient.deleteProject("TP", "true");
        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals("TP", json.getString("projectKey"));
        verify(jiraClient).executeDelete(contains("project/TP"));
    }

    // ─── restoreProject ───────────────────────────────────────────────────────

    @Test
    public void testRestoreProject_callsRestoreEndpoint() throws IOException {
        String responseBody = new JSONObject().put("key", "TP").put("id", "10033").put("name", "Test Playground").toString();
        doReturn(responseBody).when(jiraClient).executePost(contains("project/TP/restore"), eq(""));
        String result = jiraClient.restoreProject("TP");
        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals("TP", json.getString("projectKey"));
        verify(jiraClient).executePost(contains("project/TP/restore"), eq(""));
    }

    // ─── cloneProject ─────────────────────────────────────────────────────────

    @Test
    public void testCloneProject_createsNewProject() throws IOException {
        JSONObject sourceJson = new JSONObject()
                .put("id", "10099").put("key", "MYTUBE").put("name", "MyTube")
                .put("style", "next-gen").put("projectTypeKey", "software")
                .put("lead", new JSONObject().put("accountId", "abc123"))
                .put("issueTypes", new JSONArray()
                        .put(new JSONObject().put("name", "Task").put("id", "1"))
                        .put(new JSONObject().put("name", "Bug").put("id", "2"))
                        .put(new JSONObject().put("name", "Test Case").put("id", "3")));
        Project sourceProject = new Project(sourceJson);

        JSONObject newProjectJson = new JSONObject()
                .put("id", "10200").put("key", "TP2").put("name", "Test Playground 2")
                .put("style", "next-gen").put("projectTypeKey", "software")
                .put("lead", new JSONObject().put("accountId", "abc123"))
                .put("issueTypes", new JSONArray()
                        .put(new JSONObject().put("name", "Task").put("id", "4"))
                        .put(new JSONObject().put("name", "Bug").put("id", "5")));
        Project newProject = new Project(newProjectJson);

        doReturn(sourceProject).when(jiraClient).getProjectDetails("MYTUBE");
        doReturn(newProject).when(jiraClient).getProjectDetails("TP2");
        String createResponse = new JSONObject().put("key", "TP2").put("id", "10200").toString();
        doReturn(createResponse).when(jiraClient).executePost(contains("project"), anyString());

        String result = jiraClient.cloneProject("MYTUBE", "TP2", "Test Playground 2");
        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals("TP2", json.getString("projectKey"));
        assertTrue(json.has("missingIssueTypes"));
        // Test Case should be reported as missing
        boolean foundTestCase = false;
        JSONArray missing = json.getJSONArray("missingIssueTypes");
        for (int i = 0; i < missing.length(); i++) {
            if ("Test Case".equals(missing.getString(i))) foundTestCase = true;
        }
        assertTrue("Test Case should be missing", foundTestCase);
    }

    // ─── Project model extended getters ──────────────────────────────────────

    @Test
    public void testProjectModel_getLeadAccountId() {
        JSONObject json = new JSONObject()
                .put("id", "10099")
                .put("lead", new JSONObject().put("accountId", "abc123"));
        Project project = new Project(json);
        assertEquals("abc123", project.getLeadAccountId());
    }

    @Test
    public void testProjectModel_getProjectTypeKey() {
        JSONObject json = new JSONObject()
                .put("id", "10099")
                .put("projectTypeKey", "software");
        Project project = new Project(json);
        assertEquals("software", project.getProjectTypeKey());
    }

    // ─── syncProjectWorkflow ──────────────────────────────────────────────────

    @Test
    public void testSyncProjectWorkflow_success() throws IOException {
        // Mock source project statuses (v2)
        JSONArray sourceV2 = new JSONArray().put(new JSONObject()
                .put("statuses", new JSONArray()
                        .put(new JSONObject().put("id","10100").put("name","Backlog"))
                        .put(new JSONObject().put("id","10101").put("name","In Dev"))
                        .put(new JSONObject().put("id","10102").put("name","Done"))));
        // Mock source project statuses (v3 categories)
        JSONArray sourceV3 = new JSONArray().put(new JSONObject()
                .put("statuses", new JSONArray()
                        .put(new JSONObject().put("id","10100").put("statusCategory", new JSONObject().put("key","new")))
                        .put(new JSONObject().put("id","10101").put("statusCategory", new JSONObject().put("key","indeterminate")))
                        .put(new JSONObject().put("id","10102").put("statusCategory", new JSONObject().put("key","done")))));

        // Mock target project details
        JSONObject targetProjectJson = new JSONObject()
                .put("id", "10033").put("key", "TP").put("name", "Test Project")
                .put("issueTypes", new JSONArray()
                        .put(new JSONObject().put("id","10166").put("name","Task")));
        // Mock target statuses (v2) — same statuses already exist
        JSONArray targetV2 = new JSONArray().put(new JSONObject()
                .put("statuses", new JSONArray()
                        .put(new JSONObject().put("id","10100").put("name","Backlog"))
                        .put(new JSONObject().put("id","10101").put("name","In Dev"))
                        .put(new JSONObject().put("id","10102").put("name","Done"))));
        JSONArray targetV3 = sourceV3; // same categories

        // Mock workflow GET
        JSONObject workflowsResp = new JSONObject()
                .put("workflows", new JSONArray().put(new JSONObject()
                        .put("id", "wf-test-id")
                        .put("name", "Software workflow for project 10033")
                        .put("version", new JSONObject().put("id","ver-id").put("versionNumber",5))
                        .put("statuses", new JSONArray()
                                .put(new JSONObject().put("statusReference","10100"))
                                .put(new JSONObject().put("statusReference","10200"))))); // 10200 is extra

        doReturn(sourceV3.toString()).when(jiraClient).executeGet(contains("/rest/api/3/project/SOURCE/statuses"));
        doReturn(sourceV2.toString()).when(jiraClient).executeGet(contains("project/SOURCE/statuses"));
        doReturn(targetProjectJson.toString()).when(jiraClient).executeGet(contains("project/TP"));
        doReturn(targetV3.toString()).when(jiraClient).executeGet(contains("/rest/api/3/project/TP/statuses"));
        doReturn(targetV2.toString()).when(jiraClient).executeGet(contains("project/TP/statuses"));
        doReturn(workflowsResp.toString()).when(jiraClient).executePost(contains("/rest/api/3/workflows"), anyString());
        doReturn("{}").when(jiraClient).executePost(contains("/rest/api/3/workflows/update"), anyString());

        String result = jiraClient.syncProjectWorkflow("SOURCE", "TP");
        JSONObject json = new JSONObject(result);

        assertEquals("SOURCE", json.getString("sourceProject"));
        assertEquals("TP", json.getString("targetProject"));
        assertEquals("success", json.getString("result"));
        assertEquals(3, json.getInt("statusesSynced"));
        // 10200 was in current workflow but NOT in source → should be in removed
        assertEquals(1, json.getInt("removedStatuses"));
    }

    @Test
    public void testSyncProjectWorkflow_noWorkflowFound() throws IOException {
        JSONArray sourceV2 = new JSONArray().put(new JSONObject()
                .put("statuses", new JSONArray().put(new JSONObject().put("id","10100").put("name","Todo"))));
        JSONArray sourceV3 = new JSONArray().put(new JSONObject()
                .put("statuses", new JSONArray().put(new JSONObject().put("id","10100")
                        .put("statusCategory", new JSONObject().put("key","new")))));
        JSONObject targetProject = new JSONObject().put("id","10033").put("key","TP").put("name","TP")
                .put("issueTypes", new JSONArray());
        JSONArray targetV2 = sourceV2;
        JSONArray targetV3 = sourceV3;
        JSONObject emptyWorkflows = new JSONObject().put("workflows", new JSONArray());

        doReturn(sourceV3.toString()).when(jiraClient).executeGet(contains("/rest/api/3/project/SRC/statuses"));
        doReturn(sourceV2.toString()).when(jiraClient).executeGet(contains("project/SRC/statuses"));
        doReturn(targetProject.toString()).when(jiraClient).executeGet(contains("project/TP"));
        doReturn(targetV3.toString()).when(jiraClient).executeGet(contains("/rest/api/3/project/TP/statuses"));
        doReturn(targetV2.toString()).when(jiraClient).executeGet(contains("project/TP/statuses"));
        doReturn(emptyWorkflows.toString()).when(jiraClient).executePost(contains("/rest/api/3/workflows"), anyString());

        try {
            jiraClient.syncProjectWorkflow("SRC", "TP");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("No workflow found"));
        }
    }
}
