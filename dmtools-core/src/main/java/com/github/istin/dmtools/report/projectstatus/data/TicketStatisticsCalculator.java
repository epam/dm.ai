// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.data;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;

import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TicketStatisticsCalculator {

    private static final Logger logger = LogManager.getLogger(TicketStatisticsCalculator.class);

    private final ReportConfiguration config;

    public TicketStatisticsCalculator(ReportConfiguration config) {
        this.config = config;
    }

    public Map<String, Map<String, Integer>> collectTicketStatisticsByTypeAndPriority(List<ITicket> tickets) {
        Map<String, Map<String, Integer>> typePriorityCount = new HashMap<>();

        // Count tickets by type and priority
        for (ITicket ticket : tickets) {
            try {
                String type = nullToEmpty(ticket.getIssueType(), "Task");
                String priority = nullToEmpty(ticket.getPriority(), "Trivial");

                // Add to type-priority map
                typePriorityCount.computeIfAbsent(type, k -> new HashMap<>())
                        .merge(priority, 1, Integer::sum);
            } catch (IOException e) {
                logger.error("Error processing ticket: " + e.getMessage());
            }
        }

        return typePriorityCount;
    }

    public Map<String, Double> calculateStoryPointsByType(List<ITicket> tickets) {
        Map<String, Double> storyPointsByType = new HashMap<>();

        for (ITicket ticket : tickets) {
            try {
                String type = nullToEmpty(ticket.getIssueType(), "Task");
                double storyPoints = ticket.getWeight();
                storyPointsByType.merge(type, storyPoints, Double::sum);
            } catch (IOException e) {
                logger.error("Error processing ticket for story points: " + e.getMessage());
            }
        }

        return storyPointsByType;
    }

    public Map<String, Integer> countTicketsByPriority(List<ITicket> tickets) {
        Map<String, Integer> countByPriority = new HashMap<>();

        for (ITicket ticket : tickets) {
            try {
                String priority = nullToEmpty(ticket.getPriority(), "Trivial");
                countByPriority.merge(priority, 1, Integer::sum);
            } catch (IOException e) {
                logger.error("Error processing ticket priority: " + e.getMessage());
            }
        }

        return countByPriority;
    }

    public Map<String, List<ITicket>> categorizeTicketsByRole(List<ITicket> tickets) {
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        String[] rolePrefixes = config.getRolePrefixes();

        for (ITicket ticket : tickets) {
            try {
                String summary = ticket.getTicketTitle();
                String role = determineTicketRole(summary, rolePrefixes);

                // Add ticket to appropriate role list
                ticketsByRole.computeIfAbsent(role, k -> new ArrayList<>()).add(ticket);
            } catch (IOException e) {
                logger.error("Error categorizing ticket by role: " + e.getMessage());
            }
        }

        return ticketsByRole;
    }

    private String determineTicketRole(String summary, String[] rolePrefixes) {
        if (summary == null) {
            return "Development";
        }

        for (String tag : rolePrefixes) {
            if (summary.contains(tag)) {
                return tag;
            }
        }

        return "Development"; // Default role if no tag is found
    }

    public double calculatePercentage(double part, double total) {
        return (total > 0) ? (part / total) * 100 : 0;
    }

    public static String nullToEmpty(String value, String defaultValue) {
        return value == null || value.equalsIgnoreCase("null") ? defaultValue : value;
    }
}