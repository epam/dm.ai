// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.bitbucket.Bitbucket;
import com.github.istin.dmtools.common.model.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class BitbucketResultTest {

    private BitbucketResult bitbucketResult;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        bitbucketResult = new BitbucketResult(mockJson);
    }

    @Test
    public void testGetValues_V1() {
        Bitbucket.ApiVersion apiVersion = Bitbucket.ApiVersion.V1;
        List<? extends PullRequest> values = bitbucketResult.getValues(apiVersion);
        assertNotNull(values);
    }

    @Test
    public void testGetValues_V2() {
        Bitbucket.ApiVersion apiVersion = Bitbucket.ApiVersion.V2;
        List<? extends PullRequest> values = bitbucketResult.getValues(apiVersion);
        assertNotNull(values);
    }

    @Test
    public void testGetActivities() {
        List<IActivity> activities = bitbucketResult.getActivities();
        assertNotNull(activities);
    }

    @Test
    public void testGetTasks() {
        List<ITask> tasks = bitbucketResult.getTasks();
        assertNotNull(tasks);
    }

    @Test
    public void testGetDiffs() {
        List<Diff> diffs = bitbucketResult.getDiffs();
        assertNotNull(diffs);
    }

    @Test
    public void testGetRepositories() {
        List<Repository> repositories = bitbucketResult.getRepositories();
        assertNotNull(repositories);
    }

    @Test
    public void testGetChanges() {
        List<IChange> changes = bitbucketResult.getChanges();
        assertNotNull(changes);
    }

    @Test
    public void testGetFiles() {
        List<IFile> files = bitbucketResult.getFiles();
        assertNotNull(files);
    }

    @Test
    public void testGetComments() {
        List<IComment> comments = bitbucketResult.getComments();
        assertNotNull(comments);
    }

}