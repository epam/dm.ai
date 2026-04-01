// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class RevisionHistoryTest {

    private RevisionHistory revisionHistory;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        revisionHistory = new RevisionHistory(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"_ref\":\"someRefValue\"}";
        RevisionHistory revisionHistoryFromString = new RevisionHistory(jsonString);
        assertNotNull(revisionHistoryFromString);
    }

    @Test
    public void testConstructorWithJsonObject() {
        assertNotNull(revisionHistory);
    }

    @Test
    public void testGetRef() {
        when(mockJsonObject.getString(RallyFields._REF)).thenReturn("someRefValue");
        String ref = revisionHistory.getRef();
        assertEquals("someRefValue", ref);
    }
}