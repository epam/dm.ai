// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class GitLabCommitTest {

    @Test
    public void testGetId() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", "12345");
        GitLabCommit commit = new GitLabCommit(json);
        assertEquals("12345", commit.getId());
    }

    @Test
    public void testGetHash() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", "12345");
        GitLabCommit commit = new GitLabCommit(json);
        assertEquals("12345", commit.getHash());
    }

    @Test
    public void testGetMessage() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("message", "Initial commit");
        GitLabCommit commit = new GitLabCommit(json);
        assertEquals("Initial commit", commit.getMessage());
    }

    @Test
    public void testGetStats() {
        GitLabCommit commit = new GitLabCommit();
        assertThrows(UnsupportedOperationException.class, commit::getStats);
    }

    @Test
    public void testGetChanges() {
        GitLabCommit commit = new GitLabCommit();
        assertThrows(UnsupportedOperationException.class, commit::getChanges);
    }

    @Test
    public void testGetAuthor() throws JSONException {
        JSONObject authorJson = new JSONObject();
        authorJson.put("name", "John Doe");
        JSONObject json = new JSONObject();
        json.put("author", authorJson);
        GitLabCommit commit = new GitLabCommit(json);
        assertNotNull(commit.getAuthor());
    }

    @Test
    public void testGetCommitterDate() {
        GitLabCommit commit = Mockito.mock(GitLabCommit.class);
        Calendar calendar = Calendar.getInstance();
        when(commit.getCommitterDate()).thenReturn(calendar);
        assertEquals(calendar, commit.getCommitterDate());
    }
}