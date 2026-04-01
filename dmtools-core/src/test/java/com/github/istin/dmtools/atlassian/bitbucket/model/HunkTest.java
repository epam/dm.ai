// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class HunkTest {

    @Test
    public void testHunkDefaultConstructor() {
        Hunk hunk = new Hunk();
        assertNotNull(hunk);
    }

    @Test
    public void testHunkJsonStringConstructor() throws JSONException {
        String jsonString = "{\"segments\":[]}";
        Hunk hunk = new Hunk(jsonString);
        assertNotNull(hunk);
    }

    @Test
    public void testHunkJsonObjectConstructor() {
        JSONObject jsonObject = new JSONObject();
        Hunk hunk = new Hunk(jsonObject);
        assertNotNull(hunk);
    }

    @Test
    public void testGetSegments() {
        Hunk hunk = Mockito.spy(new Hunk());
        List<Segment> segments = hunk.getSegments();
        assertNotNull(segments);
        assertTrue(segments.isEmpty());
    }
}