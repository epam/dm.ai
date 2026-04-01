// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.diagram;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class DiagramsCreatorParamsTest {

    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put(DiagramsCreatorParams.STORIES_JQL, "sampleJql");
        jsonObject.put(DiagramsCreatorParams.LABEL_NAME_TO_MARK_AS_REVIEWED, "reviewedLabel");
        jsonObject.put(DiagramsCreatorParams.ROLE_SPECIFIC, "roleSpecificValue");
        jsonObject.put(DiagramsCreatorParams.PROJECT_SPECIFIC, "projectSpecificValue");
    }

    @Test
    public void testConstructorWithValidJsonString() throws JSONException {
        String jsonString = jsonObject.toString();
        DiagramsCreatorParams params = new DiagramsCreatorParams(jsonString);
        assertEquals("sampleJql", params.getStoriesJql());
        assertEquals("reviewedLabel", params.getLabelNameToMarkAsReviewed());
        assertEquals("roleSpecificValue", params.getRoleSpecific());
        assertEquals("projectSpecificValue", params.getProjectSpecific());
    }

    @Test
    public void testConstructorWithInvalidJsonString() {
        String invalidJsonString = "{invalidJson}";
        assertThrows(JSONException.class, () -> new DiagramsCreatorParams(invalidJsonString));
    }

    @Test
    public void testConstructorWithValidJsonObject() {
        DiagramsCreatorParams params = new DiagramsCreatorParams(jsonObject);
        assertEquals("sampleJql", params.getStoriesJql());
        assertEquals("reviewedLabel", params.getLabelNameToMarkAsReviewed());
        assertEquals("roleSpecificValue", params.getRoleSpecific());
        assertEquals("projectSpecificValue", params.getProjectSpecific());
    }

    @Test
    public void testGetStoriesJql() {
        DiagramsCreatorParams params = new DiagramsCreatorParams(jsonObject);
        assertEquals("sampleJql", params.getStoriesJql());
    }

    @Test
    public void testGetLabelNameToMarkAsReviewed() {
        DiagramsCreatorParams params = new DiagramsCreatorParams(jsonObject);
        assertEquals("reviewedLabel", params.getLabelNameToMarkAsReviewed());
    }

    @Test
    public void testGetRoleSpecific() {
        DiagramsCreatorParams params = new DiagramsCreatorParams(jsonObject);
        assertEquals("roleSpecificValue", params.getRoleSpecific());
    }

    @Test
    public void testGetProjectSpecific() {
        DiagramsCreatorParams params = new DiagramsCreatorParams(jsonObject);
        assertEquals("projectSpecificValue", params.getProjectSpecific());
    }
}