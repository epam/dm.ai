// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReportUtilsTest {


    @Test
    public void testGetReportFileName() {
        String reportFriendlyName = "Test Report";
        String expected = "Test_Report";

        String actual = ReportUtils.getReportFileName(reportFriendlyName);

        assertEquals(expected, actual);
    }
}