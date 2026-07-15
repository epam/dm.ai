// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IFile;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.github.model.GitHubConversation;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Coverage-oriented tests for the network-bound and wait-loop parts of {@link GitHub}.
 * All HTTP traffic goes to a local in-process HttpServer (localhost only, unique ping
 * token per run - same pattern as AbstractRestClientCoverageTest); the static
 * GitHubWorkflowUtils HTTP entry points are stubbed with mockStatic, and unused
 * private helpers are exercised via reflection. No external network is used.
 */
public class GitHubNetworkCoverageTest {

    private static final String BASE_PATH = "https://api.github.com";
    private static final String WORKSPACE = "testWorkspace";
    private static final String REPOSITORY = "testRepo";
    private static final String RESPONSE_MD_CONTENT = "analysis result content";

    private HttpServer server;
    private String baseUrl;
    private String pingToken;
    private GitHub gitHub;
    private File tempAsset;
    private byte[] zipWithResponse;
    private byte[] zipWithoutResponse;

    @Before
    public void setUp() throws IOException {
        pingToken = UUID.randomUUID().toString();
        zipWithResponse = buildZip("response.md", RESPONSE_MD_CONTENT);
        zipWithoutResponse = buildZip("other.txt", "irrelevant");
        for (int attempt = 0; attempt < 5; attempt++) {
            server = createServer();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            if (verifyServerOwnership()) {
                break;
            }
            server.stop(0);
            server = null;
        }
        if (server == null) {
            fail("Could not bind a uniquely owned local HTTP server port");
        }
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
        if (server != null) {
            server.stop(0);
        }
        if (tempAsset != null) {
            Files.deleteIfExists(tempAsset.toPath());
        }
    }

    private HttpServer createServer() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            byte[] bytes;
            switch (path) {
                case "/__ping":
                    bytes = pingToken.getBytes(StandardCharsets.UTF_8);
                    break;
                case "/asset-upload":
                    bytes = "{\"id\":42,\"name\":\"f.txt\"}".getBytes(StandardCharsets.UTF_8);
                    break;
                case "/asset-error":
                    status = 500;
                    bytes = "server error".getBytes(StandardCharsets.UTF_8);
                    break;
                case "/artifact-zip":
                    bytes = zipWithResponse;
                    break;
                case "/artifact-no-md":
                    bytes = zipWithoutResponse;
                    break;
                case "/artifact-empty":
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                    return;
                case "/artifact-404":
                    status = 404;
                    bytes = "not found".getBytes(StandardCharsets.UTF_8);
                    break;
                default:
                    status = 404;
                    bytes = "unknown".getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    private boolean verifyServerOwnership() {
        for (int i = 0; i < 5; i++) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/__ping").openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                try (java.io.InputStream in = connection.getInputStream()) {
                    if (!pingToken.equals(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
                        return false;
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private static byte[] buildZip(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static String artifactsJson(String name, String downloadUrl) {
        JSONObject artifact = new JSONObject().put("name", name);
        if (downloadUrl != null) {
            artifact.put("archive_download_url", downloadUrl);
        }
        return new JSONObject().put("artifacts", new JSONArray().put(artifact)).toString();
    }

    // ── uploadReleaseAssetBinary / executeRaw via local server ───────────────

    @Test
    public void testUploadReleaseAssetBinarySuccess() throws IOException {
        tempAsset = Files.createTempFile("asset", ".txt").toFile();
        Files.write(tempAsset.toPath(), "payload".getBytes(StandardCharsets.UTF_8));

        String result = gitHub.uploadReleaseAssetBinary(baseUrl + "/asset-upload", tempAsset, "text/plain");

        assertEquals("{\"id\":42,\"name\":\"f.txt\"}", result);
    }

    @Test
    public void testUploadReleaseAssetBinaryInvalidContentType() throws IOException {
        tempAsset = Files.createTempFile("asset", ".txt").toFile();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.uploadReleaseAssetBinary(baseUrl + "/asset-upload", tempAsset, "invalid"));
        assertTrue(exception.getMessage().contains("Invalid content type"));
    }

    @Test
    public void testUploadReleaseAssetBinaryServerError() throws IOException {
        tempAsset = Files.createTempFile("asset", ".txt").toFile();
        Files.write(tempAsset.toPath(), "payload".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () ->
                gitHub.uploadReleaseAssetBinary(baseUrl + "/asset-error", tempAsset, "text/plain"));
    }

    @Test
    public void testGetAnalysisResponseFromArtifactsDownloadsAndExtractsZip() throws IOException {
        doReturn(artifactsJson("response-analysis", baseUrl + "/artifact-zip"))
                .when(gitHub).execute(any(GenericRequest.class));

        String result = gitHub.getAnalysisResponseFromArtifacts(WORKSPACE, REPOSITORY, 555L);

        assertEquals(RESPONSE_MD_CONTENT, result);
    }

    @Test
    public void testGetAnalysisResponseFromArtifactsEmptyDownload() throws IOException {
        doReturn(artifactsJson("response-analysis", baseUrl + "/artifact-empty"))
                .when(gitHub).execute(any(GenericRequest.class));

        assertNull(gitHub.getAnalysisResponseFromArtifacts(WORKSPACE, REPOSITORY, 555L));
    }

    @Test
    public void testGetAnalysisResponseFromArtifactsZipWithoutResponseMd() throws IOException {
        doReturn(artifactsJson("response-analysis", baseUrl + "/artifact-no-md"))
                .when(gitHub).execute(any(GenericRequest.class));

        assertNull(gitHub.getAnalysisResponseFromArtifacts(WORKSPACE, REPOSITORY, 555L));
    }

    @Test
    public void testGetAnalysisResponseFromArtifactsDownloadHttpError() throws IOException {
        doReturn(artifactsJson("response-analysis", baseUrl + "/artifact-404"))
                .when(gitHub).execute(any(GenericRequest.class));

        String result = gitHub.getAnalysisResponseFromArtifacts(WORKSPACE, REPOSITORY, 555L);

        assertNotNull(result);
        assertTrue(result.startsWith("Analysis response artifact available but extraction failed."));
    }

    @Test
    public void testGetWorkflowSummaryReturnsArtifactContent() throws IOException {
        doReturn("{\"status\":\"completed\"}", artifactsJson("response-analysis", baseUrl + "/artifact-zip"))
                .when(gitHub).execute(any(GenericRequest.class));

        String summary = gitHub.getWorkflowSummary(WORKSPACE, REPOSITORY, 555L);

        assertEquals(RESPONSE_MD_CONTENT, summary);
    }

    // ── static GitHubWorkflowUtils entry points ──────────────────────────────

    @Test
    public void testGetWorkflowRunLogs() throws IOException {
        try (MockedStatic<GitHubWorkflowUtils> utils = mockStatic(GitHubWorkflowUtils.class)) {
            utils.when(() -> GitHubWorkflowUtils.downloadWorkflowRunLogs(gitHub, WORKSPACE, REPOSITORY, "123"))
                    .thenReturn("log content");

            assertEquals("log content", gitHub.getWorkflowRunLogs(WORKSPACE, REPOSITORY, "123"));
        }
    }

    @Test
    public void testTriggerWorkflowFourArgDelegatesToUtils() throws IOException {
        try (MockedStatic<GitHubWorkflowUtils> utils = mockStatic(GitHubWorkflowUtils.class)) {
            String result = gitHub.triggerWorkflow(WORKSPACE, REPOSITORY, "rework.yml", "request-params");

            assertEquals("Workflow 'rework.yml' triggered successfully on testWorkspace/testRepo", result);
            utils.verify(() -> GitHubWorkflowUtils.triggerWorkflow(gitHub, WORKSPACE, REPOSITORY,
                    "rework.yml", "request-params"));
        }
    }

    // ── callHookAndWaitResponse flow (mockStatic + fast immediate responses) ──

    private void stubWorkflowApiResponses(String status) throws IOException {
        String runsJson = new JSONObject().put("workflow_runs", new JSONArray().put(
                new JSONObject().put("id", 555L).put("created_at", Instant.now().toString()))).toString();
        doAnswer(invocation -> {
            String url = ((GenericRequest) invocation.getArgument(0)).url();
            if (url.contains("/actions/workflows/") && url.contains("/runs")) {
                return runsJson;
            }
            if (url.endsWith("/artifacts")) {
                return artifactsJson("response-analysis", baseUrl + "/artifact-zip");
            }
            if (url.contains("/actions/runs/")) {
                return new JSONObject().put("status", status).toString();
            }
            return "";
        }).when(gitHub).execute(any(GenericRequest.class));
    }

    @Test
    public void testCallHookAndWaitResponseWebUrlHappyPath() throws Exception {
        try (MockedStatic<GitHubWorkflowUtils> utils = mockStatic(GitHubWorkflowUtils.class)) {
            stubWorkflowApiResponses("completed");

            String result = gitHub.callHookAndWaitResponse(
                    "https://github.com/owner/repo/actions/workflows/rework.yml", "params");

            assertEquals(RESPONSE_MD_CONTENT, result);
            utils.verify(() -> GitHubWorkflowUtils.triggerWorkflow(gitHub, "owner", "repo",
                    "rework.yml", "params"));
        }
    }

    @Test
    public void testCallHookAndWaitResponseApiUrlFailureStatus() throws Exception {
        try (MockedStatic<GitHubWorkflowUtils> utils = mockStatic(GitHubWorkflowUtils.class)) {
            stubWorkflowApiResponses("failure");

            // enterprise-style host: the web-URL branch requires github.com, so this
            // reaches the API dispatch URL parsing branch
            String result = gitHub.callHookAndWaitResponse(
                    "https://ghes.example.com/repos/owner/repo/actions/workflows/123/dispatches", "params");

            // failure status counts as completed -> summary is returned
            assertEquals(RESPONSE_MD_CONTENT, result);
            utils.verify(() -> GitHubWorkflowUtils.triggerWorkflow(gitHub, "owner", "repo", "123", "params"));
        }
    }

    @Test
    public void testCallHookAndWaitResponseApiUrlTooShort() throws Exception {
        String result = gitHub.callHookAndWaitResponse(
                "http://x/actions/workflows/dispatches", "params");

        assertTrue(result.startsWith("Error: Cannot parse GitHub API workflow URL"));
    }

    @Test
    public void testCallHookAndWaitResponseInterrupted() throws Exception {
        try (MockedStatic<GitHubWorkflowUtils> utils = mockStatic(GitHubWorkflowUtils.class)) {
            Thread.currentThread().interrupt();
            try {
                String result = gitHub.callHookAndWaitResponse(
                        "https://github.com/owner/repo/actions/workflows/rework.yml", "params");
                assertEquals("Error: Workflow processing was interrupted", result);
            } finally {
                // callHookAndWaitResponse restores the interrupt flag; clear it for other tests
                Thread.interrupted();
            }
        }
    }

    @Test
    public void testCallHookAndWaitResponseTriggerFailure() throws Exception {
        try (MockedStatic<GitHubWorkflowUtils> utils = mockStatic(GitHubWorkflowUtils.class)) {
            utils.when(() -> GitHubWorkflowUtils.triggerWorkflow(gitHub, "owner", "repo",
                    "rework.yml", "params")).thenThrow(new IOException("kaboom"));

            String result = gitHub.callHookAndWaitResponse(
                    "https://github.com/owner/repo/actions/workflows/rework.yml", "params");

            assertEquals("Error: kaboom", result);
        }
    }

    // ── private helpers via reflection (unused / wait-loop code) ─────────────

    @Test
    public void testGetWorkflowTriggerErrorDetailsViaReflection() throws Exception {
        Method method = GitHub.class.getDeclaredMethod("getWorkflowTriggerErrorDetails",
                Exception.class, String.class, String.class, String.class);
        method.setAccessible(true);

        String s404 = (String) method.invoke(gitHub, new IOException("404 Not Found"), "o", "r", "wf.yml");
        assertTrue(s404.contains("Workflow file 'wf.yml' not found"));

        String s403 = (String) method.invoke(gitHub, new IOException("403 Forbidden"), "o", "r", "wf.yml");
        assertTrue(s403.contains("Insufficient permissions"));

        String s422 = (String) method.invoke(gitHub, new IOException("422 Unprocessable"), "o", "r", "wf.yml");
        assertTrue(s422.contains("Invalid workflow inputs format"));

        String s422large = (String) method.invoke(gitHub,
                new IOException("422: inputs are too large"), "o", "r", "wf.yml");
        assertTrue(s422large.contains("exceed size limit"));

        String s401 = (String) method.invoke(gitHub, new IOException("401 Unauthorized"), "o", "r", "wf.yml");
        assertTrue(s401.contains("token is invalid or expired"));

        String generic = (String) method.invoke(gitHub, new IOException("timeout"), "o", "r", "wf.yml");
        assertTrue(generic.contains("workflow_dispatch"));

        String nullMessage = (String) method.invoke(gitHub, new IOException(), "o", "r", "wf.yml");
        assertTrue(nullMessage.contains("workflow_dispatch"));
    }

    @Test
    public void testGetWorkflowArtifactsViaReflection() throws Exception {
        Method method = GitHub.class.getDeclaredMethod("getWorkflowArtifacts",
                String.class, String.class, Long.class);
        method.setAccessible(true);

        doReturn(artifactsJson("build-logs", "http://x/y")).when(gitHub).execute(any(GenericRequest.class));
        String summary = (String) method.invoke(gitHub, WORKSPACE, REPOSITORY, 555L);
        assertNotNull(summary);
        assertTrue(summary.contains("build-logs"));
        assertTrue(summary.contains("http://x/y"));

        doThrow(new IOException("boom")).when(gitHub).execute(any(GenericRequest.class));
        assertNull(method.invoke(gitHub, WORKSPACE, REPOSITORY, 555L));
    }

    @Test
    public void testFindLatestWorkflowRunViaReflection() throws Exception {
        Method method = GitHub.class.getDeclaredMethod("findLatestWorkflowRun",
                String.class, String.class, String.class);
        method.setAccessible(true);

        String runsJson = new JSONObject().put("workflow_runs", new JSONArray().put(
                new JSONObject().put("id", 987L))).toString();
        doReturn(runsJson).when(gitHub).execute(any(GenericRequest.class));
        assertEquals(987L, method.invoke(gitHub, "o", "r", "wf.yml"));

        doReturn("{\"workflow_runs\":[]}").when(gitHub).execute(any(GenericRequest.class));
        assertNull(method.invoke(gitHub, "o", "r", "wf.yml"));
    }

    @Test
    public void testWaitForWorkflowCompletionCancelledViaReflection() throws Exception {
        Method method = GitHub.class.getDeclaredMethod("waitForWorkflowCompletion",
                String.class, String.class, Long.class);
        method.setAccessible(true);
        doReturn("{\"status\":\"cancelled\"}").when(gitHub).execute(any(GenericRequest.class));

        assertEquals(Boolean.TRUE, method.invoke(gitHub, WORKSPACE, REPOSITORY, 555L));
    }

    @Test
    public void testIsRateLimitedViaReflection() throws Exception {
        Method method = GitHub.class.getDeclaredMethod("isRateLimited", JSONObject.class);
        method.setAccessible(true);

        assertEquals(Boolean.TRUE, method.invoke(gitHub,
                new JSONObject().put("rate", new JSONObject().put("remaining", 0))));
        assertEquals(Boolean.FALSE, method.invoke(gitHub,
                new JSONObject().put("rate", new JSONObject().put("remaining", 5))));
        assertEquals(Boolean.FALSE, method.invoke(gitHub, new JSONObject()));
    }

    // ── pagination edge paths ────────────────────────────────────────────────

    private static JSONArray commentsPage(int startId, int count) {
        JSONArray page = new JSONArray();
        for (int i = 0; i < count; i++) {
            long id = startId + i;
            page.put(new JSONObject()
                    .put("id", id)
                    .put("created_at", "2024-01-15T10:00:00Z")
                    .put("body", "comment " + id)
                    .put("user", new JSONObject().put("login", "user")));
        }
        return page;
    }

    @Test
    public void testPullRequestCommentsFullPagePagination() throws IOException {
        doReturn(commentsPage(1, 100).toString(), commentsPage(101, 2).toString(),
                commentsPage(201, 100).toString(), commentsPage(301, 1).toString())
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<com.github.istin.dmtools.common.model.IComment> comments =
                gitHub.pullRequestComments(WORKSPACE, REPOSITORY, "74");

        assertEquals(203, comments.size());
        verify(gitHub, org.mockito.Mockito.times(4)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testGetPRConversationsFullPagePagination() throws IOException {
        doReturn(commentsPage(1, 100).toString(), commentsPage(101, 1).toString(), "[]")
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<GitHubConversation> conversations = gitHub.getPRConversations(WORKSPACE, REPOSITORY, "74");

        assertEquals(101, conversations.size());
        verify(gitHub, org.mockito.Mockito.times(3)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testPullRequestActivitiesFullReviewsPage() throws IOException {
        JSONArray reviews = new JSONArray();
        for (int i = 0; i < 100; i++) {
            reviews.put(new JSONObject().put("id", i + 1L).put("state", "APPROVED")
                    .put("body", "ok").put("submitted_at", "2024-01-15T10:00:00Z")
                    .put("user", new JSONObject().put("login", "reviewer")));
        }
        doReturn(reviews.toString(), "[]", "", "").when(gitHub).execute(any(genericRequestMatcher()));

        List<com.github.istin.dmtools.common.model.IActivity> activities =
                gitHub.pullRequestActivities(WORKSPACE, REPOSITORY, "74");

        assertEquals(100, activities.size());
        verify(gitHub, org.mockito.Mockito.times(4)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testPullRequestActivitiesCommentFetchFailuresAreSwallowed() throws IOException {
        // reviews empty -> break; inline comments throw; issue comments throw
        doReturn("").doThrow(new IOException("inline failed")).doThrow(new IOException("issue failed"))
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<com.github.istin.dmtools.common.model.IActivity> activities =
                gitHub.pullRequestActivities(WORKSPACE, REPOSITORY, "74");

        assertTrue(activities.isEmpty());
    }

    // ── release lookup edge paths ────────────────────────────────────────────

    @Test
    public void testGetOrCreateDraftReleaseCreatesOnEmptyResponse() throws IOException {
        doReturn("").when(gitHub).execute(any(genericRequestMatcher()));
        doReturn(new JSONObject().put("id", 999L).put("draft", true).toString())
                .when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.getOrCreateDraftRelease(WORKSPACE, REPOSITORY,
                "storage-tag", "Storage", "main", "notes");

        assertEquals(999L, new JSONObject(result).getLong("id"));
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).post(captor.capture());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("storage-tag", body.getString("tag_name"));
        assertEquals("Storage", body.getString("name"));
        assertTrue(body.getBoolean("draft"));
        assertEquals("main", body.getString("target_commitish"));
        assertEquals("notes", body.getString("body"));
    }

    @Test
    public void testGetOrCreateDraftReleaseMatchesByName() throws IOException {
        JSONArray releases = new JSONArray().put(new JSONObject()
                .put("id", 321L).put("tag_name", "other-tag").put("name", "Storage").put("draft", true));
        doReturn(releases.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        String result = gitHub.getOrCreateDraftRelease(WORKSPACE, REPOSITORY,
                "storage-tag", "Storage", null, null);

        assertEquals(321L, new JSONObject(result).getLong("id"));
        verify(gitHub, org.mockito.Mockito.never()).post(any(GenericRequest.class));
    }

    @Test
    public void testGetOrCreateDraftReleasePaginatesReleases() throws IOException {
        JSONArray fullPage = new JSONArray();
        for (int i = 0; i < 100; i++) {
            fullPage.put(new JSONObject().put("id", i).put("tag_name", "tag-" + i)
                    .put("name", "Release " + i).put("draft", true));
        }
        JSONArray secondPage = new JSONArray().put(new JSONObject()
                .put("id", 777L).put("tag_name", "storage-tag").put("name", "X").put("draft", true));
        doReturn(fullPage.toString(), secondPage.toString()).when(gitHub).execute(any(genericRequestMatcher()));

        String result = gitHub.getOrCreateDraftRelease(WORKSPACE, REPOSITORY,
                "storage-tag", "Storage", null, null);

        assertEquals(777L, new JSONObject(result).getLong("id"));
        verify(gitHub, org.mockito.Mockito.times(2)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testUploadReleaseAssetOverwriteWithEmptyAssetsResponse() throws IOException {
        tempAsset = Files.createTempFile("asset", ".txt").toFile();
        Files.write(tempAsset.toPath(), "data".getBytes(StandardCharsets.UTF_8));
        doReturn("").when(gitHub).execute(any(genericRequestMatcher()));
        doReturn("{}").when(gitHub).uploadReleaseAssetBinary(
                org.mockito.ArgumentMatchers.anyString(), any(File.class),
                org.mockito.ArgumentMatchers.anyString());

        gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "1", tempAsset.getAbsolutePath(),
                null, null, null, "true");

        verify(gitHub, org.mockito.Mockito.never()).delete(any(GenericRequest.class));
    }

    @Test
    public void testUploadReleaseAssetContentTypeDetection() throws IOException {
        tempAsset = Files.createTempFile("detect-me", ".txt").toFile();
        Files.write(tempAsset.toPath(), "data".getBytes(StandardCharsets.UTF_8));
        doReturn("{}").when(gitHub).uploadReleaseAssetBinary(
                org.mockito.ArgumentMatchers.anyString(), any(File.class),
                org.mockito.ArgumentMatchers.anyString());

        gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "1", tempAsset.getAbsolutePath(),
                null, null, null, "false");

        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHub).uploadReleaseAssetBinary(org.mockito.ArgumentMatchers.anyString(),
                any(File.class), contentTypeCaptor.capture());
        assertEquals("text/plain", contentTypeCaptor.getValue());
    }

    @Test
    public void testUploadReleaseAssetUnknownContentTypeFallsBackToOctetStream() throws IOException {
        tempAsset = Files.createTempFile("detect-me", ".unknownext123").toFile();
        Files.write(tempAsset.toPath(), "data".getBytes(StandardCharsets.UTF_8));
        doReturn("{}").when(gitHub).uploadReleaseAssetBinary(
                org.mockito.ArgumentMatchers.anyString(), any(File.class),
                org.mockito.ArgumentMatchers.anyString());

        gitHub.uploadReleaseAsset(WORKSPACE, REPOSITORY, "1", tempAsset.getAbsolutePath(),
                null, null, null, "false");

        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHub).uploadReleaseAssetBinary(org.mockito.ArgumentMatchers.anyString(),
                any(File.class), contentTypeCaptor.capture());
        assertEquals("application/octet-stream", contentTypeCaptor.getValue());
    }

    // ── misc uncovered branches ──────────────────────────────────────────────

    @Test
    public void testListWorkflowRunsWithoutAnyParams() throws IOException {
        doReturn("{\"workflow_runs\":[]}").when(gitHub).execute(any(genericRequestMatcher()));

        gitHub.listWorkflowRuns(WORKSPACE, REPOSITORY, null, null, null, null, null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).execute(captor.capture());
        assertFalse(captor.getValue().url().contains("?"));
        assertTrue(captor.getValue().url().endsWith("/repos/testWorkspace/testRepo/actions/runs"));
    }

    @Test
    public void testGetPullRequestDiffTextExceptionReturnsEmpty() throws IOException {
        Response errorResponse = new Response.Builder()
                .request(new Request.Builder().url("https://api.github.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .build();
        doThrow(new RestClient.RateLimitException("rate limited", "body", errorResponse, 403))
                .when(gitHub).execute(any(genericRequestMatcher()));

        String diffText = gitHub.getPullRequestDiffText(WORKSPACE, REPOSITORY, "74");

        if (new PropertyReader().isReadPullRequestDiff()) {
            assertEquals("", diffText);
        }
    }

    @Test
    public void testSearchFilesEmptyItemsStops() throws IOException, InterruptedException {
        doReturn("{\"items\":[]}").when(gitHub).execute(any(genericRequestMatcher()));

        assertTrue(gitHub.searchFiles(WORKSPACE, REPOSITORY, "query", 10).isEmpty());
    }

    @Test
    public void testSearchFilesUnlimitedStopsAtMaxResults() throws IOException, InterruptedException {
        JSONArray fullPage = new JSONArray();
        for (int i = 0; i < 100; i++) {
            fullPage.put(new JSONObject().put("path", "file" + i + ".java"));
        }
        String page = new JSONObject().put("items", fullPage).toString();
        doReturn(page, page, page, page, page, page, page, page, page, page)
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<IFile> files = gitHub.searchFiles(WORKSPACE, REPOSITORY, "query", -1);

        assertEquals(1000, files.size());
        verify(gitHub, org.mockito.Mockito.times(10)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testSearchFilesRateLimitWaitsUntilReset() throws IOException, InterruptedException {
        long resetSeconds = System.currentTimeMillis() / 1000 + 1;
        Response errorResponse = new Response.Builder()
                .request(new Request.Builder().url("https://api.github.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("rate limited")
                .header("X-RateLimit-Reset", String.valueOf(resetSeconds))
                .build();
        doThrow(new RestClient.RateLimitException("rate limited", "body", errorResponse, 403))
                .doReturn("{\"items\":[{\"path\":\"a.java\"}]}")
                .when(gitHub).execute(any(genericRequestMatcher()));

        List<IFile> files = gitHub.searchFiles(WORKSPACE, REPOSITORY, "query", 10);

        assertEquals(1, files.size());
        verify(gitHub, org.mockito.Mockito.times(2)).execute(any(genericRequestMatcher()));
    }

    @Test
    public void testUriToObjectRethrowsIOException() throws IOException {
        doThrow(new IOException("fetch failed")).when(gitHub).execute(any(genericRequestMatcher()));

        IOException exception = assertThrows(IOException.class, () ->
                gitHub.uriToObject("https://github.com/owner/repo/blob/main/file.md"));
        assertEquals("fetch failed", exception.getMessage());
    }

    @Test
    public void testGetFileContentFromGithubWebUrlNoBlobPath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.getFileContent("https://github.com/blob/x"));
        assertTrue(exception.getMessage().contains("Could not parse GitHub URL"));
    }

    @Test
    public void testGetFileContentFromRawUrlMissingFilePath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                gitHub.getFileContent("https://raw.githubusercontent.com/owner/repo/main"));
        assertTrue(exception.getMessage().contains("Could not extract branch and path"));
    }

    @Test
    public void testDefaultMethodsUnsupportedOnPlainGitHub() throws IOException {
        GitHub plainGitHub = new GitHub(BASE_PATH, "token") {
            @Override
            public SourceCodeConfig getDefaultConfig() {
                return null;
            }

            @Override
            public boolean isConfigured() {
                return true;
            }
        };

        assertThrows(UnsupportedOperationException.class, plainGitHub::getDefaultRepository);
        assertThrows(UnsupportedOperationException.class, plainGitHub::getDefaultBranch);
        assertThrows(UnsupportedOperationException.class, plainGitHub::getDefaultWorkspace);
    }

    @Test
    public void testProcessLargePayloadNonStringEssentialFieldBreaksAtLimit() {
        // huge non-string essential field: kept as-is in the minimal JSON, forcing the
        // encoded-size guard to break the field loop
        JSONArray huge = new JSONArray();
        for (int i = 0; i < 4000; i++) {
            huge.put(UUID.randomUUID().toString());
        }
        String request = new JSONObject()
                .put("knownInfo", huge)
                .put("request", "x")
                .toString();

        String result = gitHub.processLargePayload(request);

        String decoded = com.github.istin.dmtools.job.JobRunner.decodeBase64(result);
        assertTrue(decoded.contains("_truncation_note"));
    }

    private static Class<GenericRequest> genericRequestMatcher() {
        return GenericRequest.class;
    }
}
