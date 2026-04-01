// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitLabCommentTest {

    private GitLabComment gitLabComment;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        gitLabComment = new GitLabComment(mockJsonObject);
    }

    @Test
    public void testGetBody() {
        when(mockJsonObject.getString("body")).thenReturn("Sample body text");

        assertEquals("Sample body text", gitLabComment.getBody());
    }

    @Test
    public void testIsSystem() {
        when(mockJsonObject.getBoolean("system")).thenReturn(true);

        assertTrue(gitLabComment.isSystem());
    }

    @Test
    public void testGetType() {
        when(mockJsonObject.getString("type")).thenReturn("DiffNote");

        assertEquals("DiffNote", gitLabComment.getType());
    }

    @Test
    public void testGetId() {
        when(mockJsonObject.getLong("id")).thenReturn(12345L);

        assertEquals("12345", gitLabComment.getId());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetCreated() {
        gitLabComment.getCreated();
    }
}