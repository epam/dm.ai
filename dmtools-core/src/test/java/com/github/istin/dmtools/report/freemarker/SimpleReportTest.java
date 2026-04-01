// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SimpleReportTest {

    @Test
    public void testGetName() {
        SimpleReport report = new SimpleReport();
        report.setName("Test Report");
        assertEquals("Test Report", report.getName());
    }

    @Test
    public void testSetName() {
        SimpleReport report = new SimpleReport();
        report.setName("Another Report");
        assertEquals("Another Report", report.getName());
    }

    @Test
    public void testGetFilterUrl() {
        SimpleReport report = new SimpleReport();
        report.setFilter("http://example.com");
        assertEquals("http://example.com", report.getFilterUrl());
    }

    @Test
    public void testSetFilter() {
        SimpleReport report = new SimpleReport();
        report.setFilter("http://test.com");
        assertEquals("http://test.com", report.getFilterUrl());
    }
}