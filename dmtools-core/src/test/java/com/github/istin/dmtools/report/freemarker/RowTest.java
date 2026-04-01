// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class RowTest {

    private Row row;
    private Ticket mockTicket;

    @Before
    public void setUp() {
        row = new Row();
        mockTicket = Mockito.mock(Ticket.class);
    }

    @Test
    public void testGetTicketsInitiallyNull() {
        assertEquals(null, row.getTickets());
    }

    @Test
    public void testSetTickets() {
        List<Ticket> tickets = new ArrayList<>();
        tickets.add(mockTicket);
        row.setTickets(tickets);
        assertEquals(tickets, row.getTickets());
    }

    @Test
    public void testAddTicket() {
        row.addTicket(mockTicket);
        assertNotNull(row.getTickets());
        assertEquals(1, row.getTickets().size());
        assertEquals(mockTicket, row.getTickets().get(0));
    }

    @Test
    public void testPlaceholder() {
        int duration = 5;
        row.placeholder(duration);
        assertNotNull(row.getTickets());
        assertEquals(1, row.getTickets().size());
        Ticket ticket = row.getTickets().get(0);
        assertEquals(duration, ticket.getDuration());
    }
}