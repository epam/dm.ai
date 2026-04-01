// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.networking;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class RestClientTest {

    private RestClient restClient;
    private OkHttpClient mockClient;
    private Request.Builder mockBuilder;
    private Response mockResponse;
    private ResponseBody mockResponseBody;
    private GenericRequest mockGenericRequest;
    private File mockFile;

    @Before
    public void setUp() {
        restClient = mock(RestClient.class);
        mockClient = mock(OkHttpClient.class);
        mockBuilder = mock(Request.Builder.class);
        mockResponse = mock(Response.class);
        mockResponseBody = mock(ResponseBody.class);
        mockGenericRequest = mock(GenericRequest.class);
        mockFile = mock(File.class);

        when(restClient.getClient()).thenReturn(mockClient);
        when(restClient.sign(any(Request.Builder.class))).thenReturn(mockBuilder);
    }

    @Test
    public void testGetFileImageExtension() {
        assertEquals(".png", RestClient.Impl.getFileImageExtension("http://example.com/image.png"));
        assertEquals(".jpg", RestClient.Impl.getFileImageExtension("http://example.com/image.jpg"));
        assertEquals(".jpg", RestClient.Impl.getFileImageExtension("http://example.com/image.jpeg"));
        assertEquals(".gif", RestClient.Impl.getFileImageExtension("http://example.com/image.gif"));
        assertEquals(".bmp", RestClient.Impl.getFileImageExtension("http://example.com/image.bmp"));
        assertEquals(".svg", RestClient.Impl.getFileImageExtension("http://example.com/image.svg"));
        assertEquals(".webp", RestClient.Impl.getFileImageExtension("http://example.com/image.webp"));
        assertEquals("", RestClient.Impl.getFileImageExtension("http://example.com/image.txt"));
    }
}