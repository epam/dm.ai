// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GitLabUserTest {

    @Test
    public void testConstructorWithJsonString() {
        String jsonString = "{\"id\":\"123\", \"name\":\"John Doe\", \"email\":\"john.doe@example.com\"}";
        GitLabUser user = new GitLabUser(jsonString);
        assertEquals("123", user.getID());
        assertEquals("John Doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "123");
        jsonObject.put("name", "John Doe");
        jsonObject.put("email", "john.doe@example.com");

        GitLabUser user = new GitLabUser(jsonObject);
        assertEquals("123", user.getID());
        assertEquals("John Doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }

    @Test
    public void testCreateStaticMethod() {
        String jsonString = "{\"id\":\"123\", \"name\":\"John Doe\", \"email\":\"john.doe@example.com\"}";
        GitLabUser user = GitLabUser.create(jsonString);
        assertNotNull(user);
        assertEquals("123", user.getID());
        assertEquals("John Doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }

    @Test
    public void testCreateStaticMethodWithNull() {
        GitLabUser user = GitLabUser.create(null);
        assertNotNull(user);
    }
}