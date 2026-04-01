// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation.area;

import com.github.istin.dmtools.common.model.TicketLink;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class ITicketDocumentationHistoryTrackerTest {

    private ITicketDocumentationHistoryTracker tracker;
    private TicketLink ticketLink;
    private String pageName;

    @Before
    public void setUp() {
        tracker = Mockito.mock(ITicketDocumentationHistoryTracker.class);
        ticketLink = new TicketLink() {
            @Override
            public String getTicketLink() {
                return "";
            }
        }; // Assuming a default constructor exists
        pageName = "SamplePage";
    }

    @Test
    public void testIsTicketWasAddedToPage() throws IOException {
        when(tracker.isTicketWasAddedToPage(ticketLink, pageName)).thenReturn(true);

        boolean result = tracker.isTicketWasAddedToPage(ticketLink, pageName);
        assertTrue(result);

        verify(tracker, times(1)).isTicketWasAddedToPage(ticketLink, pageName);
    }

    @Test
    public void testAddTicketToPageHistory() throws IOException {
        doNothing().when(tracker).addTicketToPageHistory(ticketLink, pageName);

        tracker.addTicketToPageHistory(ticketLink, pageName);

        verify(tracker, times(1)).addTicketToPageHistory(ticketLink, pageName);
    }
}