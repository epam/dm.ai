// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.model.ITicket;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class TicketLinkCellTest {

    @Test
    public void testConstructorWithTicketKeyAndLink() {
        String ticketKey = "TICKET-123";
        String ticketLink = "http://example.com/ticket/123";
        TicketLinkCell cell = new TicketLinkCell(ticketKey, ticketLink);
        assertEquals("<a href=\"http://example.com/ticket/123\">TICKET-123</a>", cell.getText());
    }

    @Test
    public void testConstructorWithTicketsCollection() throws IOException {
        ITicket ticket1 = Mockito.mock(ITicket.class);
        ITicket ticket2 = Mockito.mock(ITicket.class);

        Mockito.when(ticket1.getTicketKey()).thenReturn("TICKET-123");
        Mockito.when(ticket1.getTicketLink()).thenReturn("http://example.com/ticket/123");
        Mockito.when(ticket1.getStatus()).thenReturn("Open");

        Mockito.when(ticket2.getTicketKey()).thenReturn("TICKET-456");
        Mockito.when(ticket2.getTicketLink()).thenReturn("http://example.com/ticket/456");
        Mockito.when(ticket2.getStatus()).thenReturn("Closed");

        Collection<ITicket> tickets = Arrays.asList(ticket1, ticket2);
        Function<ITicket, String> function = ticket -> {
            try {
                return " - " + ticket.getStatus();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        TicketLinkCell cell = new TicketLinkCell(tickets, function);
        String expected = "<a href=\"http://example.com/ticket/123\">TICKET-123</a>[Open] - Open," +
                          "<a href=\"http://example.com/ticket/456\">TICKET-456</a>[Closed] - Closed";
        assertEquals(expected, cell.getText());
    }
}