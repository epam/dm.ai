// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JiraResponseUtilsTest {

    @Test
    public void testTransformJson() {
        JSONObject jsonObject = new JSONObject();
        JSONObject fields = new JSONObject();
        fields.put("customfield_10004", 5.0);
        fields.put("summary", "Test summary");
        jsonObject.put("fields", fields);

        Map<String, String> reverseMapping = new HashMap<>();
        reverseMapping.put("customfield_10004", "Story Points");

        JiraResponseUtils.transformJson(jsonObject, reverseMapping);

        JSONObject transformedFields = jsonObject.getJSONObject("fields");
        assertTrue(transformedFields.has("Story Points"));
        assertEquals(5.0, transformedFields.getDouble("Story Points"), 0.01);
        assertFalse(transformedFields.has("customfield_10004"));
        assertEquals("Test summary", transformedFields.getString("summary"));
    }

    @Test
    public void testTransformJsonWithIssues() {
        JSONObject searchResult = new JSONObject();
        JSONArray issues = new JSONArray();
        
        JSONObject issue = new JSONObject();
        JSONObject fields = new JSONObject();
        fields.put("customfield_10004", 3.0);
        issue.put("fields", fields);
        issues.put(issue);
        
        searchResult.put("issues", issues);

        Map<String, String> reverseMapping = new HashMap<>();
        reverseMapping.put("customfield_10004", "Story Points");

        JiraResponseUtils.transformJson(searchResult, reverseMapping);

        JSONObject transformedIssue = searchResult.getJSONArray("issues").getJSONObject(0);
        JSONObject transformedFields = transformedIssue.getJSONObject("fields");
        assertTrue(transformedFields.has("Story Points"));
        assertEquals(3.0, transformedFields.getDouble("Story Points"), 0.01);
        assertFalse(transformedFields.has("customfield_10004"));
    }

    @Test
    public void testTransformJsonWithParent() {
        JSONObject issue = new JSONObject();
        JSONObject fields = new JSONObject();
        issue.put("fields", fields);
        
        JSONObject parent = new JSONObject();
        JSONObject parentFields = new JSONObject();
        parentFields.put("customfield_10004", 8.0);
        parent.put("fields", parentFields);
        issue.put("parent", parent);

        Map<String, String> reverseMapping = new HashMap<>();
        reverseMapping.put("customfield_10004", "Story Points");

        JiraResponseUtils.transformJson(issue, reverseMapping);

        JSONObject transformedParentFields = issue.getJSONObject("parent").getJSONObject("fields");
        assertTrue(transformedParentFields.has("Story Points"));
        assertEquals(8.0, transformedParentFields.getDouble("Story Points"), 0.01);
        assertFalse(transformedParentFields.has("customfield_10004"));
    }
}

