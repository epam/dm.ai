// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class RallyAttachmentTest {

    private RallyAttachment rallyAttachment;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        when(mockJson.getString("Name")).thenReturn("TestName");
        when(mockJson.getString("ContentType")).thenReturn("application/json");
        when(mockJson.getString(RallyFields._REF)).thenReturn("http://example.com/webservice/v2.x/ref");

        rallyAttachment = new RallyAttachment(mockJson);
    }

    @Test
    public void testGetRef() {
        String ref = rallyAttachment.getRef();
        assertEquals("http://example.com/webservice/v2.x/ref", ref);
    }

    @Test
    public void testGetName() {
        String name = rallyAttachment.getName();
        assertEquals("TestName", name);
    }

    @Test
    public void testGetUrl() {
        String url = rallyAttachment.getUrl();
        assertEquals("http://example.com/ref/TestName", url);
    }

    @Test
    public void testGetContentType() {
        String contentType = rallyAttachment.getContentType();
        assertEquals("application/json", contentType);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"Name\":\"TestName\",\"ContentType\":\"application/json\",\"_ref\":\"http://example.com/webservice/v2.x/ref\"}";
        RallyAttachment attachment = new RallyAttachment(jsonString);
        assertNotNull(attachment);
        assertEquals("TestName", attachment.getName());
        assertEquals("application/json", attachment.getContentType());
        assertEquals("http://example.com/webservice/v2.x/ref", attachment.getRef());
    }

    @Test
    public void testConstructorWithJsonObject() {
        RallyAttachment attachment = new RallyAttachment(mockJson);
        assertNotNull(attachment);
        assertEquals("TestName", attachment.getName());
        assertEquals("application/json", attachment.getContentType());
        assertEquals("http://example.com/webservice/v2.x/ref", attachment.getRef());
    }
}