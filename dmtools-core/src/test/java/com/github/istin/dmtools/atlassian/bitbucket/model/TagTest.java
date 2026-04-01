// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TagTest {

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"id\":\"refs/tags/v1.0\",\"name\":\"v1.0\",\"latestCommit\":\"abc123\"}";
        Tag tag = new Tag(jsonString);

        assertEquals("refs/tags/v1.0", tag.getId());
        assertEquals("v1.0", tag.getName());
        assertEquals("abc123", tag.getLatestCommit());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "refs/tags/v1.0");
        jsonObject.put("name", "v1.0");
        jsonObject.put("latestCommit", "abc123");

        Tag tag = new Tag(jsonObject);

        assertEquals("refs/tags/v1.0", tag.getId());
        assertEquals("v1.0", tag.getName());
        assertEquals("abc123", tag.getLatestCommit());
    }

    @Test
    public void testGetNameWithNullId() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "v1.0");

        Tag tag = new Tag(jsonObject);

        assertNull(tag.getId());
        assertEquals("v1.0", tag.getName());
    }

    @Test
    public void testGetLatestCommit() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("latestCommit", "abc123");

        Tag tag = new Tag(jsonObject);

        assertEquals("abc123", tag.getLatestCommit());
    }
}