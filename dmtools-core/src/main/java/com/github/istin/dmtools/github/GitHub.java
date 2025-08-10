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
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

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
    public List<IPullRequest> pullRequests(String workspace, String repository, String state, boolean checkAllRequests, Calendar startDate) throws IOException {
        boolean isMerged = state.equalsIgnoreCase(IPullRequest.PullRequestState.STATE_MERGED);
        if (isMerged) {
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
            }
            allPullRequests.addAll(pullRequests);

            if (startDate != null && !pullRequests.isEmpty() && pullRequests.getLast().getCreatedDate() < startDate.getTimeInMillis()) {
                break;
            }

            if (!checkAllRequests || pullRequestsInResponse.length() < perPage) {
                // If not checking all requests, or if fewer entries than the max per page are returned, we're done
                break;
            }

            // Move to next page
            currentPage++;
        }

        return allPullRequests;
    }

    @Override
    public IPullRequest pullRequest(String workspace, String repository, String pullRequestId) throws IOException {
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
    public List<IComment> pullRequestComments(String workspace, String repository, String pullRequestId) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s/comments", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        List<IComment> result = new ArrayList<>();
        if (response == null) {
            return result;
        }
        List<IComment> comments = JSONModel.convertToModels(GitHubComment.class, new JSONArray(response));
        result.addAll(comments);
        result.addAll(pullRequestCommentsFromIssue(workspace, repository, pullRequestId));
        comments.sort((c1, c2) -> c1.getCreated().compareTo(c2.getCreated()));
        return comments;
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

    @Override
    public List<IActivity> pullRequestActivities(String workspace, String repository, String pullRequestId) throws IOException {
        String path = path(String.format("repos/%s/%s/pulls/%s/reviews", workspace, repository, pullRequestId));
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        if (response == null) {
            return new ArrayList<>();
        }
        return JSONModel.convertToModels(GitHubActivity.class, new JSONArray(response));
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
    public String addPullRequestComment(String workspace, String repository, String pullRequestId, String text) throws IOException {
        String path = path(String.format("repos/%s/%s/issues/%s/comments", workspace, repository, pullRequestId));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", text);
        postRequest.setBody(jsonObject.toString());
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
    public void addPullRequestLabel(String workspace, String repository, String pullRequestId, String label) throws IOException {
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
    public IDiffStats getPullRequestDiff(String workspace, String repository, String pullRequestID) throws IOException {
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
     * Gets file content from either a GitHub API blob URL or a standard GitHub web URL.
     *
     * @param fileUrl The URL to the file (either API blob URL or standard GitHub URL)
     * @return The content of the file
     * @throws IOException If there's an error fetching the file
     */
    @Override
    public String getFileContent(String fileUrl) throws IOException {
        if (fileUrl.contains("github.com/") && fileUrl.contains("/blob/")) {
            // Handle standard GitHub web URL
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

    /**
     * Tests the GitHub connection by making a simple API call to verify authentication and connectivity.
     * 
     * @return true if the connection is successful, false otherwise
     */
    public boolean testConnection() {
        try {
            String path = path("user");
            GenericRequest getRequest = new GenericRequest(this, path);
            String response = execute(getRequest);
            
            if (response != null && !response.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(response);
                // Check if the response contains expected user fields
                return jsonResponse.has("login") || jsonResponse.has("id");
            }
            return false;
        } catch (Exception e) {
            logger.warn("GitHub connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tests the GitHub connection and returns detailed information about the result.
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
        } catch (RateLimitException e) {
            result.put("success", false);
            result.put("message", "GitHub API rate limit exceeded");
            result.put("error", "rate_limit");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "GitHub API connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            logger.warn("GitHub connection test failed", e);
        }
        
        return result;
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

            // 1. Trigger the workflow
            logger.info("Triggering workflow {}/{}/{}", owner, repo, workflowId);
            triggerWorkflow(owner, repo, workflowId, requestParams);

            // 2. Wait a bit for the workflow to be created
            Thread.sleep(10000);

            // 3. Find the triggered workflow run
            logger.info("Finding latest workflow run...");
            Long runId = findLatestWorkflowRun(owner, repo, workflowId);

            if (runId != null) {
                logger.info("Found workflow run ID: {}", runId);

                // 4. Wait for workflow completion
                logger.info("Waiting for workflow to complete...");
                boolean completed = waitForWorkflowCompletion(owner, repo, runId);

                if (completed) {
                    // 5. Get workflow summary
                    String summary = getWorkflowSummary(owner, repo, runId);
                    logger.info("Workflow completed. Summary: {}", summary);
                    return summary;
                } else {
                    logger.warn("Workflow did not complete within timeout");
                    return "Workflow timeout - did not complete within expected time";
                }
            } else {
                logger.error("Could not find triggered workflow run");
                return "Error: Could not find triggered workflow run";
            }

        } catch (Exception e) {
            // If hook fails, log the error but don't fail the whole workflow
            logger.error("Hook call failed for URL: {}, error: {}", hookUrl, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    private void triggerWorkflow(String owner, String repo, String workflowId, String request) throws IOException {
        String triggerPath = path(String.format("repos/%s/%s/actions/workflows/%s/dispatches", owner, repo, workflowId));
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("ref", "main");
        requestBody.put("inputs", new JSONObject().put("user_request", JobRunner.encodeBase64(request)));
        
        GenericRequest postRequest = new GenericRequest(this, triggerPath);
        postRequest.setBody(requestBody.toString());
        String response = post(postRequest);
        
        logger.debug("Workflow trigger response: {}", response);
    }
    
    private Long findLatestWorkflowRun(String owner, String repo, String workflowId) throws IOException {
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
            
        } catch (Exception e) {
            logger.error("Failed to download binary artifact: {}", e.getMessage());
            throw new IOException("Failed to download binary artifact", e);
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