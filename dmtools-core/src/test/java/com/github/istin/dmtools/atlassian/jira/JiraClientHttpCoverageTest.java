// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.networking.RetryPolicy;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Coverage tests for the raw HTTP internals of {@link JiraClient}
 * ({@code execute(String,...)}, {@code post}, {@code put}, {@code patch},
 * {@code delete}, {@code isImageAttachment}, multipart attachment upload and
 * file download). All traffic goes to a local in-process
 * {@link HttpServer} bound to 127.0.0.1 — no external network is used.
 * Port-ownership is verified with a unique ping token (same approach as
 * AbstractRestClientCoverageTest).
 */
public class JiraClientHttpCoverageTest {

    private static final Logger logger = LogManager.getLogger(JiraClientHttpCoverageTest.class);
    private static final String CLOSED_PORT_URL = "http://127.0.0.1:1/resource";

    private HttpServer server;
    private String baseUrl;
    private String pingToken;
    private HttpJiraClient client;

    private final AtomicInteger getCount = new AtomicInteger();
    private final AtomicInteger flaky503Count = new AtomicInteger();
    private final AtomicInteger flaky429Count = new AtomicInteger();
    private final AtomicInteger post429Count = new AtomicInteger();
    private final AtomicInteger keyErrorCount = new AtomicInteger();

    static class HttpJiraClient extends JiraClient<Ticket> {
        HttpJiraClient(String basePath) throws IOException {
            super(basePath, "auth");
        }

        @Override
        public String getTextFieldsOnly(ITicket ticket) {
            return "";
        }

        @Override
        public String[] getDefaultQueryFields() {
            return new String[0];
        }

        @Override
        public String[] getExtendedQueryFields() {
            return new String[0];
        }

        @Override
        public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) {
            return List.of();
        }

        @Override
        public TextType getTextType() {
            return null;
        }

        @Override
        public Ticket createTicket(String body) {
            return new Ticket(body);
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
                client = new HttpJiraClient(baseUrl);
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
            String query = exchange.getRequestURI().getRawQuery();
            int status = 200;
            String responseBody = "ok";
            String contentType = "application/json";
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
                        responseBody = "recovered-from-503";
                    }
                    break;
                case "/always503":
                    status = 503;
                    responseBody = "Service Unavailable";
                    break;
                case "/flaky429":
                    if (flaky429Count.getAndIncrement() == 0) {
                        status = 429;
                        responseBody = "rate limit exceeded";
                    } else {
                        responseBody = "recovered-from-429";
                    }
                    break;
                case "/keyerror":
                    if (query != null && query.contains("TP-1")) {
                        keyErrorCount.incrementAndGet();
                        status = 400;
                        responseBody = "The value 'TP-1' does not exist for field 'key'.";
                    } else {
                        responseBody = "stripped-ok";
                    }
                    break;
                case "/post":
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
                case "/postAlways429":
                    status = 429;
                    responseBody = "rate limit exceeded";
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
                case "/img":
                    contentType = "image/png";
                    responseBody = "png-bytes";
                    break;
                case "/text":
                    contentType = "text/plain";
                    responseBody = "plain";
                    break;
                case "/file.png":
                    contentType = "image/png";
                    responseBody = "downloaded-image-bytes";
                    break;
                case "/rest/api/latest/issue/TP-1":
                    responseBody = "{\"key\":\"TP-1\",\"fields\":{\"summary\":\"s\",\"attachment\":[]}}";
                    break;
                case "/rest/api/latest/issue/TP-1/attachments":
                    responseBody = "[{\"id\":\"1\",\"filename\":\"new.png\"}]";
                    break;
                case "/rest/api/latest/serverInfo":
                    responseBody = "{\"version\":\"9.4.0\"}";
                    break;
                case "/rest/api/latest/issue/createmeta":
                    responseBody = "[{\"name\":\"Bug\"}]";
                    break;
                default:
                    status = 404;
                    responseBody = "unknown";
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
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
        // Clean up the cache folder created by the client under test
        if (client != null) {
            File cacheFolder = new File(client.getCacheFolderName());
            if (cacheFolder.exists()) {
                FileUtils.deleteDirectory(cacheFolder);
            }
        }
    }

    private RetryPolicy fastRetryPolicy() {
        return new RetryPolicy(3, 1, 10, 1.0, 0.0, logger);
    }

    // ─── GET (execute) ───────────────────────────────────────────────────────

    @Test
    public void testExecuteGetSuccessWritesAndReadsCache() throws IOException {
        GenericRequest request = new GenericRequest(client, baseUrl + "/get");
        assertEquals("get-ok", client.execute(request));
        assertEquals(1, getCount.get());

        // second call must be served from the cache file without another HTTP request
        assertEquals("get-ok", client.execute(new GenericRequest(client, baseUrl + "/get")));
        assertEquals(1, getCount.get());
    }

    @Test
    public void testExecuteGetWithWaitBeforePerform() throws IOException {
        client.setWaitBeforePerform(true);
        client.setSleepTimeRequest(1);
        try {
            assertEquals("get-ok", client.execute(new GenericRequest(client, baseUrl + "/get")));
        } finally {
            client.setWaitBeforePerform(false);
        }
    }

    @Test
    public void testExecuteGet503ThenSuccess() throws IOException {
        client.setRetryPolicy(fastRetryPolicy());
        client.setCacheGetRequestsEnabled(false);
        assertEquals("recovered-from-503", client.execute(new GenericRequest(client, baseUrl + "/flaky503")));
        assertEquals(2, flaky503Count.get());
    }

    @Test
    public void testExecuteGet503Exhausted() throws IOException {
        client.setRetryPolicy(new RetryPolicy(2, 1, 10, 1.0, 0.0, logger));
        client.setCacheGetRequestsEnabled(false);
        IOException exception = assertThrows(IOException.class,
                () -> client.execute(new GenericRequest(client, baseUrl + "/always503")));
        assertTrue(exception.getMessage().contains("503"));
    }

    @Test
    public void testExecuteGet429ThenSuccess() throws IOException {
        client.setRetryPolicy(fastRetryPolicy());
        client.setCacheGetRequestsEnabled(false);
        assertEquals("recovered-from-429", client.execute(new GenericRequest(client, baseUrl + "/flaky429")));
        assertEquals(2, flaky429Count.get());
    }

    @Test
    public void testExecuteGetConnectionErrorRetriesAndThrows() throws IOException {
        client.setRetryPolicy(new RetryPolicy(2, 1, 10, 1.0, 0.0, logger));
        client.setCacheGetRequestsEnabled(false);
        assertThrows(IOException.class,
                () -> client.execute(new GenericRequest(client, CLOSED_PORT_URL)));
    }

    @Test
    public void testExecuteGenericRequestStripsMissingKeyAndRetries() throws IOException {
        client.setCacheGetRequestsEnabled(false);
        String url = baseUrl + "/keyerror?jql=key%20in%20(TP-1%2CTP-2)";
        assertEquals("stripped-ok", client.execute(new GenericRequest(client, url)));
        assertEquals(1, keyErrorCount.get());
    }

    @Test
    public void testExecuteGetNotFoundThrows() throws IOException {
        client.setCacheGetRequestsEnabled(false);
        RestClient.RestClientException exception = assertThrows(RestClient.RestClientException.class,
                () -> client.execute(new GenericRequest(client, baseUrl + "/missing-endpoint")));
        assertEquals(404, exception.getCode());
    }

    @Test
    public void testExecuteUrlMcpMethod() throws IOException {
        assertEquals("get-ok", client.execute(baseUrl + "/get"));
    }

    // ─── POST ────────────────────────────────────────────────────────────────

    @Test
    public void testPostSuccess() throws IOException {
        GenericRequest request = new GenericRequest(client, baseUrl + "/post").setBody("{\"a\":1}");
        assertEquals("post-ok", client.post(request));
    }

    @Test
    public void testPostWithWaitBeforePerform() throws IOException {
        client.setWaitBeforePerform(true);
        client.setSleepTimeRequest(1);
        try {
            assertEquals("post-ok", client.post(new GenericRequest(client, baseUrl + "/post").setBody("{}")));
        } finally {
            client.setWaitBeforePerform(false);
        }
    }

    @Test
    public void testPost429ThenSuccess() throws IOException {
        client.setRetryPolicy(fastRetryPolicy());
        GenericRequest request = new GenericRequest(client, baseUrl + "/post429").setBody("{}");
        assertEquals("post-recovered", client.post(request));
        assertEquals(2, post429Count.get());
    }

    @Test
    public void testPost429Exhausted() throws IOException {
        client.setRetryPolicy(new RetryPolicy(2, 1, 10, 1.0, 0.0, logger));
        GenericRequest request = new GenericRequest(client, baseUrl + "/postAlways429").setBody("{}");
        assertThrows(RestClient.RateLimitException.class, () -> client.post(request));
    }

    @Test
    public void testPost500ReturnsBody() throws IOException {
        GenericRequest request = new GenericRequest(client, baseUrl + "/error500").setBody("{}");
        assertEquals("Internal Server Error", client.post(request));
    }

    @Test
    public void testPostConnectionErrorThrows() throws IOException {
        client.setRetryPolicy(new RetryPolicy(2, 1, 10, 1.0, 0.0, logger));
        GenericRequest request = new GenericRequest(client, CLOSED_PORT_URL).setBody("{}");
        assertThrows(IOException.class, () -> client.post(request));
    }

    // ─── PUT / PATCH / DELETE ────────────────────────────────────────────────

    @Test
    public void testPutSuccess() throws IOException {
        assertEquals("put-ok", client.put(new GenericRequest(client, baseUrl + "/put").setBody("{}")));
    }

    @Test
    public void testPutWithWaitBeforePerform() throws IOException {
        client.setWaitBeforePerform(true);
        client.setSleepTimeRequest(1);
        try {
            assertEquals("put-ok", client.put(new GenericRequest(client, baseUrl + "/put").setBody("{}")));
        } finally {
            client.setWaitBeforePerform(false);
        }
    }

    @Test
    public void testPutErrorWithBase64BodySanitization() throws IOException {
        String base64 = "A".repeat(150);
        String body = "{\"data\":\"" + base64 + "\"," +
                "\"url\":\"data:image/png;base64," + base64 + "\"," +
                "\"image_url\":{\"url\":\"data:image/png;base64," + base64 + "\"}}";
        GenericRequest request = new GenericRequest(client, baseUrl + "/error500").setBody(body);
        assertEquals("Internal Server Error", client.put(request));
    }

    @Test
    public void testPatchSuccess() throws IOException {
        assertEquals("patch-ok", client.patch(new GenericRequest(client, baseUrl + "/patch").setBody("{}")));
    }

    @Test
    public void testPatchError() throws IOException {
        assertEquals("Internal Server Error",
                client.patch(new GenericRequest(client, baseUrl + "/error500").setBody("{}")));
    }

    @Test
    public void testDeleteSuccess() throws IOException {
        assertEquals("delete-ok", client.delete(new GenericRequest(client, baseUrl + "/delete")));
    }

    @Test
    public void testDeleteWithBody() throws IOException {
        assertEquals("delete-ok",
                client.delete(new GenericRequest(client, baseUrl + "/delete").setBody("{\"id\":1}")));
    }

    @Test
    public void testDeleteWithWaitBeforePerform() throws IOException {
        client.setWaitBeforePerform(true);
        client.setSleepTimeRequest(1);
        try {
            assertEquals("delete-ok", client.delete(new GenericRequest(client, baseUrl + "/delete")));
        } finally {
            client.setWaitBeforePerform(false);
        }
    }

    // ─── attachments & images ────────────────────────────────────────────────

    @Test
    public void testIsImageAttachment() throws IOException {
        assertTrue(client.isImageAttachment(baseUrl + "/img"));
        assertFalse(client.isImageAttachment(baseUrl + "/text"));
        assertThrows(IOException.class, () -> client.isImageAttachment(baseUrl + "/error500"));
    }

    @Test
    public void testIsValidImageUrlFallsBackToAttachmentCheck() throws IOException {
        // no image extension -> HEAD-like attachment check against the local server
        assertTrue(client.isValidImageUrl(baseUrl + "/img"));
    }

    @Test
    public void testAttachFileToTicketFullUpload() throws IOException {
        File tempFile = File.createTempFile("upload", ".png");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "fake-image-content");

        // ticket has no attachments -> upload proceeds; local server accepts the multipart POST
        client.attachFileToTicket("TP-1", "new.png", "image/png", tempFile);
    }

    @Test
    public void testAttachFileToTicketDefaultContentType() throws IOException {
        File tempFile = File.createTempFile("upload", ".png");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "fake-image-content");

        client.attachFileToTicket("TP-1", "new.png", null, tempFile);
    }

    @Test
    public void testConvertUrlToFileDownloads() throws IOException {
        String href = baseUrl + "/file.png";
        File target = client.getCachedFile(href);
        try {
            File result = client.convertUrlToFile(href);
            assertTrue(result.exists());
            assertEquals("downloaded-image-bytes", Files.readString(result.toPath()));
        } finally {
            if (target.exists()) {
                target.delete();
            }
        }
    }

    // ─── cloud detection fallback ────────────────────────────────────────────

    @Test
    public void testCloudDetectionWithoutDeploymentTypeOverHttp() throws IOException {
        // serverInfo has no deploymentType -> URL-pattern fallback (127.0.0.1 is not Cloud)
        assertEquals(1, client.getIssueTypes("TP").size());
    }
}
