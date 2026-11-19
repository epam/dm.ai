// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.gitlab.model.GitLabComment;
import com.github.istin.dmtools.gitlab.model.GitLabTicket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tracker client backed by GitLab project issues. */
public class GitLabTrackerClient extends GitLabIssues implements TrackerClient<GitLabTicket> {

    private static final Logger logger = LogManager.getLogger(GitLabTrackerClient.class);
    private static final String UNSUPPORTED = "Not supported by the GitLab issues tracker backend: ";
    private static final String DEFAULT_BASE_PATH = "https://gitlab.com";

    private static GitLabTrackerClient instance;
    private final SourceCodeConfig config;

    public GitLabTrackerClient(String basePath, String authorization) throws IOException {
        this(basePath, authorization, new PropertyReader().getGitLabWorkspace(),
                new PropertyReader().getGitLabRepository());
    }

    public GitLabTrackerClient(String basePath, String authorization, String workspace, String repository)
            throws IOException {
        super(normalizeBasePath(basePath), authorization);
        PropertyReader propertyReader = new PropertyReader();
        this.config = SourceCodeConfig.builder()
                .branchName(propertyReader.getGitLabBranch())
                .workspaceName(workspace)
                .repoName(repository)
                .type(SourceCodeConfig.Type.GITLAB)
                .auth(authorization)
                .path(normalizeBasePath(basePath))
                .build();
    }

    public static synchronized GitLabTrackerClient getInstance() throws IOException {
        if (instance == null) {
            PropertyReader propertyReader = new PropertyReader();
            String token = propertyReader.getGitLabToken();
            if (token == null || token.trim().isEmpty()) {
                logger.debug("GitLab tracker not configured: GITLAB_TOKEN is not set.");
                return null;
            }
            String basePath = propertyReader.getGitLabBasePath();
            if (basePath == null || basePath.trim().isEmpty()) {
                basePath = DEFAULT_BASE_PATH;
            }
            instance = new GitLabTrackerClient(basePath, token);
        }
        return instance;
    }

    public static synchronized void resetInstanceForTesting() {
        instance = null;
    }

    @Override
    public String getDefaultRepository() {
        return config.getRepoName();
    }

    @Override
    public String getDefaultBranch() {
        return config.getBranchName();
    }

    @Override
    public String getDefaultWorkspace() {
        return config.getWorkspaceName();
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public SourceCodeConfig getDefaultConfig() {
        return config;
    }

    @Override
    public String callHookAndWaitResponse(String hookUrl, String request) {
        throw new UnsupportedOperationException(UNSUPPORTED + "callHookAndWaitResponse");
    }

    @Override
    public GitLabTicket performTicket(String ticketKey, String[] fields) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey);
        String response = execute(new GenericRequest(this, issuePath(ref)));
        return new GitLabTicket(ref.projectPath, response);
    }

    @Override
    public List<GitLabTicket> searchAndPerform(String searchQuery, String[] fields) throws IOException {
        String projectPath = defaultProjectPath();
        List<GitLabTicket> tickets = new ArrayList<>();
        int page = 1;
        while (true) {
            GenericRequest request = new GenericRequest(this,
                    path("projects/" + encodeProjectPath(projectPath) + "/issues"));
            request.param("state", "all").param("per_page", 100).param("page", page);
            request.param("search", searchQuery);
            JSONArray issues = new JSONArray(execute(request));
            tickets.addAll(toTickets(projectPath, issues));
            if (issues.length() < 100) {
                return tickets;
            }
            page++;
        }
    }

    @Override
    public void searchAndPerform(JiraClient.Performer<GitLabTicket> performer, String searchQuery, String[] fields)
            throws Exception {
        for (GitLabTicket ticket : searchAndPerform(searchQuery, fields)) {
            if (performer.perform(ticket)) {
                break;
            }
        }
    }

    @Override
    public void postComment(String ticketKey, String comment) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey);
        JSONObject body = new JSONObject().put("body", comment);
        post(new GenericRequest(this, issuePath(ref) + "/notes").setBody(body.toString()));
    }

    @Override
    public void postCommentIfNotExists(String ticketKey, String comment) throws IOException {
        for (IComment existing : getComments(ticketKey, null)) {
            if (existing.getBody() != null && existing.getBody().contains(comment)) {
                return;
            }
        }
        postComment(ticketKey, comment);
    }

    @Override
    public List<GitLabComment> getComments(String ticketKey, ITicket ticket) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey);
        List<GitLabComment> comments = new ArrayList<>();
        int page = 1;
        while (true) {
            GenericRequest request = new GenericRequest(this, issuePath(ref) + "/notes");
            request.param("per_page", 100).param("page", page);
            JSONArray notes = new JSONArray(execute(request));
            comments.addAll(JSONModel.convertToModels(GitLabComment.class, notes));
            if (notes.length() < 100) {
                return comments;
            }
            page++;
        }
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey);
        for (IComment existing : getComments(ticketKey, null)) {
            if (existing.getBody() != null && existing.getBody().contains(comment) && existing.getId() != null) {
                delete(new GenericRequest(this, issuePath(ref) + "/notes/" + existing.getId()));
            }
        }
    }

    @Override
    public String moveToStatus(String ticketKey, String statusName) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey);
        if (statusName == null || statusName.trim().isEmpty()) {
            throw new IllegalArgumentException("GitLab issue status must not be empty");
        }
        String status = statusName.trim();
        if (!"closed".equalsIgnoreCase(status) && !"done".equalsIgnoreCase(status)
                && !"resolved".equalsIgnoreCase(status)
                && !"open".equalsIgnoreCase(status) && !"opened".equalsIgnoreCase(status)
                && !"reopen".equalsIgnoreCase(status) && !"reopened".equalsIgnoreCase(status)) {
            return patchIssue(ref, new JSONObject().put("add_labels", "status::" + status));
        }
        String stateEvent = "closed".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status)
                || "resolved".equalsIgnoreCase(status) ? "close" : "reopen";
        return patchIssue(ref, new JSONObject().put("state_event", stateEvent));
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("GitLab username must not be empty");
        }
        IssueRef ref = resolveIssueRef(ticketKey);
        GenericRequest userRequest = new GenericRequest(this, path("users"));
        userRequest.param("username", userName);
        JSONArray users = new JSONArray(execute(userRequest));
        if (users.length() == 0) {
            throw new IOException("GitLab user not found: " + userName);
        }
        int userId = users.getJSONObject(0).getInt("id");
        return patchIssue(ref, new JSONObject().put("assignee_ids", new JSONArray().put(userId)));
    }

    @Override
    public void deleteLabelInTicket(GitLabTicket ticket, String label) throws IOException {
        IssueRef ref = resolveIssueRef(ticket.getTicketKey());
        patchIssue(ref, new JSONObject().put("remove_labels", label));
    }

    @Override
    public void addLabelIfNotExists(ITicket ticket, String label) throws IOException {
        if (Utils.isLabelExists(ticket, label)) {
            return;
        }
        IssueRef ref = resolveIssueRef(ticket.getTicketKey());
        patchIssue(ref, new JSONObject().put("add_labels", label));
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description,
                                        FieldsInitializer fieldsInitializer) throws IOException {
        String projectPath = projectPath(project);
        if (issueType != null || fieldsInitializer != null) {
            logger.debug("GitLabTrackerClient: issueType and custom fields are ignored because GitLab issues "
                    + "do not support those fields.");
        }
        JSONObject body = new JSONObject().put("title", summary);
        if (description != null) {
            body.put("description", description);
        }
        String response = post(new GenericRequest(this,
                path("projects/" + encodeProjectPath(projectPath) + "/issues")).setBody(body.toString()));
        return new GitLabTicket(projectPath, response).getTicketKey();
    }

    @Override
    public GitLabTicket createTicket(String body) {
        throw new UnsupportedOperationException(UNSUPPORTED + "createTicket(body) without project context");
    }

    @Override
    public String updateDescription(String key, String description) throws IOException {
        return patchIssue(resolveIssueRef(key), new JSONObject().put("description", description));
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) {
        throw new UnsupportedOperationException(UNSUPPORTED + "updateTicket with custom fields");
    }

    @Override
    public String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) {
        throw new UnsupportedOperationException(UNSUPPORTED + "linkIssueWithRelationship");
    }

    @Override
    public String buildUrlToSearch(String query) {
        return getBasePath() + "/dashboard/issues?scope=all&search="
                + URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
    }

    @Override
    public String getTicketBrowseUrl(String ticketKey) {
        try {
            IssueRef ref = resolveIssueRef(ticketKey);
            return getBasePath() + "/" + ref.projectPath + "/-/issues/" + ref.iid;
        } catch (IllegalArgumentException e) {
            return ticketKey;
        }
    }

    @Override
    public String tag(String initiator) {
        return initiator;
    }

    @Override
    public String getTextFieldsOnly(ITicket ticket) {
        if (ticket == null) {
            return "";
        }
        try {
            return ticket.toText();
        } catch (IOException e) {
            logger.warn("Failed to render GitLab issue text for {}", ticket.getTicketKey(), e);
            return "";
        }
    }

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) {
        throw new UnsupportedOperationException(UNSUPPORTED + "getChangeLog");
    }

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) {
        return Collections.emptyList();
    }

    @Override
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) {
        return Collections.emptyList();
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) {
        throw new UnsupportedOperationException(UNSUPPORTED + "attachFileToTicket");
    }

    @Override
    public boolean isValidImageUrl(String url) {
        return false;
    }

    @Override
    public File convertUrlToFile(String href) {
        throw new UnsupportedOperationException(UNSUPPORTED + "convertUrlToFile");
    }

    @Override
    public String[] getDefaultQueryFields() {
        return new String[]{"title", "state", "labels", "assignees", "created_at", "updated_at"};
    }

    @Override
    public String[] getExtendedQueryFields() {
        return new String[]{"description"};
    }

    @Override
    public String getDefaultStatusField() {
        return "state";
    }

    @Override
    public TextType getTextType() {
        return TextType.MARKDOWN;
    }

    @Override
    public void setLogEnabled(boolean isLogEnabled) {
        // GitLab request logging is managed by the shared REST client.
    }

    private String patchIssue(IssueRef ref, JSONObject body) throws IOException {
        return patch(new GenericRequest(this, issuePath(ref)).setBody(body.toString()));
    }

    private List<GitLabTicket> toTickets(String projectPath, JSONArray issues) {
        List<GitLabTicket> tickets = new ArrayList<>();
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.optJSONObject(i);
            if (issue != null) {
                tickets.add(new GitLabTicket(projectPath, issue));
            }
        }
        return tickets;
    }

    private IssueRef resolveIssueRef(String ticketKey) {
        return resolveIssueRef(ticketKey, null, null, null);
    }

    private String defaultProjectPath() throws IOException {
        if (getDefaultWorkspace() == null || getDefaultWorkspace().trim().isEmpty()
                || getDefaultRepository() == null || getDefaultRepository().trim().isEmpty()) {
            throw new IOException("GitLab default project is not configured. Set GITLAB_WORKSPACE and GITLAB_REPOSITORY, "
                    + "or use a namespace/project#iid issue key.");
        }
        return getDefaultWorkspace() + "/" + getDefaultRepository();
    }

    private String projectPath(String project) throws IOException {
        if (project == null || project.trim().isEmpty()) {
            return defaultProjectPath();
        }
        String value = project.trim();
        if (value.contains("/") || value.matches("\\d+")) {
            return value;
        }
        if (getDefaultWorkspace() == null || getDefaultWorkspace().trim().isEmpty()) {
            throw new IOException("GitLab workspace is not configured for project: " + value);
        }
        return getDefaultWorkspace() + "/" + value;
    }

    private static String normalizeBasePath(String basePath) {
        String normalized = basePath == null || basePath.trim().isEmpty() ? DEFAULT_BASE_PATH : basePath.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
