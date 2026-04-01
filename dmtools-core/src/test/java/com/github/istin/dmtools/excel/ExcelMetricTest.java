// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.excel;

import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class ExcelMetricTest {

    @Test
    public void testExcelMetricConstructor() {
        // Arrange
        String metricName = "Test Metric";
        IEmployees employees = mock(IEmployees.class);
        String fileName = "testFile.xlsx";
        String whoColumn = "A";
        String whenColumn = "B";
        String weightColumn = "C";
        double weightMultiplier = 1.0;

        // Act
        ExcelMetric excelMetric = new ExcelMetric(metricName, employees, fileName, whoColumn, whenColumn, weightColumn, weightMultiplier);

        // Assert
        assertNotNull(excelMetric);
    }
}