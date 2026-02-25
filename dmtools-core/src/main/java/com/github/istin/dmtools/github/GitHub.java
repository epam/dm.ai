package com.github.istin.dmtools.github;

import com.github.istin.dmtools.atlassian.bitbucket.model.Commit;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.IOUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.github.model.*;
import com.github.istin.dmtools.job.JobRunner;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class GitHub extends AbstractRestClient implements SourceCode, UriToObject {
    private static final Logger logger = LogManager.getLogger(GitHub.class);
    private static final String API_VERSION = "v3";
    private static final boolean IS_READ_PULL_REQUEST_DIFF;

    static {
        PropertyReader propertyReader = new PropertyReader();
        IS_READ_PULL_REQUEST_DIFF = propertyReader.isReadPullRequestDiff();
    }

    public GitHub(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    /**
     * Gets the authorization token for API access.
     * Used by GitHubWorkflowUtils for native HTTP client requests.
     * 
     * @return the authorization token
     */
    public String getAuthorization() {
        return authorization;
    }

    /**
     * Tests the GitHub connection and returns detailed information about the result.
     * Used by IntegrationService to validate GitHub configuration.
     * 
     * @return Map containing test result details
     */
    public Map<String, Object> testConnectionDetailed() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String path = path("user");
            GenericRequest getRequest = new GenericRequest(this, path);
            String response = execute(getRequest);
            
            if (response != null && !response.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(response);
                
                if (jsonResponse.has("login") || jsonResponse.has("id")) {
                    result.put("success", true);
                    result.put("message", "GitHub API connection successful");
                    result.put("user", jsonResponse.optString("login", "unknown"));
                    result.put("userId", jsonResponse.optString("id", "unknown"));
                } else {
                    result.put("success", false);
                    result.put("message", "Unexpected response format from GitHub API");
                }
            } else {
                result.put("success", false);
                result.put("message", "Empty response from GitHub API");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "GitHub API connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            logger.warn("GitHub connection test failed", e);
        }
        
        return result;
    }

    @Override
    public String path(String path) {
        return getBasePath() + "/" + path;
    }

    @Override
    public synchronized Request.Builder sign(Request.Builder builder) {
        return builder
                .header("Authorization", "Bearer " + authorization)
                .header("Accept","application/vnd.github.v3+json")
                .header("Content-Type", "application/json");
    }

    @Override
    public List<IPullRequest> pullRequests(
            String workspace,
            String repository,
            String state,
            boolean checkAllRequests,
            Calendar startDate) throws IOException {
        boolean isMerged = state.equalsIgnoreCase(IPullRequest.PullRequestState.STATE_MERGED);
        boolean isDeclined = state.equalsIgnoreCase(IPullRequest.PullRequestState.STATE_DECLINED);
        if (isMerged || isDeclined) {
            state = "closed";
        } else if (state.equalsIgnoreCase(IPullRequest.PullRequestState.STATE_OPEN)) {
            state = "open";
        }
        List<IPullRequest> allPullRequests = new ArrayList<>();
        int perPage = 100; // Maximum allowed by GitHub
        int currentPage = 1;

        while (true) {
            String path = path(String.format("repos/%s/%s/pulls?state=%s&per_page=%d&page=%d",
                    workspace, repository, state, perPage, currentPage));
            GenericRequest getRequest = new GenericRequest(this, path);
            String response = execute(getRequest);

            if (response == null || response.isEmpty()) {
                break;
            }

            JSONArray pullRequestsInResponse = new JSONArray(response);
            List<GitHubPullRequest> pullRequests = JSONModel.convertToModels(GitHubPullRequest.class, pullRequestsInResponse);
            if (isMerged) {
                pullRequests = pullRequests.stream()
                        .filter(GitHubPullRequest::isMerged)
                        .collect(Collectors.toList());
            } else if (isDeclined) {
                pullRequests = pullRequests.stream()
                        .filter(pr -> !pr.isMerged())
                        .collect(Collectors.toList());
            }
            allPullRequests.addAll(pullRequests);

            if (startDate != null && !pullRequests.isEmpty() && pullRequests.getLast().getCreatedDate() < startDate.getTimeInMillis()) {
                break;
            }

            if (!checkAllRequests || pullRequestsInResponse.length() < perPage) {
                break;
            }

            currentPage++;
        }

        return allPullRequests;
    }

    @MCPTool(
            name = "github_list_prs",
            description = "List pull requests in a GitHub repository by state. State can be 'open', 'closed', or 'merged'. Returns first page (up to 100) of pull requests.",
            integration = "github",
            category = "pull_requests"
    )
    public List<IPullRequest> listPullRequests(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "state", description = "The state of pull requests to list: 'open', 'closed', or 'merged'", required = true, example = "open")
            String state) throws IOException {
        return pullRequests(workspace, repository, state, false, null);
    }

    @Override
    @MCPTool(
            name = "github_get_pr",
            description = "Get details of a GitHub pull request including title, description, status, author, branches, and merge info.",
            integration = "github",
            category = "pull_requests"
    )
    public IPullRequest pullRequest(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId) throws IOException {
        String response = getPullRequestResponse(workspace, repository, pullRequestId, false);
        return new GitHubPullRequest(response);
    }

    public String triggerAction(String workspace, String repository, JSONObject params) throws IOException {
        String path = path(String.format("repos/%s/%s/dispatches", workspace, repository));
        GenericRequest postRequest = new GenericRequest(this, path);
        postRequest.setBody(params.toString());
        return post(postRequest);
    }

    private String getPullRequestResponse(String workspace, String repository, String pullRequestId, boolean isDiff) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        if (isDiff) {
            addDiffHeader(getRequest);
        }
        return execute(getRequest);
    }

    private static void addDiffHeader(GenericRequest getRequest) {
        getRequest.header("Accept", "application/vnd.github.diff");
    }

    @Override
    @MCPTool(
            name = "github_get_pr_comments",
            description = "Get all comments for a GitHub pull request, including both inline code review comments and general discussion comments. Results are sorted by creation date.",
            integration = "github",
            category = "pull_requests"
    )
    public List<IComment> pullRequestComments(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s/comments", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        List<IComment> result = new ArrayList<>();
        if (response != null) {
            List<IComment> comments = JSONModel.convertToModels(GitHubComment.class, new JSONArray(response));
            result.addAll(comments);
        }
        result.addAll(pullRequestCommentsFromIssue(workspace, repository, pullRequestId));
        result.sort((c1, c2) -> c1.getCreated().compareTo(c2.getCreated()));
        return result;
    }

    public List<IComment> pullRequestCommentsFromIssue(String workspace, String repository, String pullRequestId) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%s/comments", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return new ArrayList<>();
        }
        return JSONModel.convertToModels(GitHubComment.class, new JSONArray(response));
    }

    @MCPTool(
            name = "github_get_pr_conversations",
            description = "Get all review conversations (inline code comment threads) for a GitHub pull request. Groups inline code review comments into threads showing root comment and replies. Also includes general PR discussion comments as separate entries.",
            integration = "github",
            category = "pull_requests"
    )
    public List<GitHubConversation> getPRConversations(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s/comments", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);

        List<GitHubConversation> conversations = new ArrayList<>();
        List<GitHubComment> inlineComments = new ArrayList<>();
        if (response != null) {
            inlineComments = JSONModel.convertToModels(GitHubComment.class, new JSONArray(response));
        }

        Map<Long, GitHubConversation> threadMap = new LinkedHashMap<>();
        for (GitHubComment comment : inlineComments) {
            Long replyToId = comment.getInReplyToId();
            if (replyToId == null) {
                GitHubConversation conversation = new GitHubConversation(comment);
                threadMap.put(Long.parseLong(comment.getId()), conversation);
                conversations.add(conversation);
            } else {
                GitHubConversation parentConversation = threadMap.get(replyToId);
                if (parentConversation != null) {
                    parentConversation.addReply(comment);
                } else {
                    GitHubConversation orphanConversation = new GitHubConversation(comment);
                    threadMap.put(Long.parseLong(comment.getId()), orphanConversation);
                    conversations.add(orphanConversation);
                }
            }
        }

        List<IComment> issueComments = pullRequestCommentsFromIssue(workspace, repository, pullRequestId);
        for (IComment issueComment : issueComments) {
            if (issueComment instanceof GitHubComment) {
                conversations.add(new GitHubConversation((GitHubComment) issueComment));
            }
        }

        return conversations;
    }

    @Override
    @MCPTool(
            name = "github_get_pr_activities",
            description = "Get all activities for a GitHub pull request including reviews (approvals, change requests), inline code comments, and general discussion comments.",
            integration = "github",
            category = "pull_requests"
    )
    public List<IActivity> pullRequestActivities(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId) throws IOException {
        // Fetch review-level activities (approvals, review comments, change requests)
        String reviewsPath = path(String.format("repos/%s/%s/pulls/%s/reviews", workspace, repository, pullRequestId));
        GenericRequest reviewsRequest = new GenericRequest(this, reviewsPath);
        String reviewsResponse = execute(reviewsRequest);
        List<IActivity> activities = new ArrayList<>();
        if (reviewsResponse != null) {
            activities.addAll(JSONModel.convertToModels(GitHubActivity.class, new JSONArray(reviewsResponse)));
        }

        // Also fetch inline review comments (/pulls/{id}/comments)
        // These are code-level comments left during reviews
        try {
            String commentsPath = path(String.format("repos/%s/%s/pulls/%s/comments", workspace, repository, pullRequestId));
            GenericRequest commentsRequest = new GenericRequest(this, commentsPath);
            String commentsResponse = execute(commentsRequest);
            if (commentsResponse != null) {
                List<GitHubComment> inlineComments = JSONModel.convertToModels(GitHubComment.class, new JSONArray(commentsResponse));
                for (GitHubComment comment : inlineComments) {
                    activities.add(new GitHubCommentActivity(comment));
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch inline PR comments for PR {}: {}", pullRequestId, e.getMessage());
        }

        // Also fetch issue-level comments (/issues/{id}/comments)
        // These are general conversation comments on the PR
        try {
            List<IComment> issueComments = pullRequestCommentsFromIssue(workspace, repository, pullRequestId);
            for (IComment comment : issueComments) {
                if (comment instanceof GitHubComment) {
                    activities.add(new GitHubCommentActivity((GitHubComment) comment));
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch issue-level PR comments for PR {}: {}", pullRequestId, e.getMessage());
        }

        return activities;
    }

    @Override
    public List<ITask> pullRequestTasks(String workspace, String repository, String pullRequestId) throws IOException {
        List<IActivity> activities = pullRequestActivities(workspace, repository, pullRequestId);
        List<ITask> tasks = new ArrayList<>();
        for (IActivity activity : activities) {
            if (activity.getAction().equalsIgnoreCase(GitHubActivity.State.CHANGES_REQUESTED.name())) {
                tasks.add(new GitHubTask(((GitHubActivity)activity).getJSONObject()));
            }
        }
        return tasks;
    }

    @Override
    public String addTask(Integer commentId, String text) throws IOException {
        // GitHub does not have a direct equivalent for adding tasks to comments
        throw new UnsupportedOperationException("GitHub does not support adding tasks to comments.");
    }

    @Override
    public String createPullRequestCommentAndTaskIfNotExists(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException {
        // GitHub does not have a direct equivalent for creating comments and tasks
        throw new UnsupportedOperationException("GitHub does not support creating comments and tasks.");
    }

    @Override
    public String createPullRequestCommentAndTask(String workspace, String repository, String pullRequestId, String commentText, String taskText) throws IOException {
        // GitHub does not have a direct equivalent for creating comments and tasks
        throw new UnsupportedOperationException("GitHub does not support creating comments and tasks.");
    }

    @Override
    @MCPTool(
            name = "github_add_pr_comment",
            description = "Add a comment to a GitHub pull request discussion.",
            integration = "github",
            category = "pull_requests"
    )
    public String addPullRequestComment(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId,
            @MCPParam(name = "text", description = "The comment text to add", required = true, example = "Looks good!")
            String text) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%s/comments", workspace, repository, pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", text);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_reply_to_pr_thread",
            description = "Reply to an existing inline code review comment thread in a GitHub pull request. Use the comment ID of the root comment (or any comment) in the thread as inReplyToId.",
            integration = "github",
            category = "pull_requests"
    )
    public String replyToPullRequestComment(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId,
            @MCPParam(name = "inReplyToId", description = "The ID of the comment to reply to (from github_get_pr_conversations rootComment.id or replies[].id)", required = true, example = "123456789")
            String inReplyToId,
            @MCPParam(name = "text", description = "The reply text (Markdown supported)", required = true, example = "Fixed in the latest commit.")
            String text) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s/comments", workspace, repository, pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("body", text);
        body.put("in_reply_to", Long.parseLong(inReplyToId));
        postRequest.setBody(body.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_add_inline_comment",
            description = "Create a new inline code review comment on a specific file and line in a GitHub pull request. To comment on a range of lines, provide both startLine and line. Side is 'RIGHT' for new code (default) or 'LEFT' for old code.",
            integration = "github",
            category = "pull_requests"
    )
    public String addInlineReviewComment(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId,
            @MCPParam(name = "path", description = "The relative file path in the repository", required = true, example = "src/main/java/com/example/Foo.java")
            String filePath,
            @MCPParam(name = "line", description = "The line number in the file to comment on", required = true, example = "42")
            String line,
            @MCPParam(name = "text", description = "The comment text (Markdown supported)", required = true, example = "This should be refactored.")
            String text,
            @MCPParam(name = "commitId", description = "The SHA of the commit to comment on. If empty, uses the PR head commit.", required = false, example = "abc123def456")
            String commitId,
            @MCPParam(name = "startLine", description = "For multi-line comments: the first line of the range. Must be less than line.", required = false, example = "40")
            String startLine,
            @MCPParam(name = "side", description = "Which diff side to comment on: RIGHT (new code, default) or LEFT (old code)", required = false, example = "RIGHT")
            String side) throws IOException {
        String resolvedCommitId = commitId;
        if (resolvedCommitId == null || resolvedCommitId.trim().isEmpty()) {
            String prResponse = getPullRequestResponse(workspace, repository, pullRequestId, false);
            JSONObject prJson = new JSONObject(prResponse);
            resolvedCommitId = prJson.getJSONObject("head").getString("sha");
        }
        String path = path(String.format("repos/%s/%s/pulls/%s/comments", workspace, repository, pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject body = new JSONObject();
        body.put("body", text);
        body.put("commit_id", resolvedCommitId);
        body.put("path", filePath);
        body.put("line", Integer.parseInt(line));
        body.put("side", (side != null && !side.trim().isEmpty()) ? side.toUpperCase() : "RIGHT");
        if (startLine != null && !startLine.trim().isEmpty()) {
            body.put("start_line", Integer.parseInt(startLine));
            body.put("start_side", (side != null && !side.trim().isEmpty()) ? side.toUpperCase() : "RIGHT");
        }
        postRequest.setBody(body.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_get_pr_review_threads",
            description = "Get all review threads for a GitHub pull request via GraphQL, including each thread's node ID (needed for resolving), resolved status, file path, line, and comments. Use the returned thread 'id' with github_resolve_pr_thread.",
            integration = "github",
            category = "pull_requests"
    )
    public String getPullRequestReviewThreads(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId) throws IOException {
        String query = "query($owner: String!, $repo: String!, $prNumber: Int!) { " +
                "repository(owner: $owner, name: $repo) { " +
                "pullRequest(number: $prNumber) { " +
                "reviewThreads(first: 100) { " +
                "nodes { " +
                "id isResolved path line startLine " +
                "comments(first: 50) { " +
                "nodes { databaseId body author { login } createdAt } " +
                "} } } } } }";
        JSONObject variables = new JSONObject();
        variables.put("owner", workspace);
        variables.put("repo", repository);
        variables.put("prNumber", Integer.parseInt(pullRequestId));
        return executeGraphQL(query, variables);
    }

    @MCPTool(
            name = "github_resolve_pr_thread",
            description = "Resolve a review thread in a GitHub pull request. Requires the thread's GraphQL node ID, which can be obtained from github_get_pr_review_threads (the 'id' field of each thread).",
            integration = "github",
            category = "pull_requests"
    )
    public String resolveReviewThread(
            @MCPParam(name = "threadId", description = "The GraphQL node ID of the review thread to resolve (from github_get_pr_review_threads thread.id)", required = true, example = "PRRT_kwDOBQfyNc5A...")
            String threadId) throws IOException {
        String mutation = String.format(
                "mutation { resolveReviewThread(input: { threadId: \"%s\" }) { thread { id isResolved } } }",
                threadId);
        return executeGraphQL(mutation, null);
    }

    private String executeGraphQL(String query, JSONObject variables) throws IOException {
        String graphqlPath = path("graphql");
        GenericRequest postRequest = new GenericRequest(this, graphqlPath);
        JSONObject requestBody = new JSONObject();
        requestBody.put("query", query);
        if (variables != null) {
            requestBody.put("variables", variables);
        }
        postRequest.setBody(requestBody.toString());
        return post(postRequest);
    }

    @Override
    public JSONModel commitComments(String workspace, String repository, String commitId) throws IOException {
        String path = path(String.format("repos/%s/%s/commits/%s/comments", workspace, repository, commitId));
        GenericRequest getRequest = new GenericRequest(this, path);
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

        String path = path(String.format("repos/%s/%s/pulls/%s", workspace, repository, pullRequest.getId()));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", updatedTitleIfWip);
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @Override
    public List<ITag> getTags(String workspace, String repository) throws IOException {
        String path = path(String.format("repos/%s/%s/tags", workspace, repository));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GithubTag.class, new JSONArray(response));
    }

    @Override
    @MCPTool(
            name = "github_add_pr_label",
            description = "Add a label to a GitHub pull request.",
            integration = "github",
            category = "pull_requests"
    )
    public void addPullRequestLabel(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request number", required = true, example = "74")
            String pullRequestId,
            @MCPParam(name = "label", description = "The label name to add to the pull request", required = true, example = "bug")
            String label) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%s/labels", workspace, repository, pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONArray labelsArray = new JSONArray();
        labelsArray.put(label);
        postRequest.setBody(labelsArray.toString());
        post(postRequest);
    }

    @Override
    public List<IRepository> getRepositories(String namespace) throws IOException {
        throw new UnsupportedOperationException("implement me");
    }

    @Override
    public List<ITag> getBranches(String workspace, String repository) throws IOException {
        String path = path(String.format("repos/%s/%s/branches", workspace, repository));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GithubTag.class, new JSONArray(response));
    }

    @Override
    public List<ICommit> getCommitsBetween(String workspace, String repository, String from, String to) throws IOException {
        String path = path(String.format("repos/%s/%s/compare/%s...%s", workspace, repository, from, to));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(Commit.class, new JSONObject(response).getJSONArray("commits"));
    }

    @Override
    public List<ICommit> getCommitsFromBranch(String workspace, String repository, String branchName, String startDate, String endDate) throws IOException {
        List<ICommit> allCommits = new ArrayList<>();
        int page = 1;
        boolean hasMoreData = true;
        int perPage = 100; // Set a default value for perPage

        while (hasMoreData) {
            // Build the URL with optional date filtering and pagination
            StringBuilder path = new StringBuilder(String.format("repos/%s/%s/commits?sha=%s", workspace, repository, branchName));

            if (startDate != null && !startDate.isEmpty()) {
                path.append("&since=").append(startDate).append("T00:00:00Z");
            }
            if (endDate != null && !endDate.isEmpty()) {
                path.append("&until=").append(endDate).append("T23:59:59Z");
            }

            // Add pagination parameters
            path.append("&page=").append(page)
                    .append("&per_page=").append(perPage);

            GenericRequest getRequest = new GenericRequest(this, path(path.toString()));
            String response = execute(getRequest);

            if (response == null || response.isEmpty()) {
                break;
            }

            List<ICommit> commits = JSONModel.convertToModels(GitHubCommit.class, new JSONArray(response));

            if (commits.isEmpty()) {
                hasMoreData = false;
            } else {
                allCommits.addAll(commits);
                page++;
            }
        }

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
    public IBody getCommitDiff(String workspace, String repository, String commitId) throws IOException {
        String response = getCommitAsDiff(workspace, repository, commitId);
        return () -> response;
    }

    @Override
    @MCPTool(
            name = "github_get_pr_diff",
            description = "Get the diff statistics for a GitHub pull request (files changed, additions, deletions). Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled.",
            integration = "github",
            category = "pull_requests"
    )
    public IDiffStats getPullRequestDiff(
            @MCPParam(name = "workspace", description = "The GitHub owner/organization name", required = true, example = "IstiN")
            String workspace,
            @MCPParam(name = "repository", description = "The GitHub repository name", required = true, example = "dmtools")
            String repository,
            @MCPParam(name = "pullRequestID", description = "The pull request number", required = true, example = "74")
            String pullRequestID) throws IOException {
        if (!IS_READ_PULL_REQUEST_DIFF) {
            return new IDiffStats.Empty();
        }
        try {
            String pullRequestResponse = getPullRequestResponse(workspace, repository, pullRequestID, true);
            return parseDiffStats(pullRequestResponse);
        } catch (AtlassianRestClient.RestClientException e) {
            e.printStackTrace();
            return new IDiffStats.Empty();
        }
    }

    public static IDiffStats parseDiffStats(String diff) {
        int addedLines = 0;
        int removedLines = 0;

        String[] lines = diff.split("\n");

        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                addedLines++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removedLines++;
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
                return Collections.emptyList();
            }
        };
    }

    private GitHubCommit getCommitAsObject(String workspace, String repository, String commitId) throws IOException {
        String response = getCommit(workspace, repository, commitId, false);
        return new GitHubCommit(response);
    }

    private String getCommitAsDiff(String workspace, String repository, String commitId) throws IOException {
        return getCommit(workspace, repository, commitId, true);
    }

    private String getCommit(String workspace, String repository, String commitId, boolean isDiff) throws IOException {
        String path = path(String.format("repos/%s/%s/commits/%s", workspace, repository, commitId));
        GenericRequest getRequest = new GenericRequest(this, path);
        if (isDiff) {
            addDiffHeader(getRequest);
        }
        return execute(getRequest);
    }

    @Override
    public IBody getDiff(String workspace, String repository, String pullRequestId) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s/files", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        return new IBody() {
            @Override
            public String getBody() {
                return response;
            }
        };
    }

    public String getSingleFileContent(String workspace, String repository, String branchName, String filePath) throws IOException {
        String path = path(String.format("repos/%s/%s/contents/%s", workspace, repository, filePath));
        GenericRequest getRequest = new GenericRequest(this, path);

        // Add a query parameter for the branch
        getRequest.param("ref", branchName);

        String response = execute(getRequest);
        if (response == null) {
            throw new FileNotFoundException("File not found: " + filePath);
        }

        JSONObject jsonResponse = new JSONObject(response);
        String content = jsonResponse.optString("content", "");
        String encoding = jsonResponse.optString("encoding", "");

        if ("base64".equalsIgnoreCase(encoding)) {
            return JobRunner.decodeBase64(content.replaceAll("\\r\\n|\\r|\\n", ""));
        } else {
            // If it's not base64 encoded, return the content as is
            return content;
        }
    }

    @Override
    public List<IFile> getListOfFiles(String workspace, String repository, String branchName) throws IOException {
        String path = path(String.format("repos/%s/%s/git/trees/%s?recursive=1", workspace, repository, branchName));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return Collections.emptyList();
        }
        return JSONModel.convertToModels(GitHubFile.class, new JSONObject(response).getJSONArray("tree"));
    }

    /**
     * Gets file content from either a GitHub API blob URL, a standard GitHub web URL,
     * or a raw.githubusercontent.com URL.
     *
     * @param fileUrl The URL to the file
     * @return The content of the file
     * @throws IOException If there's an error fetching the file
     */
    @Override
    public String getFileContent(String fileUrl) throws IOException {
        if (fileUrl.startsWith("https://raw.githubusercontent.com/")) {
            // Handle raw GitHub URL: https://raw.githubusercontent.com/owner/repo/branch/path
            return getFileContentFromRawUrl(fileUrl);
        } else if (fileUrl.contains("github.com/") && fileUrl.contains("/blob/")) {
            // Handle standard GitHub web URL: https://github.com/owner/repo/blob/branch/path
            return getFileContentFromGithubWebUrl(fileUrl);
        } else {
            // Handle GitHub API blob URL
            return getFileContentFromApiUrl(fileUrl);
        }
    }

    /**
     * Gets file content from a GitHub API blob URL.
     *
     * @param apiUrl The GitHub API URL to the blob
     * @return The content of the file
     * @throws IOException If there's an error fetching the file
     */
    private String getFileContentFromApiUrl(String apiUrl) throws IOException {
        GenericRequest getRequest = new GenericRequest(this, apiUrl);
        String response = execute(getRequest);
        String content = new JSONModel(response).getString("content").replaceAll("\\r\\n|\\r|\\n", "");
        return JobRunner.decodeBase64(content);
    }

    /**
     * Gets file content from a standard GitHub web URL.
     * Converts URLs like https://github.com/{owner}/{repo}/blob/{branch}/{path}
     * to the corresponding API URL format.
     *
     * @param githubWebUrl The GitHub web URL of the file
     * @return The content of the file
     * @throws IOException If there's an error fetching the file
     */
    private String getFileContentFromGithubWebUrl(String githubWebUrl) throws IOException {
        // Parse the URL
        String[] parts = githubWebUrl.replace("https://github.com/", "").split("/blob/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Could not parse GitHub URL: " + githubWebUrl);
        }

        String ownerRepo = parts[0]; // owner/repo
        String[] branchAndPath = parts[1].split("/", 2);
        if (branchAndPath.length != 2) {
            throw new IllegalArgumentException("Could not extract branch and path from URL: " + githubWebUrl);
        }

        String branch = branchAndPath[0];
        String path = branchAndPath[1];

        // First get the file metadata to get the SHA
        String contentsPath = String.format("repos/%s/contents/%s", ownerRepo, path);
        GenericRequest metadataRequest = new GenericRequest(this, path(contentsPath));
        metadataRequest.param("ref", branch);

        String metadataResponse = execute(metadataRequest);
        String sha = new JSONModel(metadataResponse).getString("sha");

        // Now get the blob using the SHA
        String blobPath = String.format("repos/%s/git/blobs/%s", ownerRepo, sha);

        // Use the API URL method to get the content
        return getFileContentFromApiUrl(path(blobPath));
    }

    /**
     * Gets file content from a raw.githubusercontent.com URL.
     * Converts URLs like https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
     * to the corresponding API URL format.
     *
     * @param rawUrl The raw GitHub content URL
     * @return The content of the file
     * @throws IOException If there's an error fetching the file
     */
    private String getFileContentFromRawUrl(String rawUrl) throws IOException {
        // Parse: https://raw.githubusercontent.com/owner/repo/branch/path/to/file.md
        String withoutPrefix = rawUrl.replace("https://raw.githubusercontent.com/", "");
        String[] parts = withoutPrefix.split("/", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Could not parse raw GitHub URL: " + rawUrl);
        }

        String ownerRepo = parts[0] + "/" + parts[1];
        String[] branchAndPath = parts[2].split("/", 2);
        if (branchAndPath.length < 2) {
            throw new IllegalArgumentException("Could not extract branch and path from raw GitHub URL: " + rawUrl);
        }

        String branch = branchAndPath[0];
        String filePath = branchAndPath[1];

        // Get file metadata to obtain the SHA
        String contentsPath = String.format("repos/%s/contents/%s", ownerRepo, filePath);
        GenericRequest metadataRequest = new GenericRequest(this, path(contentsPath));
        metadataRequest.param("ref", branch);

        String metadataResponse = execute(metadataRequest);
        String sha = new JSONModel(metadataResponse).getString("sha");

        // Get the blob content via SHA
        String blobPath = String.format("repos/%s/git/blobs/%s", ownerRepo, sha);
        return getFileContentFromApiUrl(path(blobPath));
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
        return getBasePath().replaceAll("api.", "") + "/" + workspace + "/" + repository + "/pull/" + id;
    }

    @Override
    public List<IFile> searchFiles(String workspace, String repository, String query, int filesLimit) throws IOException, InterruptedException {
        List<IFile> allFiles = new ArrayList<>();
        int perPage = 100; // GitHub's max items per page
        int page = 1;
        int maxResults = filesLimit == -1 ? 1000 : filesLimit; // Use 1000 for unlimited, otherwise use filesLimit

        while (true) {
            // Break if we've reached the desired limit (except for unlimited case)
            if (filesLimit != -1 && allFiles.size() >= filesLimit) {
                break;
            }

            // Break if we've reached the max results for unlimited case
            if (filesLimit == -1 && allFiles.size() >= maxResults) {
                break;
            }

            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String path = path(String.format("search/code?q=%s+repo:%s/%s&per_page=%d&page=%d",
                    encodedQuery, workspace, repository, perPage, page));

            GenericRequest getRequest = new GenericRequest(this, path);
            getRequest.header("Accept", "application/vnd.github.v3.text-match+json");
            String response = null;

            try {
                response = execute(getRequest);
            } catch (RateLimitException rateLimitException) {
                Response errorResponse = rateLimitException.getResponse();
                String resetTimeStr = errorResponse.header("X-RateLimit-Reset");

                if (resetTimeStr != null) {
                    long resetTime = Long.parseLong(resetTimeStr) * 1000; // Convert seconds to milliseconds
                    long currentTime = System.currentTimeMillis();
                    long waitTime = resetTime - currentTime;

                    if (waitTime > 0) {
                        logger.warn("Rate limit reached. Waiting for " + (waitTime / 1000) + " seconds.");
                        Thread.sleep(waitTime + 1000);
                    }
                } else {
                    logger.warn("Rate limit reached. Default waiting for 60 seconds.");
                    Thread.sleep(60000);
                }
                response = execute(getRequest);
            }

            if (response == null) {
                break;
            }

            JSONObject jsonResponse = new JSONObject(response);
            JSONArray items = jsonResponse.getJSONArray("items");

            if (items.length() == 0) {
                break;
            }

            List<IFile> pageFiles = JSONModel.convertToModels(GitHubFile.class, items);

            // Add only up to the limit if specified
            if (filesLimit != -1) {
                int remainingSpace = filesLimit - allFiles.size();
                if (pageFiles.size() > remainingSpace) {
                    allFiles.addAll(pageFiles.subList(0, remainingSpace));
                    break;
                }
            }

            allFiles.addAll(pageFiles);

            // Break if we got fewer items than requested per page
            if (items.length() < perPage) {
                break;
            }

            page++;
        }

        // Final check to ensure we don't exceed the limit
        if (filesLimit != -1 && allFiles.size() > filesLimit) {
            return allFiles.subList(0, filesLimit);
        }

        return allFiles;
    }

    private boolean isRateLimited(JSONObject response) {
        if (response.has("rate")) {
            JSONObject rate = response.getJSONObject("rate");
            int remaining = rate.getInt("remaining");
            return remaining == 0;
        }
        return false;
    }

    @Override
    public Set<String> parseUris(String object) throws Exception {
        if (object == null || object.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();

        // Pattern to match GitHub URLs in the format:
        // https://github.com/{owner}/{repo}/blob/{branch}/{path}
        String pattern = "https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+?)(?=[\\s\"'<>]|$)";

        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(object);

        while (m.find()) {
            String url = m.group(0);
            // Make sure we don't include trailing characters that might have been matched
            url = url.trim();
            result.add(url);
        }

        return result;
    }

    @Override
    public Object uriToObject(String uri) throws Exception {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        // Check if the URI is a GitHub file URL
        if (uri.startsWith("https://github.com/") && uri.contains("/blob/")) {
            try {
                // Use the existing method to get file content
                return getFileContent(uri);
            } catch (IOException e) {
                logger.error("Failed to fetch content from GitHub URL: " + uri, e);
                throw e;
            }
        }

        // Return null if the URI is not recognized
        return null;
    }









    @Override
    public String callHookAndWaitResponse(String hookUrl, String requestParams) throws Exception {
        try {
            logger.info("Triggering GitHub workflow for URL: {}", hookUrl);
            
            String owner, repo, workflowId;
            
            // Handle both GitHub web URLs and API URLs
            if (hookUrl.contains("github.com/") && hookUrl.contains("/actions/workflows/")) {
                // GitHub web URL format: https://github.com/{owner}/{repo}/actions/workflows/{workflow_file}
                String[] parts = hookUrl.replace("https://github.com/", "").split("/");
                if (parts.length >= 4 && "actions".equals(parts[2]) && "workflows".equals(parts[3])) {
                    owner = parts[0];
                    repo = parts[1];
                    workflowId = parts[4]; // This could be filename.yml or workflow ID
                    logger.info("Parsed GitHub web URL - owner: {}, repo: {}, workflow: {}", owner, repo, workflowId);
                } else {
                    logger.error("Invalid GitHub web URL format: {}", hookUrl);
                    return "Error: Invalid GitHub web URL format. Expected: https://github.com/{owner}/{repo}/actions/workflows/{workflow_file}";
                }
            } else if (hookUrl.contains("/actions/workflows/") && hookUrl.endsWith("/dispatches")) {
                // API URL format: https://api.github.com/repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches
                String[] urlParts = hookUrl.split("/");
                if (urlParts.length < 7) {
                    logger.error("Cannot parse GitHub API URL: {}", hookUrl);
                    return "Error: Cannot parse GitHub API workflow URL";
                }
                
                owner = urlParts[urlParts.length - 6]; // repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches
                repo = urlParts[urlParts.length - 5];
                workflowId = urlParts[urlParts.length - 2];
                logger.info("Parsed GitHub API URL - owner: {}, repo: {}, workflow: {}", owner, repo, workflowId);
            } else {
                logger.error("Unsupported GitHub URL format: {}", hookUrl);
                return "Error: Unsupported GitHub URL format. Expected either GitHub web URL or API dispatch URL";
            }

            // 1. Record trigger timestamp before triggering the workflow
            long triggerTimestamp = System.currentTimeMillis();
            logger.info("Recording webhook trigger timestamp: {}", triggerTimestamp);

            // 2. Trigger the workflow using enhanced method
            logger.info("Triggering workflow {}/{}/{}", owner, repo, workflowId);
            GitHubWorkflowUtils.triggerWorkflow(this, owner, repo, workflowId, requestParams);
            Thread.sleep(2000L);
            // 3. Wait and retry to find the triggered workflow run
            logger.info("Waiting for workflow run to appear in GitHub API...");
            Long runId = waitAndFindWorkflowRun(owner, repo, workflowId, triggerTimestamp);

            if (runId != null) {
                logger.info("Found workflow run ID: {}", runId);

                // 5. Wait for workflow completion
                logger.info("Waiting for workflow to complete...");
                boolean completed = waitForWorkflowCompletion(owner, repo, runId);

                if (completed) {
                    // 6. Get workflow summary
                    String summary = getWorkflowSummary(owner, repo, runId);
                    logger.info("Workflow completed. Summary: {}", summary);
                    return summary;
                } else {
                    logger.warn("Workflow did not complete within timeout");
                    return "Workflow timeout - did not complete within expected time";
                }
            } else {
                logger.error("Could not find workflow run triggered by this webhook. No new workflow was triggered.");
                return "Error: No new workflow was triggered by this webhook call. This may indicate a webhook configuration issue or the workflow dispatch failed.";
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            logger.error("Workflow processing was interrupted for URL: {}", hookUrl);
            return "Error: Workflow processing was interrupted";
        } catch (Exception e) {
            // If hook fails, log the error but don't fail the whole workflow
            logger.error("Hook call failed for URL: {}, error: {}", hookUrl, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    





    
    /**
     * Provides detailed error information for workflow trigger failures.
     * 
     * @param error The exception that occurred
     * @param owner Repository owner
     * @param repo Repository name  
     * @param workflowId Workflow ID or filename
     * @return Detailed error information and possible solutions
     */
    private String getWorkflowTriggerErrorDetails(Exception error, String owner, String repo, String workflowId) {
        StringBuilder details = new StringBuilder();
        
        String errorMessage = error.getMessage();
        
        if (errorMessage != null) {
            if (errorMessage.contains("404")) {
                details.append("Possible causes: ")
                      .append("1) Workflow file '").append(workflowId).append("' not found in repository. ")
                      .append("2) Repository '").append(owner).append("/").append(repo).append("' not found or not accessible. ")
                      .append("3) Workflow does not have 'workflow_dispatch' trigger enabled.");
            } else if (errorMessage.contains("403")) {
                details.append("Possible causes: ")
                      .append("1) Insufficient permissions to trigger workflows. ")
                      .append("2) GitHub token does not have 'actions:write' permission. ")
                      .append("3) Repository actions are disabled.");
            } else if (errorMessage.contains("422")) {
                if (errorMessage.toLowerCase().contains("inputs are too large")) {
                    details.append("GitHub workflow inputs exceed size limit. ")
                          .append("The request payload is too large for GitHub Actions workflow dispatch. ")
                          .append("Solutions: 1) Reduce input data size. 2) Use compression. 3) Split into multiple smaller requests.");
                } else {
                    details.append("Possible causes: ")
                          .append("1) Invalid workflow inputs format. ")
                          .append("2) Required workflow inputs are missing. ")
                          .append("3) Branch 'main' does not exist (workflow trying to run on non-existent branch). ")
                          .append("4) Workflow inputs are too large (>65KB limit).");
                }
            } else if (errorMessage.contains("401")) {
                details.append("Possible causes: ")
                      .append("1) GitHub token is invalid or expired. ")
                      .append("2) Authentication header is malformed.");
            }
        }
        
        if (details.length() == 0) {
            details.append("Check that: ")
                  .append("1) The workflow file exists and has 'workflow_dispatch' trigger. ")
                  .append("2) GitHub token has proper permissions. ")
                  .append("3) Repository actions are enabled.");
        }
        
        return details.toString();
    }
    
    /**
     * Processes large payloads to fit within GitHub's workflow input size limits.
     * GitHub Actions has input size limits (approximately 65KB for workflow dispatch).
     * 
     * @param request The original request string
     * @return Processed request that fits within GitHub's limits
     */
    public String processLargePayload(String request) {
        if (request == null) {
            return JobRunner.encodeBase64("");
        }
        
        // Check the size of the base64 encoded request
        String encoded = JobRunner.encodeBase64(request);
        int encodedSize = encoded.length();
        
        // GitHub workflow dispatch input limit is approximately 65KB (65536 bytes)
        // We'll use 60KB as a safe threshold to account for JSON overhead
        final int MAX_GITHUB_INPUT_SIZE = 60000; // 60KB
        
        logger.info("Original request size: {} characters, encoded size: {} characters", request.length(), encodedSize);
        
        if (encodedSize <= MAX_GITHUB_INPUT_SIZE) {
            logger.debug("Request within size limits, no processing needed");
            return encoded;
        }
        
        logger.warn("Request size {} exceeds GitHub input limit of {}. Implementing compression and truncation.", 
            encodedSize, MAX_GITHUB_INPUT_SIZE);
        
        // Try compression first
        try {
            String compressed = compressAndEncode(request);
            if (compressed.length() <= MAX_GITHUB_INPUT_SIZE) {
                logger.info("Successfully compressed request from {} to {} characters", encodedSize, compressed.length());
                return compressed;
            }
            
            logger.warn("Compression insufficient. Compressed size: {} still exceeds limit.", compressed.length());
        } catch (Exception e) {
            logger.error("Failed to compress request: {}", e.getMessage());
        }
        
        // If compression doesn't work, truncate intelligently
        String truncated = truncateIntelligently(request, MAX_GITHUB_INPUT_SIZE);
        logger.warn("Applied intelligent truncation. Final size: {} characters", truncated.length());
        
        return truncated;
    }
    
    /**
     * Compresses the request using GZIP and base64 encodes the result.
     * Adds a compression marker so the receiving workflow can detect and decompress.
     * 
     * @param request The original request string
     * @return Compressed and encoded request with compression marker
     * @throws IOException If compression fails
     */
    private String compressAndEncode(String request) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos)) {
            gzipOut.write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        
        byte[] compressed = baos.toByteArray();
        String compressedBase64 = java.util.Base64.getEncoder().encodeToString(compressed);
        
        // Add compression marker for the workflow to detect
        return "GZIP_COMPRESSED:" + compressedBase64;
    }
    
    /**
     * Intelligently truncates the request to fit within size limits.
     * Preserves the most important parts: ticket key, summary, and essential data.
     * 
     * @param request The original request string
     * @param maxEncodedSize Maximum size for the base64 encoded result
     * @return Truncated and encoded request
     */
    private String truncateIntelligently(String request, int maxEncodedSize) {
        // Parse the request to extract key information
        StringBuilder truncated = new StringBuilder();
        
        try {
            // Try to parse as JSON to preserve structure
            if (request.trim().startsWith("{")) {
                org.json.JSONObject json = new org.json.JSONObject(request);
                
                // Create a minimal version with essential fields
                org.json.JSONObject minimal = new org.json.JSONObject();
                
                // Preserve essential fields in order of importance
                String[] essentialFields = {"knownInfo", "request", "ticket", "key", "summary", "description"};
                
                for (String field : essentialFields) {
                    if (json.has(field)) {
                        Object value = json.get(field);
                        
                        // For large string fields, truncate them
                        if (value instanceof String) {
                            String stringValue = (String) value;
                            if (stringValue.length() > 1000) {
                                stringValue = stringValue.substring(0, 1000) + "...[TRUNCATED]";
                            }
                            minimal.put(field, stringValue);
                        } else {
                            minimal.put(field, value);
                        }
                        
                        // Check if we're approaching the limit
                        String currentEncoded = JobRunner.encodeBase64(minimal.toString());
                        if (currentEncoded.length() > maxEncodedSize * 0.9) { // 90% of limit
                            break;
                        }
                    }
                }
                
                minimal.put("_truncation_note", "Request was truncated to fit GitHub workflow input limits. " +
                    "Original size: " + request.length() + " characters. " +
                    "Only essential fields are preserved.");
                
                truncated.append(minimal.toString());
            } else {
                // Handle non-JSON strings
                int maxPlainTextSize = (int) (maxEncodedSize * 0.75); // Account for base64 expansion
                if (request.length() > maxPlainTextSize) {
                    truncated.append(request, 0, maxPlainTextSize - 100);
                    truncated.append("\n\n[TRUNCATED - Original size: ").append(request.length()).append(" characters]");
                } else {
                    truncated.append(request);
                }
            }
        } catch (Exception e) {
            // Fallback to simple truncation if JSON parsing fails
            logger.warn("Failed to parse request as JSON for intelligent truncation: {}", e.getMessage());
            int maxPlainTextSize = (int) (maxEncodedSize * 0.75);
            if (request.length() > maxPlainTextSize) {
                truncated.append(request, 0, maxPlainTextSize - 100);
                truncated.append("\n\n[TRUNCATED - Original size: ").append(request.length()).append(" characters]");
            } else {
                truncated.append(request);
            }
        }
        
        return JobRunner.encodeBase64(truncated.toString());
    }
    
    /**
     * Waits for and finds a workflow run triggered after the specified timestamp.
     * Retries multiple times to handle delays in GitHub API.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param workflowId Workflow ID or filename
     * @param triggerTimestamp Timestamp when the workflow was triggered (in milliseconds)
     * @return Workflow run ID if found, null if no matching run exists after all retries
     * @throws IOException If the API call fails
     * @throws InterruptedException If the waiting is interrupted
     */
    private Long waitAndFindWorkflowRun(String owner, String repo, String workflowId, long triggerTimestamp) throws IOException, InterruptedException {
        int maxAttempts = 6; // Try 6 times over 60 seconds
        int[] waitTimes = {5000, 10000, 15000, 15000, 10000, 5000}; // Increasing then decreasing wait times
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            logger.info("Attempt {} of {} to find workflow run after timestamp {}", attempt + 1, maxAttempts, triggerTimestamp);
            
            Long runId = findWorkflowRunAfterTimestamp(owner, repo, workflowId, triggerTimestamp);
            if (runId != null) {
                logger.info("Successfully found workflow run ID: {} on attempt {}", runId, attempt + 1);
                return runId;
            }
            
            if (attempt < maxAttempts - 1) {
                int waitTime = waitTimes[attempt];
                logger.info("No workflow run found yet, waiting {} ms before retry...", waitTime);
                Thread.sleep(waitTime);
            }
        }
        
        logger.error("Failed to find workflow run after {} attempts over ~60 seconds", maxAttempts);
        return null;
    }

    /**
     * Finds a workflow run that was created after the specified timestamp.
     * This ensures we get the run triggered by the current webhook call, not an older one.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param workflowId Workflow ID or filename
     * @param triggerTimestamp Timestamp when the workflow was triggered (in milliseconds)
     * @return Workflow run ID if found, null if no matching run exists
     * @throws IOException If the API call fails
     */
    private Long findWorkflowRunAfterTimestamp(String owner, String repo, String workflowId, long triggerTimestamp) throws IOException {
        // Get multiple runs to find the one triggered by this webhook
        String runsPath = path(String.format("repos/%s/%s/actions/workflows/%s/runs?per_page=10", owner, repo, workflowId));
        GenericRequest getRequest = new GenericRequest(this, runsPath);
        String response = execute(getRequest);
        
        if (response != null && !response.isEmpty()) {
            JSONObject json = new JSONObject(response);
            JSONArray runs = json.optJSONArray("workflow_runs");
            
            if (runs != null && !runs.isEmpty()) {
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject run = runs.getJSONObject(i);
                    String createdAtStr = run.optString("created_at");
                    long runId = run.getLong("id");
                    
                    if (createdAtStr != null && !createdAtStr.isEmpty()) {
                        try {
                            // Parse GitHub's ISO 8601 timestamp format
                            java.time.Instant createdAt = java.time.Instant.parse(createdAtStr);
                            long createdTimestamp = createdAt.toEpochMilli();
                            
                            logger.debug("Checking workflow run ID: {}, created at: {} ({}), trigger time: {}",
                                runId, createdAtStr, createdTimestamp, triggerTimestamp);
                            
                            // Check if this run was created after our trigger timestamp
                            // Allow for a small buffer (5 seconds) to account for timing differences
                            if (createdTimestamp >= (triggerTimestamp - 5000)) {
                                logger.info("Found workflow run ID: {} created after trigger timestamp", runId);
                                return runId;
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to parse created_at timestamp for run {}: {}", runId, createdAtStr);
                        }
                    }
                }
                
                logger.warn("No workflow runs found after trigger timestamp {}. Found {} total runs.", triggerTimestamp, runs.length());
                if (runs.length() > 0) {
                    // Log details about the runs we found for debugging
                    for (int i = 0; i < Math.min(3, runs.length()); i++) {
                        JSONObject run = runs.getJSONObject(i);
                        logger.debug("Available run {}: ID={}, created_at={}", 
                            i, run.getLong("id"), run.optString("created_at"));
                    }
                }
            } else {
                logger.warn("No workflow runs found in response");
            }
        } else {
            logger.warn("Empty or null response when fetching workflow runs");
        }

        return null;
    }

    @Deprecated
    private Long findLatestWorkflowRun(String owner, String repo, String workflowId) throws IOException {
        logger.warn("Using deprecated findLatestWorkflowRun method - should use findWorkflowRunAfterTimestamp instead");
        String runsPath = path(String.format("repos/%s/%s/actions/workflows/%s/runs?per_page=1", owner, repo, workflowId));
        GenericRequest getRequest = new GenericRequest(this, runsPath);
        String response = execute(getRequest);
        
        if (response != null && !response.isEmpty()) {
            JSONObject json = new JSONObject(response);
            JSONArray runs = json.optJSONArray("workflow_runs");
            
            if (runs != null && !runs.isEmpty()) {
                return runs.getJSONObject(0).getLong("id");
            }
        }

        return null;
    }

    private boolean waitForWorkflowCompletion(String owner, String repo, Long runId) throws IOException, InterruptedException {
        int maxAttempts = 180; // 15 minutes with 5-second intervals
        int attempts = 0;

        while (attempts < maxAttempts) {
            String status = getWorkflowStatus(owner, repo, runId);

            switch (status) {
                case "completed":
                    return true;
                case "cancelled":
                case "failure":
                case "timed_out":
                    logger.info("Workflow ended with status: {}", status);
                    return true;
                default:
                    logger.debug("Current status: {} - waiting...", status);
                    Thread.sleep(5000);
                    attempts++;
            }
        }

        logger.warn("Timeout waiting for workflow completion");
        return false;
    }

    public String getWorkflowStatus(String owner, String repo, Long runId) throws IOException {
        String statusPath = path(String.format("repos/%s/%s/actions/runs/%d", owner, repo, runId));
        GenericRequest getRequest = new GenericRequest(this, statusPath);
        getRequest.setIgnoreCache(true);
        clearCache(getRequest);
        String response = execute(getRequest);
        
        if (response != null && !response.isEmpty()) {
            JSONObject json = new JSONObject(response);
            return json.optString("status", "unknown");
        }
        
        throw new IOException("Failed to get workflow status");
    }
    
    public String getWorkflowSummary(String owner, String repo, Long runId) throws IOException {
        String summaryPath = path(String.format("repos/%s/%s/actions/runs/%d", owner, repo, runId));
        GenericRequest getRequest = new GenericRequest(this, summaryPath);
        String response = execute(getRequest);
        
        if (response != null && !response.isEmpty()) {

            StringBuilder summary = new StringBuilder();

            // Try to get the actual analysis response from artifacts
            String analysisResponse = getAnalysisResponseFromArtifacts(owner, repo, runId);
            if (analysisResponse != null && !analysisResponse.isEmpty()) {
                summary.append(analysisResponse);
            }
            return summary.toString();
        }

        throw new IOException("Failed to get workflow summary");
    }
    
    public String getAnalysisResponseFromArtifacts(String owner, String repo, Long runId) throws IOException {
        try {
            String artifactsPath = path(String.format("repos/%s/%s/actions/runs/%d/artifacts", owner, repo, runId));
            GenericRequest artifactsRequest = new GenericRequest(this, artifactsPath);
            String artifactsResponse = execute(artifactsRequest);
            
            if (artifactsResponse != null && !artifactsResponse.isEmpty()) {
                GitHubArtifactsResponse artifactsModel = new GitHubArtifactsResponse(artifactsResponse);
                List<GitHubArtifact> artifacts = artifactsModel.getArtifacts();
                
                if (artifacts != null && !artifacts.isEmpty()) {
                    // Look for the dedicated response artifact (try multiple patterns)
                    for (GitHubArtifact artifact : artifacts) {
                        String name = artifact.getName();
                        
                        if (name != null) {
                            String nameLower = name.toLowerCase();
                            // Check for various response artifact patterns
                            if (nameLower.startsWith("response-") || 
                                nameLower.contains("response")) {
                                
                                // Found a response artifact
                                String downloadUrl = artifact.getArchiveDownloadUrl();
                                if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                    logger.info("Found response artifact: {} at {}", name, downloadUrl);
                                    
                                    // Download and extract the artifact content
                                    String responseContent = downloadAndExtractResponse(downloadUrl);
                                    if (responseContent != null && !responseContent.isEmpty()) {
                                        return responseContent;
                                    }
                                }
                            }
                        }
                    }
                    
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get analysis response from artifacts: {}", e.getMessage());
        }
        
        return null;
    }
    
    private String downloadAndExtractResponse(String downloadUrl) throws IOException {
        try {
            logger.info("Downloading and extracting response artifact from: {}", downloadUrl);
            
            // Download the ZIP artifact
            GenericRequest downloadRequest = new GenericRequest(this, downloadUrl);
            byte[] zipData = executeRaw(downloadRequest);
            
            if (zipData.length == 0) {
                logger.warn("No data received from artifact download");
                return null;
            }
            
            // Extract response.md from the ZIP using IOUtils
            String responseContent = IOUtils.extractFileFromZip(zipData, "response.md");
            if (responseContent != null && !responseContent.isEmpty()) {
                logger.info("Successfully extracted response content ({} characters)", responseContent.length());
                return responseContent;
            } else {
                logger.warn("No response.md found in artifact");
                return null;
            }
            
        } catch (Exception e) {
            logger.warn("Failed to download and extract artifact: {}", e.getMessage());
            return String.format("Analysis response artifact available but extraction failed.\n" +
                               "Download URL: %s\n" +
                               "Error: %s", downloadUrl, e.getMessage());
        }
    }
    
    private byte[] executeRaw(GenericRequest request) throws IOException {
        String url = request.url();
        logger.info("Downloading binary artifact from: {}", url);
        
        okhttp3.Request.Builder requestBuilder = sign(new okhttp3.Request.Builder())
                .url(url)
                .get();
        
        // Apply headers from GenericRequest
        for (String key : request.getHeaders().keySet()) {
            requestBuilder.header(key, request.getHeaders().get(key));
        }
        
        okhttp3.Request okHttpRequest = requestBuilder.build();
        
        try (okhttp3.Response response = client.newCall(okHttpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download artifact: HTTP " + response.code() + " " + response.message());
            }
            
            if (response.body() == null) {
                throw new IOException("Response body is null");
            }
            
            byte[] data = response.body().bytes();
            logger.info("Downloaded {} bytes", data.length);
            return data;
            
        } catch (IOException e) {
            String errorMessage = e.getMessage();
            boolean isConnectionError = errorMessage != null && (
                errorMessage.toLowerCase().contains("broken pipe") ||
                errorMessage.toLowerCase().contains("connection reset") ||
                errorMessage.toLowerCase().contains("timeout")
            );
            
            if (isConnectionError) {
                logger.warn("Connection issue during artifact download: {}. This might be due to large file size or network instability in cloud environment.", errorMessage);
            } else {
                logger.error("Failed to download binary artifact: {}", errorMessage);
            }
            throw new IOException("Failed to download binary artifact from: " + url + ". Error: " + errorMessage, e);
        } catch (Exception e) {
            logger.error("Unexpected error during artifact download: {}", e.getMessage(), e);
            throw new IOException("Unexpected error downloading binary artifact from: " + url, e);
        }
    }
    
    private String getWorkflowArtifacts(String owner, String repo, Long runId) throws IOException {
        try {
            String artifactsPath = path(String.format("repos/%s/%s/actions/runs/%d/artifacts", owner, repo, runId));
            GenericRequest artifactsRequest = new GenericRequest(this, artifactsPath);
            String artifactsResponse = execute(artifactsRequest);
            
            if (artifactsResponse != null && !artifactsResponse.isEmpty()) {
                GitHubArtifactsResponse artifactsModel = new GitHubArtifactsResponse(artifactsResponse);
                List<GitHubArtifact> artifacts = artifactsModel.getArtifacts();
                
                if (artifacts != null && !artifacts.isEmpty()) {
                    StringBuilder artifactsSummary = new StringBuilder();
                    
                    for (GitHubArtifact artifact : artifacts) {
                        String name = artifact.getName();
                        String downloadUrl = artifact.getArchiveDownloadUrl();
                        
                        artifactsSummary.append("- Artifact: ").append(name != null ? name : "Unknown");
                        if (downloadUrl != null && !downloadUrl.isEmpty()) {
                            artifactsSummary.append(" (Download: ").append(downloadUrl).append(")");
                        }
                        artifactsSummary.append("\n");
                    }
                    
                    return artifactsSummary.toString();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get workflow artifacts: {}", e.getMessage());
        }
        
        return null;
    }
    
}