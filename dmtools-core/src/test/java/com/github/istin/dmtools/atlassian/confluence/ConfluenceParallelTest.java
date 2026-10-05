// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ConfluenceParallelTest {

    @Test
    public void keepsInputOrderAndYieldsNullForFailedTasks() {
        List<Integer> result = ConfluenceParallel.map(Arrays.asList(1, 2, 3, 4, 5), 3, n -> {
            if (n == 3) {
                throw new IllegalStateException("boom");
            }
            Thread.sleep(50L * (6 - n));
            return n * 10;
        });

        assertEquals(Arrays.asList(10, 20, null, 40, 50), result);
    }

    @Test
    public void runsTasksConcurrentlyButNotAboveTheLimit() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        ConfluenceParallel.map(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), 3, n -> {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            Thread.sleep(80);
            running.decrementAndGet();
            return n;
        });

        assertTrue("expected concurrency, peak=" + peak.get(), peak.get() > 1);
        assertTrue("limit exceeded, peak=" + peak.get(), peak.get() <= 3);
    }

    @Test
    public void emptyInputReturnsEmptyList() {
        assertTrue(ConfluenceParallel.map(Collections.<String>emptyList(), 4, s -> s).isEmpty());
    }

    @Test
    public void parallelismReadsPropertyWithBounds() {
        String key = ConfluenceParallel.PARALLELISM_PROPERTY;
        try {
            System.setProperty(key, "7");
            assertEquals(7, ConfluenceParallel.parallelism());
            System.setProperty(key, "0");
            assertEquals(1, ConfluenceParallel.parallelism());
            System.setProperty(key, "500");
            assertEquals(16, ConfluenceParallel.parallelism());
            System.setProperty(key, "abc");
            assertEquals(4, ConfluenceParallel.parallelism());
        } finally {
            System.clearProperty(key);
        }
    }
}
