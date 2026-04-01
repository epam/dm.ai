// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

public class IDiffStatsTest {

    @Test
    public void testEmptyGetStats() {
        IDiffStats.Empty emptyDiffStats = new IDiffStats.Empty();
        IStats stats = emptyDiffStats.getStats();
        
        assertEquals(0, stats.getTotal());
        assertEquals(0, stats.getAdditions());
        assertEquals(0, stats.getDeletions());
    }

    @Test
    public void testEmptyGetChanges() {
        IDiffStats.Empty emptyDiffStats = new IDiffStats.Empty();
        List<IChange> changes = emptyDiffStats.getChanges();
        
        assertTrue(changes.isEmpty());
    }
}