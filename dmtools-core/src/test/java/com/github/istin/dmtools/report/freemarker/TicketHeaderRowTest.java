// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TicketHeaderRowTest {

    @Test
    public void testTicketHeaderRowInitialization() {
        TicketHeaderRow ticketHeaderRow = new TicketHeaderRow();
        
        // Verify that the cells are initialized correctly
        assertEquals(7, ticketHeaderRow.getCells().size());
        assertEquals("Key", ticketHeaderRow.getCells().get(0).getText());
        assertEquals("Priority", ticketHeaderRow.getCells().get(1).getText());
        assertEquals("Type", ticketHeaderRow.getCells().get(2).getText());
        assertEquals("Summary", ticketHeaderRow.getCells().get(3).getText());
        assertEquals("SPs", ticketHeaderRow.getCells().get(4).getText());
        assertEquals("Progress", ticketHeaderRow.getCells().get(5).getText());
        assertEquals("Status", ticketHeaderRow.getCells().get(6).getText());
    }

    @Test
    public void testIsHeader() {
        TicketHeaderRow ticketHeaderRow = new TicketHeaderRow();
        
        // Verify that the row is marked as a header
        assertTrue(ticketHeaderRow.getIsHeader());
    }
}