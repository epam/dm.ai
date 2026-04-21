// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.ado;

import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AzureDevOpsClient pipeline operations:
 * listPipelines, triggerPipeline, listPipelineRuns, getPipelineRun, getPipelineLogs
 */
public class AzureDevOpsPipelineToolsTest {

    private TestableAzureDevOpsClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = new TestableAzureDevOpsClient("TestOrg", "TestProject", "fake-pat");
    }

    // ---- listPipelines ----

    @Test
    void testListPipelines_returnsJsonFromApi() throws IOException {
        String fakeResponse = "{\"count\":2,\"value\":[{\"id\":1,\"name\":\"pipeline-a\"},{\"id\":2,\"name\":\"pipeline-b\"}]}";
        client.setMockResponse("/TestProject/_apis/pipelines", fakeResponse);

        JSONObject result = client.listPipelines();

        assertEquals(2, result.getInt("count"));
        assertEquals("pipeline-a", result.getJSONArray("value").getJSONObject(0).getString("name"));
    }

    // ---- triggerPipeline ----

    @Test
    void testTriggerPipeline_noOptionalParams_postsMinimalBody() throws IOException {
        String fakeResponse = "{\"id\":42,\"state\":\"inProgress\"}";
        client.setMockPostResponse("/TestProject/_apis/pipelines/9/runs", fakeResponse);

        JSONObject result = client.triggerPipeline(9, null, null);

        assertEquals(42, result.getInt("id"));
        assertEquals("inProgress", result.getString("state"));
        // Verify an empty body was posted (no branch, no variables)
        String postedBody = client.getLastPostedBody("/TestProject/_apis/pipelines/9/runs");
        JSONObject posted = new JSONObject(postedBody);
        assertFalse(posted.has("resources"), "No resources key expected when branch is null");
        assertFalse(posted.has("variables"), "No variables key expected when variablesJson is null");
    }

    @Test
    void testTriggerPipeline_withBranch_includesBranchInBody() throws IOException {
        String fakeResponse = "{\"id\":43,\"state\":\"inProgress\"}";
        client.setMockPostResponse("/TestProject/_apis/pipelines/9/runs", fakeResponse);

        client.triggerPipeline(9, "feature/my-branch", null);

        String postedBody = client.getLastPostedBody("/TestProject/_apis/pipelines/9/runs");
        JSONObject posted = new JSONObject(postedBody);
        assertTrue(posted.has("resources"), "Should include resources when branch is set");
        String refName = posted.getJSONObject("resources")
                .getJSONObject("repositories")
                .getJSONObject("self")
                .getString("refName");
        assertEquals("refs/heads/feature/my-branch", refName);
    }

    @Test
    void testTriggerPipeline_withVariables_includesVariablesInBody() throws IOException {
        String fakeResponse = "{\"id\":44,\"state\":\"inProgress\"}";
        client.setMockPostResponse("/TestProject/_apis/pipelines/9/runs", fakeResponse);

        client.triggerPipeline(9, null, "{\"myVar\":\"hello\"}");

        String postedBody = client.getLastPostedBody("/TestProject/_apis/pipelines/9/runs");
        JSONObject posted = new JSONObject(postedBody);
        assertTrue(posted.has("variables"), "Should include variables");
        JSONObject vars = posted.getJSONObject("variables");
        assertTrue(vars.has("myVar"));
        assertEquals("hello", vars.getJSONObject("myVar").getString("value"));
        assertFalse(vars.getJSONObject("myVar").getBoolean("isSecret"));
    }

    // ---- listPipelineRuns ----

    @Test
    void testListPipelineRuns_defaultsToTop10() throws IOException {
        String fakeResponse = "{\"count\":1,\"value\":[{\"id\":8,\"state\":\"completed\"}]}";
        client.setMockResponse("/TestProject/_apis/pipelines/9/runs", fakeResponse);

        JSONObject result = client.listPipelineRuns(9, null);

        assertEquals(1, result.getInt("count"));
        // Verify $top=10 was appended in the URL called
        assertTrue(client.getLastCalledUrl().contains("$top=10"),
                "Expected $top=10 in URL, got: " + client.getLastCalledUrl());
    }

    @Test
    void testListPipelineRuns_customTop() throws IOException {
        String fakeResponse = "{\"count\":0,\"value\":[]}";
        client.setMockResponse("/TestProject/_apis/pipelines/9/runs", fakeResponse);

        client.listPipelineRuns(9, 25);

        assertTrue(client.getLastCalledUrl().contains("$top=25"),
                "Expected $top=25 in URL, got: " + client.getLastCalledUrl());
    }

    // ---- getPipelineRun ----

    @Test
    void testGetPipelineRun_returnsRunDetails() throws IOException {
        String fakeResponse = "{\"id\":8,\"state\":\"completed\",\"result\":\"succeeded\"}";
        client.setMockResponse("/TestProject/_apis/pipelines/9/runs/8", fakeResponse);

        JSONObject result = client.getPipelineRun(9, 8);

        assertEquals(8, result.getInt("id"));
        assertEquals("succeeded", result.getString("result"));
    }

    // ---- getPipelineLogs ----

    @Test
    void testGetPipelineLogs_noRecords_returnsNoLogsMessage() throws IOException {
        String timelineResponse = "{\"records\":[]}";
        client.setMockResponse("/TestProject/_apis/build/builds/8/timeline", timelineResponse);

        String result = client.getPipelineLogs(8, null, null);

        assertTrue(result.contains("no timeline records found"), "Got: " + result);
    }

    @Test
    void testGetPipelineLogs_skipsStageAndCheckpointRecords() throws IOException {
        String timelineResponse = "{\"records\":["
                + "{\"type\":\"Stage\",\"name\":\"__default\",\"state\":\"completed\",\"log\":{\"id\":1}},"
                + "{\"type\":\"Checkpoint\",\"name\":\"cp\",\"state\":\"completed\",\"log\":{\"id\":2}}"
                + "]}";
        client.setMockResponse("/TestProject/_apis/build/builds/8/timeline", timelineResponse);

        String result = client.getPipelineLogs(8, null, null);

        assertEquals("(no logs found for build 8)", result);
    }

    @Test
    void testGetPipelineLogs_includesJobAndTaskLogs() throws IOException {
        String timelineResponse = "{\"records\":["
                + "{\"type\":\"Job\",\"name\":\"My Job\",\"state\":\"completed\",\"log\":{\"id\":10}},"
                + "{\"type\":\"Task\",\"name\":\"Run Tests\",\"state\":\"completed\",\"log\":{\"id\":11}}"
                + "]}";
        client.setMockResponse("/TestProject/_apis/build/builds/8/timeline", timelineResponse);
        client.setMockResponse("/TestProject/_apis/build/builds/8/logs/10", "line1\nline2\nline3\n");
        client.setMockResponse("/TestProject/_apis/build/builds/8/logs/11", "test output\n");

        String result = client.getPipelineLogs(8, null, null);

        assertTrue(result.contains("=== Job: My Job ==="), "Got: " + result);
        assertTrue(result.contains("=== Task: Run Tests ==="), "Got: " + result);
        assertTrue(result.contains("line1"), "Got: " + result);
        assertTrue(result.contains("test output"), "Got: " + result);
    }

    @Test
    void testGetPipelineLogs_taskNameFilter_onlyMatchingTask() throws IOException {
        String timelineResponse = "{\"records\":["
                + "{\"type\":\"Task\",\"name\":\"Initialize job\",\"state\":\"completed\",\"log\":{\"id\":4}},"
                + "{\"type\":\"Task\",\"name\":\"PowerShell\",\"state\":\"completed\",\"log\":{\"id\":7}}"
                + "]}";
        client.setMockResponse("/TestProject/_apis/build/builds/8/timeline", timelineResponse);
        client.setMockResponse("/TestProject/_apis/build/builds/8/logs/7", "ps output\n");

        String result = client.getPipelineLogs(8, "powershell", null);

        assertTrue(result.contains("=== Task: PowerShell ==="), "Got: " + result);
        assertFalse(result.contains("Initialize job"), "Should not include Initialize job, got: " + result);
    }

    @Test
    void testGetPipelineLogs_tailLines_truncatesLongLogs() throws IOException {
        String timelineResponse = "{\"records\":["
                + "{\"type\":\"Task\",\"name\":\"Build\",\"state\":\"completed\",\"log\":{\"id\":5}}"
                + "]}";
        client.setMockResponse("/TestProject/_apis/build/builds/8/timeline", timelineResponse);

        // Build a 10-line log
        StringBuilder logContent = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            logContent.append("line").append(i).append("\n");
        }
        client.setMockResponse("/TestProject/_apis/build/builds/8/logs/5", logContent.toString());

        // Request only last 3 lines
        String result = client.getPipelineLogs(8, null, 3);

        assertTrue(result.contains("line8"), "Got: " + result);
        assertTrue(result.contains("line9"), "Got: " + result);
        assertTrue(result.contains("line10"), "Got: " + result);
        assertFalse(result.contains("line1\n"), "Should not include line1 with tail=3, got: " + result);
    }

    // ---- Testable subclass ----

    private static class TestableAzureDevOpsClient extends AzureDevOpsClient {

        /** URL-keyed GET responses (partial path match) */
        private final Map<String, String> getResponses = new HashMap<>();
        /** URL-keyed POST responses (partial path match) */
        private final Map<String, String> postResponses = new HashMap<>();
        /** Last posted body per path */
        private final Map<String, String> postedBodies = new HashMap<>();
        private String lastCalledUrl = "";

        TestableAzureDevOpsClient(String org, String project, String pat) throws IOException {
            super(org, project, pat);
        }

        void setMockResponse(String pathFragment, String response) {
            getResponses.put(pathFragment, response);
        }

        void setMockPostResponse(String pathFragment, String response) {
            postResponses.put(pathFragment, response);
        }

        String getLastPostedBody(String pathFragment) {
            return postedBodies.get(pathFragment);
        }

        String getLastCalledUrl() {
            return lastCalledUrl;
        }

        @Override
        public String execute(GenericRequest request) throws IOException {
            String url = request.url();
            lastCalledUrl = url;
            for (Map.Entry<String, String> entry : getResponses.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            throw new IOException("No mock GET response configured for URL: " + url);
        }

        @Override
        public String post(GenericRequest request) throws IOException {
            String url = request.url();
            lastCalledUrl = url;
            for (Map.Entry<String, String> entry : postResponses.entrySet()) {
                if (url.contains(entry.getKey())) {
                    postedBodies.put(entry.getKey(), request.getBody());
                    return entry.getValue();
                }
            }
            throw new IOException("No mock POST response configured for URL: " + url);
        }

        // Legacy method kept for completeness — not needed for current tests
    }
}
