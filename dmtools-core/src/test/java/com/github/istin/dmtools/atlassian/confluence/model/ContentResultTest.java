// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ContentResultTest {

    private ContentResult contentResult;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        contentResult = new ContentResult(mockJsonObject);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"results\":[]}";
        ContentResult contentResultFromString = new ContentResult(jsonString);
        assertNotNull(contentResultFromString);
    }

    @Test
    public void testConstructorWithJsonObject() {
        ContentResult contentResultFromObject = new ContentResult(mockJsonObject);
        assertNotNull(contentResultFromObject);
    }

    @Test
    public void testGetContents() {
        List<Content> contents = contentResult.getContents();
        assertNotNull(contents);
        assertTrue(contents.isEmpty());
    }

    @Test
    public void testGetAttachments() {
        List<Attachment> attachments = contentResult.getAttachments();
        assertNotNull(attachments);
        assertTrue(attachments.isEmpty());
    }
}