// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class AttachmentTest {

    private Attachment attachment;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        attachment = new Attachment(mockJson);
    }

    @Test
    public void testGetFilename() {
        String expectedFilename = "testFile.txt";
        when(attachment.getString("filename")).thenReturn(expectedFilename);

        String filename = attachment.getFilename();
        assertEquals(expectedFilename, filename);
    }

    @Test
    public void testGetContent() {
        String expectedContent = "http://example.com/content";
        when(attachment.getString("content")).thenReturn(expectedContent);

        String content = attachment.getContent();
        assertEquals(expectedContent, content);
    }

    @Test
    public void testGetCreated() {
        Date expectedDate = new Date();
        mockStatic(Fields.class);
        when(Fields.getCreatedUtils(attachment)).thenReturn(expectedDate);

        Date createdDate = attachment.getCreated();
        assertEquals(expectedDate, createdDate);
    }

    @Test
    public void testGetName() {
        String expectedFilename = "testFile.txt";
        when(attachment.getString("filename")).thenReturn(expectedFilename);

        String name = attachment.getName();
        assertEquals(expectedFilename, name);
    }

    @Test
    public void testGetUrl() {
        String expectedContent = "http://example.com/content";
        when(attachment.getString("content")).thenReturn(expectedContent);

        String url = attachment.getUrl();
        assertEquals(expectedContent, url);
    }

    @Test
    public void testGetContentType() {
        assertNull(attachment.getContentType());
    }
}