// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.scopestatus;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.report.freemarker.*;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ScopeStatusReport {

    private List<? extends ITicket> ticketList;
    private Integer defaultSPsValue;

    public void setTicketProgressCalc(ITicket.ITicketProgress ticketProgressCalc) {
        this.ticketProgressCalc = ticketProgressCalc;
    }

    private ITicket.ITicketProgress ticketProgressCalc = new ITicket.ITicketProgress.Impl();

    public ScopeStatusReport(List<? extends ITicket> ticketList) {
        this.ticketList = ticketList;
    }

    public GenericReport generateReport() throws IOException {
        GenericReport report = new GenericReport();
        List<GenericRow> rows = new ArrayList<>();
        rows.add(new TicketHeaderRow());
        for (ITicket ticket : ticketList) {
            rows.add(new TicketBaseRow(ticket, ticketProgressCalc, defaultSPsValue));
        }
        report.setRows(rows);
        return report;
    }

    public static void generateCharts(List<ScopePageSummary> results, String name, String chartColors, String jqlBasePath, BasicConfluence confluence, String ... namesOfMetrics) throws TemplateException, IOException {
        GenericReport chartsSummaryReport = new GenericReport();
        chartsSummaryReport.setName(name);
        chartsSummaryReport.setFilter("none");
        chartsSummaryReport.setChartColors(chartColors);
        chartsSummaryReport.setIsNotWiki(true);


        GenericRow headerRow = new GenericRow(true);
        headerRow.getCells().add(new GenericCell("&nbsp;"));
        chartsSummaryReport.getRows().add(headerRow);
        chartsSummaryReport.setChart(true);

        for (ScopePageSummary scopePageSummary : results) {
            headerRow.getCells().add(new GenericCell(scopePageSummary.getName()));
        }

        List<String> nameOfMetricsAsArray;
        List<GenericRow> listOfDataRows = new ArrayList<>();
        if (namesOfMetrics == null || namesOfMetrics.length == 0) {
            nameOfMetricsAsArray = new ArrayList<>();
            List<SummaryItem> summaryItems = results.get(0).getSummaryItems();
            //adding rows labels
            for (SummaryItem summaryItem : summaryItems) {
                String firstEntryKey = summaryItem.getLabel();
                nameOfMetricsAsArray.add(firstEntryKey);
            }
        } else {
            nameOfMetricsAsArray = Arrays.asList(namesOfMetrics);
        }

        for (String nameOfMetric : nameOfMetricsAsArray) {
            GenericRow dataRow = new GenericRow();
            chartsSummaryReport.getRows().add(dataRow);
            dataRow.getCells().add(new GenericCell(nameOfMetric));
            listOfDataRows.add(dataRow);
        }

        for (ScopePageSummary scopePageSummary : results) {
            List<SummaryItem> summaryItems = scopePageSummary.getSummaryItems();
            for (int i = 0; i < nameOfMetricsAsArray.size(); i++) {
                String nameOfMetric = nameOfMetricsAsArray.get(i);
                for (SummaryItem summaryItem : summaryItems) {
                    if (summaryItem.getLabel().equals(nameOfMetric)) {
                        Object value = summaryItem.getData();
                        GenericRow dataRow = listOfDataRows.get(i);
                        if (value instanceof Integer || value instanceof String || value instanceof Long || value instanceof Double) {
                            dataRow.getCells().add(new GenericCell(String.valueOf(value)));
                        } else {
                            dataRow.getCells().add(new JQLNumberCell(jqlBasePath, (Collection<? extends Key>) value).setWeightPrint(false));
                        }
                        break;
                    }
                }
            }

        }

        confluence.publishPageToDefaultSpace("Summary By Iterations", null, chartsSummaryReport);
    }

    public void setDefaultSPsValue(Integer defaultSPsValue) {
        this.defaultSPsValue = defaultSPsValue;
    }
}
