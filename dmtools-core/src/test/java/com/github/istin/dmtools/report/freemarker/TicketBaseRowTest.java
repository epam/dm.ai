// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.broadcom.rally.model.RallyIssueType;
import com.github.istin.dmtools.common.model.ITicket;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TicketBaseRowTest {


    @Test
    public void testTicketBaseRowConstructorWithNegativeWeight() throws IOException {
        // Mocking ITicket and ITicket.ITicketProgress
        ITicket ticketMock = mock(ITicket.class);
        ITicket.ITicketProgress ticketProgressMock = mock(ITicket.ITicketProgress.class);

        // Setting up mock behavior
        when(ticketMock.getTicketKey()).thenReturn("TICKET-123");
        when(ticketMock.getTicketLink()).thenReturn("http://example.com/TICKET-123");
        when(ticketMock.getPriority()).thenReturn("High");
        when(ticketMock.getIssueType()).thenReturn(RallyIssueType.HIERARCHICAL_REQUIREMENT);
        when(ticketMock.getTicketTitle()).thenReturn("Sample Ticket Title");
        when(ticketMock.getWeight()).thenReturn(-1.0);
        when(ticketMock.getStatus()).thenReturn("Open");
        when(ticketProgressMock.calc(ticketMock)).thenReturn(50d);

        // Creating an instance of TicketBaseRow with negative weight
        TicketBaseRow ticketBaseRow = new TicketBaseRow(ticketMock, ticketProgressMock, null);

        // Assertions to verify the behavior
        assertEquals("&nbsp;", ((GenericCell) ticketBaseRow.getCells().get(4)).getText());
    }
}