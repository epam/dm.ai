// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model.server;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class ServerPullRequestTest {

    private JSONObject mockJsonObject;
    private ServerPullRequest serverPullRequest;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class, RETURNS_DEEP_STUBS);
        serverPullRequest = new ServerPullRequest(mockJsonObject);
    }

    @Test
    public void testGetSourceBranchName() {
        when(mockJsonObject.getJSONObject("fromRef").getString("displayId")).thenReturn("sourceBranch");
        String sourceBranchName = serverPullRequest.getSourceBranchName();
        assertEquals("sourceBranch", sourceBranchName);
    }

    @Test
    public void testGetTargetBranchName() {
        when(mockJsonObject.getJSONObject("toRef").getString("displayId")).thenReturn("targetBranch");
        String targetBranchName = serverPullRequest.getTargetBranchName();
        assertEquals("targetBranch", targetBranchName);
    }

    @Test
    public void testGetAuthor() {
        JSONObject authorObject = mock(JSONObject.class);
        JSONObject userObject = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("author")).thenReturn(authorObject);
        when(authorObject.optJSONObject("user")).thenReturn(userObject);

        Assignee author = serverPullRequest.getAuthor();
        assertNotNull(author);
    }

    @Test
    public void testGetCreatedDate() {
        when(mockJsonObject.getLong("createdDate")).thenReturn(1620000000000L);
        Long createdDate = serverPullRequest.getCreatedDate();
        assertEquals(Long.valueOf(1620000000000L), createdDate);
    }

    @Test
    public void testGetClosedDate() {
        when(mockJsonObject.getLong("closedDate")).thenReturn(1620000000000L);
        Long closedDate = serverPullRequest.getClosedDate();
        assertEquals(Long.valueOf(1620000000000L), closedDate);
    }

    @Test
    public void testGetUpdatedDate() {
        when(mockJsonObject.getLong("updatedDate")).thenReturn(1620000000000L);
        Long updatedDate = serverPullRequest.getUpdatedDate();
        assertEquals(Long.valueOf(1620000000000L), updatedDate);
    }
}