// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.timeinstatus;

import com.github.istin.dmtools.report.freemarker.GenericRow;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class TimeInStatusReportTest {

    private TimeInStatusReport timeInStatusReport;

    @Before
    public void setUp() {
        timeInStatusReport = new TimeInStatusReport();
    }

    @Test
    public void testGetAndSetRows() {
        List<GenericRow> rows = new ArrayList<>();
        timeInStatusReport.setRows(rows);
        assertEquals(rows, timeInStatusReport.getRows());
    }

    @Test
    public void testGetAndSetTableHeight() {
        String tableHeight = "500px";
        timeInStatusReport.setTableHeight(tableHeight);
        assertEquals(tableHeight, timeInStatusReport.getTableHeight());
    }

    @Test
    public void testGetAndSetItems() {
        List<TimeInStatus.Item> items = new ArrayList<>();
        timeInStatusReport.setItems(items);
        assertEquals(items, timeInStatusReport.getItems());
    }

    @Test
    public void testGetItemsAsString() {
        List<TimeInStatus.Item> items = new ArrayList<>();
        items.add(mock(TimeInStatus.Item.class));
        timeInStatusReport.setItems(items);
        assertEquals(items.toString(), timeInStatusReport.getItemsAsString());
    }

    @Test
    public void testGetAndSetIsData() {
        timeInStatusReport.setIsData(false);
        assertTrue(!timeInStatusReport.getIsData());
    }
}