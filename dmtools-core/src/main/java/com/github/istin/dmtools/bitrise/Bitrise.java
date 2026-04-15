// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bitrise;

import com.github.istin.dmtools.bitrise.model.BitriseArtifact;
import com.github.istin.dmtools.bitrise.model.BitriseApp;
import com.github.istin.dmtools.bitrise.model.BitriseBuild;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Bitrise CI/CD API client.
 *
 * <p>Provides MCP-annotated methods for:
 * <ul>
 *   <li>Triggering and monitoring workflow builds (with parameters)</li>
 *   <li>Reading build logs and artifacts</li>
 *   <li>Managing {@code bitrise.yml} configuration</li>
 *   <li>Managing app secrets and environment variables</li>
 * </ul>
 *
 * <p>Configure via environment variables:
 * <pre>
 *   BITRISE_TOKEN     – Personal Access Token from bitrise.io
 *   BITRISE_APP_SLUG  – Default app slug (optional)
 * </pre>
 *
 * <p>Base URL: {@code https://api.bitrise.io/v0.1}
 */
public abstract class Bitrise extends AbstractRestClient {

    private static final Logger logger = LogManager.getLogger(Bitrise.class);

    public Bitrise(String basePath, String token) throws IOException {
        super(basePath, token);
    }

    @Override
    public String path(String path) {
        return getBasePath() + "/" + path;
    }

    @Override
    public synchronized Request.Builder sign(Request.Builder builder) {
        // Bitrise API expects "Authorization: token <PAT>"
        String authHeader = (authorization != null && !authorization.startsWith("token "))
                ? "token " + authorization
                : authorization;
        return builder
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    // -------------------------------------------------------------------------
    // Apps
    // -------------------------------------------------------------------------

    @MCPTool(
            name = "bitrise_list_apps",
            description = "List all Bitrise apps accessible with the current token. Returns app slugs, titles, project types and repo URLs.",
            integration = "bitrise",
            category = "apps"
    )
    public String listApps(
            @MCPParam(name = "sortBy", description = "Sort apps by: last_build_at or created_at", required = false, example = "last_build_at")
            String sortBy,
            @MCPParam(name = "title", description = "Filter apps by title (case-insensitive substring match)", required = false, example = "MyApp")
            String title,
            @MCPParam(name = "limit", description = "Max number of apps to return (1-50, default 50)", required = false, example = "20")
            Integer limit) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps"));
        if (sortBy != null && !sortBy.isEmpty()) req.param("sort_by", sortBy);
        if (title != null && !title.isEmpty()) req.param("title", title);
        if (limit != null) req.param("limit", String.valueOf(limit));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_get_app",
            description = "Get details of a specific Bitrise app by its slug.",
            integration = "bitrise",
            category = "apps"
    )
    public String getApp(
            @MCPParam(name = "appSlug", description = "The app slug identifier", required = true, example = "abc123def456")
            String appSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug));
        return execute(req);
    }

    // -------------------------------------------------------------------------
    // Builds / Workflow triggers
    // -------------------------------------------------------------------------

    @MCPTool(
            name = "bitrise_list_builds",
            description = "List builds for a Bitrise app. Optionally filter by workflow, branch or status. Status codes: not_started, in_progress, success, failed, aborted.",
            integration = "bitrise",
            category = "builds"
    )
    public String listBuilds(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "workflowId", description = "Filter by workflow ID / name", required = false, example = "primary")
            String workflowId,
            @MCPParam(name = "branch", description = "Filter by branch name", required = false, example = "main")
            String branch,
            @MCPParam(name = "status", description = "Filter by status: not_started | in_progress | success | failed | aborted", required = false, example = "failed")
            String status,
            @MCPParam(name = "limit", description = "Max results to return (default 20, max 100)", required = false, example = "50")
            Integer limit,
            @MCPParam(name = "next", description = "Pagination cursor from previous response paging.next", required = false, example = "next_cursor_value")
            String next) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/builds"));
        if (workflowId != null && !workflowId.isEmpty()) req.param("workflow", workflowId);
        if (branch != null && !branch.isEmpty()) req.param("branch", branch);
        if (status != null && !status.isEmpty()) req.param("status", mapBuildStatus(status));
        if (limit != null) req.param("limit", String.valueOf(limit));
        if (next != null && !next.isEmpty()) req.param("next", next);
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_trigger_build",
            description = "Trigger a new Bitrise workflow build for an app. Supports custom branch, environment variables, and workflow selection.",
            integration = "bitrise",
            category = "builds"
    )
    public String triggerBuild(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "workflowId", description = "Workflow ID to trigger (e.g. 'primary', 'deploy')", required = true, example = "primary")
            String workflowId,
            @MCPParam(name = "branch", description = "Branch to build (defaults to main/master)", required = false, example = "main")
            String branch,
            @MCPParam(name = "commitMessage", description = "Commit message for the build", required = false, example = "Triggered by DMtools")
            String commitMessage,
            @MCPParam(name = "envVars", description = "JSON array of env var objects: [{\"mapped_to\":\"KEY\",\"value\":\"val\",\"is_expand\":true}]", required = false, example = "[{\"mapped_to\":\"MY_VAR\",\"value\":\"hello\"}]")
            String envVars) throws IOException {
        JSONObject buildParams = new JSONObject();
        buildParams.put("workflow_id", workflowId);
        if (branch != null && !branch.isEmpty()) buildParams.put("branch", branch);
        if (commitMessage != null && !commitMessage.isEmpty()) buildParams.put("commit_message", commitMessage);
        if (envVars != null && !envVars.isEmpty()) {
            try {
                buildParams.put("environments", new JSONArray(envVars));
            } catch (Exception e) {
                logger.warn("Could not parse envVars JSON, ignoring: {}", e.getMessage());
            }
        }

        JSONObject hookInfo = new JSONObject();
        hookInfo.put("type", "bitrise");

        JSONObject body = new JSONObject();
        body.put("build_params", buildParams);
        body.put("hook_info", hookInfo);

        String url = path("apps/" + appSlug + "/builds");
        GenericRequest req = new GenericRequest(this, url);
        req.setBody(body.toString());
        return post(req);
    }

    @MCPTool(
            name = "bitrise_get_build",
            description = "Get details and current status of a specific Bitrise build. Returns status, branch, workflow, timing, commit info.",
            integration = "bitrise",
            category = "builds"
    )
    public String getBuild(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "buildSlug", description = "The build slug identifier", required = true, example = "build_slug_abc")
            String buildSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/builds/" + buildSlug));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_abort_build",
            description = "Abort a running Bitrise build with an optional reason message.",
            integration = "bitrise",
            category = "builds"
    )
    public String abortBuild(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "buildSlug", description = "The build slug to abort", required = true, example = "build_slug_abc")
            String buildSlug,
            @MCPParam(name = "reason", description = "Human-readable reason for aborting the build", required = false, example = "Superseded by newer build")
            String reason) throws IOException {
        JSONObject body = new JSONObject();
        if (reason != null && !reason.isEmpty()) body.put("abort_reason", reason);
        body.put("abort_with_success", false);
        body.put("skip_notifications", false);

        String url = path("apps/" + appSlug + "/builds/" + buildSlug + "/abort");
        GenericRequest req = new GenericRequest(this, url);
        req.setBody(body.toString());
        return post(req);
    }

    @MCPTool(
            name = "bitrise_get_build_log",
            description = "Get the full log of a Bitrise build. Returns log chunks and expiring download URL for the complete log.",
            integration = "bitrise",
            category = "builds"
    )
    public String getBuildLog(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "buildSlug", description = "The build slug", required = true, example = "build_slug_abc")
            String buildSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/builds/" + buildSlug + "/log"));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_list_workflows",
            description = "List all available workflow IDs defined in the bitrise.yml for a Bitrise app.",
            integration = "bitrise",
            category = "builds"
    )
    public String listWorkflows(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/build-workflows"));
        return execute(req);
    }

    // -------------------------------------------------------------------------
    // Artifacts
    // -------------------------------------------------------------------------

    @MCPTool(
            name = "bitrise_list_build_artifacts",
            description = "List all artifacts produced by a Bitrise build (APKs, IPAs, logs, test results, etc.).",
            integration = "bitrise",
            category = "artifacts"
    )
    public String listBuildArtifacts(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "buildSlug", description = "The build slug", required = true, example = "build_slug_abc")
            String buildSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/builds/" + buildSlug + "/artifacts"));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_get_build_artifact",
            description = "Get details and expiring download URL for a specific Bitrise build artifact.",
            integration = "bitrise",
            category = "artifacts"
    )
    public String getBuildArtifact(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "buildSlug", description = "The build slug", required = true, example = "build_slug_abc")
            String buildSlug,
            @MCPParam(name = "artifactSlug", description = "The artifact slug", required = true, example = "artifact_slug_xyz")
            String artifactSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/builds/" + buildSlug + "/artifacts/" + artifactSlug));
        return execute(req);
    }

    // -------------------------------------------------------------------------
    // bitrise.yml management
    // -------------------------------------------------------------------------

    @MCPTool(
            name = "bitrise_get_yml",
            description = "Download the bitrise.yml configuration file for a Bitrise app.",
            integration = "bitrise",
            category = "config"
    )
    public String getBitriseYml(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/bitrise.yml"));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_update_yml",
            description = "Upload/replace the bitrise.yml configuration file for a Bitrise app. Pass either the full YAML content or a local file path ending in .yml/.yaml. The YAML is validated via the Bitrise API before uploading.",
            integration = "bitrise",
            category = "config"
    )
    public String updateBitriseYml(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "ymlContent", description = "Full YAML content OR a local file path to a .yml/.yaml file", required = true, example = "/path/to/bitrise.yml")
            String ymlContent) throws IOException {
        String content = resolveYmlContent(ymlContent);

        // Validate before uploading
        String validationResult = validateBitriseYml(content, appSlug);
        JSONObject validationJson = new JSONObject(validationResult);
        if (validationJson.has("error_message") && !validationJson.isNull("error_message")
                && !validationJson.optString("error_message").isEmpty()) {
            throw new IOException("bitrise.yml validation failed: " + validationJson.optString("error_message"));
        }
        if (validationJson.has("warnings")) {
            logger.warn("bitrise.yml validation warnings: {}", validationJson.opt("warnings"));
        }

        JSONObject body = new JSONObject();
        body.put("app_config_datastore_yaml", content);
        String url = path("apps/" + appSlug + "/bitrise.yml");
        GenericRequest req = new GenericRequest(this, url);
        req.setBody(body.toString());
        return post(req);
    }

    @MCPTool(
            name = "bitrise_validate_yml",
            description = "Validate a bitrise.yml file content via the Bitrise API. Returns validation errors and warnings without modifying the app configuration. Accepts YAML content or a local file path.",
            integration = "bitrise",
            category = "config"
    )
    public String validateBitriseYml(
            @MCPParam(name = "ymlContent", description = "Full YAML content OR a local file path to a .yml/.yaml file to validate", required = true, example = "/path/to/bitrise.yml")
            String ymlContent,
            @MCPParam(name = "appSlug", description = "Optional app slug for app-specific validation (stack, machines, licenses)", required = false, example = "abc123def456")
            String appSlug) throws IOException {
        String content = resolveYmlContent(ymlContent);
        JSONObject body = new JSONObject();
        body.put("bitrise_yml", content);
        GenericRequest req = new GenericRequest(this, path("validate-bitrise-yml"));
        if (appSlug != null && !appSlug.isEmpty()) req.param("app_slug", appSlug);
        req.setBody(body.toString());
        return post(req);
    }

    @MCPTool(
            name = "bitrise_get_yml_config",
            description = "Get the JSON-structured config representation of the bitrise.yml for a Bitrise app.",
            integration = "bitrise",
            category = "config"
    )
    public String getBitriseYmlConfig(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/bitrise.yml/config"));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_update_yml_config",
            description = "Update the bitrise.yml using a JSON-structured config representation for a Bitrise app.",
            integration = "bitrise",
            category = "config"
    )
    public String updateBitriseYmlConfig(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "configJson", description = "JSON string of the bitrise.yml config object", required = true, example = "{\"format_version\":\"11\",\"workflows\":{}}")
            String configJson) throws IOException {
        String url = path("apps/" + appSlug + "/bitrise.yml/config");
        GenericRequest req = new GenericRequest(this, url);
        req.setBody(configJson);
        return put(req);
    }

    // -------------------------------------------------------------------------
    // Secrets & Environment Variables
    // -------------------------------------------------------------------------

    @MCPTool(
            name = "bitrise_list_secrets",
            description = "List all secret environment variables for a Bitrise app. Values are not returned by default (protected).",
            integration = "bitrise",
            category = "secrets"
    )
    public String listSecrets(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/secrets"));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_get_secret",
            description = "Get metadata for a specific secret by name. The value is not returned unless it's non-protected.",
            integration = "bitrise",
            category = "secrets"
    )
    public String getSecret(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "secretName", description = "The secret environment variable name", required = true, example = "MY_API_KEY")
            String secretName) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/secrets/" + secretName));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_upsert_secret",
            description = "Create or update a secret environment variable for a Bitrise app.",
            integration = "bitrise",
            category = "secrets"
    )
    public String upsertSecret(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "secretName", description = "The secret name (environment variable key)", required = true, example = "MY_API_KEY")
            String secretName,
            @MCPParam(name = "value", description = "The secret value", required = true, example = "s3cr3t_v4lu3")
            String value,
            @MCPParam(name = "isProtected", description = "If true the value cannot be read back via API (default: true)", required = false, example = "true")
            Boolean isProtected,
            @MCPParam(name = "isExposedForPullRequests", description = "Whether the secret is available in PR builds", required = false, example = "false")
            Boolean isExposedForPullRequests,
            @MCPParam(name = "expandInStepInputs", description = "Whether the value is expanded (interpolated) in step inputs", required = false, example = "true")
            Boolean expandInStepInputs) throws IOException {
        JSONObject body = new JSONObject();
        body.put("name", secretName);
        body.put("value", value);
        if (isProtected != null) body.put("is_protected", isProtected);
        if (isExposedForPullRequests != null) body.put("is_exposed_for_pull_requests", isExposedForPullRequests);
        if (expandInStepInputs != null) body.put("expand_in_step_inputs", expandInStepInputs);

        // Try PUT (update) first; if secret not found, fall back to POST (create)
        String putUrl = path("apps/" + appSlug + "/secrets/" + secretName);
        GenericRequest putReq = new GenericRequest(this, putUrl);
        putReq.setBody(body.toString());
        try {
            return put(putReq);
        } catch (RestClient.RestClientException e) {
            if (e.getCode() == 404) {
                // Secret doesn't exist yet — create it via POST
                String postUrl = path("apps/" + appSlug + "/secrets");
                GenericRequest postReq = new GenericRequest(this, postUrl);
                postReq.setBody(body.toString());
                return post(postReq);
            }
            throw e;
        }
    }

    @MCPTool(
            name = "bitrise_delete_secret",
            description = "Delete a secret environment variable from a Bitrise app.",
            integration = "bitrise",
            category = "secrets"
    )
    public String deleteSecret(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "secretName", description = "The secret name to delete", required = true, example = "MY_API_KEY")
            String secretName) throws IOException {
        String url = path("apps/" + appSlug + "/secrets/" + secretName);
        GenericRequest req = new GenericRequest(this, url);
        return delete(req);
    }

    @MCPTool(
            name = "bitrise_get_secret_value",
            description = "Retrieve the plaintext value of a non-protected secret. Returns 403 if the secret is marked as protected.",
            integration = "bitrise",
            category = "secrets"
    )
    public String getSecretValue(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "secretName", description = "The secret name", required = true, example = "MY_API_KEY")
            String secretName) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/secrets/" + secretName + "/value"));
        return execute(req);
    }

    // -------------------------------------------------------------------------
    // Pipelines
    // -------------------------------------------------------------------------

    @MCPTool(
            name = "bitrise_list_pipelines",
            description = "List pipeline runs for a Bitrise app.",
            integration = "bitrise",
            category = "pipelines"
    )
    public String listPipelines(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/pipelines"));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_get_pipeline",
            description = "Get details of a specific Bitrise pipeline run by its ID.",
            integration = "bitrise",
            category = "pipelines"
    )
    public String getPipeline(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "pipelineId", description = "The pipeline run ID (UUID)", required = true, example = "pipeline-uuid-here")
            String pipelineId) throws IOException {
        GenericRequest req = new GenericRequest(this, path("apps/" + appSlug + "/pipelines/" + pipelineId));
        return execute(req);
    }

    @MCPTool(
            name = "bitrise_abort_pipeline",
            description = "Abort a running Bitrise pipeline.",
            integration = "bitrise",
            category = "pipelines"
    )
    public String abortPipeline(
            @MCPParam(name = "appSlug", description = "The Bitrise app slug", required = true, example = "abc123def456")
            String appSlug,
            @MCPParam(name = "pipelineId", description = "The pipeline run ID (UUID) to abort", required = true, example = "pipeline-uuid-here")
            String pipelineId) throws IOException {
        String url = path("apps/" + appSlug + "/pipelines/" + pipelineId + "/abort");
        GenericRequest req = new GenericRequest(this, url);
        req.setBody("{}");
        return post(req);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Maps human-readable status strings to Bitrise API integer status values.
     * Bitrise API accepts numeric status:
     * 0 = not started, 1 = in_progress, 2 = success, 3 = failed, 4 = aborted
     */
    private String mapBuildStatus(String status) {
        if (status == null) return null;
        switch (status.toLowerCase()) {
            case "not_started": return "0";
            case "in_progress": return "1";
            case "success":     return "2";
            case "failed":      return "3";
            case "aborted":     return "4";
            default:            return status; // pass through numeric values as-is
        }
    }

    /**
     * Resolves {@code ymlContent} to actual YAML text.
     * If the value looks like a file path (starts with {@code /}, {@code ./}, {@code ../},
     * or ends with {@code .yml}/{@code .yaml}), the file is read from disk.
     * Otherwise the value is returned as-is (inline YAML).
     */
    private String resolveYmlContent(String ymlContent) throws IOException {
        if (ymlContent == null) throw new IOException("ymlContent must not be null");
        String trimmed = ymlContent.trim();
        boolean looksLikeFilePath = trimmed.startsWith("/")
                || trimmed.startsWith("./")
                || trimmed.startsWith("../")
                || trimmed.endsWith(".yml")
                || trimmed.endsWith(".yaml");
        if (looksLikeFilePath) {
            java.nio.file.Path path = Paths.get(trimmed);
            if (!Files.exists(path)) {
                throw new IOException("bitrise.yml file not found: " + trimmed);
            }
            logger.info("Reading bitrise.yml from file: {}", trimmed);
            return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        }
        return ymlContent;
    }
}
