// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.tracker.model.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class IssueLinkTest {

    private IssueLink issueLink;
    private Ticket mockTicket;

    @Before
    public void setUp() {
        mockTicket = mock(Ticket.class);
        issueLink = Mockito.spy(new IssueLink());
    }

    @Test
    public void testGetId() {
        doReturn("123").when(issueLink).getString("id");
        assertEquals("123", issueLink.getId());
    }

    @Test
    public void testGetInwardIssue() {
        doReturn(mockTicket).when(issueLink).getModel(Ticket.class, "inwardIssue");
        assertEquals(mockTicket, issueLink.getInwardIssue());
    }

    @Test
    public void testGetOutwardIssue() {
        doReturn(mockTicket).when(issueLink).getModel(Ticket.class, "outwardIssue");
        assertEquals(mockTicket, issueLink.getOutwardIssue());
    }

    @Test
    public void testIsTest() {
        doReturn(true).when(issueLink).inwardRelationshipIn(Relationship.TESTED_BY);
        doReturn(mockTicket).when(issueLink).getInwardIssue();
        Fields mockFields = mock(Fields.class);
        when(mockTicket.getFields()).thenReturn(mockFields);
        IssueType mockIssueType = mock(IssueType.class);
        when(mockFields.getIssueType()).thenReturn(mockIssueType);
        when(mockIssueType.isTest()).thenReturn(true);
        assertTrue(issueLink.isTest());
    }

    @Test
    public void testGetStatus() throws IOException {
        Status mockStatus = mock(Status.class);
        doReturn(mockStatus).when(issueLink).getStatusModel();
        when(mockStatus.getName()).thenReturn("Open");
        assertEquals("Open", issueLink.getStatus());
    }

    @Test
    public void testGetPriority() throws IOException {
        doReturn(mockTicket).when(issueLink).getRelatedTicket();
        Fields mockFields = mock(Fields.class);
        when(mockTicket.getFields()).thenReturn(mockFields);
        Priority mockPriority = mock(Priority.class);
        when(mockFields.getPriority()).thenReturn(mockPriority);
        when(mockPriority.getName()).thenReturn("High");
        assertEquals("High", issueLink.getPriority());
    }

    @Test
    public void testGetTicketKey() {
        doReturn(mockTicket).when(issueLink).getInwardIssue();
        when(mockTicket.getKey()).thenReturn("TICKET-123");
        assertEquals("TICKET-123", issueLink.getTicketKey());
    }

    @Test
    public void testGetTicketTitle() throws IOException {
        doReturn(mockTicket).when(issueLink).getInwardIssue();
        Fields mockFields = mock(Fields.class);
        when(mockTicket.getFields()).thenReturn(mockFields);
        when(mockFields.getSummary()).thenReturn("Summary");
        assertEquals("Summary", issueLink.getTicketTitle());
    }

    @Test
    public void testGetHtmlTicketLink() {
        doReturn("TICKET-123").when(issueLink).getTicketKey();
        doReturn("http://example.com/").when(issueLink).getTicketLink();
        assertEquals("<a href=\"http://example.com/\">TICKET-123</a>", issueLink.getHtmlTicketLink());
    }

}