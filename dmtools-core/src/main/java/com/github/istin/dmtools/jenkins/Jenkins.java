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
import okhttp3.RequestBody;
import okhttp3.Response;
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
 *   <li>Resolving a triggered job's queue item to its build number</li>
 *   <li>Triggering a job and blocking until it finishes (queue + build polling)</li>
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
        return fetchBuildInfoJson(jobPath, buildNumber).toMap();
    }

    private JSONObject fetchBuildInfoJson(String jobPath, int buildNumber) throws IOException {
        GenericRequest req = new GenericRequest(this,
                path(toApiJobPath(jobPath) + buildNumber + "/api/json"));
        String response = execute(req);
        return new JSONObject(response);
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
            description = "Trigger a Jenkins job. Provide parameters as a JSON object to use buildWithParameters. Returns 'queueUrl' — the Jenkins queue item URL (from the response's Location header) — which can be resolved to a build number via jenkins_get_queue_item. Prefer jenkins_trigger_job_and_wait when the caller needs to block until the build finishes.",
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

            // Jenkins' build/buildWithParameters endpoints return an empty body and put the
            // actual queue item location in the "Location" response header — post(GenericRequest)
            // only returns the body, so we make the raw call ourselves (via the overridable
            // executeRawRequest seam below, so tests can stub the HTTP layer) to read that header.
            Request.Builder builder = new Request.Builder()
                    .url(req.url())
                    .header("User-Agent", "DMTools")
                    .post(RequestBody.create("", JSON));
            sign(builder);

            try (Response response = executeRawRequest(builder.build())) {
                if (response.isSuccessful() || response.code() == 302) {
                    result.put("success", true);
                    result.put("queueUrl", response.header("Location"));
                } else {
                    result.put("success", false);
                    result.put("message", "Failed to trigger Jenkins job: HTTP " + response.code());
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Failed to trigger Jenkins job: " + e.getMessage());
            logger.warn("Failed to trigger Jenkins job", e);
        }
        return result;
    }

    /**
     * Executes a raw OkHttp request and returns the response (headers included), bypassing
     * {@link #post(GenericRequest)} which only exposes the response body. Overridable/spy-able
     * seam so unit tests can stub the HTTP layer without a real Jenkins instance.
     */
    protected Response executeRawRequest(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    @MCPTool(
            name = "jenkins_get_queue_item",
            description = "Check the status of a Jenkins queue item — the 'queueUrl' returned by jenkins_trigger_job. While the job is queued/blocked (e.g. waiting for an executor or an upstream lock) there is no build number yet; once Jenkins starts executing it, the response includes 'buildNumber'/'buildUrl'. Returns 'cancelled': true if the queued item was cancelled instead of executed.",
            integration = "jenkins",
            category = "builds"
    )
    public Map<String, Object> getQueueItem(
            @MCPParam(name = "queueUrl", description = "Queue item URL returned by jenkins_trigger_job's 'queueUrl' field, e.g. https://jenkins.example.com/queue/item/12345/", required = true)
            String queueUrl) throws IOException {
        Map<String, Object> result = new HashMap<>();
        if (queueUrl == null || queueUrl.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "queueUrl is required");
            return result;
        }
        String normalized = queueUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        GenericRequest req = new GenericRequest(this, normalized + "api/json");
        String response = execute(req);
        JSONObject json = new JSONObject(response);

        result.put("success", true);
        result.put("cancelled", json.optBoolean("cancelled", false));
        result.put("blocked", json.optBoolean("blocked", false));
        if (json.has("why")) {
            result.put("why", json.optString("why", null));
        }
        JSONObject executable = json.optJSONObject("executable");
        if (executable != null) {
            result.put("buildNumber", executable.optInt("number"));
            result.put("buildUrl", executable.optString("url", null));
        }
        return result;
    }

    @MCPTool(
            name = "jenkins_trigger_job_and_wait",
            description = "Trigger a Jenkins job and block until the resulting build finishes (or a timeout elapses). Internally resolves the queue item to a build number (falling back to build-number polling if no queue URL is available), then polls the build until it is no longer 'building'. Use this instead of jenkins_trigger_job + manual polling when the caller needs a synchronous result — e.g. before continuing a git workflow that depends on the triggered job's outcome (such as a branch-creation job that must finish before the branch can be checked out).",
            integration = "jenkins",
            category = "builds"
    )
    public Map<String, Object> triggerJobAndWait(
            @MCPParam(name = "jobPath", description = "Jenkins job path, e.g. folder/job-name", required = true)
            String jobPath,
            @MCPParam(name = "parametersJson", description = "JSON object of parameter names to values, e.g. {\"BRANCH\":\"main\"}", required = false)
            String parametersJson,
            @MCPParam(name = "pollIntervalSeconds", description = "Seconds to wait between status checks. Default: 10", required = false)
            Integer pollIntervalSeconds,
            @MCPParam(name = "timeoutSeconds", description = "Maximum seconds to wait before giving up. Default: 600 (10 minutes)", required = false)
            Integer timeoutSeconds) throws IOException {
        Map<String, Object> result = new HashMap<>();
        int interval = (pollIntervalSeconds != null && pollIntervalSeconds >= 0) ? pollIntervalSeconds : 10;
        int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 600;
        long deadline = System.currentTimeMillis() + (timeout * 1000L);

        Map<String, Object> triggerResult = triggerJob(jobPath, parametersJson);
        if (!Boolean.TRUE.equals(triggerResult.get("success"))) {
            result.put("success", false);
            result.put("message", triggerResult.containsKey("message") ? triggerResult.get("message") : "Failed to trigger Jenkins job");
            return result;
        }

        String queueUrl = (String) triggerResult.get("queueUrl");
        Integer buildNumber = null;

        if (queueUrl != null && !queueUrl.trim().isEmpty()) {
            while (System.currentTimeMillis() < deadline) {
                Map<String, Object> queueItem = getQueueItem(queueUrl);
                if (Boolean.TRUE.equals(queueItem.get("cancelled"))) {
                    result.put("success", false);
                    result.put("message", "Jenkins queue item was cancelled before it started building");
                    result.put("queueUrl", queueUrl);
                    return result;
                }
                Object bn = queueItem.get("buildNumber");
                if (bn instanceof Integer && (Integer) bn > 0) {
                    buildNumber = (Integer) bn;
                    break;
                }
                sleepSeconds(interval);
            }
            if (buildNumber == null) {
                result.put("success", false);
                result.put("message", "Timed out waiting for Jenkins to start the queued build");
                result.put("queueUrl", queueUrl);
                return result;
            }
        } else {
            // No queueUrl (e.g. an older Jenkins/proxy that doesn't surface the Location
            // header) — fall back to diffing the latest build number before/after triggering.
            // Best-effort: can race with a concurrent trigger of the same job by someone else.
            logger.warn("jenkins_trigger_job_and_wait: no queueUrl returned for {} — falling back to build-number polling (racy under concurrent use)", jobPath);
            List<JenkinsBuild> before = listBuilds(jobPath, 1);
            int beforeNumber = before.isEmpty() ? 0 : before.get(0).getNumber();
            while (System.currentTimeMillis() < deadline && buildNumber == null) {
                sleepSeconds(interval);
                List<JenkinsBuild> builds = listBuilds(jobPath, 1);
                if (!builds.isEmpty() && builds.get(0).getNumber() > beforeNumber) {
                    buildNumber = builds.get(0).getNumber();
                }
            }
            if (buildNumber == null) {
                result.put("success", false);
                result.put("message", "Timed out waiting for a new build to appear");
                return result;
            }
        }

        while (System.currentTimeMillis() < deadline) {
            JSONObject buildInfo = fetchBuildInfoJson(jobPath, buildNumber);
            if (!buildInfo.optBoolean("building", false)) {
                String buildResult = buildInfo.optString("result", null);
                result.put("success", "SUCCESS".equals(buildResult));
                result.put("buildNumber", buildNumber);
                result.put("result", buildResult);
                result.put("buildUrl", buildInfo.optString("url", null));
                result.put("timedOut", false);
                return result;
            }
            sleepSeconds(interval);
        }

        result.put("success", false);
        result.put("message", "Timed out waiting for build #" + buildNumber + " to finish");
        result.put("buildNumber", buildNumber);
        result.put("timedOut", true);
        return result;
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
