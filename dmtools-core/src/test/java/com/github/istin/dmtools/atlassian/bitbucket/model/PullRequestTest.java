// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.bitbucket.Bitbucket;
import com.github.istin.dmtools.atlassian.bitbucket.model.cloud.CloudPullRequest;
import com.github.istin.dmtools.atlassian.bitbucket.model.server.ServerPullRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class PullRequestTest {

    private PullRequest pullRequest;
    private JSONObject jsonObject;

    @Before
    public void setUp() {
        jsonObject = new JSONObject();
        jsonObject.put("title", "Test Title");
        jsonObject.put("description", "Test Description");
        jsonObject.put("id", 123);
        jsonObject.put("version", 1);

        pullRequest = Mockito.mock(PullRequest.class, Mockito.CALLS_REAL_METHODS);
        pullRequest.setJO(jsonObject);
    }

    @Test
    public void testGetTitle() {
        assertEquals("Test Title", pullRequest.getTitle());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Test Description", pullRequest.getDescription());
    }

    @Test
    public void testGetId() {
        assertEquals(Integer.valueOf(123), pullRequest.getId());
    }

    @Test
    public void testGetVersion() {
        assertEquals(Integer.valueOf(1), pullRequest.getVersion());
    }

    @Test
    public void testCreateServerPullRequest() {
        PullRequest pr = PullRequest.create(Bitbucket.ApiVersion.V1, jsonObject.toString());
        assertNotNull(pr);
        assertEquals(ServerPullRequest.class, pr.getClass());
    }

    @Test
    public void testCreateCloudPullRequest() {
        PullRequest pr = PullRequest.create(Bitbucket.ApiVersion.V2, jsonObject.toString());
        assertNotNull(pr);
        assertEquals(CloudPullRequest.class, pr.getClass());
    }

    @Test
    public void testCreateWithNullJson() {
        PullRequest pr = PullRequest.create(Bitbucket.ApiVersion.V2, null);
        assertNotNull(pr);
    }
}