// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sm;

import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class ChangeTest {

    private Change change;
    private ITicket mockTicket;
    private IUser mockUser;
    private IHistoryItem mockHistoryItem;
    private Calendar mockCalendar;

    @Before
    public void setUp() {
        change = new Change();
        mockTicket = mock(ITicket.class);
        mockUser = mock(IUser.class);
        mockHistoryItem = mock(IHistoryItem.class);
        mockCalendar = mock(Calendar.class);
    }

    @Test
    public void testGetAndSetTicket() {
        change.setTicket(mockTicket);
        assertEquals(mockTicket, change.getTicket());
    }

    @Test
    public void testGetAndSetWhen() {
        change.setWhen(mockCalendar);
        assertEquals(mockCalendar, change.getWhen());
    }

    @Test
    public void testGetAndSetWho() {
        change.setWho(mockUser);
        assertEquals(mockUser, change.getWho());
    }

    @Test
    public void testGetAndSetHistoryItem() {
        change.setHistoryItem(mockHistoryItem);
        assertEquals(mockHistoryItem, change.getHistoryItem());
    }

    @Test
    public void testToString() {
        change.setTicket(mockTicket);
        change.setWhen(mockCalendar);
        change.setWho(mockUser);
        change.setHistoryItem(mockHistoryItem);

        String result = change.toString();
        assertNotNull(result);
        // Additional assertions can be added here to verify the string content if necessary
    }
}