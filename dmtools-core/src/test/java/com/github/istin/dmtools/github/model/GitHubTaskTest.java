// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.ITask;
import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class GitHubTaskTest {

    private GitHubTask gitHubTask;
    private GitHubTask gitHubTaskWithJson;
    private GitHubTask gitHubTaskWithJsonObject;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        gitHubTask = new GitHubTask();
        mockJsonObject = mock(JSONObject.class);
        gitHubTaskWithJson = new GitHubTask("{}");
        gitHubTaskWithJsonObject = new GitHubTask(mockJsonObject);
    }

    @Test
    public void testGetBody() {
        GitHubComment mockComment = mock(GitHubComment.class);
        when(mockComment.getBody()).thenReturn("Test Body");
        gitHubTask = spy(gitHubTask);
        doReturn(mockComment).when(gitHubTask).getComment();

        String body = gitHubTask.getBody();
        assertEquals("Test Body", body);
    }

    @Test
    public void testGetAuthor() {
        IUser mockUser = mock(IUser.class);
        gitHubTask = spy(gitHubTask);
        doReturn(mockUser).when(gitHubTask).getApproval();

        IUser author = gitHubTask.getAuthor();
        assertEquals(mockUser, author);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        GitHubTask task = new GitHubTask("{}");
        assertEquals(GitHubTask.class, task.getClass());
    }

    @Test
    public void testConstructorWithJsonObject() {
        GitHubTask task = new GitHubTask(mockJsonObject);
        assertEquals(GitHubTask.class, task.getClass());
    }
}