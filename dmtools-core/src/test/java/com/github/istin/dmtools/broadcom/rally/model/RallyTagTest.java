// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class RallyTagTest {

    private JSONObject mockJsonObject;
    private RallyTag rallyTag;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = Mockito.mock(JSONObject.class);
        when(mockJsonObject.getString("Name")).thenReturn("Sample Name");
        when(mockJsonObject.getString(RallyFields._REF)).thenReturn("Sample Ref");

        rallyTag = new RallyTag(mockJsonObject);
    }

    @Test
    public void testGetRef() {
        String expectedRef = "Sample Ref";
        String actualRef = rallyTag.getRef();
        assertEquals(expectedRef, actualRef);
    }

    @Test
    public void testGetName() {
        String expectedName = "Sample Name";
        String actualName = rallyTag.getName();
        assertEquals(expectedName, actualName);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"Name\":\"Sample Name\",\"_ref\":\"Sample Ref\"}";
        RallyTag rallyTagFromString = new RallyTag(jsonString);

        assertEquals("Sample Name", rallyTagFromString.getName());
        assertEquals("Sample Ref", rallyTagFromString.getRef());
    }
}