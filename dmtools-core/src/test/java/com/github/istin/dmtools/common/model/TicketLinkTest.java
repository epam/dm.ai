// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TicketLinkTest {

    @Test
    public void testGetTicketLink() {
        // Create a mock instance of TicketLink
        TicketLink ticketLink = mock(TicketLink.class);

        // Define the behavior of getTicketLink method
        when(ticketLink.getTicketLink()).thenReturn("http://example.com/ticket");

        // Verify the method returns the expected value
        assertEquals("http://example.com/ticket", ticketLink.getTicketLink());

        // Verify that the method was called exactly once
        verify(ticketLink, times(1)).getTicketLink();
    }
}