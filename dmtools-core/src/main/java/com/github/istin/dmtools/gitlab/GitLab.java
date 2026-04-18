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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
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
        String path = path(String.format("projects/%s/merge_requests/%s/notes", getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return new ArrayList<>();
        }
        return JSONModel.convertToModels(GitLabComment.class, new JSONArray(response));
    }

    @Override
    public void addPullRequestLabel(String workspace, String repository, String pullRequestId, String label) throws IOException {
        throw new UnsupportedOperationException("implement me");
    }

    @Override
    public void removePullRequestLabel(String workspace, String repository, String pullRequestId, String label) throws IOException {
        throw new UnsupportedOperationException("implement me");
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
                    String.format("projects/%s/repository/commits?ref_name=%s&per_page=%d&page=%d",
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
    public IDiffStats getPullRequestDiff(String workspace, String repository, String pullRequestID) throws IOException {
        String mergeRequestChanges = getMergeRequestChanges(workspace, repository, pullRequestID);
        if (mergeRequestChanges == null) {
            return new IDiffStats.Empty();
        }
        return parseDiffStats(new JSONObject(mergeRequestChanges));
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
        if ("open".equalsIgnoreCase(state)) {
            state = "opened";
        }
        List<IPullRequest> mrs = pullRequests(workspace, repository, state, false, null);
        JSONArray arr = new JSONArray();
        for (IPullRequest mr : mrs) {
            arr.put(((GitLabPullRequest) mr).getJSONObject());
        }
        return arr.toString();
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
        String path = path(String.format("projects/%s/merge_requests/%s/discussions", getEncodedProject(workspace, repository), pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        return response != null ? response : "[]";
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

    private String getEncodedProject(String workspace, String repository) {
        try {
            return java.net.URLEncoder.encode(workspace + "/" + repository, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // Method to list jobs for a project
    public List<GitLabJob> listJobs(String workspace, String repository) throws IOException {
        String path = path(String.format("projects/%s/jobs", getEncodedProject(workspace, repository)));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabJob.class, new JSONArray(response));
    }

    // Method to cancel a specific job in a project
    public String cancelJob(String workspace, String repository, String jobId) throws IOException {
        String path = path(String.format("projects/%s/jobs/%s/cancel", getEncodedProject(workspace, repository), jobId));
        GenericRequest postRequest = new GenericRequest(this, path);
        return post(postRequest);
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