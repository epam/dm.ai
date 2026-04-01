// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.networking;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class GenericRequestTest {

    private RestClient restClientMock;
    private GenericRequest genericRequest;

    @Before
    public void setUp() {
        restClientMock = mock(RestClient.class);
        genericRequest = new GenericRequest(restClientMock, "http://example.com");
    }

    @Test
    public void testIsIgnoreCache() {
        assertTrue(!genericRequest.isIgnoreCache());
        genericRequest.setIgnoreCache(true);
        assertTrue(genericRequest.isIgnoreCache());
    }

    @Test
    public void testFields() {
        genericRequest.fields("field1", "field2");
        assertEquals("http://example.com?fields=field1%2Cfield2", genericRequest.url());
    }

    @Test
    public void testParamWithInt() {
        genericRequest.param("param1", 123);
        assertEquals("http://example.com?param1=123", genericRequest.url());
    }

    @Test
    public void testParamWithString() throws UnsupportedEncodingException {
        genericRequest.param("param1", "value1");
        assertEquals("http://example.com?param1=value1", genericRequest.url());
    }

    @Test
    public void testSetBodyAndGetBody() {
        genericRequest.setBody("Test Body");
        assertEquals("Test Body", genericRequest.getBody());
    }

    @Test
    public void testExecute() throws IOException {
        when(restClientMock.execute(genericRequest)).thenReturn("response");
        String response = genericRequest.execute();
        assertEquals("response", response);
        verify(restClientMock, times(1)).execute(genericRequest);
    }

    @Test
    public void testPost() throws IOException {
        when(restClientMock.post(genericRequest)).thenReturn("response");
        String response = genericRequest.post();
        assertEquals("response", response);
        verify(restClientMock, times(1)).post(genericRequest);
    }

    @Test
    public void testPatch() throws IOException {
        when(restClientMock.patch(genericRequest)).thenReturn("response");
        String response = genericRequest.patch();
        assertEquals("response", response);
        verify(restClientMock, times(1)).patch(genericRequest);
    }

    @Test
    public void testPut() throws IOException {
        when(restClientMock.put(genericRequest)).thenReturn("response");
        String response = genericRequest.put();
        assertEquals("response", response);
        verify(restClientMock, times(1)).put(genericRequest);
    }

    @Test
    public void testDelete() throws IOException {
        when(restClientMock.delete(genericRequest)).thenReturn("response");
        String response = genericRequest.delete();
        assertEquals("response", response);
        verify(restClientMock, times(1)).delete(genericRequest);
    }

    @Test
    public void testParamWithNullValue_shouldSkipParameter() {
        // Given: A request with null parameter value
        genericRequest.param("nullParam", (String) null);
        
        // Then: URL should not contain the parameter (no ? added)
        assertEquals("http://example.com", genericRequest.url());
    }

    @Test
    public void testParamWithEmptyValue_shouldSkipParameter() {
        // Given: A request with empty parameter value
        genericRequest.param("emptyParam", "");
        
        // Then: URL should not contain the parameter (no ? added)
        assertEquals("http://example.com", genericRequest.url());
    }

    @Test
    public void testParamWithNullValue_afterValidParam_shouldOnlyIncludeValidParam() {
        // Given: A request with valid param followed by null param
        genericRequest.param("validParam", "value");
        genericRequest.param("nullParam", (String) null);
        
        // Then: URL should only contain the valid parameter
        assertEquals("http://example.com?validParam=value", genericRequest.url());
    }
}