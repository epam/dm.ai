// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue and issue-comment MCP tools for GitLab, carrying the tracker-aliases
 * so GitLab issues can serve as the tracker backend (DEFAULT_TRACKER=gitlab).
 *
 * <p>Tool and parameter names mirror the GitHub issue family
 * ({@link com.github.istin.dmtools.github.GitHubIssues}) so the same agent
 * code runs on both forges. On top of the canonical params, every issue tool
 * also accepts a composite {@code key} of the form
 * {@code namespace/project#iid}, the ecosystem router key {@code gl-N}
 * (issue N on the configured default project), or a bare issue IID — this is
 * what the generic {@code tracker_*} callers pass.</p>
 */
public abstract class GitLabIssues extends GitLab {

    /** {@code gl-123} — the dmtools-agents GitLab router key convention. */
    private static final Pattern GITLAB_ROUTER_KEY = Pattern.compile("^gl-(\\d+)$", Pattern.CASE_INSENSITIVE);

    public GitLabIssues(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    /** A resolved issue reference: full project path + issue IID. */
    protected static final class IssueRef {
        final String projectPath;
        final String iid;
        IssueRef(String projectPath, String iid) {
            this.projectPath = projectPath;
            this.iid = iid;
        }

        String getProjectPath() {
            return projectPath;
        }

        String getIid() {
            return iid;
        }
    }

    /**
     * Resolve an issue reference from either a composite {@code key}
     * ("namespace/project#123", "gl-123", or a bare IID) or explicit
     * workspace/repository/issueNumber parts. Explicit parts win over the
     * key; a key without a project path uses the configured default
     * workspace/repository.
     */
    protected IssueRef resolveIssueRef(String key, String workspace, String repository, String issueNumber) {
        String resolvedProject = null;
        if (workspace != null && !workspace.trim().isEmpty()
                && repository != null && !repository.trim().isEmpty()) {
            resolvedProject = workspace.trim() + "/" + repository.trim();
        }
        String resolvedIid = issueNumber != null && !issueNumber.trim().isEmpty()
                ? issueNumber.trim() : null;
        if (key != null && !key.trim().isEmpty() && (resolvedProject == null || resolvedIid == null)) {
            String k = key.trim();
            int separator = k.lastIndexOf('#');
            Matcher router = GITLAB_ROUTER_KEY.matcher(k);
            if (separator > 0 && separator < k.length() - 1
                    && k.substring(separator + 1).matches("\\d+")) {
                if (resolvedProject == null) {
                    resolvedProject = k.substring(0, separator);
                }
                if (resolvedIid == null) {
                    resolvedIid = k.substring(separator + 1);
                }
            } else if (router.matches()) {
                if (resolvedIid == null) {
                    resolvedIid = router.group(1);
                }
            } else if (k.matches("\\d+")) {
                if (resolvedIid == null) {
                    resolvedIid = k;
                }
            } else {
                throw new IllegalArgumentException("Cannot parse GitLab issue key: '" + key
                        + "'. Expected 'namespace/project#123', 'gl-123', or a bare issue IID.");
            }
        }
        if (resolvedProject == null) {
            String ws = getDefaultWorkspace();
            String rp = getDefaultRepository();
            if (ws != null && !ws.trim().isEmpty() && rp != null && !rp.trim().isEmpty()) {
                resolvedProject = ws.trim() + "/" + rp.trim();
            }
        }
        if (resolvedProject == null || resolvedIid == null) {
            throw new IllegalArgumentException("GitLab issue reference requires workspace/repository/issueNumber "
                    + "or a composite key 'namespace/project#123' (or 'gl-123' with a configured default project).");
        }
        return new IssueRef(resolvedProject, resolvedIid);
    }

    /** Path-segment encoding for the URL-encoded full project path. */
    protected static String encodeProjectPath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** API path of one issue: {@code projects/<encoded path>/issues/<iid>}. */
    protected String issuePath(IssueRef ref) {
        return path("projects/" + encodeProjectPath(ref.projectPath) + "/issues/" + ref.iid);
    }

    /** PUT a partial update (labels, state_event, assignee_ids, ...) to one issue. */
    protected String updateIssue(IssueRef ref, JSONObject body) throws IOException {
        GenericRequest putRequest = new GenericRequest(this, issuePath(ref));
        putRequest.setBody(body.toString());
        return put(putRequest);
    }

    @MCPTool(
            name = "gitlab_get_issue",
            description = "Get details of a GitLab issue including title, description, state, author, labels, and assignees.",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_get_ticket"}
    )
    public String getIssue(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        return execute(new GenericRequest(this, issuePath(ref)));
    }

    @MCPTool(
            name = "gitlab_search_issues",
            description = "Search GitLab issues of a project with a query string matched against title and description. Returns a JSON array of issues.",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_search"}
    )
    public String searchIssues(
            @MCPParam(name = "query", description = "Text matched against issue title and description", required = true, example = "login fails", aliases = {"jql", "wiql"})
            String query,
            @MCPParam(name = "workspace", description = "GitLab group or namespace to scope the search to", required = false, example = "mygroup")
            String workspace,
            @MCPParam(name = "repository", description = "Repository name to scope the search to", required = false, example = "myrepo")
            String repository,
            @MCPParam(name = "state", description = "Issue state filter: opened, closed, all (default: all)", required = false, example = "opened")
            String state) throws IOException {
        String projectPath = resolveProjectPath(workspace, repository);
        String effectiveState = state == null || state.trim().isEmpty() ? "all" : state.trim();
        GenericRequest request = new GenericRequest(this,
                path("projects/" + encodeProjectPath(projectPath) + "/issues"));
        request.param("state", effectiveState).param("per_page", 100);
        request.param("search", query == null ? "" : query);
        return execute(request);
    }

    @MCPTool(
            name = "gitlab_create_issue",
            description = "Create a GitLab issue",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_create_ticket"}
    )
    public String createIssue(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "title", description = "The title of the new issue", required = true, example = "Something is broken", aliases = {"summary"})
            String title,
            @MCPParam(name = "body", description = "The issue description (markdown)", required = false, example = "Steps to reproduce...", aliases = {"description"})
            String body,
            @MCPParam(name = "key", description = "Composite project key 'namespace/project' (alternative to workspace/repository)", required = false, example = "mygroup/myrepo", aliases = {"project"})
            String key) throws IOException {
        String projectPath;
        if (key != null && !key.trim().isEmpty() && key.contains("/")) {
            projectPath = key.trim();
        } else {
            projectPath = resolveProjectPath(workspace, repository);
        }
        GenericRequest postRequest = new GenericRequest(this,
                path("projects/" + encodeProjectPath(projectPath) + "/issues"));
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", title);
        if (body != null && !body.trim().isEmpty()) {
            jsonObject.put("description", body);
        }
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "gitlab_close_issue",
            description = "Close a GitLab issue",
            integration = "gitlab",
            category = "issues"
    )
    public String closeIssue(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        return updateIssue(ref, new JSONObject().put("state_event", "close"));
    }

    @MCPTool(
            name = "gitlab_reopen_issue",
            description = "Reopen a closed GitLab issue",
            integration = "gitlab",
            category = "issues"
    )
    public String reopenIssue(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        return updateIssue(ref, new JSONObject().put("state_event", "reopen"));
    }

    /**
     * Move an issue to a status. GitLab issues only have opened/closed
     * states, so "done"/"closed"/"completed"/"resolved" close the issue,
     * "open"/"opened"/"reopen"/"reopened" reopen it, and any other status is
     * carried as a scoped label {@code status::<status>} (scoped labels are
     * mutually exclusive on GitLab, so a new status label replaces the
     * previous one).
     */
    @MCPTool(
            name = "gitlab_move_issue_to_status",
            description = "Move a GitLab issue to a status. 'done'/'closed'/'completed'/'resolved' close the issue, 'open'/'reopened' reopen it; any other status is applied as a scoped 'status::<status>' label.",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_move_to_status"}
    )
    public String moveIssueToStatus(
            @MCPParam(name = "statusName", description = "The target status name", required = true, example = "Done", aliases = {"state", "status"})
            String statusName,
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        if (statusName == null || statusName.trim().isEmpty()) {
            throw new IllegalArgumentException("statusName is required");
        }
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        String s = statusName.trim().toLowerCase();
        if (s.equals("done") || s.equals("closed") || s.equals("completed") || s.equals("resolved")) {
            return updateIssue(ref, new JSONObject().put("state_event", "close"));
        }
        if (s.equals("open") || s.equals("opened") || s.equals("reopen") || s.equals("reopened")) {
            return updateIssue(ref, new JSONObject().put("state_event", "reopen"));
        }
        return updateIssue(ref, new JSONObject().put("add_labels", "status::" + statusName.trim()));
    }

    @MCPTool(
            name = "gitlab_assign_issue",
            description = "Assign a GitLab issue to a user by username",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_assign_ticket", "tracker_assign"}
    )
    public String assignIssue(
            @MCPParam(name = "user", description = "The assignee GitLab username", required = true, example = "jsmith", aliases = {"accountId", "assignee", "userName"})
            String user,
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException("GitLab username must not be empty");
        }
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        GenericRequest userRequest = new GenericRequest(this, path("users"));
        userRequest.param("username", user.trim());
        JSONArray users = new JSONArray(execute(userRequest));
        if (users.length() == 0) {
            throw new IOException("GitLab user not found: " + user);
        }
        int userId = users.getJSONObject(0).getInt("id");
        return updateIssue(ref, new JSONObject().put("assignee_ids", new JSONArray().put(userId)));
    }

    @MCPTool(
            name = "gitlab_add_issue_label",
            description = "Add a label to a GitLab issue",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_add_label"}
    )
    public String addIssueLabel(
            @MCPParam(name = "label", description = "The label name to add", required = true, example = "bug", aliases = {"labels"})
            String label,
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        return updateIssue(ref, new JSONObject().put("add_labels", label));
    }

    @MCPTool(
            name = "gitlab_remove_issue_label",
            description = "Remove a label from a GitLab issue",
            integration = "gitlab",
            category = "issues",
            aliases = {"tracker_remove_label"}
    )
    public String removeIssueLabel(
            @MCPParam(name = "label", description = "The label name to remove", required = true, example = "bug")
            String label,
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        return updateIssue(ref, new JSONObject().put("remove_labels", label));
    }

    @MCPTool(
            name = "gitlab_create_issue_comment",
            description = "Create a comment (note) on a GitLab issue",
            integration = "gitlab",
            category = "comments",
            aliases = {"tracker_post_comment"}
    )
    public String createIssueComment(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "body", description = "The comment body text", required = true, example = "Looks good!", aliases = {"text", "comment"})
            String body,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        GenericRequest postRequest = new GenericRequest(this, issuePath(ref) + "/notes");
        postRequest.setBody(new JSONObject().put("body", body).toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "gitlab_get_issue_comments",
            description = "Get all comments (notes) of a GitLab issue, oldest first. Returns a JSON array of notes.",
            integration = "gitlab",
            category = "comments",
            aliases = {"tracker_get_comments"}
    )
    public String getIssueComments(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = false, example = "mygroup", aliases = {"owner"})
            String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = false, example = "myrepo", aliases = {"repo"})
            String repository,
            @MCPParam(name = "issueNumber", description = "The issue IID", required = false, example = "42", aliases = {"number", "iid", "pullRequestId"})
            String issueNumber,
            @MCPParam(name = "key", description = "Composite issue key 'namespace/project#123' or 'gl-123' (alternative to workspace/repository/issueNumber)", required = false, example = "mygroup/myrepo#42")
            String key) throws IOException {
        IssueRef ref = resolveIssueRef(key, workspace, repository, issueNumber);
        JSONArray all = new JSONArray();
        int page = 1;
        while (true) {
            GenericRequest request = new GenericRequest(this, issuePath(ref) + "/notes");
            request.param("sort", "asc").param("per_page", 100).param("page", page);
            JSONArray notes = new JSONArray(execute(request));
            for (int i = 0; i < notes.length(); i++) {
                all.put(notes.get(i));
            }
            if (notes.length() < 100) {
                return all.toString();
            }
            page++;
        }
    }

    /** Full project path from explicit parts or the configured defaults. */
    protected String resolveProjectPath(String workspace, String repository) {
        String ws = workspace != null && !workspace.trim().isEmpty() ? workspace.trim() : getDefaultWorkspace();
        String rp = repository != null && !repository.trim().isEmpty() ? repository.trim() : getDefaultRepository();
        if (ws == null || ws.trim().isEmpty() || rp == null || rp.trim().isEmpty()) {
            throw new IllegalArgumentException("GitLab project is not configured. Pass workspace/repository "
                    + "or set GITLAB_WORKSPACE and GITLAB_REPOSITORY.");
        }
        return ws.trim() + "/" + rp.trim();
    }
}
