// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IChange;
import com.github.istin.dmtools.common.model.IStats;
import com.github.istin.dmtools.common.model.IUser;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class GitHubCommitTest {

    private GitHubCommit gitHubCommit;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        when(mockJsonObject.getString("node_id")).thenReturn("12345");
        when(mockJsonObject.getString("sha")).thenReturn("abcde");
        when(mockJsonObject.getJSONObject("commit")).thenReturn(mockJsonObject);
        when(mockJsonObject.getString("message")).thenReturn("Commit message");
        when(mockJsonObject.getJSONObject("committer")).thenReturn(mockJsonObject);
        when(mockJsonObject.getString("date")).thenReturn("2023-10-01T12:00:00Z");

        gitHubCommit = new GitHubCommit(mockJsonObject);
    }

    @Test
    public void testGetId() {
        String id = gitHubCommit.getId();
        assertEquals("12345", id);
    }

    @Test
    public void testGetHash() {
        String hash = gitHubCommit.getHash();
        assertEquals("abcde", hash);
    }

    @Test
    public void testGetMessage() {
        String message = gitHubCommit.getMessage();
        assertEquals("Commit message", message);
    }


    @Test
    public void testGetChanges() {
        // Assuming getModels returns a mock or a real list
        List<IChange> changes = gitHubCommit.getChanges();
        assertNotNull(changes);
    }


}