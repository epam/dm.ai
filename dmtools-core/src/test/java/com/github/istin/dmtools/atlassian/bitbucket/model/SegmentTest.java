// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class SegmentTest {

    private Segment segment;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        when(mockJson.getString(Segment.TYPE)).thenReturn("mockType");
        segment = new Segment(mockJson);
    }

    @Test
    public void testGetType() {
        String type = segment.getType();
        assertEquals("mockType", type);
    }

    @Test
    public void testGetLines() {
        // Assuming Line is another class that needs to be tested separately
        List<Line> lines = segment.getLines();
        assertNotNull(lines);
        // Further assertions can be added based on the expected behavior of getLines()
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"type\":\"mockType\"}";
        Segment segmentFromString = new Segment(jsonString);
        assertEquals("mockType", segmentFromString.getType());
    }

    @Test
    public void testConstructorWithJsonObject() {
        Segment segmentFromJson = new Segment(mockJson);
        assertEquals("mockType", segmentFromJson.getType());
    }
}