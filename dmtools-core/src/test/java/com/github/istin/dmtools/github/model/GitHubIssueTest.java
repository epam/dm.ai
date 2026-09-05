// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitHubIssueTest {

    private GitHubIssue gitHubIssue;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        gitHubIssue = new GitHubIssue(mockJsonObject);
    }

    @Test
    public void testGetNumber() {
        when(mockJsonObject.getInt("number")).thenReturn(42);
        assertEquals(Integer.valueOf(42), gitHubIssue.getNumber());
    }

    @Test
    public void testGetTitle() {
        when(mockJsonObject.getString("title")).thenReturn("Issue title");
        assertEquals("Issue title", gitHubIssue.getTitle());
    }

    @Test
    public void testGetBody() {
        when(mockJsonObject.getString("body")).thenReturn("Issue body");
        assertEquals("Issue body", gitHubIssue.getBody());
    }

    @Test
    public void testGetState() {
        when(mockJsonObject.getString("state")).thenReturn("open");
        assertEquals("open", gitHubIssue.getState());
    }

    @Test
    public void testGetHtmlUrl() {
        when(mockJsonObject.getString("html_url")).thenReturn("https://github.com/org/repo/issues/42");
        assertEquals("https://github.com/org/repo/issues/42", gitHubIssue.getHtmlUrl());
    }

    @Test
    public void testGetCommentsCount() {
        when(mockJsonObject.getInt("comments")).thenReturn(5);
        assertEquals(Integer.valueOf(5), gitHubIssue.getCommentsCount());
    }

    @Test
    public void testGetLabels() {
        JSONArray labels = new JSONArray();
        labels.put(new JSONObject().put("name", "bug"));
        labels.put(new JSONObject().put("name", "help wanted"));
        when(mockJsonObject.has("labels")).thenReturn(true);
        when(mockJsonObject.getJSONArray("labels")).thenReturn(labels);

        List<String> result = gitHubIssue.getLabels();
        assertEquals(2, result.size());
        assertTrue(result.contains("bug"));
        assertTrue(result.contains("help wanted"));
    }

    @Test
    public void testGetLabelsEmptyWhenMissing() {
        when(mockJsonObject.has("labels")).thenReturn(false);
        assertTrue(gitHubIssue.getLabels().isEmpty());
    }

    @Test
    public void testGetAssigneesEmptyWhenMissing() {
        when(mockJsonObject.has("assignees")).thenReturn(false);
        List<IUser> result = gitHubIssue.getAssignees();
        assertTrue(result.isEmpty());
    }
}
