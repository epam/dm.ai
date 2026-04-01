// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.model.ITicket;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TicketTest {

    private Ticket ticket;
    private ITicket mockITicket;

    @Before
    public void setUp() {
        ticket = new Ticket();
        mockITicket = mock(ITicket.class);
    }

    @Test
    public void testCreate() throws IOException {
        when(mockITicket.getTicketTitle()).thenReturn("Sample Title");
        when(mockITicket.getTicketKey()).thenReturn("TICKET-123");
        when(mockITicket.getTicketLink()).thenReturn("http://example.com");
        when(mockITicket.getStatus()).thenReturn("Open");
        when(mockITicket.getTicketDependenciesDescription()).thenReturn("Dependency Description");

        Ticket createdTicket = Ticket.create(mockITicket);

        assertEquals("Sample Title", createdTicket.getName());
        assertEquals("TICKET-123", createdTicket.getKey());
        assertEquals("http://example.com", createdTicket.getUrl());
        assertEquals("Open", createdTicket.getStatus());
        assertEquals("Dependency Description", createdTicket.getDependenciesDescription());
    }

    @Test
    public void testGettersAndSetters() {
        ticket.setDuration(5);
        assertEquals(5, ticket.getDuration());

        ticket.setPercent(50);
        assertEquals(50, ticket.getPercent());

        ticket.setStartSprint(1);
        assertEquals(1, ticket.getStartSprint());

        ticket.setStatusStyle("style");
        assertEquals("style", ticket.getStatusStyle());

        ticket.setStatus("In Progress");
        assertEquals("In Progress", ticket.getStatus());

        ticket.setUrl("http://example.com");
        assertEquals("http://example.com", ticket.getUrl());

        ticket.setKey("KEY-123");
        assertEquals("KEY-123", ticket.getKey());

        ticket.setName("Ticket Name");
        assertEquals("Ticket Name", ticket.getName());

        ticket.setProgress("50%");
        assertEquals("50%", ticket.getProgress());

        List<Dependency> dependencies = new ArrayList<>();
        ticket.setDependencies(dependencies);
        assertEquals(dependencies, ticket.getDependencies());

        List<String> labels = new ArrayList<>();
        ticket.setLabels(labels);
        assertEquals(labels, ticket.getLabels());

        ticket.setIsHasCapacity(false);
        assertFalse(ticket.getIsHasCapacity());

        ticket.setRelease(2);
        assertEquals(2, ticket.getRelease());

        ticket.setDependenciesDescription("Description");
        assertEquals("Description", ticket.getDependenciesDescription());
    }

    @Test
    public void testAddLabel() {
        ticket.addLabel("New Label");
        assertTrue(ticket.getLabels().contains("New Label"));
    }

    @Test
    public void testGetOpenedDependencies() {
        Dependency dependency1 = mock(Dependency.class);
        when(dependency1.getStatus()).thenReturn("Open");

        Dependency dependency2 = mock(Dependency.class);
        when(dependency2.getStatus()).thenReturn("Done");

        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(dependency1);
        dependencies.add(dependency2);

        ticket.setDependencies(dependencies);

        List<Dependency> openedDependencies = ticket.getOpenedDependencies();
        assertEquals(1, openedDependencies.size());
        assertEquals(dependency1, openedDependencies.get(0));
    }

    @Test
    public void testCompareTo() {
        Ticket otherTicket = new Ticket();
        otherTicket.setRelease(3);

        ticket.setRelease(2);

        assertTrue(ticket.compareTo(otherTicket) < 0);
    }
}