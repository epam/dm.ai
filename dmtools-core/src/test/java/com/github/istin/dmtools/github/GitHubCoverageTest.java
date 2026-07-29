// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IBody;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IFile;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.github.model.GitHubConversation;
import com.github.istin.dmtools.github.model.GithubTag;
import com.github.istin.dmtools.job.JobRunner;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Additional unit tests for {@link GitHub} aimed at covering the public API surface
 * (pull requests, comments, releases, check runs, workflow helpers, file content,
 * payload processing) with mocked HTTP layer - no real network calls are made.
 */
public class GitHubCoverageTest {

    private static final String BASE_PATH = "https://api.github.com";
    private static final String WORKSPACE = "testWorkspace";
    private static final String REPOSITORY = "testRepo";

    private GitHub gitHub;
    private File tempAsset;
    private File tempDir;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(BASE_PATH)
                .auth("test-token")
                .workspaceName(WORKSPACE)
                .repoName(REPOSITORY)
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = mock(BasicGithub.class, withSettings().useConstructor(config).defaultAnswer(CALLS_REAL_METHODS));
    }

    @After
    public void tearDown() throws IOException {
        if (tempAsset != null) {
            Files.deleteIfExists(tempAsset.toPath());
        }
        if (tempDir != null) {
            Files.deleteIfExists(tempDir.toPath());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static JSONObject prJson(int number, String title, String mergedAt, String updatedAt) {
        JSONObject pr = new JSONObject();
        pr.put("number", number);
        if (title != null) {
            pr.put("title", title);
        }
        pr.put("created_at", "2024-01-15T10:00:00Z");
        pr.put("updated_at", updatedAt != null ? updatedAt : "2024-01-16T10:00:00Z");
        if (mergedAt != null) {
            pr.put("merged_at", mergedAt);
        }
        pr.put("head", new JSONObject().put("ref", "feature").put("sha", "headsha123"));
        pr.put("base", new JSONObject().put("ref", "main"));
        return pr;
    }

    private static JSONObject commentJson(long id, Long inReplyToId, String createdAt, String body) {
        JSONObject comment = new JSONObject();
        comment.put("id", id);
        if (inReplyToId != null) {
            comment.put("in_reply_to_id", inReplyToId);
        }
        comment.put("created_at", createdAt);
        comment.put("body", body);
        comment.put("user", new JSONObject().put("login", "user" + id));
        return comment;
    }

    private static String randomString(int length) {
        Random random = new Random(42);
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private GenericRequest captureLastPost() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, atLeastOnce()).post(captor.capture());
        List<GenericRequest> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    // ── basic accessors / signing ────────────────────────────────────────────

    @Test
    public void testGetAuthorization() {
        assertEquals("test-token", gitHub.getAuthorization());
    }

    @Test
    public void testSignAddsGitHubHeaders() {
        Request.Builder signed = gitHub.sign(new Request.Builder().url("https://api.github.com/user"));
        Request request = signed.build();
        assertEquals("Bearer test-token", request.header("Authorization"));
        assertEquals("application/vnd.github.v3+json", request.header("Accept"));
        assertEquals("application/json", request.header("Content-Type"));
    }

    @Test
    public void testSupportsPullRequestTitleFiltering() {
        assertTrue(gitHub.supportsPullRequestTitleFiltering());
    }

    // ── connection test ──────────────────────────────────────────────────────

    @Test
    public void testTestConnectionDetailedSuccess() throws IOException {
        doReturn("{\"login\":\"octocat\",\"id\":42}").when(gitHub).execute(any(GenericRequest.class));

        Map<String, Object> result = gitHub.testConnection();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("octocat", result.get("user"));
        assertEquals("42", result.get("userId"));
    }

    @Test
    public void testTestConnectionDetailedUnexpectedFormat() throws IOException {
        doReturn("{\"something\":\"else\"}").when(gitHub).execute(any(GenericRequest.class));

        Map<String, Object> result = gitHub.testConnectionDetailed();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("Unexpected response format from GitHub API", result.get("message"));
    }

    @Test
    public void testTestConnectionDetailedEmptyResponse() throws IOException {
        doReturn("").when(gitHub).execute(any(GenericRequest.class));

        Map<String, Object> result = gitHub.testConnectionDetailed();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("Empty response from GitHub API", result.get("message"));
    }

    @Test
    public void testTestConnectionDetailedException() throws IOException {
        doThrow(new IOException("boom")).when(gitHub).execute(any(GenericRequest.class));

        Map<String, Object> result = gitHub.testConnectionDetailed();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("GitHub API connection failed: boom", result.get("message"));
        assertEquals("IOException", result.get("error"));
    }

    // ── pull requests listing ────────────────────────────────────────────────

    @Test
    public void testPullRequestReturnsModel() throws IOException {
        doReturn(prJson(74, "My PR", null, null).toString()).when(gitHub).execute(any(GenericRequest.class));

        IPullRequest pr = gitHub.pullRequest(WORKSPACE, REPOSITORY, "74");

        assertEquals("My PR", pr.getTitle());
    }

    @Test
    public void testPullRequestsOpenSinglePage() throws IOException {
        JSONArray page = new JSONArray()
                .put(prJson(1, "First", null, null))
                .put(prJson(2, "Second", null, null));
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY, "open", false, null);

        assertEquals(2, result.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=open"));
        assertTrue(captor.getValue().url().contains("page=1"));
    }

    @Test
    public void testPullRequestsMergedFiltersNonMerged() throws IOException {
        JSONArray page = new JSONArray()
                .put(prJson(1, "Merged one", "2024-02-01T10:00:00Z", null))
                .put(prJson(2, "Closed not merged", null, null));
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY,
                IPullRequest.PullRequestState.STATE_MERGED, false, null);

        assertEquals(1, result.size());
        assertEquals("Merged one", result.get(0).getTitle());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=closed"));
    }

    @Test
    public void testPullRequestsDeclinedFiltersMerged() throws IOException {
        JSONArray page = new JSONArray()
                .put(prJson(1, "Merged one", "2024-02-01T10:00:00Z", null))
                .put(prJson(2, "Declined one", null, null));
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY,
                IPullRequest.PullRequestState.STATE_DECLINED, false, null);

        assertEquals(1, result.size());
        assertEquals("Declined one", result.get(0).getTitle());
    }

    @Test
    public void testPullRequestsPaginatesWhenPageIsFull() throws IOException {
        JSONArray fullPage = new JSONArray();
        for (int i = 0; i < 100; i++) {
            fullPage.put(prJson(i, "PR " + i, null, null));
        }
        JSONArray secondPage = new JSONArray().put(prJson(100, "Last", null, null));
        doReturn(fullPage.toString(), secondPage.toString()).when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY, "open", true, null);

        assertEquals(101, result.size());
        verify(gitHub, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testPullRequestsStopsAtStartDate() throws IOException {
        JSONArray page = new JSONArray()
                .put(prJson(1, "Old PR", null, "2024-01-01T10:00:00Z"));
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));
        Calendar startDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startDate.set(2024, Calendar.JUNE, 1, 0, 0, 0);

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY, "open", true, startDate);

        assertTrue(result.isEmpty());
        verify(gitHub, times(1)).execute(any(GenericRequest.class));
    }

    @Test
    public void testPullRequestsFiltersByStartDateAndTitlePattern() throws IOException {
        JSONArray page = new JSONArray()
                .put(prJson(1, "feat: matching", null, "2024-07-01T10:00:00Z"))
                .put(prJson(2, "fix: other", null, "2024-07-02T10:00:00Z"))
                .put(prJson(3, null, null, "2024-07-03T10:00:00Z"));
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));
        Calendar startDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startDate.set(2024, Calendar.JUNE, 1, 0, 0, 0);

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY, "open", false,
                startDate, Pattern.compile("^feat"));

        assertEquals(1, result.size());
        assertEquals("feat: matching", result.get(0).getTitle());
    }

    @Test
    public void testPullRequestsEmptyResponseReturnsEmptyList() throws IOException {
        doReturn("").when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.pullRequests(WORKSPACE, REPOSITORY, "open", true, null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testListPullRequestsNormalizesOpenedSynonym() throws IOException {
        doReturn("[]").when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.listPullRequests(WORKSPACE, REPOSITORY, "opened");

        assertTrue(result.isEmpty());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=open"));
    }

    @Test
    public void testListPullRequestsNormalizesDeclinedSynonym() throws IOException {
        doReturn("[]").when(gitHub).execute(any(GenericRequest.class));

        gitHub.listPullRequests(WORKSPACE, REPOSITORY, "declined");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("state=closed"));
    }

    @Test
    public void testListPullRequestsFilteredByTitleRegex() throws IOException {
        JSONArray page = new JSONArray()
                .put(prJson(1, "feat(core): change", null, null))
                .put(prJson(2, "chore: cleanup", null, null));
        doReturn(page.toString()).when(gitHub).execute(any(GenericRequest.class));

        List<IPullRequest> result = gitHub.listPullRequestsFiltered(WORKSPACE, REPOSITORY, "open", "^feat\\(");

        assertEquals(1, result.size());
        assertEquals("feat(core): change", result.get(0).getTitle());
    }

    // ── actions / workflows dispatch ─────────────────────────────────────────

    @Test
    public void testTriggerActionPostsParams() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));
        JSONObject params = new JSONObject().put("event_type", "rework");

        String result = gitHub.triggerAction(WORKSPACE, REPOSITORY, params);

        assertEquals("{}", result);
        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/repos/testWorkspace/testRepo/dispatches"));
        assertEquals("rework", new JSONObject(request.getBody()).getString("event_type"));
    }

    @Test
    public void testRepositoryDispatchWithClientPayload() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.repositoryDispatch(WORKSPACE, REPOSITORY, "rework", "{\"key\":\"value\"}");

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertEquals("rework", body.getString("event_type"));
        assertEquals("value", body.getJSONObject("client_payload").getString("key"));
    }

    @Test
    public void testRepositoryDispatchWithoutClientPayload() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.repositoryDispatch(WORKSPACE, REPOSITORY, "rework", "  ");

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertEquals("rework", body.getString("event_type"));
        assertFalse(body.has("client_payload"));
    }

    @Test
    public void testTriggerWorkflowWithRefAndInputs() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.triggerWorkflow(WORKSPACE, REPOSITORY, "rework.yml",
                "{\"user_request\":\"do it\"}", "develop");

        assertEquals("Workflow 'rework.yml' triggered successfully on testWorkspace/testRepo", result);
        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/actions/workflows/rework.yml/dispatches"));
        JSONObject body = new JSONObject(request.getBody());
        assertEquals("develop", body.getString("ref"));
        assertEquals("do it", body.getJSONObject("inputs").getString("user_request"));
    }

    @Test
    public void testTriggerWorkflowDefaultsRefToMain() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.triggerWorkflow(WORKSPACE, REPOSITORY, "rework.yml", null, null);

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertEquals("main", body.getString("ref"));
        assertFalse(body.has("inputs"));
    }

    // ── release assets ───────────────────────────────────────────────────────

    @Test
    public void testDeleteReleaseAsset() throws IOException {
        doReturn("").when(gitHub).delete(any(genericRequestMatcher()));

        gitHub.deleteReleaseAsset(WORKSPACE, REPOSITORY, "422721847");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).delete(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/releases/assets/422721847"));
    }

    @Test
    public void testListReleaseAssets() throws IOException {
        doReturn("[{\"id\":1,\"name\":\"a.png\"}]").when(gitHub).execute(any(GenericRequest.class));

        String result = gitHub.listReleaseAssets(WORKSPACE, REPOSITORY, "323096697");

        assertEquals("[{\"id\":1,\"name\":\"a.png\"}]", result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/releases/323096697/assets"));
    }

    @Test
    public void testUploadReleaseAssetDeletesExistingAssetOnOverwrite() throws IOException {
        tempAsset = Files.createTempFile("release-asset", ".txt").toFile();
        Files.write(tempAsset.toPath(), "content".getBytes());
        JSONArray assets = new JSONArray()
                .put(new JSONObject().put("id", 555L).put("name", tempAsset.getName()));
        doReturn(assets.toString()).when(gitHub).execute(any(GenericRequest.class));
        doReturn("").when(gitHub).delete(any(genericRequestMatcher()));
        doReturn("{\"id\":999}").when(gitHub).uploadReleaseAssetBinary(anyString(), any(File.class), anyString());

        String result = gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "323096697",
                tempAsset.getAbsolutePath(), null, "text/plain", "Label", "true");

        assertEquals("{\"id\":999}", result);
        ArgumentCaptor<GenericRequest> deleteCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).delete(deleteCaptor.capture());
        assertTrue(deleteCaptor.getValue().url().contains("/releases/assets/555"));
    }

    @Test
    public void testUploadReleaseAssetMissingFileThrows() {
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () ->
                gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "1",
                        "/nonexistent/path/to/file.png", null, null, null, "false"));
        assertTrue(exception.getMessage().contains("Release asset file not found"));
    }

    @Test
    public void testUploadReleaseAssetDirectoryThrows() throws IOException {
        tempDir = Files.createTempDirectory("release-asset-dir").toFile();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "1",
                        tempDir.getAbsolutePath(), null, null, null, "false"));
        assertTrue(exception.getMessage().contains("must point to a file"));
    }

    @Test
    public void testUploadReleaseAssetNoOverwriteUsesDefaults() throws IOException {
        tempAsset = Files.createTempFile("asset-defaults", ".png").toFile();
        Files.write(tempAsset.toPath(), new byte[]{1, 2, 3});
        doReturn("{\"id\":1}").when(gitHub).uploadReleaseAssetBinary(anyString(), any(File.class), anyString());

        String result = gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "42",
                tempAsset.getAbsolutePath(), "custom.png", "image/png", null, null);

        assertEquals("{\"id\":1}", result);
        verify(gitHub, never()).delete(any(GenericRequest.class));
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHub).uploadReleaseAssetBinary(urlCaptor.capture(), any(File.class), contentTypeCaptor.capture());
        assertTrue(urlCaptor.getValue().startsWith("https://uploads.github.com/repos/testWorkspace/testRepo/releases/42/assets"));
        assertTrue(urlCaptor.getValue().contains("name=custom.png"));
        assertEquals("image/png", contentTypeCaptor.getValue());
    }

    // ── PR comments ──────────────────────────────────────────────────────────

    @Test
    public void testAddPullRequestComment() throws IOException {
        doReturn("{\"id\":1}").when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.addPullRequestComment(WORKSPACE, REPOSITORY, "74", "Looks good!");

        assertEquals("{\"id\":1}", result);
        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/repos/testWorkspace/testRepo/issues/74/comments"));
        assertEquals("Looks good!", new JSONObject(request.getBody()).getString("body"));
    }

    @Test
    public void testUpdatePullRequestComment() throws IOException {
        doReturn("{\"id\":1}").when(gitHub).patch(any(GenericRequest.class));

        String result = gitHub.updatePullRequestComment(WORKSPACE, REPOSITORY, "123456789", "Updated");

        assertEquals("{\"id\":1}", result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).patch(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/issues/comments/123456789"));
        assertEquals("Updated", new JSONObject(captor.getValue().getBody()).getString("body"));
    }

    @Test
    public void testDeletePullRequestComment() throws IOException {
        doReturn("").when(gitHub).delete(any(genericRequestMatcher()));

        gitHub.deletePullRequestComment(WORKSPACE, REPOSITORY, "123456789");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).delete(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/issues/comments/123456789"));
    }

    @Test
    public void testReplyToPullRequestComment() throws IOException {
        doReturn("{\"id\":2}").when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.replyToPullRequestComment(WORKSPACE, REPOSITORY, "74", "123456789", "Fixed.");

        assertEquals("{\"id\":2}", result);
        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/repos/testWorkspace/testRepo/pulls/74/comments"));
        JSONObject body = new JSONObject(request.getBody());
        assertEquals("Fixed.", body.getString("body"));
        assertEquals(123456789L, body.getLong("in_reply_to"));
    }

    @Test
    public void testReplyToPullRequestCommentInvalidId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.replyToPullRequestComment(WORKSPACE, REPOSITORY, "74", "not-a-number", "text"));
        assertTrue(exception.getMessage().contains("Invalid inReplyToId"));
    }

    @Test
    public void testAddInlineReviewCommentWithExplicitCommitAndRange() throws IOException {
        // no pending review -> submitPendingReview finds nothing
        doReturn("[]").when(gitHub).execute(any(GenericRequest.class));
        doReturn("{\"id\":10}").when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.addInlineReviewComment(WORKSPACE, REPOSITORY, "74",
                "src/Foo.java", "42", "Refactor this", "abc123", "40", "left");

        assertEquals("{\"id\":10}", result);
        GenericRequest request = captureLastPost();
        JSONObject body = new JSONObject(request.getBody());
        assertEquals("Refactor this", body.getString("body"));
        assertEquals("abc123", body.getString("commit_id"));
        assertEquals("src/Foo.java", body.getString("path"));
        assertEquals(42, body.getInt("line"));
        assertEquals("LEFT", body.getString("side"));
        assertEquals(40, body.getInt("start_line"));
        assertEquals("LEFT", body.getString("start_side"));
    }

    @Test
    public void testAddInlineReviewCommentSubmitsPendingReview() throws IOException {
        JSONArray reviews = new JSONArray()
                .put(new JSONObject().put("id", 777L).put("state", "PENDING"))
                .put(new JSONObject().put("id", 778L).put("state", "APPROVED"));
        doReturn(reviews.toString()).when(gitHub).execute(any(GenericRequest.class));
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.addInlineReviewComment(WORKSPACE, REPOSITORY, "74",
                "src/Foo.java", "42", "Comment", "abc123", null, null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(2)).post(captor.capture());
        GenericRequest submitRequest = captor.getAllValues().get(0);
        assertTrue(submitRequest.url().contains("/pulls/74/reviews/777/events"));
        assertEquals("COMMENT", new JSONObject(submitRequest.getBody()).getString("event"));
        JSONObject commentBody = new JSONObject(captor.getAllValues().get(1).getBody());
        assertEquals("RIGHT", commentBody.getString("side"));
        assertFalse(commentBody.has("start_line"));
    }

    @Test
    public void testAddInlineReviewCommentResolvesHeadCommitFromPr() throws IOException {
        JSONObject pr = new JSONObject().put("head", new JSONObject().put("sha", "resolvedsha"));
        // first execute: PR fetch for head sha; second: pending reviews check
        doReturn(pr.toString(), "[]").when(gitHub).execute(any(GenericRequest.class));
        doReturn("{\"id\":11}").when(gitHub).post(any(GenericRequest.class));

        gitHub.addInlineReviewComment(WORKSPACE, REPOSITORY, "74",
                "src/Foo.java", "10", "Comment", "  ", null, null);

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertEquals("resolvedsha", body.getString("commit_id"));
    }

    @Test
    public void testAddInlineReviewCommentEmptyPrResponseThrows() throws IOException {
        doReturn("").when(gitHub).execute(any(GenericRequest.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.addInlineReviewComment(WORKSPACE, REPOSITORY, "74",
                        "src/Foo.java", "10", "Comment", null, null, null));
        assertTrue(exception.getMessage().contains("Unable to resolve head commit SHA"));
    }

    @Test
    public void testAddInlineReviewCommentInvalidLineThrows() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.addInlineReviewComment(WORKSPACE, REPOSITORY, "74",
                        "src/Foo.java", "not-a-line", "Comment", "abc123", null, null));
        assertTrue(exception.getMessage().contains("Invalid line"));
    }

    @Test
    public void testAddInlineReviewCommentInvalidStartLineThrows() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.addInlineReviewComment(WORKSPACE, REPOSITORY, "74",
                        "src/Foo.java", "42", "Comment", "abc123", "not-a-line", null));
        assertTrue(exception.getMessage().contains("Invalid startLine"));
    }

    // ── GraphQL helpers ──────────────────────────────────────────────────────

    @Test
    public void testGetPullRequestReviewThreads() throws IOException {
        doReturn("{\"data\":{}}").when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.getPullRequestReviewThreads(WORKSPACE, REPOSITORY, "74");

        assertEquals("{\"data\":{}}", result);
        GenericRequest request = captureLastPost();
        assertTrue(request.url().endsWith("/graphql"));
        JSONObject body = new JSONObject(request.getBody());
        assertTrue(body.getString("query").contains("reviewThreads"));
        assertEquals(74, body.getJSONObject("variables").getInt("prNumber"));
        assertEquals(WORKSPACE, body.getJSONObject("variables").getString("owner"));
    }

    @Test
    public void testResolveReviewThread() throws IOException {
        doReturn("{\"data\":{}}").when(gitHub).post(any(GenericRequest.class));

        gitHub.resolveReviewThread("PRRT_kwDOBQfyNc5A");

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertTrue(body.getString("query").contains("resolveReviewThread"));
        assertTrue(body.getString("query").contains("PRRT_kwDOBQfyNc5A"));
        assertFalse(body.has("variables"));
    }

    // ── check runs / statuses / workflow runs ────────────────────────────────

    @Test
    public void testGetCommitCheckRuns() throws IOException {
        doReturn("{\"check_runs\":[]}").when(gitHub).execute(any(GenericRequest.class));

        String result = gitHub.getCommitCheckRuns(WORKSPACE, REPOSITORY, "abc123");

        assertEquals("{\"check_runs\":[]}", result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/commits/abc123/check-runs"));
    }

    @Test
    public void testCreateCommitStatusWithAllFields() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.createCommitStatus(WORKSPACE, REPOSITORY, "abc123", "pending",
                "AI analysis in progress...", "dmtools/pr-review", "https://ci.example/1");

        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/repos/testWorkspace/testRepo/statuses/abc123"));
        JSONObject body = new JSONObject(request.getBody());
        assertEquals("pending", body.getString("state"));
        assertEquals("AI analysis in progress...", body.getString("description"));
        assertEquals("dmtools/pr-review", body.getString("context"));
        assertEquals("https://ci.example/1", body.getString("target_url"));
    }

    @Test
    public void testCreateCommitStatusMinimal() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.createCommitStatus(WORKSPACE, REPOSITORY, "abc123", "success", null, " ", null);

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertEquals("success", body.getString("state"));
        assertFalse(body.has("description"));
        assertFalse(body.has("context"));
        assertFalse(body.has("target_url"));
    }

    @Test
    public void testCreateCheckRunWithOutput() throws IOException {
        doReturn("{\"id\":1}").when(gitHub).post(any(GenericRequest.class));

        gitHub.createCheckRun(WORKSPACE, REPOSITORY, "dmtools / pr-review", "abc123",
                "in_progress", "AI PR Review", "Analysis started", "Details", "MAPC-6653");

        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/repos/testWorkspace/testRepo/check-runs"));
        JSONObject body = new JSONObject(request.getBody());
        assertEquals("dmtools / pr-review", body.getString("name"));
        assertEquals("abc123", body.getString("head_sha"));
        assertEquals("in_progress", body.getString("status"));
        assertEquals("MAPC-6653", body.getString("external_id"));
        JSONObject output = body.getJSONObject("output");
        assertEquals("AI PR Review", output.getString("title"));
        assertEquals("Analysis started", output.getString("summary"));
        assertEquals("Details", output.getString("text"));
    }

    @Test
    public void testCreateCheckRunMinimalOmitsOutput() throws IOException {
        doReturn("{\"id\":1}").when(gitHub).post(any(GenericRequest.class));

        gitHub.createCheckRun(WORKSPACE, REPOSITORY, "check", "abc123",
                null, null, null, null, null);

        JSONObject body = new JSONObject(captureLastPost().getBody());
        assertFalse(body.has("status"));
        assertFalse(body.has("external_id"));
        assertFalse(body.has("output"));
    }

    @Test
    public void testUpdateCheckRun() throws IOException {
        doReturn("{\"id\":1}").when(gitHub).patch(any(GenericRequest.class));

        gitHub.updateCheckRun(WORKSPACE, REPOSITORY, "1234567890", "completed",
                "success", "Done", "All good", "Full text");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).patch(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/check-runs/1234567890"));
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("completed", body.getString("status"));
        assertEquals("success", body.getString("conclusion"));
        JSONObject output = body.getJSONObject("output");
        assertEquals("Done", output.getString("title"));
        assertEquals("All good", output.getString("summary"));
        assertEquals("Full text", output.getString("text"));
    }

    @Test
    public void testUpdateCheckRunMinimal() throws IOException {
        doReturn("{\"id\":1}").when(gitHub).patch(any(GenericRequest.class));

        gitHub.updateCheckRun(WORKSPACE, REPOSITORY, "1", "in_progress", null, null, null, null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).patch(captor.capture());
        JSONObject actual = new JSONObject(captor.getValue().getBody());
        assertEquals("in_progress", actual.getString("status"));
        assertFalse(actual.has("conclusion"));
        assertFalse(actual.has("output"));
    }

    @Test
    public void testGetWorkflowRunAndJobsAndLogs() throws IOException {
        doReturn("{\"status\":\"completed\"}").when(gitHub).execute(any(GenericRequest.class));

        assertEquals("{\"status\":\"completed\"}", gitHub.getWorkflowRun(WORKSPACE, REPOSITORY, "111"));
        assertEquals("{\"status\":\"completed\"}", gitHub.getWorkflowRunJobs(WORKSPACE, REPOSITORY, "111"));
        assertEquals("{\"status\":\"completed\"}", gitHub.getJobLogs(WORKSPACE, REPOSITORY, "222"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(3)).execute(captor.capture());
        List<GenericRequest> requests = captor.getAllValues();
        assertTrue(requests.get(0).url().contains("/actions/runs/111"));
        assertTrue(requests.get(1).url().contains("/actions/runs/111/jobs"));
        assertTrue(requests.get(2).url().contains("/actions/jobs/222/logs"));
    }

    // ── commits / branches / tags ────────────────────────────────────────────

    @Test
    public void testCommitCommentsNullResponse() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));

        assertNotNull(gitHub.commitComments(WORKSPACE, REPOSITORY, "abc123"));
    }

    @Test
    public void testCommitCommentsWithResponse() throws IOException {
        doReturn("{\"total_count\":1}").when(gitHub).execute(any(genericRequestMatcher()));

        assertNotNull(gitHub.commitComments(WORKSPACE, REPOSITORY, "abc123"));
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/commits/abc123/comments"));
    }

    @Test
    public void testGetTagsNullResponse() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));

        assertTrue(gitHub.getTags(WORKSPACE, REPOSITORY).isEmpty());
    }

    @Test
    public void testGetTagsWithResponse() throws IOException {
        doReturn("[{\"name\":\"v1.0\"},{\"name\":\"v2.0\"}]").when(gitHub).execute(any(GenericRequest.class));

        List<ITag> tags = gitHub.getTags(WORKSPACE, REPOSITORY);

        assertEquals(2, tags.size());
        assertEquals("v1.0", tags.get(0).getName());
    }

    @Test
    public void testGetBranchesPaginates() throws IOException {
        JSONArray fullPage = new JSONArray();
        for (int i = 0; i < 100; i++) {
            fullPage.put(new JSONObject().put("name", "branch-" + i));
        }
        JSONArray secondPage = new JSONArray().put(new JSONObject().put("name", "main"));
        doReturn(fullPage.toString(), secondPage.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        List<ITag> branches = gitHub.getBranches(WORKSPACE, REPOSITORY);

        assertEquals(101, branches.size());
        verify(gitHub, times(2)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testGetBranchesNullResponse() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));

        assertTrue(gitHub.getBranches(WORKSPACE, REPOSITORY).isEmpty());
    }

    @Test
    public void testGetCommitsBetween() throws IOException {
        doReturn("{\"commits\":[{\"sha\":\"a\"},{\"sha\":\"b\"}]}").when(gitHub).execute(any(GenericRequest.class));

        List<ICommit> commits = gitHub.getCommitsBetween(WORKSPACE, REPOSITORY, "main", "feature");

        assertEquals(2, commits.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/compare/main...feature"));
    }

    @Test
    public void testGetCommitsBetweenNullResponse() throws IOException {
        doReturn(null).when(gitHub).execute(any(GenericRequest.class));

        assertTrue(gitHub.getCommitsBetween(WORKSPACE, REPOSITORY, "main", "feature").isEmpty());
    }

    @Test
    public void testGetCommitsFromBranchWithDates() throws IOException {
        doReturn("[{\"sha\":\"a\"}]", "[]").when(gitHub).execute(any(GenericRequest.class));

        List<ICommit> commits = gitHub.getCommitsFromBranch(WORKSPACE, REPOSITORY, "main",
                "2024-01-01", "2024-12-31");

        assertEquals(1, commits.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(2)).execute(captor.capture());
        String url = captor.getAllValues().get(0).url();
        assertTrue(url.contains("sha=main"));
        assertTrue(url.contains("since=2024-01-01T00:00:00Z"));
        assertTrue(url.contains("until=2024-12-31T23:59:59Z"));
    }

    @Test
    public void testGetCommitsFromBranchEmptyResponse() throws IOException {
        doReturn("").when(gitHub).execute(any(GenericRequest.class));

        assertTrue(gitHub.getCommitsFromBranch(WORKSPACE, REPOSITORY, "main", null, null).isEmpty());
    }

    @Test
    public void testPerformCommitsFromBranchStopsWhenPerformerReturnsTrue() throws Exception {
        doReturn("[{\"sha\":\"a\"},{\"sha\":\"b\"}]", "[]").when(gitHub).execute(any(GenericRequest.class));
        List<String> performed = new ArrayList<>();

        gitHub.performCommitsFromBranch(WORKSPACE, REPOSITORY, "main", commit -> {
            performed.add(commit.getHash());
            return true;
        });

        assertEquals(1, performed.size());
        assertEquals("a", performed.get(0));
    }

    @Test
    public void testGetCommitsFromBranchesByRegex() throws IOException {
        List<ITag> branches = Arrays.asList(
                new GithubTag(new JSONObject().put("name", "feature/a")),
                new GithubTag(new JSONObject().put("name", "main")),
                new GithubTag(new JSONObject().put("name", "feature/b")));
        doReturn(branches).when(gitHub).getBranches(WORKSPACE, REPOSITORY);
        doReturn("[{\"sha\":\"h1\"}]", "[]",
                "[{\"sha\":\"h1\"},{\"sha\":\"h2\"}]", "[]").when(gitHub).execute(any(GenericRequest.class));

        List<ICommit> commits = gitHub.getCommitsFromBranchesByRegex(WORKSPACE, REPOSITORY, "^feature/", null);

        assertEquals(2, commits.size());
        assertEquals("h1", commits.get(0).getHash());
        assertEquals("h2", commits.get(1).getHash());
    }

    @Test
    public void testGetCommitDiffStat() throws IOException {
        doReturn("{\"sha\":\"abc\"}").when(gitHub).execute(any(GenericRequest.class));

        IDiffStats stats = gitHub.getCommitDiffStat(WORKSPACE, REPOSITORY, "abc");

        assertNotNull(stats);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/commits/abc"));
    }

    @Test
    public void testGetCommitDiff() throws IOException {
        doReturn("diff text").when(gitHub).execute(any(GenericRequest.class));

        IBody diff = gitHub.getCommitDiff(WORKSPACE, REPOSITORY, "abc");

        assertEquals("diff text", diff.getBody());
    }

    @Test
    public void testGetDiff() throws IOException {
        doReturn("[{\"filename\":\"a.java\"}]").when(gitHub).execute(any(GenericRequest.class));

        IBody diff = gitHub.getDiff(WORKSPACE, REPOSITORY, "74");

        assertEquals("[{\"filename\":\"a.java\"}]", diff.getBody());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/pulls/74/files"));
    }

    // ── pull request diff (flag dependent) ───────────────────────────────────

    @Test
    public void testGetPullRequestDiff() throws IOException {
        doReturn("+added line\n-removed line\n+++ header\n--- header\n")
                .when(gitHub).execute(any(GenericRequest.class));
        boolean flagEnabled = new PropertyReader().isReadPullRequestDiff();

        IDiffStats stats = gitHub.getPullRequestDiff(WORKSPACE, REPOSITORY, "74");

        assertNotNull(stats);
        if (flagEnabled) {
            assertEquals(2, stats.getStats().getTotal());
            assertEquals(1, stats.getStats().getAdditions());
            assertEquals(1, stats.getStats().getDeletions());
        } else {
            assertEquals(0, stats.getStats().getTotal());
        }
    }

    @Test
    public void testGetPullRequestDiffRestClientExceptionReturnsEmpty() throws IOException {
        Response errorResponse = new Response.Builder()
                .request(new Request.Builder().url("https://api.github.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .build();
        doThrow(new RestClient.RateLimitException("rate limited", "body", errorResponse, 403))
                .when(gitHub).execute(any(GenericRequest.class));
        boolean flagEnabled = new PropertyReader().isReadPullRequestDiff();

        IDiffStats stats = gitHub.getPullRequestDiff(WORKSPACE, REPOSITORY, "74");

        assertNotNull(stats);
        if (flagEnabled) {
            assertEquals(0, stats.getStats().getTotal());
        }
    }

    @Test
    public void testGetPullRequestDiffText() throws IOException {
        doReturn("+added\n-removed").when(gitHub).execute(any(GenericRequest.class));
        boolean flagEnabled = new PropertyReader().isReadPullRequestDiff();

        String diffText = gitHub.getPullRequestDiffText(WORKSPACE, REPOSITORY, "74");

        assertNotNull(diffText);
        if (flagEnabled) {
            assertEquals("+added\n-removed", diffText);
        } else {
            assertEquals("", diffText);
        }
    }

    @Test
    public void testParseDiffStats() {
        IDiffStats stats = GitHub.parseDiffStats(
                "+++ file.java\n--- file.java\n+line1\n+line2\n-line3\n context");

        assertEquals(2, stats.getStats().getAdditions());
        assertEquals(1, stats.getStats().getDeletions());
        assertEquals(3, stats.getStats().getTotal());
        assertTrue(stats.getChanges().isEmpty());
    }

    // ── file content ─────────────────────────────────────────────────────────

    @Test
    public void testGetSingleFileContentBase64() throws IOException {
        String encoded = JobRunner.encodeBase64("hello world");
        JSONObject response = new JSONObject().put("content", encoded).put("encoding", "base64");
        doReturn(response.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        String content = gitHub.getSingleFileContent(WORKSPACE, REPOSITORY, "main", "README.md");

        assertEquals("hello world", content);
    }

    @Test
    public void testGetSingleFileContentPlain() throws IOException {
        JSONObject response = new JSONObject().put("content", "plain content").put("encoding", "none");
        doReturn(response.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        String content = gitHub.getSingleFileContent(WORKSPACE, REPOSITORY, "main", "README.md");

        assertEquals("plain content", content);
    }

    @Test
    public void testGetSingleFileContentNotFound() throws IOException {
        doReturn(null).when(gitHub).execute(any(genericRequestMatcher()));

        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () ->
                gitHub.getSingleFileContent(WORKSPACE, REPOSITORY, "main", "missing.md"));
        assertTrue(exception.getMessage().contains("missing.md"));
    }

    @Test
    public void testGetListOfFiles() throws IOException {
        doReturn("{\"tree\":[{\"path\":\"a.java\"},{\"path\":\"b.java\"}]}")
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<IFile> files = gitHub.getListOfFiles(WORKSPACE, REPOSITORY, "main");

        assertEquals(2, files.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/git/trees/main?recursive=1"));
    }

    @Test
    public void testGetListOfFilesNullResponse() throws IOException {
        doReturn(null).when(gitHub).execute(any(genericRequestMatcher()));

        assertTrue(gitHub.getListOfFiles(WORKSPACE, REPOSITORY, "main").isEmpty());
    }

    @Test
    public void testGetFileContentFromApiUrl() throws IOException {
        String encoded = JobRunner.encodeBase64("api blob content");
        doReturn(new JSONObject().put("content", encoded).toString())
                .when(gitHub).execute(any(genericRequestMatcher()));

        String content = gitHub.getFileContent("https://api.github.com/repos/o/r/git/blobs/sha123");

        assertEquals("api blob content", content);
    }

    @Test
    public void testGetFileContentFromGithubWebUrl() throws IOException {
        String encoded = JobRunner.encodeBase64("web url content");
        doReturn(new JSONObject().put("sha", "sha999").toString(),
                new JSONObject().put("content", encoded).toString())
                .when(gitHub).execute(any(genericRequestMatcher()));

        String content = gitHub.getFileContent("https://github.com/owner/repo/blob/main/docs/guide.md");

        assertEquals("web url content", content);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(2)).execute(captor.capture());
        assertTrue(captor.getAllValues().get(0).url().contains("/repos/owner/repo/contents/docs/guide.md"));
        assertTrue(captor.getAllValues().get(1).url().contains("/repos/owner/repo/git/blobs/sha999"));
    }

    @Test
    public void testGetFileContentFromGithubWebUrlInvalid() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.getFileContent("https://github.com/owner/repo/blob/"));
        assertTrue(exception.getMessage().contains("Could not extract branch and path"));
    }

    @Test
    public void testGetFileContentFromRawUrl() throws IOException {
        String encoded = JobRunner.encodeBase64("raw content");
        doReturn(new JSONObject().put("sha", "sha111").toString(),
                new JSONObject().put("content", encoded).toString())
                .when(gitHub).execute(any(genericRequestMatcher()));

        String content = gitHub.getFileContent(
                "https://raw.githubusercontent.com/owner/repo/main/docs/guide.md");

        assertEquals("raw content", content);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(2)).execute(captor.capture());
        assertTrue(captor.getAllValues().get(0).url().contains("/repos/owner/repo/contents/docs/guide.md"));
        assertTrue(captor.getAllValues().get(1).url().contains("/repos/owner/repo/git/blobs/sha111"));
    }

    @Test
    public void testGetFileContentFromRawUrlInvalid() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.getFileContent("https://raw.githubusercontent.com/owner/repo"));
        assertTrue(exception.getMessage().contains("Could not parse raw GitHub URL"));
    }

    // ── uriToObject ──────────────────────────────────────────────────────────

    @Test
    public void testUriToObjectNullAndEmpty() throws Exception {
        assertNull(gitHub.uriToObject(null));
        assertNull(gitHub.uriToObject(""));
    }

    @Test
    public void testUriToObjectNonGithubUrl() throws Exception {
        assertNull(gitHub.uriToObject("https://example.com/file.md"));
    }

    @Test
    public void testUriToObjectStripsSmartLinkSuffixAndFetches() throws Exception {
        String encoded = JobRunner.encodeBase64("uri content");
        doReturn(new JSONObject().put("sha", "sha1").toString(),
                new JSONObject().put("content", encoded).toString())
                .when(gitHub).execute(any(genericRequestMatcher()));

        Object result = gitHub.uriToObject(
                "https://github.com/owner/repo/blob/main/file.md|https://github.com/owner/repo/blob/main/file.md|smart-link]");

        assertEquals("uri content", result);
    }

    // ── search files ─────────────────────────────────────────────────────────

    @Test
    public void testSearchFilesRespectsLimit() throws IOException, InterruptedException {
        doReturn("{\"items\":[{\"path\":\"a.java\"},{\"path\":\"b.java\"}]}")
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<IFile> files = gitHub.searchFiles(WORKSPACE, REPOSITORY, "foo bar", 1);

        assertEquals(1, files.size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("search/code?q=foo+bar+repo:testWorkspace/testRepo"));
    }

    @Test
    public void testSearchFilesUnlimitedStopsOnShortPage() throws IOException, InterruptedException {
        doReturn("{\"items\":[{\"path\":\"a.java\"}]}").when(gitHub).execute(any(genericRequestMatcher()));

        List<IFile> files = gitHub.searchFiles(WORKSPACE, REPOSITORY, "query", -1);

        assertEquals(1, files.size());
    }

    @Test
    public void testSearchFilesNullResponse() throws IOException, InterruptedException {
        doReturn(null).when(gitHub).execute(any(genericRequestMatcher()));

        assertTrue(gitHub.searchFiles(WORKSPACE, REPOSITORY, "query", 10).isEmpty());
    }

    @Test
    public void testSearchFilesRetriesAfterRateLimit() throws IOException, InterruptedException {
        Response errorResponse = new Response.Builder()
                .request(new Request.Builder().url("https://api.github.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("rate limited")
                // reset time in the past -> no actual sleeping
                .header("X-RateLimit-Reset", "1")
                .build();
        doThrow(new RestClient.RateLimitException("rate limited", "body", errorResponse, 403))
                .doReturn("{\"items\":[{\"path\":\"a.java\"}]}")
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<IFile> files = gitHub.searchFiles(WORKSPACE, REPOSITORY, "query", 10);

        assertEquals(1, files.size());
        verify(gitHub, times(2)).execute(any(genericRequestMatcher()));
    }

    // ── labels / merge / rename ──────────────────────────────────────────────

    @Test
    public void testAddPullRequestLabel() throws IOException {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        gitHub.addPullRequestLabel(WORKSPACE, REPOSITORY, "74", "bug");

        GenericRequest request = captureLastPost();
        assertTrue(request.url().contains("/repos/testWorkspace/testRepo/issues/74/labels"));
        assertEquals("bug", new JSONArray(request.getBody()).getString(0));
    }

    @Test
    public void testRemovePullRequestLabel() throws IOException {
        doReturn("").when(gitHub).delete(any(genericRequestMatcher()));

        gitHub.removePullRequestLabel(WORKSPACE, REPOSITORY, "74", "bug");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).delete(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/issues/74/labels/bug"));
    }

    @Test
    public void testMergePullRequestDefaults() throws IOException {
        doReturn("{\"merged\":true}").when(gitHub).put(any(GenericRequest.class));

        String result = gitHub.mergePullRequest(WORKSPACE, REPOSITORY, "74", null, null, null);

        assertEquals("{\"merged\":true}", result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).put(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/pulls/74/merge"));
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("merge", body.getString("merge_method"));
        assertFalse(body.has("commit_title"));
        assertFalse(body.has("commit_message"));
    }

    @Test
    public void testMergePullRequestSquashWithTitleAndMessage() throws IOException {
        doReturn("{\"merged\":true}").when(gitHub).put(any(GenericRequest.class));

        gitHub.mergePullRequest(WORKSPACE, REPOSITORY, "74", "squash", "Title", "Closes #1");

        JSONObject body = new JSONObject(captorPut().getBody());
        assertEquals("squash", body.getString("merge_method"));
        assertEquals("Title", body.getString("commit_title"));
        assertEquals("Closes #1", body.getString("commit_message"));
    }

    @Test
    public void testRenamePullRequestNoChange() throws IOException {
        IPullRequest pullRequest = mock(IPullRequest.class);
        when(pullRequest.getTitle()).thenReturn("Same Title");

        String result = gitHub.renamePullRequest(WORKSPACE, REPOSITORY, pullRequest, "same title");

        assertEquals("", result);
        verify(gitHub, never()).patch(any(GenericRequest.class));
    }

    @Test
    public void testRenamePullRequestChanged() throws IOException {
        IPullRequest pullRequest = mock(IPullRequest.class);
        when(pullRequest.getTitle()).thenReturn("Old Title");
        when(pullRequest.getId()).thenReturn(74);
        doReturn("{}").when(gitHub).patch(any(GenericRequest.class));

        gitHub.renamePullRequest(WORKSPACE, REPOSITORY, pullRequest, "New Title");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).patch(captor.capture());
        assertTrue(captor.getValue().url().contains("/repos/testWorkspace/testRepo/pulls/74"));
        assertEquals("New Title", new JSONObject(captor.getValue().getBody()).getString("title"));
    }

    @Test
    public void testRenamePullRequestKeepsWipPrefix() throws IOException {
        IPullRequest pullRequest = mock(IPullRequest.class);
        when(pullRequest.getTitle()).thenReturn("[WIP] Old Title");
        when(pullRequest.getId()).thenReturn(74);
        doReturn("{}").when(gitHub).patch(any(GenericRequest.class));

        gitHub.renamePullRequest(WORKSPACE, REPOSITORY, pullRequest, "New Title");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).patch(captor.capture());
        assertEquals("[WIP] New Title", new JSONObject(captor.getValue().getBody()).getString("title"));
    }

    // ── unsupported operations ───────────────────────────────────────────────

    @Test
    public void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class, () -> gitHub.addTask(1, "text"));
        assertThrows(UnsupportedOperationException.class, () ->
                gitHub.createPullRequestCommentAndTaskIfNotExists(WORKSPACE, REPOSITORY, "74", "c", "t"));
        assertThrows(UnsupportedOperationException.class, () ->
                gitHub.createPullRequestCommentAndTask(WORKSPACE, REPOSITORY, "74", "c", "t"));
        assertThrows(UnsupportedOperationException.class, () -> gitHub.getRepositories("ns"));
    }

    // ── comments / conversations / activities ────────────────────────────────

    @Test
    public void testPullRequestCommentsMergedAndSorted() throws IOException {
        JSONArray inline = new JSONArray()
                .put(commentJson(2L, null, "2024-01-16T10:00:00Z", "inline"));
        JSONArray issue = new JSONArray()
                .put(commentJson(1L, null, "2024-01-15T10:00:00Z", "issue"));
        doReturn(inline.toString(), issue.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        List<IComment> comments = gitHub.pullRequestComments(WORKSPACE, REPOSITORY, "74");

        assertEquals(2, comments.size());
        assertEquals("1", comments.get(0).getId());
        assertEquals("2", comments.get(1).getId());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(2)).execute(captor.capture());
        assertTrue(captor.getAllValues().get(0).url().contains("/pulls/74/comments"));
        assertTrue(captor.getAllValues().get(1).url().contains("/issues/74/comments"));
    }

    @Test
    public void testPullRequestCommentsEmptyResponses() throws IOException {
        doReturn("", "").when(gitHub).execute(any(genericRequestMatcher()));

        assertTrue(gitHub.pullRequestComments(WORKSPACE, REPOSITORY, "74").isEmpty());
    }

    @Test
    public void testGetPRConversations() throws IOException {
        JSONArray inline = new JSONArray()
                .put(commentJson(1L, null, "2024-01-15T10:00:00Z", "root"))
                .put(commentJson(2L, 1L, "2024-01-16T10:00:00Z", "reply"))
                .put(commentJson(3L, 99L, "2024-01-17T10:00:00Z", "orphan"));
        JSONArray issue = new JSONArray()
                .put(commentJson(4L, null, "2024-01-18T10:00:00Z", "issue comment"));
        doReturn(inline.toString(), issue.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        List<GitHubConversation> conversations = gitHub.getPRConversations(WORKSPACE, REPOSITORY, "74");

        assertEquals(3, conversations.size());
        GitHubConversation root = conversations.get(0);
        assertEquals("1", root.getRootComment().getId());
        assertEquals(1, root.getReplies().size());
        assertEquals("2", root.getReplies().get(0).getId());
    }

    @Test
    public void testPullRequestActivities() throws IOException {
        JSONArray reviews = new JSONArray()
                .put(new JSONObject()
                        .put("id", 1L)
                        .put("state", "APPROVED")
                        .put("body", "ok")
                        .put("submitted_at", "2024-01-15T10:00:00Z")
                        .put("user", new JSONObject().put("login", "reviewer")));
        JSONArray inline = new JSONArray()
                .put(commentJson(2L, null, "2024-01-16T10:00:00Z", "inline"));
        JSONArray issue = new JSONArray()
                .put(commentJson(3L, null, "2024-01-17T10:00:00Z", "issue"));
        doReturn(reviews.toString(), inline.toString(), issue.toString())
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<com.github.istin.dmtools.common.model.IActivity> activities =
                gitHub.pullRequestActivities(WORKSPACE, REPOSITORY, "74");

        assertEquals(3, activities.size());
        assertEquals("APPROVED", activities.get(0).getAction());
    }

    @Test
    public void testPullRequestTasksOnlyChangesRequested() throws IOException {
        JSONArray reviews = new JSONArray()
                .put(new JSONObject().put("id", 1L).put("state", "CHANGES_REQUESTED")
                        .put("body", "fix it").put("submitted_at", "2024-01-15T10:00:00Z")
                        .put("user", new JSONObject().put("login", "reviewer")))
                .put(new JSONObject().put("id", 2L).put("state", "APPROVED")
                        .put("body", "ok").put("submitted_at", "2024-01-16T10:00:00Z")
                        .put("user", new JSONObject().put("login", "reviewer")));
        doReturn(reviews.toString(), "", "").when(gitHub).execute(any(genericRequestMatcher()));

        List<com.github.istin.dmtools.common.model.ITask> tasks =
                gitHub.pullRequestTasks(WORKSPACE, REPOSITORY, "74");

        assertEquals(1, tasks.size());
    }

    // ── workflow status / summary / artifacts ────────────────────────────────

    @Test
    public void testGetWorkflowStatus() throws IOException {
        doReturn("{\"status\":\"completed\"}").when(gitHub).execute(any(genericRequestMatcher()));

        assertEquals("completed", gitHub.getWorkflowStatus(WORKSPACE, REPOSITORY, 123L));
    }

    @Test
    public void testGetWorkflowStatusEmptyResponseThrows() throws IOException {
        doReturn("").when(gitHub).execute(any(genericRequestMatcher()));

        IOException exception = assertThrows(IOException.class, () ->
                gitHub.getWorkflowStatus(WORKSPACE, REPOSITORY, 123L));
        assertEquals("Failed to get workflow status", exception.getMessage());
    }

    @Test
    public void testGetWorkflowSummaryWithoutResponseArtifact() throws IOException {
        doReturn("{\"status\":\"completed\"}", "{\"artifacts\":[{\"name\":\"logs\",\"archive_download_url\":\"http://x\"}]}")
                .when(gitHub).execute(any(genericRequestMatcher()));

        String summary = gitHub.getWorkflowSummary(WORKSPACE, REPOSITORY, 123L);

        assertEquals("", summary);
    }

    @Test
    public void testGetWorkflowSummaryEmptyResponseThrows() throws IOException {
        doReturn("").when(gitHub).execute(any(genericRequestMatcher()));

        IOException exception = assertThrows(IOException.class, () ->
                gitHub.getWorkflowSummary(WORKSPACE, REPOSITORY, 123L));
        assertEquals("Failed to get workflow summary", exception.getMessage());
    }

    @Test
    public void testGetAnalysisResponseFromArtifactsNoMatch() throws IOException {
        doReturn("{\"artifacts\":[{\"name\":\"test-logs\",\"archive_download_url\":\"http://x\"}]}")
                .when(gitHub).execute(any(genericRequestMatcher()));

        assertNull(gitHub.getAnalysisResponseFromArtifacts(WORKSPACE, REPOSITORY, 123L));
    }

    @Test
    public void testGetAnalysisResponseFromArtifactsExceptionReturnsNull() throws IOException {
        doThrow(new IOException("boom")).when(gitHub).execute(any(genericRequestMatcher()));

        assertNull(gitHub.getAnalysisResponseFromArtifacts(WORKSPACE, REPOSITORY, 123L));
    }

    // ── payload processing ───────────────────────────────────────────────────

    @Test
    public void testProcessLargePayloadNull() {
        assertEquals(JobRunner.encodeBase64(""), gitHub.processLargePayload(null));
    }

    @Test
    public void testProcessLargePayloadSmallRequest() {
        assertEquals(JobRunner.encodeBase64("small request"), gitHub.processLargePayload("small request"));
    }

    @Test
    public void testProcessLargePayloadCompressibleRequest() {
        String request = repeat("repetitive content ", 20000); // ~360KB, compresses well

        String result = gitHub.processLargePayload(request);

        assertTrue(result.startsWith("GZIP_COMPRESSED:"));
        assertTrue(result.length() <= 60000);
    }

    @Test
    public void testProcessLargePayloadIncompressibleJsonIsTruncated() {
        String request = "{\"request\":\"" + randomString(200000) + "\",\"summary\":\"s\"}";

        String result = gitHub.processLargePayload(request);

        String decoded = JobRunner.decodeBase64(result);
        assertTrue(decoded.contains("_truncation_note"));
        assertTrue(decoded.contains("\"summary\":\"s\""));
    }

    @Test
    public void testProcessLargePayloadIncompressiblePlainTextIsTruncated() {
        String request = randomString(200000);

        String result = gitHub.processLargePayload(request);

        String decoded = JobRunner.decodeBase64(result);
        assertTrue(decoded.contains("[TRUNCATED - Original size: 200000 characters]"));
        assertTrue(decoded.length() < request.length());
    }

    @Test
    public void testProcessLargePayloadInvalidJsonFallsBackToPlainTruncation() {
        String request = "{not valid json " + randomString(200000);

        String result = gitHub.processLargePayload(request);

        String decoded = JobRunner.decodeBase64(result);
        assertTrue(decoded.contains("[TRUNCATED"));
        assertFalse(decoded.contains("_truncation_note"));
    }

    // ── hook URL parsing ─────────────────────────────────────────────────────

    @Test
    public void testCallHookAndWaitResponseUnsupportedUrl() throws Exception {
        String result = gitHub.callHookAndWaitResponse("https://example.com/some/hook", "params");

        assertTrue(result.startsWith("Error: Unsupported GitHub URL format"));
    }

    @Test
    public void testCallHookAndWaitResponseInvalidWebUrl() throws Exception {
        String result = gitHub.callHookAndWaitResponse(
                "https://github.com/owner/repo/tree/x/actions/workflows/rework.yml", "params");

        assertTrue(result.startsWith("Error: Invalid GitHub web URL format"));
    }

    // ── matchers / captors ───────────────────────────────────────────────────

    private GenericRequest captorPut() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, atLeastOnce()).put(captor.capture());
        List<GenericRequest> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    private static Class<GenericRequest> genericRequestMatcher() {
        return GenericRequest.class;
    }
}
