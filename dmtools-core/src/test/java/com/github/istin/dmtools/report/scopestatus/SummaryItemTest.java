// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.scopestatus;

import com.github.istin.dmtools.common.model.Key;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collection;

public class SummaryItemTest {

    @Test
    public void testGetLabel() {
        SummaryItem item = new SummaryItem("Test Label", 123);
        assertEquals("Test Label", item.getLabel());
    }

    @Test
    public void testGetData() {
        SummaryItem item = new SummaryItem("Test Label", 123);
        assertEquals(123, item.getData());
    }

    @Test
    public void testGetNumericValueWithInteger() {
        SummaryItem item = new SummaryItem("Test Label", 123);
        assertEquals(123, item.getNumericValue());
    }

    @Test
    public void testGetNumericValueWithString() {
        SummaryItem item = new SummaryItem("Test Label", "123");
        assertEquals("123", item.getNumericValue());
    }

    @Test
    public void testGetNumericValueWithCollection() {
        Collection<Key> keys = Arrays.asList(mock(Key.class), mock(Key.class), mock(Key.class));
        SummaryItem item = new SummaryItem("Test Label", keys);
        assertEquals(3, item.getNumericValue());
    }

    @Test
    public void testGetNumericValueWithEmptyCollection() {
        Collection<Key> keys = Arrays.asList();
        SummaryItem item = new SummaryItem("Test Label", keys);
        assertEquals(0, item.getNumericValue());
    }
}