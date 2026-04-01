// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoleDistributionTableGenerator implements TableGenerator {

    private static final Logger logger = LogManager.getLogger(RoleDistributionTableGenerator.class);
    private final TableGenerator baseTableGenerator;
    private final TicketStatisticsCalculator statisticsCalculator;
    private final TicketSorter ticketSorter;
    private final ReportConfiguration config;

    public RoleDistributionTableGenerator(TableGenerator baseTableGenerator, TicketStatisticsCalculator statisticsCalculator,
                                          TicketSorter ticketSorter, ReportConfiguration config) {
        this.baseTableGenerator = baseTableGenerator;
        this.statisticsCalculator = statisticsCalculator;
        this.ticketSorter = ticketSorter;
        this.config = config;
    }

    @Override
    public String generateTable(TableData tableData) {
        return baseTableGenerator.generateTable(tableData);
    }

    @Override
    public String generateTable(List<ITicket> tickets) {
        return generateRoleBasedDistributionTable(tickets);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        // Default implementation
        return generateTable(tickets);
    }

    public String generateRoleBasedDistributionTable(List<ITicket> tickets) {
        // Existing implementation...
        // Maps to store data by role
        Map<String, Integer> ticketCountByRole = new HashMap<>();
        Map<String, Double> storyPointsByRole = new HashMap<>();
        Map<String, List<String>> ticketKeysByRole = new HashMap<>();

        double totalStoryPoints = 0;
        int totalTickets = 0;

        // Categorize tickets by role
        Map<String, List<ITicket>> ticketsByRole = statisticsCalculator.categorizeTicketsByRole(tickets);

        // Process each role
        for (Map.Entry<String, List<ITicket>> entry : ticketsByRole.entrySet()) {
            String role = entry.getKey();
            List<ITicket> roleTickets = entry.getValue();

            int count = roleTickets.size();
            double points = 0;
            List<String> keys = new ArrayList<>();

            for (ITicket ticket : roleTickets) {
                points += ticket.getWeight();
                keys.add(ticket.getKey());
            }

            ticketCountByRole.put(role, count);
            storyPointsByRole.put(role, points);
            ticketKeysByRole.put(role, keys);

            totalStoryPoints += points;
            totalTickets += count;
        }

        // Sort roles
        List<String> sortedRoles = ticketSorter.sortRoles(ticketCountByRole.keySet());

        // Create table headers
        List<String> headers = Arrays.asList(
                "Role", "Ticket Count", "% of Tickets", "Story Points", "% of Points", "Avg. Points per Ticket"
        );
        TableData tableData = new TableData("Work Distribution by Role Category", headers);

        // Add data rows
        for (String role : sortedRoles) {
            int count = ticketCountByRole.get(role);
            double points = storyPointsByRole.getOrDefault(role, 0.0);
            double ticketPercentage = statisticsCalculator.calculatePercentage(count, totalTickets);
            double pointsPercentage = statisticsCalculator.calculatePercentage(points, totalStoryPoints);
            double avgPointsPerTicket = (count > 0) ? points / count : 0;
            String roleDescription = config.getRoleDescriptions().getOrDefault(role, role);

            List<String> row = Arrays.asList(
                    roleDescription,
                    String.valueOf(count),
                    String.format("%.1f%%", ticketPercentage),
                    String.format("%.1f", points),
                    String.format("%.1f%%", pointsPercentage),
                    String.format("%.2f", avgPointsPerTicket)
            );
            tableData.addRow(row);
        }

        // Add totals row
        double avgPointsOverall = (totalTickets > 0) ? totalStoryPoints / totalTickets : 0;
        List<String> totalsRow = Arrays.asList(
                "**Total**",
                String.valueOf(totalTickets),
                "100.0%",
                String.format("%.1f", totalStoryPoints),
                "100.0%",
                String.format("%.2f", avgPointsOverall)
        );
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    public String generateRoleSpecificTables(List<ITicket> tickets) {
        // Categorize tickets by role
        Map<String, List<ITicket>> ticketsByRole = statisticsCalculator.categorizeTicketsByRole(tickets);

        // Sort roles for consistent display
        List<String> sortedRoles = ticketSorter.sortRoles(ticketsByRole.keySet());

        StringBuilder roleSpecificTables = new StringBuilder();
        roleSpecificTables.append("## Role-Specific Work Details\n\n");

        // Generate table for each role
        for (String role : sortedRoles) {
            List<ITicket> roleTickets = ticketsByRole.get(role);
            String roleDescription = config.getRoleDescriptions().getOrDefault(role, role);

            roleSpecificTables.append("### ").append(roleDescription).append(" Work\n\n");

            // Generate story points summary for this role
            roleSpecificTables.append(generateRoleStoryPointsSummary(roleTickets));

            // Generate tickets table for this role
            roleSpecificTables.append(generateRoleTicketsTable(roleTickets));

            roleSpecificTables.append("\n\n");
        }

        return roleSpecificTables.toString();
    }

    private String generateRoleStoryPointsSummary(List<ITicket> roleTickets) {
        // Calculate story points by issue type
        Map<String, Double> storyPointsByType = new HashMap<>();
        double totalPoints = 0;

        for (ITicket ticket : roleTickets) {
            try {
                String type = TicketStatisticsCalculator.nullToEmpty(ticket.getIssueType(), "Task");
                double points = ticket.getWeight();

                storyPointsByType.merge(type, points, Double::sum);
                totalPoints += points;
            } catch (IOException e) {
                logger.error("Error processing story points for role table: " + e.getMessage());
            }
        }

        // Create table headers
        List<String> headers = Arrays.asList("Issue Type", "Story Points", "% of Role Total");
        TableData tableData = new TableData("Story Points by Issue Type", headers);

        List<String> types = new ArrayList<>(storyPointsByType.keySet());
        Collections.sort(types);

        for (String type : types) {
            double points = storyPointsByType.get(type);
            double percentage = statisticsCalculator.calculatePercentage(points, totalPoints);

            List<String> row = Arrays.asList(
                    type,
                    String.format("%.1f", points),
                    String.format("%.1f%%", percentage)
            );
            tableData.addRow(row);
        }

        List<String> totalsRow = Arrays.asList(
                "**Total**",
                String.format("%.1f", totalPoints),
                "100.0%"
        );
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    private String generateRoleTicketsTable(List<ITicket> roleTickets) {
        // Create table headers
        List<String> headers = Arrays.asList("Key", "Type", "Priority", "Story Points", "Closed Date", "Summary");
        TableData tableData = new TableData("Tickets", headers);

        for (ITicket ticket : roleTickets) {
            try {
                List<String> row = Arrays.asList(
                        ticket.getKey(),
                        ticket.getIssueType(),
                        TicketStatisticsCalculator.nullToEmpty(ticket.getPriority(), "Trivial"),
                        String.valueOf(ticket.getWeight()),
                        ticket.getFieldsAsJSON().getString("dateClosed"),
                        ticket.getTicketTitle()
                );
                tableData.addRow(row);
            } catch (IOException e) {
                logger.error("Error adding ticket to role table: " + e.getMessage());
            }
        }

        return baseTableGenerator.generateTable(tableData);
    }
}