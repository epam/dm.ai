// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class IterationTest {

    private Iteration iteration;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        iteration = new Iteration(mockJson);
    }

    @Test
    public void testGetIterationName() {
        when(mockJson.getString(RallyFields.NAME)).thenReturn("Iteration 1");
        assertEquals("Iteration 1", iteration.getIterationName());
    }

    @Test
    public void testGetId() {
        when(mockJson.getString(RallyFields.NAME)).thenReturn("Iteration 1");
        assertEquals("Iteration 1".hashCode(), iteration.getId());
    }

    @Test
    public void testGetStartDate() {
        String startDateStr = "2023-10-01T00:00:00.000Z";
        Date expectedDate = DateUtils.parseRallyDate(startDateStr);
        when(mockJson.getString(RallyFields.START_DATE)).thenReturn(startDateStr);
        assertEquals(expectedDate, iteration.getStartDate());
    }

    @Test
    public void testGetEndDate() {
        String endDateStr = "2023-10-01T00:00:00.000Z";
        Date expectedDate = DateUtils.parseRallyDate(endDateStr);
        when(mockJson.getString(RallyFields.END_DATE)).thenReturn(endDateStr);
        assertEquals(expectedDate, iteration.getEndDate());
    }

    @Test
    public void testIsReleased() {
        String endDateStr = "2023-10-01T00:00:00.000Z";
        when(mockJson.getString(RallyFields.END_DATE)).thenReturn(endDateStr);
        assertTrue(iteration.isReleased());
    }

    @Test
    public void testGetName() {
        when(mockJson.getString(RallyFields.NAME)).thenReturn("Iteration 1");
        assertEquals("Iteration 1", iteration.getName());
    }

    @Test
    public void testGetType() {
        when(mockJson.getString(RallyFields._TYPE)).thenReturn("Type A");
        assertEquals("Type A", iteration.getType());
    }

    @Test
    public void testGetState() {
        when(mockJson.getString(RallyFields.STATE)).thenReturn("Active");
        assertEquals("Active", iteration.getState());
    }
}