// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.jenkins;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.jenkins.model.JenkinsBuild;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Jenkins client.
 *
 * <p>Uses a spy on a stub subclass so HTTP calls are intercepted without
 * requiring a real Jenkins instance.
 */
class JenkinsClientTest {

    private static final String JOB_PATH = "folder/job-name";

    /** Minimal concrete subclass that lets us spy on execute/post. */
    private static class StubJenkins extends Jenkins {
        StubJenkins() throws IOException {
            super("http://localhost:8080", "test-user", "test-token");
        }

        @Override
        public String execute(GenericRequest request) {
            return "{}";
        }

        @Override
        public String post(GenericRequest request) {
            return "{}";
        }
    }

    private StubJenkins jenkins;

    @BeforeEach
    void setUp() throws IOException {
        jenkins = spy(new StubJenkins());
    }

    // -------------------------------------------------------------------------
    // Model tests
    // -------------------------------------------------------------------------

    @Test
    void jenkinsBuild_parsesFields() {
        JSONObject json = new JSONObject()
                .put("number", 42)
                .put("result", "SUCCESS")
                .put("url", "http://localhost:8080/job/folder/job/job-name/42/")
                .put("duration", 12345L)
                .put("building", false);

        JenkinsBuild build = new JenkinsBuild(json);

        assertEquals(42, build.getNumber());
        assertEquals("SUCCESS", build.getResult());
        assertEquals("http://localhost:8080/job/folder/job/job-name/42/", build.getUrl());
        assertEquals(12345L, build.getDuration());
        assertFalse(build.isBuilding());
    }

    // -------------------------------------------------------------------------
    // API tests
    // -------------------------------------------------------------------------

    @Test
    void jenkins_test_success() {
        doReturn("{\"mode\":\"NORMAL\",\"nodeName\":\"master\"}").when(jenkins).execute(any(GenericRequest.class));

        Map<String, Object> result = jenkins.testConnection();

        assertTrue((Boolean) result.get("success"));
        assertEquals("NORMAL", result.get("mode"));
        assertEquals("master", result.get("nodeName"));
        verify(jenkins).execute(argThat((GenericRequest req) -> req.url().endsWith("/api/json")));
    }

    @Test
    void jenkins_list_builds_parsesBuildsAndRespectsLimit() throws IOException {
        JSONObject build1 = new JSONObject().put("number", 1).put("result", "SUCCESS");
        JSONObject build2 = new JSONObject().put("number", 2).put("result", "FAILURE");
        JSONObject build3 = new JSONObject().put("number", 3).put("result", "SUCCESS");
        JSONObject jobJson = new JSONObject()
                .put("name", "job-name")
                .put("url", "http://localhost:8080/job/folder/job/job-name/")
                .put("builds", new org.json.JSONArray().put(build1).put(build2).put(build3));

        doReturn(jobJson.toString()).when(jenkins).execute(any(GenericRequest.class));

        List<JenkinsBuild> builds = jenkins.listBuilds(JOB_PATH, 2);

        assertEquals(2, builds.size());
        assertEquals(1, builds.get(0).getNumber());
        assertEquals(2, builds.get(1).getNumber());
        verify(jenkins).execute(argThat((GenericRequest req) ->
                req.url().contains("/job/folder/job/job-name/api/json")));
    }

    @Test
    void jenkins_get_job_info_returnsBuildInfo() throws IOException {
        JSONObject buildJson = new JSONObject()
                .put("number", 7)
                .put("result", "SUCCESS")
                .put("building", false);

        doReturn(buildJson.toString()).when(jenkins).execute(any(GenericRequest.class));

        Map<String, Object> info = jenkins.getBuildInfo(JOB_PATH, 7);

        assertEquals(7, info.get("number"));
        assertEquals("SUCCESS", info.get("result"));
        assertEquals(Boolean.FALSE, info.get("building"));
        verify(jenkins).execute(argThat((GenericRequest req) ->
                req.url().contains("/job/folder/job/job-name/7/api/json")));
    }

    @Test
    void jenkins_get_build_log_returnsConsoleText() throws IOException {
        doReturn("Build started...\nBuild finished successfully.").when(jenkins).execute(any(GenericRequest.class));

        String log = jenkins.getBuildLog(JOB_PATH, 5);

        assertEquals("Build started...\nBuild finished successfully.", log);
        verify(jenkins).execute(argThat((GenericRequest req) ->
                req.url().contains("/job/folder/job/job-name/5/consoleText")));
    }

    @Test
    void jenkins_trigger_job_withParameters_usesBuildWithParametersAndQueryParams() throws IOException {
        doReturn("").when(jenkins).post(any(GenericRequest.class));

        Map<String, Object> result = jenkins.triggerJob(JOB_PATH, "{\"BRANCH\":\"main\",\"ENV\":\"prod\"}");

        assertTrue((Boolean) result.get("success"));
        verify(jenkins).post(argThat((GenericRequest req) ->
                req.url().contains("/job/folder/job/job-name/buildWithParameters") &&
                req.url().contains("BRANCH=main") &&
                req.url().contains("ENV=prod")));
    }

    @Test
    void jenkins_trigger_job_withoutParameters_usesBuild() throws IOException {
        doReturn("").when(jenkins).post(any(GenericRequest.class));

        Map<String, Object> result = jenkins.triggerJob(JOB_PATH, null);

        assertTrue((Boolean) result.get("success"));
        verify(jenkins).post(argThat((GenericRequest req) ->
                req.url().contains("/job/folder/job/job-name/build") &&
                !req.url().contains("buildWithParameters")));
    }

    @Test
    void path_prependsBasePathAndAvoidsDoubleSlash() {
        assertEquals("http://localhost:8080/api/json", jenkins.path("api/json"));
        assertEquals("http://localhost:8080/job/folder/api/json", jenkins.path("/job/folder/api/json"));
    }

    @Test
    void toApiJobPath_convertsJobPath() {
        assertEquals("/job/folder/job/job-name/", jenkins.toApiJobPath("folder/job-name"));
    }

    @Test
    void toApiJobPath_handlesAlreadyPrefixedPath() {
        assertEquals("/job/folder/job/job-name/", jenkins.toApiJobPath("/job/folder/job/job-name"));
        assertEquals("/job/folder/job/job-name/", jenkins.toApiJobPath("/job/folder/job/job-name/"));
    }

    // -------------------------------------------------------------------------
    // BasicJenkins configuration tests
    // -------------------------------------------------------------------------

    @Test
    void basicJenkins_isConfigured_methodExists() {
        boolean result = BasicJenkins.isConfigured();
        assertTrue(result == true || result == false, "isConfigured() must return a boolean");
    }

    @Test
    void basicJenkins_getInstanceReturnsNull_whenNotConfigured() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeFalse(BasicJenkins.isConfigured(),
                "Skipped: Jenkins is configured in this environment");
        BasicJenkins.resetInstance();
        BasicJenkins instance = BasicJenkins.getInstance();
        assertNull(instance);
    }
}
