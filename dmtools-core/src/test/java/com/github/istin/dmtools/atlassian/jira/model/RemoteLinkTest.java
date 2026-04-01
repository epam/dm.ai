// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.ITicket;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RemoteLinkTest {

    private RemoteLink remoteLink;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        remoteLink = new RemoteLink(mockJsonObject);
    }

    @Test
    public void testGetRelationship() {
        when(mockJsonObject.getString("relationship")).thenReturn("related");
        assertEquals("related", remoteLink.getRelationship());
    }

    @Test
    public void testGetUrl() {
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("url")).thenReturn("http://example.com");
        assertEquals("http://example.com", remoteLink.getUrl());
    }

    @Test
    public void testGetTitle() {
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("title")).thenReturn("Example Title");
        assertEquals("Example Title", remoteLink.getTitle());
    }

    @Test
    public void testIsBlocker() {
        when(mockJsonObject.getString("relationship")).thenReturn("is blocked by");
        assertTrue(remoteLink.isBlocker());
    }

    @Test
    public void testGetStatus() throws IOException {
        assertNull(remoteLink.getStatus());
    }

    @Test
    public void testGetPriority() throws IOException {
        assertNull(remoteLink.getPriority());
    }

    @Test
    public void testGetStatusModel() throws IOException {
        assertNull(remoteLink.getStatusModel());
    }

    @Test
    public void testGetTicketKey() {
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("title")).thenReturn("Example Title");
        assertEquals("Example Title", remoteLink.getTicketKey());
    }

    @Test
    public void testGetIssueType() throws IOException {
        assertEquals("Unknown", remoteLink.getIssueType());
    }

    @Test
    public void testGetTicketLink() {
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("url")).thenReturn("http://example.com");
        assertEquals("http://example.com", remoteLink.getTicketLink());
    }

    @Test
    public void testGetHtmlTicketLink() {
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("url")).thenReturn("http://example.com");
        when(object.getString("title")).thenReturn("Example Title");
        assertEquals("<a href=\"http://example.com\">Example Title</a>", remoteLink.getHtmlTicketLink());
    }

    @Test
    public void testGetGlobalId() {
        when(mockJsonObject.getString("globalId")).thenReturn("global-id-123");
        assertEquals("global-id-123", remoteLink.getGlobalId());
    }

    @Test
    public void testGetTicketTitle() throws IOException {
        assertEquals("Unknown", remoteLink.getTicketTitle());
    }

    @Test
    public void testGetTicketDescription() {
        assertEquals("Unknown", remoteLink.getTicketDescription());
    }

    @Test
    public void testGetTicketDependenciesDescription() {
        assertNull(remoteLink.getTicketDependenciesDescription());
    }

    @Test
    public void testGetCreated() {
        assertNull(remoteLink.getCreated());
    }

    @Test
    public void testGetFieldsAsJSON() {
        assertNull(remoteLink.getFieldsAsJSON());
    }

    @Test
    public void testGetUpdatedAsMillis() {
        assertNull(remoteLink.getUpdatedAsMillis());
    }

    @Test
    public void testGetCreator() {
        assertNull(remoteLink.getCreator());
    }

    @Test
    public void testGetResolution() {
        assertNull(remoteLink.getResolution());
    }

    @Test
    public void testGetTicketLabels() {
        assertNull(remoteLink.getTicketLabels());
    }

    @Test
    public void testGetFields() {
        assertNull(remoteLink.getFields());
    }

    @Test
    public void testGetIteration() {
        assertNull(remoteLink.getIteration());
    }

    @Test
    public void testGetProgress() throws IOException {
        assertEquals(0.0, remoteLink.getProgress(), 0.0);
    }

    @Test
    public void testGetAttachments() {
        assertTrue(remoteLink.getAttachments().isEmpty());
    }

    @Test
    public void testGetPriorityAsEnum() {
        assertNull(remoteLink.getPriorityAsEnum());
    }

    @Test
    public void testToText() {
        assertEquals(remoteLink.toString(), remoteLink.toText());
    }

    @Test
    public void testEquals() {
        ITicket mockTicket = mock(ITicket.class);
        when(mockTicket.getTicketKey()).thenReturn("Example Title");
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("title")).thenReturn("Example Title");
        assertTrue(remoteLink.equals(mockTicket));
    }

    @Test
    public void testGetWeight() {
        assertEquals(1.0, remoteLink.getWeight(), 0.0);
    }

    @Test
    public void testGetKey() {
        JSONObject object = mock(JSONObject.class);
        when(mockJsonObject.getJSONObject("object")).thenReturn(object);
        when(object.getString("title")).thenReturn("Example Title");
        assertEquals("Example Title", remoteLink.getKey());
    }
}