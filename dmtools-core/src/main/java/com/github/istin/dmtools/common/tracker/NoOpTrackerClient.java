// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.tracker;

import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * No-op {@link TrackerClient} for test/CI environments where no tracker integration
 * is configured. All mutating operations throw {@link UnsupportedOperationException}.
 * Read-only operations return empty defaults.
 *
 * <p>This allows {@link com.github.istin.dmtools.js.JSRunner} and other components
 * to initialize without real tracker credentials when tracker functionality is not
 * actually exercised (e.g. agents JS unit tests mock all tool calls).</p>
 */
public class NoOpTrackerClient implements TrackerClient<ITicket> {

    private static final String MSG = "No tracker integration configured. "
            + "Configure JIRA, ADO, Rally, or X-ray to use tracker features.";

    @Override
    public String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String tag(String initiator) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String getTextFieldsOnly(ITicket ticket) {
        return "";
    }

    @Override
    public String updateDescription(String key, String description) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String buildUrlToSearch(String query) {
        return "";
    }

    @Override
    public String getBasePath() {
        return "";
    }

    @Override
    public String getTicketBrowseUrl(String ticketKey) {
        return "";
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public void deleteLabelInTicket(ITicket ticket, String label) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public void addLabelIfNotExists(ITicket ticket, String label) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ITicket createTicket(String body) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public List<ITicket> searchAndPerform(String searchQuery, String[] fields) throws Exception {
        return Collections.emptyList();
    }

    @Override
    public void searchAndPerform(com.github.istin.dmtools.atlassian.jira.JiraClient.Performer<ITicket> performer, String searchQuery, String[] fields) throws Exception {
        // no-op
    }

    @Override
    public ITicket performTicket(String ticketKey, String[] fields) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public void postCommentIfNotExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public List<? extends IComment> getComments(String ticketKey, ITicket ticket) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void postComment(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String moveToStatus(String ticketKey, String statusName) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public String[] getDefaultQueryFields() {
        return new String[0];
    }

    @Override
    public String[] getExtendedQueryFields() {
        return new String[0];
    }

    @Override
    public String getDefaultStatusField() {
        return "";
    }

    @Override
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void setLogEnabled(boolean isLogEnabled) {
        // no-op
    }

    @Override
    public void setCacheGetRequestsEnabled(boolean isCacheOfGetRequestsEnabled) {
        // no-op
    }

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public TextType getTextType() {
        return TextType.MARKDOWN;
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public boolean isValidImageUrl(String url) throws IOException {
        return false;
    }

    @Override
    public File convertUrlToFile(String href) throws Exception {
        throw new UnsupportedOperationException(MSG);
    }
}
