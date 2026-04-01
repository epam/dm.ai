// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class FileTest {

    private File file;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        file = new File(mockJsonObject);
    }


    @Test
    public void testGetFileContent() {
        file.setFileContent("file content");
        assertEquals("file content", file.getFileContent());
    }

    @Test
    public void testSetFileContent() {
        file.setFileContent("new content");
        assertEquals("new content", file.getFileContent());
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"path\":\"some/path\",\"type\":\"commit_file\"}";
        File fileFromJsonString = new File(jsonString);
        assertEquals("some/path", fileFromJsonString.getPath());
        assertEquals("commit_file", fileFromJsonString.getType());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("path", "some/path");
        jsonObject.put("type", "commit_file");
        File fileFromJsonObject = new File(jsonObject);
        assertEquals("some/path", fileFromJsonObject.getPath());
        assertEquals("commit_file", fileFromJsonObject.getType());
    }
}