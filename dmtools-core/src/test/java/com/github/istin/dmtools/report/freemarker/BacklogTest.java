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

public class BacklogTest {

    private Backlog backlog;
    private List<Ticket> mockTickets;

    @Before
    public void setUp() {
        mockTickets = new ArrayList<>();
        mockTickets.add(Mockito.mock(Ticket.class));
        mockTickets.add(Mockito.mock(Ticket.class));
        backlog = new Backlog(mockTickets, 10);
    }

    @Test
    public void testGetTickets() {
        List<Ticket> tickets = backlog.getTickets();
        assertNotNull(tickets);
        assertEquals(2, tickets.size());
    }

    @Test
    public void testSetTickets() {
        List<Ticket> newTickets = new ArrayList<>();
        newTickets.add(Mockito.mock(Ticket.class));
        backlog.setTickets(newTickets);
        assertEquals(1, backlog.getTickets().size());
    }

    @Test
    public void testGetScopeSP() {
        assertEquals(10, backlog.getScopeSP());
    }

    @Test
    public void testSetScopeSP() {
        backlog.setScopeSP(20);
        assertEquals(20, backlog.getScopeSP());
    }

    @Test
    public void testConstructorWithParameters() {
        Backlog newBacklog = new Backlog(mockTickets, 15);
        assertEquals(mockTickets, newBacklog.getTickets());
        assertEquals(15, newBacklog.getScopeSP());
    }

    @Test
    public void testDefaultConstructor() {
        Backlog defaultBacklog = new Backlog();
        assertNotNull(defaultBacklog);
    }
}