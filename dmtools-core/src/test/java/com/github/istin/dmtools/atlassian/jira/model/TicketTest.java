// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TicketTest {

    private Ticket ticket;
    private Fields fieldsMock;

    @Before
    public void setUp() throws JSONException {
        fieldsMock = mock(Fields.class);
        ticket = spy(new Ticket(new JSONObject()));
        doReturn(fieldsMock).when(ticket).getFields();
    }

    @Test
    public void testGetWeight() {
        when(fieldsMock.getStoryPoints()).thenReturn(5);
        assertEquals(5, ticket.getWeight(), 0.0);
    }

    @Test
    public void testAreStoryPointsSet() {
        JSONObject jsonObjectMock = mock(JSONObject.class);
        when(fieldsMock.getJSONObject()).thenReturn(jsonObjectMock);
        when(jsonObjectMock.optInt(Fields.STORY_POINTS, -1)).thenReturn(3);
        assertTrue(ticket.areStoryPointsSet());
    }

    @Test
    public void testGetKey() {
        doReturn("TICKET-123").when(ticket).getString("key");
        assertEquals("TICKET-123", ticket.getKey());
    }


    @Test
    public void testGetIterationName() {
        ReportIteration iterationMock = mock(ReportIteration.class);
        doReturn(iterationMock).when(ticket).getIteration();
        when(iterationMock.getIterationName()).thenReturn("Iteration 1");
        assertEquals("Iteration 1", ticket.getIterationName());
    }


    @Test
    public void testGetAttachments() {
        // Create a concrete list instead of mocking it
        List<IAttachment> attachments = new ArrayList<>();
        attachments.add(mock(IAttachment.class));

        // Use doReturn().when() for the method that returns a generic type
        doReturn(attachments).when(fieldsMock).getAttachments();

        List<? extends IAttachment> result = ticket.getAttachments();

        assertNotNull(result);
        assertEquals(attachments, result);
    }

    @Test
    public void testGetPriorityAsEnum() throws IOException {
        doReturn("High").when(ticket).getPriority();
        assertEquals(ITicket.TicketPriority.High, ticket.getPriorityAsEnum());
    }

    @Test
    public void testToText() {
        JSONObject jsonObjectMock = mock(JSONObject.class);
        doReturn(jsonObjectMock).when(ticket).getFieldsAsJSON();
        StringBuilder sb = new StringBuilder();
        sb.append("Next \n");
        StringUtils.transformJSONToText(sb, jsonObjectMock, false);
        assertEquals(sb.toString(), ticket.toText());
    }

    @Test
    public void testGetId() {
        doReturn("123").when(ticket).getString("id");
        assertEquals("123", ticket.getId());
    }

    @Test
    public void testGetStatus() throws IOException {
        Status statusMock = mock(Status.class);
        doReturn(statusMock).when(ticket).getStatusModel();
        when(statusMock.getName()).thenReturn("Open");
        assertEquals("Open", ticket.getStatus());
    }

    @Test
    public void testGetPriority() throws IOException {
        Priority priorityMock = mock(Priority.class);
        when(fieldsMock.getPriority()).thenReturn(priorityMock);
        when(priorityMock.getName()).thenReturn("High");
        assertEquals("High", ticket.getPriority());
    }

    @Test
    public void testGetStatusModel() throws IOException {
        Status statusMock = mock(Status.class);
        when(fieldsMock.getStatus()).thenReturn(statusMock);
        assertEquals(statusMock, ticket.getStatusModel());
    }

    @Test
    public void testGetChangelog() {
        Changelog changelogMock = mock(Changelog.class);
        doReturn(changelogMock).when(ticket).getModel(Changelog.class, "changelog");
        assertEquals(changelogMock, ticket.getChangelog());
    }

    @Test
    public void testGetTicketKey() {
        doReturn("TICKET-123").when(ticket).getKey();
        assertEquals("TICKET-123", ticket.getTicketKey());
    }

    @Test
    public void testGetIssueType() {
        IssueType issueTypeMock = mock(IssueType.class);
        when(fieldsMock.getIssueType()).thenReturn(issueTypeMock);
        when(issueTypeMock.getName()).thenReturn("Bug");
        assertEquals("Bug", ticket.getIssueType());
    }

    @Test
    public void testGetTicketLink() {
        doReturn("http://example.com/rest/api/2/issue/123").when(ticket).getString("self");
        doReturn("TICKET-123").when(ticket).getKey();
        assertEquals("http://example.com/browse/TICKET-123", ticket.getTicketLink());
    }

    @Test
    public void testGetBasePath() {
        assertEquals("http://example.com/", Ticket.getBasePath("http://example.com/rest/api/2/issue/123"));
    }

    @Test
    public void testGetTicketTitle() throws IOException {
        when(fieldsMock.getSummary()).thenReturn("Ticket Title");
        assertEquals("Ticket Title", ticket.getTicketTitle());
    }

    @Test
    public void testGetTicketDescription() {
        when(fieldsMock.getDescription()).thenReturn("Description");
        assertEquals("Description", ticket.getTicketDescription());
    }

    @Test
    public void testGetCreated() {
        Date dateMock = mock(Date.class);
        when(fieldsMock.getCreated()).thenReturn(dateMock);
        assertEquals(dateMock, ticket.getCreated());
    }

    @Test
    public void testGetFieldsAsJSON() {
        JSONObject jsonObjectMock = mock(JSONObject.class);
        when(fieldsMock.getJSONObject()).thenReturn(jsonObjectMock);
        assertEquals(jsonObjectMock, ticket.getFieldsAsJSON());
    }

    @Test
    public void testGetUpdatedAsMillis() {
        Long updatedMillis = 123456789L;
        when(fieldsMock.getUpdatedAsMillis()).thenReturn(updatedMillis);
        assertEquals(updatedMillis, ticket.getUpdatedAsMillis());
    }

    @Test
    public void testGetCreator() {
        Assignee creatorMock = mock(Assignee.class);
        when(fieldsMock.getCreator()).thenReturn(creatorMock);
        assertEquals(creatorMock, ticket.getCreator());
    }

    @Test
    public void testGetResolution() {
        Resolution resolutionMock = mock(Resolution.class);
        when(fieldsMock.getResolution()).thenReturn(resolutionMock);
        assertEquals(resolutionMock, ticket.getResolution());
    }

    @Test
    public void testGetTicketLabels() {
        JSONArray labelsMock = mock(JSONArray.class);
        when(fieldsMock.getLabels()).thenReturn(labelsMock);
        assertEquals(labelsMock, ticket.getTicketLabels());
    }

    @Test
    public void testGetHtmlTicketLink() {
        doReturn("http://example.com/browse/TICKET-123").when(ticket).getTicketLink();
        doReturn("TICKET-123").when(ticket).getKey();
        assertEquals("<a href=\"http://example.com/browse/TICKET-123\">TICKET-123</a>", ticket.getHtmlTicketLink());
    }


    @Test
    public void testToString() {
        JSONObject jsonObjectMock = mock(JSONObject.class);
        doReturn(jsonObjectMock).when(ticket).getJSONObject();
        when(jsonObjectMock.toString()).thenReturn("{json}");
        assertEquals("{json}", ticket.toString());
    }
}