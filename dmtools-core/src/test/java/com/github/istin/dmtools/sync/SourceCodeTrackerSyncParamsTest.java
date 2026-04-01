// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sync;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;

public class SourceCodeTrackerSyncParamsTest {

    private SourceCodeTrackerSyncParams params;

    @Before
    public void setUp() {
        params = Mockito.spy(new SourceCodeTrackerSyncParams());
    }

    @Test
    public void testGetPullRequestState() {
        String expected = "open";
        doReturn(expected).when(params).getString(SourceCodeTrackerSyncParams.PULL_REQUESTS_STATE);
        assertEquals(expected, params.getPullRequestState());
    }

    @Test
    public void testGetIssueIdCodes() {
        String[] expected = {"ID1", "ID2"};
        doReturn(expected).when(params).getStringArray(SourceCodeTrackerSyncParams.ISSUE_ID_CODES);
        assertArrayEquals(expected, params.getIssueIdCodes());
    }

    @Test
    public void testGetPriorityHighAttentionIcon() {
        doReturn(null).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_HIGH_ATTENTION_ICON);
        assertEquals(SourceCodeTrackerSyncParams.Icons.HIGH_ATTENTION, params.getPriorityHighAttentionIcon());

        String customIcon = "customIcon";
        doReturn(customIcon).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_HIGH_ATTENTION_ICON);
        assertEquals(customIcon, params.getPriorityHighAttentionIcon());
    }

    @Test
    public void testGetPriorityNormalIcon() {
        doReturn(null).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_NORMAL_ICON);
        assertEquals(SourceCodeTrackerSyncParams.Icons.NORMAL, params.getPriorityNormalIcon());

        String customIcon = "customIcon";
        doReturn(customIcon).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_NORMAL_ICON);
        assertEquals(customIcon, params.getPriorityNormalIcon());
    }

    @Test
    public void testGetPriorityLowIcon() {
        doReturn(null).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_LOW_ICON);
        assertEquals(SourceCodeTrackerSyncParams.Icons.LOW, params.getPriorityLowIcon());

        String customIcon = "customIcon";
        doReturn(customIcon).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_LOW_ICON);
        assertEquals(customIcon, params.getPriorityLowIcon());
    }

    @Test
    public void testGetPriorityDefaultIcon() {
        doReturn(null).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_DEFAULT_ICON);
        assertEquals(SourceCodeTrackerSyncParams.Icons.DEFAULT, params.getPriorityDefaultIcon());

        String customIcon = "customIcon";
        doReturn(customIcon).when(params).getString(SourceCodeTrackerSyncParams.PRIORITY_DEFAULT_ICON);
        assertEquals(customIcon, params.getPriorityDefaultIcon());
    }

    @Test
    public void testGetInProgressReopenedStatuses() {
        String[] expected = {"status1", "status2"};
        doReturn(expected).when(params).getStringArray(SourceCodeTrackerSyncParams.IN_PROGRESS_REOPENED_STATUSES);
        assertArrayEquals(expected, params.getInProgressReopenedStatuses());
    }

    @Test
    public void testIsCheckAllPullRequests() {
        doReturn(true).when(params).getBoolean(SourceCodeTrackerSyncParams.IS_CHECK_ALL_PULL_REQUESTS);
        assertTrue(params.isCheckAllPullRequests());
    }

    @Test
    public void testGetAddPullRequestLabelsAsIssueType() {
        doReturn(true).when(params).getBoolean(SourceCodeTrackerSyncParams.ADD_PULL_REQUEST_LABELS_AS_ISSUE_TYPE);
        assertTrue(params.getAddPullRequestLabelsAsIssueType());
    }

    @Test
    public void testGetOnPullRequestCreatedDefaultStatus() {
        String expected = "defaultStatus";
        doReturn(expected).when(params).getString(SourceCodeTrackerSyncParams.ON_PULL_REQUEST_CREATED_DEFAULT_STATUS);
        assertEquals(expected, params.getOnPullRequestCreatedDefaultStatus());
    }

    @Test
    public void testGetOnPullRequestMergedDefaultStatus() {
        String expected = "defaultStatus";
        doReturn(expected).when(params).getString(SourceCodeTrackerSyncParams.ON_PULL_REQUEST_MERGED_DEFAULT_STATUS);
        assertEquals(expected, params.getOnPullRequestMergedDefaultStatus());
    }
}