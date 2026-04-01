// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContentTest {

    private Content content;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = mock(JSONObject.class);
        content = new Content(jsonObject);
    }

    @Test
    public void testGetId() {
        when(jsonObject.getString(Content.ID)).thenReturn("123");
        assertEquals("123", content.getId());
    }

    @Test
    public void testGetTitle() {
        when(jsonObject.getString(Content.TITLE)).thenReturn("Test Title");
        assertEquals("Test Title", content.getTitle());
    }

    @Test
    public void testGetStorage() {
        JSONObject body = mock(JSONObject.class);
        JSONObject storage = mock(JSONObject.class);
        when(jsonObject.getJSONObject("body")).thenReturn(body);
        when(body.getJSONObject(Content.STORAGE)).thenReturn(storage);

        Storage result = content.getStorage();
        assertNotNull(result);
    }

    @Test
    public void testGetVersionNumber() {
        JSONObject version = mock(JSONObject.class);
        when(jsonObject.getJSONObject(Content.VERSION)).thenReturn(version);
        when(version.getInt("number")).thenReturn(5);

        assertEquals(5, content.getVersionNumber());
    }

    @Test
    public void testGetViewUrl() {
        JSONObject links = mock(JSONObject.class);
        when(jsonObject.getJSONObject("_links")).thenReturn(links);
        when(links.getString("webui")).thenReturn("/view/url");

        String basePath = "http://example.com";
        assertEquals("http://example.com/view/url", content.getViewUrl(basePath));
    }

    @Test
    public void testToText() throws IOException {
        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn("Sample Text");
        Content spyContent = Mockito.spy(content);
        doReturn(storage).when(spyContent).getStorage();

        assertEquals("Sample Text", spyContent.toText());
    }

    @Test
    public void testGetWeight() {
        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn("Sample Text");
        Content spyContent = Mockito.spy(content);
        doReturn(storage).when(spyContent).getStorage();

        assertEquals(0.011, spyContent.getWeight(), 0.001);
    }

    @Test
    public void testGetKey() {
        JSONObject expandable = mock(JSONObject.class);
        when(jsonObject.optJSONObject("_expandable")).thenReturn(expandable);
        when(expandable.optString("space")).thenReturn("space/KEY");

        when(jsonObject.getString(Content.ID)).thenReturn("123");

        assertEquals("KEY-123", content.getKey());
    }
}