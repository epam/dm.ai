// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class GitHubUserTest {

    private GitHubUser gitHubUser;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        gitHubUser = new GitHubUser(mockJsonObject);
    }

    @Test
    public void testGetID() {
        when(mockJsonObject.getString("id")).thenReturn("12345");
        String id = gitHubUser.getID();
        assertEquals("12345", id);
    }

    @Test
    public void testGetFullName() {
        when(mockJsonObject.getString("login")).thenReturn("john_doe");
        String fullName = gitHubUser.getFullName();
        assertEquals("john_doe", fullName);
    }

    @Test
    public void testGetEmailAddress() {
        when(mockJsonObject.getString("email")).thenReturn("john.doe@example.com");
        String email = gitHubUser.getEmailAddress();
        assertEquals("john.doe@example.com", email);
    }

    @Test
    public void testCreate() {
        String json = "{\"id\":\"12345\",\"login\":\"john_doe\",\"email\":\"john.doe@example.com\"}";
        GitHubUser user = GitHubUser.create(json);
        assertEquals("12345", user.getID());
        assertEquals("john_doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }
}