// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Pull-request lifecycle MCP tools for GitHub (create, close, reopen, update,
 * changed files, request reviewers, native reviews).
 *
 * <p>Tool names and parameter names mirror the dmtools-dart catalog 1:1 so the
 * same agent code runs on both runtimes. Split out of {@link GitHub} to keep
 * that file under the file-size quality gate.</p>
 */
public abstract class GitHubPullRequests extends GitHubIssues {

    public GitHubPullRequests(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    @MCPTool(
            name = "github_create_pr",
            description = "Create a GitHub pull request",
            integration = "github",
            category = "pull_requests"
    )
    public String createPr(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "title", description = "The title of the new pull request", required = true, example = "Add feature X")
            String title,
            @MCPParam(name = "head", description = "The branch containing changes (source)", required = true, example = "feature/x")
            String head,
            @MCPParam(name = "base", description = "The branch to merge changes into (target)", required = true, example = "main")
            String base) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls", owner, repo));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", title);
        jsonObject.put("head", head);
        jsonObject.put("base", base);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_close_pr",
            description = "Close a GitHub pull request",
            integration = "github",
            category = "pull_requests"
    )
    public String closePr(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number) throws IOException {
        return setPrState(owner, repo, number, "closed");
    }

    @MCPTool(
            name = "github_reopen_pr",
            description = "Reopen a GitHub pull request",
            integration = "github",
            category = "pull_requests"
    )
    public String reopenPr(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number) throws IOException {
        return setPrState(owner, repo, number, "open");
    }

    private String setPrState(String owner, String repo, Integer number, String state) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%d", owner, repo, number));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state", state);
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @MCPTool(
            name = "github_update_pr",
            description = "Update the title or body of a GitHub pull request. Only the provided fields are changed.",
            integration = "github",
            category = "pull_requests"
    )
    public String updatePr(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number,
            @MCPParam(name = "title", description = "The new title (omitted to leave unchanged)", required = false, example = "Updated title")
            String title,
            @MCPParam(name = "body", description = "The new body (omitted to leave unchanged)", required = false, example = "Updated description")
            String body) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%d", owner, repo, number));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        if (title != null) {
            jsonObject.put("title", title);
        }
        if (body != null) {
            jsonObject.put("body", body);
        }
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @MCPTool(
            name = "github_get_pr_files",
            description = "List the files changed in a GitHub pull request",
            integration = "github",
            category = "pull_requests"
    )
    public String getPrFiles(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%d/files", owner, repo, number));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_request_reviewers",
            description = "Request reviewers on a GitHub pull request",
            integration = "github",
            category = "reviews"
    )
    public String requestReviewers(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number,
            @MCPParam(name = "reviewers", description = "The reviewer logins to request", required = true, example = "[\"octocat\"]")
            String[] reviewers) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%d/requested_reviewers", owner, repo, number));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        JSONArray reviewersArray = new JSONArray();
        if (reviewers != null) {
            for (String reviewer : reviewers) {
                reviewersArray.put(reviewer);
            }
        }
        jsonObject.put("reviewers", reviewersArray);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_create_review",
            description = "Create a review on a GitHub pull request",
            integration = "github",
            category = "reviews"
    )
    public String createReview(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number,
            @MCPParam(name = "body", description = "The review body text", required = true, example = "Looks good!")
            String body,
            @MCPParam(name = "event", description = "Review event: APPROVE, REQUEST_CHANGES, or COMMENT", required = true, example = "APPROVE")
            String event) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%d/reviews", owner, repo, number));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", body);
        jsonObject.put("event", event);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_dismiss_review",
            description = "Dismiss a review on a GitHub pull request",
            integration = "github",
            category = "reviews"
    )
    public String dismissReview(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The pull request number", required = true, example = "74")
            Integer number,
            @MCPParam(name = "review_id", description = "The id of the review to dismiss", required = true, example = "123456")
            Integer reviewId,
            @MCPParam(name = "message", description = "The dismissal message", required = true, example = "Outdated")
            String message) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%d/reviews/%d/dismissals", owner, repo, number, reviewId));
        GenericRequest putRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message", message);
        putRequest.setBody(jsonObject.toString());
        return put(putRequest);
    }
}
