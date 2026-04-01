// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class RepositoryTest {

    @Test
    public void testDefaultConstructor() {
        Repository repository = new Repository();
        assertNotNull(repository);
    }

    @Test
    public void testConstructorWithJSONString() {
        String jsonString = "{\"name\":\"test-repo\"}";
        try {
            Repository repository = new Repository(jsonString);
            assertNotNull(repository);
            assertEquals("test-repo", repository.getName());
        } catch (JSONException e) {
            fail("JSONException should not have been thrown");
        }
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "test-repo");
        Repository repository = new Repository(jsonObject);
        assertNotNull(repository);
        assertEquals("test-repo", repository.getName());
    }

    @Test
    public void testGetName() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "test-repo");
        Repository repository = new Repository(jsonObject);
        assertEquals("test-repo", repository.getName());
    }
}