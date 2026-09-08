// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.github.model.GitHubComment;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Issue and issue-comment MCP tools for GitHub.
 *
 * <p>This class carries the tracker-critical issue tool family that the
 * tracker-agnostic agent helpers ({@code js/common/trackers.js} in
 * dmtools-agents) call through the JS bridge. Tool names and parameter names
 * mirror the dmtools-dart catalog 1:1 so the same agent code runs on both
 * runtimes.</p>
 *
 * <p>Split out of {@link GitHub} to keep that file under the file-size
 * quality gate.</p>
 */
public abstract class GitHubIssues extends GitHub {

    public GitHubIssues(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    /**
     * 404-tolerant override: the {@code pulls/{id}/comments} endpoint 404s for
     * plain issues (non-PRs), which the tracker-agnostic helpers rely on when
     * GitHub is the tracker backend. Tolerate that 404 (treat as no inline
     * review comments) while still surfacing the issue discussion comments and
     * rethrowing any other error.
     */
    @Override
    public List<IComment> pullRequestComments(String workspace, String repository, String pullRequestId) throws IOException {
        List<IComment> result = new ArrayList<>();
        int perPage = 100;
        int currentPage = 1;

        while (true) {
            String path = path(String.format("repos/%s/%s/pulls/%s/comments?per_page=%d&page=%d",
                    workspace, repository, pullRequestId, perPage, currentPage));
            GenericRequest getRequest = new GenericRequest(this, path);
            String response;
            try {
                response = execute(getRequest);
            } catch (IOException e) {
                if (e instanceof RestClient.RestClientException
                        && ((RestClient.RestClientException) e).getCode() == 404) {
                    break;
                }
                throw e;
            }
            if (response == null || response.isEmpty()) {
                break;
            }
            JSONArray pageArray = new JSONArray(response);
            result.addAll(JSONModel.convertToModels(GitHubComment.class, pageArray));
            if (pageArray.length() < perPage) {
                break;
            }
            currentPage++;
        }

        result.addAll(pullRequestCommentsFromIssue(workspace, repository, pullRequestId));
        result.sort((c1, c2) -> c1.getCreated().compareTo(c2.getCreated()));
        return result;
    }

    @MCPTool(
            name = "github_create_issue",
            description = "Create a GitHub issue",
            integration = "github",
            category = "issues"
    )
    public String createIssue(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "title", description = "The title of the new issue", required = true, example = "Something is broken")
            String title,
            @MCPParam(name = "body", description = "The issue description (markdown)", required = false, example = "Steps to reproduce...")
            String body) throws IOException {
        String path = path(String.format("repos/%s/%s/issues", owner, repo));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", title);
        if (body != null && !body.trim().isEmpty()) {
            jsonObject.put("body", body);
        }
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_close_issue",
            description = "Close a GitHub issue",
            integration = "github",
            category = "issues"
    )
    public String closeIssue(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = true, example = "42")
            Integer number) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%d", owner, repo, number));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state", "closed");
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @MCPTool(
            name = "github_add_labels",
            description = "Add labels to a GitHub issue",
            integration = "github",
            category = "issues"
    )
    public String addLabels(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = true, example = "42")
            Integer number,
            @MCPParam(name = "labels", description = "The label names to add", required = true, example = "[\"bug\",\"help wanted\"]")
            String[] labels) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%d/labels", owner, repo, number));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        JSONArray labelsArray = new JSONArray();
        if (labels != null) {
            for (String label : labels) {
                labelsArray.put(label);
            }
        }
        jsonObject.put("labels", labelsArray);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_remove_label",
            description = "Remove a label from a GitHub issue",
            integration = "github",
            category = "issues"
    )
    public String removeLabel(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = true, example = "42")
            Integer number,
            @MCPParam(name = "label", description = "The name of the label to remove", required = true, example = "bug")
            String label) throws IOException {
        // Path-segment encoding: %20 for spaces (matches Dart Uri.encodeComponent),
        // not the form-style '+' that URLEncoder.encode produces.
        String encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8.name()).replace("+", "%20");
        String path = path(String.format("repos/%s/%s/issues/%d/labels/%s", owner, repo, number, encodedLabel));
        GenericRequest deleteRequest = new GenericRequest(this, path);
        return delete(deleteRequest);
    }

    @MCPTool(
            name = "github_create_comment",
            description = "Create a comment on a GitHub issue or pull request (PRs are issues upstream).",
            integration = "github",
            category = "comments"
    )
    public String createComment(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The issue or pull request number", required = true, example = "74")
            String pullRequestId,
            @MCPParam(name = "body", description = "The comment body text", required = true, example = "Looks good!", aliases = {"text"})
            String body) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%s/comments", workspace, repository, pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", body);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }
}
