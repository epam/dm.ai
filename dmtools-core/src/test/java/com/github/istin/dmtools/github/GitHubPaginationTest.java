// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Pagination parity for GitHub list tools (dmtools-dart#388). Mocked HTTP only. */
public class GitHubPaginationTest {

    private BasicGithub gitHub;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com").auth("t").workspaceName("o").repoName("r")
                .branchName("main").type(SourceCodeConfig.Type.GITHUB).build();
        gitHub = mock(BasicGithub.class, withSettings().useConstructor(config).defaultAnswer(CALLS_REAL_METHODS));
    }

    private static JSONArray items(int count, int offset, boolean merged) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < count; i++) {
            a.put(new JSONObject().put("id", offset + i).put("number", offset + i).put("name", "b" + (offset + i))
                    .put("title", "t").put("merged_at", merged ? "2024-01-01T00:00:00Z" : JSONObject.NULL));
        }
        return a;
    }

    private List<String> urls(int expectedCalls) throws IOException {
        ArgumentCaptor<GenericRequest> c = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(expectedCalls)).execute(c.capture());
        return c.getAllValues().stream().map(GenericRequest::url).toList();
    }

    @Test
    public void openPrsWalkAllPages() throws IOException {
        doReturn(items(100, 0, false).toString(), items(100, 100, false).toString(), items(3, 200, false).toString())
                .when(gitHub).execute(any(GenericRequest.class));
        List<IPullRequest> prs = gitHub.listPullRequests("o", "r", "opened");
        assertEquals(203, prs.size());
        List<String> u = urls(3);
        assertTrue(u.get(0).contains("state=open") && u.get(0).contains("per_page=100&page=1"));
        assertTrue(u.get(2).endsWith("page=3"));
    }

    @Test
    public void openPrsEmptyBodyGivesEmptyList() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));
        assertTrue(gitHub.listPullRequests("o", "r", "open").isEmpty());
        urls(1);
    }

    @Test
    public void closedPrsStaySinglePage() throws IOException {
        doReturn(items(100, 0, false).toString()).when(gitHub).execute(any(GenericRequest.class));
        assertEquals(100, gitHub.listPullRequests("o", "r", "closed").size());
        urls(1);
    }

    @Test
    public void mergedFilterStillAppliedOnSinglePage() throws IOException {
        JSONArray page = items(60, 0, true);
        items(40, 60, false).forEach(page::put);
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));
        assertEquals(60, gitHub.listPullRequests("o", "r", "merged").size());
        assertTrue(urls(1).get(0).contains("state=closed"));
    }

    @Test
    public void reviewsShortFirstPageIsSingleRequestUnchanged() throws IOException {
        String body = items(2, 0, false).toString();
        doReturn(body).when(gitHub).execute(any(GenericRequest.class));
        assertEquals(body, gitHub.listPullRequestReviews("o", "r", "5"));
        assertTrue(urls(1).get(0).contains("pulls/5/reviews?per_page=100&page=1"));
    }

    @Test
    public void reviewsNullBodyGivesEmptyArray() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));
        assertEquals("[]", gitHub.listPullRequestReviews("o", "r", "5"));
    }

    @Test
    public void reviewsLaterPageFailureReturnsCollected() throws IOException {
        when(gitHub.execute(any(GenericRequest.class)))
                .thenReturn(items(100, 0, false).toString())
                .thenThrow(new IOException("boom"));
        assertEquals(100, new JSONArray(gitHub.listPullRequestReviews("o", "r", "5")).length());
        urls(2);
    }

    @Test
    public void branchesWalkAllPagesAndStopOnEmpty() throws IOException {
        doReturn(items(100, 0, false).toString(), "[]").when(gitHub).execute(any(GenericRequest.class));
        assertEquals(100, new JSONArray(gitHub.listBranches("o", "r")).length());
        urls(2);
    }

    @Test
    public void getBranchesUsesSharedPager() throws IOException {
        doReturn(items(100, 0, false).toString(), items(1, 100, false).toString())
                .when(gitHub).execute(any(GenericRequest.class));
        List<ITag> tags = gitHub.getBranches("o", "r");
        assertEquals(101, tags.size());
    }

    @Test
    public void getBranchesNullBody() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));
        assertTrue(gitHub.getBranches("o", "r").isEmpty());
    }

    @Test
    public void branchesErrorObjectPassesThrough() throws IOException {
        String err = "{\"message\":\"Not Found\"}";
        doReturn(err).when(gitHub).execute(any(GenericRequest.class));
        assertEquals(err, gitHub.listBranches("o", "r"));
        urls(1);
    }

    @Test
    public void jobsMergeArraysKeepTotalCount() throws IOException {
        String p1 = new JSONObject().put("total_count", 150).put("jobs", items(100, 0, false)).toString();
        String p2 = new JSONObject().put("total_count", 999).put("jobs", items(50, 100, false)).toString();
        doReturn(p1, p2).when(gitHub).execute(any(GenericRequest.class));
        JSONObject merged = new JSONObject(gitHub.getWorkflowRunJobs("o", "r", "42"));
        assertEquals(150, merged.getInt("total_count"));
        assertEquals(150, merged.getJSONArray("jobs").length());
        assertTrue(urls(2).get(1).contains("runs/42/jobs?per_page=100&page=2"));
    }

    @Test
    public void jobsNonJsonAndMissingArrayPassThrough() throws IOException {
        doReturn("not json").when(gitHub).execute(any(GenericRequest.class));
        assertEquals("not json", gitHub.getWorkflowRunJobs("o", "r", "1"));
        doReturn("{\"x\":1}").when(gitHub).execute(any(GenericRequest.class));
        assertEquals("{\"x\":1}", gitHub.getWorkflowRunJobs("o", "r", "1"));
        doReturn("").when(gitHub).execute(any(GenericRequest.class));
        assertEquals("", gitHub.getWorkflowRunJobs("o", "r", "1"));
    }

    @Test
    public void jobsLaterPageWithoutArrayStops() throws IOException {
        String p1 = new JSONObject().put("jobs", items(100, 0, false)).toString();
        doReturn(p1, "{}").when(gitHub).execute(any(GenericRequest.class));
        JSONObject merged = new JSONObject(gitHub.getWorkflowRunJobs("o", "r", "1"));
        assertEquals(100, merged.getJSONArray("jobs").length());
        assertTrue(!merged.has("total_count"));
    }

    @Test
    public void arrayNonJsonFirstPagePassesThrough() throws IOException {
        doReturn("oops").when(gitHub).execute(any(GenericRequest.class));
        assertEquals("oops", gitHub.listPullRequestReviews("o", "r", "1"));
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));
        assertNull(gitHub.listBranches("o", "r"));
    }
}
