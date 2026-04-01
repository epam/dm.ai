// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class CommentsResultTest {

    private CommentsResult commentsResult;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        when(mockJsonObject.optInt("maxResults")).thenReturn(10);
        when(mockJsonObject.optInt("total")).thenReturn(100);
        when(mockJsonObject.getJSONArray("comments")).thenReturn(new org.json.JSONArray());

        commentsResult = new CommentsResult(mockJsonObject);
    }

    @Test
    public void testGetMaxResults() {
        int maxResults = commentsResult.getMaxResults();
        assertEquals(10, maxResults);
    }

    @Test
    public void testGetTotal() {
        int total = commentsResult.getTotal();
        assertEquals(100, total);
    }

    @Test
    public void testGetComments() {
        List<Comment> comments = commentsResult.getComments();
        assertNotNull(comments);
    }

    @Test
    public void testConstructorWithString() throws JSONException {
        String jsonString = "{\"maxResults\": 10, \"total\": 100, \"comments\": []}";
        CommentsResult result = new CommentsResult(jsonString);
        assertEquals(10, result.getMaxResults());
        assertEquals(100, result.getTotal());
    }

    @Test
    public void testConstructorWithJSONObject() {
        CommentsResult result = new CommentsResult(mockJsonObject);
        assertEquals(10, result.getMaxResults());
        assertEquals(100, result.getTotal());
    }
}