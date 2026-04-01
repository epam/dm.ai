// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class ChangeTest {

    private Change change;
    private JSONObject jsonObject;

    @Before
    public void setUp() {
        jsonObject = mock(JSONObject.class);
        change = new Change(jsonObject);
    }


    @Test
    public void testGetFilePathWithPath() {
        JSONObject pathObject = mock(JSONObject.class);
        when(jsonObject.getJSONObject("path")).thenReturn(pathObject);
        when(pathObject.getString("toString")).thenReturn("some/path/to/file");
        assertEquals("some/path/to/file", change.getFilePath());
    }

    @Test
    public void testGetFilePathWithNew() {
        when(jsonObject.getJSONObject("path")).thenReturn(null);
        JSONObject newObject = mock(JSONObject.class);
        when(jsonObject.getJSONObject("new")).thenReturn(newObject);
        when(newObject.getString("path")).thenReturn("new/path/to/file");
        assertEquals("new/path/to/file", change.getFilePath());
    }

    @Test
    public void testGetFilePathWithEmpty() {
        when(jsonObject.getJSONObject("path")).thenReturn(null);
        when(jsonObject.getJSONObject("new")).thenReturn(null);
        assertEquals("", change.getFilePath());
    }

    @Test
    public void testConstructorWithInvalidJson() {
        assertThrows(JSONException.class, () -> new Change("{invalidJson}"));
    }
}