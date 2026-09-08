// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.github.model.GitHubComment;
import com.github.istin.dmtools.github.model.GitHubIssue;
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
 * Issue and issue-comment MCP tools for GitHub, carrying the tracker-aliases
 * so GitHub issues can serve as the tracker backend (DEFAULT_TRACKER=github).
 *
 * <p>Tool names and parameter names mirror the dmtools-dart catalog 1:1 so the
 * same agent code runs on both runtimes. On top of the canonical params, every
 * issue tool also accepts a composite {@code key} of the form
 * {@code owner/repo#123} (or a bare issue number, resolved against the
 * configured default workspace/repository) — this is what the generic
 * {@code tracker_*} callers pass.</p>
 *
 * <p>Split out of {@link GitHub} to keep that file under the file-size
 * quality gate.</p>
 */
public abstract class GitHubIssues extends GitHub {

    public GitHubIssues(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    /** A resolved issue reference: owner + repo + issue number. */
    protected static final class IssueRef {
        final String owner;
        final String repo;
        final int number;
        IssueRef(String owner, String repo, int number) {
            this.owner = owner;
            this.repo = repo;
            this.number = number;
        }
    }

    /**
     * Resolve an issue reference from either a composite {@code key}
     * ("owner/repo#123" or a bare number) or explicit owner/repo/number parts.
     * Explicit parts win over the key; a bare-number key uses the configured
     * default workspace/repository.
     */
    protected IssueRef resolveIssueRef(String key, String owner, String repo, Integer number) {
        String resolvedOwner = owner;
        String resolvedRepo = repo;
        Integer resolvedNumber = number;
        if (key != null && !key.trim().isEmpty()) {
            String k = key.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^[\\w.-]+/[\\w.-]+#(\\d+)$").matcher(k);
            if (m.find()) {
                resolvedNumber = Integer.valueOf(m.group(1));
                resolvedOwner = k.substring(0, k.indexOf('/'));
                resolvedRepo = k.substring(k.indexOf('/') + 1, k.indexOf('#'));
            } else if (k.matches("^\\d+$")) {
                resolvedNumber = Integer.valueOf(k);
            } else {
                throw new IllegalArgumentException("Cannot parse GitHub issue key: '" + key
                        + "'. Expected 'owner/repo#123' or a bare issue number.");
            }
        }
        if (resolvedOwner == null || resolvedOwner.trim().isEmpty()) {
            resolvedOwner = getDefaultWorkspace();
        }
        if (resolvedRepo == null || resolvedRepo.trim().isEmpty()) {
            resolvedRepo = getDefaultRepository();
        }
        if (resolvedOwner == null || resolvedRepo == null || resolvedNumber == null) {
            throw new IllegalArgumentException(
                    "Issue reference requires owner/repo/number or a composite key 'owner/repo#123'.");
        }
        return new IssueRef(resolvedOwner, resolvedRepo, resolvedNumber);
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
            name = "github_get_pr_comments",
            description = "Get all comments for a GitHub pull request or issue, including both inline code review comments and general discussion comments. Results are sorted by creation date.",
            integration = "github",
            category = "pull_requests",
            aliases = {"source_code_get_pr_comments", "tracker_get_comments"}
    )
    public List<IComment> getPrComments(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = false, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = false, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request or issue number", required = false, example = "74", aliases = {"number"})
            String pullRequestId,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to workspace/repository/pullRequestId)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        Integer number = pullRequestId != null && !pullRequestId.trim().isEmpty() ? Integer.valueOf(pullRequestId.trim()) : null;
        IssueRef ref = resolveIssueRef(key, workspace, repository, number);
        return pullRequestComments(ref.owner, ref.repo, String.valueOf(ref.number));
    }

    @MCPTool(
            name = "github_get_issue",
            description = "Get details of a GitHub issue including title, description, state, author, labels, assignees, and comments count.",
            integration = "github",
            category = "issues",
            aliases = {"source_code_get_issue", "tracker_get_ticket"}
    )
    public GitHubIssue issue(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = false, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = false, example = "dmtools")
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue number", required = false, example = "42", aliases = {"number"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to workspace/repository/issueNumber)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        Integer number = issueNumber != null && !issueNumber.trim().isEmpty() ? Integer.valueOf(issueNumber.trim()) : null;
        IssueRef ref = resolveIssueRef(key, workspace, repository, number);
        String path = path(String.format("repos/%s/%s/issues/%d", ref.owner, ref.repo, ref.number));
        GenericRequest getRequest = new GenericRequest(this, path);
        return new GitHubIssue(execute(getRequest));
    }

    @MCPTool(
            name = "github_search_issues",
            description = "Search GitHub issues (and pull requests) with a query string. Returns a JSON object with 'items'.",
            integration = "github",
            category = "issues",
            aliases = {"tracker_search"}
    )
    public String searchIssues(
            @MCPParam(name = "query", description = "The GitHub issue search query (e.g. 'repo:owner/name is:open label:bug')", required = true, example = "repo:IstiN/dmtools is:open", aliases = {"jql", "wiql"})
            String query,
            @MCPParam(name = "workspace", description = "The GitHub owner/organization to scope the search to", required = false, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository to scope the search to", required = false, example = "dmtools")
            String repository) throws IOException {
        String scopedQuery = query;
        String ws = workspace != null && !workspace.trim().isEmpty() ? workspace : getDefaultWorkspace();
        String rp = repository != null && !repository.trim().isEmpty() ? repository : getDefaultRepository();
        if (!scopedQuery.contains("repo:") && ws != null && rp != null) {
            scopedQuery = "repo:" + ws + "/" + rp + " " + scopedQuery;
        }
        String path = path("search/issues?q=" + URLEncoder.encode(scopedQuery, StandardCharsets.UTF_8.name()) + "&per_page=100");
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_create_issue",
            description = "Create a GitHub issue",
            integration = "github",
            category = "issues",
            aliases = {"tracker_create_ticket"}
    )
    public String createIssue(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "title", description = "The title of the new issue", required = true, example = "Something is broken", aliases = {"summary"})
            String title,
            @MCPParam(name = "body", description = "The issue description (markdown)", required = false, example = "Steps to reproduce...", aliases = {"description"})
            String body,
            @MCPParam(name = "key", description = "Composite project key 'owner/repo' (alternative to owner/repo)", required = false, example = "IstiN/dmtools", aliases = {"project"})
            String key) throws IOException {
        String resolvedOwner = owner;
        String resolvedRepo = repo;
        if (key != null && !key.trim().isEmpty() && key.contains("/")) {
            resolvedOwner = key.substring(0, key.indexOf('/'));
            resolvedRepo = key.substring(key.indexOf('/') + 1);
        }
        if (resolvedOwner == null || resolvedOwner.trim().isEmpty()) {
            resolvedOwner = getDefaultWorkspace();
        }
        if (resolvedRepo == null || resolvedRepo.trim().isEmpty()) {
            resolvedRepo = getDefaultRepository();
        }
        if (resolvedOwner == null || resolvedRepo == null) {
            throw new IllegalArgumentException("github_create_issue requires owner/repo or a composite key/project 'owner/repo'.");
        }
        String path = path(String.format("repos/%s/%s/issues", resolvedOwner, resolvedRepo));
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
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = false, example = "42")
            Integer number,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to owner/repo/number)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, owner, repo, number);
        String path = path(String.format("repos/%s/%s/issues/%d", ref.owner, ref.repo, ref.number));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state", "closed");
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @MCPTool(
            name = "github_reopen_issue",
            description = "Reopen a closed GitHub issue",
            integration = "github",
            category = "issues"
    )
    public String reopenIssue(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = false, example = "42")
            Integer number,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to owner/repo/number)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, owner, repo, number);
        String path = path(String.format("repos/%s/%s/issues/%d", ref.owner, ref.repo, ref.number));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("state", "open");
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    /**
     * Move an issue to a status. GitHub issues only have open/closed states, so
     * "done"/"closed" close the issue, "open"/"reopened" reopen it, and any
     * other status is carried as an issue label (mirrors trackers.js).
     */
    @MCPTool(
            name = "github_move_issue_to_status",
            description = "Move a GitHub issue to a status. 'done'/'closed' close the issue, 'open'/'reopened' reopen it; any other status is applied as an issue label.",
            integration = "github",
            category = "issues",
            aliases = {"tracker_move_to_status"}
    )
    public String moveIssueToStatus(
            @MCPParam(name = "statusName", description = "The target status name", required = true, example = "Done", aliases = {"state", "status"})
            String statusName,
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = false, example = "42")
            Integer number,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to owner/repo/number)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        if (statusName == null || statusName.trim().isEmpty()) {
            throw new IllegalArgumentException("statusName is required");
        }
        IssueRef ref = resolveIssueRef(key, owner, repo, number);
        String s = statusName.trim().toLowerCase();
        if (s.equals("done") || s.equals("closed") || s.equals("completed") || s.equals("resolved")) {
            return closeIssue(ref.owner, ref.repo, ref.number, null);
        }
        if (s.equals("open") || s.equals("reopened") || s.equals("reopen") || s.equals("todo") || s.equals("backlog") || s.equals("in progress")) {
            return reopenIssue(ref.owner, ref.repo, ref.number, null);
        }
        return addLabels(ref.owner, ref.repo, ref.number, new String[]{statusName.trim()}, null);
    }

    @MCPTool(
            name = "github_assign_issue",
            description = "Assign a GitHub issue to a user",
            integration = "github",
            category = "issues",
            aliases = {"tracker_assign_ticket"}
    )
    public String assignIssue(
            @MCPParam(name = "user", description = "The assignee GitHub login", required = true, example = "octocat", aliases = {"accountId", "assignee", "userName"})
            String user,
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = false, example = "42")
            Integer number,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to owner/repo/number)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, owner, repo, number);
        String path = path(String.format("repos/%s/%s/issues/%d/assignees", ref.owner, ref.repo, ref.number));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        JSONArray assignees = new JSONArray();
        assignees.put(user);
        jsonObject.put("assignees", assignees);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_add_labels",
            description = "Add labels to a GitHub issue",
            integration = "github",
            category = "issues"
    )
    public String addLabels(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = false, example = "42")
            Integer number,
            @MCPParam(name = "labels", description = "The label names to add", required = true, example = "[\"bug\",\"help wanted\"]")
            String[] labels,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to owner/repo/number)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, owner, repo, number);
        String path = path(String.format("repos/%s/%s/issues/%d/labels", ref.owner, ref.repo, ref.number));
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
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = false, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = false, example = "dmtools")
            String repo,
            @MCPParam(name = "number", description = "The issue number", required = false, example = "42")
            Integer number,
            @MCPParam(name = "label", description = "The name of the label to remove", required = true, example = "bug")
            String label,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to owner/repo/number)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, owner, repo, number);
        // Path-segment encoding: %20 for spaces (matches Dart Uri.encodeComponent),
        // not the form-style '+' that URLEncoder.encode produces.
        String encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8.name()).replace("+", "%20");
        String path = path(String.format("repos/%s/%s/issues/%d/labels/%s", ref.owner, ref.repo, ref.number, encodedLabel));
        GenericRequest deleteRequest = new GenericRequest(this, path);
        return delete(deleteRequest);
    }

    @MCPTool(
            name = "github_create_comment",
            description = "Create a comment on a GitHub issue or pull request (PRs are issues upstream).",
            integration = "github",
            category = "comments",
            aliases = {"tracker_post_comment"}
    )
    public String createComment(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = false, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = false, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The issue or pull request number", required = false, example = "74")
            String pullRequestId,
            @MCPParam(name = "body", description = "The comment body text", required = true, example = "Looks good!", aliases = {"text", "comment"})
            String body,
            @MCPParam(name = "key", description = "Composite issue key 'owner/repo#123' (alternative to workspace/repository/pullRequestId)", required = false, example = "IstiN/dmtools#42")
            String key) throws IOException {
        Integer number = pullRequestId != null && !pullRequestId.trim().isEmpty() ? Integer.valueOf(pullRequestId.trim()) : null;
        IssueRef ref = resolveIssueRef(key, workspace, repository, number);
        String path = path(String.format("repos/%s/%s/issues/%d/comments", ref.owner, ref.repo, ref.number));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", body);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }
}
