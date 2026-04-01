// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally;

import com.github.istin.dmtools.broadcom.rally.model.RallyFields;
import com.github.istin.dmtools.broadcom.rally.model.RallyIssue;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RallyClientTest {

    private RallyClient rallyClient;
    private static final String BASE_PATH = "http://example.com";
    private static final String AUTHORIZATION = "auth-token";

    @Before
    public void setUp() throws IOException {
        rallyClient = Mockito.mock(RallyClient.class, Mockito.withSettings()
                .useConstructor(BASE_PATH, AUTHORIZATION)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = rallyClient.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testPath() {
        String path = "testPath";
        String fullPath = rallyClient.path(path);
        assertEquals(BASE_PATH + "/slm/webservice/v2.0/" + path, fullPath);
    }


    @Test
    public void testClearCacheForIssue() {
        String formattedID = "ID123";
        String[] fields = {"field1", "field2"};
        rallyClient.clearCacheForIssue(formattedID, fields);
        // No exception means success
    }


    @Test
    public void testGetBrowseUrl() {
        RallyIssue issue = mock(RallyIssue.class);
        when(issue.getRef()).thenReturn("ref123");
        String url = rallyClient.getBrowseUrl(issue);
        assertNotNull(url);
    }

    @Test
    public void testGetLastTwoSegments() {
        String url = "http://example.com/segment1/segment2";
        String result = RallyClient.getLastTwoSegments(url);
        assertEquals("segment1/segment2", result);
    }

    @Test
    public void testTag() {
        String tag = rallyClient.tag("initiator");
        assertEquals("", tag);
    }

    @Test
    public void testGetTicketBrowseUrl() {
        String ticketKey = "ticketKey";
        String url = rallyClient.getTicketBrowseUrl(ticketKey);
        assertNotNull(url);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAssignTo() throws IOException {
        rallyClient.assignTo("ticketKey", "userName");
    }


    @Test
    public void testGetTextType() {
        assertEquals(TrackerClient.TextType.HTML, rallyClient.getTextType());
    }



    @Test
    public void testSetCacheExpirationForJQLInHours() {
        rallyClient.setCacheExpirationForJQLInHours("jql", 1);
        // No exception means success
    }


    @Test
    public void testCreateTicketFromIssue() {
        RallyIssue issue = mock(RallyIssue.class);
        RallyIssue result = rallyClient.createTicket(issue);
        assertEquals(issue, result);
    }

    @Test
    public void testGetTicketClass() {
        assertEquals(RallyIssue.class, rallyClient.getTicketClass());
    }


    @Test
    public void testGetDefaultStatusField() {
        assertEquals("FLOW STATE", rallyClient.getDefaultStatusField());
    }

    @Test
    public void testGetExtendedQueryFields() {
        assertArrayEquals(RallyFields.DEFAULT_EXTENDED, rallyClient.getExtendedQueryFields());
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testLinkIssueWithRelationship() throws IOException {
        rallyClient.linkIssueWithRelationship("sourceKey", "anotherKey", "relationship");
    }


    @Test
    public void testIsValidImageUrl() {
        assertTrue(rallyClient.isValidImageUrl("http://rally1.rallydev.com/image.png"));
        assertFalse(rallyClient.isValidImageUrl("http://example.com/image.png"));
    }


    @Test
    public void testSetLogEnabled() {
        rallyClient.setLogEnabled(true);
        // No exception means success
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateDescription() throws IOException {
        rallyClient.updateDescription("key", "description");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAttachFileToTicket() throws IOException {
        rallyClient.attachFileToTicket("ticketKey", "name", "contentType", new File("path"));
    }
}