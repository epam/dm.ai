// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model.cloud;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class CloudPullRequestTest {

    private JSONObject mockJsonObject;
    private CloudPullRequest cloudPullRequest;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class, RETURNS_DEEP_STUBS);
        cloudPullRequest = new CloudPullRequest(mockJsonObject);
    }


    @Test
    public void testGetTargetBranchName() {
        when(mockJsonObject.getJSONObject("destination").getJSONObject("branch").getString("name"))
                .thenReturn("target-branch");

        String targetBranchName = cloudPullRequest.getTargetBranchName();

        assertEquals("target-branch", targetBranchName);
    }

    @Test
    public void testGetSourceBranchName() {
        when(mockJsonObject.getJSONObject("source").getJSONObject("branch").getString("name"))
                .thenReturn("source-branch");

        String sourceBranchName = cloudPullRequest.getSourceBranchName();

        assertEquals("source-branch", sourceBranchName);
    }

}