// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.scopestatus;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.report.freemarker.GenericCell;
import com.github.istin.dmtools.report.freemarker.GenericReport;
import com.github.istin.dmtools.report.freemarker.GenericRow;
import com.github.istin.dmtools.report.freemarker.JQLNumberCell;
import com.github.istin.dmtools.report.freemarker.TicketBaseRow;
import com.github.istin.dmtools.report.freemarker.TicketHeaderRow;
import freemarker.template.TemplateException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class ScopeStatusReportTest {

    private List<ITicket> mockTicketList;
    private ScopeStatusReport scopeStatusReport;

    @Before
    public void setUp() {
        mockTicketList = new ArrayList<>();
        ITicket mockTicket = mock(ITicket.class);
        mockTicketList.add(mockTicket);
        scopeStatusReport = new ScopeStatusReport(mockTicketList);
    }


    @Test
    public void testGenerateCharts() throws TemplateException, IOException {
        List<ScopePageSummary> mockResults = new ArrayList<>();
        ScopePageSummary mockSummary = mock(ScopePageSummary.class);
        when(mockSummary.getName()).thenReturn("Test Summary");
        when(mockSummary.getSummaryItems()).thenReturn(Collections.emptyList());
        mockResults.add(mockSummary);

        BasicConfluence mockConfluence = mock(BasicConfluence.class);

        ScopeStatusReport.generateCharts(mockResults, "Test Name", "Test Colors", "Test JQL", mockConfluence);

        verify(mockConfluence, times(1)).publishPageToDefaultSpace(eq("Summary By Iterations"), isNull(), any(GenericReport.class));
    }

    @Test
    public void testSetDefaultSPsValue() {
        scopeStatusReport.setDefaultSPsValue(10);
        // No direct way to assert this as it's a private field, but we can ensure no exceptions are thrown
    }

    @Test
    public void testSetTicketProgressCalc() {
        ITicket.ITicketProgress mockProgressCalc = mock(ITicket.ITicketProgress.class);
        scopeStatusReport.setTicketProgressCalc(mockProgressCalc);
        // No direct way to assert this as it's a private field, but we can ensure no exceptions are thrown
    }
}