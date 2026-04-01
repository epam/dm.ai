// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class GenericCellTest {

    @Test
    public void testDefaultConstructor() {
        GenericCell cell = new GenericCell();
        assertEquals(1, cell.getDuration());
        assertEquals("", cell.getText());
        assertEquals("", cell.getMeta());
    }

    @Test
    public void testConstructorWithText() {
        String text = "Sample Text";
        GenericCell cell = new GenericCell(text);
        assertEquals(text, cell.getText());
    }

    @Test
    public void testSetDuration() {
        GenericCell cell = new GenericCell();
        cell.setDuration(5);
        assertEquals(5, cell.getDuration());
    }

    @Test
    public void testSetText() {
        GenericCell cell = new GenericCell();
        String text = "New Text";
        cell.setText(text);
        assertEquals(text, cell.getText());
    }

    @Test
    public void testSetMeta() {
        GenericCell cell = new GenericCell();
        String meta = "Sample Meta";
        cell.setMeta(meta);
        assertEquals(meta, cell.getMeta());
    }

    @Test
    public void testRoundOff() {
        assertEquals("5", GenericCell.roundOff(5.4));
        assertEquals("6", GenericCell.roundOff(5.5));
    }
}