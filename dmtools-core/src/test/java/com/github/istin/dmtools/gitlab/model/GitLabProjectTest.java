// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class GitLabProjectTest {

    private GitLabProject gitLabProject;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = Mockito.mock(JSONObject.class);
        gitLabProject = new GitLabProject(mockJsonObject);
    }

    @Test
    public void testGetId() {
        when(mockJsonObject.getString("id")).thenReturn("123");
        assertEquals("123", gitLabProject.getId());
    }

    @Test
    public void testGetNameWithNamespace() {
        when(mockJsonObject.getString("path_with_namespace")).thenReturn("group/project");
        assertEquals("project", gitLabProject.getName());
    }

    @Test
    public void testGetNameWithoutNamespace() {
        when(mockJsonObject.getString("path_with_namespace")).thenReturn(null);
        when(mockJsonObject.getString("name")).thenReturn("projectName");
        assertEquals("projectName", gitLabProject.getName());
    }

    @Test
    public void testGetDescription() {
        when(mockJsonObject.getString("description")).thenReturn("A sample project");
        assertEquals("A sample project", gitLabProject.getDescription());
    }

    @Test
    public void testGetWebUrl() {
        when(mockJsonObject.getString("web_url")).thenReturn("http://example.com");
        assertEquals("http://example.com", gitLabProject.getWebUrl());
    }

    @Test
    public void testGetSshUrl() {
        when(mockJsonObject.getString("ssh_url_to_repo")).thenReturn("git@example.com:project.git");
        assertEquals("git@example.com:project.git", gitLabProject.getSshUrl());
    }

    @Test
    public void testGetHttpUrl() {
        when(mockJsonObject.getString("http_url_to_repo")).thenReturn("http://example.com/project.git");
        assertEquals("http://example.com/project.git", gitLabProject.getHttpUrl());
    }
}