// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class RoadmapTest {

    private Roadmap roadmap;
    private List<Row> mockRows;

    @Before
    public void setUp() {
        mockRows = mock(List.class);
        roadmap = new Roadmap(mockRows);
    }

    @Test
    public void testGetRows() {
        List<Row> expectedRows = new ArrayList<>();
        when(mockRows.size()).thenReturn(1);
        when(mockRows.get(0)).thenReturn(new Row());

        roadmap.setRows(expectedRows);
        List<Row> actualRows = roadmap.getRows();

        assertEquals(expectedRows, actualRows);
    }

    @Test
    public void testSetRows() {
        List<Row> newRows = new ArrayList<>();
        roadmap.setRows(newRows);

        assertEquals(newRows, roadmap.getRows());
    }
}