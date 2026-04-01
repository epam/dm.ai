// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DevProductivityReportParamsTest {

    private DevProductivityReportParams params;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        params = new DevProductivityReportParams(mockJson);
    }

    @Test
    public void testGetCommentsRegexResponsible() throws JSONException {
        when(mockJson.getString(DevProductivityReportParams.COMMENTS_REGEX_RESPONSIBLE)).thenReturn("regex");
        assertEquals("regex", params.getCommentsRegexResponsible());
    }

    @Test
    public void testGetInitialStatus() throws JSONException {
        when(mockJson.getString(DevProductivityReportParams.INITIAL_STATUS)).thenReturn("initial");
        assertEquals("initial", params.getInitialStatus());
    }

    @Test
    public void testGetCalcWeightType() throws JSONException {
        when(mockJson.getString(DevProductivityReportParams.CALC_WEIGHT_TYPE)).thenReturn("TIME_SPENT");
        assertEquals(DevProductivityReportParams.CalcWeightType.TIME_SPENT, params.getCalcWeightType());
    }

    @Test
    public void testGetStatusesReadyForTesting() throws JSONException {
        when(mockJson.getJSONArray(DevProductivityReportParams.STATUSES_READY_FOR_TESTING)).thenReturn(new JSONArray("[\"status1\", \"status2\"]"));
        String[] statuses = params.getStatusesReadyForTesting();
        assertEquals(2, statuses.length);
        assertEquals("status1", statuses[0]);
        assertEquals("status2", statuses[1]);
    }

    @Test
    public void testGetStatusesInDevelopment() throws JSONException {
        when(mockJson.getJSONArray(DevProductivityReportParams.STATUSES_IN_DEVELOPMENT)).thenReturn(new JSONArray("[\"dev1\", \"dev2\"]"));
        String[] statuses = params.getStatusesInDevelopment();
        assertEquals(2, statuses.length);
        assertEquals("dev1", statuses[0]);
        assertEquals("dev2", statuses[1]);
    }

    @Test
    public void testGetStatusesInTesting() throws JSONException {
        when(mockJson.getJSONArray(DevProductivityReportParams.STATUSES_IN_TESTING)).thenReturn(new JSONArray("[\"test1\", \"test2\"]"));
        String[] statuses = params.getStatusesInTesting();
        assertEquals(2, statuses.length);
        assertEquals("test1", statuses[0]);
        assertEquals("test2", statuses[1]);
    }

    @Test
    public void testGetSources() throws JSONException {
        JSONArray jsonArray = new JSONArray("[{\"source\": \"source1\"}, {\"source\": \"source2\"}]");
        when(mockJson.getJSONArray(DevProductivityReportParams.SOURCES)).thenReturn(jsonArray);
        JSONArray sources = params.getSources();
        assertNotNull(sources);
        assertEquals(2, sources.length());
    }

}