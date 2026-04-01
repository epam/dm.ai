// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class GenericRowTest {

    @Test
    public void testDefaultConstructor() {
        GenericRow row = new GenericRow();
        assertFalse(row.getIsHeader());
        assertNotNull(row.getCells());
        assertTrue(row.getCells().isEmpty());
    }

    @Test
    public void testConstructorWithHeader() {
        GenericRow row = new GenericRow(true);
        assertTrue(row.getIsHeader());
        assertNotNull(row.getCells());
        assertTrue(row.getCells().isEmpty());
    }

    @Test
    public void testConstructorWithHeaderAndCells() {
        GenericRow row = new GenericRow(true, "Test", 2);
        assertTrue(row.getIsHeader());
        assertNotNull(row.getCells());
        assertEquals(1, row.getCells().size());
        assertEquals("Test", row.getCells().get(0).getText());
        assertEquals(2, row.getCells().get(0).getDuration());
    }

    @Test
    public void testSetCells() {
        GenericRow row = new GenericRow();
        List<GenericCell> cells = new ArrayList<>();
        cells.add(new GenericCell("Cell1"));
        row.setCells(cells);
        assertEquals(cells, row.getCells());
    }

    @Test
    public void testSetHeader() {
        GenericRow row = new GenericRow();
        row.setHeader(true);
        assertTrue(row.getIsHeader());
    }
}