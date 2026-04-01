// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sync;

import com.github.istin.dmtools.common.model.JSONModel;

public class SourceCodeTrackerSyncParams extends JSONModel {

    public static final String PULL_REQUESTS_STATE = "pull_requests_state";
    public static final String ISSUE_ID_CODES = "issue_id_codes";
    public static final String PRIORITY_HIGH_ATTENTION_ICON = "priority_high_attention_icon";
    public static final String BUG_ICON = "bug_icon";
    public static final String STORY_ICON = "story_icon";
    public static final String PRIORITY_NORMAL_ICON = "priority_normal_icon";
    public static final String PRIORITY_LOW_ICON = "priority_low_icon";
    public static final String PRIORITY_DEFAULT_ICON = "priority_default_icon";
    public static final String IN_PROGRESS_REOPENED_STATUSES = "in_progress_reopened_statuses";
    public static final String IS_CHECK_ALL_PULL_REQUESTS = "is_check_all_pull_requests";
    public static final String ADD_PULL_REQUEST_LABELS_AS_ISSUE_TYPE = "add_pull_request_labels_as_issue_type";
    public static final String ON_PULL_REQUEST_CREATED_STATUSES_MAPPING = "on_pull_request_created_statuses_mapping";
    public static final String ON_PULL_REQUEST_MERGED_STATUSES_MAPPING = "on_pull_request_merged_statuses_mapping";
    public static final String ON_PULL_REQUEST_CREATED_DEFAULT_STATUS = "on_pull_request_created_default_status";
    public static final String ON_PULL_REQUEST_MERGED_DEFAULT_STATUS = "on_pull_request_merged_default_status";

    public static class Icons {

        public static final String HIGH_ATTENTION = "\uD83D\uDD34";
        public static final String NORMAL = "\uD83D\uDFE0";
        public static final String LOW = "\uD83D\uDFE1";
        public static final String DEFAULT = "\uD83D\uDFE2";
        public static final String BUG = "\uD83D\uDC1E";
        public static final String STORY = "\uD83D\uDD16";
    }

    public String getPullRequestState() {
        return getString(PULL_REQUESTS_STATE);
    }

    public String[] getIssueIdCodes() {
        return getStringArray(ISSUE_ID_CODES);
    }

    public String getPriorityHighAttentionIcon() {
        String icon = getString(PRIORITY_HIGH_ATTENTION_ICON);
        if (icon == null) {
            return Icons.HIGH_ATTENTION;
        }
        return icon;
    }

    public String getBugIcon() {
        String icon = getString(BUG_ICON);
        if (icon == null) {
            return Icons.BUG;
        }
        return icon;
    }

    public String getStoryIcon() {
        String icon = getString(STORY_ICON);
        if (icon == null) {
            return Icons.STORY;
        }
        return icon;
    }

    public String getPriorityNormalIcon() {
        String icon = getString(PRIORITY_NORMAL_ICON);
        if (icon == null) {
            return Icons.NORMAL;
        }
        return icon;
    }

    public String getPriorityLowIcon() {
        String icon = getString(PRIORITY_LOW_ICON);
        if (icon == null) {
            return Icons.LOW;
        }
        return icon;
    }

    public String getPriorityDefaultIcon() {
        String icon = getString(PRIORITY_DEFAULT_ICON);
        if (icon == null) {
            return Icons.DEFAULT;
        }
        return icon;
    }

    public String[] getInProgressReopenedStatuses() {
        return getStringArray(IN_PROGRESS_REOPENED_STATUSES);
    }

    public boolean isCheckAllPullRequests() {
        return getBoolean(IS_CHECK_ALL_PULL_REQUESTS);
    }

    public boolean getAddPullRequestLabelsAsIssueType() {
        return getBoolean(ADD_PULL_REQUEST_LABELS_AS_ISSUE_TYPE);
    }

    public JSONModel getOnPullRequestCreatedStatusesMapping() {
        return new JSONModel(getJSONObject(ON_PULL_REQUEST_CREATED_STATUSES_MAPPING));
    }

    public JSONModel getOnPullRequestMergedStatusesMapping() {
        return new JSONModel(getJSONObject(ON_PULL_REQUEST_MERGED_STATUSES_MAPPING));
    }

    public String getOnPullRequestCreatedDefaultStatus() {
        return getString(ON_PULL_REQUEST_CREATED_DEFAULT_STATUS);
    }

    public String getOnPullRequestMergedDefaultStatus() {
        return getString(ON_PULL_REQUEST_MERGED_DEFAULT_STATUS);
    }

    public SourceCodeTrackerSyncParams setPullRequestState(String pullRequestState) {
        set(PULL_REQUESTS_STATE, pullRequestState);
        return this;
    }

    public SourceCodeTrackerSyncParams setIssueIdCodes(String... issueIdCodes) {
        setArray(ISSUE_ID_CODES, issueIdCodes);
        return this;
    }

    public SourceCodeTrackerSyncParams setPriorityHighAttentionIcon(String priorityHighAttentionIcon) {
        set(PRIORITY_HIGH_ATTENTION_ICON, priorityHighAttentionIcon);
        return this;
    }

    public SourceCodeTrackerSyncParams setPriorityNormalIcon(String priorityNormalIcon) {
        set(PRIORITY_NORMAL_ICON, priorityNormalIcon);
        return this;
    }

    public SourceCodeTrackerSyncParams setPriorityLowIcon(String priorityLowIcon) {
        set(PRIORITY_LOW_ICON, priorityLowIcon);
        return this;
    }

    public SourceCodeTrackerSyncParams setPriorityDefaultIcon(String priorityDefaultIcon) {
        set(PRIORITY_DEFAULT_ICON, priorityDefaultIcon);
        return this;
    }

    public SourceCodeTrackerSyncParams setInProgressReopenedStatuses(String... inProgressReopenedStatuses) {
        setArray(IN_PROGRESS_REOPENED_STATUSES, inProgressReopenedStatuses);
        return this;
    }

    public SourceCodeTrackerSyncParams setCheckAllPullRequests(boolean checkAllPullRequests) {
        set(IS_CHECK_ALL_PULL_REQUESTS, checkAllPullRequests);
        return this;
    }

    public SourceCodeTrackerSyncParams setAddPullRequestLabelsAsIssueType(boolean addPullRequestLabelsAsIssueType) {
        set(ADD_PULL_REQUEST_LABELS_AS_ISSUE_TYPE, addPullRequestLabelsAsIssueType);
        return this;
    }

    public SourceCodeTrackerSyncParams setOnPullRequestCreatedStatusesMapping(JSONModel onPullRequestCreatedStatusesMapping) {
        setModel(ON_PULL_REQUEST_CREATED_STATUSES_MAPPING, onPullRequestCreatedStatusesMapping);
        return this;
    }

    public SourceCodeTrackerSyncParams setOnPullRequestMergedStatusesMapping(JSONModel onPullRequestMergedStatusesMapping) {
        setModel(ON_PULL_REQUEST_MERGED_STATUSES_MAPPING, onPullRequestMergedStatusesMapping);
        return this;
    }

    public SourceCodeTrackerSyncParams setOnPullRequestCreatedDefaultStatus(String onPullRequestCreatedDefaultStatus) {
        set(ON_PULL_REQUEST_CREATED_DEFAULT_STATUS, onPullRequestCreatedDefaultStatus);
        return this;
    }

    public SourceCodeTrackerSyncParams setOnPullRequestMergedDefaultStatus(String onPullRequestMergedDefaultStatus) {
        set(ON_PULL_REQUEST_MERGED_DEFAULT_STATUS, onPullRequestMergedDefaultStatus);
        return this;
    }
}
