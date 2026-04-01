// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.report.DevChart.ReportIterationData;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DevChartTest {

    private DevChart devChart;
    private ReportIterationData reportIterationData;

    @Before
    public void setUp() {
        devChart = new DevChart("Developer Name");
        reportIterationData = new ReportIterationData(1, "Iteration 1", 0, "2 + 2");
    }

    @Test
    public void testGetHeaders() {
        List<String> headers = new ArrayList<>();
        headers.add("Header1");
        headers.add("Header2");
        devChart.setHeaders(headers);
        assertEquals(headers, devChart.getHeaders());
    }

    @Test
    public void testGetId() {
        assertEquals("Developer_Name", devChart.getId());
    }


    @Test
    public void testReportIterationDataCustomMetrics() {
        List<Metric> customMetricsHeaders = new ArrayList<>();
        Metric metric = mock(Metric.class);
        when(metric.getName()).thenReturn("Metric1");
        customMetricsHeaders.add(metric);

        reportIterationData.customMetricsHeaders = customMetricsHeaders;
        Map<Metric, List<KeyTime>> customMetrics = new HashMap<>();
        List<KeyTime> keyTimes = new ArrayList<>();
        KeyTime keyTime = mock(KeyTime.class);
        when(keyTime.getWeight()).thenReturn(1.0);
        keyTimes.add(keyTime);
        customMetrics.put(metric, keyTimes);
        reportIterationData.customMetrics = customMetrics;

        List<String> expectedValues = new ArrayList<>();
        expectedValues.add("1");
        assertEquals(expectedValues, reportIterationData.getCustomMetrics());
    }
}