// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.utils.DateInterval;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChangelogTest {

    private Changelog changelog;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        changelog = new Changelog(mockJsonObject);
    }

    @Test
    public void testGetErrorMessages() throws JSONException {
        JSONArray expectedArray = new JSONArray();
        when(mockJsonObject.getJSONArray(Changelog.ERROR_MESSAGES)).thenReturn(expectedArray);

        JSONArray result = changelog.getErrorMessages();

        assertEquals(expectedArray, result);
    }

    @Test
    public void testGetMaxResults() throws JSONException {
        int expectedMaxResults = 10;
        when(mockJsonObject.optInt(Changelog.MAX_RESULTS)).thenReturn(expectedMaxResults);

        int result = changelog.getMaxResults();

        assertEquals(expectedMaxResults, result);
    }

    @Test
    public void testGetTotal() throws JSONException {
        int expectedTotal = 100;
        when(mockJsonObject.optInt(Changelog.TOTAL)).thenReturn(expectedTotal);

        int result = changelog.getTotal();

        assertEquals(expectedTotal, result);
    }


}