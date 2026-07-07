// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.gitlab.model.*;
import com.github.istin.dmtools.job.JobRunner;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class GitLab extends AbstractRestClient implements SourceCode {
    private static final Logger logger = LogManager.getLogger(GitLab.class);
    private static final String API_VERSION = "v4"; // GitLab uses v4 API

    public GitLab(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
        setCacheGetRequestsEnabled(false);
    }

    @Override
    public String path(String path) {
        return getBasePath() + "/api/" + API_VERSION + "/" + path;
    }


    @Override
    public synchronized Request.Builder sign(Request.Builder builder) {
        return builder
                .header("Authorization", "Bearer " + authorization)
                .header("Accept",  "application/json")
                .header("Content-Type", "application/json");
    }

    @MCPTool(
            name = "gitlab_test",
            description = "Test GitLab connectivity by fetching the current user's profile",
            integration = "gitlab",
            category = "system"
    )
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();
        try {
            GenericRequest getRequest = new GenericRequest(this, path("user"));
            String response = execute(getRequest);
            if (response != null && !response.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.has("id") || jsonResponse.has("username")) {
                    result.put("success", true);
                    result.put("message", "GitLab API connection successful");
                    result.put("user", jsonResponse.optString("username", "unknown"));
                    result.put("userId", jsonResponse.optString("id", "unknown"));
                    result.put("email", jsonResponse.optString("email", "unknown"));
                } else {
                    result.put("success", false);
                    result.put("message", "Unexpected response format from GitLab API");
                }
            } else {
                result.put("success", false);
                result.put("message", "Empty response from GitLab API");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "GitLab API connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            logger.warn("GitLab connection test failed", e);
        }
        return result;
    }

    @Override
    public List<IPullRequest> pullRequests(String workspace, String repository, String state, boolean checkAllRequests, Calendar startDate) throws IOException {
        List<IPullRequest> allPullRequests = new ArrayList<>();
        int page = 1;
        int perPage = 100; // Adjust as needed, GitLab default is 20, max is 100

        // Build base URL with optional created_after for server-side filtering.
        // Sort ascending (oldest first) so early pages are stable between daily runs
        // and the HTTP cache only misses on the last page (newest MRs).
        String baseUrl = String.format("projects/%s/merge_requests?state=%s&per_page=%d&order_by=created_at&sort=asc",
                getEncodedProject(workspace, repository), state, perPage);
        if (startDate != null) {
            String createdAfter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(startDate.getTime());
            baseUrl += "&created_after=" + createdAfter;
        }

        do {
            String path = path(baseUrl + "&page=" + page);

            GenericRequest getRequest = new GenericRequest(this, path);
            String response = execute(getRequest);

            if (response == null || response.isEmpty()) {
                break;
            }

            List<IPullRequest> pullRequests = JSONModel.convertToModels(GitLabPullRequest.class, new JSONArray(response));
            allPullRequests.addAll(pullRequests);
            if (!checkAllRequests || pullRequests.size() < perPage) {
                break;
            }

            page++;
        } while (true);

        return allPullRequests;
    }

    @Override
    public IPullRequest pullRequest(String workspace, String repository, String pullRequestId) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s", getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        return new GitLabPullRequest(response);
    }

    @MCPTool(
        name = "gitlab_get_mr_comments",
        description = "Get all comments for a GitLab merge request, including both inline code review comments (DiffNote) and general discussion notes. Excludes system-generated notes.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_get_pr_comments"}
    )
    @Override
    public List<IComment> pullRequestComments(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId) throws IOException {
        List<IComment> pullRequestNotes = getPullRequestNotes(workspace, repository, pullRequestId);
        return pullRequestNotes.stream()
                .filter(comment -> !((GitLabComment)comment).isSystem())
                .collect(Collectors.toList());
    }

    private @NotNull List<IComment> getPullRequestNotes(String workspace, String repository, String pullRequestId) throws IOException {
        int perPage = 100;
        int page = 1;
        List<IComment> result = new ArrayList<>();
        while (true) {
            String path = path(String.format("projects/%s/merge_requests/%s/notes", getEncodedProject(workspace, repository), pullRequestId));
            GenericRequest getRequest = new GenericRequest(this, path);
            getRequest.param("per_page", perPage);
            getRequest.param("page", page);
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                break;
            }
            JSONArray pageArray = new JSONArray(response);
            result.addAll(JSONModel.convertToModels(GitLabComment.class, pageArray));
            if (pageArray.length() < perPage) {
                break;
            }
            page++;
        }
        return result;
    }

    @Override
    @MCPTool(
        name = "gitlab_add_mr_label",
        description = "Add a label to a GitLab merge request.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_add_pr_label"}
    )
    public void addPullRequestLabel(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "label", description = "Label to add", required = true, example = "pr_approved") String label) throws IOException {
        updateMergeRequestLabels(workspace, repository, pullRequestId, "add_labels", label);
    }

    @Override
    @MCPTool(
        name = "gitlab_remove_mr_label",
        description = "Remove a label from a GitLab merge request.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_remove_pr_label"}
    )
    public void removePullRequestLabel(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "label", description = "Label to remove", required = true, example = "pr_approved") String label) throws IOException {
        updateMergeRequestLabels(workspace, repository, pullRequestId, "remove_labels", label);
    }

    private String updateMergeRequestLabels(String workspace, String repository, String pullRequestId, String field, String label) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s", getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest putRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put(field, label);
        putRequest.setBody(body.toString());
        return put(putRequest);
    }

    @MCPTool(
        name = "gitlab_get_mr_activities",
        description = "Get all activities for a GitLab merge request. Approvals are fetched from the real GitLab Approvals API. General discussion notes are also included.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_get_pr_activities"}
    )
    @Override
    public List<IActivity> pullRequestActivities(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId) throws IOException {
        List<IActivity> activities = new ArrayList<>();

        // Fetch real approvals via the GitLab MR Approvals API
        // GET /projects/:id/merge_requests/:iid/approvals → { "approved_by": [ { "user": {...} } ] }
        String approvalsPath = path(String.format("projects/%s/merge_requests/%s/approvals",
                getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest approvalsRequest = new GenericRequest(this, approvalsPath);
        String approvalsResponse = execute(approvalsRequest);
        if (approvalsResponse != null && !approvalsResponse.isEmpty()) {
            JSONArray approvedBy = new JSONObject(approvalsResponse).optJSONArray("approved_by");
            if (approvedBy != null) {
                for (int i = 0; i < approvedBy.length(); i++) {
                    GitLabMRApproval approval = new GitLabMRApproval(approvedBy.getJSONObject(i));
                    IUser approver = approval.getUser();
                    activities.add(new IActivity() {
                        @Override public String getAction() { return "APPROVED"; }
                        @Override public IComment getComment() { return null; }
                        @Override public IUser getApproval() { return approver; }
                    });
                }
            }
        }

        // Also include non-system discussion notes as COMMENTED activities
        List<IComment> notes = getPullRequestNotes(workspace, repository, pullRequestId);
        for (IComment note : notes) {
            if (((GitLabComment) note).isSystem()) continue;
            activities.add(new IActivity() {
                @Override public String getAction() { return "COMMENTED"; }
                @Override public IComment getComment() { return note; }
                @Override public IUser getApproval() { return null; }
            });
        }

        return activities;
    }

    @Override
    public List<ITask> pullRequestTasks(String workspace, String repository, String pullRequestId) throws IOException {
        // GitLab doesn't have "tasks" but we can interpret "to-do" items if possible
        throw new UnsupportedOperationException("GitLab does not natively support tasks similar to GitHub.");
    }

    @Override
    public String addTask(Integer commentId, String text) throws IOException {
        throw new UnsupportedOperationException("GitLab does not support adding tasks to comments.");
    }

    @Override
    public String createPullRequestCommentAndTaskIfNotExists(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException {
        throw new UnsupportedOperationException("GitLab does not support creating comments and tasks.");
    }

    @Override
    public String createPullRequestCommentAndTask(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException {
        throw new UnsupportedOperationException("GitLab does not support creating comments and tasks.");
    }

    @MCPTool(
        name = "gitlab_add_mr_comment",
        description = "Add a general discussion comment to a GitLab merge request.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_add_pr_comment"}
    )
    @Override
    public String addPullRequestComment(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "text", description = "Comment text", required = true, example = "LGTM!") String text) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s/notes", getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", text);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @Override
    public JSONModel commitComments(String workspace, String repository, String commitId) throws IOException {
        String path = path(String.format("projects/%s/repository/commits/%s/comments", getEncodedProject(workspace, repository), commitId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return new JSONModel();
        }
        return new JSONModel(response);
    }

    @Override
    public String renamePullRequest(String workspace, String repository, IPullRequest pullRequest, String newTitle) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s", getEncodedProject(workspace, repository), pullRequest.getId()));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", newTitle);
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @Override
    public List<ITag> getTags(String workspace, String repository) throws IOException {
        String path = path(String.format("projects/%s/repository/tags", getEncodedProject(workspace, repository)));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabTag.class, new JSONArray(response));
    }

    @Override
    public List<ITag> getBranches(String workspace, String repository) throws IOException {
        String path = path(String.format("projects/%s/repository/branches", getEncodedProject(workspace, repository)));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabTag.class, new JSONArray(response));
    }

    @Override
    public List<IRepository> getRepositories(String namespace) throws IOException {
        String path = path(String.format("groups/%s/projects", namespace)); // or "users/%s/projects" for user namespace
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabProject.class, new JSONArray(response));
    }

    @Override
    public List<ICommit> getCommitsBetween(String workspace, String repository, String from, String to) throws IOException {
        String path = path(String.format("projects/%s/repository/compare?from=%s&to=%s", getEncodedProject(workspace, repository), from, to));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabCommit.class, new JSONObject(response).getJSONArray("commits"));
    }

    @Override
    public List<ICommit> getCommitsFromBranch(String workspace, String repository, String branchName, String startDate, String endDate) throws IOException {
        List<ICommit> allCommits = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        do {
            StringBuilder urlBuilder = new StringBuilder(
                    String.format("projects/%s/repository/commits?ref_name=%s&per_page=%d&page=%d&with_stats=true",
                            getEncodedProject(workspace, repository), branchName, perPage, page));
            if (startDate != null && !startDate.isEmpty()) {
                urlBuilder.append("&since=").append(startDate);
            }
            GenericRequest getRequest = new GenericRequest(this, path(urlBuilder.toString()));
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                break;
            }
            List<ICommit> commits = JSONModel.convertToModels(GitLabCommit.class, new JSONArray(response));
            allCommits.addAll(commits);
            if (commits.size() < perPage) {
                break;
            }
            page++;
        } while (true);
        return allCommits;
    }

    @Override
    public void performCommitsFromBranch(String workspace, String repository, String branchName, Performer<ICommit> performer) throws Exception {
        List<ICommit> commits = getCommitsFromBranch(workspace, repository, branchName, null, null);
        for (ICommit commit : commits) {
            if (performer.perform(commit)) {
                break;
            }
        }
    }

    @Override
    public IDiffStats getCommitDiffStat(String workspace, String repository, String commitId) throws IOException {
        return getCommitAsObject(workspace, repository, commitId);
    }

    @Override
    @MCPTool(
        name = "gitlab_get_mr_diff",
        description = "Get diff stats and changed files for a GitLab merge request.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_get_pr_diff"}
    )
    public IDiffStats getPullRequestDiff(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestID) throws IOException {
        String mergeRequestChanges = getMergeRequestChanges(workspace, repository, pullRequestID);
        if (mergeRequestChanges == null) {
            return new IDiffStats.Empty();
        }
        return parseDiffStats(new JSONObject(mergeRequestChanges));
    }

    @MCPTool(
        name = "gitlab_get_mr_diff_text",
        description = "Get the raw unified diff text for a GitLab merge request (suitable for locating file/line positions, e.g. for inline review comments).",
        integration = "gitlab",
        category = "merge_requests"
    )
    public String getPullRequestDiffText(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestID) throws IOException {
        String mergeRequestChanges = getMergeRequestChanges(workspace, repository, pullRequestID);
        if (mergeRequestChanges == null) {
            return "";
        }
        return buildUnifiedDiffText(new JSONObject(mergeRequestChanges));
    }

    /**
     * The GitLab "changes" API returns each file's diff as a bare hunk body (starting at
     * {@code @@ ... @@}), without the leading {@code diff --git a/... b/...} / {@code --- a/...}
     * / {@code +++ b/...} header lines that a standard {@code git diff}/unified diff produces.
     * Callers that parse unified diff text (e.g. to map an inline review comment to a specific
     * file/line) need those headers to identify which file a hunk belongs to, so we reconstruct
     * them here from the per-change metadata.
     */
    private static String buildUnifiedDiffText(JSONObject response) {
        JSONArray changes = response.optJSONArray("changes");
        if (changes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.getJSONObject(i);
            String oldPath = change.optString("old_path", change.optString("new_path", ""));
            String newPath = change.optString("new_path", oldPath);
            boolean isNewFile = change.optBoolean("new_file", false);
            boolean isDeletedFile = change.optBoolean("deleted_file", false);
            String diff = change.optString("diff", "");

            sb.append("diff --git a/").append(oldPath).append(" b/").append(newPath).append("\n");
            if (isNewFile) {
                sb.append("new file mode 100644\n");
                sb.append("--- /dev/null\n");
                sb.append("+++ b/").append(newPath).append("\n");
            } else if (isDeletedFile) {
                sb.append("deleted file mode 100644\n");
                sb.append("--- a/").append(oldPath).append("\n");
                sb.append("+++ /dev/null\n");
            } else {
                sb.append("--- a/").append(oldPath).append("\n");
                sb.append("+++ b/").append(newPath).append("\n");
            }
            sb.append(diff);
            if (!diff.endsWith("\n")) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private boolean isMRChangesError = false;
    private String getMergeRequestChanges(String workspace, String repository, String pullRequestId) throws IOException {
        if (isMRChangesError) {
            return null;
        }
        String path = path(String.format("projects/%s/merge_requests/%s/changes", getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        try {
            return execute(getRequest);
        } catch (AtlassianRestClient.RestClientException e) {
            isMRChangesError = true;
            e.printStackTrace();
            return null;
        }
    }

    public static IDiffStats parseDiffStats(JSONObject response) {
        int addedLines = 0;
        int removedLines = 0;

        JSONArray changes = response.getJSONArray("changes");
        List<IChange> changesList = new ArrayList<>();
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.getJSONObject(i);
            changesList.add(new IChange() {
                @Override
                public String getFilePath() {
                    return change.getString("new_path");
                }
            });
            String diff = change.getString("diff");

            String[] lines = diff.split("\n");

            for (String line : lines) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    addedLines++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    removedLines++;
                }
            }
        }

        int changedLines = Math.min(addedLines, removedLines);
        int finalRemovedLines = removedLines;
        int finalAddedLines = addedLines;
        return new IDiffStats() {
            @Override
            public IStats getStats() {
                return new IStats() {
                    @Override
                    public int getTotal() {
                        return finalAddedLines + finalRemovedLines;
                    }

                    @Override
                    public int getAdditions() {
                        return finalAddedLines;
                    }

                    @Override
                    public int getDeletions() {
                        return finalRemovedLines;
                    }
                };
            }

            @Override
            public List<IChange> getChanges() {
                return changesList;
            }
        };
    }

    @Override
    public IBody getCommitDiff(String workspace, String repository, String commitId) throws IOException {
        String response = getCommitAsDiff(workspace, repository, commitId);
        return () -> response;
    }

    private GitLabCommit getCommitAsObject(String workspace, String repository, String commitId) throws IOException {
        String response = getCommit(workspace, repository, commitId);
        return new GitLabCommit(response);
    }

    private String getCommitAsDiff(String workspace, String repository, String commitId) throws IOException {
        return getCommit(workspace, repository, commitId);
    }

    private String getCommit(String workspace, String repository, String commitId) throws IOException {
        String path = path(String.format("projects/%s/repository/commits/%s", getEncodedProject(workspace, repository), commitId));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @Override
    public IBody getDiff(String workspace, String repository, String pullRequestId) throws IOException {
        String response = getMergeRequestChanges(workspace, repository, pullRequestId);
        return new IBody() {
            @Override
            public String getBody() {
                return response;
            }
        };
    }


    @Override
    public List<IFile> getListOfFiles(String workspace, String repository, String branchName) throws IOException {
        String path = path(String.format("projects/%s/repository/tree?ref=%s&recursive=true", getEncodedProject(workspace, repository), branchName));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabFile.class, new JSONArray(response));
    }

    @Override
    public String getFileContent(String selfLink) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, selfLink);
        String response = execute(getRequest);
        String content = new JSONModel(response).getString("content").replaceAll("\\r\\n|\\r|\\n", "");
        return JobRunner.decodeBase64(content);
    }

    @Override
    public String getDefaultRepository() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDefaultBranch() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDefaultWorkspace() {
        throw new UnsupportedOperationException();
    }

    @MCPTool(
        name = "gitlab_get_mr",
        description = "Get details of a specific GitLab merge request including title, description, state, author, diff_refs (base_sha, head_sha, start_sha needed for inline comments).",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_get_pr"}
    )
    public String getMRDetails(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId) throws IOException {
        IPullRequest mr = pullRequest(workspace, repository, pullRequestId);
        if (mr == null) return "{}";
        return ((GitLabPullRequest) mr).getJSONObject().toString();
    }

    @MCPTool(
        name = "gitlab_list_mrs",
        description = "List merge requests for a GitLab project. State can be 'opened', 'closed', 'merged', or 'all'.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_list_prs"}
    )
    public String listMergeRequests(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "state", description = "MR state: opened, closed, merged, all. 'open' is also accepted as a synonym for 'opened'.", required = true, example = "opened") String state) throws IOException {
        // Normalize state synonyms: GitHub uses "open", GitLab uses "opened"
        boolean includeClosedAndMerged = false;
        if ("open".equalsIgnoreCase(state)) {
            state = "opened";
        }
        if ("closed".equalsIgnoreCase(state)) {
            state = "all";
            includeClosedAndMerged = true;
        }
        List<IPullRequest> mrs = pullRequests(workspace, repository, state, false, null);
        JSONArray arr = new JSONArray();
        for (IPullRequest mr : mrs) {
            JSONObject mrJson = ((GitLabPullRequest) mr).getJSONObject();
            if (includeClosedAndMerged) {
                String mrState = mrJson.optString("state");
                if (!"closed".equalsIgnoreCase(mrState) && !"merged".equalsIgnoreCase(mrState)) {
                    continue;
                }
            }
            arr.put(mrJson);
        }
        return arr.toString();
    }

    @MCPTool(
        name = "gitlab_create_mr",
        description = "Create a GitLab merge request from a source branch into a target branch.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_create_pr"}
    )
    public String createMergeRequest(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "sourceBranch", description = "Source branch name", required = true, example = "feature/PROJ-123") String sourceBranch,
            @MCPParam(name = "targetBranch", description = "Target branch name", required = true, example = "main") String targetBranch,
            @MCPParam(name = "title", description = "Merge request title", required = true, example = "PROJ-123 Implement feature") String title,
            @MCPParam(name = "description", description = "Merge request description", required = false, example = "Automated changes") String description,
            @MCPParam(name = "removeSourceBranch", description = "Remove source branch after merge", required = false, example = "true") String removeSourceBranch) throws IOException {
        String path = path(String.format("projects/%s/merge_requests", getEncodedProject(workspace, repository)));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("source_branch", sourceBranch);
        body.put("target_branch", targetBranch);
        body.put("title", title);
        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }
        if (removeSourceBranch != null && !removeSourceBranch.isEmpty()) {
            body.put("remove_source_branch", Boolean.parseBoolean(removeSourceBranch));
        }
        postRequest.setBody(body.toString());
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_get_mr_discussions",
        description = "Get all discussion threads for a GitLab merge request. Each discussion contains notes (comments) and a resolved status. Use the discussion id with gitlab_resolve_mr_thread.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_get_pr_discussions"}
    )
    public String getPRDiscussions(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId) throws IOException {
        int perPage = 100;
        int page = 1;
        JSONArray allDiscussions = new JSONArray();
        while (true) {
            String path = path(String.format("projects/%s/merge_requests/%s/discussions", getEncodedProject(workspace, repository), pullRequestId));
            GenericRequest getRequest = new GenericRequest(this, path);
            getRequest.param("per_page", perPage);
            getRequest.param("page", page);
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                break;
            }
            JSONArray pageArray = new JSONArray(response);
            for (int i = 0; i < pageArray.length(); i++) {
                allDiscussions.put(pageArray.get(i));
            }
            if (pageArray.length() < perPage) {
                break;
            }
            page++;
        }
        return allDiscussions.toString();
    }

    @MCPTool(
        name = "gitlab_reply_to_mr_thread",
        description = "Reply to an existing discussion thread in a GitLab merge request. Use the discussion id from gitlab_get_mr_discussions.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_reply_to_pr_thread"}
    )
    public String replyToPullRequestComment(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "discussionId", description = "Discussion thread ID", required = true, example = "6a9c1750b37d57bba1079be3bbd13a...", aliases = {"threadId"}) String discussionId,
            @MCPParam(name = "text", description = "Reply text", required = true, example = "Addressed in latest commit") String text) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s/discussions/%s/notes",
                getEncodedProject(workspace, repository), pullRequestId, discussionId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("body", text);
        postRequest.setBody(body.toString());
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_add_inline_mr_comment",
        description = "Create a new inline code review comment on a specific file and line in a GitLab merge request. Requires base_sha, head_sha, start_sha from the MR diff refs (use gitlab_get_mr to get them from diff_refs).",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_add_inline_comment"}
    )
    public String addInlineReviewComment(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "filePath", description = "Path to the file to comment on", required = true, example = "src/main/Foo.java") String filePath,
            @MCPParam(name = "line", description = "Line number in the new file to comment on", required = true, example = "42") String line,
            @MCPParam(name = "text", description = "Comment text", required = true, example = "This looks wrong") String text,
            @MCPParam(name = "baseSha", description = "Base commit SHA from MR diff_refs", required = true, example = "abc123") String baseSha,
            @MCPParam(name = "headSha", description = "Head commit SHA from MR diff_refs", required = true, example = "def456") String headSha,
            @MCPParam(name = "startSha", description = "Start commit SHA from MR diff_refs", required = true, example = "abc123") String startSha) throws IOException {
        final int lineNum;
        try {
            lineNum = Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid line: expected numeric, got: '" + line + "'", e);
        }
        String path = path(String.format("projects/%s/merge_requests/%s/discussions",
                getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("body", text);
        JSONObject position = new JSONObject();
        position.put("position_type", "text");
        position.put("base_sha", baseSha);
        position.put("head_sha", headSha);
        position.put("start_sha", startSha);
        position.put("new_path", filePath);
        position.put("old_path", filePath);
        position.put("new_line", lineNum);
        body.put("position", position);
        postRequest.setBody(body.toString());
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_resolve_mr_thread",
        description = "Resolve (close) a review discussion thread in a GitLab merge request. Use the discussion id from gitlab_get_mr_discussions.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_resolve_pr_thread"}
    )
    public String resolveReviewThread(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "discussionId", description = "Discussion thread ID to resolve", required = true, example = "6a9c1750b37d57bba1079be3bbd13a...", aliases = {"threadId"}) String discussionId) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s/discussions/%s",
                getEncodedProject(workspace, repository), pullRequestId, discussionId));
        GenericRequest putRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("resolved", true);
        putRequest.setBody(body.toString());
        return put(putRequest);
    }

    @MCPTool(
        name = "gitlab_approve_mr",
        description = "Approve a GitLab merge request. Adds your approval to the MR.",
        integration = "gitlab",
        category = "merge_requests"
    )
    public String approveMergeRequest(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s/approve",
                getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        postRequest.setBody("{}");
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_merge_mr",
        description = "Merge a GitLab merge request. Optionally provide a custom merge commit message.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_merge_pr"}
    )
    public String mergeMergeRequest(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId,
            @MCPParam(name = "mergeCommitMessage", description = "Optional custom merge commit message", required = false, example = "Merge feature branch") String mergeCommitMessage) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s/merge",
                getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest putRequest = new GenericRequest(this, path);
        if (mergeCommitMessage != null && !mergeCommitMessage.isEmpty()) {
            JSONObject body = new JSONObject();
            body.put("merge_commit_message", mergeCommitMessage);
            putRequest.setBody(body.toString());
        } else {
            putRequest.setBody("{}");
        }
        return put(putRequest);
    }

    @MCPTool(
        name = "gitlab_rebase_mr",
        description = "Ask GitLab to rebase/update a merge request source branch with its target branch.",
        integration = "gitlab",
        category = "merge_requests",
        aliases = {"source_code_update_pr_branch"}
    )
    public String rebaseMergeRequest(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pullRequestId", description = "Merge request IID", required = true, example = "42") String pullRequestId) throws IOException {
        String path = path(String.format("projects/%s/merge_requests/%s/rebase",
                getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest putRequest = new GenericRequest(this, path);
        putRequest.setBody("{}");
        return put(putRequest);
    }

    private String getEncodedProject(String workspace, String repository) {
        try {
            return java.net.URLEncoder.encode(workspace + "/" + repository, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @MCPTool(
        name = "gitlab_list_project_jobs",
        description = "List recent GitLab CI jobs for a project.",
        integration = "gitlab",
        category = "ci"
    )
    public List<GitLabJob> listJobs(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository) throws IOException {
        int perPage = 100;
        int page = 1;
        JSONArray allJobs = new JSONArray();
        while (true) {
            String path = path(String.format("projects/%s/jobs", getEncodedProject(workspace, repository)));
            GenericRequest getRequest = new GenericRequest(this, path);
            getRequest.param("per_page", perPage);
            getRequest.param("page", page);
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                break;
            }
            JSONArray pageArray = new JSONArray(response);
            for (int i = 0; i < pageArray.length(); i++) {
                allJobs.put(pageArray.get(i));
            }
            if (pageArray.length() < perPage) {
                break;
            }
            page++;
        }
        if (allJobs.isEmpty()) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabJob.class, allJobs);
    }

    @MCPTool(
        name = "gitlab_cancel_job",
        description = "Cancel a GitLab CI job.",
        integration = "gitlab",
        category = "ci"
    )
    public String cancelJob(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "jobId", description = "GitLab job ID", required = true, example = "123456") String jobId) throws IOException {
        String path = path(String.format("projects/%s/jobs/%s/cancel", getEncodedProject(workspace, repository), jobId));
        GenericRequest postRequest = new GenericRequest(this, path);
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_list_pipeline_runs",
        description = "List recent GitLab CI pipelines. Optionally filter by status, ref, and limit.",
        integration = "gitlab",
        category = "ci",
        aliases = {"source_code_list_workflow_runs"}
    )
    public String listPipelineRuns(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "status", description = "Pipeline status filter", required = false, example = "failed") String status,
            @MCPParam(name = "ref", description = "Branch or tag ref", required = false, example = "main") String ref,
            @MCPParam(name = "limit", description = "Maximum number of pipelines to return", required = false, example = "50") String limit) throws IOException {
        int maxResults = parsePositiveInt(limit, 50);
        int perPage = Math.min(maxResults, 100);
        int page = 1;
        JSONArray pipelines = new JSONArray();
        while (pipelines.length() < maxResults) {
            String path = path(String.format("projects/%s/pipelines", getEncodedProject(workspace, repository)));
            GenericRequest getRequest = new GenericRequest(this, path);
            getRequest.param("status", normalizePipelineStatus(status));
            getRequest.param("ref", ref);
            getRequest.param("per_page", perPage);
            getRequest.param("page", page);
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                break;
            }
            JSONArray pageArray = new JSONArray(response);
            for (int i = 0; i < pageArray.length() && pipelines.length() < maxResults; i++) {
                pipelines.put(pageArray.get(i));
            }
            if (pageArray.length() < perPage) {
                break;
            }
            page++;
        }
        return pipelines.toString();
    }

    @MCPTool(
        name = "gitlab_trigger_pipeline",
        description = "Trigger a GitLab CI pipeline for a branch or tag using the authenticated API token.",
        integration = "gitlab",
        category = "ci",
        aliases = {"source_code_trigger_workflow"}
    )
    public String triggerPipeline(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "ref", description = "Branch or tag ref", required = true, example = "main") String ref,
            @MCPParam(name = "variablesJson", description = "Optional JSON object of CI variables", required = false, example = "{\"CONFIG_FILE\":\"agents/story_development.json\"}") String variablesJson) throws IOException {
        String path = path(String.format("projects/%s/pipeline", getEncodedProject(workspace, repository)));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("ref", ref);
        if (variablesJson != null && !variablesJson.isEmpty()) {
            JSONObject vars = new JSONObject(variablesJson);
            JSONArray variables = new JSONArray();
            for (String key : vars.keySet()) {
                JSONObject variable = new JSONObject();
                variable.put("key", key);
                variable.put("value", String.valueOf(vars.get(key)));
                variables.put(variable);
            }
            body.put("variables", variables);
        }
        postRequest.setBody(body.toString());
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_get_pipeline_jobs",
        description = "List jobs for a GitLab CI pipeline.",
        integration = "gitlab",
        category = "ci",
        aliases = {"source_code_get_workflow_run_jobs"}
    )
    public String getPipelineJobs(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "pipelineId", description = "GitLab pipeline ID", required = true, example = "123456") String pipelineId) throws IOException {
        int perPage = 100;
        int page = 1;
        JSONArray allJobs = new JSONArray();
        while (true) {
            String path = path(String.format("projects/%s/pipelines/%s/jobs", getEncodedProject(workspace, repository), pipelineId));
            GenericRequest getRequest = new GenericRequest(this, path);
            getRequest.param("per_page", perPage);
            getRequest.param("page", page);
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                break;
            }
            JSONArray pageArray = new JSONArray(response);
            for (int i = 0; i < pageArray.length(); i++) {
                allJobs.put(pageArray.get(i));
            }
            if (pageArray.length() < perPage) {
                break;
            }
            page++;
        }
        return allJobs.toString();
    }

    @MCPTool(
        name = "gitlab_get_job_logs",
        description = "Get GitLab CI job trace logs.",
        integration = "gitlab",
        category = "ci",
        aliases = {"source_code_get_job_logs"}
    )
    public String getJobLogs(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "jobId", description = "GitLab job ID", required = true, example = "123456") String jobId) throws IOException {
        String path = path(String.format("projects/%s/jobs/%s/trace", getEncodedProject(workspace, repository), jobId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        return response != null ? response : "";
    }

    @MCPTool(
        name = "gitlab_get_or_create_release",
        description = "Find an existing GitLab release by tag, or create one if it does not exist. Useful for a stable artefact storage release (mirrors github_get_or_create_draft_release). Note: GitLab releases have no draft concept; releases are visible as soon as they are created.",
        integration = "gitlab",
        category = "releases"
    )
    public String getOrCreateRelease(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "tagName", description = "The Git tag name for the release. Reused to find an existing release.", required = true, example = "pr-attachments-storage") String tagName,
            @MCPParam(name = "releaseName", description = "The human-readable release name. If empty, tagName is used.", required = false, example = "PR Attachments Storage") String releaseName,
            @MCPParam(name = "targetCommitish", description = "Optional branch or commit SHA the release's tag should point to when created (GitLab 'ref'). Required if the tag does not already exist.", required = false, example = "main") String targetCommitish,
            @MCPParam(name = "body", description = "Optional Markdown release notes/description.", required = false, example = "Internal storage release for PR attachments.") String body) throws IOException {
        String normalizedReleaseName = isBlank(releaseName) ? tagName : releaseName.trim();
        JSONObject existingRelease = findReleaseByTag(workspace, repository, tagName);
        if (existingRelease != null) {
            return existingRelease.toString();
        }
        return createRelease(workspace, repository, tagName, normalizedReleaseName, targetCommitish, body);
    }

    @MCPTool(
        name = "gitlab_upload_release_asset",
        description = "Upload a local file as a GitLab release asset. Internally publishes the file to the project's Generic Package Registry and attaches it to the release as an asset link, so it is discoverable the same way as a GitHub release asset. Returns the release link metadata including direct_asset_url. Set overwrite=true to automatically replace an existing asset with the same name before uploading (GitLab's Generic Package Registry rejects duplicate uploads with 409 Conflict otherwise).",
        integration = "gitlab",
        category = "releases"
    )
    public String uploadReleaseAsset(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "tagName", description = "The tag name of the release returned by gitlab_get_or_create_release.", required = true, example = "pr-attachments-storage") String tagName,
            @MCPParam(name = "filePath", description = "Absolute or relative path to the local file to upload.", required = true, example = "/tmp/preview.png") String filePath,
            @MCPParam(name = "assetName", description = "Optional asset filename shown in GitLab. Defaults to the local filename.", required = false, example = "clip_123.png") String assetName,
            @MCPParam(name = "contentType", description = "Optional MIME type. Defaults to detected type or application/octet-stream.", required = false, example = "image/png") String contentType,
            @MCPParam(name = "packageName", description = "Optional Generic Package Registry package name used to group the uploaded files. Defaults to 'release-assets'.", required = false, example = "release-assets") String packageName,
            @MCPParam(name = "overwrite", description = "If true, delete any existing asset (release link + underlying generic package file) with the same name before uploading. Defaults to false.", required = false, example = "true") String overwrite) throws IOException {
        File assetFile = new File(filePath);
        if (!assetFile.exists()) {
            throw new FileNotFoundException("Release asset file not found: " + assetFile.getAbsolutePath());
        }
        if (!assetFile.isFile()) {
            throw new IllegalArgumentException("Release asset path must point to a file: " + assetFile.getAbsolutePath());
        }
        String resolvedAssetName = isBlank(assetName) ? assetFile.getName() : assetName.trim();
        String resolvedPackageName = sanitizePackageComponent(isBlank(packageName) ? "release-assets" : packageName.trim());
        String packageVersion = sanitizePackageComponent(tagName);
        if (Boolean.parseBoolean(overwrite)) {
            deleteExistingAssetByName(workspace, repository, tagName, resolvedPackageName, packageVersion, resolvedAssetName);
        }
        String resolvedContentType = resolveAssetContentType(assetFile, contentType);
        String uploadUrl = path(String.format("projects/%s/packages/generic/%s/%s/%s",
                getEncodedProject(workspace, repository), resolvedPackageName, packageVersion, urlEncodePathSegment(resolvedAssetName)));
        uploadGenericPackageBinary(uploadUrl, assetFile, resolvedContentType);

        String directAssetPath = "/" + resolvedPackageName + "/" + packageVersion + "/" + resolvedAssetName;
        String downloadUrl = uploadUrl;

        String linksPath = path(String.format("projects/%s/releases/%s/assets/links",
                getEncodedProject(workspace, repository), urlEncodePathSegment(tagName)));
        GenericRequest postRequest = new GenericRequest(this, linksPath);
        JSONObject linkBody = new JSONObject();
        linkBody.put("name", resolvedAssetName);
        linkBody.put("url", downloadUrl);
        linkBody.put("direct_asset_path", directAssetPath);
        linkBody.put("link_type", "package");
        postRequest.setBody(linkBody.toString());
        return post(postRequest);
    }

    @MCPTool(
        name = "gitlab_list_release_assets",
        description = "List all assets (asset links) attached to a GitLab release. Returns a JSON array of link objects including id, name, url, and direct_asset_url.",
        integration = "gitlab",
        category = "releases"
    )
    public String listReleaseAssets(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "tagName", description = "The tag name of the release.", required = true, example = "pr-attachments-storage") String tagName) throws IOException {
        String path = path(String.format("projects/%s/releases/%s/assets/links",
                getEncodedProject(workspace, repository), urlEncodePathSegment(tagName)));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        return response != null ? response : "[]";
    }

    @MCPTool(
        name = "gitlab_delete_release_asset",
        description = "Delete a GitLab release asset by its asset name. Removes both the release link and the underlying Generic Package Registry file. Use gitlab_list_release_assets to find asset names.",
        integration = "gitlab",
        category = "releases"
    )
    public void deleteReleaseAsset(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "tagName", description = "The tag name of the release.", required = true, example = "pr-attachments-storage") String tagName,
            @MCPParam(name = "assetName", description = "The name of the asset to delete, as returned by gitlab_list_release_assets.", required = true, example = "clip_123.png") String assetName,
            @MCPParam(name = "packageName", description = "Optional Generic Package Registry package name the asset was uploaded under. Defaults to 'release-assets'.", required = false, example = "release-assets") String packageName) throws IOException {
        String resolvedPackageName = sanitizePackageComponent(isBlank(packageName) ? "release-assets" : packageName.trim());
        String packageVersion = sanitizePackageComponent(tagName);
        deleteExistingAssetByName(workspace, repository, tagName, resolvedPackageName, packageVersion, assetName);
    }

    @MCPTool(
        name = "gitlab_download_release_asset",
        description = "Download a GitLab release asset (a file published to the Generic Package Registry and attached to a release) to a local file path.",
        integration = "gitlab",
        category = "releases"
    )
    public String downloadReleaseAsset(
            @MCPParam(name = "workspace", description = "GitLab group or namespace", required = true, example = "mygroup") String workspace,
            @MCPParam(name = "repository", description = "Repository name", required = true, example = "myrepo") String repository,
            @MCPParam(name = "tagName", description = "The tag name of the release.", required = true, example = "pr-attachments-storage") String tagName,
            @MCPParam(name = "assetName", description = "The name of the asset to download, as returned by gitlab_list_release_assets.", required = true, example = "clip_123.png") String assetName,
            @MCPParam(name = "targetFilePath", description = "Local file path where the downloaded asset should be saved.", required = true, example = "/tmp/downloaded_clip_123.png") String targetFilePath,
            @MCPParam(name = "packageName", description = "Optional Generic Package Registry package name the asset was uploaded under. Defaults to 'release-assets'.", required = false, example = "release-assets") String packageName) throws IOException {
        String resolvedPackageName = sanitizePackageComponent(isBlank(packageName) ? "release-assets" : packageName.trim());
        String packageVersion = sanitizePackageComponent(tagName);
        String downloadPath = path(String.format("projects/%s/packages/generic/%s/%s/%s",
                getEncodedProject(workspace, repository), resolvedPackageName, packageVersion, urlEncodePathSegment(assetName)));
        return downloadBinaryToFile(downloadPath, new File(targetFilePath));
    }

    private JSONObject findReleaseByTag(String workspace, String repository, String tagName) throws IOException {
        int perPage = 100;
        int page = 1;
        while (true) {
            String releasesPath = path(String.format("projects/%s/releases", getEncodedProject(workspace, repository)));
            GenericRequest getRequest = new GenericRequest(this, releasesPath);
            getRequest.param("per_page", perPage);
            getRequest.param("page", page);
            String response = execute(getRequest);
            if (response == null || response.isEmpty()) {
                return null;
            }
            JSONArray releases = new JSONArray(response);
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                if (tagName.equals(release.optString("tag_name"))) {
                    return release;
                }
            }
            if (releases.length() < perPage) {
                return null;
            }
            page++;
        }
    }

    private String createRelease(String workspace, String repository, String tagName, String releaseName,
                                  String targetCommitish, String body) throws IOException {
        String releasesPath = path(String.format("projects/%s/releases", getEncodedProject(workspace, repository)));
        GenericRequest postRequest = new GenericRequest(this, releasesPath);
        JSONObject releaseBody = new JSONObject();
        releaseBody.put("tag_name", tagName);
        releaseBody.put("name", releaseName);
        // GitLab's release-create API requires 'ref' when the tag does not already exist
        // (returns 422 "Ref is not specified" otherwise). Fall back to the project's
        // default branch when the caller doesn't specify one.
        String ref = !isBlank(targetCommitish) ? targetCommitish.trim() : resolveDefaultBranch(workspace, repository);
        releaseBody.put("ref", ref);
        if (!isBlank(body)) {
            releaseBody.put("description", body);
        }
        postRequest.setBody(releaseBody.toString());
        return post(postRequest);
    }

    /**
     * Resolves the project's default branch, used as the release 'ref' when the caller
     * does not specify a targetCommitish. Falls back to "main" if the project's default
     * branch cannot be determined (e.g. transient API error).
     */
    private String resolveDefaultBranch(String workspace, String repository) {
        try {
            String projectPath = path(String.format("projects/%s", getEncodedProject(workspace, repository)));
            GenericRequest getRequest = new GenericRequest(this, projectPath);
            String response = execute(getRequest);
            if (response != null && !response.isEmpty()) {
                JSONObject project = new JSONObject(response);
                String defaultBranch = project.optString("default_branch", null);
                if (!isBlank(defaultBranch)) {
                    return defaultBranch;
                }
            }
        } catch (IOException e) {
            logger.warn("Could not resolve default branch for {}/{}: {}", workspace, repository, e.getMessage());
        }
        return "main";
    }

    private void deleteExistingAssetByName(String workspace, String repository, String tagName,
                                            String packageName, String packageVersion, String assetName) throws IOException {
        // Delete the release link first (if present), then the underlying generic package file.
        String linksPath = path(String.format("projects/%s/releases/%s/assets/links",
                getEncodedProject(workspace, repository), urlEncodePathSegment(tagName)));
        GenericRequest getLinksRequest = new GenericRequest(this, linksPath);
        String linksResponse = execute(getLinksRequest);
        if (linksResponse != null && !linksResponse.isEmpty()) {
            JSONArray links = new JSONArray(linksResponse);
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.getJSONObject(i);
                if (assetName.equals(link.optString("name"))) {
                    String linkId = String.valueOf(link.getLong("id"));
                    String deleteLinkPath = path(String.format("projects/%s/releases/%s/assets/links/%s",
                            getEncodedProject(workspace, repository), urlEncodePathSegment(tagName), linkId));
                    GenericRequest deleteLinkRequest = new GenericRequest(this, deleteLinkPath);
                    delete(deleteLinkRequest);
                    break;
                }
            }
        }

        String packageId = findGenericPackageId(workspace, repository, packageName, packageVersion);
        if (packageId != null) {
            String deletePackagePath = path(String.format("projects/%s/packages/%s",
                    getEncodedProject(workspace, repository), packageId));
            GenericRequest deletePackageRequest = new GenericRequest(this, deletePackagePath);
            delete(deletePackageRequest);
        }
    }

    private String findGenericPackageId(String workspace, String repository, String packageName, String packageVersion) throws IOException {
        String packagesPath = path(String.format("projects/%s/packages", getEncodedProject(workspace, repository)));
        GenericRequest getRequest = new GenericRequest(this, packagesPath);
        getRequest.param("package_type", "generic");
        getRequest.param("package_name", packageName);
        getRequest.param("package_version", packageVersion);
        String response = execute(getRequest);
        if (response == null || response.isEmpty()) {
            return null;
        }
        JSONArray packages = new JSONArray(response);
        for (int i = 0; i < packages.length(); i++) {
            JSONObject pkg = packages.getJSONObject(i);
            if (packageName.equals(pkg.optString("name")) && packageVersion.equals(pkg.optString("version"))) {
                return String.valueOf(pkg.getLong("id"));
            }
        }
        return null;
    }

    private String resolveAssetContentType(File assetFile, String explicitContentType) throws IOException {
        if (!isBlank(explicitContentType)) {
            return explicitContentType.trim();
        }
        String detected = Files.probeContentType(assetFile.toPath());
        if (isBlank(detected)) {
            detected = java.net.URLConnection.guessContentTypeFromName(assetFile.getName());
        }
        return isBlank(detected) ? "application/octet-stream" : detected;
    }

    protected String uploadGenericPackageBinary(String uploadUrl, File assetFile, String contentType) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse(contentType);
        if (mediaType == null) {
            throw new IllegalArgumentException("Invalid content type for release asset upload: " + contentType);
        }
        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(assetFile, mediaType);
        okhttp3.Request request = sign(new okhttp3.Request.Builder())
                .url(uploadUrl)
                .header("Content-Type", contentType)
                .put(requestBody)
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw AbstractRestClient.printAndCreateException(request, response);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    protected String downloadBinaryToFile(String downloadUrl, File targetFile) throws IOException {
        okhttp3.Request request = sign(new okhttp3.Request.Builder())
                .url(downloadUrl)
                .get()
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw AbstractRestClient.printAndCreateException(request, response);
            }
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (java.io.InputStream inputStream = response.body() != null ? response.body().byteStream() : null) {
                if (inputStream == null) {
                    throw new IOException("Empty response body while downloading: " + downloadUrl);
                }
                Files.copy(inputStream, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return targetFile.getAbsolutePath();
    }


    // GitLab Generic Package Registry names/versions only allow letters, digits, '.', '_', '+', '-'.
    private String sanitizePackageComponent(String value) {
        if (value == null) {
            return "unknown";
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9_.+-]", "-");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private String urlEncodePathSegment(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizePipelineStatus(String status) {
        if (status == null) {
            return null;
        }
        if ("failure".equalsIgnoreCase(status)) {
            return "failed";
        }
        if ("success".equalsIgnoreCase(status)) {
            return "success";
        }
        if ("in_progress".equalsIgnoreCase(status)) {
            return "running";
        }
        return status;
    }

    private int parsePositiveInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public String getPullRequestUrl(String workspace, String repository, String id) {
        return getBasePath().replaceAll("api.", "") + "/" + workspace + "/" + repository + "/-/merge_requests/" + id;
    }

    @Override
    public List<IFile> searchFiles(String workspace, String repository, String query, int filesLimit) throws IOException {
        throw new UnsupportedOperationException("Implement Me.");
    }
}
