package com.github.istin.dmtools.atlassian.bitbucket;

import com.github.istin.dmtools.atlassian.bitbucket.model.*;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
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

public abstract class Bitbucket extends AtlassianRestClient implements SourceCode {
    private static final Logger logger = LogManager.getLogger(Bitbucket.class);
    public static final boolean IGNORE_CACHE = false;

    public enum ApiVersion {
        V1,
        V2
    }

    private ApiVersion apiVersion = ApiVersion.V1;

    private int defaultLimit = 50;

    public Bitbucket(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    public void setApiVersion(ApiVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    @Override
    public String path(String path) {
        if (apiVersion == ApiVersion.V1) {
            return getBasePath() + "/rest/api/1.0/" + path;
        } else {
            return getBasePath() + "/2.0/" + path;
        }
    }

    @Override
    public List<IPullRequest> pullRequests(String workspace, String repository, String state, boolean checkAllRequests, Calendar startDate) throws IOException {
        List<IPullRequest> result = new ArrayList<>();
        int start = getInitialStartValue();
        String stateConverted = apiVersion == ApiVersion.V1 ? state : state.toUpperCase();
        GenericRequest getRequest = new GenericRequest(this, buildPullRequestsPath(workspace, repository, stateConverted));
        getRequest.param(getNextPageField(), start);
        getRequest.param(getPageLimitField(), String.valueOf(defaultLimit));
        getRequest.setIgnoreCache(IGNORE_CACHE);
        String response = execute(getRequest);
        if (response == null) {
            return result;
        }

        List<? extends PullRequest> values = new BitbucketResult(response).getValues(apiVersion);
        result.addAll(values);
        while (!values.isEmpty() && checkAllRequests) {
            start = buildNexPage(start);
            logger.info("pull requests: {}", start);

            getRequest = new GenericRequest(this, buildPullRequestsPath(workspace, repository, stateConverted));
            getRequest.param(getNextPageField(), ""+ start);
            getRequest.param(getPageLimitField(), String.valueOf(defaultLimit));
            getRequest.setIgnoreCache(IGNORE_CACHE);

            response = execute(getRequest);
            if (response == null) {
                return result;
            }

            values = new BitbucketResult(response).getValues(apiVersion);
            logger.info("pull requests response size: {}", values.size());
            if (!values.isEmpty()) {
                logger.info("pull requests response first item: {}", values.get(0).getId());
            }
            result.addAll(values);
            if (startDate != null && !result.isEmpty() && result.getLast().getCreatedDate() < startDate.getTimeInMillis()) {
                break;
            }
        }

        return result;
    }

    @Override
    public List<IRepository> getRepositories(String namespace) throws IOException {
        throw new UnsupportedOperationException("implement me");
    }

    @Override
    public IDiffStats getPullRequestDiff(String workspace, String repository, String pullRequestID) throws IOException {
        String pullRequestChanges = getPullRequestChanges(workspace, repository, pullRequestID);
        if (pullRequestChanges == null) {
            return new IDiffStats.Empty();
        }
        List<IChange> changes = new BitbucketResult(pullRequestChanges).getChanges();
        int additions = 0;
        int deletions = 0;
        for (IChange change : changes) {
            additions += ((Change)change).getLinesAdded();
            deletions += ((Change)change).getLinesRemoved();
        }
        int finalAdditions = additions;
        int finalDeletions = deletions;
        return new IDiffStats() {
            @Override
            public IStats getStats() {
                return new IStats() {
                    @Override
                    public int getTotal() {
                        return finalAdditions + finalDeletions;
                    }

                    @Override
                    public int getAdditions() {
                        return finalAdditions;
                    }

                    @Override
                    public int getDeletions() {
                        return finalDeletions;
                    }
                };
            }

            @Override
            public List<IChange> getChanges() {
                return changes;
            }
        };
    }

    private String getPullRequestChanges(String workspace, String repository, String pullRequestId) throws IOException {
        // Bitbucket API endpoint for pull request diff
        String path = path(String.format("repositories/%s/%s/pullrequests/%s/diffstat", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    private int getInitialStartValue() {
        return (apiVersion == ApiVersion.V1) ? 0 : 1;
    }

    @NotNull
    private String getNextPageField() {
        if (apiVersion == ApiVersion.V1) {
            return "start";
        } else {
            return "page";
        }
    }

    @Override
    public void addPullRequestLabel(String workspace, String repository, String pullRequestId, String label) throws IOException {
        throw new UnsupportedOperationException("implement me");
    }

    private int buildNexPage(int start) {
        if (apiVersion == ApiVersion.V1) {
            start = start + defaultLimit;
            return start;
        } else {
            return ++start;
        }
    }

    @NotNull
    private String getPageLimitField() {
        if (apiVersion == ApiVersion.V1) {
            return "limit";
        } else {
            return "pagelen";
        }
    }

    private String buildPullRequestsPath(String workspace, String repository, String state) {
        if (apiVersion == ApiVersion.V1) {
            return path("projects/" + workspace + "/repos/" + repository + "/pull-requests/?state=" + state);
        } else {
            return path("repositories/" + workspace + "/" + repository + "/pullrequests/?state=" + state);
        }
    }

    @Override
    public IPullRequest pullRequest(String workspace, String repository, String pullRequestId) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path(buildPullRequestPath(workspace, repository, pullRequestId)));
        getRequest.setIgnoreCache(IGNORE_CACHE);
        String response = execute(getRequest);
        return PullRequest.create(apiVersion, response);
    }

    @NotNull
    private String buildPullRequestPath(String workspace, String repository, String pullRequestId) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/" + repository + "/pull-requests/" + pullRequestId;
        } else {
            return "repositories/" + workspace + "/" + repository + "/pullrequests/" + pullRequestId;
        }
    }

    @Override
    public List<IComment> pullRequestComments(String workspace, String repository, String pullRequestId) throws IOException {
        GenericRequest getRequest = createPullRequestCommentsRequest(workspace, repository, pullRequestId);
        String response = execute(getRequest);
        if (response == null) {
            return new ArrayList<>();
        }

        return new BitbucketResult(response).getComments();
    }

    @NotNull
    private GenericRequest createPullRequestCommentsRequest(String workspace, String repository, String pullRequestId) {
        return new GenericRequest(this, path(buildPullRequestCommentsPath(workspace, repository, pullRequestId)));
    }

    @NotNull
    private String buildPullRequestCommentsPath(String workspace, String repository, String pullRequestId) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/" + repository + "/pull-requests/" + pullRequestId + "/comments";
        } else {
            return "repositories/" + workspace + "/"+ repository + "/pullrequests/" + pullRequestId + "/comments";
        }
    }

    @Override
    public List<IActivity> pullRequestActivities(String workspace, String repository, String pullRequestId) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path(buildPullRequestActivityPath(workspace, repository, pullRequestId)));
        if (apiVersion == ApiVersion.V2) {
            getRequest.param(getNextPageField(), getInitialStartValue());
            getRequest.param(getPageLimitField(), String.valueOf(defaultLimit));
        }
        String response = execute(getRequest);
        if (response == null) {
            return new BitbucketResult().getActivities();
        }
        return new BitbucketResult(response).getActivities();
    }

    @NotNull
    private String buildPullRequestActivityPath(String workspace, String repository, String pullRequestId) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/" + repository + "/pull-requests/" + pullRequestId + "/activities";
        } else {
            return "repositories/" + workspace + "/" + repository + "/pullrequests/" + pullRequestId + "/activity";
        }
    }

    public BitbucketResult repositories(String workspace) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path(buildRepositoriesPath(workspace)));
        getRequest.param(getPageLimitField(), defaultLimit);
        String response = execute(getRequest);
        if (response == null) {
            return new BitbucketResult();
        }
        return new BitbucketResult(response);
    }

    @NotNull
    private String buildRepositoriesPath(String workspace) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/";
        } else {
            return "repositories/" + workspace;
        }
    }

    @Override
    public List<ITask> pullRequestTasks(String workspace, String repository, String pullRequestId) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path(buildPullRequestTasksPath(workspace, repository, pullRequestId)));
        String response = execute(getRequest);
        if (response == null) {
            return new BitbucketResult().getTasks();
        }
        return new BitbucketResult(response).getTasks();
    }

    @NotNull
    private String buildPullRequestTasksPath(String workspace, String repository, String pullRequestId) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/" + repository + "/pull-requests/" + pullRequestId + "/tasks";
        } else {
            return "repositories/" + workspace + "/" + repository + "/pullrequests/" + pullRequestId + "/tasks";
        }
    }

    @Override
    public String addTask(Integer commentId, String text) throws IOException {
        //it's not supported in api V2
        GenericRequest getRequest = new GenericRequest(this, path("tasks"));
        JSONObject jsonObject = new JSONObject();

        createTaskObject(commentId, text, jsonObject);
        getRequest.setBody(jsonObject.toString());
        return post(getRequest);
    }

    private void createTaskObject(Integer commentId, String text, JSONObject jsonObject) {
        if (apiVersion == ApiVersion.V1) {
            jsonObject.put("anchor", new JSONObject().put("id", commentId).put("type", "COMMENT"));
            jsonObject.put("text", text);
        } else {
            jsonObject.put("comment", new JSONObject().put("id", String.valueOf(commentId)));
            jsonObject.put("content", new JSONObject().put("raw", text));
            jsonObject.put("pending", false);
        }
    }


    @Override
    public String createPullRequestCommentAndTaskIfNotExists(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException {
        List<ITask> tasks = pullRequestTasks(workspace, repository, pullRequestId);
        String result = IComment.Impl.checkCommentStartedWith(tasks, commentText);
        if (result != null) {
            return "";
        }
        return createPullRequestCommentAndTask(workspace, repository, pullRequestId, commentText, taskText);
    }

    @Override
    public String createPullRequestCommentAndTask(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException {
        List<ITask> tasks = pullRequestTasks(workspace, repository, pullRequestId);
        for (ITask task : tasks) {
            Task bitbucketTask = (Task)task;
            if (bitbucketTask.getText().equals(taskText) && (bitbucketTask.getState().equals("OPEN") || bitbucketTask.getState().equals("UNRESOLVED"))) {
                return "";
            }
        }
        String result = addPullRequestComment(workspace, repository, pullRequestId, commentText);
        return addTask(new JSONModel(result).getInt("id"), taskText);
    }

    @Override
    public String addPullRequestComment(String workspace, String repository, String pullRequestId, String text) throws IOException {
        GenericRequest getRequest = createPullRequestCommentsRequest(workspace, repository, pullRequestId);
        JSONObject jsonObject = createPullRequestCommentObject(text);
        getRequest.setBody(jsonObject.toString());
        clearCache(getRequest);
        return post(getRequest);
    }

    @NotNull
    private JSONObject createPullRequestCommentObject(String text) {
        if (apiVersion == ApiVersion.V1) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("text", text);
            return jsonObject;
        } else {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("content", new JSONObject().put("raw", text));
            return jsonObject;
        }
    }

    @Override
    public JSONModel commitComments(String workspace, String repository, String commitId) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path("projects/" + workspace + "/repos/" + repository + "/commits/" + commitId + "/comments"));
        String response = execute(getRequest);
        if (response == null) {
            return new JSONModel();
        }

        return new JSONModel(response);
    }

    @Override
    public String renamePullRequest(String workspace, String repository, IPullRequest pullRequest, String newTitle) throws IOException {
        String updatedTitleIfWip = IPullRequest.Utils.upgradeTitleIfWip(pullRequest, newTitle);

        if (pullRequest.getTitle().equalsIgnoreCase(updatedTitleIfWip)) {
            return "";
        }

        GenericRequest putRequest = new GenericRequest(this, path(buildPullRequestPath(workspace, repository, String.valueOf(pullRequest.getId()))));
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("title", updatedTitleIfWip);
//        jsonObject.put("version", pullRequest.getVersion());
//        jsonObject.put("reviewers", pullRequest.getJSONObject().optJSONArray("reviewers"));
        putRequest.setBody(jsonObject.toString());
        return put(putRequest);
    }

    @Override
    public List<ITag> getTags(String workspace, String repository) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, buildTagsPath(workspace, repository));
        getRequest.param(getPageLimitField(), defaultLimit);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(Tag.class, new BitbucketResult(response).getJSONObject().optJSONArray("values"));
    }

    private String buildTagsPath(String workspace, String repository) {
        if (apiVersion == ApiVersion.V1) {
            return path("projects/" + workspace + "/repos/" + repository + "/tags");
        } else {
            return path("repositories/" + workspace + "/" + repository + "/refs/tags");
        }
    }

    @Override
    public List<ITag> getBranches(String workspace, String repository) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path(buildGetBranchesPath(workspace, repository)));
        getRequest.param(getPageLimitField(), defaultLimit);
        getRequest.param("orderBy", "MODIFICATION");
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(Tag.class, new BitbucketResult(response).getJSONObject().optJSONArray("values"));
    }

    @NotNull
    private String buildGetBranchesPath(String workspace, String repository) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/" + repository + "/branches";
        } else {
            return "repositories/" + workspace + "/" + repository + "/refs/branches";
        }
    }

    @Override
    public List<ICommit> getCommitsBetween(String workspace, String repository, String from, String to) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path(buildPathGetCommitsBetween(workspace, repository, from, to)));
        getRequest.param(getPageLimitField(), String.valueOf(defaultLimit));
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(Commit.class, new BitbucketResult(response).getJSONObject().optJSONArray("values"));
    }

    @NotNull
    private String buildPathGetCommitsBetween(String workspace, String repository, String from, String to) {
        if (apiVersion == ApiVersion.V1) {
            return "projects/" + workspace + "/repos/" + repository + "/compare/commits?from=" + from + "&to=" + to;
        } else {
            return "repositories/" + workspace + "/" + repository + "/commits?include=" + from + "&exclude=" + to;
        }
    }

    @Override
    public List<ICommit> getCommitsFromBranch(String workspace, String repository, String branchName, String startDate, String endDate) throws IOException {
        List<ICommit> commits = new ArrayList<>();
        int start = getInitialStartValue();
        while (start <= 2000) {
            GenericRequest getRequest = new GenericRequest(this, buildCommitsPath(workspace, repository, branchName));
            getRequest.param(getNextPageField(), start);
            getRequest.param(getPageLimitField(), String.valueOf(defaultLimit));
            start = buildNexPage(start);
            String response = execute(getRequest);
            if (response == null) {
                return commits;
            }
            JSONArray values = new BitbucketResult(response).getJSONObject().optJSONArray("values");
            if (values.isEmpty()) {
                break;
            }
            commits.addAll(JSONModel.convertToModels(Commit.class, values));
        }

        return commits;
    }

    private String buildCommitsPath(String workspace, String repository, String branchName) {
        if (apiVersion == ApiVersion.V1) {
            return path("projects/" + workspace + "/repos/" + repository + "/commits?until=refs/heads/" + branchName + "&merges=include");
        } else {
            return path("repositories/" + workspace + "/" + repository + "/commits?include=" + branchName);
        }
    }

    @Override
    public void performCommitsFromBranch(String workspace, String repository, String branchName, Performer<ICommit> performer) throws Exception {
        int prevSize = 0;
        int currentSize = 0;
        int start = getInitialStartValue();
        boolean isFirst = true;
        while (isFirst || prevSize != currentSize) {
            isFirst = false;
            prevSize = currentSize;
            GenericRequest getRequest = new GenericRequest(this, buildCommitsPath(workspace, repository, branchName));
            getRequest.param(getNextPageField(), start);

            getRequest.param(getPageLimitField(), String.valueOf(defaultLimit));
            getRequest.setIgnoreCache(IGNORE_CACHE);
            start = buildNexPage(start);
            String response = execute(getRequest);
            if (response == null) {
                return;
            }

            List<Commit> values = JSONModel.convertToModels(Commit.class, new BitbucketResult(response).getJSONObject().optJSONArray("values"));
            for (Commit commit : values) {
                if (performer.perform(commit)) {
                    break;
                }
            }
            currentSize = currentSize + values.size();
        }
    }

    @Override
    public IDiffStats getCommitDiffStat(String workspace, String repository, String commitId) throws IOException {
        String path = "";

        if (apiVersion == ApiVersion.V1) {
            throw new UnsupportedOperationException();
        } else {
            path = path("repositories/" + workspace + "/" + repository + "/diffstat/" + commitId);
        }
        GenericRequest getRequest = new GenericRequest(this, path);
        try {
            String response = execute(getRequest);
            if (response == null) {
                return new IDiffStats.Empty();
            }
            List<IChange> changes = new BitbucketResult(response).getChanges();
            return new IDiffStats() {
                @Override
                public IStats getStats() {
                    return null;
                }

                @Override
                public List<IChange> getChanges() {
                    return changes;
                }
            };
        } catch (Exception e) {
            clearCache(getRequest);
            return new IDiffStats.Empty();
        }
    }

    @Override
    public IBody getCommitDiff(String workspace, String repository, String commitId) throws IOException {
        String path = "";

        if (apiVersion == ApiVersion.V1) {
            path = path("projects/" + workspace + "/repos/" + repository + "/commits/" + commitId + "/diff?AUTOSRCPATH&CONTEXTLINES&SINCE&SRCPATH&WHITESPACE&WITHCOMMENTS");
        } else {
            path = path("repositories/" + workspace + "/" + repository + "/diff/" + commitId);
        }
        GenericRequest getRequest = new GenericRequest(this, path);
        try {
            String response = execute(getRequest);
            if (response == null) {
                return () -> "";
            }
            return () -> response;
        } catch (Exception e) {
            clearCache(getRequest);
            return () -> "";
        }
    }

    @Override
    public IBody getDiff(String workspace, String repository, String pullRequestId) throws IOException {
        String path = "";
        if (apiVersion == ApiVersion.V1) {
            path = path("repositories/" + workspace + "/" + repository + "/pullrequests/" + pullRequestId + "/diff");
        } else {
            throw new UnsupportedOperationException();
        }
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        return () -> response;
    }

    @Override
    public List<IFile> getListOfFiles(String workspace, String repository, String branchName)  throws IOException {
        GenericRequest getRequest = new GenericRequest(this, path("repositories/" + workspace + "/" + repository + "/src/" + branchName +"/?max_depth=50"));
//        getRequest.param(getNextPageField(), 1);
        getRequest.param(getPageLimitField(), 100);
        BitbucketResult bitbucketResult = new BitbucketResult(execute(getRequest));
        List<IFile> result = bitbucketResult.getFiles();
        while (bitbucketResult.getNext() != null) {
            getRequest = new GenericRequest(this, bitbucketResult.getNext());
            bitbucketResult = new BitbucketResult(execute(getRequest));
            result.addAll(bitbucketResult.getFiles());
        }
        return result;
    }

    @Override
    public String getFileContent(String selfLink) throws IOException {
        GenericRequest request = new GenericRequest(this, selfLink);
        return request.execute();
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

    @Override
    public String getPullRequestUrl(String workspace, String repository, String id) {
        return getBasePath().replaceAll("api.", "") + "/" + workspace + "/" + repository + "/pull-requests/" + id;
    }

    @Override
    public List<IFile> searchFiles(String workspace, String repository, String query, int filesLimit) throws IOException {
        throw new UnsupportedOperationException("Implement Me.");
    }
}

