// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitHubFileTest {

    private GitHubFile gitHubFile;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        gitHubFile = new GitHubFile(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"path\":\"/test/path\",\"type\":\"blob\",\"url\":\"http://example.com\"}";
        GitHubFile file = new GitHubFile(jsonString);
        assertNotNull(file);
    }

    @Test
    public void testConstructorWithJsonObject() {
        assertNotNull(gitHubFile);
    }

    @Test
    public void testGetPath() {
        when(mockJsonObject.getString("path")).thenReturn("/test/path");
        assertEquals("/test/path", gitHubFile.getPath());
    }

    @Test
    public void testGetType() {
        when(mockJsonObject.getString("type")).thenReturn("blob");
        assertEquals("blob", gitHubFile.getType());
    }

    @Test
    public void testIsDir() {
        when(mockJsonObject.getString("type")).thenReturn("tree");
        assertTrue(gitHubFile.isDir());

        when(mockJsonObject.getString("type")).thenReturn("blob");
        assertFalse(gitHubFile.isDir());
    }

    @Test
    public void testGetSelfLink() {
        when(mockJsonObject.getString("url")).thenReturn("http://example.com");
        assertEquals("http://example.com", gitHubFile.getSelfLink());
    }

    @Test
    public void testGetFileContent() {
        gitHubFile.setFileContent("Sample content");
        assertEquals("Sample content", gitHubFile.getFileContent());
    }

    @Test
    public void testSetFileContent() {
        gitHubFile.setFileContent("New content");
        assertEquals("New content", gitHubFile.getFileContent());
    }
}