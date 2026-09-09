// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;

import java.io.IOException;

/**
 * GitHub Actions MCP tools: workflow catalog, workflow runs, and check runs.
 *
 * <p>Tool names and parameter names mirror the dmtools-dart catalog 1:1 so the
 * same agent code runs on both runtimes. Split out of {@link GitHub} to keep
 * that file under the file-size quality gate.</p>
 */
public abstract class GitHubActions extends GitHubRepository {

    public GitHubActions(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    @MCPTool(
            name = "github_get_workflows",
            description = "List GitHub Actions workflows in a repository",
            integration = "github",
            category = "actions"
    )
    public String getWorkflows(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo) throws IOException {
        String path = path(String.format("repos/%s/%s/actions/workflows", owner, repo));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_enable_workflow",
            description = "Enable a GitHub Actions workflow by id",
            integration = "github",
            category = "actions"
    )
    public String enableWorkflow(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "workflow_id", description = "The workflow id to enable", required = true, example = "123456")
            Integer workflowId) throws IOException {
        String path = path(String.format("repos/%s/%s/actions/workflows/%d/enable", owner, repo, workflowId));
        GenericRequest putRequest = new GenericRequest(this, path);
        return put(putRequest);
    }

    @MCPTool(
            name = "github_disable_workflow",
            description = "Disable a GitHub Actions workflow by id",
            integration = "github",
            category = "actions"
    )
    public String disableWorkflow(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "workflow_id", description = "The workflow id to disable", required = true, example = "123456")
            Integer workflowId) throws IOException {
        String path = path(String.format("repos/%s/%s/actions/workflows/%d/disable", owner, repo, workflowId));
        GenericRequest putRequest = new GenericRequest(this, path);
        return put(putRequest);
    }

    @MCPTool(
            name = "github_get_workflow_runs",
            description = "List GitHub Actions workflow runs for a repository",
            integration = "github",
            category = "actions"
    )
    public String getWorkflowRuns(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo) throws IOException {
        String path = path(String.format("repos/%s/%s/actions/runs", owner, repo));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_rerun_workflow",
            description = "Re-run a GitHub Actions workflow run by id",
            integration = "github",
            category = "actions"
    )
    public String rerunWorkflow(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "run_id", description = "The workflow run id to re-run", required = true, example = "1234567890")
            Integer runId) throws IOException {
        String path = path(String.format("repos/%s/%s/actions/runs/%d/rerun", owner, repo, runId));
        GenericRequest postRequest = new GenericRequest(this, path);
        return post(postRequest);
    }

    @MCPTool(
            name = "github_get_check_runs",
            description = "List check runs for a GitHub commit ref",
            integration = "github",
            category = "actions"
    )
    public String getCheckRuns(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "ref", description = "The branch, tag, or commit SHA to list check runs for", required = true, example = "main")
            String ref) throws IOException {
        String path = path(String.format("repos/%s/%s/commits/%s/check-runs", owner, repo, ref));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }
}
