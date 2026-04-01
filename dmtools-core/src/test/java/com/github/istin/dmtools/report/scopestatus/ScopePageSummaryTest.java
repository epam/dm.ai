// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.scopestatus;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScopePageSummaryTest {

    private ScopePageSummary scopePageSummary;
    private List<SummaryItem> summaryItems;

    @Before
    public void setUp() {
        summaryItems = new ArrayList<>();
        summaryItems.add(new SummaryItem("Metric1", "10"));
        summaryItems.add(new SummaryItem("Metric2", "20"));
        scopePageSummary = new ScopePageSummary("TestSummary", summaryItems);
    }

    @Test
    public void testGetName() {
        assertEquals("TestSummary", scopePageSummary.getName());
    }

    @Test
    public void testGetSummaryItems() {
        assertEquals(summaryItems, scopePageSummary.getSummaryItems());
    }

}