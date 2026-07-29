// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.jenkins;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.jenkins.model.JenkinsBuild;
import com.github.istin.dmtools.jenkins.model.JenkinsJob;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jenkins CI API client.
 *
 * <p>Provides MCP-annotated methods for:
 * <ul>
 *   <li>Testing Jenkins connectivity</li>
 *   <li>Listing and inspecting builds</li>
 *   <li>Reading build console logs</li>
 *   <li>Triggering parameterized and non-parameterized jobs</li>
 * </ul>
 *
 * <p>Configure via environment variables:
 * <pre>
 *   JENKINS_BASE_PATH – Jenkins URL (default: http://localhost:8080)
 *   JENKINS_USER      – Jenkins user name
 *   JENKINS_API_TOKEN – Jenkins API token
 * </pre>
 */
public abstract class Jenkins extends AbstractRestClient {

    private static final Logger logger = LogManager.getLogger(Jenkins.class);

    private final String user;
    private final String apiToken;

    public Jenkins(String basePath, String user, String apiToken) throws IOException {
        super(basePath, null);
        this.user = user;
        this.apiToken = apiToken;
    }

    @Override
    public String path(String path) {
        String base = getBasePath();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

    @Override
    public synchronized Request.Builder sign(Request.Builder builder) {
        String credentials = user + ":" + apiToken;
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return builder
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    /**
     * Converts a human-readable job path into Jenkins REST API path segments.
     * <p>{@code folder/job-name} becomes {@code /job/folder/job/job-name/}.
     * Paths that already start with {@code /job/} are returned unchanged.
     */
    public String toApiJobPath(String jobPath) {
        if (jobPath == null || jobPath.trim().isEmpty()) {
            return "/";
        }
        String normalized = jobPath.trim();
        // Accept both "job/folder/job/name" (from URL parsing) and "/job/folder/job/name"
        if (normalized.startsWith("job/")) {
            normalized = "/" + normalized;
        }
        if (normalized.startsWith("/job/")) {
            if (normalized.endsWith("/")) {
                return normalized;
            }
            return normalized + "/";
        }
        String[] segments = normalized.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            builder.append("/job/").append(segment);
        }
        builder.append("/");
        return builder.toString();
    }

    @MCPTool(
            name = "jenkins_test",
            description = "Test Jenkins connectivity by fetching the root system information",
            integration = "jenkins",
            category = "system"
    )
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();
        try {
            GenericRequest req = new GenericRequest(this, path("api/json"));
            String response = execute(req);
            if (response != null && !response.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(response);
                result.put("success", true);
                result.put("message", "Jenkins API connection successful");
                result.put("mode", jsonResponse.optString("mode", "unknown"));
                result.put("nodeName", jsonResponse.optString("nodeName", "unknown"));
            } else {
                result.put("success", false);
                result.put("message", "Empty response from Jenkins API");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Jenkins API connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            logger.warn("Jenkins connection test failed", e);
        }
        return result;
    }

    @MCPTool(
            name = "jenkins_list_builds",
            description = "List recent builds for a Jenkins job. Provide the job path as folder/job-name.",
            integration = "jenkins",
            category = "builds"
    )
    public List<JenkinsBuild> listBuilds(
            @MCPParam(name = "jobPath", description = "Jenkins job path, e.g. folder/job-name", required = true)
            String jobPath,
            @MCPParam(name = "limit", description = "Max number of builds to return", required = false)
            Integer limit) throws IOException {
        GenericRequest req = new GenericRequest(this, path(toApiJobPath(jobPath) + "api/json"));
        String response = execute(req);
        JenkinsJob job = new JenkinsJob(new JSONObject(response));
        List<JenkinsBuild> builds = job.getBuilds();
        if (limit != null && builds.size() > limit) {
            return new ArrayList<>(builds.subList(0, limit));
        }
        return builds;
    }

    @MCPTool(
            name = "jenkins_get_job_info",
            description = "Get details of a specific Jenkins build by job path and build number.",
            integration = "jenkins",
            category = "builds"
    )
    public Map<String, Object> getBuildInfo(
            @MCPParam(name = "jobPath", description = "Jenkins job path, e.g. folder/job-name", required = true)
            String jobPath,
            @MCPParam(name = "buildNumber", description = "Build number", required = true)
            Integer buildNumber) throws IOException {
        GenericRequest req = new GenericRequest(this,
                path(toApiJobPath(jobPath) + buildNumber + "/api/json"));
        String response = execute(req);
        return new JSONObject(response).toMap();
    }

    @MCPTool(
            name = "jenkins_get_build_log",
            description = "Get the raw console log text of a specific Jenkins build.",
            integration = "jenkins",
            category = "builds"
    )
    public String getBuildLog(
            @MCPParam(name = "jobPath", description = "Jenkins job path, e.g. folder/job-name", required = true)
            String jobPath,
            @MCPParam(name = "buildNumber", description = "Build number", required = true)
            Integer buildNumber) throws IOException {
        GenericRequest req = new GenericRequest(this,
                path(toApiJobPath(jobPath) + buildNumber + "/consoleText"));
        return execute(req);
    }

    @MCPTool(
            name = "jenkins_trigger_job",
            description = "Trigger a Jenkins job. Provide parameters as a JSON object to use buildWithParameters.",
            integration = "jenkins",
            category = "builds"
    )
    public Map<String, Object> triggerJob(
            @MCPParam(name = "jobPath", description = "Jenkins job path, e.g. folder/job-name", required = true)
            String jobPath,
            @MCPParam(name = "parametersJson", description = "JSON object of parameter names to values, e.g. {\"BRANCH\":\"main\"}", required = false)
            String parametersJson) throws IOException {
        Map<String, Object> result = new HashMap<>();
        try {
            GenericRequest req;
            if (parametersJson != null && !parametersJson.trim().isEmpty()) {
                req = new GenericRequest(this, path(toApiJobPath(jobPath) + "buildWithParameters"));
                JSONObject params = new JSONObject(parametersJson);
                params.keys().forEachRemaining(key -> {
                    Object value = params.get(key);
                    if (value != null) {
                        req.param(key, String.valueOf(value));
                    }
                });
            } else {
                req = new GenericRequest(this, path(toApiJobPath(jobPath) + "build"));
            }
            req.setBody("");
            String response = post(req);
            result.put("success", true);
            result.put("queueUrl", response != null && !response.isEmpty() ? response : null);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to trigger Jenkins job: " + e.getMessage());
            logger.warn("Failed to trigger Jenkins job", e);
        }
        return result;
    }
}
