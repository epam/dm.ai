// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class FlowStateTest {

    private FlowState flowState;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        flowState = new FlowState(mockJsonObject);
    }

    @Test
    public void testGetRef() throws JSONException {
        when(mockJsonObject.getString(RallyFields._REF)).thenReturn("mockRef");
        String ref = flowState.getRef();
        assertEquals("mockRef", ref);
    }

    @Test
    public void testGetRefObjectName() throws JSONException {
        when(mockJsonObject.getString(RallyFields._REF_OBJECT_NAME)).thenReturn("mockRefObjectName");
        String refObjectName = flowState.getRefObjectName();
        assertEquals("mockRefObjectName", refObjectName);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"" + RallyFields._REF + "\": \"mockRef\", \"" + RallyFields._REF_OBJECT_NAME + "\": \"mockRefObjectName\"}";
        FlowState flowStateFromString = new FlowState(jsonString);
        assertEquals("mockRef", flowStateFromString.getRef());
        assertEquals("mockRefObjectName", flowStateFromString.getRefObjectName());
    }

}