// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.utils.DateUtils;

public class GitLabPullRequestTest {

    private GitLabPullRequest gitLabPullRequest;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("title", "Sample Title");
        jsonObject.put("description", "Sample Description");
        jsonObject.put("iid", 123);
        jsonObject.put("author", new JSONObject());
        jsonObject.put("target_branch", "main");
        jsonObject.put("source_branch", "feature");
        jsonObject.put("created_at", "2023-10-01T12:00:00Z");
        jsonObject.put("merged_at", "2023-10-02T12:00:00Z");
        jsonObject.put("updated_at", "2023-10-03T12:00:00Z");

        gitLabPullRequest = new GitLabPullRequest(jsonObject);
    }

    @Test
    public void testGetTitle() {
        assertEquals("Sample Title", gitLabPullRequest.getTitle());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Sample Description", gitLabPullRequest.getDescription());
    }

    @Test
    public void testGetId() {
        assertEquals(Integer.valueOf(123), gitLabPullRequest.getId());
    }

    @Test
    public void testGetAuthor() {
        GitLabUser mockUser = mock(GitLabUser.class);
        GitLabPullRequest spyPullRequest = Mockito.spy(gitLabPullRequest);
        doReturn(mockUser).when(spyPullRequest).getModel(GitLabUser.class, "author");
        assertEquals(mockUser, spyPullRequest.getAuthor());
    }

    @Test
    public void testGetTargetBranchName() {
        assertEquals("main", gitLabPullRequest.getTargetBranchName());
    }

    @Test
    public void testGetSourceBranchName() {
        assertEquals("feature", gitLabPullRequest.getSourceBranchName());
    }

    @Test
    public void testGetCreatedDate() {
        Long expectedTime = DateUtils.smartParseDate("2023-10-01T12:00:00Z").getTime();
        assertEquals(expectedTime, gitLabPullRequest.getCreatedDate());
    }

    @Test
    public void testGetClosedDate() {
        Long expectedTime = DateUtils.smartParseDate("2023-10-02T12:00:00Z").getTime();
        assertEquals(expectedTime, gitLabPullRequest.getClosedDate());
    }

    @Test
    public void testGetClosedDateWhenMergedAtIsNull() throws JSONException {
        jsonObject.put("merged_at", JSONObject.NULL);
        gitLabPullRequest = new GitLabPullRequest(jsonObject);
        Long expectedTime = DateUtils.smartParseDate("2023-10-03T12:00:00Z").getTime();
        assertEquals(expectedTime, gitLabPullRequest.getClosedDate());
    }

    @Test
    public void testGetUpdatedDate() {
        Long expectedTime = DateUtils.smartParseDate("2023-10-03T12:00:00Z").getTime();
        assertEquals(expectedTime, gitLabPullRequest.getUpdatedDate());
    }
}