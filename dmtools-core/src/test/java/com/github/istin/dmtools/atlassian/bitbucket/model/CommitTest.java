// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class CommitTest {

    private JSONObject mockJsonObject;
    private Commit commit;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        commit = new Commit(mockJsonObject);
    }

    @Test
    public void testGetId() {
        when(mockJsonObject.getString("id")).thenReturn("12345");
        assertEquals("12345", commit.getId());
    }

    @Test
    public void testGetHash() {
        when(mockJsonObject.getString("hash")).thenReturn("abcde12345");
        assertEquals("abcde12345", commit.getHash());
    }

    @Test
    public void testGetMessage() {
        when(mockJsonObject.getString("message")).thenReturn("Initial commit");
        assertEquals("Initial commit", commit.getMessage());
    }

    @Test
    public void testGetSummary() {
        JSONObject summaryObject = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("summary")).thenReturn(summaryObject);
        when(summaryObject.getString("raw")).thenReturn("Summary text");
        assertEquals("Summary text", commit.getSummary());
    }

    @Test
    public void testGetAuthor() {
        JSONObject authorObject = mock(JSONObject.class);
        JSONObject userObject = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("author")).thenReturn(authorObject);
        when(authorObject.getJSONObject("user")).thenReturn(userObject);
        Assignee expectedAssignee = new Assignee(userObject);
        IUser author = commit.getAuthor();
        assertNotNull(author);
        assertEquals(expectedAssignee.getClass(), author.getClass());
    }

    @Test
    public void testGetCommiterTimestamp() {
        when(mockJsonObject.getLong("committerTimestamp")).thenReturn(1622548800000L);
        assertEquals(Long.valueOf(1622548800000L), commit.getCommiterTimestamp());
    }


}