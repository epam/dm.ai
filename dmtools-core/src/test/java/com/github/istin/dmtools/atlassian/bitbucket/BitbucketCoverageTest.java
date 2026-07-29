// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket;

import com.github.istin.dmtools.atlassian.bitbucket.model.BitbucketResult;
import com.github.istin.dmtools.atlassian.bitbucket.model.server.ServerPullRequest;
import com.github.istin.dmtools.common.model.IBody;
import com.github.istin.dmtools.common.model.IChange;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IFile;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.model.ITask;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Additional unit tests for {@link Bitbucket} aimed at covering its public API surface
 * (pull requests, comments, tasks, diffs, tags, branches, commits, files) for both
 * API V1 (server) and V2 (cloud) with a fully mocked HTTP layer - no real network calls.
 */
public class BitbucketCoverageTest {

    private static final String BASE_PATH = "https://api.bitbucket.org";
    private static final String WORKSPACE = "testWorkspace";
    private static final String REPOSITORY = "testRepo";
    private static final String PR_ID = "42";

    private Bitbucket bitbucket;

    @Before
    public void setUp() throws IOException {
        bitbucket = mock(Bitbucket.class,
                withSettings().useConstructor(BASE_PATH, "test-auth").defaultAnswer(CALLS_REAL_METHODS));
    }

    private void useV1() {
        bitbucket.setApiVersion(Bitbucket.ApiVersion.V1);
    }

    private void useV2() {
        bitbucket.setApiVersion(Bitbucket.ApiVersion.V2);
    }

    private GenericRequest captureLastExecute() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(bitbucket, atLeastOnce()).execute(captor.capture());
        List<GenericRequest> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    private static String prPageJson(String... ids) {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                values.append(",");
            }
            values.append("{\"id\":").append(ids[i])
                    .append(",\"title\":\"PR ").append(ids[i]).append("\"")
                    .append(",\"createdDate\":1000}");
        }
        return "{\"values\":[" + values + "]}";
    }

    // ── path building / accessors ────────────────────────────────────────────

    @Test
    public void testPathV1() {
        useV1();
        assertEquals(BASE_PATH + "/rest/api/1.0/some/path", bitbucket.path("some/path"));
    }

    @Test
    public void testPathV2() {
        useV2();
        assertEquals(BASE_PATH + "/2.0/some/path", bitbucket.path("some/path"));
    }

    @Test
    public void testGetPullRequestUrl() {
        assertEquals("https://bitbucket.org/" + WORKSPACE + "/" + REPOSITORY + "/pull-requests/7",
                bitbucket.getPullRequestUrl(WORKSPACE, REPOSITORY, "7"));
    }

    // ── connection test ──────────────────────────────────────────────────────

    @Test
    public void testTestConnectionSuccessV1() throws IOException {
        useV1();
        doReturn("{\"displayName\":\"John Doe\"}").when(bitbucket).execute(any(GenericRequest.class));

        Map<String, Object> result = bitbucket.testConnection();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("John Doe", result.get("user"));
        assertEquals(BASE_PATH + "/rest/api/1.0/users/~me", captureLastExecute().url());
    }

    @Test
    public void testTestConnectionSuccessV2() throws IOException {
        useV2();
        doReturn("{\"nickname\":\"jdoe\"}").when(bitbucket).execute(any(GenericRequest.class));

        Map<String, Object> result = bitbucket.testConnection();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("jdoe", result.get("user"));
        assertEquals(BASE_PATH + "/2.0/user", captureLastExecute().url());
    }

    @Test
    public void testTestConnectionUnexpectedFormat() throws IOException {
        doReturn("{\"something\":\"else\"}").when(bitbucket).execute(any(GenericRequest.class));

        Map<String, Object> result = bitbucket.testConnection();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("Unexpected response format from Bitbucket API", result.get("message"));
    }

    @Test
    public void testTestConnectionEmptyResponse() throws IOException {
        doReturn("").when(bitbucket).execute(any(GenericRequest.class));

        Map<String, Object> result = bitbucket.testConnection();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("Empty response from Bitbucket API", result.get("message"));
    }

    @Test
    public void testTestConnectionException() throws IOException {
        doThrow(new IOException("boom")).when(bitbucket).execute(any(GenericRequest.class));

        Map<String, Object> result = bitbucket.testConnection();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(String.valueOf(result.get("message")).contains("boom"));
        assertEquals("IOException", result.get("error"));
    }

    // ── pull requests list ───────────────────────────────────────────────────

    @Test
    public void testPullRequestsV1SinglePage() throws IOException {
        useV1();
        doReturn(prPageJson("1", "2")).when(bitbucket).execute(any(GenericRequest.class));

        List<IPullRequest> result = bitbucket.pullRequests(WORKSPACE, REPOSITORY, "OPEN", false, null);

        assertEquals(2, result.size());
        GenericRequest request = captureLastExecute();
        assertTrue(request.url().startsWith(
                BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY + "/pull-requests/?state=OPEN"));
        assertTrue(request.url().contains("start=0"));
        assertTrue(request.url().contains("limit=50"));
    }

    @Test
    public void testPullRequestsV2SinglePage() throws IOException {
        useV2();
        doReturn(prPageJson("3")).when(bitbucket).execute(any(GenericRequest.class));

        List<IPullRequest> result = bitbucket.pullRequests(WORKSPACE, REPOSITORY, "open", false, null);

        assertEquals(1, result.size());
        GenericRequest request = captureLastExecute();
        assertTrue(request.url().startsWith(
                BASE_PATH + "/2.0/repositories/" + WORKSPACE + "/" + REPOSITORY + "/pullrequests/?state=OPEN"));
        assertTrue(request.url().contains("page=1"));
        assertTrue(request.url().contains("pagelen=50"));
    }

    @Test
    public void testPullRequestsNullResponse() throws IOException {
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<IPullRequest> result = bitbucket.pullRequests(WORKSPACE, REPOSITORY, "OPEN", true, null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testPullRequestsV1AllPagesWithStartDateBreak() throws IOException {
        useV1();
        bitbucket.setDefaultLimit(2);
        doReturn(prPageJson("1"), prPageJson("2"))
                .when(bitbucket).execute(any(GenericRequest.class));
        Calendar startDate = Calendar.getInstance(); // createdDate(1000) is far in the past -> break

        List<IPullRequest> result = bitbucket.pullRequests(WORKSPACE, REPOSITORY, "OPEN", true, startDate);

        assertEquals(2, result.size());
        verify(bitbucket, times(2)).execute(any(GenericRequest.class));
        GenericRequest secondPage = captureLastExecute();
        assertTrue(secondPage.url().contains("start=2"));
        assertTrue(secondPage.url().contains("limit=2"));
    }

    @Test
    public void testPullRequestsV2NullResponseOnSecondPage() throws IOException {
        useV2();
        doReturn(prPageJson("1"), null)
                .when(bitbucket).execute(any(GenericRequest.class));

        List<IPullRequest> result = bitbucket.pullRequests(WORKSPACE, REPOSITORY, "open", true, null);

        assertEquals(1, result.size());
        verify(bitbucket, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testPullRequestsV2AllPagesUntilEmpty() throws IOException {
        useV2();
        doReturn(prPageJson("1"), "{\"values\":[]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        List<IPullRequest> result = bitbucket.pullRequests(WORKSPACE, REPOSITORY, "merged", true, null);

        assertEquals(1, result.size());
        verify(bitbucket, times(2)).execute(any(GenericRequest.class));
        assertTrue(captureLastExecute().url().contains("page=2"));
    }

    // ── single pull request ──────────────────────────────────────────────────

    @Test
    public void testPullRequestV1() throws IOException {
        useV1();
        doReturn("{\"id\":42,\"title\":\"My PR\"}").when(bitbucket).execute(any(GenericRequest.class));

        IPullRequest pr = bitbucket.pullRequest(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals(Integer.valueOf(42), pr.getId());
        assertEquals("My PR", pr.getTitle());
        assertEquals(BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY
                + "/pull-requests/" + PR_ID, captureLastExecute().url());
    }

    @Test
    public void testPullRequestV2() throws IOException {
        useV2();
        doReturn("{\"id\":42,\"title\":\"Cloud PR\"}").when(bitbucket).execute(any(GenericRequest.class));

        IPullRequest pr = bitbucket.pullRequest(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals("Cloud PR", pr.getTitle());
        assertEquals(BASE_PATH + "/2.0/repositories/" + WORKSPACE + "/" + REPOSITORY
                + "/pullrequests/" + PR_ID, captureLastExecute().url());
    }

    // ── pull request diff stats ──────────────────────────────────────────────

    @Test
    public void testGetPullRequestDiff() throws IOException {
        useV2();
        doReturn("{\"values\":[{\"lines_added\":5,\"lines_removed\":2}," +
                "{\"lines_added\":3,\"lines_removed\":4}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        IDiffStats diffStats = bitbucket.getPullRequestDiff(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals(14, diffStats.getStats().getTotal());
        assertEquals(8, diffStats.getStats().getAdditions());
        assertEquals(6, diffStats.getStats().getDeletions());
        assertEquals(2, diffStats.getChanges().size());
        assertTrue(captureLastExecute().url().endsWith(
                "/repositories/" + WORKSPACE + "/" + REPOSITORY + "/pullrequests/" + PR_ID + "/diffstat"));
    }

    @Test
    public void testGetPullRequestDiffNullResponse() throws IOException {
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        IDiffStats diffStats = bitbucket.getPullRequestDiff(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(diffStats);
        assertEquals(0, diffStats.getStats().getTotal());
    }

    // ── comments ─────────────────────────────────────────────────────────────

    @Test
    public void testPullRequestCommentsV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":1,\"content\":{\"raw\":\"hello\"}}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        List<IComment> comments = bitbucket.pullRequestComments(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals(1, comments.size());
        assertEquals(BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY
                + "/pull-requests/" + PR_ID + "/comments", captureLastExecute().url());
    }

    @Test
    public void testPullRequestCommentsV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<IComment> comments = bitbucket.pullRequestComments(WORKSPACE, REPOSITORY, PR_ID);

        assertTrue(comments.isEmpty());
    }

    @Test
    public void testAddPullRequestCommentV1() throws IOException {
        useV1();
        doReturn("{\"id\":100}").when(bitbucket).post(any(GenericRequest.class));

        String response = bitbucket.addPullRequestComment(WORKSPACE, REPOSITORY, PR_ID, "some comment");

        assertEquals("{\"id\":100}", response);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(bitbucket).post(captor.capture());
        assertTrue(captor.getValue().url().contains("/pull-requests/" + PR_ID + "/comments"));
    }

    @Test
    public void testAddPullRequestCommentV2() throws IOException {
        useV2();
        doReturn("{\"id\":101}").when(bitbucket).post(any(GenericRequest.class));

        String response = bitbucket.addPullRequestComment(WORKSPACE, REPOSITORY, PR_ID, "cloud comment");

        assertEquals("{\"id\":101}", response);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(bitbucket).post(captor.capture());
        assertTrue(captor.getValue().url().contains("/pullrequests/" + PR_ID + "/comments"));
    }

    @Test
    public void testCommitComments() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":5}]}").when(bitbucket).execute(any(GenericRequest.class));

        JSONModel result = bitbucket.commitComments(WORKSPACE, REPOSITORY, "abc123");

        assertNotNull(result.getJSONObject().optJSONArray("values"));
        assertTrue(captureLastExecute().url().contains("/commits/abc123/comments"));
    }

    @Test
    public void testCommitCommentsNullResponse() throws IOException {
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        JSONModel result = bitbucket.commitComments(WORKSPACE, REPOSITORY, "abc123");

        assertNotNull(result);
    }

    // ── activities ───────────────────────────────────────────────────────────

    @Test
    public void testPullRequestActivitiesV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":1}]}").when(bitbucket).execute(any(GenericRequest.class));

        List<?> activities = bitbucket.pullRequestActivities(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals(1, activities.size());
        GenericRequest request = captureLastExecute();
        assertEquals(BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY
                + "/pull-requests/" + PR_ID + "/activities", request.url());
    }

    @Test
    public void testPullRequestActivitiesV2AddsPagingParams() throws IOException {
        useV2();
        doReturn("{\"values\":[{\"id\":1}]}").when(bitbucket).execute(any(GenericRequest.class));

        List<?> activities = bitbucket.pullRequestActivities(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals(1, activities.size());
        GenericRequest request = captureLastExecute();
        assertTrue(request.url().startsWith(BASE_PATH + "/2.0/repositories/" + WORKSPACE + "/" + REPOSITORY
                + "/pullrequests/" + PR_ID + "/activity"));
        assertTrue(request.url().contains("page=1"));
        assertTrue(request.url().contains("pagelen=50"));
    }

    @Test
    public void testPullRequestActivitiesNullResponse() throws IOException {
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<?> activities = bitbucket.pullRequestActivities(WORKSPACE, REPOSITORY, PR_ID);

        assertTrue(activities.isEmpty());
    }

    // ── repositories ─────────────────────────────────────────────────────────

    @Test
    public void testRepositoriesV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"slug\":\"repo1\"}]}").when(bitbucket).execute(any(GenericRequest.class));

        BitbucketResult result = bitbucket.repositories(WORKSPACE);

        assertEquals(1, result.getRepositories().size());
        assertEquals(BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/?limit=50",
                captureLastExecute().url());
    }

    @Test
    public void testRepositoriesV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        BitbucketResult result = bitbucket.repositories(WORKSPACE);

        assertNotNull(result);
        assertTrue(captureLastExecute().url().startsWith(BASE_PATH + "/2.0/repositories/" + WORKSPACE));
    }

    // ── tasks ────────────────────────────────────────────────────────────────

    @Test
    public void testPullRequestTasksV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"text\":\"task one\",\"state\":\"OPEN\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        List<ITask> tasks = bitbucket.pullRequestTasks(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals(1, tasks.size());
        assertEquals(BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY
                + "/pull-requests/" + PR_ID + "/tasks", captureLastExecute().url());
    }

    @Test
    public void testPullRequestTasksV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<ITask> tasks = bitbucket.pullRequestTasks(WORKSPACE, REPOSITORY, PR_ID);

        assertTrue(tasks.isEmpty());
        assertTrue(captureLastExecute().url().contains("/pullrequests/" + PR_ID + "/tasks"));
    }

    @Test
    public void testAddTaskV1() throws IOException {
        useV1();
        doReturn("{\"id\":7}").when(bitbucket).post(any(GenericRequest.class));

        String response = bitbucket.addTask(55, "new task");

        assertEquals("{\"id\":7}", response);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(bitbucket).post(captor.capture());
        assertEquals(BASE_PATH + "/rest/api/1.0/tasks", captor.getValue().url());
    }

    @Test
    public void testAddTaskV2() throws IOException {
        useV2();
        doReturn("{\"id\":8}").when(bitbucket).post(any(GenericRequest.class));

        String response = bitbucket.addTask(56, "cloud task");

        assertEquals("{\"id\":8}", response);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(bitbucket).post(captor.capture());
        assertEquals(BASE_PATH + "/2.0/tasks", captor.getValue().url());
    }

    @Test
    public void testCreatePullRequestCommentAndTaskIfNotExistsSkipsWhenCommentExists() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"text\":\"bot comment body\",\"state\":\"OPEN\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        String result = bitbucket.createPullRequestCommentAndTaskIfNotExists(
                WORKSPACE, REPOSITORY, PR_ID, "bot comment", "task text");

        assertEquals("", result);
    }

    @Test
    public void testCreatePullRequestCommentAndTaskIfNotExistsCreatesWhenMissing() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"text\":\"other task\",\"state\":\"OPEN\"}]}",
                "{\"values\":[{\"text\":\"other task\",\"state\":\"OPEN\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));
        doReturn("{\"id\":42}", "{\"id\":9}").when(bitbucket).post(any(GenericRequest.class));

        String result = bitbucket.createPullRequestCommentAndTaskIfNotExists(
                WORKSPACE, REPOSITORY, PR_ID, "fresh comment", "fresh task");

        assertEquals("{\"id\":9}", result);
        verify(bitbucket, times(2)).post(any(GenericRequest.class));
    }

    @Test
    public void testCreatePullRequestCommentAndTaskSkipsDuplicateOpenTask() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"text\":\"duplicate task\",\"state\":\"OPEN\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        String result = bitbucket.createPullRequestCommentAndTask(
                WORKSPACE, REPOSITORY, PR_ID, "comment", "duplicate task");

        assertEquals("", result);
    }

    @Test
    public void testCreatePullRequestCommentAndTaskSkipsDuplicateUnresolvedTaskV2() throws IOException {
        useV2();
        doReturn("{\"values\":[{\"content\":{\"raw\":\"duplicate task\"},\"state\":\"UNRESOLVED\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        String result = bitbucket.createPullRequestCommentAndTask(
                WORKSPACE, REPOSITORY, PR_ID, "comment", "duplicate task");

        assertEquals("", result);
    }

    @Test
    public void testCreatePullRequestCommentAndTaskCreatesForResolvedTask() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"text\":\"old task\",\"state\":\"RESOLVED\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));
        doReturn("{\"id\":42}", "{\"id\":10}").when(bitbucket).post(any(GenericRequest.class));

        String result = bitbucket.createPullRequestCommentAndTask(
                WORKSPACE, REPOSITORY, PR_ID, "comment", "old task");

        assertEquals("{\"id\":10}", result);
        verify(bitbucket, times(2)).post(any(GenericRequest.class));
    }

    // ── rename pull request ──────────────────────────────────────────────────

    @Test
    public void testRenamePullRequestNoChange() throws IOException {
        ServerPullRequest pr = new ServerPullRequest(new JSONObject("{\"id\":7,\"title\":\"Same Title\"}"));

        String result = bitbucket.renamePullRequest(WORKSPACE, REPOSITORY, pr, "same title");

        assertEquals("", result);
    }

    @Test
    public void testRenamePullRequestUpdatesTitle() throws IOException {
        useV1();
        ServerPullRequest pr = new ServerPullRequest(new JSONObject("{\"id\":7,\"title\":\"Old Title\"}"));
        doReturn("{\"id\":7,\"title\":\"New Title\"}").when(bitbucket).put(any(GenericRequest.class));

        String result = bitbucket.renamePullRequest(WORKSPACE, REPOSITORY, pr, "New Title");

        assertEquals("{\"id\":7,\"title\":\"New Title\"}", result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(bitbucket).put(captor.capture());
        assertTrue(captor.getValue().url().endsWith("/pull-requests/7"));
    }

    @Test
    public void testRenamePullRequestKeepsWipPrefix() throws IOException {
        useV2();
        ServerPullRequest pr = new ServerPullRequest(new JSONObject("{\"id\":7,\"title\":\"[WIP] Old Title\"}"));
        doReturn("{\"id\":7}").when(bitbucket).put(any(GenericRequest.class));

        String result = bitbucket.renamePullRequest(WORKSPACE, REPOSITORY, pr, "New Title");

        assertEquals("{\"id\":7}", result);
        verify(bitbucket).put(any(GenericRequest.class));
    }

    // ── tags / branches / commits ────────────────────────────────────────────

    @Test
    public void testGetTagsV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":\"refs/tags/v1\"}]}").when(bitbucket).execute(any(GenericRequest.class));

        List<ITag> tags = bitbucket.getTags(WORKSPACE, REPOSITORY);

        assertEquals(1, tags.size());
        assertEquals(BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY + "/tags?limit=50",
                captureLastExecute().url());
    }

    @Test
    public void testGetTagsV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<ITag> tags = bitbucket.getTags(WORKSPACE, REPOSITORY);

        assertTrue(tags.isEmpty());
        assertEquals(BASE_PATH + "/2.0/repositories/" + WORKSPACE + "/" + REPOSITORY + "/refs/tags?pagelen=50",
                captureLastExecute().url());
    }

    @Test
    public void testGetBranchesV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":\"refs/heads/main\"}]}").when(bitbucket).execute(any(GenericRequest.class));

        List<ITag> branches = bitbucket.getBranches(WORKSPACE, REPOSITORY);

        assertEquals(1, branches.size());
        GenericRequest request = captureLastExecute();
        assertTrue(request.url().startsWith(
                BASE_PATH + "/rest/api/1.0/projects/" + WORKSPACE + "/repos/" + REPOSITORY + "/branches"));
        assertTrue(request.url().contains("orderBy=MODIFICATION"));
    }

    @Test
    public void testGetBranchesV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<ITag> branches = bitbucket.getBranches(WORKSPACE, REPOSITORY);

        assertTrue(branches.isEmpty());
        assertTrue(captureLastExecute().url().startsWith(
                BASE_PATH + "/2.0/repositories/" + WORKSPACE + "/" + REPOSITORY + "/refs/branches"));
    }

    @Test
    public void testGetCommitsBetweenV1() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":\"commit1\"}]}").when(bitbucket).execute(any(GenericRequest.class));

        List<ICommit> commits = bitbucket.getCommitsBetween(WORKSPACE, REPOSITORY, "fromSha", "toSha");

        assertEquals(1, commits.size());
        assertTrue(captureLastExecute().url().contains("/compare/commits?from=fromSha&to=toSha"));
    }

    @Test
    public void testGetCommitsBetweenV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<ICommit> commits = bitbucket.getCommitsBetween(WORKSPACE, REPOSITORY, "fromSha", "toSha");

        assertTrue(commits.isEmpty());
        assertTrue(captureLastExecute().url().contains("/commits?include=fromSha&exclude=toSha"));
    }

    @Test
    public void testGetCommitsFromBranchV1PagesUntilEmpty() throws IOException {
        useV1();
        doReturn("{\"values\":[{\"id\":\"c1\"}]}", "{\"values\":[]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        List<ICommit> commits = bitbucket.getCommitsFromBranch(WORKSPACE, REPOSITORY, "main", null, null);

        assertEquals(1, commits.size());
        verify(bitbucket, times(2)).execute(any(GenericRequest.class));
        GenericRequest firstPage = captureLastExecute();
        assertTrue(firstPage.url().contains("until=refs/heads/main"));
        assertTrue(firstPage.url().contains("merges=include"));
    }

    @Test
    public void testGetCommitsFromBranchV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        List<ICommit> commits = bitbucket.getCommitsFromBranch(WORKSPACE, REPOSITORY, "main", null, null);

        assertTrue(commits.isEmpty());
        assertTrue(captureLastExecute().url().contains("commits?include=main"));
    }

    @Test
    public void testPerformCommitsFromBranchIteratesUntilStable() throws Exception {
        useV1();
        doReturn("{\"values\":[{\"id\":\"c1\"}]}", "{\"values\":[{\"id\":\"c2\"}]}", "{\"values\":[]}")
                .when(bitbucket).execute(any(GenericRequest.class));
        StringBuilder performed = new StringBuilder();

        bitbucket.performCommitsFromBranch(WORKSPACE, REPOSITORY, "main",
                commit -> {
                    performed.append(commit.getId()).append(";");
                    return false;
                });

        assertEquals("c1;c2;", performed.toString());
        verify(bitbucket, times(3)).execute(any(GenericRequest.class));
    }

    @Test
    public void testPerformCommitsFromBranchStopsWhenPerformerReturnsTrue() throws Exception {
        useV1();
        doReturn("{\"values\":[{\"id\":\"c1\"},{\"id\":\"c2\"}]}", "{\"values\":[]}")
                .when(bitbucket).execute(any(GenericRequest.class));
        StringBuilder performed = new StringBuilder();

        bitbucket.performCommitsFromBranch(WORKSPACE, REPOSITORY, "main",
                commit -> {
                    performed.append(commit.getId()).append(";");
                    return true;
                });

        assertEquals("c1;", performed.toString());
    }

    @Test
    public void testPerformCommitsFromBranchNullResponse() throws Exception {
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        bitbucket.performCommitsFromBranch(WORKSPACE, REPOSITORY, "main", commit -> false);

        verify(bitbucket, times(1)).execute(any(GenericRequest.class));
    }

    // ── diffs ────────────────────────────────────────────────────────────────

    @Test
    public void testGetCommitDiffStatV1Unsupported() {
        useV1();
        assertThrows(UnsupportedOperationException.class,
                () -> bitbucket.getCommitDiffStat(WORKSPACE, REPOSITORY, "abc"));
    }

    @Test
    public void testGetCommitDiffStatV2() throws IOException {
        useV2();
        doReturn("{\"values\":[{\"lines_added\":3,\"lines_removed\":1}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        IDiffStats diffStats = bitbucket.getCommitDiffStat(WORKSPACE, REPOSITORY, "abc");

        List<IChange> changes = diffStats.getChanges();
        assertEquals(1, changes.size());
        // getStats() is intentionally not implemented for commit diff stats
        assertNull(diffStats.getStats());
        assertTrue(captureLastExecute().url().contains("/diffstat/abc"));
    }

    @Test
    public void testGetCommitDiffStatV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        IDiffStats diffStats = bitbucket.getCommitDiffStat(WORKSPACE, REPOSITORY, "abc");

        assertEquals(0, diffStats.getStats().getTotal());
    }

    @Test
    public void testGetCommitDiffStatV2ExceptionReturnsEmpty() throws IOException {
        useV2();
        doThrow(new IOException("boom")).when(bitbucket).execute(any(GenericRequest.class));

        IDiffStats diffStats = bitbucket.getCommitDiffStat(WORKSPACE, REPOSITORY, "abc");

        assertEquals(0, diffStats.getStats().getTotal());
    }

    @Test
    public void testGetCommitDiffV1() throws IOException {
        useV1();
        doReturn("diff content v1").when(bitbucket).execute(any(GenericRequest.class));

        IBody body = bitbucket.getCommitDiff(WORKSPACE, REPOSITORY, "abc");

        assertEquals("diff content v1", body.getBody());
        assertTrue(captureLastExecute().url().contains("/commits/abc/diff"));
    }

    @Test
    public void testGetCommitDiffV2NullResponse() throws IOException {
        useV2();
        doReturn(null).when(bitbucket).execute(any(GenericRequest.class));

        IBody body = bitbucket.getCommitDiff(WORKSPACE, REPOSITORY, "abc");

        assertEquals("", body.getBody());
        assertTrue(captureLastExecute().url().contains("/diff/abc"));
    }

    @Test
    public void testGetCommitDiffExceptionReturnsEmptyBody() throws IOException {
        doThrow(new IOException("boom")).when(bitbucket).execute(any(GenericRequest.class));

        IBody body = bitbucket.getCommitDiff(WORKSPACE, REPOSITORY, "abc");

        assertEquals("", body.getBody());
    }

    @Test
    public void testGetDiffV1() throws IOException {
        useV1();
        doReturn("pr diff").when(bitbucket).execute(any(GenericRequest.class));

        IBody body = bitbucket.getDiff(WORKSPACE, REPOSITORY, PR_ID);

        assertEquals("pr diff", body.getBody());
        assertTrue(captureLastExecute().url().contains("/pullrequests/" + PR_ID + "/diff"));
    }

    @Test
    public void testGetDiffV2Unsupported() {
        useV2();
        assertThrows(UnsupportedOperationException.class,
                () -> bitbucket.getDiff(WORKSPACE, REPOSITORY, PR_ID));
    }

    // ── files ────────────────────────────────────────────────────────────────

    @Test
    public void testGetListOfFilesPaginatesViaNext() throws IOException {
        useV2();
        doReturn("{\"values\":[{\"path\":\"a.txt\"}],\"next\":\"https://next.page\"}",
                "{\"values\":[{\"path\":\"b.txt\"}]}")
                .when(bitbucket).execute(any(GenericRequest.class));

        List<IFile> files = bitbucket.getListOfFiles(WORKSPACE, REPOSITORY, "main");

        assertEquals(2, files.size());
        verify(bitbucket, times(2)).execute(any(GenericRequest.class));
        assertEquals("https://next.page", captureLastExecute().url());
    }

    @Test
    public void testGetFileContent() throws IOException {
        doReturn("file body").when(bitbucket).execute(any(GenericRequest.class));

        String content = bitbucket.getFileContent("https://self.link/file");

        assertEquals("file body", content);
        assertEquals("https://self.link/file", captureLastExecute().url());
    }

    // ── unsupported operations ───────────────────────────────────────────────

    @Test
    public void testGetRepositoriesUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> bitbucket.getRepositories("ns"));
    }

    @Test
    public void testAddPullRequestLabelUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> bitbucket.addPullRequestLabel(WORKSPACE, REPOSITORY, PR_ID, "label"));
    }

    @Test
    public void testRemovePullRequestLabelUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> bitbucket.removePullRequestLabel(WORKSPACE, REPOSITORY, PR_ID, "label"));
    }

    @Test
    public void testGetDefaultRepositoryUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> bitbucket.getDefaultRepository());
    }

    @Test
    public void testGetDefaultBranchUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> bitbucket.getDefaultBranch());
    }

    @Test
    public void testGetDefaultWorkspaceUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> bitbucket.getDefaultWorkspace());
    }

    @Test
    public void testSearchFilesUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> bitbucket.searchFiles(WORKSPACE, REPOSITORY, "query", 10));
    }
}
