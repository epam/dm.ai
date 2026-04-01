// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitHubPullRequestTest {

    private GitHubPullRequest gitHubPullRequest;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("title", "Sample Title");
        jsonObject.put("body", "Sample Description");
        jsonObject.put("number", 123);
        jsonObject.put("user", new JSONObject());
        jsonObject.put("base", new JSONObject().put("ref", "main"));
        jsonObject.put("head", new JSONObject().put("ref", "feature-branch"));
        jsonObject.put("created_at", "2023-10-01T12:00:00Z");
        jsonObject.put("closed_at", "2023-10-02T12:00:00Z");
        jsonObject.put("updated_at", "2023-10-03T12:00:00Z");
        jsonObject.put("merged_at", "2023-10-02T12:00:00Z");

        gitHubPullRequest = new GitHubPullRequest(jsonObject);
    }

    @Test
    public void testGetTitle() {
        assertEquals("Sample Title", gitHubPullRequest.getTitle());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Sample Description", gitHubPullRequest.getDescription());
    }

    @Test
    public void testGetId() {
        assertEquals(Integer.valueOf(123), gitHubPullRequest.getId());
    }

    @Test
    public void testGetAuthor() {
        GitHubUser mockUser = mock(GitHubUser.class);
        GitHubPullRequest spyPullRequest = Mockito.spy(gitHubPullRequest);
        doReturn(mockUser).when(spyPullRequest).getModel(GitHubUser.class, "user");
        assertEquals(mockUser, spyPullRequest.getAuthor());
    }

    @Test
    public void testGetTargetBranchName() {
        assertEquals("main", gitHubPullRequest.getTargetBranchName());
    }

    @Test
    public void testGetSourceBranchName() {
        assertEquals("feature-branch", gitHubPullRequest.getSourceBranchName());
    }

    @Test
    public void testIsMerged() {
        assertTrue(gitHubPullRequest.isMerged());
    }
}