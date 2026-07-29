// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.sun.net.httpserver.HttpServer;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage-oriented tests for AbstractRestClient HTTP methods (GET/POST/PUT/PATCH/DELETE),
 * retry loops, caching and redirect resolution. All HTTP traffic goes to a local
 * in-process HttpServer; no external network is used.
 */
public class AbstractRestClientCoverageTest {

    private static final Logger logger = LogManager.getLogger(AbstractRestClientCoverageTest.class);
    private static final String CLOSED_PORT_URL = "http://127.0.0.1:1/resource";

    private HttpServer server;
    private CoverageRestClient restClient;
    private String baseUrl;
    private String pingToken;

    private final AtomicInteger getCount = new AtomicInteger();
    private final AtomicInteger postCount = new AtomicInteger();
    private final AtomicInteger flaky503Count = new AtomicInteger();
    private final AtomicInteger post429Count = new AtomicInteger();

    public static class CoverageRestClient extends AbstractRestClient {
        public CoverageRestClient(String basePath) throws IOException {
            super(basePath, "auth");
        }

        @Override
        public String path(String path) {
            return getBasePath() + path;
        }

        @Override
        public Request.Builder sign(Request.Builder builder) {
            return builder;
        }
    }

    @Before
    public void setUp() throws IOException {
        pingToken = UUID.randomUUID().toString();
        // On macOS ServerSocket sets SO_REUSEADDR by default, which permits another
        // process to bind the same ephemeral port. Rebind until the port is provably ours.
        for (int attempt = 0; attempt < 5; attempt++) {
            server = createServer();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            if (verifyServerOwnership()) {
                restClient = new CoverageRestClient(baseUrl);
                return;
            }
            server.stop(0);
            server = null;
        }
        fail("Could not bind a uniquely owned local HTTP server port");
    }

    private HttpServer createServer() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String responseBody = "ok";
            switch (path) {
                case "/__ping":
                    responseBody = pingToken;
                    break;
                case "/get":
                    getCount.incrementAndGet();
                    responseBody = "get-ok";
                    break;
                case "/flaky503":
                    if (flaky503Count.getAndIncrement() == 0) {
                        status = 503;
                        responseBody = "Service Unavailable";
                    } else {
                        responseBody = "recovered";
                    }
                    break;
                case "/always503":
                    status = 503;
                    responseBody = "Service Unavailable";
                    break;
                case "/notfound":
                    status = 404;
                    responseBody = "Not Found";
                    break;
                case "/post":
                    postCount.incrementAndGet();
                    responseBody = "post-ok";
                    break;
                case "/post429":
                    if (post429Count.getAndIncrement() == 0) {
                        status = 429;
                        responseBody = "rate limit exceeded";
                    } else {
                        responseBody = "post-recovered";
                    }
                    break;
                case "/error500":
                    status = 500;
                    responseBody = "Internal Server Error";
                    break;
                case "/put":
                    responseBody = "put-ok";
                    break;
                case "/patch":
                    responseBody = "patch-ok";
                    break;
                case "/delete":
                    responseBody = "delete-ok";
                    break;
                case "/redir-absolute":
                    exchange.getResponseHeaders().add("Location",
                            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/final");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                case "/redir-relative":
                    exchange.getResponseHeaders().add("Location", "/final");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                case "/redir-missing-location":
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                case "/redir-loop":
                    exchange.getResponseHeaders().add("Location", "/redir-loop");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                case "/final":
                    responseBody = "final-ok";
                    break;
                default:
                    status = 404;
                    responseBody = "unknown";
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    private boolean verifyServerOwnership() {
        // Several sequential pings: with a duplicate-bound port each connection may land
        // on a foreign listener, so all pings must echo our unique token.
        for (int i = 0; i < 5; i++) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/__ping").openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                try (java.io.InputStream in = connection.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    if (!pingToken.equals(new String(bytes, StandardCharsets.UTF_8))) {
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

    @After
    public void tearDown() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        // Clean up cache folder created by the client under test
        if (restClient != null) {
            File cacheFolder = new File(restClient.getCacheFolderName());
            if (cacheFolder.exists()) {
                FileUtils.deleteDirectory(cacheFolder);
            }
        }
    }

    private RetryPolicy fastRetryPolicy() {
        return new RetryPolicy(2, 1, 1, 1.0, 0.0, logger);
    }

    // GET (execute) tests

    @Test
    public void testExecuteGetSuccess() throws IOException {
        String result = restClient.execute(baseUrl + "/get");
        assertEquals("get-ok", result);
        assertEquals(1, getCount.get());
    }

    @Test
    public void testExecuteGenericRequestWithHeaders() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        request.header("X-Custom-Header", "value");
        String result = restClient.execute(request);
        assertEquals("get-ok", result);
    }

    @Test
    public void testExecuteGetCacheWriteAndRead() throws IOException {
        restClient.setCacheGetRequestsEnabled(true);
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");

        String first = restClient.execute(request);
        assertEquals("get-ok", first);
        assertEquals(1, getCount.get());
        File cachedFile = restClient.getCachedFile(request);
        assertTrue(cachedFile.exists());

        // Second call must be served from the cache without hitting the server
        String second = restClient.execute(request);
        assertEquals("get-ok", second);
        assertEquals(1, getCount.get());
    }

    @Test
    public void testExecuteGetIgnoreCacheHitsServerAgain() throws IOException {
        restClient.setCacheGetRequestsEnabled(true);
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        request.setIgnoreCache(true);

        restClient.execute(request);
        restClient.execute(request);

        assertEquals(2, getCount.get());
    }

    @Test
    public void testExecuteGetNotFoundThrowsRestClientException() throws IOException {
        try {
            restClient.execute(baseUrl + "/notfound");
            fail("Expected RestClientException");
        } catch (RestClient.RestClientException e) {
            assertEquals(404, e.getCode());
        }
    }

    @Test
    public void testExecuteGet503RetriesThenSucceeds() throws IOException {
        restClient.setRetryPolicy(fastRetryPolicy());
        String result = restClient.execute(baseUrl + "/flaky503");
        assertEquals("recovered", result);
        assertEquals(2, flaky503Count.get());
    }

    @Test
    public void testExecuteGet503ExhaustsRetriesAndThrows() throws IOException {
        restClient.setRetryPolicy(fastRetryPolicy());
        try {
            restClient.execute(baseUrl + "/always503");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains(RestClient.RestClientException.BACKUP_503));
        }
    }

    @Test
    public void testExecuteGetConnectionRefusedRetriesAndThrows() throws IOException {
        restClient.setRetryPolicy(fastRetryPolicy());
        try {
            restClient.execute(CLOSED_PORT_URL);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(restClient.isRecoverableConnectionError(e));
        }
    }

    // POST tests

    @Test
    public void testPostSuccess() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/post");
        request.setBody("{\"a\":1}");
        String result = restClient.post(request);
        assertEquals("post-ok", result);
        assertEquals(1, postCount.get());
    }

    @Test
    public void testPostNullRequestReturnsEmptyString() throws IOException {
        assertEquals("", restClient.post(null));
    }

    @Test
    public void testPostCacheWriteAndRead() throws IOException {
        restClient.setCachePostRequestsEnabled(true);
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/post");
        request.setBody("{\"cached\":true}");

        String first = restClient.post(request);
        assertEquals("post-ok", first);
        assertEquals(1, postCount.get());

        String second = restClient.post(request);
        assertEquals("post-ok", second);
        assertEquals(1, postCount.get());
    }

    @Test
    public void testPost429RetriesThenSucceeds() throws IOException {
        restClient.setRetryPolicy(fastRetryPolicy());
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/post429");
        request.setBody("{}");
        String result = restClient.post(request);
        assertEquals("post-recovered", result);
        assertEquals(2, post429Count.get());
    }

    @Test
    public void testPostServerErrorThrows() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/error500");
        request.setBody("{}");
        try {
            restClient.post(request);
            fail("Expected RestClientException");
        } catch (RestClient.RestClientException e) {
            assertEquals(500, e.getCode());
        }
    }

    @Test
    public void testPostConnectionRefusedRetriesAndThrows() throws IOException {
        restClient.setRetryPolicy(fastRetryPolicy());
        GenericRequest request = new GenericRequest(restClient, CLOSED_PORT_URL);
        request.setBody("{}");
        try {
            restClient.post(request);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(restClient.isRecoverableConnectionError(e));
        }
    }

    // PUT tests

    @Test
    public void testPutSuccess() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/put");
        request.setBody("{\"b\":2}");
        assertEquals("put-ok", restClient.put(request));
    }

    @Test
    public void testPutServerErrorThrows() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/error500");
        request.setBody("{}");
        try {
            restClient.put(request);
            fail("Expected RestClientException");
        } catch (RestClient.RestClientException e) {
            assertEquals(500, e.getCode());
        }
    }

    @Test
    public void testPutConnectionRefusedRetriesAndThrows() throws IOException {
        GenericRequest request = new GenericRequest(restClient, CLOSED_PORT_URL);
        request.setBody("{}");
        try {
            restClient.put(request);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(restClient.isRecoverableConnectionError(e));
        }
    }

    // PATCH tests

    @Test
    public void testPatchSuccess() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/patch");
        request.setBody("{\"c\":3}");
        assertEquals("patch-ok", restClient.patch(request));
    }

    @Test
    public void testPatchServerErrorThrows() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/error500");
        request.setBody("{}");
        try {
            restClient.patch(request);
            fail("Expected RestClientException");
        } catch (RestClient.RestClientException e) {
            assertEquals(500, e.getCode());
        }
    }

    @Test
    public void testPatchConnectionRefusedRetriesAndThrows() throws IOException {
        GenericRequest request = new GenericRequest(restClient, CLOSED_PORT_URL);
        request.setBody("{}");
        try {
            restClient.patch(request);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(restClient.isRecoverableConnectionError(e));
        }
    }

    // DELETE tests

    @Test
    public void testDeleteSuccessWithBody() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/delete");
        request.setBody("{\"d\":4}");
        assertEquals("delete-ok", restClient.delete(request));
    }

    @Test
    public void testDeleteSuccessWithoutBody() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/delete");
        assertEquals("delete-ok", restClient.delete(request));
    }

    @Test
    public void testDeleteServerErrorThrows() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/error500");
        try {
            restClient.delete(request);
            fail("Expected RestClientException");
        } catch (RestClient.RestClientException e) {
            assertEquals(500, e.getCode());
        }
    }

    // waitBeforePerform branches

    @Test
    public void testWaitBeforePerformForAllMethods() throws IOException {
        restClient.setWaitBeforePerform(true);

        assertEquals("get-ok", restClient.execute(baseUrl + "/get"));

        GenericRequest postRequest = new GenericRequest(restClient, baseUrl + "/post");
        postRequest.setBody("{}");
        assertEquals("post-ok", restClient.post(postRequest));

        GenericRequest putRequest = new GenericRequest(restClient, baseUrl + "/put");
        putRequest.setBody("{}");
        assertEquals("put-ok", restClient.put(putRequest));

        GenericRequest patchRequest = new GenericRequest(restClient, baseUrl + "/patch");
        patchRequest.setBody("{}");
        assertEquals("patch-ok", restClient.patch(patchRequest));

        GenericRequest deleteRequest = new GenericRequest(restClient, baseUrl + "/delete");
        assertEquals("delete-ok", restClient.delete(deleteRequest));
    }

    // resolveRedirect tests

    @Test
    public void testResolveRedirectAbsoluteLocation() throws IOException {
        String resolved = AbstractRestClient.resolveRedirect(restClient, baseUrl + "/redir-absolute");
        assertEquals(baseUrl + "/final", resolved);
    }

    @Test
    public void testResolveRedirectRelativeLocation() throws IOException {
        String resolved = AbstractRestClient.resolveRedirect(restClient, baseUrl + "/redir-relative");
        assertEquals(baseUrl + "/final", resolved);
    }

    @Test
    public void testResolveRedirectNoRedirect() throws IOException {
        String resolved = AbstractRestClient.resolveRedirect(restClient, baseUrl + "/final");
        assertEquals(baseUrl + "/final", resolved);
    }

    @Test
    public void testResolveRedirectMissingLocationThrows() throws IOException {
        try {
            AbstractRestClient.resolveRedirect(restClient, baseUrl + "/redir-missing-location");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Redirect location is missing"));
        }
    }

    @Test
    public void testResolveRedirectTooManyRedirectsThrows() throws IOException {
        try {
            AbstractRestClient.resolveRedirect(restClient, baseUrl + "/redir-loop");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Too many redirects"));
        }
    }

    // cache file name / hash helpers

    @Test
    public void testGetCacheFileNameWithBodyAndHeaders() {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        request.setBody("body-content");
        request.header("X-Header", "header-value");
        String hash = restClient.getCacheFileName(request);
        assertNotNull(hash);
        assertEquals(32, hash.length());
    }

    @Test
    public void testGetCacheFileNameUrlOnly() {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        String hash = restClient.getCacheFileName(request);
        assertNotNull(hash);
        assertEquals(32, hash.length());
    }

    @Test
    public void testBuildHashForPostRequest() {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/post");
        request.setBody("payload");
        assertEquals(baseUrl + "/postpayload", restClient.buildHashForPostRequest(request, baseUrl + "/post"));
    }

    // retry policy accessors

    @Test
    public void testSetAndGetRetryPolicy() {
        RetryPolicy custom = fastRetryPolicy();
        restClient.setRetryPolicy(custom);
        assertSame(custom, restClient.getRetryPolicy());

        restClient.setRetryPolicy(null);
        assertNotNull(restClient.getRetryPolicy());
    }

    // clearRequestIfExpired (3-arg overload)

    @Test
    public void testClearRequestIfExpiredThreeArgExpired() throws IOException {
        restClient.setCacheGetRequestsEnabled(true);
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        File cachedFile = restClient.getCachedFile(request);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached", StandardCharsets.UTF_8);

        restClient.clearRequestIfExpired(request, System.currentTimeMillis() + 100000, cachedFile);

        assertFalse(cachedFile.exists());
    }

    @Test
    public void testClearRequestIfExpiredThreeArgNotExpired() throws IOException {
        restClient.setCacheGetRequestsEnabled(true);
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        File cachedFile = restClient.getCachedFile(request);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached", StandardCharsets.UTF_8);

        restClient.clearRequestIfExpired(request, System.currentTimeMillis() - 100000, cachedFile);

        assertTrue(cachedFile.exists());
    }

    // setClearCache / reinitCache

    @Test
    public void testSetClearCacheDeletesCacheFolder() throws IOException {
        GenericRequest request = new GenericRequest(restClient, baseUrl + "/get");
        File cachedFile = restClient.getCachedFile(request);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached", StandardCharsets.UTF_8);
        assertTrue(cachedFile.exists());

        restClient.setClearCache(true);
        assertFalse(cachedFile.exists());

        restClient.setClearCache(false);
        assertFalse(restClient.isClearCache);
    }

    // misc accessors and helpers

    @Test
    public void testCachePostRequestsEnabledAccessor() {
        assertFalse(restClient.isCachePostRequestsEnabled());
        restClient.setCachePostRequestsEnabled(true);
        assertTrue(restClient.isCachePostRequestsEnabled());
    }

    @Test
    public void testGetClientNotNull() {
        assertNotNull(restClient.getClient());
    }

    @Test
    public void testCleanupConnectionPool() {
        restClient.cleanupConnectionPool();
    }

    @Test
    public void testLogRetryResumption() {
        // Should execute without throwing
        restClient.logRetryResumption("GET", baseUrl + "/get?token=secret", 2, 3);
    }

    // printAndCreateException branches

    @Test
    public void testPrintAndCreateException503Backup() throws IOException {
        Request mockRequest = mock(Request.class);
        when(mockRequest.url()).thenReturn(HttpUrl.parse("https://example.com/api"));

        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(503);
        when(mockResponse.body()).thenReturn(ResponseBody.create("backup", okhttp3.MediaType.parse("text/plain")));
        when(mockResponse.message()).thenReturn("Service Unavailable");

        IOException ex = AbstractRestClient.printAndCreateException(mockRequest, mockResponse);
        assertTrue(ex.getMessage().contains(RestClient.RestClientException.BACKUP_503));
    }

    @Test
    public void testPrintAndCreateException400NoSuchParentEpics() throws IOException {
        Request mockRequest = mock(Request.class);
        when(mockRequest.url()).thenReturn(HttpUrl.parse("https://example.com/api"));

        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(400);
        when(mockResponse.body()).thenReturn(ResponseBody.create(
                "No issues have a parent epic with key or name: TEST-1", okhttp3.MediaType.parse("text/plain")));
        when(mockResponse.message()).thenReturn("Bad Request");

        IOException ex = AbstractRestClient.printAndCreateException(mockRequest, mockResponse);
        assertTrue(ex.getMessage().contains(RestClient.RestClientException.NO_SUCH_PARENT_EPICS));
    }

    @Test
    public void testPrintAndCreateException404IssueUrl() throws IOException {
        Request mockRequest = mock(Request.class);
        when(mockRequest.url()).thenReturn(HttpUrl.parse("https://example.com/rest/api/2/issue/TEST-1"));

        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(404);
        when(mockResponse.body()).thenReturn(ResponseBody.create("Issue does not exist", okhttp3.MediaType.parse("text/plain")));
        when(mockResponse.message()).thenReturn("Not Found");

        IOException ex = AbstractRestClient.printAndCreateException(mockRequest, mockResponse);
        assertTrue(ex instanceof RestClient.RestClientException);
        assertEquals(404, ((RestClient.RestClientException) ex).getCode());
    }

    @Test
    public void testPrintAndCreateExceptionNullBody() throws IOException {
        Request mockRequest = mock(Request.class);
        when(mockRequest.url()).thenReturn(HttpUrl.parse("https://example.com/api"));

        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(500);
        when(mockResponse.body()).thenReturn(null);
        when(mockResponse.message()).thenReturn("Internal Server Error");

        IOException ex = AbstractRestClient.printAndCreateException(mockRequest, mockResponse);
        assertTrue(ex instanceof RestClient.RestClientException);
        assertEquals(500, ((RestClient.RestClientException) ex).getCode());
    }
}
