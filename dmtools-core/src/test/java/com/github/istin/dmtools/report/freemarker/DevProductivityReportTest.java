// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.report.DevChart;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class DevProductivityReportTest {

    private DevProductivityReport report;

    @Before
    public void setUp() {
        report = new DevProductivityReport();
    }

    @Test
    public void testGetAndSetListDevCharts() {
        List<DevChart> devCharts = new ArrayList<>();
        report.setListDevCharts(devCharts);
        assertSame(devCharts, report.getListDevCharts());
    }

    @Test
    public void testGetAndSetHeaders() {
        List<String> headers = new ArrayList<>();
        report.setHeaders(headers);
        assertSame(headers, report.getHeaders());
    }

    @Test
    public void testSetAndGetTicketCounter() {
        int ticketCounter = 5;
        report.setTicketsCount(ticketCounter);
        assertEquals(ticketCounter, report.getTicketCounter());
    }

    @Test
    public void testShiftTimelineStarts() {
        // Placeholder for shiftTimelineStarts method
        // TODO: Implement test for shiftTimelineStarts method
    }
}