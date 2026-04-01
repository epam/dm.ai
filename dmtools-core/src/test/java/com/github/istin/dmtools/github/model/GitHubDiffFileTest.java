// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class GitHubDiffFileTest {

    private GitHubDiffFile gitHubDiffFile;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        gitHubDiffFile = new GitHubDiffFile(mockJsonObject);
    }

    @Test
    public void testGetPatch() {
        when(mockJsonObject.getString("patch")).thenReturn("mockPatch");
        String patch = gitHubDiffFile.getPatch();
        assertEquals("mockPatch", patch);
    }

    @Test
    public void testGetFilePath() {
        GitHubDiffFile spyGitHubDiffFile = spy(gitHubDiffFile);
        doReturn("mockPath").when(spyGitHubDiffFile).getPath();
        String filePath = spyGitHubDiffFile.getFilePath();
        assertEquals("mockPath", filePath);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"patch\":\"mockPatch\"}";
        GitHubDiffFile gitHubDiffFileFromString = new GitHubDiffFile(jsonString);
        assertEquals("mockPatch", gitHubDiffFileFromString.getPatch());
    }

    @Test
    public void testConstructorWithJsonObject() {
        when(mockJsonObject.getString("patch")).thenReturn("mockPatch");
        GitHubDiffFile gitHubDiffFileFromObject = new GitHubDiffFile(mockJsonObject);
        assertEquals("mockPatch", gitHubDiffFileFromObject.getPatch());
    }
}