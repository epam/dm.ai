// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.report.freemarker.DevProductivityReport;
import com.github.istin.dmtools.report.freemarker.GenericCell;
import com.github.istin.dmtools.report.freemarker.GenericRow;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class ProductivityToolsTest {

    private TrackerClient trackerClient;
    private IReleaseGenerator releaseGenerator;
    private List<Metric> listOfCustomMetrics;
    private Employees employees;
    private String[] ignorePrefixes;

    @Before
    public void setUp() {
        trackerClient = mock(TrackerClient.class);
        releaseGenerator = mock(IReleaseGenerator.class);
        listOfCustomMetrics = new ArrayList<>();
        employees = mock(Employees.class);
        ignorePrefixes = new String[]{"ignore"};
    }

    @Test
    public void testGenerate() throws Exception {
        when(releaseGenerator.getStartDate()).thenReturn(Calendar.getInstance());
        when(releaseGenerator.generate()).thenReturn(new ArrayList<>());

        File result = ProductivityTools.generate(trackerClient, releaseGenerator, "team", "formula", "jql", listOfCustomMetrics, Release.Style.BY_SPRINTS, employees, ignorePrefixes);

        assertNotNull(result);
    }

    @Test
    public void testBuildReport() throws Exception {
        when(releaseGenerator.getStartDate()).thenReturn(Calendar.getInstance());
        when(releaseGenerator.generate()).thenReturn(new ArrayList<>());

        DevProductivityReport report = ProductivityTools.buildReport(trackerClient, releaseGenerator, "team", "formula", "jql", listOfCustomMetrics, Release.Style.BY_SPRINTS, employees, ignorePrefixes);

        assertNotNull(report);
    }

    @Test
    public void testCheckEmployees() {
        List<KeyTime> productivityItem = new ArrayList<>();
        KeyTime keyTime = mock(KeyTime.class);
        when(keyTime.getWho()).thenReturn("John Doe");
        productivityItem.add(keyTime);

        ProductivityTools.checkEmployees(employees, productivityItem);

        verify(employees, times(1)).transformName("John Doe");
    }

    @Test
    public void testGetCurrentTimeForMeasurements() {
        long currentTime = ProductivityTools.getCurrentTimeForMeasurements();
        assertNotNull(currentTime);
    }

    @Test
    public void testMeasureTime() {
        long startTime = System.currentTimeMillis();
        long measuredTime = ProductivityTools.measureTime("testAction", startTime);
        assertNotNull(measuredTime);
    }

    @Test
    public void testFindRow() {
        List<GenericRow> rows = new ArrayList<>();
        GenericRow row = mock(GenericRow.class);
        GenericCell cell = mock(GenericCell.class);
        when(cell.getText()).thenReturn("devName");
        when(row.getCells()).thenReturn(Collections.singletonList(cell));
        rows.add(row);

        GenericRow foundRow = ProductivityTools.findRow(rows, "devName");

        assertNotNull(foundRow);
    }
}