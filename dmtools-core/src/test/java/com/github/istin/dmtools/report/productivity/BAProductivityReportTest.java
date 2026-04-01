// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.metrics.Metric;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class BAProductivityReportTest {

    private BAProductivityReport report;
    private BAProductivityReportParams params;

    @Before
    public void setUp() {
        report = new BAProductivityReport();
        params = mock(BAProductivityReportParams.class);
    }

    @Test
    public void testGenerateListOfMetrics() throws IOException {
        // Mock dependencies
        List<Metric> metrics = report.generateListOfMetrics(params);

        // Verify the list is not null
        assertNotNull(metrics);
    }

}