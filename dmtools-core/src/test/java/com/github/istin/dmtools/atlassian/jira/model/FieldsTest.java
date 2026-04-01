// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FieldsTest {

    private Fields fields;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        fields = new Fields(mockJsonObject);
    }


    @Test
    public void testGetDueDateAsString() {
        when(fields.getString(Fields.DUEDATE)).thenReturn("2023-10-10");
        assertEquals("2023-10-10", fields.getDueDateAsString());
    }


    @Test
    public void testGetSummary() {
        when(fields.getString(Fields.SUMMARY)).thenReturn("Summary");
        assertEquals("Summary", fields.getSummary());
    }

    @Test
    public void testGetDescription() {
        when(fields.getString(Fields.DESCRIPTION)).thenReturn("Description");
        assertEquals("Description", fields.getDescription());
    }

    @Test
    public void testGetLabels() {
        JSONArray mockLabels = mock(JSONArray.class);
        when(fields.getJSONArray("labels")).thenReturn(mockLabels);
        assertEquals(mockLabels, fields.getLabels());
    }

    @Test
    public void testGetLabelsByKey() {
        JSONArray mockLabels = mock(JSONArray.class);
        when(mockJsonObject.optJSONArray("key")).thenReturn(mockLabels);
        assertEquals(mockLabels, fields.getLabelsByKey("key"));
    }

    @Test
    public void testIsLabelsContains() {
        JSONArray mockLabels = new JSONArray();
        mockLabels.put("label1");
        when(fields.getLabels()).thenReturn(mockLabels);
        assertTrue(fields.isLabelsContains("label1"));
        assertFalse(fields.isLabelsContains("label2"));
    }



}