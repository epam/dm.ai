// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimelineTableGenerator implements TableGenerator {

    private static final Logger logger = LogManager.getLogger(TimelineTableGenerator.class);
    private final TableGenerator baseTableGenerator;
    private final TicketSorter ticketSorter;

    public TimelineTableGenerator(TableGenerator baseTableGenerator, TicketSorter ticketSorter) {
        this.baseTableGenerator = baseTableGenerator;
        this.ticketSorter = ticketSorter;
    }

    @Override
    public String generateTable(TableData tableData) {
        return baseTableGenerator.generateTable(tableData);
    }

    @Override
    public String generateTable(List<ITicket> tickets) {
        // Default to monthly timeline without story points
        return generateTimelineTable(tickets, TimelinePeriod.MONTH, false);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        return generateTable(tickets);
    }

    /**
     * Generates a timeline table showing deliverables over time
     *
     * @param tickets The list of tickets to analyze
     * @param period The time period to group by (WEEK, TWO_WEEKS, MONTH, QUARTER)
     * @param countStoryPoints If true, include story point calculations and columns; otherwise, focus on counts.
     * @return A formatted table showing deliverables over time
     */
    public String generateTimelineTable(List<ITicket> tickets, TimelinePeriod period, boolean countStoryPoints) {
        // Group tickets by time period and type
        Map<String, Map<String, List<ITicket>>> ticketsByPeriodAndType = groupTicketsByPeriodAndType(tickets, period);

        // Get sorted list of periods
        List<String> sortedPeriods = new ArrayList<>(ticketsByPeriodAndType.keySet());
        Collections.sort(sortedPeriods);

        // Get all unique issue types across all periods
        Set<String> allIssueTypes = new TreeSet<>();
        for (Map<String, List<ITicket>> typeMap : ticketsByPeriodAndType.values()) {
            allIssueTypes.addAll(typeMap.keySet());
        }
        List<String> sortedIssueTypes = new ArrayList<>(allIssueTypes);

        // Identify bug-like and feature-like issue types
        List<String> bugTypes = sortedIssueTypes.stream()
                .filter(type -> type.toLowerCase().contains("bug") || type.toLowerCase().contains("defect"))
                .collect(Collectors.toList());

        List<String> featureTypes = sortedIssueTypes.stream()
                .filter(type -> !bugTypes.contains(type))
                .collect(Collectors.toList());

        boolean onlyBugs = featureTypes.isEmpty() && !bugTypes.isEmpty(); // Check if only bugs are present

        StringBuilder result = new StringBuilder();

        // Generate the main timeline table (conditionally include SP column)
        result.append(generateDeliverableCountTable(ticketsByPeriodAndType, sortedPeriods, sortedIssueTypes, period, countStoryPoints));

        // Generate the story points timeline table only if requested
        if (countStoryPoints) {
            result.append("\n");
            result.append(generateStoryPointsTable(ticketsByPeriodAndType, sortedPeriods, sortedIssueTypes, period));
        }

        // Generate the averages summary (conditionally include SP metrics)
        result.append("\n");
        result.append(generateAveragesSummary(ticketsByPeriodAndType, sortedPeriods, bugTypes, featureTypes, period, onlyBugs, countStoryPoints));

        // Generate the dual-column chart (conditionally include SP column)
        result.append("\n");
        result.append(generateDualColumnChart(ticketsByPeriodAndType, sortedPeriods, bugTypes, period, onlyBugs, countStoryPoints));

        return result.toString();
    }

    /**
     * Generates a timeline table showing bugs created over time, grouped by priority.
     *
     * @param bugs   The list of bug tickets to analyze.
     * @param period The time period to group by (WEEK, TWO_WEEKS, MONTH, QUARTER).
     * @return A formatted table showing created bugs over time by priority.
     */
    public String generateTimelineByCreationAndPriority(List<ITicket> bugs, TimelinePeriod period) {
        // Group bugs by time period (creation date) and priority
        Map<String, Map<String, List<ITicket>>> bugsByPeriodAndPriority = groupBugsByCreationPeriodAndPriority(bugs, period);

        // Get sorted list of periods
        List<String> sortedPeriods = new ArrayList<>(bugsByPeriodAndPriority.keySet());
        Collections.sort(sortedPeriods);

        // Get all unique priorities across all periods
        Set<String> allPriorities = new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder())); // Handle potential null priorities
        for (Map<String, List<ITicket>> priorityMap : bugsByPeriodAndPriority.values()) {
            allPriorities.addAll(priorityMap.keySet());
        }
        List<String> sortedPriorities = new ArrayList<>(allPriorities);
        // TODO: Consider using TicketSorter to sort priorities if a specific order is needed

        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Creation Period");
        headers.addAll(sortedPriorities.stream().map(p -> p != null ? p : "No Priority").collect(Collectors.toList())); // Use "No Priority" for null
        headers.add("Total Created");

        TableData tableData = new TableData(
                "Created Bugs Timeline by Priority (" + period.getDescription() + ")",
                headers,
                "Shows the number of bugs created over time periods, grouped by priority."
        );

        // Add data rows
        Map<String, Integer> priorityTotals = new HashMap<>();
        int grandTotal = 0;

        for (String periodKey : sortedPeriods) {
            Map<String, List<ITicket>> priorityMap = bugsByPeriodAndPriority.get(periodKey);
            List<String> row = new ArrayList<>();
            row.add(periodKey);

            int periodTotal = 0;

            // Add counts for each priority
            for (String priority : sortedPriorities) {
                int count = priorityMap.getOrDefault(priority, Collections.emptyList()).size();
                row.add(String.valueOf(count));
                periodTotal += count;

                // Update priority totals
                priorityTotals.put(priority, priorityTotals.getOrDefault(priority, 0) + count);
            }

            row.add(String.valueOf(periodTotal));
            tableData.addRow(row);

            // Update grand total
            grandTotal += periodTotal;
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");

        for (String priority : sortedPriorities) {
            totalsRow.add(String.valueOf(priorityTotals.getOrDefault(priority, 0)));
        }
        totalsRow.add(String.valueOf(grandTotal));
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    /**
     * Generates a table showing the count of deliverables by type over time
     */
    private String generateDeliverableCountTable(Map<String, Map<String, List<ITicket>>> ticketsByPeriodAndType,
                                                 List<String> sortedPeriods, List<String> issueTypes, TimelinePeriod period,
                                                 boolean countStoryPoints) {
        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Time Period");
        headers.addAll(issueTypes);
        headers.add("Total");
        if (countStoryPoints) {
            headers.add("Total Story Points");
        }

        TableData tableData = new TableData(
                "Deliverables Timeline (" + period.getDescription() + ")",
                headers,
                "Shows the number of completed items by type over time periods."
        );

        // Add data rows
        Map<String, Integer> typeTotals = new HashMap<>();
        int grandTotal = 0;
        double totalStoryPoints = 0;

        for (String periodKey : sortedPeriods) {
            Map<String, List<ITicket>> typeMap = ticketsByPeriodAndType.get(periodKey);

            List<String> row = new ArrayList<>();
            row.add(periodKey);

            int periodTotal = 0;

            // Add counts for each issue type
            for (String issueType : issueTypes) {
                int count = typeMap.getOrDefault(issueType, Collections.emptyList()).size();
                row.add(String.valueOf(count));
                periodTotal += count;

                // Update type totals
                typeTotals.put(issueType, typeTotals.getOrDefault(issueType, 0) + count);
            }

            // Calculate story points for this period
            double periodStoryPoints = calculateTotalStoryPoints(typeMap);

            row.add(String.valueOf(periodTotal));
            if (countStoryPoints) {
                row.add(String.format("%.1f", periodStoryPoints));
            }

            tableData.addRow(row);

            // Update totals
            grandTotal += periodTotal;
            totalStoryPoints += periodStoryPoints;
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");

        for (String issueType : issueTypes) {
            totalsRow.add(String.valueOf(typeTotals.getOrDefault(issueType, 0)));
        }

        totalsRow.add(String.valueOf(grandTotal));
        if (countStoryPoints) {
            totalsRow.add(String.format("%.1f", totalStoryPoints));
        }

        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    /**
     * Generates a table showing story points by issue type over time
     */
    private String generateStoryPointsTable(Map<String, Map<String, List<ITicket>>> ticketsByPeriodAndType,
                                            List<String> sortedPeriods, List<String> issueTypes, TimelinePeriod period) {
        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Time Period");

        // Add points columns for each issue type
        for (String issueType : issueTypes) {
            headers.add(issueType + " Points");
        }

        headers.add("Total Points");
        headers.add("Avg Points per Item");

        TableData tableData = new TableData(
                "Story Points Timeline (" + period.getDescription() + ")",
                headers,
                "Shows the distribution of story points by issue type over time periods."
        );

        // Add data rows
        Map<String, Double> typePointsTotals = new HashMap<>();
        double grandTotalPoints = 0;
        int grandTotalItems = 0;

        for (String periodKey : sortedPeriods) {
            Map<String, List<ITicket>> typeMap = ticketsByPeriodAndType.get(periodKey);

            List<String> row = new ArrayList<>();
            row.add(periodKey);

            double periodTotalPoints = 0;

            // Add story points for each issue type
            for (String issueType : issueTypes) {
                double points = calculateStoryPointsForType(typeMap.getOrDefault(issueType, Collections.emptyList()));
                row.add(String.format("%.1f", points));
                periodTotalPoints += points;

                // Update type totals
                typePointsTotals.put(issueType, typePointsTotals.getOrDefault(issueType, 0.0) + points);
            }

            int periodTotalItems = typeMap.values().stream().mapToInt(List::size).sum();
            double avgPointsPerItem = periodTotalItems > 0 ? periodTotalPoints / periodTotalItems : 0;

            row.add(String.format("%.1f", periodTotalPoints));
            row.add(String.format("%.2f", avgPointsPerItem));

            tableData.addRow(row);

            // Update totals
            grandTotalPoints += periodTotalPoints;
            grandTotalItems += periodTotalItems;
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");

        for (String issueType : issueTypes) {
            totalsRow.add(String.format("%.1f", typePointsTotals.getOrDefault(issueType, 0.0)));
        }

        double overallAvgPoints = grandTotalItems > 0 ? grandTotalPoints / grandTotalItems : 0;
        totalsRow.add(String.format("%.1f", grandTotalPoints));
        totalsRow.add(String.format("%.2f", overallAvgPoints));

        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    /**
     * Generates a summary of average metrics across time periods
     */
    private String generateAveragesSummary(Map<String, Map<String, List<ITicket>>> ticketsByPeriodAndType,
                                           List<String> sortedPeriods, List<String> bugTypes, List<String> featureTypes,
                                           TimelinePeriod period, boolean onlyBugs,
                                           boolean countStoryPoints) {
        StringBuilder summary = new StringBuilder();
        summary.append("## Average Metrics per ").append(period.getDescription()).append(" Period\n\n");

        // Calculate totals and averages
        int totalBugs = 0;
        int totalFeatures = 0;
        double totalBugPoints = 0;
        double totalFeaturePoints = 0;
        double totalStoryPoints = 0;

        for (String periodKey : sortedPeriods) {
            Map<String, List<ITicket>> typeMap = ticketsByPeriodAndType.get(periodKey);

            // Count bugs and features
            for (String bugType : bugTypes) {
                totalBugs += typeMap.getOrDefault(bugType, Collections.emptyList()).size();
                totalBugPoints += calculateStoryPointsForType(typeMap.getOrDefault(bugType, Collections.emptyList()));
            }

            for (String featureType : featureTypes) {
                totalFeatures += typeMap.getOrDefault(featureType, Collections.emptyList()).size();
                totalFeaturePoints += calculateStoryPointsForType(typeMap.getOrDefault(featureType, Collections.emptyList()));
            }

            // Calculate total story points
            for (List<ITicket> tickets : typeMap.values()) {
                totalStoryPoints += calculateStoryPointsForType(tickets);
            }
        }

        int periodCount = sortedPeriods.size();
        if (periodCount > 0) {
            if (onlyBugs) {
                // Simplified summary for bugs only
                List<String> headers = new ArrayList<>(Arrays.asList("Metric", "Total", "Average per " + period.getDescription()));
                TableData tableData = new TableData("Bug Metrics Averages", headers);

                double avgBugsPerPeriod = (double) totalBugs / periodCount;
                tableData.addRow(Arrays.asList(
                        "Bugs Completed",
                        String.valueOf(totalBugs),
                        String.format("%.1f", avgBugsPerPeriod)
                ));

                if (countStoryPoints) {
                    double avgBugPointsPerPeriod = totalBugPoints / periodCount;
                    tableData.addRow(Arrays.asList(
                            "Bug Story Points",
                            String.format("%.1f", totalBugPoints),
                            String.format("%.1f", avgBugPointsPerPeriod)
                    ));
                }

                summary.append(baseTableGenerator.generateTable(tableData));

                // Simplified Key Insights
                summary.append("### Key Insights\n\n");
                if (countStoryPoints) {
                    double avgBugPointsPerPeriod = totalBugPoints / periodCount;
                    summary.append("- **Average Bug Velocity**: ")
                            .append(String.format("%.1f", avgBugsPerPeriod))
                            .append(" bugs / ")
                            .append(String.format("%.1f", avgBugPointsPerPeriod))
                            .append(" SPs per ")
                            .append(period.getDescription().toLowerCase())
                            .append("\n");
                    double avgPointsPerBug = totalBugs > 0 ? totalBugPoints / totalBugs : 0;
                    summary.append("- **Average Points per Bug**: ")
                            .append(String.format("%.2f", avgPointsPerBug))
                            .append("\n");
                } else {
                    summary.append("- **Average Bugs Completed**: ")
                            .append(String.format("%.1f", avgBugsPerPeriod))
                            .append(" per ")
                            .append(period.getDescription().toLowerCase())
                            .append("\n");
                }

            } else {
                // Create a table for the averages
                List<String> headers = new ArrayList<>(Arrays.asList(
                        "Metric", "Total", "Average per " + period.getDescription()));
                 if (countStoryPoints) {
                      headers.add("% of Total Work (Items)"); // Or SP?
                      headers.add("% of Total SP");
                 } else {
                      headers.add("% of Total Items");
                 }

                TableData tableData = new TableData("Work Distribution Averages", headers);

                // Bug metrics
                double avgBugsPerPeriod = (double) totalBugs / periodCount;
                double bugItemPercentage = (totalBugs + totalFeatures > 0) ? totalBugs * 100.0 / (totalBugs + totalFeatures) : 0.0;
                List<String> bugRow = new ArrayList<>(Arrays.asList(
                        "Bugs Fixed",
                        String.valueOf(totalBugs),
                        String.format("%.1f", avgBugsPerPeriod)
                ));
                if (countStoryPoints) {
                     bugRow.add(String.format("%.1f%%", bugItemPercentage));
                     double bugPointsPercentage = (totalStoryPoints > 0) ? totalBugPoints * 100.0 / totalStoryPoints : 0.0;
                     bugRow.add(String.format("%.1f%%", bugPointsPercentage));
                } else {
                     bugRow.add(String.format("%.1f%%", bugItemPercentage));
                }
                tableData.addRow(bugRow);

                // Feature work metrics
                double avgFeaturesPerPeriod = (double) totalFeatures / periodCount;
                double featureItemPercentage = (totalBugs + totalFeatures > 0) ? totalFeatures * 100.0 / (totalBugs + totalFeatures) : 0.0;
                List<String> featureWorkRow = new ArrayList<>(Arrays.asList(
                        "Feature Work (" + String.join(", ", featureTypes) + ")",
                        String.valueOf(totalFeatures),
                        String.format("%.1f", avgFeaturesPerPeriod)
                ));
                 if (countStoryPoints) {
                     featureWorkRow.add(String.format("%.1f%%", featureItemPercentage));
                     double featurePointsPercentage = (totalStoryPoints > 0) ? totalFeaturePoints * 100.0 / totalStoryPoints : 0.0;
                     featureWorkRow.add(String.format("%.1f%%", featurePointsPercentage));
                } else {
                     featureWorkRow.add(String.format("%.1f%%", featureItemPercentage));
                }
                tableData.addRow(featureWorkRow);

                // Add individual feature type rows
                for (String featureType : featureTypes) {
                    int typeTotal = 0;
                    for (Map<String, List<ITicket>> typeMap : ticketsByPeriodAndType.values()) {
                        typeTotal += typeMap.getOrDefault(featureType, Collections.emptyList()).size();
                    }

                    double avgPerPeriod = (double) typeTotal / periodCount;
                    double percentage = typeTotal * 100.0 / (totalBugs + totalFeatures);

                    List<String> typeRow = new ArrayList<>(Arrays.asList(
                            featureType + " Completed",
                            String.valueOf(typeTotal),
                            String.format("%.1f", avgPerPeriod)
                    ));
                    if (countStoryPoints) {
                         // When counting SP, we add Item % and SP %. SP % needs calculation or placeholder
                         // Placeholder for SP % for individual feature type row
                         typeRow.add(String.format("%.1f%%", percentage)); 
                         typeRow.add("-"); // Placeholder for SP % 
                    } else {
                        // When not counting SP, we just add Item %
                        typeRow.add(String.format("%.1f%%", percentage));
                    }
                    tableData.addRow(typeRow);
                }

                // Total items
                int totalItems = totalBugs + totalFeatures;
                double avgItemsPerPeriod = (double) totalItems / periodCount;
                List<String> totalItemsRow = new ArrayList<>(Arrays.asList(
                        "Total Items Completed",
                        String.valueOf(totalItems),
                        String.format("%.1f", avgItemsPerPeriod)
                ));
                 if (countStoryPoints) {
                     totalItemsRow.add("100.0%");
                     totalItemsRow.add("100.0%");
                 } else {
                     totalItemsRow.add("100.0%");
                 }
                tableData.addRow(totalItemsRow);

                // Story points metrics (only if requested)
                if (countStoryPoints) {
                    double avgPointsPerPeriod = totalStoryPoints / periodCount;
                    List<String> pointsRow = new ArrayList<>(Arrays.asList(
                            "Total Story Points",
                            String.format("%.1f", totalStoryPoints),
                            String.format("%.1f", avgPointsPerPeriod),
                            "-", // N/A for item percentage
                            "100.0%"
                    ));
                    tableData.addRow(pointsRow);

                    // Bug points vs Feature points
                    double bugPointsPercentage = (totalStoryPoints > 0) ? totalBugPoints * 100.0 / totalStoryPoints : 0.0;
                    List<String> bugPointsRow = new ArrayList<>(Arrays.asList(
                            "Bug Story Points",
                            String.format("%.1f", totalBugPoints),
                            String.format("%.1f", totalBugPoints / periodCount),
                            "-", // N/A for item percentage
                            String.format("%.1f%%", bugPointsPercentage)
                    ));
                    tableData.addRow(bugPointsRow);

                    double featurePointsPercentage = (totalStoryPoints > 0) ? totalFeaturePoints * 100.0 / totalStoryPoints : 0.0;
                    List<String> featurePointsRow = new ArrayList<>(Arrays.asList(
                            "Feature Story Points",
                            String.format("%.1f", totalFeaturePoints),
                            String.format("%.1f", totalFeaturePoints / periodCount),
                            "-", // N/A for item percentage
                            String.format("%.1f%%", featurePointsPercentage)
                    ));
                    tableData.addRow(featurePointsRow);
                }

                summary.append(baseTableGenerator.generateTable(tableData));

                // Add key insights (conditionally include SP)
                summary.append("### Key Insights\n\n");

                // Bug to feature work ratio
                double bugToFeatureRatio = totalFeatures > 0 ? (double) totalBugs / totalFeatures : 0;
                summary.append("- **Bug to Feature Work Ratio**: ")
                        .append(String.format("%.2f", bugToFeatureRatio))
                        .append(" bugs per feature work item\n");

                if (countStoryPoints) {
                    double avgPointsPerPeriod = totalStoryPoints / periodCount;
                    summary.append("- **Average Velocity**: ")
                            .append(String.format("%.1f", avgPointsPerPeriod))
                            .append(" story points per ")
                            .append(period.getDescription().toLowerCase())
                            .append("\n");

                    double bugPointsPercentage = (totalStoryPoints > 0) ? totalBugPoints * 100.0 / totalStoryPoints : 0.0;
                    summary.append("- **Bug Work**: ")
                            .append(String.format("%.1f%%", bugItemPercentage))
                            .append(" of total items, ")
                            .append(String.format("%.1f%%", bugPointsPercentage))
                            .append(" of total story points\n");

                    double featurePointsPercentage = (totalStoryPoints > 0) ? totalFeaturePoints * 100.0 / totalStoryPoints : 0.0;
                    summary.append("- **Feature Work**: ")
                            .append(String.format("%.1f%%", featureItemPercentage))
                            .append(" of total items, ")
                            .append(String.format("%.1f%%", featurePointsPercentage))
                            .append(" of total story points\n");

                    double avgPointsPerBug = totalBugs > 0 ? totalBugPoints / totalBugs : 0;
                    double avgPointsPerFeature = totalFeatures > 0 ? totalFeaturePoints / totalFeatures : 0;
                    summary.append("- **Average Points**: ")
                            .append(String.format("%.2f", avgPointsPerBug))
                            .append(" per bug, ")
                            .append(String.format("%.2f", avgPointsPerFeature))
                            .append(" per feature work item\n");
                } else {
                    summary.append("- **Average Items Completed**: ")
                            .append(String.format("%.1f", avgItemsPerPeriod))
                            .append(" per ")
                            .append(period.getDescription().toLowerCase())
                            .append("\n");
                     summary.append("- **Bug Work**: ")
                            .append(String.format("%.1f%%", bugItemPercentage))
                            .append(" of total items\n");
                     summary.append("- **Feature Work**: ")
                            .append(String.format("%.1f%%", featureItemPercentage))
                            .append(" of total items\n");
                }
            }
        }

        return summary.toString();
    }

    /**
     * Generates a dual-column chart showing bug count and optionally story points over time
     */
    private String generateDualColumnChart(Map<String, Map<String, List<ITicket>>> ticketsByPeriodAndType,
                                           List<String> sortedPeriods, List<String> bugTypes, TimelinePeriod period,
                                           boolean onlyBugs,
                                           boolean countStoryPoints) {
        StringBuilder chart = new StringBuilder();
        String chartTitle = countStoryPoints ? ("Bug Count vs " + (onlyBugs ? "Bug Story Points" : "Total Story Points")) : "Bug Count";
        chart.append("## ").append(chartTitle).append(" (").append(period.getDescription()).append(")\n\n");
        chart.append("```\n");

        // Calculate bug counts and optionally story points for each period
        List<Integer> bugCounts = new ArrayList<>();
        List<Double> storyPointsValues = new ArrayList<>(); // Only populated if countStoryPoints is true

        int maxBugCount = 0;
        double maxStoryPoints = 0; // Only used if countStoryPoints is true

        for (String periodKey : sortedPeriods) {
            Map<String, List<ITicket>> typeMap = ticketsByPeriodAndType.get(periodKey);

            // Count bugs for this period
            int bugCount = 0;
            for (String bugType : bugTypes) {
                bugCount += typeMap.getOrDefault(bugType, Collections.emptyList()).size();
            }

            // Calculate story points for this period (total or bugs only)
            double storyPoints;
            if (onlyBugs) {
                storyPoints = calculateStoryPointsForSpecificTypes(typeMap, bugTypes);
            } else {
                storyPoints = calculateTotalStoryPoints(typeMap);
            }

            bugCounts.add(bugCount);
            if (countStoryPoints) {
                storyPointsValues.add(storyPoints);
                maxStoryPoints = Math.max(maxStoryPoints, storyPoints);
            }

            maxBugCount = Math.max(maxBugCount, bugCount);
        }

        // Chart settings
        int chartHeight = 15;

        // Handle case where there are no bugs
        if (maxBugCount == 0) {
            maxBugCount = 1; // Set to 1 to avoid division by zero
        }

        // Y-axis scale for bugs (left side)
        double bugScale = (double) chartHeight / maxBugCount;

        // Handle case where there are no story points
        if (maxStoryPoints == 0) {
            maxStoryPoints = 1.0; // Set to 1.0 to avoid division by zero
        }

        // Y-axis scale for story points (right side), only if needed
        double pointsScale = 0;
        if (countStoryPoints) {
             pointsScale = (double) chartHeight / maxStoryPoints;
        }

        // Generate chart rows from top to bottom
        for (int row = chartHeight; row >= 0; row--) {
            // Left Y-axis label (bugs)
            if (row % 3 == 0 || row == chartHeight) {
                // Calculate bug value for this row
                int bugValue = (int) Math.round(row / bugScale);
                chart.append(String.format("%3d |", bugValue));
            } else {
                chart.append("    |");
            }

            // Chart data
            for (int i = 0; i < sortedPeriods.size(); i++) {
                int bugHeight = (int) Math.round(bugCounts.get(i) * bugScale);
                int pointsHeight = 0; // Default if not counting SP
                if (countStoryPoints) {
                    pointsHeight = (int) Math.round(storyPointsValues.get(i) * pointsScale);
                }

                // Bug column (█)
                if (row <= bugHeight && bugCounts.get(i) > 0) {
                    chart.append("█");
                } else {
                    chart.append(" ");
                }

                // Spacer and Story points column (▓), only if counting SP
                if (countStoryPoints) {
                    chart.append(" "); // Spacer
                    if (row <= pointsHeight && storyPointsValues.get(i) > 0) {
                        chart.append("▓");
                    } else {
                        chart.append(" ");
                    }
                    chart.append(" "); // Column separator
                } else {
                     chart.append("  "); // Wider separator if only one column
                }
            }

            // Right Y-axis label (story points), only if counting SP
            if (countStoryPoints) {
                if (row % 3 == 0 || row == chartHeight) {
                    double pointsValue = row / pointsScale;
                    chart.append(String.format("| %5.1f", pointsValue));
                } else {
                    chart.append("|");
                }
            } else {
                 chart.append("|"); // Still need the closing pipe
            }

            chart.append("\n");
        }

        // X-axis
        chart.append("    +");
        for (int i = 0; i < sortedPeriods.size(); i++) {
            chart.append(countStoryPoints ? "----" : "---"); // Adjust line length
        }
        chart.append("+\n     ");

        // X-axis labels
        for (String sortedPeriod : sortedPeriods) {
            // Extract the last part of the sortedPeriod key (e.g., "01" from "2023-01")
            String label = sortedPeriod.substring(sortedPeriod.length() - 2);
            chart.append(label).append(countStoryPoints ? "  " : " "); // Adjust spacing
        }

        chart.append("\n\n");

        // Add note if no bugs were found
        if (bugCounts.stream().mapToInt(Integer::intValue).sum() == 0) {
            chart.append("Note: No bugs were found in this time period.\n");
        }

        if (countStoryPoints) {
            String pointsLabel = onlyBugs ? "Bug Story Points" : "Total Story Points";
            chart.append("Legend: █ = Bug Count   ▓ = ").append(pointsLabel).append("\n");
            chart.append("Left axis: Bug Count   Right axis: ").append(pointsLabel).append("\n");
        } else {
            chart.append("Legend: █ = Bug Count\n");
            chart.append("Left axis: Bug Count\n");
        }
        chart.append("```\n\n");

        return chart.toString();
    }

    /**
     * Calculates total story points for all tickets in a type map
     */
    private double calculateTotalStoryPoints(Map<String, List<ITicket>> typeMap) {
        double totalPoints = 0;
        for (List<ITicket> tickets : typeMap.values()) {
            totalPoints += calculateStoryPointsForType(tickets);
        }
        return totalPoints;
    }

    /**
     * Calculates story points for a list of tickets
     */
    private double calculateStoryPointsForType(List<ITicket> tickets) {
        double points = 0;
        for (ITicket ticket : tickets) {
            points += ticket.getWeight();
        }
        return points;
    }

    /**
     * Calculates total story points for specific issue types within a type map
     */
    private double calculateStoryPointsForSpecificTypes(Map<String, List<ITicket>> typeMap, List<String> targetTypes) {
        double totalPoints = 0;
        for (String type : targetTypes) {
            totalPoints += calculateStoryPointsForType(typeMap.getOrDefault(type, Collections.emptyList()));
        }
        return totalPoints;
    }

    /**
     * Groups tickets by time period and issue type
     */
    private Map<String, Map<String, List<ITicket>>> groupTicketsByPeriodAndType(List<ITicket> tickets, TimelinePeriod period) {
        Map<String, Map<String, List<ITicket>>> result = new TreeMap<>();

        for (ITicket ticket : tickets) {
            try {
                // Get the closed date
                String closedDateStr = ticket.getFieldsAsJSON().getString("dateClosed");
                Date closedDate = parseDate(closedDateStr);

                if (closedDate == null) {
                    logger.error("Could not parse date: " + closedDateStr);
                    continue;
                }

                // Format the period key
                String periodKey = formatDateToPeriod(closedDate, period);

                // Get the issue type
                String issueType = TicketStatisticsCalculator.nullToEmpty(ticket.getIssueType(), "Task");

                // Add to the appropriate maps
                Map<String, List<ITicket>> typeMap = result.computeIfAbsent(periodKey, k -> new HashMap<>());
                List<ITicket> typeList = typeMap.computeIfAbsent(issueType, k -> new ArrayList<>());
                typeList.add(ticket);

            } catch (Exception e) {
                logger.error("Error processing ticket for timeline: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Groups bug tickets based on their creation date and priority.
     */
    private Map<String, Map<String, List<ITicket>>> groupBugsByCreationPeriodAndPriority(List<ITicket> bugs, TimelinePeriod period) {
        Map<String, Map<String, List<ITicket>>> groupedBugs = new LinkedHashMap<>(); // Use LinkedHashMap to maintain insertion order for periods

        for (ITicket bug : bugs) {
            Date creationDate = bug.getCreated(); // Use creation date
            if (creationDate == null) {
                logger.error("Warning: Bug " + bug.getKey() + " has no creation date, skipping for timeline.");
                continue; // Skip tickets without a creation date
            }

            String periodKey = formatDateToPeriod(creationDate, period);
            String priority = null;
            try {
                priority = bug.getPriority(); // Use priority
            } catch (IOException e) {
                logger.error("Warning: Could not get priority for bug " + bug.getKey() + ": " + e.getMessage());
                // Assign a default or handle as needed, here we use null
            }

            groupedBugs
                .computeIfAbsent(periodKey, k -> new HashMap<>())
                .computeIfAbsent(priority, k -> new ArrayList<>())
                .add(bug);
        }
        return groupedBugs;
    }

    /**
     * Attempts to parse a date string using multiple common formats
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // List of date formats to try
        List<String> dateFormats = Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        );

        for (String format : dateFormats) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(format);
                return dateFormat.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }

        return null; // Could not parse with any format
    }

    /**
     * Formats a date to the appropriate period string
     */
    private String formatDateToPeriod(Date date, TimelinePeriod period) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);

        switch (period) {
            case WEEK:
                // Format as YYYY-Www (e.g., 2023-W01)
                int week = cal.get(Calendar.WEEK_OF_YEAR);
                return String.format("%d-W%02d", year, week);

            case TWO_WEEKS:
                // Group into two-week periods (e.g., 2023-P01 for weeks 1-2)
                int weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
                int twoWeekPeriod = (weekOfYear + 1) / 2;
                return String.format("%d-P%02d", year, twoWeekPeriod);

            case MONTH:
                // Format as YYYY-MM
                int month = cal.get(Calendar.MONTH) + 1; // Calendar months are 0-based
                return String.format("%d-%02d", year, month);

            case QUARTER:
                // Calculate quarter (1-4) and format as YYYY-Q# (e.g., 2023-Q1)
                int month0Based = cal.get(Calendar.MONTH);
                int quarter = (month0Based / 3) + 1;
                return String.format("%d-Q%d", year, quarter);

            default:
                // Default to YYYY-MM-DD
                int monthDefault = cal.get(Calendar.MONTH) + 1;
                int day = cal.get(Calendar.DAY_OF_MONTH);
                return String.format("%d-%02d-%02d", year, monthDefault, day);
        }
    }

    /**
     * Generates a timeline table showing items created over time, grouped by a specific category field.
     *
     * @param items           The list of tickets (items) to analyze.
     * @param period          The time period to group by (WEEK, TWO_WEEKS, MONTH, QUARTER).
     * @param categoryFieldName The name of the field to use for categorization (e.g., "priority", "status").
     * @param categoryDisplayName The display name for the category (e.g., "Priority", "Status").
     * @param countStoryPoints If true, aggregate Story Points; otherwise, count tickets.
     * @return A formatted table showing created items over time by the specified category.
     */
    public String generateTimelineByCreationAndCategory(List<ITicket> items, TimelinePeriod period, String categoryFieldName, String categoryDisplayName, boolean countStoryPoints) {
        // Group items by time period (creation date) and category field
        Map<String, Map<String, List<ITicket>>> itemsByPeriodAndCategory = groupItemsByCreationPeriodAndCategory(items, period, categoryFieldName);

        // Get sorted list of periods
        List<String> sortedPeriods = new ArrayList<>(itemsByPeriodAndCategory.keySet());
        Collections.sort(sortedPeriods);

        // Get all unique categories across all periods
        Set<String> allCategories = new HashSet<>();
        for (Map<String, List<ITicket>> categoryMap : itemsByPeriodAndCategory.values()) {
            allCategories.addAll(categoryMap.keySet());
        }

        // Sort categories using TicketSorter if applicable, otherwise alphabetically
        List<String> sortedCategories;
        if ("priority".equalsIgnoreCase(categoryFieldName) && ticketSorter != null) {
            sortedCategories = ticketSorter.sortPriorities(allCategories);
        } else if ("issueType".equalsIgnoreCase(categoryFieldName) && ticketSorter != null) {
            sortedCategories = ticketSorter.sortIssueTypes(allCategories);
        } else {
            // Default sort (handle nulls)
            sortedCategories = new ArrayList<>(allCategories);
            sortedCategories.sort(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }

        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Creation Period");
        headers.addAll(sortedCategories.stream().map(c -> c != null ? c : "N/A").collect(Collectors.toList())); // Use "N/A" for null
        headers.add(countStoryPoints ? "Total SP Created" : "Total Created");

        TableData tableData = new TableData(
                "Created Items Timeline by " + categoryDisplayName + " (" + period.getDescription() + ")",
                headers,
                "Shows the " + (countStoryPoints ? "sum of story points" : "number of items") + " created over time periods, grouped by " + categoryDisplayName.toLowerCase() + "."
        );

        // Add data rows
        Map<String, Double> categoryTotalsSP = new HashMap<>(); // Use Double for SP
        Map<String, Integer> categoryTotalsCount = new HashMap<>();
        double grandTotalSP = 0;
        int grandTotalCount = 0;

        for (String periodKey : sortedPeriods) {
            Map<String, List<ITicket>> categoryMap = itemsByPeriodAndCategory.get(periodKey);
            List<String> row = new ArrayList<>();
            row.add(periodKey);

            double periodTotalSP = 0;
            int periodTotalCount = 0;

            // Add counts or SP for each category
            for (String category : sortedCategories) {
                List<ITicket> categoryItems = categoryMap.getOrDefault(category, Collections.emptyList());
                if (countStoryPoints) {
                    double points = calculateStoryPointsForType(categoryItems);
                    row.add(String.format("%.1f", points));
                    periodTotalSP += points;
                    categoryTotalsSP.put(category, categoryTotalsSP.getOrDefault(category, 0.0) + points);
                } else {
                    int count = categoryItems.size();
                    row.add(String.valueOf(count));
                    periodTotalCount += count;
                    categoryTotalsCount.put(category, categoryTotalsCount.getOrDefault(category, 0) + count);
                }
            }

            if (countStoryPoints) {
                row.add(String.format("%.1f", periodTotalSP));
                grandTotalSP += periodTotalSP;
            } else {
                row.add(String.valueOf(periodTotalCount));
                grandTotalCount += periodTotalCount;
            }
            tableData.addRow(row);
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");

        for (String category : sortedCategories) {
            if (countStoryPoints) {
                totalsRow.add(String.format("%.1f", categoryTotalsSP.getOrDefault(category, 0.0)));
            } else {
                totalsRow.add(String.valueOf(categoryTotalsCount.getOrDefault(category, 0)));
            }
        }

        if (countStoryPoints) {
            totalsRow.add(String.format("%.1f", grandTotalSP));
        } else {
            totalsRow.add(String.valueOf(grandTotalCount));
        }
        tableData.addRow(totalsRow);

        return baseTableGenerator.generateTable(tableData);
    }

    /**
     * Groups items based on their creation date and a specified category field.
     */
    private Map<String, Map<String, List<ITicket>>> groupItemsByCreationPeriodAndCategory(
            List<ITicket> items, TimelinePeriod period, String categoryFieldName) {
        Map<String, Map<String, List<ITicket>>> groupedItems = new LinkedHashMap<>(); // Maintain insertion order

        for (ITicket item : items) {
            Date creationDate = item.getCreated();
            if (creationDate == null) {
                logger.error("Warning: Item " + item.getKey() + " has no creation date, skipping for timeline.");
                continue;
            }

            String periodKey = formatDateToPeriod(creationDate, period);
            String categoryValue = null;
            try {
                // Dynamically get category value based on field name
                if ("priority".equalsIgnoreCase(categoryFieldName)) {
                    categoryValue = item.getPriority();
                } else if ("status".equalsIgnoreCase(categoryFieldName)) {
                    categoryValue = item.getStatus();
                } else if ("issueType".equalsIgnoreCase(categoryFieldName)) {
                    categoryValue = item.getIssueType();
                } else {
                    // Attempt to get from fields JSON as a fallback
                    categoryValue = item.getFieldsAsJSON().optString(categoryFieldName, null);
                }
            } catch (IOException e) {
                logger.error("Warning: Could not get category '" + categoryFieldName + "' for item " + item.getKey() + ": " + e.getMessage());
            } catch (Exception e) { // Catch potential JSON exceptions
                logger.error("Warning: Error accessing category field '" + categoryFieldName + "' for item " + item.getKey() + ": " + e.getMessage());
            }

            groupedItems
                .computeIfAbsent(periodKey, k -> new HashMap<>()) // Group by period first
                .computeIfAbsent(categoryValue, k -> new ArrayList<>()) // Then by category value
                .add(item);
        }
        return groupedItems;
    }
}