// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitLabFileTest {

    private GitLabFile gitLabFile;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        gitLabFile = new GitLabFile(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"path\":\"/some/path\",\"type\":\"blob\",\"url\":\"http://example.com\"}";
        GitLabFile file = new GitLabFile(jsonString);
        assertNotNull(file);
    }

    @Test
    public void testGetPath() {
        when(mockJsonObject.getString("path")).thenReturn("/some/path");
        assertEquals("/some/path", gitLabFile.getPath());
    }

    @Test
    public void testGetType() {
        when(mockJsonObject.getString("type")).thenReturn("blob");
        assertEquals("blob", gitLabFile.getType());
    }

    @Test
    public void testIsDir() {
        when(mockJsonObject.getString("type")).thenReturn("tree");
        assertTrue(gitLabFile.isDir());

        when(mockJsonObject.getString("type")).thenReturn("blob");
        assertFalse(gitLabFile.isDir());
    }

    @Test
    public void testGetSelfLink() {
        when(mockJsonObject.getString("url")).thenReturn("http://example.com");
        assertEquals("http://example.com", gitLabFile.getSelfLink());
    }

    @Test
    public void testGetFileContent() {
        gitLabFile.setFileContent("Sample content");
        assertEquals("Sample content", gitLabFile.getFileContent());
    }

    @Test
    public void testSetFileContent() {
        gitLabFile.setFileContent("New content");
        assertEquals("New content", gitLabFile.getFileContent());
    }
}