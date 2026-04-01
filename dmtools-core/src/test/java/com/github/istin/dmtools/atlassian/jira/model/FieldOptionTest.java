// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class FieldOptionTest {

    private FieldOption fieldOption;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        fieldOption = new FieldOption(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"id\":\"123\", \"value\":\"Test Value\"}";
        FieldOption fieldOptionFromString = new FieldOption(jsonString);
        assertNotNull(fieldOptionFromString);
    }

    @Test
    public void testGetId() {
        when(mockJsonObject.getString("id")).thenReturn("123");
        String id = fieldOption.getId();
        assertEquals("123", id);
    }

    @Test
    public void testGetValue() {
        when(mockJsonObject.getString("value")).thenReturn("Test Value");
        String value = fieldOption.getValue();
        assertEquals("Test Value", value);
    }

    @Test
    public void testSetId() {
        String newId = "456";
        JSONObject result = fieldOption.setId(newId);
        assertNotNull(result);
        verify(mockJsonObject).put("id", newId);
    }
}