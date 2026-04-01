// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ITicketTest {

    private ITicket ticket;
    private ITicket.Wrapper ticketWrapper;
    private ITicket.ITicketProgress.Impl ticketProgressImpl;

    @Before
    public void setUp() {
        ticket = mock(ITicket.class);
        ticketWrapper = new ITicket.Wrapper(ticket);
        ticketProgressImpl = new ITicket.ITicketProgress.Impl();
    }

    @Test
    public void testGetStatus() throws IOException {
        when(ticket.getStatus()).thenReturn("Open");
        assertEquals("Open", ticketWrapper.getStatus());
    }

    @Test
    public void testGetStatusModel() throws IOException {
        Status status = mock(Status.class);
        when(ticket.getStatusModel()).thenReturn(status);
        assertEquals(status, ticketWrapper.getStatusModel());
    }

    @Test
    public void testGetTicketKey() {
        when(ticket.getTicketKey()).thenReturn("TICKET-123");
        assertEquals("TICKET-123", ticketWrapper.getTicketKey());
    }

    @Test
    public void testGetIssueType() throws IOException {
        when(ticket.getIssueType()).thenReturn("Bug");
        assertEquals("Bug", ticketWrapper.getIssueType());
    }

    @Test
    public void testGetPriority() throws IOException {
        when(ticket.getPriority()).thenReturn("High");
        assertEquals("High", ticketWrapper.getPriority());
    }

    @Test
    public void testGetTicketTitle() throws IOException {
        when(ticket.getTicketTitle()).thenReturn("Title");
        assertEquals("Title", ticketWrapper.getTicketTitle());
    }

    @Test
    public void testGetTicketDescription() {
        when(ticket.getTicketDescription()).thenReturn("Description");
        assertEquals("Description", ticketWrapper.getTicketDescription());
    }

    @Test
    public void testGetTicketDependenciesDescription() {
        when(ticket.getTicketDependenciesDescription()).thenReturn("Dependencies");
        assertEquals("Dependencies", ticketWrapper.getTicketDependenciesDescription());
    }

    @Test
    public void testGetCreated() {
        Date date = new Date();
        when(ticket.getCreated()).thenReturn(date);
        assertEquals(date, ticketWrapper.getCreated());
    }

    @Test
    public void testGetFieldsAsJSON() {
        JSONObject jsonObject = new JSONObject();
        when(ticket.getFieldsAsJSON()).thenReturn(jsonObject);
        assertEquals(jsonObject, ticketWrapper.getFieldsAsJSON());
    }

    @Test
    public void testGetUpdatedAsMillis() {
        Long millis = 123456789L;
        when(ticket.getUpdatedAsMillis()).thenReturn(millis);
        assertEquals(millis, ticketWrapper.getUpdatedAsMillis());
    }

    @Test
    public void testGetCreator() {
        IUser user = mock(IUser.class);
        when(ticket.getCreator()).thenReturn(user);
        assertEquals(user, ticketWrapper.getCreator());
    }

    @Test
    public void testGetResolution() {
        Resolution resolution = mock(Resolution.class);
        when(ticket.getResolution()).thenReturn(resolution);
        assertEquals(resolution, ticketWrapper.getResolution());
    }

    @Test
    public void testGetTicketLabels() {
        JSONArray jsonArray = new JSONArray();
        when(ticket.getTicketLabels()).thenReturn(jsonArray);
        assertEquals(jsonArray, ticketWrapper.getTicketLabels());
    }

    @Test
    public void testGetFields() {
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        assertEquals(fields, ticketWrapper.getFields());
    }

    @Test
    public void testGetIteration() {
        assertNull(ticketWrapper.getIteration());
    }

    @Test
    public void testGetProgress() throws IOException {
        when(ticket.getProgress()).thenReturn(75.0);
        assertEquals(75.0, ticketWrapper.getProgress(), 0.01);
    }

    @Test
    public void testGetAttachments() {
        List<IAttachment> mockAttachments = new ArrayList<>();
        mockAttachments.add(mock(IAttachment.class));

        doReturn(mockAttachments).when(ticket).getAttachments();

        List<? extends IAttachment> attachments = ticketWrapper.getAttachments();
        assertEquals(mockAttachments, attachments);
    }

    @Test
    public void testGetPriorityAsEnum() throws IOException {
        when(ticket.getPriority()).thenReturn("High");
        assertEquals(ITicket.TicketPriority.High, ticketWrapper.getPriorityAsEnum());
    }

    @Test
    public void testToText() throws IOException {
        when(ticket.toText()).thenReturn("Text");
        assertEquals("Text", ticketWrapper.toText());
    }

    @Test
    public void testGetWeight() {
        when(ticket.getWeight()).thenReturn(1.0);
        assertEquals(1.0, ticketWrapper.getWeight(), 0.01);
    }

    @Test
    public void testGetKey() {
        when(ticket.getKey()).thenReturn("KEY-123");
        assertEquals("KEY-123", ticketWrapper.getKey());
    }


    @Test
    public void testCompareProgress() throws IOException {
        ITicket secondTicket = mock(ITicket.class);
        when(ticket.getProgress()).thenReturn(50.0);
        when(secondTicket.getProgress()).thenReturn(75.0);
        assertEquals(Integer.valueOf(1), ITicket.ITicketProgress.compare(ticket, secondTicket));
    }
}