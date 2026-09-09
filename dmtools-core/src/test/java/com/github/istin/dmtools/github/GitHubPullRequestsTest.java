// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GitHub pull-request lifecycle tools in {@link GitHubPullRequests}.
 * All HTTP is mocked via a spy; no external API calls are made.
 */
public class GitHubPullRequestsTest {

    private GitHubPullRequests gitHub;

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
    public void testCreatePrPostsExpectedUrlAndBody() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{\"number\":74}").when(spy).post(any(GenericRequest.class));

        spy.createPr("acme", "widgets", "Add feature X", "feature/x", "main");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/pulls", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("Add feature X", body.getString("title"));
        assertEquals("feature/x", body.getString("head"));
        assertEquals("main", body.getString("base"));
    }

    @Test
    public void testClosePrPatchesStateClosed() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{}").when(spy).patch(any(GenericRequest.class));

        spy.closePr("acme", "widgets", 74);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).patch(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/pulls/74", captor.getValue().url());
        assertEquals("closed", new JSONObject(captor.getValue().getBody()).getString("state"));
    }

    @Test
    public void testReopenPrPatchesStateOpen() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{}").when(spy).patch(any(GenericRequest.class));

        spy.reopenPr("acme", "widgets", 74);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).patch(captor.capture());
        assertEquals("open", new JSONObject(captor.getValue().getBody()).getString("state"));
    }

    @Test
    public void testUpdatePrSendsOnlyProvidedFields() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{}").when(spy).patch(any(GenericRequest.class));

        spy.updatePr("acme", "widgets", 74, "New title", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).patch(captor.capture());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("New title", body.getString("title"));
        assertFalse(body.has("body"));
    }

    @Test
    public void testGetPrFilesFetchesExpectedUrl() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("[]").when(spy).execute(any(GenericRequest.class));

        spy.getPrFiles("acme", "widgets", 74);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/pulls/74/files", captor.getValue().url());
    }

    @Test
    public void testRequestReviewersPostsReviewerArray() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{}").when(spy).post(any(GenericRequest.class));

        spy.requestReviewers("acme", "widgets", 74, new String[]{"octocat", "hubot"});

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/pulls/74/requested_reviewers",
                captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals(2, body.getJSONArray("reviewers").length());
        assertEquals("octocat", body.getJSONArray("reviewers").getString(0));
    }

    @Test
    public void testCreateReviewPostsBodyAndEvent() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{}").when(spy).post(any(GenericRequest.class));

        spy.createReview("acme", "widgets", 74, "Looks good!", "APPROVE");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/pulls/74/reviews", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("Looks good!", body.getString("body"));
        assertEquals("APPROVE", body.getString("event"));
    }

    @Test
    public void testDismissReviewPutsDismissal() throws Exception {
        GitHubPullRequests spy = spy(gitHub);
        doReturn("{}").when(spy).put(any(GenericRequest.class));

        spy.dismissReview("acme", "widgets", 74, 123456, "Outdated");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).put(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/pulls/74/reviews/123456/dismissals",
                captor.getValue().url());
        assertEquals("Outdated", new JSONObject(captor.getValue().getBody()).getString("message"));
    }
}
