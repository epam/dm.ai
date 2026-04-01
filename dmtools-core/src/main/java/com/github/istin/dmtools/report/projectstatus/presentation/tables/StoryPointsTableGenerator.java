// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StoryPointsTableGenerator implements TableGenerator {

    private static final Logger logger = LogManager.getLogger(StoryPointsTableGenerator.class);
    private final TableGenerator baseTableGenerator;
    private final TicketStatisticsCalculator statisticsCalculator;
    private final TicketSorter ticketSorter;

    public StoryPointsTableGenerator(TableGenerator baseTableGenerator, TicketStatisticsCalculator statisticsCalculator, TicketSorter ticketSorter) {
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
        return generateStoryPointsDistributionTable(tickets);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        // Default implementation
        return generateTable(tickets);
    }

    public String generateStoryPointsDistributionTable(List<ITicket> tickets) {
        // Existing implementation...
        // Calculate story points by issue type
        Map<String, Double> storyPointsByType = new HashMap<>();
        Map<String, Integer> ticketCountByType = new HashMap<>();
        double totalStoryPoints = 0;
        int totalTickets = 0;

        for (ITicket ticket : tickets) {
            try {
                String type = TicketStatisticsCalculator.nullToEmpty(ticket.getIssueType(), "Task");
                double storyPoints = ticket.getWeight();

                storyPointsByType.merge(type, storyPoints, Double::sum);
                ticketCountByType.merge(type, 1, Integer::sum);

                totalStoryPoints += storyPoints;
                totalTickets++;
            } catch (IOException e) {
                logger.error("Error processing ticket for story points: " + e.getMessage());
            }
        }

        // Sort issue types
        List<String> sortedTypes = ticketSorter.sortIssueTypes(storyPointsByType.keySet());

        // Create table headers
        List<String> headers = Arrays.asList(
                "Issue Type", "Ticket Count", "Story Points", "% of Total Points", "Avg. Points per Ticket"
        );
        TableData tableData = new TableData("Story Points Distribution by Issue Type", headers);

        // Add data rows
        for (String type : sortedTypes) {
            double points = storyPointsByType.get(type);
            int count = ticketCountByType.get(type);
            double percentage = statisticsCalculator.calculatePercentage(points, totalStoryPoints);
            double avgPointsPerTicket = (count > 0) ? points / count : 0;

            List<String> row = Arrays.asList(
                    type,
                    String.valueOf(count),
                    String.format("%.1f", points),
                    String.format("%.1f%%", percentage),
                    String.format("%.2f", avgPointsPerTicket)
            );
            tableData.addRow(row);
        }

        // Add totals row
        double avgPointsOverall = (totalTickets > 0) ? totalStoryPoints / totalTickets : 0;
        List<String> totalsRow = Arrays.asList(
                "**Total**",
                String.valueOf(totalTickets),
                String.format("%.1f", totalStoryPoints),
                "100.0%",
                String.format("%.2f", avgPointsOverall)
        );
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }
}