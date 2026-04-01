// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.atlassian.common.model.Assignee;

import java.util.Date;

public class CommentTest {

    private Comment comment;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("id", "123");
        jsonObject.put("body", "This is a comment body");
        jsonObject.put("author", new JSONObject());

        comment = new Comment(jsonObject);
    }

    @Test
    public void testGetId() {
        assertEquals("123", comment.getId());
    }

    @Test
    public void testGetBody() {
        assertEquals("This is a comment body", comment.getBody());
    }

    @Test
    public void testGetAuthor() {
        Assignee mockAssignee = mock(Assignee.class);
        Comment spyComment = Mockito.spy(comment);
        doReturn(mockAssignee).when(spyComment).getModel(Assignee.class, "author");

        IUser author = spyComment.getAuthor();
        assertEquals(mockAssignee, author);
    }
}