// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class IHistoryTest {

    @Test
    public void testGetHistoryItems() {
        IHistory history = mock(IHistory.class);
        List<IHistoryItem> mockHistoryItems = new ArrayList<>();
        mockHistoryItems.add(mock(IHistoryItem.class));

        doReturn(mockHistoryItems).when(history).getHistoryItems();

        List<? extends IHistoryItem> historyItems = history.getHistoryItems();
        assertNotNull(historyItems);
        assertEquals(mockHistoryItems, historyItems);
    }

    @Test
    public void testGetAuthor() {
        IHistory history = mock(IHistory.class);
        IUser mockAuthor = mock(IUser.class);
        when(history.getAuthor()).thenReturn(mockAuthor);

        IUser author = history.getAuthor();
        assertNotNull(author);
        assertEquals(mockAuthor, author);
    }

    @Test
    public void testGetCreated() {
        IHistory history = mock(IHistory.class);
        Calendar mockCreated = mock(Calendar.class);
        when(history.getCreated()).thenReturn(mockCreated);

        Calendar created = history.getCreated();
        assertNotNull(created);
        assertEquals(mockCreated, created);
    }
}