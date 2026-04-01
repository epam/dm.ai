// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class GithubStatsTest {

    @Test
    public void testGetTotal() throws JSONException {
        JSONObject jsonObject = mock(JSONObject.class);
        when(jsonObject.optInt("total")).thenReturn(100);

        GithubStats githubStats = new GithubStats(jsonObject);
        int total = githubStats.getTotal();

        assertEquals(100, total);
    }

    @Test
    public void testGetAdditions() throws JSONException {
        JSONObject jsonObject = mock(JSONObject.class);
        when(jsonObject.optInt("additions")).thenReturn(50);

        GithubStats githubStats = new GithubStats(jsonObject);
        int additions = githubStats.getAdditions();

        assertEquals(50, additions);
    }

    @Test
    public void testGetDeletions() throws JSONException {
        JSONObject jsonObject = mock(JSONObject.class);
        when(jsonObject.optInt("deletions")).thenReturn(30);

        GithubStats githubStats = new GithubStats(jsonObject);
        int deletions = githubStats.getDeletions();

        assertEquals(30, deletions);
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"total\": 100, \"additions\": 50, \"deletions\": 30}";
        GithubStats githubStats = new GithubStats(jsonString);

        assertEquals(100, githubStats.getTotal());
        assertEquals(50, githubStats.getAdditions());
        assertEquals(30, githubStats.getDeletions());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("total", 100);
        jsonObject.put("additions", 50);
        jsonObject.put("deletions", 30);

        GithubStats githubStats = new GithubStats(jsonObject);

        assertEquals(100, githubStats.getTotal());
        assertEquals(50, githubStats.getAdditions());
        assertEquals(30, githubStats.getDeletions());
    }
}