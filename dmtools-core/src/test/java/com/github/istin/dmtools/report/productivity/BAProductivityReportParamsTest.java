// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class BAProductivityReportParamsTest {

    private BAProductivityReportParams params;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        params = new BAProductivityReportParams(mockJsonObject);
    }

    @Test
    public void testGetFeatureProjectCode() throws JSONException {
        when(mockJsonObject.getString(BAProductivityReportParams.FEATURE_PROJECT_CODE)).thenReturn("featureCode");
        String result = params.getFeatureProjectCode();
        assertEquals("featureCode", result);
    }

    @Test
    public void testGetStoryProjectCode() throws JSONException {
        when(mockJsonObject.getString(BAProductivityReportParams.STORY_PROJECT_CODE)).thenReturn("storyCode");
        String result = params.getStoryProjectCode();
        assertEquals("storyCode", result);
    }

    @Test
    public void testGetStatusesDone() throws JSONException {
        String[] expectedStatuses = {"done1", "done2"};
        when(mockJsonObject.getJSONArray(BAProductivityReportParams.STATUSES_DONE)).thenReturn(new org.json.JSONArray(expectedStatuses));
        String[] result = params.getStatusesDone();
        assertArrayEquals(expectedStatuses, result);
    }

    @Test
    public void testGetStatusesInProgress() throws JSONException {
        String[] expectedStatuses = {"inProgress1", "inProgress2"};
        when(mockJsonObject.getJSONArray(BAProductivityReportParams.STATUSES_IN_PROGRESS)).thenReturn(new org.json.JSONArray(expectedStatuses));
        String[] result = params.getStatusesInProgress();
        assertArrayEquals(expectedStatuses, result);
    }

    @Test
    public void testGetFigmaFiles() throws JSONException {
        String[] expectedFiles = {"file1", "file2"};
        when(mockJsonObject.getJSONArray(BAProductivityReportParams.FIGMA_FILES)).thenReturn(new org.json.JSONArray(expectedFiles));
        String[] result = params.getFigmaFiles();
        assertArrayEquals(expectedFiles, result);
    }
}