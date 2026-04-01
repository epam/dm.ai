// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.tracker;

import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TrackerClientTest {

    private TrackerClient<ITicket> trackerClient;
    private ITicket mockTicket;

    @Before
    public void setUp() {
        trackerClient = Mockito.mock(TrackerClient.class);
        mockTicket = Mockito.mock(ITicket.class);
    }

    @Test
    public void testLinkIssueWithRelationship() throws IOException {
        String sourceKey = "SRC-123";
        String anotherKey = "ANR-456";
        String relationship = "relates to";
        String expectedResponse = "Link created";

        when(trackerClient.linkIssueWithRelationship(sourceKey, anotherKey, relationship)).thenReturn(expectedResponse);

        String actualResponse = trackerClient.linkIssueWithRelationship(sourceKey, anotherKey, relationship);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void testTag() {
        String initiator = "user";
        String expectedTag = "tagged";

        when(trackerClient.tag(initiator)).thenReturn(expectedTag);

        String actualTag = trackerClient.tag(initiator);
        assertEquals(expectedTag, actualTag);
    }

    @Test
    public void testUpdateDescription() throws IOException {
        String key = "TCK-789";
        String description = "New description";
        String expectedResponse = "Description updated";

        when(trackerClient.updateDescription(key, description)).thenReturn(expectedResponse);

        String actualResponse = trackerClient.updateDescription(key, description);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void testUpdateTicket() throws IOException {
        String key = "TCK-789";
        TrackerClient.FieldsInitializer fieldsInitializer = fields -> fields.set("field", "value");
        String expectedResponse = "Ticket updated";

        when(trackerClient.updateTicket(key, fieldsInitializer)).thenReturn(expectedResponse);

        String actualResponse = trackerClient.updateTicket(key, fieldsInitializer);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void testBuildUrlToSearch() {
        String query = "project = TEST";
        String expectedUrl = "http://example.com/search?jql=project%20%3D%20TEST";

        when(trackerClient.buildUrlToSearch(query)).thenReturn(expectedUrl);

        String actualUrl = trackerClient.buildUrlToSearch(query);
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void testGetBasePath() {
        String expectedBasePath = "http://example.com";

        when(trackerClient.getBasePath()).thenReturn(expectedBasePath);

        String actualBasePath = trackerClient.getBasePath();
        assertEquals(expectedBasePath, actualBasePath);
    }

    @Test
    public void testGetTicketBrowseUrl() {
        String ticketKey = "TCK-789";
        String expectedUrl = "http://example.com/browse/TCK-789";

        when(trackerClient.getTicketBrowseUrl(ticketKey)).thenReturn(expectedUrl);

        String actualUrl = trackerClient.getTicketBrowseUrl(ticketKey);
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void testAssignTo() throws IOException {
        String ticketKey = "TCK-789";
        String userName = "user";
        String expectedResponse = "Assigned";

        when(trackerClient.assignTo(ticketKey, userName)).thenReturn(expectedResponse);

        String actualResponse = trackerClient.assignTo(ticketKey, userName);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void testGetChangeLog() throws IOException {
        String ticketKey = "TCK-789";
        IChangelog expectedChangelog = Mockito.mock(IChangelog.class);

        when(trackerClient.getChangeLog(ticketKey, mockTicket)).thenReturn(expectedChangelog);

        IChangelog actualChangelog = trackerClient.getChangeLog(ticketKey, mockTicket);
        assertEquals(expectedChangelog, actualChangelog);
    }

    @Test
    public void testDeleteLabelInTicket() throws IOException {
        String label = "label";

        doNothing().when(trackerClient).deleteLabelInTicket(mockTicket, label);

        trackerClient.deleteLabelInTicket(mockTicket, label);

        verify(trackerClient, times(1)).deleteLabelInTicket(mockTicket, label);
    }

    @Test
    public void testAddLabelIfNotExists() throws IOException {
        String label = "label";

        doNothing().when(trackerClient).addLabelIfNotExists(mockTicket, label);

        trackerClient.addLabelIfNotExists(mockTicket, label);

        verify(trackerClient, times(1)).addLabelIfNotExists(mockTicket, label);
    }

    @Test
    public void testCreateTicketInProject() throws IOException {
        String project = "TEST";
        String issueType = "Bug";
        String summary = "Summary";
        String description = "Description";
        TrackerClient.FieldsInitializer fieldsInitializer = fields -> fields.set("field", "value");
        String expectedResponse = "Ticket created";

        when(trackerClient.createTicketInProject(project, issueType, summary, description, fieldsInitializer)).thenReturn(expectedResponse);

        String actualResponse = trackerClient.createTicketInProject(project, issueType, summary, description, fieldsInitializer);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void testSearchAndPerform() throws Exception {
        String searchQuery = "project = TEST";
        String[] fields = {"field1", "field2"};
        List<ITicket> expectedTickets = List.of(mockTicket);

        when(trackerClient.searchAndPerform(searchQuery, fields)).thenReturn(expectedTickets);

        List<ITicket> actualTickets = trackerClient.searchAndPerform(searchQuery, fields);
        assertEquals(expectedTickets, actualTickets);
    }

    @Test
    public void testPerformTicket() throws IOException {
        String ticketKey = "TCK-789";
        String[] fields = {"field1", "field2"};
        ITicket expectedTicket = mockTicket;

        when(trackerClient.performTicket(ticketKey, fields)).thenReturn(expectedTicket);

        ITicket actualTicket = trackerClient.performTicket(ticketKey, fields);
        assertEquals(expectedTicket, actualTicket);
    }

    @Test
    public void testPostCommentIfNotExists() throws IOException {
        String ticketKey = "TCK-789";
        String comment = "Comment";

        doNothing().when(trackerClient).postCommentIfNotExists(ticketKey, comment);

        trackerClient.postCommentIfNotExists(ticketKey, comment);

        verify(trackerClient, times(1)).postCommentIfNotExists(ticketKey, comment);
    }

    @Test
    public void testGetComments() throws IOException {
        String ticketKey = "TCK-789";
        List<IComment> expectedComments = List.of(Mockito.mock(IComment.class));

        doReturn(expectedComments).when(trackerClient).getComments(eq(ticketKey), any());

        List<? extends IComment> actualComments = trackerClient.getComments(ticketKey, mockTicket);
        assertEquals(expectedComments, actualComments);
    }

    @Test
    public void testPostComment() throws IOException {
        String ticketKey = "TCK-789";
        String comment = "Comment";

        doNothing().when(trackerClient).postComment(ticketKey, comment);

        trackerClient.postComment(ticketKey, comment);

        verify(trackerClient, times(1)).postComment(ticketKey, comment);
    }

    @Test
    public void testDeleteCommentIfExists() throws IOException {
        String ticketKey = "TCK-789";
        String comment = "Comment";

        doNothing().when(trackerClient).deleteCommentIfExists(ticketKey, comment);

        trackerClient.deleteCommentIfExists(ticketKey, comment);

        verify(trackerClient, times(1)).deleteCommentIfExists(ticketKey, comment);
    }

    @Test
    public void testMoveToStatus() throws IOException {
        String ticketKey = "TCK-789";
        String statusName = "Done";
        String expectedResponse = "Moved to Done";

        when(trackerClient.moveToStatus(ticketKey, statusName)).thenReturn(expectedResponse);

        String actualResponse = trackerClient.moveToStatus(ticketKey, statusName);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void testGetDefaultQueryFields() {
        String[] expectedFields = {"field1", "field2"};

        when(trackerClient.getDefaultQueryFields()).thenReturn(expectedFields);

        String[] actualFields = trackerClient.getDefaultQueryFields();
        assertArrayEquals(expectedFields, actualFields);
    }

    @Test
    public void testGetExtendedQueryFields() {
        String[] expectedFields = {"field1", "field2", "field3"};

        when(trackerClient.getExtendedQueryFields()).thenReturn(expectedFields);

        String[] actualFields = trackerClient.getExtendedQueryFields();
        assertArrayEquals(expectedFields, actualFields);
    }

    @Test
    public void testGetDefaultStatusField() {
        String expectedStatusField = "status";

        when(trackerClient.getDefaultStatusField()).thenReturn(expectedStatusField);

        String actualStatusField = trackerClient.getDefaultStatusField();
        assertEquals(expectedStatusField, actualStatusField);
    }

    @Test
    public void testGetTestCases() throws IOException {
        List<ITicket> expectedTestCases = List.of(mockTicket);

        doReturn(expectedTestCases).when(trackerClient).getTestCases(any(), any());

        List<? extends ITicket> actualTestCases = trackerClient.getTestCases(mockTicket, "Test Case");
        assertEquals(expectedTestCases, actualTestCases);
    }

    @Test
    public void testSetLogEnabled() {
        doNothing().when(trackerClient).setLogEnabled(true);

        trackerClient.setLogEnabled(true);

        verify(trackerClient, times(1)).setLogEnabled(true);
    }

    @Test
    public void testSetCacheGetRequestsEnabled() {
        doNothing().when(trackerClient).setCacheGetRequestsEnabled(true);

        trackerClient.setCacheGetRequestsEnabled(true);

        verify(trackerClient, times(1)).setCacheGetRequestsEnabled(true);
    }

    @Test
    public void testGetFixVersions() throws IOException {
        String projectCode = "TEST";
        List<ReportIteration> expectedFixVersions = List.of(Mockito.mock(ReportIteration.class));

        doReturn(expectedFixVersions).when(trackerClient).getFixVersions(eq(projectCode));

        List<? extends ReportIteration> actualFixVersions = trackerClient.getFixVersions(projectCode);
        assertEquals(expectedFixVersions, actualFixVersions);
    }

    @Test
    public void testGetTextType() {
        TrackerClient.TextType expectedTextType = TrackerClient.TextType.HTML;

        when(trackerClient.getTextType()).thenReturn(expectedTextType);

        TrackerClient.TextType actualTextType = trackerClient.getTextType();
        assertEquals(expectedTextType, actualTextType);
    }

    @Test
    public void testAttachFileToTicket() throws IOException {
        String ticketKey = "TCK-789";
        String name = "file.txt";
        String contentType = "text/plain";
        File file = new File("path/to/file.txt");

        doNothing().when(trackerClient).attachFileToTicket(ticketKey, name, contentType, file);

        trackerClient.attachFileToTicket(ticketKey, name, contentType, file);

        verify(trackerClient, times(1)).attachFileToTicket(ticketKey, name, contentType, file);
    }
}