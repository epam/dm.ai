// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import com.github.istin.dmtools.common.tracker.TrackerClient;

public class TicketLinksCellTest {

    @Test
    public void testTicketsWithValidKeys() {
        TrackerClient trackerClient = Mockito.mock(TrackerClient.class);
        when(trackerClient.getTicketBrowseUrl("TICKET-1")).thenReturn("http://example.com/TICKET-1");
        when(trackerClient.getTicketBrowseUrl("TICKET-2")).thenReturn("http://example.com/TICKET-2");

        String result = TicketLinksCell.tickets(trackerClient, "TICKET-1", "TICKET-2");
        String expected = "<a href=\"http://example.com/TICKET-1\">TICKET-1</a><br/><a href=\"http://example.com/TICKET-2\">TICKET-2</a>";

        assertEquals(expected, result);
    }

    @Test
    public void testTicketsWithNoKeys() {
        TrackerClient trackerClient = Mockito.mock(TrackerClient.class);

        String result = TicketLinksCell.tickets(trackerClient);
        String expected = "0";

        assertEquals(expected, result);
    }

    @Test
    public void testTicketsWithNullKeys() {
        TrackerClient trackerClient = Mockito.mock(TrackerClient.class);

        String result = TicketLinksCell.tickets(trackerClient, (String[]) null);
        String expected = "0";

        assertEquals(expected, result);
    }
}