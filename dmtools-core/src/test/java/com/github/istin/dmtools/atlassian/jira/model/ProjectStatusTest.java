// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.tracker.model.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class ProjectStatusTest {

    private ProjectStatus projectStatus;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        when(mockJson.getString("name")).thenReturn("Test Project");
        when(mockJson.getJSONArray("statuses")).thenReturn(new org.json.JSONArray());

        projectStatus = new ProjectStatus(mockJson);
    }

    @Test
    public void testGetName() {
        String name = projectStatus.getName();
        assertEquals("Test Project", name);
    }

    @Test
    public void testGetStatuses() {
        List<Status> statuses = projectStatus.getStatuses();
        assertNotNull(statuses);
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"name\":\"Test Project\",\"statuses\":[]}";
        ProjectStatus projectStatusFromString = new ProjectStatus(jsonString);
        assertEquals("Test Project", projectStatusFromString.getName());
    }

    @Test
    public void testConstructorWithJSONObject() {
        ProjectStatus projectStatusFromJson = new ProjectStatus(mockJson);
        assertEquals("Test Project", projectStatusFromJson.getName());
    }
}