// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ba;

import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class UserStoryGeneratorParamsTest {

    @Test
    public void testGetExistingUserStoriesJql() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setExistingUserStoriesJql("JQL Query");
        assertEquals("JQL Query", params.getExistingUserStoriesJql());
    }

    @Test
    public void testGetOutputType() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setOutputType("trackerComment");
        assertEquals("trackerComment", params.getOutputType());
    }

    @Test
    public void testGetPriorities() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setPriorities("High");
        assertEquals("High", params.getPriorities());
    }

    @Test
    public void testGetProjectCode() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setProjectCode("PCODE");
        assertEquals("PCODE", params.getProjectCode());
    }

    @Test
    public void testGetIssueType() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setIssueType("Bug");
        assertEquals("Bug", params.getIssueType());
    }

    @Test
    public void testGetAcceptanceCriteriaField() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setAcceptanceCriteriaField("Criteria");
        assertEquals("Criteria", params.getAcceptanceCriteriaField());
    }

    @Test
    public void testGetRelationship() {
        UserStoryGeneratorParams params = new UserStoryGeneratorParams();
        params.setRelationship("Related");
        assertEquals("Related", params.getRelationship());
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"existingUserStoriesJql\":\"JQL Query\",\"outputType\":\"trackerComment\"}";
        UserStoryGeneratorParams params = new UserStoryGeneratorParams(jsonString);
        assertEquals("JQL Query", params.getExistingUserStoriesJql());
        assertEquals("trackerComment", params.getOutputType());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("existingUserStoriesJql", "JQL Query");
        jsonObject.put("outputType", "trackerComment");
        UserStoryGeneratorParams params = new UserStoryGeneratorParams(jsonObject);
        assertEquals("JQL Query", params.getExistingUserStoriesJql());
        assertEquals("trackerComment", params.getOutputType());
    }
}