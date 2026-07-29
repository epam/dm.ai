// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Coverage-oriented tests for the real HTTP paths of {@link BedrockAIClient}:
 * the overridden {@code post()} method and the AWS4-signed GET used by
 * {@code getAvailableModels()} for IAM-based authentication. All traffic goes
 * to a local in-process HttpServer; no external network is used.
 */
public class BedrockAIClientHttpCoverageTest {

    private static final String REGION = "us-east-1";
    private static final String CLAUDE_MODEL = "anthropic.claude-sonnet-4-20250514-v1:0";
    private static final String BEARER_TOKEN = "test-bearer-token";
    private static final String CLAUDE_RESPONSE =
            "{\"content\":[{\"text\":\"Hello over HTTP\"}],\"stop_reason\":\"end_turn\"}";
    private static final String CLOSED_PORT_URL = "http://127.0.0.1:1/resource";

    private HttpServer server;
    private String baseUrl;
    private String pingToken;

    private final AtomicInteger invokeCount = new AtomicInteger();
    private final AtomicReference<String> seenContentType = new AtomicReference<>();
    private final AtomicReference<String> seenCustomHeader = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();

    @Before
    public void setUp() throws IOException {
        pingToken = UUID.randomUUID().toString();
        // On macOS ServerSocket sets SO_REUSEADDR by default, which permits another
        // process to bind the same ephemeral port. Rebind until the port is provably ours.
        for (int attempt = 0; attempt < 5; attempt++) {
            server = createServer();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            if (verifyServerOwnership()) {
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
                case "/model/" + CLAUDE_MODEL + "/invoke":
                case "/post":
                    invokeCount.incrementAndGet();
                    seenContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                    seenCustomHeader.set(exchange.getRequestHeaders().getFirst("X-Custom-Header"));
                    seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    responseBody = CLAUDE_RESPONSE;
                    break;
                case "/error500":
                    status = 500;
                    responseBody = "Internal Server Error";
                    break;
                case "/foundation-models":
                    responseBody = "{\"modelSummaries\":[]}";
                    break;
                case "/foundation-models-403":
                    status = 403;
                    responseBody = "{\"Message\":\"User is not authorized to perform: bedrock:ListFoundationModels\"}";
                    break;
                case "/foundation-models-500":
                    status = 500;
                    responseBody = "Internal Server Error";
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
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        // Best-effort cleanup of the cache folder created by the testable client
        File cacheDir = new File("cacheTestableBedrockAIClient");
        File[] files = cacheDir.listFiles();
        if (files != null && files.length == 0) {
            cacheDir.delete();
        }
    }

    private BedrockAIClient bearerClient() throws IOException {
        return new BedrockAIClient(baseUrl, REGION, CLAUDE_MODEL, BEARER_TOKEN,
                1000, 0.7, null, null, null);
    }

    // ------------------------------------------------------------------
    // post() over real (local) HTTP
    // ------------------------------------------------------------------

    @Test
    public void testChatEndToEndOverHttp() throws Exception {
        BedrockAIClient client = bearerClient();
        client.setCachePostRequestsEnabled(false);
        String result = client.chat("Hi there");
        assertEquals("Hello over HTTP", result);
        assertEquals(1, invokeCount.get());
        // Bedrock API requires Content-Type without charset
        assertEquals("application/json", seenContentType.get());
        assertNotNull(seenBody.get());
        assertTrue(seenBody.get().contains("\"anthropic_version\""));
    }

    @Test
    public void testPostSuccessWritesAndReadsCache() throws IOException {
        BedrockAIClientCoverageTest.TestableBedrockAIClient client =
                new BedrockAIClientCoverageTest.TestableBedrockAIClient(
                        baseUrl, REGION, CLAUDE_MODEL, BEARER_TOKEN, 1000, 0.7);
        GenericRequest request = new GenericRequest(client, baseUrl + "/post");
        request.setBody("covtest-http-body-" + UUID.randomUUID());

        File cachedFile = new File(client.cacheFolder(), client.cacheFileName(request));
        try {
            String first = client.post(request);
            assertEquals(CLAUDE_RESPONSE, first);
            assertEquals(1, invokeCount.get());
            assertTrue(cachedFile.exists());

            // second call must be served from cache without hitting the server
            String second = client.post(request);
            assertEquals(CLAUDE_RESPONSE, second);
            assertEquals(1, invokeCount.get());
        } finally {
            cachedFile.delete();
        }
    }

    @Test
    public void testPostIgnoreCacheHitsServerEachTime() throws IOException {
        BedrockAIClient client = bearerClient();
        GenericRequest request = new GenericRequest(client, baseUrl + "/post");
        request.setBody("{\"ignore\":true}");
        request.setIgnoreCache(true);

        assertEquals(CLAUDE_RESPONSE, client.post(request));
        assertEquals(CLAUDE_RESPONSE, client.post(request));
        assertEquals(2, invokeCount.get());
    }

    @Test
    public void testPostForwardsCustomRequestHeaders() throws IOException {
        BedrockAIClient client = bearerClient();
        client.setCachePostRequestsEnabled(false);
        GenericRequest request = new GenericRequest(client, baseUrl + "/post");
        request.setBody("{\"h\":1}");
        request.header("X-Custom-Header", "custom-value");

        assertEquals(CLAUDE_RESPONSE, client.post(request));
        assertEquals("custom-value", seenCustomHeader.get());
        assertEquals("application/json", seenContentType.get());
    }

    @Test
    public void testPostServerErrorThrows() throws IOException {
        BedrockAIClient client = bearerClient();
        client.setCachePostRequestsEnabled(false);
        GenericRequest request = new GenericRequest(client, baseUrl + "/error500");
        request.setBody("{}");
        try {
            client.post(request);
            fail("Expected RestClientException");
        } catch (RestClient.RestClientException e) {
            assertEquals(500, e.getCode());
        }
    }

    @Test
    public void testPostConnectionErrorThrows() throws IOException {
        BedrockAIClient client = bearerClient();
        client.setCachePostRequestsEnabled(false);
        GenericRequest request = new GenericRequest(client, CLOSED_PORT_URL);
        request.setBody("{}");
        try {
            client.post(request);
            fail("Expected IOException");
        } catch (IOException e) {
            assertFalse(e instanceof RestClient.RestClientException);
        }
    }

    // ------------------------------------------------------------------
    // executeSignedGetRequest (private) over real (local) HTTP
    // ------------------------------------------------------------------

    private String invokeExecuteSignedGetRequest(BedrockAIClient client, String url) throws IOException {
        try {
            Method method = BedrockAIClient.class.getDeclaredMethod("executeSignedGetRequest", String.class);
            method.setAccessible(true);
            return (String) method.invoke(client, url);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BedrockAIClient iamClient() throws IOException {
        return new BedrockAIClient(baseUrl, REGION, CLAUDE_MODEL,
                "AKIAIOSFODNN7EXAMPLE", "secretAccessKeyValue", "sessionTokenValue",
                1000, 0.7, null, null, null);
    }

    @Test
    public void testExecuteSignedGetRequestSuccess() throws IOException {
        String result = invokeExecuteSignedGetRequest(iamClient(), baseUrl + "/foundation-models");
        assertEquals("{\"modelSummaries\":[]}", result);
    }

    @Test
    public void testExecuteSignedGetRequest403PermissionDenied() throws IOException {
        try {
            invokeExecuteSignedGetRequest(iamClient(), baseUrl + "/foundation-models-403");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("AWS Bedrock permission denied"));
            assertTrue(e.getMessage().contains("User is not authorized"));
            assertTrue(e.getMessage().contains("bedrock:ListFoundationModels"));
        }
    }

    @Test
    public void testExecuteSignedGetRequestServerError() throws IOException {
        try {
            invokeExecuteSignedGetRequest(iamClient(), baseUrl + "/foundation-models-500");
            fail("Expected an exception");
        } catch (RuntimeException e) {
            // For non-403 errors the production code reads the error body at line 1125
            // and then again inside printAndCreateException, so the consumed OkHttp
            // body surfaces as IllegalStateException("closed") instead of RestClientException.
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertEquals("closed", e.getCause().getMessage());
        }
    }

    // ------------------------------------------------------------------
    // signGetRequestWithAWS4 fallback for non-IAM strategies
    // ------------------------------------------------------------------

    @Test
    public void testSignGetRequestWithBearerStrategyFallsBackToDefaultProvider() throws Exception {
        BedrockAIClient client = bearerClient();
        Method method = BedrockAIClient.class.getDeclaredMethod(
                "signGetRequestWithAWS4", String.class, Request.Builder.class);
        method.setAccessible(true);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + "/foundation-models").header("User-Agent", "DMTools").get();
        try {
            Request request = (Request) method.invoke(client, baseUrl + "/foundation-models", builder);
            // environment has AWS credentials configured - signing succeeds locally
            assertNotNull(request.header("Authorization"));
        } catch (InvocationTargetException e) {
            // no credentials in the environment - wrapped in IOException
            assertTrue(e.getCause() instanceof IOException);
            assertTrue(e.getCause().getMessage().contains("Failed to sign GET request"));
        }
    }
}
