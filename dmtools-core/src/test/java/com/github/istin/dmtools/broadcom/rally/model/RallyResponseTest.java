// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class RallyResponseTest {

    private RallyResponse rallyResponse;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        rallyResponse = new RallyResponse(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"QueryResult\": {}}";
        RallyResponse response = new RallyResponse(jsonString);
        assertNotNull(response);
    }

    @Test
    public void testConstructorWithJsonObject() {
        RallyResponse response = new RallyResponse(mockJsonObject);
        assertNotNull(response);
    }


    @Test
    public void testGetQueryResultWhenNull() {
        when(rallyResponse.getModel(QueryResult.class, "QueryResult")).thenReturn(null);

        QueryResult result = rallyResponse.getQueryResult();
        assertNull(result);
    }
}