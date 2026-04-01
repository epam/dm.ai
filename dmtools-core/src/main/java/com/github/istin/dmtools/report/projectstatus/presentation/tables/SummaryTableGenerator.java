// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.util.*;

public class SummaryTableGenerator implements TableGenerator {
    private final TableGenerator baseTableGenerator;
    private final TicketStatisticsCalculator statisticsCalculator;
    private final TicketSorter ticketSorter;

    public SummaryTableGenerator(TableGenerator baseTableGenerator, TicketStatisticsCalculator statisticsCalculator, TicketSorter ticketSorter) {
        this.baseTableGenerator = baseTableGenerator;
        this.statisticsCalculator = statisticsCalculator;
        this.ticketSorter = ticketSorter;
    }

    @Override
    public String generateTable(TableData tableData) {
        return baseTableGenerator.generateTable(tableData);
    }

    @Override
    public String generateTable(List<ITicket> tickets) {
        return generateSummaryTable(tickets);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        // Default implementation just calls the main method
        return generateTable(tickets);
    }

    public String generateSummaryTable(List<ITicket> tickets) {
        // Existing implementation...
        // Get statistics by type and priority
        Map<String, Map<String, Integer>> typePriorityStats = statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets);

        // Extract all unique priorities and types
        Set<String> allPriorities = new HashSet<>();
        Map<String, Integer> typeTotals = new HashMap<>();

        for (Map.Entry<String, Map<String, Integer>> typeEntry : typePriorityStats.entrySet()) {
            String type = typeEntry.getKey();
            Map<String, Integer> priorityCounts = typeEntry.getValue();

            // Calculate total for this type
            int typeTotal = priorityCounts.values().stream().mapToInt(Integer::intValue).sum();
            typeTotals.put(type, typeTotal);

            // Collect all priorities
            allPriorities.addAll(priorityCounts.keySet());
        }

        // Sort priorities and issue types
        List<String> sortedPriorities = ticketSorter.sortPriorities(allPriorities);
        List<String> sortedTypes = ticketSorter.sortIssueTypes(typeTotals.keySet());

        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Issue Type");
        headers.addAll(sortedPriorities);
        headers.add("Total");

        // Create table data
        TableData tableData = new TableData("Summary by Issue Type and Priority", headers);

        // Add data rows
        for (String type : sortedTypes) {
            List<String> row = new ArrayList<>();
            row.add(type);
            Map<String, Integer> priorityCounts = typePriorityStats.getOrDefault(type, Collections.emptyMap());

            for (String priority : sortedPriorities) {
                int count = priorityCounts.getOrDefault(priority, 0);
                row.add(String.valueOf(count));
            }

            row.add(String.valueOf(typeTotals.get(type)));
            tableData.addRow(row);
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");
        int grandTotal = 0;
        for (String priority : sortedPriorities) {
            int priorityTotal = 0;
            for (String type : sortedTypes) {
                Map<String, Integer> priorityCounts = typePriorityStats.getOrDefault(type, Collections.emptyMap());
                priorityTotal += priorityCounts.getOrDefault(priority, 0);
            }
            totalsRow.add(String.valueOf(priorityTotal));
            grandTotal += priorityTotal;
        }
        totalsRow.add(String.valueOf(grandTotal));
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }
}