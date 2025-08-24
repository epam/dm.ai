package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.gitlab.model.*;
import com.github.istin.dmtools.job.JobRunner;
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

        do {
            String path = path(String.format("projects/%s/merge_requests?state=%s&per_page=%d&page=%d",
                    getEncodedProject(workspace, repository), state, perPage, page));

            GenericRequest getRequest = new GenericRequest(this, path);
            String response = execute(getRequest);

            if (response == null || response.isEmpty()) {
                break;
            }

            List<IPullRequest> pullRequests = JSONModel.convertToModels(GitLabPullRequest.class, new JSONArray(response));
            allPullRequests.addAll(pullRequests);
            if (startDate != null && !pullRequests.isEmpty() && pullRequests.getLast().getCreatedDate() < startDate.getTimeInMillis()) {
                break;
            }
            if (!checkAllRequests || pullRequests.size() < perPage) {
                // Stop if we are not checking all requests or if there are fewer pull requests than the perPage limit
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

    @Override
    public List<IComment> pullRequestComments(String workspace, String repository, String pullRequestId) throws IOException {
        List<IComment> pullRequestNotes = getPullRequestNotes(workspace, repository, pullRequestId);
        return pullRequestNotes.stream()
                .filter(comment -> !((GitLabComment)comment).isSystem())
                .filter(comment -> ((GitLabComment)comment).getType() != null)
                .filter(comment -> ((GitLabComment)comment).getType().equalsIgnoreCase("DiffNote"))
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
    public List<IActivity> pullRequestActivities(String workspace, String repository, String pullRequestId) throws IOException {
        List<IComment> pullRequestNotes = getPullRequestNotes(workspace, repository, pullRequestId);
        return pullRequestNotes.stream()
                .filter(comment -> ((GitLabComment)comment).getType() == null)
                .filter(comment -> comment.getBody().toLowerCase().contains("approved"))
                .map(new Function<IComment, IActivity>() {
                    @Override
                    public IActivity apply(IComment comment) {
                        return new IActivity() {
                            @Override
                            public String getAction() {
                                return "APPROVED";
                            }

                            @Override
                            public IComment getComment() {
                                return comment;
                            }

                            @Override
                            public IUser getApproval() {
                                return comment.getAuthor();
                            }
                        };
                    }
            })
            .collect(Collectors.toList());
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

    @Override
    public String addPullRequestComment(String workspace, String repository, String pullRequestId, String text) throws IOException {
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
        String path = path(String.format("projects/%s/repository/commits?ref_name=%s", getEncodedProject(workspace, repository), branchName));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitLabCommit.class, new JSONArray(response));
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