// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.metrics.Metric;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCasesReportTest {

    private TestCasesReport testCasesReport;
    private TestCasesReportParams mockParams;

    @Before
    public void setUp() {
        testCasesReport = new TestCasesReport();
        mockParams = mock(TestCasesReportParams.class);
    }

    @Test
    public void testGenerateListOfMetrics() throws IOException {
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");
        when(mockParams.getTestCasesProjectCode()).thenReturn("projectCode");

        List<Metric> metrics = testCasesReport.generateListOfMetrics(mockParams);

        assertEquals(6, metrics.size());
    }

    @Test
    public void testSummaryChanged() {
        List<Metric> metrics = new ArrayList<>();
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");

        testCasesReport.summaryChanged(mockParams, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Ticket Summary Changed", metrics.get(0).getName());
    }

    @Test
    public void testDescriptionChanged() {
        List<Metric> metrics = new ArrayList<>();
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");

        testCasesReport.descriptionChanged(mockParams, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Ticket Description Changed", metrics.get(0).getName());
    }

    @Test
    public void testPriorityChanged() {
        List<Metric> metrics = new ArrayList<>();
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");

        testCasesReport.priorityChanged(mockParams, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Ticket Priority Changed", metrics.get(0).getName());
    }

    @Test
    public void testTicketLinksChanged() {
        List<Metric> metrics = new ArrayList<>();
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");

        testCasesReport.ticketLinksChanged(mockParams, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Ticket Links Changed", metrics.get(0).getName());
    }

    @Test
    public void testNumberOfAttachments() {
        List<Metric> metrics = new ArrayList<>();
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");

        testCasesReport.numberOfAttachments(mockParams, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Number of Attachments", metrics.get(0).getName());
    }

    @Test
    public void testCreatedTests() {
        List<Metric> metrics = new ArrayList<>();
        when(mockParams.isWeight()).thenReturn(true);
        when(mockParams.getEmployees()).thenReturn("employees");
        when(mockParams.getTestCasesProjectCode()).thenReturn("projectCode");

        testCasesReport.createdTests(mockParams, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Created Tests", metrics.get(0).getName());
    }
}