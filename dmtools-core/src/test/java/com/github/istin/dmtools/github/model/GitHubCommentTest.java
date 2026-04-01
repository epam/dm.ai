// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class GitHubCommentTest {

    private GitHubComment gitHubComment;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("id", 12345L);
        jsonObject.put("body", "This is a comment body");
        jsonObject.put("user", new JSONObject());

        gitHubComment = new GitHubComment(jsonObject);
    }

    @Test
    public void testGetAuthor() {
        GitHubUser mockUser = mock(GitHubUser.class);
        GitHubComment spyComment = Mockito.spy(gitHubComment);
        doReturn(mockUser).when(spyComment).getModel(GitHubUser.class, "user");

        IUser author = spyComment.getAuthor();
        assertNotNull(author);
        verify(spyComment, times(1)).getModel(GitHubUser.class, "user");
    }

    @Test
    public void testGetBody() {
        String body = gitHubComment.getBody();
        assertEquals("This is a comment body", body);
    }

    @Test
    public void testGetId() {
        String id = gitHubComment.getId();
        assertEquals("12345", id);
    }

}