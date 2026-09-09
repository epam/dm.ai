// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GitHub Actions tools in {@link GitHubActions}.
 * All HTTP is mocked via a spy; no external API calls are made.
 */
public class GitHubActionsTest {

    private GitHubActions gitHub;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth("token")
                .workspaceName("testWorkspace")
                .repoName("testRepo")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = new BasicGithub(config);
    }

    @Test
    public void testGetWorkflowsFetchesExpectedUrl() throws Exception {
        GitHubActions spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getWorkflows("acme", "widgets");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/actions/workflows", captor.getValue().url());
    }

    @Test
    public void testEnableWorkflowPutsExpectedUrl() throws Exception {
        GitHubActions spy = spy(gitHub);
        doReturn("").when(spy).put(any(GenericRequest.class));
        spy.enableWorkflow("acme", "widgets", 123456);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).put(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/actions/workflows/123456/enable",
                captor.getValue().url());
    }

    @Test
    public void testDisableWorkflowPutsExpectedUrl() throws Exception {
        GitHubActions spy = spy(gitHub);
        doReturn("").when(spy).put(any(GenericRequest.class));
        spy.disableWorkflow("acme", "widgets", 123456);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).put(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/actions/workflows/123456/disable",
                captor.getValue().url());
    }

    @Test
    public void testGetWorkflowRunsFetchesExpectedUrl() throws Exception {
        GitHubActions spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getWorkflowRuns("acme", "widgets");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/actions/runs", captor.getValue().url());
    }

    @Test
    public void testRerunWorkflowPostsExpectedUrl() throws Exception {
        GitHubActions spy = spy(gitHub);
        doReturn("").when(spy).post(any(GenericRequest.class));
        spy.rerunWorkflow("acme", "widgets", 1234567890);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/actions/runs/1234567890/rerun",
                captor.getValue().url());
    }

    @Test
    public void testGetCheckRunsFetchesExpectedUrl() throws Exception {
        GitHubActions spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getCheckRuns("acme", "widgets", "main");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/commits/main/check-runs",
                captor.getValue().url());
    }
}
