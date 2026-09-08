// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GitHub issue tool family in {@link GitHubIssues}.
 * All HTTP is mocked via a spy; no external API calls are made.
 */
public class GitHubIssuesTest {

    private GitHubIssues gitHub;
    private static final String BASE_PATH = "https://api.github.com";

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(BASE_PATH)
                .auth("token")
                .workspaceName("testWorkspace")
                .repoName("testRepo")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = new BasicGithub(config);
    }

    @Test
    public void testCreateIssuePostsExpectedUrlAndBody() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doReturn("{\"number\":42,\"title\":\"New bug\",\"state\":\"open\"}")
                .when(spy).post(any(GenericRequest.class));

        String result = spy.createIssue("acme", "widgets", "New bug", "It broke");

        assertTrue(result.contains("\"number\":42"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("New bug", body.getString("title"));
        assertEquals("It broke", body.getString("body"));
    }

    @Test
    public void testCreateIssueOmitsBlankBody() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doReturn("{\"number\":43}").when(spy).post(any(GenericRequest.class));

        spy.createIssue("acme", "widgets", "Title only", "   ");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("Title only", body.getString("title"));
        assertFalse(body.has("body"));
    }

    @Test
    public void testCloseIssuePatchesStateClosed() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doReturn("{\"number\":7,\"state\":\"closed\"}")
                .when(spy).patch(any(GenericRequest.class));

        String result = spy.closeIssue("acme", "widgets", 7);

        assertTrue(result.contains("\"state\":\"closed\""));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).patch(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues/7", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("closed", body.getString("state"));
    }

    @Test
    public void testAddLabelsPostsLabelArray() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doReturn("[{\"name\":\"bug\"},{\"name\":\"ai\"}]")
                .when(spy).post(any(GenericRequest.class));

        String result = spy.addLabels("acme", "widgets", 7, new String[]{"bug", "ai"});

        assertTrue(result.contains("bug"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues/7/labels", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals(2, body.getJSONArray("labels").length());
        assertEquals("bug", body.getJSONArray("labels").getString(0));
        assertEquals("ai", body.getJSONArray("labels").getString(1));
    }

    @Test
    public void testRemoveLabelDeletesEncodedLabelUrl() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doReturn("").when(spy).delete(any(GenericRequest.class));

        spy.removeLabel("acme", "widgets", 7, "help wanted");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).delete(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues/7/labels/help%20wanted",
                captor.getValue().url());
    }

    @Test
    public void testCreateCommentPostsToIssueCommentsEndpoint() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doReturn("{\"id\":1,\"body\":\"hello\"}").when(spy).post(any(GenericRequest.class));

        String result = spy.createComment("acme", "widgets", "7", "hello");

        assertTrue(result.contains("hello"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/issues/7/comments", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("hello", body.getString("body"));
    }

    @Test
    public void testPullRequestCommentsTolerates404OnPullsEndpoint() throws Exception {
        GitHubIssues spy = spy(gitHub);
        // Pulls endpoint 404s for plain issues; issues endpoint returns a comment.
        doThrow(new RestClient.RestClientException("not found", "{}", 404))
                .doReturn("[{\"id\":1,\"body\":\"issue comment\",\"created_at\":\"2024-01-01T00:00:00Z\",\"user\":{\"login\":\"octocat\"}}]")
                .when(spy).execute(any(GenericRequest.class));

        List<IComment> comments = spy.pullRequestComments("acme", "widgets", "7");

        assertEquals(1, comments.size());
        assertEquals("issue comment", comments.get(0).getBody());
    }

    @Test
    public void testPullRequestCommentsRethrowsNon404() throws Exception {
        GitHubIssues spy = spy(gitHub);
        doThrow(new RestClient.RestClientException("server error", "{}", 500))
                .when(spy).execute(any(GenericRequest.class));

        try {
            spy.pullRequestComments("acme", "widgets", "7");
            fail("Expected IOException for non-404 error");
        } catch (IOException e) {
            assertEquals(500, ((RestClient.RestClientException) e).getCode());
        }
    }
}
