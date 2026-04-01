// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class IStatsTest {

    @Test
    public void testGetTotal() {
        IStats stats = mock(IStats.class);
        when(stats.getTotal()).thenReturn(100);

        int total = stats.getTotal();

        assertEquals(100, total);
        verify(stats).getTotal();
    }

    @Test
    public void testGetAdditions() {
        IStats stats = mock(IStats.class);
        when(stats.getAdditions()).thenReturn(50);

        int additions = stats.getAdditions();

        assertEquals(50, additions);
        verify(stats).getAdditions();
    }

    @Test
    public void testGetDeletions() {
        IStats stats = mock(IStats.class);
        when(stats.getDeletions()).thenReturn(30);

        int deletions = stats.getDeletions();

        assertEquals(30, deletions);
        verify(stats).getDeletions();
    }
}