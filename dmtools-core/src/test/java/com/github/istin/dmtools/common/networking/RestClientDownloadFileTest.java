// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.networking;

import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestClientDownloadFileTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private HttpServer server;
    private RestClient restClient;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        restClient = mock(RestClient.class);
        when(restClient.getClient()).thenReturn(new OkHttpClient());
        when(restClient.sign(any(Request.Builder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private GenericRequest request(String path) {
        GenericRequest request = mock(GenericRequest.class);
        when(request.url()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + path);
        return request;
    }

    @Test
    public void truncatedDownloadDoesNotLeaveAPartialFile() throws Exception {
        server.createContext("/cut", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write(new byte[10]);
            exchange.getResponseBody().flush();
            exchange.close();
        });
        File target = new File(tempFolder.getRoot(), "file.bin");

        try {
            RestClient.Impl.downloadFile(restClient, request("/cut"), target);
            fail("expected a failure for a truncated body");
        } catch (IOException expected) {
            // expected
        }

        assertFalse("partial file must be removed so a retry downloads it again", target.exists());
    }

    @Test
    public void successfulDownloadWritesTheFile() throws Exception {
        server.createContext("/ok", exchange -> {
            byte[] body = "hello".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        File target = new File(tempFolder.getRoot(), "ok.txt");

        File result = RestClient.Impl.downloadFile(restClient, request("/ok"), target);

        assertEquals(5, result.length());
    }
}
