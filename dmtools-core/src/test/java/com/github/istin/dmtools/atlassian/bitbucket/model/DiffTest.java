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

public class DiffTest {

    private Diff diff;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        diff = new Diff(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"key\":\"value\"}";
        Diff diffFromString = new Diff(jsonString);
        assertNotNull(diffFromString);
    }

    @Test
    public void testConstructorWithJsonObject() {
        assertNotNull(diff);
    }

    @Test
    public void testGetHunks() {
        List<Hunk> hunks = diff.getHunks();
        assertNotNull(hunks);
    }

    @Test
    public void testGetSourceWhenSourceIsNull() {
        when(mockJsonObject.optJSONObject("source")).thenReturn(null);

        String source = diff.getSource();
        assertEquals("", source);
    }
}