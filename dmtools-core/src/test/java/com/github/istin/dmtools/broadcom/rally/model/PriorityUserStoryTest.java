// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class PriorityUserStoryTest {

    private static final String TAGS_NAME = "_tagsNameArray";
    private PriorityUserStory priorityUserStory;

    @Before
    public void setUp() {
        priorityUserStory = new PriorityUserStory();
    }

    @Test
    public void testGetPriorityWithTags() throws JSONException {
        JSONObject jsonObject = mock(JSONObject.class);
        JSONArray jsonArray = mock(JSONArray.class);
        JSONObject tagObject = mock(JSONObject.class);

        when(jsonObject.getJSONArray(TAGS_NAME)).thenReturn(jsonArray);
        when(jsonArray.isEmpty()).thenReturn(false);
        when(jsonArray.getJSONObject(0)).thenReturn(tagObject);
        when(tagObject.getString("Name")).thenReturn("High Priority");

        PriorityUserStory priorityUserStory = new PriorityUserStory(jsonObject);
        String priority = priorityUserStory.getPriority();

        assertEquals("High Priority", priority);
    }

    @Test
    public void testGetPriorityWithoutTags() throws JSONException {
        JSONObject jsonObject = mock(JSONObject.class);
        JSONArray jsonArray = mock(JSONArray.class);

        when(jsonObject.optJSONArray(TAGS_NAME)).thenReturn(jsonArray);
        when(jsonArray.isEmpty()).thenReturn(true);

        PriorityUserStory priorityUserStory = new PriorityUserStory(jsonObject);
        String priority = priorityUserStory.getPriority();

        assertNull(priority);
    }

    @Test
    public void testGetPriorityWithNullTags() throws JSONException {
        JSONObject jsonObject = mock(JSONObject.class);

        when(jsonObject.optJSONArray(TAGS_NAME)).thenReturn(null);

        PriorityUserStory priorityUserStory = new PriorityUserStory(jsonObject);
        String priority = priorityUserStory.getPriority();

        assertNull(priority);
    }
}