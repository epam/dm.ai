// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.report.model.KeyTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class JQLNumberCellTest {

    private JQLNumberCell jqlNumberCell;
    private Key mockKey1;
    private Key mockKey2;

    @Before
    public void setUp() {
        mockKey1 = Mockito.mock(Key.class);
        mockKey2 = Mockito.mock(Key.class);

        when(mockKey1.getKey()).thenReturn("KEY-1");
        when(mockKey1.getWeight()).thenReturn(2.0);

        when(mockKey2.getKey()).thenReturn("KEY-2");
        when(mockKey2.getWeight()).thenReturn(3.0);

        List<Key> keys = new ArrayList<>(Arrays.asList(mockKey1, mockKey2));
        jqlNumberCell = new JQLNumberCell("http://example.com", keys);
    }

    @Test
    public void testGetItems() {
        List<Key> items = jqlNumberCell.getItems();
        assertEquals(2, items.size());
        assertTrue(items.contains(mockKey1));
        assertTrue(items.contains(mockKey2));
    }

    @Test
    public void testAddItems() {
        Key mockKey3 = Mockito.mock(Key.class);
        when(mockKey3.getKey()).thenReturn("KEY-3");
        when(mockKey3.getWeight()).thenReturn(1.0);

        jqlNumberCell.addItems(Arrays.asList(mockKey3));

        List<Key> items = jqlNumberCell.getItems();
        assertEquals(3, items.size());
        assertTrue(items.contains(mockKey3));
    }

    @Test
    public void testAdd() {
        Key mockKey4 = Mockito.mock(Key.class);
        when(mockKey4.getKey()).thenReturn("KEY-4");
        when(mockKey4.getWeight()).thenReturn(1.0);

        jqlNumberCell.add(mockKey4);

        List<Key> items = jqlNumberCell.getItems();
        assertEquals(3, items.size());
        assertTrue(items.contains(mockKey4));
    }

    @Test
    public void testGetText() {
        String text = jqlNumberCell.getText();
        assertTrue(text.contains("KEY-1"));
        assertTrue(text.contains("KEY-2"));
    }

    @Test
    public void testSetWeightPrint() {
        jqlNumberCell.setWeightPrint(false);
        assertEquals(false, jqlNumberCell.isWeightPrint());
    }

    @Test
    public void testSetCountPrint() {
        jqlNumberCell.setCountPrint(false);
        assertEquals(false, jqlNumberCell.isCountPrint());
    }
}