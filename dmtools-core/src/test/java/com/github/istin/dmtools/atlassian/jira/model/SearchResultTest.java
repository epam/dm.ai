// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class SearchResultTest {

    private SearchResult searchResult;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        searchResult = new SearchResult(mockJsonObject);
    }

    @Test
    public void testGetErrorMessages() throws JSONException {
        JSONArray expectedArray = new JSONArray();
        when(mockJsonObject.getJSONArray(SearchResult.ERROR_MESSAGES)).thenReturn(expectedArray);

        JSONArray actualArray = searchResult.getErrorMessages();

        assertNotNull(actualArray);
        assertEquals(expectedArray, actualArray);
    }

    @Test
    public void testGetMaxResults() throws JSONException {
        int expectedMaxResults = 10;
        when(mockJsonObject.optInt(SearchResult.MAX_RESULTS)).thenReturn(expectedMaxResults);

        int actualMaxResults = searchResult.getMaxResults();

        assertEquals(expectedMaxResults, actualMaxResults);
    }

    @Test
    public void testGetTotal() throws JSONException {
        int expectedTotal = 100;
        when(mockJsonObject.optInt(SearchResult.TOTAL)).thenReturn(expectedTotal);

        int actualTotal = searchResult.getTotal();

        assertEquals(expectedTotal, actualTotal);
    }

}