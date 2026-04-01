// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IUser;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class CommentTest {

    @Test
    public void testGetText() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("text", "Sample text");
        Comment comment = new Comment(json);

        assertEquals("Sample text", comment.getText());
    }

    @Test
    public void testGetUser() throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject authorJson = new JSONObject();
        authorJson.put("name", "John Doe");
        json.put("author", authorJson);

        Comment comment = new Comment(json);
        Assignee user = comment.getUser();

        assertEquals("John Doe", user.getName());
    }

    @Test
    public void testGetUserWhenAuthorIsNull() throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject userJson = new JSONObject();
        userJson.put("name", "Jane Doe");
        json.put("user", userJson);

        Comment comment = new Comment(json);
        Assignee user = comment.getUser();

        assertEquals("Jane Doe", user.getName());
    }

    @Test
    public void testGetUserWhenBothAuthorAndUserAreNull() throws JSONException {
        JSONObject json = new JSONObject();
        Comment comment = new Comment(json);

        Assignee user = comment.getUser();
        assertNull(user);
    }


    @Test
    public void testGetBody() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("text", "Sample body text");
        Comment comment = new Comment(json);

        assertEquals("Sample body text", comment.getBody());
    }

    @Test
    public void testGetId() {
        Comment comment = new Comment();
        assertEquals("", comment.getId());
    }

    @Test
    public void testGetCreatedThrowsException() {
        Comment comment = new Comment();
        assertThrows(UnsupportedOperationException.class, comment::getCreated);
    }
}