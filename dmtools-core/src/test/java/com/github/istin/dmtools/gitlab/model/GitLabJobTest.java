// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class GitLabJobTest {

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"id\":\"123\", \"status\":\"success\", \"stage\":\"build\", \"name\":\"job1\", \"started_at\":\"2023-10-01T10:00:00Z\", \"finished_at\":\"2023-10-01T11:00:00Z\", \"web_url\":\"http://example.com\"}";
        GitLabJob job = new GitLabJob(jsonString);
        assertNotNull(job);
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "123");
        jsonObject.put("status", "success");
        jsonObject.put("stage", "build");
        jsonObject.put("name", "job1");
        jsonObject.put("started_at", "2023-10-01T10:00:00Z");
        jsonObject.put("finished_at", "2023-10-01T11:00:00Z");
        jsonObject.put("web_url", "http://example.com");

        GitLabJob job = new GitLabJob(jsonObject);
        assertNotNull(job);
    }

    @Test
    public void testGetId() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getId()).thenReturn("123");
        assertEquals("123", job.getId());
    }

    @Test
    public void testGetStatus() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getStatus()).thenReturn("success");
        assertEquals("success", job.getStatus());
    }

    @Test
    public void testGetStage() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getStage()).thenReturn("build");
        assertEquals("build", job.getStage());
    }

    @Test
    public void testGetName() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getName()).thenReturn("job1");
        assertEquals("job1", job.getName());
    }

    @Test
    public void testGetUser() {
        GitLabJob job = mock(GitLabJob.class);
        IUser user = mock(IUser.class);
        when(job.getUser()).thenReturn(user);
        assertEquals(user, job.getUser());
    }

    @Test
    public void testGetStartedAt() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getStartedAt()).thenReturn("2023-10-01T10:00:00Z");
        assertEquals("2023-10-01T10:00:00Z", job.getStartedAt());
    }

    @Test
    public void testGetFinishedAt() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getFinishedAt()).thenReturn("2023-10-01T11:00:00Z");
        assertEquals("2023-10-01T11:00:00Z", job.getFinishedAt());
    }

    @Test
    public void testGetWebUrl() {
        GitLabJob job = mock(GitLabJob.class);
        when(job.getWebUrl()).thenReturn("http://example.com");
        assertEquals("http://example.com", job.getWebUrl());
    }
}