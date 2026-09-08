// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.github.model.GitHubComment;
import com.github.istin.dmtools.github.model.GitHubTicket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link TrackerClient} implementation backed by GitHub issues, enabling
 * {@code DEFAULT_TRACKER=github} for Dagger-injected jobs (Teammate,
 * TestCasesGenerator, reporting) exactly as Jira/ADO.
 *
 * <p>Tickets are keyed {@code owner/repo#number}; a bare number resolves
 * against the configured default workspace/repository
 * ({@code SOURCE_GITHUB_WORKSPACE}/{@code SOURCE_GITHUB_REPOSITORY}).
 * GitHub issues have no status field, so "done"/"closed" close the issue and
 * other statuses are carried as labels. Agile/Jira-specific concepts
 * (changelog, attachments, iterations, epic links) throw
 * {@link UnsupportedOperationException}.</p>
 */
public class GitHubTrackerClient extends GitHubActions implements TrackerClient<GitHubTicket> {

    private static final Logger logger = LogManager.getLogger(GitHubTrackerClient.class);
    private static final String UNSUPPORTED = "Not supported by the GitHub issues tracker backend: ";

    private static GitHubTrackerClient instance;
    private final SourceCodeConfig config;

    public GitHubTrackerClient(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
        PropertyReader propertyReader = new PropertyReader();
        this.config = SourceCodeConfig.builder()
                .branchName(propertyReader.getGithubBranch())
                .repoName(propertyReader.getGithubRepository())
                .workspaceName(propertyReader.getGithubWorkspace())
                .type(SourceCodeConfig.Type.GITHUB)
                .auth(authorization)
                .path(basePath)
                .build();
    }

    public static synchronized GitHubTrackerClient getInstance() throws IOException {
        if (instance == null) {
            PropertyReader propertyReader = new PropertyReader();
            String token = propertyReader.getGithubToken();
            if (token == null || token.isEmpty()) {
                logger.debug("GitHub tracker not configured: SOURCE_GITHUB_TOKEN is not set.");
                return null;
            }
            instance = new GitHubTrackerClient(propertyReader.getGithubBasePath(), token);
        }
        return instance;
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

    /** Test hook: reset the singleton so a fresh instance is built. */
    public static synchronized void resetInstanceForTesting() {
        instance = null;
    }

    // ------------------------------------------------------------------
    // Ticket retrieval / search
    // ------------------------------------------------------------------

    @Override
    public GitHubTicket performTicket(String ticketKey, String[] fields) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey, null, null, null);
        String path = path(String.format("repos/%s/%s/issues/%d", ref.owner, ref.repo, ref.number));
        GenericRequest getRequest = new GenericRequest(this, path);
        return new GitHubTicket(ref.owner, ref.repo, execute(getRequest));
    }

    @Override
    public List<GitHubTicket> searchAndPerform(String searchQuery, String[] fields) throws IOException {
        String path = path("search/issues?q=" + encodeQuery(searchQuery) + "&per_page=100");
        GenericRequest getRequest = new GenericRequest(this, path);
        String response = execute(getRequest);
        JSONObject json = new JSONObject(response);
        JSONArray items = json.optJSONArray("items");
        List<GitHubTicket> tickets = new ArrayList<>();
        if (items == null) {
            return tickets;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            // Search results carry repository_url (".../repos/{owner}/{repo}").
            String repositoryUrl = item.optString("repository_url", "");
            String owner = null;
            String repo = null;
            int idx = repositoryUrl.indexOf("/repos/");
            if (idx >= 0) {
                String segment = repositoryUrl.substring(idx + "/repos/".length());
                String[] parts = segment.split("/");
                if (parts.length == 2) {
                    owner = parts[0];
                    repo = parts[1];
                }
            }
            if (owner == null || repo == null) {
                owner = getDefaultWorkspace();
                repo = getDefaultRepository();
            }
            tickets.add(new GitHubTicket(owner, repo, item));
        }
        return tickets;
    }

    @Override
    public void searchAndPerform(com.github.istin.dmtools.atlassian.jira.JiraClient.Performer<GitHubTicket> performer, String searchQuery, String[] fields) throws Exception {
        for (GitHubTicket ticket : searchAndPerform(searchQuery, fields)) {
            boolean isBreak = performer.perform(ticket);
            if (isBreak) {
                break;
            }
        }
    }

    private String encodeQuery(String query) throws IOException {
        return java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8.name());
    }

    // ------------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------------

    @Override
    public void postComment(String ticketKey, String comment) throws IOException {
        createComment(null, null, null, comment, ticketKey);
    }

    @Override
    public void postCommentIfNotExists(String ticketKey, String comment) throws IOException {
        List<? extends IComment> comments = getComments(ticketKey, null);
        for (IComment existing : comments) {
            if (existing.getBody() != null && existing.getBody().contains(comment)) {
                return;
            }
        }
        postComment(ticketKey, comment);
    }

    @Override
    public List<? extends IComment> getComments(String ticketKey, ITicket ticket) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey, null, null, null);
        return pullRequestComments(ref.owner, ref.repo, String.valueOf(ref.number));
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        IssueRef ref = resolveIssueRef(ticketKey, null, null, null);
        List<? extends IComment> comments = getComments(ticketKey, null);
        for (IComment existing : comments) {
            if (existing.getBody() != null && existing.getBody().contains(comment)) {
                String id = existing.getId();
                if (id != null) {
                    String path = path(String.format("repos/%s/%s/issues/comments/%s", ref.owner, ref.repo, id));
                    delete(new GenericRequest(this, path));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // State / labels / assignment
    // ------------------------------------------------------------------

    @Override
    public String moveToStatus(String ticketKey, String statusName) throws IOException {
        return moveIssueToStatus(statusName, null, null, null, ticketKey);
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        return assignIssue(userName, null, null, null, ticketKey);
    }

    @Override
    public void addLabelIfNotExists(ITicket ticket, String label) throws IOException {
        if (Utils.isLabelExists(ticket, label)) {
            return;
        }
        addLabels(null, null, ticketNumber(ticket), new String[]{label}, ticket.getTicketKey());
    }

    @Override
    public void deleteLabelInTicket(GitHubTicket ticket, String label) throws IOException {
        removeLabel(null, null, ticketNumber(ticket), label, ticket.getTicketKey());
    }

    private Integer ticketNumber(ITicket ticket) {
        return ticket instanceof GitHubTicket ? ((GitHubTicket) ticket).getNumber() : null;
    }

    // ------------------------------------------------------------------
    // Create / update
    // ------------------------------------------------------------------

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        String response = createIssue(null, null, summary, description, project);
        JSONObject json = new JSONObject(response);
        String owner = project != null && project.contains("/") ? project.substring(0, project.indexOf('/')) : getDefaultWorkspace();
        String repo = project != null && project.contains("/") ? project.substring(project.indexOf('/') + 1) : getDefaultRepository();
        GitHubTicket ticket = new GitHubTicket(owner, repo, json);
        if (fieldsInitializer != null) {
            // GitHub issues have no custom fields bag; nothing to initialize.
            logger.debug("GitHubTrackerClient: fieldsInitializer ignored (no custom fields).");
        }
        return ticket.getCompositeKey();
    }

    @Override
    public GitHubTicket createTicket(String body) {
        throw new UnsupportedOperationException(UNSUPPORTED + "createTicket(body) without project context");
    }

    @Override
    public String updateDescription(String key, String description) throws IOException {
        IssueRef ref = resolveIssueRef(key, null, null, null);
        String path = path(String.format("repos/%s/%s/issues/%d", ref.owner, ref.repo, ref.number));
        GenericRequest patchRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("body", description);
        patchRequest.setBody(jsonObject.toString());
        return patch(patchRequest);
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException(UNSUPPORTED + "updateTicket with custom fields");
    }

    // ------------------------------------------------------------------
    // Issue links — GitHub has no native issue-link API
    // ------------------------------------------------------------------

    @Override
    public String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) throws IOException {
        throw new UnsupportedOperationException(UNSUPPORTED + "linkIssueWithRelationship");
    }

    // ------------------------------------------------------------------
    // URLs / metadata
    // ------------------------------------------------------------------

    @Override
    public String buildUrlToSearch(String query) {
        return "https://github.com/search?q=" + query + "&type=issues";
    }

    @Override
    public String getTicketBrowseUrl(String ticketKey) {
        try {
            IssueRef ref = resolveIssueRef(ticketKey, null, null, null);
            return "https://github.com/" + ref.owner + "/" + ref.repo + "/issues/" + ref.number;
        } catch (Exception e) {
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
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Unsupported Agile/Jira-specific concepts
    // ------------------------------------------------------------------

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException {
        throw new UnsupportedOperationException(UNSUPPORTED + "getChangeLog");
    }

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException {
        throw new UnsupportedOperationException(UNSUPPORTED + "attachFileToTicket");
    }

    @Override
    public boolean isValidImageUrl(String url) throws IOException {
        return false;
    }

    @Override
    public File convertUrlToFile(String href) throws Exception {
        throw new UnsupportedOperationException(UNSUPPORTED + "convertUrlToFile");
    }

    // ------------------------------------------------------------------
    // Query field config
    // ------------------------------------------------------------------

    @Override
    public String[] getDefaultQueryFields() {
        return new String[]{"title", "state", "labels", "assignees", "created_at", "updated_at"};
    }

    @Override
    public String[] getExtendedQueryFields() {
        return new String[]{"body"};
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
        // GitHub client logging is managed globally; nothing to toggle here.
    }
}
