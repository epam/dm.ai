// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IChange;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IFile;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IRepository;
import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.gitlab.model.GitLabJob;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class GitLabCoverageTest {

    private GitLab gitLab;
    private final String basePath = "http://example.com";

    @Before
    public void setUp() throws Exception {
        gitLab = Mockito.mock(GitLab.class, Mockito.CALLS_REAL_METHODS);
        doReturn(basePath).when(gitLab).getBasePath();
        Field authField = AbstractRestClient.class.getDeclaredField("authorization");
        authField.setAccessible(true);
        authField.set(gitLab, "test-token");
    }

    private static void setClient(GitLab gitLab, OkHttpClient client) throws Exception {
        Field clientField = AbstractRestClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(gitLab, client);
    }

    private static JSONArray jsonArrayOf(int count) {
        JSONArray array = new JSONArray();
        for (int i = 1; i <= count; i++) {
            array.put(new JSONObject().put("id", i).put("iid", i));
        }
        return array;
    }

    // ── constructor ─────────────────────────────────────────────────────────

    @Test
    public void testConstructorViaBasicGitLab() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(basePath)
                .auth("test-token")
                .workspaceName("workspace")
                .repoName("repo")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITLAB)
                .build();
        GitLab instance = mock(BasicGitLab.class,
                withSettings().useConstructor(config).defaultAnswer(CALLS_REAL_METHODS));
        assertEquals(basePath + "/api/v4/user", instance.path("user"));
    }

    // ── testConnection ──────────────────────────────────────────────────────

    @Test
    public void testConnectionSuccess() throws IOException {
        JSONObject user = new JSONObject()
                .put("id", 42)
                .put("username", "jdoe")
                .put("email", "jdoe@example.com");
        doReturn(user.toString()).when(gitLab).execute(any(GenericRequest.class));

        Map<String, Object> result = gitLab.testConnection();

        assertEquals(true, result.get("success"));
        assertEquals("jdoe", result.get("user"));
        assertEquals("42", result.get("userId"));
        assertEquals("jdoe@example.com", result.get("email"));
    }

    @Test
    public void testConnectionUnexpectedFormat() throws IOException {
        doReturn("{}").when(gitLab).execute(any(GenericRequest.class));

        Map<String, Object> result = gitLab.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("Unexpected response format from GitLab API", result.get("message"));
    }

    @Test
    public void testConnectionEmptyResponse() throws IOException {
        doReturn("").when(gitLab).execute(any(GenericRequest.class));

        Map<String, Object> result = gitLab.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("Empty response from GitLab API", result.get("message"));
    }

    @Test
    public void testConnectionException() throws IOException {
        doThrow(new IOException("boom")).when(gitLab).execute(any(GenericRequest.class));

        Map<String, Object> result = gitLab.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("IOException", result.get("error"));
        assertTrue(String.valueOf(result.get("message")).contains("boom"));
    }

    // ── pullRequests ────────────────────────────────────────────────────────

    @Test
    public void testPullRequestsSinglePageWithoutCheckAll() throws IOException {
        doReturn(jsonArrayOf(2).toString()).when(gitLab).execute(any(GenericRequest.class));

        List<IPullRequest> mrs = gitLab.pullRequests("workspace", "repo", "opened", false, null);

        assertEquals(2, mrs.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(1)).execute(captor.capture());
        String url = captor.getValue().url();
        assertTrue(url.contains("projects/workspace%2Frepo/merge_requests"));
        assertTrue(url.contains("state=opened"));
        assertTrue(url.contains("per_page=100"));
        assertTrue(url.contains("page=1"));
    }

    @Test
    public void testPullRequestsPaginatesWithStartDate() throws IOException {
        doReturn(jsonArrayOf(100).toString(), jsonArrayOf(1).toString())
                .when(gitLab).execute(any(GenericRequest.class));
        Calendar startDate = Calendar.getInstance();
        startDate.set(2024, Calendar.JANUARY, 1, 0, 0, 0);

        List<IPullRequest> mrs = gitLab.pullRequests("workspace", "repo", "all", true, startDate);

        assertEquals(101, mrs.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(2)).execute(captor.capture());
        assertTrue(captor.getAllValues().get(0).url().contains("created_after=2024-01-01T00:00:00Z"));
        assertTrue(captor.getAllValues().get(1).url().contains("page=2"));
    }

    @Test
    public void testPullRequestsNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        List<IPullRequest> mrs = gitLab.pullRequests("workspace", "repo", "opened", true, null);

        assertTrue(mrs.isEmpty());
    }

    // ── pullRequest / getMRDetails / listMergeRequests ──────────────────────

    @Test
    public void testPullRequest() throws IOException {
        JSONObject mr = new JSONObject().put("id", 7).put("iid", 3).put("title", "Fix bug");
        doReturn(mr.toString()).when(gitLab).execute(any(GenericRequest.class));

        IPullRequest result = gitLab.pullRequest("workspace", "repo", "3");

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("merge_requests/3"));
    }

    @Test
    public void testGetMRDetails() throws IOException {
        JSONObject mr = new JSONObject().put("id", 7).put("iid", 3).put("title", "Fix bug");
        doReturn(mr.toString()).when(gitLab).execute(any(GenericRequest.class));

        String details = gitLab.getMRDetails("workspace", "repo", "3");

        assertTrue(details.contains("Fix bug"));
    }

    @Test
    public void testListMergeRequestsOpenSynonymNormalizedToOpened() throws IOException {
        doReturn(jsonArrayOf(1).toString()).when(gitLab).execute(any(GenericRequest.class));

        gitLab.listMergeRequests("workspace", "repo", "open");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=opened"));
    }

    @Test
    public void testListMergeRequestsClosedFiltersToClosedAndMerged() throws IOException {
        JSONArray mrs = new JSONArray()
                .put(new JSONObject().put("iid", 1).put("state", "closed"))
                .put(new JSONObject().put("iid", 2).put("state", "merged"))
                .put(new JSONObject().put("iid", 3).put("state", "opened"));
        doReturn(mrs.toString()).when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.listMergeRequests("workspace", "repo", "closed");

        JSONArray parsed = new JSONArray(result);
        assertEquals(2, parsed.length());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=all"));
    }

    @Test
    public void testListMergeRequestsPassthroughState() throws IOException {
        doReturn(jsonArrayOf(1).toString()).when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.listMergeRequests("workspace", "repo", "merged");

        assertEquals(1, new JSONArray(result).length());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=merged"));
    }

    // ── commitComments / renamePullRequest ──────────────────────────────────

    @Test
    public void testCommitComments() throws IOException {
        JSONObject comments = new JSONObject()
                .put("comments", new JSONArray()
                        .put(new JSONObject().put("id", 1).put("note", "looks good")));
        doReturn(comments.toString()).when(gitLab).execute(any(GenericRequest.class));

        JSONModel result = gitLab.commitComments("workspace", "repo", "abc123");

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("repository/commits/abc123/comments"));
    }

    @Test
    public void testCommitCommentsNullResponseReturnsEmptyModel() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        JSONModel result = gitLab.commitComments("workspace", "repo", "abc123");

        assertNotNull(result);
    }

    @Test
    public void testRenamePullRequest() throws IOException {
        IPullRequest pullRequest = mock(IPullRequest.class);
        when(pullRequest.getId()).thenReturn(7);
        doReturn("{\"iid\":7,\"title\":\"New title\"}").when(gitLab).patch(any());

        String result = gitLab.renamePullRequest("workspace", "repo", pullRequest, "New title");

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).patch(captor.capture());
        assertEquals("New title", new JSONObject(captor.getValue().getBody()).getString("title"));
        assertTrue(captor.getValue().url().contains("merge_requests/7"));
    }

    // ── tags / branches / repositories ──────────────────────────────────────

    @Test
    public void testGetTags() throws IOException {
        JSONArray tags = new JSONArray()
                .put(new JSONObject().put("name", "v1.0"))
                .put(new JSONObject().put("name", "v1.1"));
        doReturn(tags.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<ITag> result = gitLab.getTags("workspace", "repo");

        assertEquals(2, result.size());
        assertEquals("v1.0", result.get(0).getName());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("repository/tags"));
    }

    @Test
    public void testGetTagsNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        assertTrue(gitLab.getTags("workspace", "repo").isEmpty());
    }

    @Test
    public void testGetBranches() throws IOException {
        JSONArray branches = new JSONArray()
                .put(new JSONObject().put("name", "main"))
                .put(new JSONObject().put("name", "develop"));
        doReturn(branches.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<ITag> result = gitLab.getBranches("workspace", "repo");

        assertEquals(2, result.size());
        assertEquals("develop", result.get(1).getName());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("repository/branches"));
    }

    @Test
    public void testGetBranchesNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        assertTrue(gitLab.getBranches("workspace", "repo").isEmpty());
    }

    @Test
    public void testGetRepositories() throws IOException {
        JSONArray projects = new JSONArray()
                .put(new JSONObject().put("id", 1).put("path_with_namespace", "group/repo-a"))
                .put(new JSONObject().put("id", 2).put("path_with_namespace", "group/repo-b"));
        doReturn(projects.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<IRepository> result = gitLab.getRepositories("group");

        assertEquals(2, result.size());
        assertEquals("repo-a", result.get(0).getName());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("groups/group/projects"));
    }

    @Test
    public void testGetRepositoriesNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        assertTrue(gitLab.getRepositories("group").isEmpty());
    }

    // ── commits ─────────────────────────────────────────────────────────────

    @Test
    public void testGetCommitsBetween() throws IOException {
        JSONObject response = new JSONObject().put("commits", new JSONArray()
                .put(new JSONObject().put("id", "abc1").put("message", "Fix"))
                .put(new JSONObject().put("id", "abc2").put("message", "Add")));
        doReturn(response.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<ICommit> commits = gitLab.getCommitsBetween("workspace", "repo", "v1.0", "v1.1");

        assertEquals(2, commits.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("repository/compare?from=v1.0&to=v1.1"));
    }

    @Test
    public void testGetCommitsBetweenNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        assertTrue(gitLab.getCommitsBetween("workspace", "repo", "v1.0", "v1.1").isEmpty());
    }

    @Test
    public void testPerformCommitsFromBranchStopsWhenPerformerReturnsTrue() throws Exception {
        JSONArray commits = new JSONArray()
                .put(new JSONObject().put("id", "abc1").put("message", "Fix"))
                .put(new JSONObject().put("id", "abc2").put("message", "Add"));
        doReturn(commits.toString()).when(gitLab).execute(any(GenericRequest.class));
        @SuppressWarnings("unchecked")
        AbstractRestClient.Performer<ICommit> performer = mock(AbstractRestClient.Performer.class);
        when(performer.perform(any())).thenReturn(true);

        gitLab.performCommitsFromBranch("workspace", "repo", "main", performer);

        verify(performer, times(1)).perform(any());
    }

    @Test
    public void testPerformCommitsFromBranchIteratesAllWhenPerformerReturnsFalse() throws Exception {
        JSONArray commits = new JSONArray()
                .put(new JSONObject().put("id", "abc1").put("message", "Fix"))
                .put(new JSONObject().put("id", "abc2").put("message", "Add"));
        doReturn(commits.toString()).when(gitLab).execute(any(GenericRequest.class));
        @SuppressWarnings("unchecked")
        AbstractRestClient.Performer<ICommit> performer = mock(AbstractRestClient.Performer.class);
        when(performer.perform(any())).thenReturn(false);

        gitLab.performCommitsFromBranch("workspace", "repo", "main", performer);

        verify(performer, times(2)).perform(any());
    }

    // ── diffs ───────────────────────────────────────────────────────────────

    @Test
    public void testGetCommitDiffStatAndGetCommitDiff() throws IOException {
        JSONObject commit = new JSONObject().put("id", "abc123").put("message", "Fix bug");
        doReturn(commit.toString()).when(gitLab).execute(any(GenericRequest.class));

        IDiffStats diffStats = gitLab.getCommitDiffStat("workspace", "repo", "abc123");
        assertNotNull(diffStats);

        String diffBody = gitLab.getCommitDiff("workspace", "repo", "abc123").getBody();
        assertTrue(diffBody.contains("abc123"));

        verify(gitLab, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testGetDiffReturnsRawMergeRequestChanges() throws IOException {
        JSONObject changes = new JSONObject().put("changes", new JSONArray());
        doReturn(changes.toString()).when(gitLab).execute(any(GenericRequest.class));

        String body = gitLab.getDiff("workspace", "repo", "42").getBody();

        assertEquals(changes.toString(), body);
    }

    @Test
    public void testGetPullRequestDiffNullChangesReturnsEmptyDiffStats() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        IDiffStats diffStats = gitLab.getPullRequestDiff("workspace", "repo", "42");

        assertTrue(diffStats instanceof IDiffStats.Empty);
    }

    @Test
    public void testGetPullRequestDiffParsesAddedAndRemovedLines() throws IOException {
        JSONObject change = new JSONObject()
                .put("new_path", "src/Foo.java")
                .put("diff", "@@ -1,3 +1,4 @@\n context\n+added line\n+++ not counted\n-removed line\n--- not counted\n");
        JSONObject response = new JSONObject().put("changes", new JSONArray().put(change));
        doReturn(response.toString()).when(gitLab).execute(any(GenericRequest.class));

        IDiffStats diffStats = gitLab.getPullRequestDiff("workspace", "repo", "42");

        assertEquals(1, diffStats.getStats().getAdditions());
        assertEquals(1, diffStats.getStats().getDeletions());
        assertEquals(2, diffStats.getStats().getTotal());
        List<IChange> changes = diffStats.getChanges();
        assertEquals(1, changes.size());
        assertEquals("src/Foo.java", changes.get(0).getFilePath());
    }

    @Test
    public void testParseDiffStatsStatic() {
        JSONObject change = new JSONObject()
                .put("new_path", "a.java")
                .put("diff", "+one\n+two\n-three\n");
        JSONObject response = new JSONObject().put("changes", new JSONArray().put(change));

        IDiffStats diffStats = GitLab.parseDiffStats(response);

        assertEquals(2, diffStats.getStats().getAdditions());
        assertEquals(1, diffStats.getStats().getDeletions());
        assertEquals(3, diffStats.getStats().getTotal());
    }

    @Test
    public void testMergeRequestChangesErrorIsCached() throws IOException {
        doThrow(new RestClient.RestClientException("error", "body", 500))
                .when(gitLab).execute(any(GenericRequest.class));

        IDiffStats first = gitLab.getPullRequestDiff("workspace", "repo", "42");
        IDiffStats second = gitLab.getPullRequestDiff("workspace", "repo", "42");

        assertTrue(first instanceof IDiffStats.Empty);
        assertTrue(second instanceof IDiffStats.Empty);
        // After the first failure the client must not hit the API again
        verify(gitLab, times(1)).execute(any(GenericRequest.class));
    }

    // ── files ───────────────────────────────────────────────────────────────

    @Test
    public void testGetListOfFiles() throws IOException {
        JSONArray files = new JSONArray()
                .put(new JSONObject().put("path", "src/Foo.java").put("type", "blob"))
                .put(new JSONObject().put("path", "src").put("type", "tree"));
        doReturn(files.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<IFile> result = gitLab.getListOfFiles("workspace", "repo", "main");

        assertEquals(2, result.size());
        assertEquals("src/Foo.java", result.get(0).getPath());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("repository/tree?ref=main&recursive=true"));
    }

    @Test
    public void testGetListOfFilesNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        assertTrue(gitLab.getListOfFiles("workspace", "repo", "main").isEmpty());
    }

    @Test
    public void testGetFileContentDecodesBase64() throws IOException {
        String encoded = Base64.getEncoder().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));
        doReturn(new JSONObject().put("content", encoded).toString())
                .when(gitLab).execute(any(GenericRequest.class));

        String content = gitLab.getFileContent(basePath + "/api/v4/projects/1/repository/files/a.java/raw");

        assertEquals("hello world", content);
    }

    // ── createMergeRequest optional-field branches ──────────────────────────

    @Test
    public void testCreateMergeRequestWithoutOptionalFields() throws IOException {
        doReturn("{\"iid\": 100}").when(gitLab).post(any());

        String result = gitLab.createMergeRequest(
                "workspace", "repo", "feature/X", "main", "Title", null, null);

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("feature/X", body.getString("source_branch"));
        assertFalse(body.has("description"));
        assertFalse(body.has("remove_source_branch"));
    }

    // ── CI: jobs / pipelines ────────────────────────────────────────────────

    @Test
    public void testListJobsPaginates() throws IOException {
        doReturn(jsonArrayOf(100).toString(), jsonArrayOf(1).toString())
                .when(gitLab).execute(any(GenericRequest.class));

        List<GitLabJob> jobs = gitLab.listJobs("workspace", "repo");

        assertEquals(101, jobs.size());
        verify(gitLab, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testListJobsNullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));

        assertTrue(gitLab.listJobs("workspace", "repo").isEmpty());
    }

    @Test
    public void testCancelJob() throws IOException {
        doReturn("{\"id\":123,\"status\":\"canceled\"}").when(gitLab).post(any());

        String result = gitLab.cancelJob("workspace", "repo", "123");

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        assertTrue(captor.getValue().url().contains("jobs/123/cancel"));
    }

    @Test
    public void testListPipelineRunsNormalizesInProgressStatusAndInvalidLimit() throws IOException {
        doReturn("[]").when(gitLab).execute(any(GenericRequest.class));

        gitLab.listPipelineRuns("workspace", "repo", "in_progress", null, "not-a-number");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        String url = captor.getValue().url();
        assertTrue(url.contains("status=running"));
        assertTrue(url.contains("per_page=50"));
    }

    @Test
    public void testGetPipelineJobsPaginates() throws IOException {
        doReturn(jsonArrayOf(100).toString(), jsonArrayOf(1).toString())
                .when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.getPipelineJobs("workspace", "repo", "555");

        assertEquals(101, new JSONArray(result).length());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(2)).execute(captor.capture());
        assertTrue(captor.getAllValues().get(0).url().contains("pipelines/555/jobs"));
    }

    @Test
    public void testTriggerPipelineWithVariables() throws IOException {
        doReturn("{\"id\":777,\"status\":\"pending\"}").when(gitLab).post(any());

        String result = gitLab.triggerPipeline("workspace", "repo", "main", "{\"CONFIG_FILE\":\"a.json\"}");

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        assertTrue(captor.getValue().url().contains("projects/workspace%2Frepo/pipeline"));
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("main", body.getString("ref"));
        JSONArray variables = body.getJSONArray("variables");
        assertEquals(1, variables.length());
        assertEquals("CONFIG_FILE", variables.getJSONObject(0).getString("key"));
        assertEquals("a.json", variables.getJSONObject(0).getString("value"));
    }

    @Test
    public void testTriggerPipelineWithoutVariables() throws IOException {
        doReturn("{\"id\":778}").when(gitLab).post(any());

        gitLab.triggerPipeline("workspace", "repo", "develop", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("develop", body.getString("ref"));
        assertFalse(body.has("variables"));
    }

    // ── release helpers not covered elsewhere ───────────────────────────────

    @Test
    public void testUploadReleaseAssetDetectsContentTypeWhenNotProvided() throws IOException {
        File tempAsset = File.createTempFile("release-asset-coverage-", ".bin");
        try {
            Files.writeString(tempAsset.toPath(), "binary-data");
            doReturn("").when(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), anyString());
            doReturn("{}").when(gitLab).post(any(GenericRequest.class));

            gitLab.uploadReleaseAsset("workspace", "repo", "v1.0", tempAsset.getAbsolutePath(),
                    "asset.bin", null, null, null);

            ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
            verify(gitLab).uploadGenericPackageBinary(anyString(), any(File.class), typeCaptor.capture());
            assertNotNull(typeCaptor.getValue());
            assertFalse(typeCaptor.getValue().trim().isEmpty());
        } finally {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void testUploadReleaseAssetMissingFileThrows() throws IOException {
        gitLab.uploadReleaseAsset("workspace", "repo", "v1.0",
                "/nonexistent/path/asset.png", null, null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUploadReleaseAssetDirectoryPathThrows() throws IOException {
        File tempDir = Files.createTempDirectory("release-asset-dir-").toFile();
        try {
            gitLab.uploadReleaseAsset("workspace", "repo", "v1.0",
                    tempDir.getAbsolutePath(), null, null, null, null);
        } finally {
            Files.deleteIfExists(tempDir.toPath());
        }
    }

    // ── binary upload / download via mocked OkHttp client ───────────────────

    @Test
    public void testUploadGenericPackageBinarySuccess() throws Exception {
        File tempAsset = File.createTempFile("upload-binary-", ".txt");
        try {
            Files.writeString(tempAsset.toPath(), "payload");
            ResponseBody responseBody = mock(ResponseBody.class);
            when(responseBody.string()).thenReturn("uploaded");
            Response response = mock(Response.class);
            when(response.isSuccessful()).thenReturn(true);
            when(response.body()).thenReturn(responseBody);
            Call call = mock(Call.class);
            when(call.execute()).thenReturn(response);
            OkHttpClient client = mock(OkHttpClient.class);
            when(client.newCall(any())).thenReturn(call);
            setClient(gitLab, client);

            String result = gitLab.uploadGenericPackageBinary(
                    basePath + "/api/v4/projects/1/packages/generic/p/v/file.txt", tempAsset, "text/plain");

            assertEquals("uploaded", result);
        } finally {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    @Test
    public void testUploadGenericPackageBinaryInvalidContentTypeThrows() throws Exception {
        File tempAsset = File.createTempFile("upload-binary-", ".txt");
        try {
            gitLab.uploadGenericPackageBinary(
                    basePath + "/api/v4/projects/1/packages/generic/p/v/file.txt", tempAsset, "invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid content type"));
        } finally {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    @Test
    public void testUploadGenericPackageBinaryErrorResponseThrows() throws Exception {
        File tempAsset = File.createTempFile("upload-binary-", ".txt");
        try {
            Files.writeString(tempAsset.toPath(), "payload");
            ResponseBody responseBody = mock(ResponseBody.class);
            when(responseBody.string()).thenReturn("server error");
            Response response = mock(Response.class);
            when(response.isSuccessful()).thenReturn(false);
            when(response.code()).thenReturn(500);
            when(response.message()).thenReturn("Internal Server Error");
            when(response.body()).thenReturn(responseBody);
            Call call = mock(Call.class);
            when(call.execute()).thenReturn(response);
            OkHttpClient client = mock(OkHttpClient.class);
            when(client.newCall(any())).thenReturn(call);
            setClient(gitLab, client);

            gitLab.uploadGenericPackageBinary(
                    basePath + "/api/v4/projects/1/packages/generic/p/v/file.txt", tempAsset, "text/plain");
            fail("Expected IOException");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        } finally {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    @Test
    public void testDownloadBinaryToFileWritesContentAndCreatesParentDirs() throws Exception {
        File tempDir = Files.createTempDirectory("download-binary-").toFile();
        File targetFile = new File(new File(tempDir, "nested/dir"), "asset.bin");
        try {
            ResponseBody responseBody = mock(ResponseBody.class);
            when(responseBody.byteStream()).thenReturn(
                    new ByteArrayInputStream("downloaded-content".getBytes(StandardCharsets.UTF_8)));
            Response response = mock(Response.class);
            when(response.isSuccessful()).thenReturn(true);
            when(response.body()).thenReturn(responseBody);
            Call call = mock(Call.class);
            when(call.execute()).thenReturn(response);
            OkHttpClient client = mock(OkHttpClient.class);
            when(client.newCall(any())).thenReturn(call);
            setClient(gitLab, client);

            String result = gitLab.downloadBinaryToFile(
                    basePath + "/api/v4/projects/1/packages/generic/p/v/asset.bin", targetFile);

            assertEquals(targetFile.getAbsolutePath(), result);
            assertTrue(targetFile.exists());
            assertEquals("downloaded-content",
                    new String(Files.readAllBytes(targetFile.toPath()), StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testDownloadBinaryToFileErrorResponseThrows() throws Exception {
        File tempDir = Files.createTempDirectory("download-binary-").toFile();
        File targetFile = new File(tempDir, "asset.bin");
        try {
            ResponseBody responseBody = mock(ResponseBody.class);
            when(responseBody.string()).thenReturn("not found");
            Response response = mock(Response.class);
            when(response.isSuccessful()).thenReturn(false);
            when(response.code()).thenReturn(404);
            when(response.message()).thenReturn("Not Found");
            when(response.body()).thenReturn(responseBody);
            Call call = mock(Call.class);
            when(call.execute()).thenReturn(response);
            OkHttpClient client = mock(OkHttpClient.class);
            when(client.newCall(any())).thenReturn(call);
            setClient(gitLab, client);

            gitLab.downloadBinaryToFile(
                    basePath + "/api/v4/projects/1/packages/generic/p/v/asset.bin", targetFile);
            fail("Expected IOException");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ── unsupported operations ──────────────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void testSearchFilesUnsupported() throws IOException {
        gitLab.searchFiles("workspace", "repo", "query", 10);
    }

    private static void deleteRecursively(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        Files.deleteIfExists(file.toPath());
    }
}
