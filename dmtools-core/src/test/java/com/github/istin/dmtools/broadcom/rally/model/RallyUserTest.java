// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RallyUserTest {

    private RallyUser rallyUser;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = Mockito.mock(JSONObject.class);
        Mockito.when(mockJson.getString(RallyFields._REF)).thenReturn("12345");
        Mockito.when(mockJson.getString(RallyFields._REF_OBJECT_NAME)).thenReturn("John Doe");
        rallyUser = new RallyUser(mockJson);
    }

    @Test
    public void testGetID() {
        String expectedId = "12345";
        String actualId = rallyUser.getID();
        assertEquals(expectedId, actualId);
    }

    @Test
    public void testGetFullName() {
        String expectedFullName = "John Doe";
        String actualFullName = rallyUser.getFullName();
        assertEquals(expectedFullName, actualFullName);
    }

    @Test
    public void testGetEmailAddress() {
        assertThrows(UnsupportedOperationException.class, () -> rallyUser.getEmailAddress());
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"_ref\":\"12345\",\"_refObjectName\":\"John Doe\"}";
        RallyUser userFromString = new RallyUser(jsonString);
        assertEquals("12345", userFromString.getID());
        assertEquals("John Doe", userFromString.getFullName());
    }

    @Test
    public void testConstructorWithJsonObject() {
        RallyUser userFromObject = new RallyUser(mockJson);
        assertEquals("12345", userFromObject.getID());
        assertEquals("John Doe", userFromObject.getFullName());
    }
}