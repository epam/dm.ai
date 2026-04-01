// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma.model;

import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class FigmaUserTest {

    @Test
    public void testDefaultConstructor() {
        FigmaUser user = new FigmaUser();
        assertNull(user.getID());
        assertNull(user.getFullName());
        assertNull(user.getEmailAddress());
    }

    @Test
    public void testConstructorWithJsonString() {
        String json = "{\"id\":\"123\", \"handle\":\"John Doe\", \"email\":\"john.doe@example.com\"}";
        FigmaUser user = new FigmaUser(json);
        assertEquals("123", user.getID());
        assertEquals("John Doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "123");
        jsonObject.put("handle", "John Doe");
        jsonObject.put("email", "john.doe@example.com");

        FigmaUser user = new FigmaUser(jsonObject);
        assertEquals("123", user.getID());
        assertEquals("John Doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }

    @Test
    public void testCreate() {
        String json = "{\"id\":\"123\", \"handle\":\"John Doe\", \"email\":\"john.doe@example.com\"}";
        FigmaUser user = FigmaUser.create(json);
        assertEquals("123", user.getID());
        assertEquals("John Doe", user.getFullName());
        assertEquals("john.doe@example.com", user.getEmailAddress());
    }

    @Test
    public void testCreateWithNullJson() {
        FigmaUser user = FigmaUser.create(null);
        assertNull(user.getID());
        assertNull(user.getFullName());
        assertNull(user.getEmailAddress());
    }
}