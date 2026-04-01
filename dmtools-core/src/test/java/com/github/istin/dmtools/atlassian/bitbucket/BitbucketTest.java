// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket;

import com.github.istin.dmtools.atlassian.bitbucket.model.*;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BitbucketTest {

    private Bitbucket bitbucket;
    private GenericRequest mockRequest;

    @Before
    public void setUp() throws IOException {
        bitbucket = Mockito.mock(Bitbucket.class, Mockito.CALLS_REAL_METHODS);
        mockRequest = Mockito.mock(GenericRequest.class);
    }


    @Test
    public void testPath() {
        bitbucket.setApiVersion(Bitbucket.ApiVersion.V1);
        String path = bitbucket.path("test/path");
        assertEquals(bitbucket.getBasePath() + "/rest/api/1.0/test/path", path);

        bitbucket.setApiVersion(Bitbucket.ApiVersion.V2);
        path = bitbucket.path("test/path");
        assertEquals(bitbucket.getBasePath() + "/2.0/test/path", path);
    }


    @Test
    public void testGetRepositories() throws IOException {
        try {
            bitbucket.getRepositories("namespace");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testAddPullRequestLabel() throws IOException {
        try {
            bitbucket.addPullRequestLabel("workspace", "repository", "1", "label");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }


    @Test
    public void testGetDefaultRepository() {
        try {
            bitbucket.getDefaultRepository();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetDefaultBranch() {
        try {
            bitbucket.getDefaultBranch();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetDefaultWorkspace() {
        try {
            bitbucket.getDefaultWorkspace();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

}