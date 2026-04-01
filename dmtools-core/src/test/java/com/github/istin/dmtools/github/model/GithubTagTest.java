// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class GithubTagTest {

    private GithubTag githubTag;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        githubTag = new GithubTag(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"name\":\"v1.0.0\"}";
        GithubTag tag = new GithubTag(jsonString);
        assertNotNull(tag);
    }

    @Test
    public void testConstructorWithJsonObject() {
        assertNotNull(githubTag);
    }

    @Test
    public void testGetName() {
        when(mockJsonObject.getString("name")).thenReturn("v1.0.0");
        String name = githubTag.getName();
        assertEquals("v1.0.0", name);
    }
}