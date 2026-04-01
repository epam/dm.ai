// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ProjectTest {

    @Test
    public void testDefaultConstructor() {
        Project project = new Project();
        assertNotNull("Project instance should not be null", project);
    }

    @Test
    public void testConstructorWithJSONString() {
        String jsonString = "{\"id\":\"12345\"}";
        try {
            Project project = new Project(jsonString);
            assertNotNull("Project instance should not be null", project);
            assertEquals("12345", project.getId());
        } catch (JSONException e) {
            fail("JSONException should not be thrown");
        }
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "12345");
        Project project = new Project(jsonObject);
        assertNotNull("Project instance should not be null", project);
        assertEquals("12345", project.getId());
    }

    @Test
    public void testGetId() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "12345");
        Project project = new Project(jsonObject);
        assertEquals("12345", project.getId());
    }
}