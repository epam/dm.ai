// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GitLabTagTest {

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"name\":\"v1.0.0\"}";
        GitLabTag gitLabTag = new GitLabTag(jsonString);
        assertEquals("v1.0.0", gitLabTag.getName());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "v1.0.0");
        GitLabTag gitLabTag = new GitLabTag(jsonObject);
        assertEquals("v1.0.0", gitLabTag.getName());
    }

    @Test
    public void testGetName() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "v1.0.0");
        GitLabTag gitLabTag = new GitLabTag(jsonObject);
        assertEquals("v1.0.0", gitLabTag.getName());
    }

    @Test
    public void testConstructorWithInvalidJSONString() {
        String invalidJsonString = "invalid json";
        assertThrows(JSONException.class, () -> new GitLabTag(invalidJsonString));
    }
}