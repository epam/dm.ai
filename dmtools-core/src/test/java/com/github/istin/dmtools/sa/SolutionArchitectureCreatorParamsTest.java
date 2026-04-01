// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sa;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SolutionArchitectureCreatorParamsTest {

    private static final String VALID_JSON = "{"
            + "\"storiesJql\":\"testJql\","
            + "\"labelNameToMarkAsReviewed\":\"testLabel\","
            + "\"roleSpecific\":\"testRole\","
            + "\"projectSpecific\":\"testProject\""
            + "}";

    private SolutionArchitectureCreatorParams params;

    @Before
    public void setUp() throws JSONException {
        params = new SolutionArchitectureCreatorParams(VALID_JSON);
    }

    @Test
    public void testGetStoriesJql() {
        assertEquals("testJql", params.getStoriesJql());
    }

    @Test
    public void testGetLabelNameToMarkAsReviewed() {
        assertEquals("testLabel", params.getLabelNameToMarkAsReviewed());
    }

    @Test
    public void testGetRoleSpecific() {
        assertEquals("testRole", params.getRoleSpecific());
    }

    @Test
    public void testGetProjectSpecific() {
        assertEquals("testProject", params.getProjectSpecific());
    }

    @Test
    public void testConstructorWithInvalidJson() {
        assertThrows(JSONException.class, () -> new SolutionArchitectureCreatorParams("{invalidJson}"));
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject(VALID_JSON);
        SolutionArchitectureCreatorParams paramsFromJsonObject = new SolutionArchitectureCreatorParams(jsonObject);
        assertEquals("testJql", paramsFromJsonObject.getStoriesJql());
        assertEquals("testLabel", paramsFromJsonObject.getLabelNameToMarkAsReviewed());
        assertEquals("testRole", paramsFromJsonObject.getRoleSpecific());
        assertEquals("testProject", paramsFromJsonObject.getProjectSpecific());
    }
}