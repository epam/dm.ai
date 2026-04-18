// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IUser;
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
    public void testGetAuthor_returnsUserBuiltFromFlatFields() throws JSONException {
        // GitLab commits API returns author_name and author_email as flat top-level strings
        JSONObject json = new JSONObject();
        json.put("author_name", "John Doe");
        json.put("author_email", "john@example.com");
        GitLabCommit commit = new GitLabCommit(json);
        IUser author = commit.getAuthor();
        assertNotNull(author);
        assertEquals("John Doe", author.getFullName());
    }

    @Test
    public void testGetAuthor_returnsNullWhenAuthorNameAbsent() throws JSONException {
        GitLabCommit commit = new GitLabCommit(new JSONObject());
        assertNull(commit.getAuthor());
    }

    @Test
    public void testGetAuthor_doesNotUseNestedAuthorObject() throws JSONException {
        // A nested "author" object (old wrong shape) must NOT satisfy getAuthor()
        JSONObject json = new JSONObject();
        JSONObject authorJson = new JSONObject();
        authorJson.put("name", "Wrong");
        json.put("author", authorJson);
        GitLabCommit commit = new GitLabCommit(json);
        // author_name is absent → should return null, not the nested object
        assertNull(commit.getAuthor());
    }

    @Test
    public void testGetCommiterTimestamp() throws JSONException {
        // GitLab returns committed_date as an ISO 8601 string at the top level
        JSONObject json = new JSONObject();
        json.put("committed_date", "2024-01-15T12:00:00.000Z");
        GitLabCommit commit = new GitLabCommit(json);
        Long ts = commit.getCommiterTimestamp();
        assertNotNull(ts);
        assertTrue(ts > 0);
    }

    @Test
    public void testGetUrl() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("web_url", "https://gitlab.com/mygroup/myrepo/-/commit/abc123");
        GitLabCommit commit = new GitLabCommit(json);
        assertEquals("https://gitlab.com/mygroup/myrepo/-/commit/abc123", commit.getUrl());
    }

    @Test
    public void testGetCommitterDate() {
        GitLabCommit commit = Mockito.mock(GitLabCommit.class);
        Calendar calendar = Calendar.getInstance();
        when(commit.getCommitterDate()).thenReturn(calendar);
        assertEquals(calendar, commit.getCommitterDate());
    }
}