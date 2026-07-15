// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.common.auth;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OAuth2AuthenticationFlow.
 *
 * HTTP calls are mocked by injecting a mocked OkHttpClient into the private
 * httpClient field (same technique as GitLabCoverageTest). The browser-based
 * flow is exercised against the real localhost callback server with canned
 * HTTP requests; tests that would reach Desktop.browse are skipped unless
 * the JVM is headless so no browser window can be opened.
 */
public class OAuth2AuthenticationFlowTest {

    static {
        // Prevent the browser flow from ever opening a real browser window.
        System.setProperty("java.awt.headless", "true");
    }

    private OAuth2AuthenticationFlow flow;

    @Before
    public void setUp() {
        flow = new OAuth2AuthenticationFlow();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static void injectHttpClient(OAuth2AuthenticationFlow flow, OkHttpClient client) throws Exception {
        Field field = OAuth2AuthenticationFlow.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(flow, client);
    }

    private static Response buildResponse(int code, String body) {
        Request request = new Request.Builder()
                .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Error")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }

    private static String tokenJson(String accessToken, String refreshToken, int expiresIn) {
        StringBuilder sb = new StringBuilder("{\"access_token\":\"").append(accessToken).append("\"");
        if (refreshToken != null) {
            sb.append(",\"refresh_token\":\"").append(refreshToken).append("\"");
        }
        sb.append(",\"expires_in\":").append(expiresIn).append("}");
        return sb.toString();
    }

    private static String deviceCodeJson(int expiresIn, int interval) {
        return "{\"device_code\":\"device-code-123\","
                + "\"user_code\":\"USER-CODE\","
                + "\"verification_uri\":\"https://microsoft.com/devicelogin\","
                + "\"expires_in\":" + expiresIn + ","
                + "\"interval\":" + interval + "}";
    }

    /** Mocks the client so that consecutive execute() calls return the given responses. */
    private void mockHttp(Response... responses) throws Exception {
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(client.newCall(any())).thenReturn(call);
        if (responses.length == 1) {
            when(call.execute()).thenReturn(responses[0]);
        } else {
            Response first = responses[0];
            Response[] rest = java.util.Arrays.copyOfRange(responses, 1, responses.length);
            when(call.execute()).thenReturn(first, rest);
        }
        injectHttpClient(flow, client);
    }

    /** Waits (up to 5s) until the background browser flow has terminated. */
    private static Throwable awaitError(AtomicReference<Throwable> error) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (error.get() != null) {
                return error.get();
            }
            Thread.sleep(20);
        }
        return error.get();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForPort(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket socket = new Socket("localhost", port);
                socket.close();
                return;
            } catch (IOException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("Local callback server did not start on port " + port);
    }

    /**
     * Sends a raw HTTP GET to the local callback server. Reading the response
     * is best-effort: the flow stops the server with stop(0) as soon as the
     * callback completes the future, which can reset the connection before the
     * response is fully read. A raw socket is used instead of HttpURLConnection
     * because the latter transparently retries reset GET requests, which can
     * hit an unrelated server on a recycled port.
     */
    private static void httpGet(int port, String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(2000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
            try {
                InputStream in = socket.getInputStream();
                while (in.read() != -1) {
                    // drain response
                }
            } catch (IOException ignored) {
                // server.stop(0) may reset the connection; the callback was still delivered
            }
        }
    }

    /** Runs the browser flow in a background thread and captures the outcome. */
    private static AtomicReference<Throwable> startBrowserFlow(OAuth2AuthenticationFlow flow, int port) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                flow.authenticateViaBrowser("client id&", "common", "User.Read offline_access", port);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return error;
    }

    // ── refreshAccessToken ──────────────────────────────────────────────────

    @Test
    public void testRefreshAccessTokenSuccess() throws Exception {
        mockHttp(buildResponse(200, tokenJson("new-access", "new-refresh", 3600)));

        OAuth2AuthenticationFlow.TokenResponse tokens =
                flow.refreshAccessToken("old-refresh", "client-id", "common");

        assertEquals("new-access", tokens.getAccessToken());
        assertEquals("new-refresh", tokens.getRefreshToken());
        assertEquals(3600, tokens.getExpiresIn());
    }

    @Test
    public void testRefreshAccessTokenWithoutRefreshToken() throws Exception {
        mockHttp(buildResponse(200, tokenJson("new-access", null, 1800)));

        OAuth2AuthenticationFlow.TokenResponse tokens =
                flow.refreshAccessToken("old-refresh", "client-id", "tenant-id");

        assertEquals("new-access", tokens.getAccessToken());
        assertNull(tokens.getRefreshToken());
        assertEquals(1800, tokens.getExpiresIn());
    }

    @Test
    public void testRefreshAccessTokenFailure() throws Exception {
        mockHttp(buildResponse(401, "{\"error\":\"invalid_grant\"}"));

        try {
            flow.refreshAccessToken("bad-refresh", "client-id", "common");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Token refresh failed"));
            assertTrue(e.getMessage().contains("401"));
        }
    }

    // ── authenticateViaDeviceCode ───────────────────────────────────────────

    @Test
    public void testDeviceCodeFlowSuccessAfterAuthorizationPending() throws Exception {
        mockHttp(
                buildResponse(200, deviceCodeJson(60, 0)),
                buildResponse(400, "{\"error\":\"authorization_pending\"}"),
                buildResponse(200, tokenJson("device-access", "device-refresh", 7200))
        );

        OAuth2AuthenticationFlow.TokenResponse tokens =
                flow.authenticateViaDeviceCode("client-id", "common", "User.Read");

        assertEquals("device-access", tokens.getAccessToken());
        assertEquals("device-refresh", tokens.getRefreshToken());
        assertEquals(7200, tokens.getExpiresIn());
    }

    @Test
    public void testDeviceCodeRequestFailure() throws Exception {
        mockHttp(buildResponse(400, "{\"error\":\"invalid_client\"}"));

        try {
            flow.authenticateViaDeviceCode("client-id", "common", "User.Read");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Device code request failed"));
            assertTrue(e.getMessage().contains("400"));
        }
    }

    @Test
    public void testDeviceCodePollingError() throws Exception {
        mockHttp(
                buildResponse(200, deviceCodeJson(60, 0)),
                buildResponse(400, "{\"error\":\"access_denied\"}")
        );

        try {
            flow.authenticateViaDeviceCode("client-id", "common", "User.Read");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Device code authentication failed"));
            assertTrue(e.getMessage().contains("access_denied"));
        }
    }

    @Test
    public void testDeviceCodeFlowTimeout() throws Exception {
        // expires_in = 0 means the polling loop is never entered
        mockHttp(buildResponse(200, deviceCodeJson(0, 0)));

        try {
            flow.authenticateViaDeviceCode("client-id", "common", "User.Read");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("timeout"));
        }
    }

    @Test
    public void testDeviceCodeSlowDownThenInterrupted() throws Exception {
        CountDownLatch pollingStarted = new CountDownLatch(1);
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(client.newCall(any())).thenReturn(call);
        // First call: device code request. Second call: poll -> slow_down (interval becomes 5s).
        when(call.execute())
                .thenReturn(buildResponse(200, deviceCodeJson(300, 0)))
                .thenAnswer(invocation -> {
                    pollingStarted.countDown();
                    return buildResponse(400, "{\"error\":\"slow_down\"}");
                });
        injectHttpClient(flow, client);

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                flow.authenticateViaDeviceCode("client-id", "common", "User.Read");
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.setDaemon(true);
        thread.start();

        assertTrue("Polling did not start", pollingStarted.await(5, TimeUnit.SECONDS));
        // Give the flow a moment to enter the 5s slow_down sleep, then interrupt it.
        Thread.sleep(200);
        thread.interrupt();
        thread.join(5000);

        assertNotNull("Flow should have failed", error.get());
        assertTrue(error.get() instanceof IOException);
        assertTrue(error.get().getMessage().contains("interrupted"));
    }

    // ── authenticateViaBrowser ──────────────────────────────────────────────

    @Test
    public void testBrowserFlowPortAlreadyInUse() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int port = occupied.getLocalPort();
            try {
                flow.authenticateViaBrowser("client-id", "common", "User.Read", port);
                fail("Expected IOException");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("Port is already in use"));
                assertTrue(e.getMessage().contains(String.valueOf(port)));
            }
        }
    }

    /** Drives HTTP callbacks against the running browser flow server. */
    private interface CallbackDriver {
        void drive(int port, AtomicReference<Throwable> flowError) throws Exception;
    }

    /**
     * Runs the browser flow on a free port and lets the driver send callback
     * requests. Retries on port races with other processes (a free port probed
     * via ServerSocket(0) may be grabbed before the flow binds it).
     */
    private Throwable runBrowserFlowWithRetries(CallbackDriver driver) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            int port = findFreePort();
            AtomicReference<Throwable> error = startBrowserFlow(flow, port);
            try {
                waitForPort(port);
            } catch (Exception e) {
                continue; // server never came up
            }
            if (error.get() != null) {
                continue; // lost the port race (BindException) -> retry with a new port
            }
            try {
                driver.drive(port, error);
            } catch (IOException e) {
                continue; // transient socket issue or foreign server on a recycled port -> retry
            }
            Throwable failure = awaitError(error);
            if (failure != null && failure.getMessage() != null
                    && failure.getMessage().contains("Port is already in use")) {
                continue; // port race detected late -> retry
            }
            return failure;
        }
        fail("Browser flow could not be exercised on a free port after several attempts");
        return null;
    }

    @Test
    public void testBrowserFlowCallbackWithError() throws Exception {
        assumeTrue("Browser flow must not open a real browser", GraphicsEnvironment.isHeadless());

        Throwable failure = runBrowserFlowWithRetries((port, error) ->
                httpGet(port, "/?error=access_denied&error_description=%3Cscript%3Ebad%3C/script%3E"));

        assertNotNull("Flow should have failed", failure);
        assertTrue(failure instanceof IOException);
        assertTrue(failure.getMessage().contains("Browser authentication failed"));
    }

    @Test
    public void testBrowserFlowCallbackWithInvalidState() throws Exception {
        assumeTrue("Browser flow must not open a real browser", GraphicsEnvironment.isHeadless());

        Throwable failure = runBrowserFlowWithRetries((port, error) ->
                httpGet(port, "/?code=some-auth-code&state=wrong-state"));

        assertNotNull("Flow should have failed", failure);
        assertTrue(failure instanceof IOException);
        assertTrue(failure.getMessage().contains("Browser authentication failed"));
    }

    @Test
    public void testBrowserFlowCallbackWithInvalidRequestThenError() throws Exception {
        assumeTrue("Browser flow must not open a real browser", GraphicsEnvironment.isHeadless());

        Throwable failure = runBrowserFlowWithRetries((port, error) -> {
            // Query without code= or error= -> "Invalid Request" page, flow keeps waiting
            httpGet(port, "/?foo=bar");
            Thread.sleep(300);
            assertNull("Flow must still be waiting for a valid callback", error.get());
            // Release the flow with an error callback
            httpGet(port, "/?error=user_cancelled");
        });

        assertNotNull("Flow should have failed", failure);
        assertTrue(failure instanceof IOException);
    }

    @Test
    public void testBrowserFlowSuccessWithValidState() throws Exception {
        assumeTrue("Browser flow must not open a real browser", GraphicsEnvironment.isHeadless());

        withCapturedLogs(logs -> {
            Object[] outcome = runBrowserFlowWithState(logs, buildResponse(200, tokenJson("browser-access", "browser-refresh", 5400)));
            Throwable error = (Throwable) outcome[0];
            OAuth2AuthenticationFlow.TokenResponse result = (OAuth2AuthenticationFlow.TokenResponse) outcome[1];

            assertNull("Flow should have succeeded", error);
            assertNotNull("Flow should have returned tokens", result);
            assertEquals("browser-access", result.getAccessToken());
            assertEquals("browser-refresh", result.getRefreshToken());
            assertEquals(5400, result.getExpiresIn());
        });
    }

    @Test
    public void testBrowserFlowTokenExchangeFailure() throws Exception {
        assumeTrue("Browser flow must not open a real browser", GraphicsEnvironment.isHeadless());

        withCapturedLogs(logs -> {
            Object[] outcome = runBrowserFlowWithState(logs, buildResponse(400, "{\"error\":\"invalid_grant\"}"));
            Throwable error = (Throwable) outcome[0];

            assertNotNull("Flow should have failed", error);
            assertTrue(error instanceof IOException);
            assertTrue(error.getMessage().contains("Browser authentication failed"));
            assertNotNull("Token exchange failure should be the cause", error.getCause());
            assertTrue(error.getCause().getMessage().contains("Token exchange failed"));
        });
    }

    /** Installs a temporary log appender while the body runs, then removes it. */
    private void withCapturedLogs(ThrowingConsumer<java.util.List<String>> body) throws Exception {
        java.util.List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        org.apache.logging.log4j.core.LoggerContext context =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration configuration = context.getConfiguration();
        org.apache.logging.log4j.core.layout.PatternLayout layout = org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout();
        org.apache.logging.log4j.core.appender.AbstractAppender capture =
                new org.apache.logging.log4j.core.appender.AbstractAppender("oauth-test-capture", null, layout, false, null) {
                    @Override
                    public void append(org.apache.logging.log4j.core.LogEvent event) {
                        logs.add(new String(getLayout().toByteArray(event), java.nio.charset.StandardCharsets.UTF_8));
                    }
                };
        capture.start();
        configuration.addAppender(capture);
        org.apache.logging.log4j.core.config.LoggerConfig loggerConfig =
                configuration.getLoggerConfig(OAuth2AuthenticationFlow.class.getName());
        loggerConfig.addAppender(capture, null, null);
        // In full-suite runs other tests may have reconfigured log4j (default root
        // level ERROR), so pin this logger to INFO and restore it afterwards —
        // otherwise the flow's "Opening browser..." line never reaches the appender.
        org.apache.logging.log4j.Level previousLevel = loggerConfig.getLevel();
        loggerConfig.setLevel(org.apache.logging.log4j.Level.INFO);
        context.updateLoggers();
        try {
            body.accept(logs);
        } finally {
            loggerConfig.removeAppender("oauth-test-capture");
            loggerConfig.setLevel(previousLevel);
            capture.stop();
            context.updateLoggers();
        }
    }

    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    /**
     * Runs the full browser flow to completion: the random CSRF state is read
     * back from the captured logs and a valid callback is sent, so the flow
     * proceeds to exchangeAuthCodeForTokens against the mocked token endpoint.
     *
     * @return {error, result} of the flow execution
     */
    private Object[] runBrowserFlowWithState(java.util.List<String> logs, Response tokenEndpointResponse) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            logs.clear();
            mockHttp(tokenEndpointResponse);

            int port = findFreePort();
            AtomicReference<Throwable> error = new AtomicReference<>();
            AtomicReference<OAuth2AuthenticationFlow.TokenResponse> result = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try {
                    result.set(flow.authenticateViaBrowser("client-id", "common", "User.Read", port));
                } catch (Throwable t) {
                    error.set(t);
                }
            });
            thread.setDaemon(true);
            thread.start();

            try {
                waitForPort(port);
            } catch (Exception e) {
                continue;
            }
            if (error.get() != null) {
                continue; // port race -> retry
            }

            String state = awaitLoggedState(logs);
            assertNotNull("Authorization URL with state was not logged", state);
            httpGet(port, "/?code=auth-code-123&state=" + state);

            thread.join(5000);
            if (error.get() != null && error.get().getMessage() != null
                    && error.get().getMessage().contains("Port is already in use")) {
                continue; // port race detected late -> retry
            }
            return new Object[]{error.get(), result.get()};
        }
        fail("Browser flow could not be exercised on a free port after several attempts");
        return null;
    }

    /** Waits (up to 5s) for the authorization URL log line and extracts the state parameter. */
    private static String awaitLoggedState(java.util.List<String> logs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            for (String line : logs) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("state=([0-9a-fA-F]+)").matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            Thread.sleep(20);
        }
        return null;
    }

    // ── TokenResponse ───────────────────────────────────────────────────────

    @Test
    public void testTokenResponseGetters() {
        OAuth2AuthenticationFlow.TokenResponse tokens =
                new OAuth2AuthenticationFlow.TokenResponse("access", "refresh", 123);

        assertEquals("access", tokens.getAccessToken());
        assertEquals("refresh", tokens.getRefreshToken());
        assertEquals(123, tokens.getExpiresIn());
    }
}
