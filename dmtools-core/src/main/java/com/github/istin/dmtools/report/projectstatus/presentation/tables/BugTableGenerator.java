// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BugTableGenerator implements TableGenerator {

    private static final Logger logger = LogManager.getLogger(BugTableGenerator.class);
    private final TableGenerator baseTableGenerator;
    private final TicketStatisticsCalculator statisticsCalculator;
    private final TicketSorter ticketSorter;
    private final boolean countStoryPoints;

    public BugTableGenerator(TableGenerator baseTableGenerator, TicketStatisticsCalculator statisticsCalculator, TicketSorter ticketSorter, boolean countStoryPoints) {
        this.baseTableGenerator = baseTableGenerator;
        this.statisticsCalculator = statisticsCalculator;
        this.ticketSorter = ticketSorter;
        this.countStoryPoints = countStoryPoints;
    }

    @Override
    public String generateTable(TableData tableData) {
        return baseTableGenerator.generateTable(tableData);
    }

    @Override
    public String generateTable(List<ITicket> tickets) {
        // Default to bug overview for the simple method
        return generateBugOverviewTable(tickets);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        // Default implementation
        return generateTable(tickets);
    }

    public String generateBugOverviewTable(List<ITicket> bugs) {
        // Count bugs by priority
        Map<String, Integer> bugsByPriority = statisticsCalculator.countTicketsByPriority(bugs);

        // Sort priorities by predefined order
        List<String> sortedPriorities = ticketSorter.sortPriorities(bugsByPriority.keySet());

        // Create table headers
        String countHeader = this.countStoryPoints ? "Story Points" : "Count";
        List<String> headers = Arrays.asList("Priority", countHeader, "Percentage");
        TableData tableData = new TableData("Bug Overview by Priority", headers);

        // Add data rows
        int totalBugs = bugs.size();
        for (String priority : sortedPriorities) {
            int count = bugsByPriority.get(priority);
            double percentage = statisticsCalculator.calculatePercentage(count, totalBugs);

            List<String> row = Arrays.asList(
                    priority,
                    String.valueOf(this.countStoryPoints ? calculateTotalStoryPoints(filterBugsByPriority(bugs, priority)) : count),
                    String.format("%.1f%%", percentage)
            );
            tableData.addRow(row);
        }

        // Add totals row
        List<String> totalsRow = Arrays.asList(
                "**Total**",
                String.valueOf(this.countStoryPoints ? calculateTotalStoryPoints(bugs) : totalBugs),
                "100.0%"
        );
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    public String generateBugsTable(List<ITicket> bugs) {
        // Create table headers
        List<String> headers = Arrays.asList("Key", "Priority", "Closed Date", "Labels", "Summary");
        if (this.countStoryPoints) {
            headers = new ArrayList<>(headers); // Create mutable list
            headers.add(2, "Story Points"); // Insert SP after Priority
        }
        TableData tableData = new TableData("Bugs Details", headers);

        // Add bug rows
        for (ITicket ticket : bugs) {
            try {
                List<String> rowData = new ArrayList<>(Arrays.asList(
                    ticket.getKey(),
                    TicketStatisticsCalculator.nullToEmpty(ticket.getPriority(), "Trivial"),
                    ticket.getFieldsAsJSON().optString("dateClosed"),
                    StringUtils.cleanTextForMarkdown(ticket.getTicketLabels().toString()),
                    ticket.getTicketTitle()
                ));
                
                if (this.countStoryPoints) {
                    String sp = ticket.getFieldsAsJSON().optString("storyPoints", "0");
                    rowData.add(2, sp);
                }
                
                tableData.addRow(rowData);
            } catch (IOException e) {
                // Log error and potentially skip the row or add a placeholder
                logger.error("Error processing row for ticket: " + safeGetKey(ticket) + ". Error: " + e.getMessage());
            }
        }

        return baseTableGenerator.generateTable(tableData);
    }
    
    // Helper method to calculate total story points
    private int calculateTotalStoryPoints(List<ITicket> tickets) {
        int totalPoints = 0;
        for (ITicket ticket : tickets) {
            // Removed try-catch as compiler indicates IOException is not thrown by optInt
            // Assuming story points are stored as a number in fields
            totalPoints += ticket.getFieldsAsJSON().optInt("storyPoints", 0);
        }
        return totalPoints;
    }
    
    // Helper method to filter bugs by priority for SP calculation
    private List<ITicket> filterBugsByPriority(List<ITicket> bugs, String priority) {
        List<ITicket> filtered = new ArrayList<>();
        for (ITicket bug : bugs) {
             try {
                 String bugPriority = TicketStatisticsCalculator.nullToEmpty(bug.getPriority(), "Trivial"); // Can throw IOException
                 if (bugPriority.equals(priority)) {
                     filtered.add(bug);
                 }
             } catch (IOException e) {
                  logger.error("Error reading priority for bug " + bug.getKey() + " in filterBugsByPriority: " + e.getMessage());
             }
        }
        return filtered;
    }
    
    // Helper to safely get key even if ticket processing throws exception later
    private String safeGetKey(ITicket ticket) {
        try {
            return ticket.getKey();
        } catch (Exception ignored) {
            return "[unknown key]";
        }
    }
}