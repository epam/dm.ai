// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class IHistoryItemTest {

    @Test
    public void testNewTicketCreation() {
        IHistoryItem.NewTicketCreation newTicketCreation = new IHistoryItem.NewTicketCreation();
        assertEquals("NewTicketCreation", newTicketCreation.getField());
        assertEquals("", newTicketCreation.getFromAsString());
        assertEquals("", newTicketCreation.getToAsString());
    }

    @Test
    public void testImpl() {
        String field = "TestField";
        String from = "TestFrom";
        String to = "TestTo";
        
        IHistoryItem.Impl impl = new IHistoryItem.Impl(field, from, to);
        assertEquals(field, impl.getField());
        assertEquals(from, impl.getFromAsString());
        assertEquals(to, impl.getToAsString());
    }
}