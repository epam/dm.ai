// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage-oriented tests for {@link GitHubWorkflowUtils#triggerWorkflow}.
 *
 * The production code hardcodes {@code new URL("https://api.github.com/...").openConnection()},
 * so these tests install a JVM-wide {@link java.net.URLStreamHandlerFactory} that intercepts
 * the {@code https} protocol and hands out scripted {@link HttpURLConnection} fakes instead of
 * opening real sockets. No network is used. Only {@code https} is intercepted - existing tests
 * in this module only open {@code http://127.0.0.1} connections, which keep the default handler.
 *
 * Note: the retry tests incur the production backoff sleeps (200ms / 200+400ms); that is the
 * cost of exercising the retry logic without touching production code.
 */
public class GitHubWorkflowUtilsCoverageTest {

    private static final Queue<FakeHttpURLConnection> SCRIPTED = new ConcurrentLinkedQueue<>();
    private static final List<FakeHttpURLConnection> USED = new ArrayList<>();

    static {
        URL.setURLStreamHandlerFactory(protocol -> {
            if (!"https".equals(protocol)) {
                return null; // keep the default handler for http/file/etc.
            }
            return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                    FakeHttpURLConnection connection = SCRIPTED.poll();
                    if (connection == null) {
                        throw new IOException("No scripted connection available for " + u);
                    }
                    synchronized (USED) {
                        USED.add(connection);
                    }
                    return connection;
                }
            };
        });
    }

    private GitHub github;

    @Before
    public void setUp() {
        SCRIPTED.clear();
        synchronized (USED) {
            USED.clear();
        }
        github = mock(GitHub.class);
        when(github.processLargePayload(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(github.getTimeout()).thenReturn(30);
        when(github.getAuthorization()).thenReturn("test-token");
    }

    @After
    public void tearDown() {
        SCRIPTED.clear();
        synchronized (USED) {
            USED.clear();
        }
        // Clear a possibly re-set interrupt flag so other tests on this thread are unaffected.
        Thread.interrupted();
    }

    private static FakeHttpURLConnection scripted(FakeHttpURLConnection connection) {
        SCRIPTED.add(connection);
        return connection;
    }

    private static FakeHttpURLConnection successConnection() {
        return new FakeHttpURLConnection(204, null, null, null, null);
    }

    private static FakeHttpURLConnection httpErrorConnection(int code, String errorBody) {
        InputStream errorStream = errorBody == null ? null
                : new ByteArrayInputStream(errorBody.getBytes(StandardCharsets.UTF_8));
        return new FakeHttpURLConnection(code, null, null, null, errorStream);
    }

    private static FakeHttpURLConnection failingOnOutputStream(IOException failure) {
        return new FakeHttpURLConnection(0, failure, null, null, null);
    }

    @Test
    public void triggerWorkflow_successfulDispatch_writesBodySetsHeadersAndDisconnects() throws IOException {
        FakeHttpURLConnection connection = scripted(successConnection());

        GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "user request");

        String body = connection.getSentBody();
        assertTrue(body.contains("\"ref\":\"main\""));
        assertTrue(body.contains("\"user_request\":\"user request\""));
        assertEquals("Bearer test-token", connection.getRequestProperty("Authorization"));
        assertEquals("application/vnd.github.v3+json", connection.getRequestProperty("Accept"));
        assertEquals("application/json", connection.getRequestProperty("Content-Type"));
        assertEquals("DMTools", connection.getRequestProperty("User-Agent"));
        assertEquals("POST", connection.getRequestMethod());
        assertEquals(30000, connection.getConnectTimeout());
        assertEquals(30000, connection.getReadTimeout());
        assertTrue(connection.isDisconnected());
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_httpErrorWithErrorStream_throwsInputsTooLargeWhenBodySaysSo() {
        scripted(httpErrorConnection(422, "{\"message\":\"inputs are too large\"}"));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "big request"));

        assertTrue(exception.getMessage().contains("inputs are too large even after compression"));
        assertTrue(exception.getMessage().contains("big request".length() + " chars"));
        // 422 is not a connection error - no retry must have been attempted.
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_httpError404WithNullErrorStream_includesWorkflowNotFoundDetail() {
        scripted(httpErrorConnection(404, null));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "missing.yml", "request"));

        assertTrue(exception.getMessage().contains("URLConnection HTTP 404"));
        assertTrue(exception.getMessage().contains("Workflow not found"));
        assertTrue(exception.getMessage().contains("https://github.com/owner/repo/actions/workflows/missing.yml"));
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_httpError401_includesAuthenticationDetail() {
        scripted(httpErrorConnection(401, "unauthorized"));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));

        assertTrue(exception.getMessage().contains("Authentication/permission issue"));
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_httpError422_includesInvalidRequestDetail() {
        scripted(httpErrorConnection(422, "Validation Failed"));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));

        assertTrue(exception.getMessage().contains("Invalid request"));
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_connectionError_retriesAndSucceeds() throws IOException {
        scripted(failingOnOutputStream(new IOException("Connection reset by peer")));
        FakeHttpURLConnection retry = scripted(successConnection());

        GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request");

        assertEquals(2, usedCount());
        assertTrue(retry.isDisconnected());
    }

    @Test
    public void triggerWorkflow_connectionErrorMaxRetriesExceeded_throwsAfterThreeAttempts() {
        scripted(failingOnOutputStream(new IOException("broken pipe")));
        scripted(failingOnOutputStream(new IOException("Broken pipe")));
        scripted(failingOnOutputStream(new IOException("SSL handshake failed")));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));

        assertTrue(exception.getMessage().contains("URLConnection workflow trigger failed"));
        assertTrue(exception.getMessage().contains("Troubleshooting steps"));
        assertEquals(3, usedCount());
    }

    @Test
    public void triggerWorkflow_nonConnectionIoError_doesNotRetry() {
        scripted(failingOnOutputStream(new IOException("Permission denied")));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_ioErrorWithNullMessage_throwsWithoutRetryAndWithoutDetailsSuffix() {
        scripted(failingOnOutputStream(new IOException((String) null)));

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));

        assertTrue(exception.getMessage().contains("URLConnection workflow trigger failed"));
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_interruptedDuringRetryBackoff_throwsInterruptedIOException() {
        scripted(failingOnOutputStream(new IOException("unexpected end of stream")));
        Thread.currentThread().interrupt();
        try {
            IOException exception = assertThrows(IOException.class, () ->
                    GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));
            assertTrue(exception.getMessage().contains("interrupted during retry"));
            assertNotNull(exception.getCause());
        } finally {
            Thread.interrupted();
        }
        // No second attempt: the retry was aborted by the interruption.
        assertEquals(1, usedCount());
    }

    @Test
    public void triggerWorkflow_unexpectedRuntimeException_wrappedAsIOException() {
        FakeHttpURLConnection connection = new FakeHttpURLConnection(0, null, null,
                new IllegalStateException("boom 500"), null);
        scripted(connection);

        IOException exception = assertThrows(IOException.class, () ->
                GitHubWorkflowUtils.triggerWorkflow(github, "owner", "repo", "workflow.yml", "request"));

        assertTrue(exception.getMessage().contains("unexpected error"));
        assertTrue(exception.getMessage().contains("boom 500"));
        assertEquals(1, usedCount());
        assertTrue(connection.isDisconnected());
    }

    private static int usedCount() {
        synchronized (USED) {
            return USED.size();
        }
    }

    /**
     * Scripted {@link HttpURLConnection} replacement: never touches the network.
     */
    private static class FakeHttpURLConnection extends HttpURLConnection {

        private final int scriptedResponseCode;
        private final IOException outputStreamFailure;
        private final IOException responseCodeFailure;
        private final RuntimeException runtimeFailure;
        private final InputStream scriptedErrorStream;
        private final ByteArrayOutputStream sentBody = new ByteArrayOutputStream();
        private boolean disconnected;

        FakeHttpURLConnection(int scriptedResponseCode, IOException outputStreamFailure,
                              IOException responseCodeFailure, RuntimeException runtimeFailure,
                              InputStream scriptedErrorStream) {
            super(dummyUrl());
            this.scriptedResponseCode = scriptedResponseCode;
            this.outputStreamFailure = outputStreamFailure;
            this.responseCodeFailure = responseCodeFailure;
            this.runtimeFailure = runtimeFailure;
            this.scriptedErrorStream = scriptedErrorStream;
        }

        private static URL dummyUrl() {
            try {
                return new URL("https://api.github.com/scripted");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (outputStreamFailure != null) {
                throw outputStreamFailure;
            }
            return sentBody;
        }

        @Override
        public int getResponseCode() throws IOException {
            if (responseCodeFailure != null) {
                throw responseCodeFailure;
            }
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            return scriptedResponseCode;
        }

        @Override
        public InputStream getErrorStream() {
            return scriptedErrorStream;
        }

        String getSentBody() {
            return sentBody.toString(StandardCharsets.UTF_8);
        }

        boolean isDisconnected() {
            return disconnected;
        }
    }
}
