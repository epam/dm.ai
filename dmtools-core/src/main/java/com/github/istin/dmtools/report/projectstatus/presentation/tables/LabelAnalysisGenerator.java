// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates comprehensive analysis reports for specified ticket labels.
 */
public class LabelAnalysisGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LabelAnalysisGenerator.class);
    private static final int CHART_HEIGHT = 15;
    private static final int MAX_LABELS_IN_CHART = 5;
    private static final char[] CHART_SYMBOLS = {'*', '+', 'o', 'x', '#'};
    private static final String UNLABELED_CATEGORY = "Unlabeled";
    private static final String OTHER_LABELS_CATEGORY = "Other Labels";

    private final TableGenerator tableGenerator;
    private final TimelineTableGenerator timelineTableGenerator;

    /**
     * Constructs a new LabelAnalysisGenerator with the specified table generators.
     *
     * @param tableGenerator The table generator to use for creating tables
     * @param timelineTableGenerator The timeline table generator for time-based analysis
     */
    public LabelAnalysisGenerator(TableGenerator tableGenerator, TimelineTableGenerator timelineTableGenerator) {
        this.tableGenerator = tableGenerator;
        this.timelineTableGenerator = timelineTableGenerator;
    }

    /**
     * Generates a comprehensive label analysis report for the specified labels.
     *
     * @param tickets List of tickets to analyze
     * @param period Time period for timeline analysis
     * @param focusLabels List of specific labels to analyze (must not be null or empty)
     * @return A formatted report with label analysis
     * @throws IllegalArgumentException if focusLabels is null or empty
     */
    public String generateLabelAnalysis(List<ITicket> tickets, TimelinePeriod period, List<String> focusLabels) {
        if (focusLabels == null || focusLabels.isEmpty()) {
            throw new IllegalArgumentException("Focus labels list must not be null or empty");
        }

        StringBuilder report = new StringBuilder();
        report.append("# Ticket Label Analysis\n\n");

        // Convert focus labels to a set for efficient lookups
        Set<String> labelsToAnalyze = new HashSet<>(focusLabels);

        // Categorize tickets
        Map<String, List<ITicket>> categorizedTickets = categorizeTickets(tickets, labelsToAnalyze);
        List<ITicket> unlabeledTickets = categorizedTickets.get(UNLABELED_CATEGORY);
        List<ITicket> otherLabeledTickets = categorizedTickets.get(OTHER_LABELS_CATEGORY);

        // Generate overview table with all categories
        report.append(generateLabelOverviewTable(tickets, labelsToAnalyze, unlabeledTickets, otherLabeledTickets));
        report.append("\n\n");

        // Generate pie chart data with all categories
        report.append(generateLabelPieChartData(tickets, labelsToAnalyze, unlabeledTickets, otherLabeledTickets));
        report.append("\n\n");

        // Generate timeline dynamics with all categories
        //report.append(generateLabelTimelineDynamics(tickets, labelsToAnalyze, period, unlabeledTickets, otherLabeledTickets));

        return report.toString();
    }

    /**
     * Categorizes tickets into focus labels, unlabeled, and other labels categories.
     */
    private Map<String, List<ITicket>> categorizeTickets(List<ITicket> tickets, Set<String> labelsToAnalyze) {
        Map<String, List<ITicket>> result = new HashMap<>();
        List<ITicket> unlabeledTickets = new ArrayList<>();
        List<ITicket> otherLabeledTickets = new ArrayList<>();

        for (ITicket ticket : tickets) {
            boolean matchesFocusLabel = false;
            boolean hasAnyLabel = false;

            try {
                JSONArray labels = ticket.getTicketLabels();

                if (labels != null && labels.length() > 0) {
                    hasAnyLabel = true;

                    // Check if ticket has any of the focus labels
                    for (int i = 0; i < labels.length(); i++) {
                        String label = labels.getString(i);
                        if (labelsToAnalyze.contains(label)) {
                            matchesFocusLabel = true;
                            break;
                        }
                    }
                }

                // Categorize the ticket
                if (!matchesFocusLabel) {
                    if (!hasAnyLabel) {
                        unlabeledTickets.add(ticket);
                    } else {
                        otherLabeledTickets.add(ticket);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error processing ticket labels: {}", e.getMessage(), e);
            }
        }

        result.put(UNLABELED_CATEGORY, unlabeledTickets);
        result.put(OTHER_LABELS_CATEGORY, otherLabeledTickets);
        return result;
    }

    /**
     * Generates an overview table showing counts and story points by label, including unlabeled and other labels.
     */
    private String generateLabelOverviewTable(List<ITicket> tickets, Set<String> labelsToAnalyze,
                                              List<ITicket> unlabeledTickets, List<ITicket> otherLabeledTickets) {
        List<String> headers = Arrays.asList(
                "Label", "Ticket Count", "Story Points", "Avg Points per Ticket", "% of Total Tickets", "% of Total Points"
        );

        TableData tableData = new TableData(
                "Label Overview",
                headers,
                "Summary of tickets and story points by label."
        );

        // Calculate totals for percentages
        int totalTickets = tickets.size();
        double totalPoints = tickets.stream().mapToDouble(ITicket::getWeight).sum();

        // Count tickets and points by label
        Map<String, Integer> labelCounts = new HashMap<>();
        Map<String, Double> labelPoints = new HashMap<>();

        for (ITicket ticket : tickets) {
            try {
                JSONArray labels = ticket.getTicketLabels();
                if (labels != null) {
                    double ticketPoints = ticket.getWeight();

                    for (int i = 0; i < labels.length(); i++) {
                        String label = labels.getString(i);
                        if (labelsToAnalyze.contains(label)) {
                            // Update counts
                            labelCounts.put(label, labelCounts.getOrDefault(label, 0) + 1);

                            // Update points
                            labelPoints.put(label, labelPoints.getOrDefault(label, 0.0) + ticketPoints);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error processing ticket labels: {}", e.getMessage(), e);
            }
        }

        // Sort labels by count (descending)
        List<String> sortedLabels = new ArrayList<>(labelsToAnalyze);
        sortedLabels.sort((l1, l2) -> Integer.compare(
                labelCounts.getOrDefault(l2, 0),
                labelCounts.getOrDefault(l1, 0)
        ));

        // Add data rows for focus labels
        for (String label : sortedLabels) {
            int count = labelCounts.getOrDefault(label, 0);
            double points = labelPoints.getOrDefault(label, 0.0);
            double avgPoints = count > 0 ? points / count : 0;
            double countPercent = totalTickets > 0 ? (count * 100.0 / totalTickets) : 0;
            double pointsPercent = totalPoints > 0 ? (points * 100.0 / totalPoints) : 0;

            List<String> row = Arrays.asList(
                    label,
                    String.valueOf(count),
                    String.format("%.1f", points),
                    String.format("%.2f", avgPoints),
                    String.format("%.1f%%", countPercent),
                    String.format("%.1f%%", pointsPercent)
            );

            tableData.addRow(row);
        }

        // Add unlabeled tickets row
        int unlabeledCount = unlabeledTickets.size();
        double unlabeledPoints = unlabeledTickets.stream().mapToDouble(ITicket::getWeight).sum();
        double unlabeledAvgPoints = unlabeledCount > 0 ? unlabeledPoints / unlabeledCount : 0;
        double unlabeledCountPercent = totalTickets > 0 ? (unlabeledCount * 100.0 / totalTickets) : 0;
        double unlabeledPointsPercent = totalPoints > 0 ? (unlabeledPoints * 100.0 / totalPoints) : 0;

        List<String> unlabeledRow = Arrays.asList(
                UNLABELED_CATEGORY,
                String.valueOf(unlabeledCount),
                String.format("%.1f", unlabeledPoints),
                String.format("%.2f", unlabeledAvgPoints),
                String.format("%.1f%%", unlabeledCountPercent),
                String.format("%.1f%%", unlabeledPointsPercent)
        );
        tableData.addRow(unlabeledRow);

        // Add other labeled tickets row
        int otherLabeledCount = otherLabeledTickets.size();
        double otherLabeledPoints = otherLabeledTickets.stream().mapToDouble(ITicket::getWeight).sum();
        double otherLabeledAvgPoints = otherLabeledCount > 0 ? otherLabeledPoints / otherLabeledCount : 0;
        double otherLabeledCountPercent = totalTickets > 0 ? (otherLabeledCount * 100.0 / totalTickets) : 0;
        double otherLabeledPointsPercent = totalPoints > 0 ? (otherLabeledPoints * 100.0 / totalPoints) : 0;

        List<String> otherLabeledRow = Arrays.asList(
                OTHER_LABELS_CATEGORY,
                String.valueOf(otherLabeledCount),
                String.format("%.1f", otherLabeledPoints),
                String.format("%.2f", otherLabeledAvgPoints),
                String.format("%.1f%%", otherLabeledCountPercent),
                String.format("%.1f%%", otherLabeledPointsPercent)
        );
        tableData.addRow(otherLabeledRow);

        // Add totals row
        List<String> totalsRow = Arrays.asList(
                "**Total**",
                String.valueOf(totalTickets),
                String.format("%.1f", totalPoints),
                String.format("%.2f", totalTickets > 0 ? totalPoints / totalTickets : 0),
                "100.0%",
                "100.0%"
        );
        tableData.addRow(totalsRow);

        // Add note about ticket counting
        tableData.setFootnote("Note: Tickets may have multiple labels and are counted once per label.");

        return tableGenerator.generateTable(tableData);
    }


    /**
     * Filters tickets that contain a specific label.
     */
    private List<ITicket> filterTicketsByLabel(List<ITicket> tickets, String label) {
        return tickets.stream()
                .filter(ticket -> {
                    try {
                        JSONArray labels = ticket.getTicketLabels();
                        if (labels != null) {
                            for (int i = 0; i < labels.length(); i++) {
                                if (label.equals(labels.getString(i))) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    } catch (Exception e) {
                        LOGGER.warn("Error checking ticket label: {}", e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Generates pie chart data for label distribution, including unlabeled and other labels.
     */
    private String generateLabelPieChartData(List<ITicket> tickets, Set<String> labelsToAnalyze,
                                             List<ITicket> unlabeledTickets, List<ITicket> otherLabeledTickets) {
        StringBuilder result = new StringBuilder();
        result.append("## Label Distribution\n\n");

        // Count tickets by label
        Map<String, Integer> labelCounts = new HashMap<>();
        Map<String, Double> labelPoints = new HashMap<>();

        for (ITicket ticket : tickets) {
            try {
                JSONArray labels = ticket.getTicketLabels();
                if (labels != null) {
                    double ticketPoints = ticket.getWeight();

                    for (int i = 0; i < labels.length(); i++) {
                        String label = labels.getString(i);
                        if (labelsToAnalyze.contains(label)) {
                            // Update counts
                            labelCounts.put(label, labelCounts.getOrDefault(label, 0) + 1);

                            // Update points
                            labelPoints.put(label, labelPoints.getOrDefault(label, 0.0) + ticketPoints);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error processing ticket labels: {}", e.getMessage(), e);
            }
        }

        // Add unlabeled and other labels categories
        labelCounts.put(UNLABELED_CATEGORY, unlabeledTickets.size());
        labelCounts.put(OTHER_LABELS_CATEGORY, otherLabeledTickets.size());

        labelPoints.put(UNLABELED_CATEGORY, unlabeledTickets.stream().mapToDouble(ITicket::getWeight).sum());
        labelPoints.put(OTHER_LABELS_CATEGORY, otherLabeledTickets.stream().mapToDouble(ITicket::getWeight).sum());

        // Sort labels by count (descending)
        List<String> sortedLabels = new ArrayList<>(labelsToAnalyze);
        sortedLabels.add(UNLABELED_CATEGORY);
        sortedLabels.add(OTHER_LABELS_CATEGORY);

        sortedLabels.sort((l1, l2) -> Integer.compare(
                labelCounts.getOrDefault(l2, 0),
                labelCounts.getOrDefault(l1, 0)
        ));

        // Generate ASCII pie chart for ticket counts
        result.append("### Ticket Count by Label\n\n");
        result.append("```\n");

        int totalTickets = tickets.size();
        int maxLabelLength = sortedLabels.stream()
                .mapToInt(String::length)
                .max()
                .orElse(10);

        // Format: Label | Percentage | Bar
        result.append(String.format("%-" + maxLabelLength + "s | %7s | %s\n", "Label", "Count", "Distribution"));
        result.append(String.format("%-" + maxLabelLength + "s-|-%7s-|-%s\n",
                "-".repeat(maxLabelLength), "-------", "--------------------"));

        for (String label : sortedLabels) {
            int count = labelCounts.getOrDefault(label, 0);
            double percentage = totalTickets > 0 ? (count * 100.0 / totalTickets) : 0;

            // Create a bar representing the percentage (max 20 chars)
            int barLength = (int) Math.round(percentage / 5.0); // 5% per character
            String bar = "█".repeat(barLength);

            result.append(String.format("%-" + maxLabelLength + "s | %7d | %s %.1f%%\n",
                    label, count, bar, percentage));
        }

        result.append("```\n\n");

        // Generate ASCII pie chart for story points
        result.append("### Story Points by Label\n\n");
        result.append("```\n");

        double totalPoints = tickets.stream().mapToDouble(ITicket::getWeight).sum();

        // Format: Label | Points | Percentage | Bar
        result.append(String.format("%-" + maxLabelLength + "s | %7s | %7s | %s\n",
                "Label", "Points", "Percent", "Distribution"));
        result.append(String.format("%-" + maxLabelLength + "s-|-%7s-|-%7s-|-%s\n",
                "-".repeat(maxLabelLength), "-------", "-------", "--------------------"));

        for (String label : sortedLabels) {
            double points = labelPoints.getOrDefault(label, 0.0);
            double percentage = totalPoints > 0 ? (points * 100.0 / totalPoints) : 0;

            // Create a bar representing the percentage (max 20 chars)
            int barLength = (int) Math.round(percentage / 5.0); // 5% per character
            String bar = "█".repeat(barLength);

            result.append(String.format("%-" + maxLabelLength + "s | %7.1f | %6.1f%% | %s\n",
                    label, points, percentage, bar));
        }

        result.append("```\n");

        return result.toString();
    }

    /**
     * Generates timeline dynamics for labels over time, including unlabeled and other labels.
     */
    private String generateLabelTimelineDynamics(List<ITicket> tickets, Set<String> labelsToAnalyze,
                                                 TimelinePeriod period, List<ITicket> unlabeledTickets,
                                                 List<ITicket> otherLabeledTickets) {
        StringBuilder result = new StringBuilder();
        result.append("## Label Timeline Dynamics (" + period.getDescription() + ")\n\n");

        // Create a map of tickets by label, including special categories
        Map<String, List<ITicket>> ticketsByLabel = new HashMap<>();

        // Add focus labels
        for (String label : labelsToAnalyze) {
            ticketsByLabel.put(label, filterTicketsByLabel(tickets, label));
        }

        // Add special categories
        ticketsByLabel.put(UNLABELED_CATEGORY, unlabeledTickets);
        ticketsByLabel.put(OTHER_LABELS_CATEGORY, otherLabeledTickets);

        // Group tickets by time period
        Map<String, List<ITicket>> ticketsByPeriod = groupTicketsByPeriod(tickets, period);

        // Get sorted list of periods
        List<String> sortedPeriods = new ArrayList<>(ticketsByPeriod.keySet());
        Collections.sort(sortedPeriods);

        // Generate timeline tables

        // Generate label counts timeline table
        result.append(generateLabelCountTimeline(ticketsByPeriod, sortedPeriods, ticketsByLabel, period));
        result.append("\n\n");

        // Generate label points timeline table
        result.append(generateLabelPointsTimeline(ticketsByPeriod, sortedPeriods, ticketsByLabel, period));

        return result.toString();
    }

    /**
     * Generates a timeline table showing label counts over time.
     */
    private String generateLabelCountTimeline(Map<String, List<ITicket>> ticketsByPeriod,
                                              List<String> sortedPeriods,
                                              Map<String, List<ITicket>> ticketsByLabel,
                                              TimelinePeriod period) {
        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Time Period");

        // Sort labels for consistent column order (focus labels first, then special categories)
        List<String> sortedLabels = new ArrayList<>(ticketsByLabel.keySet());
        sortedLabels.remove(UNLABELED_CATEGORY);
        sortedLabels.remove(OTHER_LABELS_CATEGORY);
        Collections.sort(sortedLabels);

        // Add special categories at the end
        sortedLabels.add(UNLABELED_CATEGORY);
        sortedLabels.add(OTHER_LABELS_CATEGORY);

        headers.addAll(sortedLabels);
        headers.add("Total");

        TableData tableData = new TableData(
                "Label Counts Timeline (" + period.getDescription() + ")",
                headers,
                "Shows the number of tickets by label over time periods."
        );

        // Add data rows
        Map<String, Integer> labelTotals = new HashMap<>();
        int grandTotal = 0;

        for (String periodKey : sortedPeriods) {
            List<ITicket> periodTickets = ticketsByPeriod.get(periodKey);

            List<String> row = new ArrayList<>();
            row.add(periodKey);

            int periodTotal = 0;

            // Count tickets by label for this period
            Map<String, Integer> periodLabelCounts = countTicketsByLabelInPeriod(periodTickets, ticketsByLabel);

            // Add counts for each label
            for (String label : sortedLabels) {
                int count = periodLabelCounts.getOrDefault(label, 0);
                row.add(String.valueOf(count));
                periodTotal += count;

                // Update label totals
                labelTotals.put(label, labelTotals.getOrDefault(label, 0) + count);
            }

            row.add(String.valueOf(periodTotal));
            tableData.addRow(row);

            // Update grand total
            grandTotal += periodTotal;
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");

        for (String label : sortedLabels) {
            totalsRow.add(String.valueOf(labelTotals.getOrDefault(label, 0)));
        }

        totalsRow.add(String.valueOf(grandTotal));
        tableData.addRow(totalsRow);

        return tableGenerator.generateTable(tableData);
    }

    /**
     * Generates a timeline table showing label story points over time.
     */
    private String generateLabelPointsTimeline(Map<String, List<ITicket>> ticketsByPeriod,
                                               List<String> sortedPeriods,
                                               Map<String, List<ITicket>> ticketsByLabel,
                                               TimelinePeriod period) {
        // Create table headers
        List<String> headers = new ArrayList<>();
        headers.add("Time Period");

        // Sort labels for consistent column order (focus labels first, then special categories)
        List<String> sortedLabels = new ArrayList<>(ticketsByLabel.keySet());
        sortedLabels.remove(UNLABELED_CATEGORY);
        sortedLabels.remove(OTHER_LABELS_CATEGORY);
        Collections.sort(sortedLabels);

        // Add special categories at the end
        sortedLabels.add(UNLABELED_CATEGORY);
        sortedLabels.add(OTHER_LABELS_CATEGORY);

        // Add points columns for each label
        for (String label : sortedLabels) {
            headers.add(label + " Points");
        }

        headers.add("Total Points");

        TableData tableData = new TableData(
                "Label Story Points Timeline (" + period.getDescription() + ")",
                headers,
                "Shows the distribution of story points by label over time periods."
        );

        // Add data rows
        Map<String, Double> labelPointsTotals = new HashMap<>();
        double grandTotalPoints = 0;

        for (String periodKey : sortedPeriods) {
            List<ITicket> periodTickets = ticketsByPeriod.get(periodKey);

            List<String> row = new ArrayList<>();
            row.add(periodKey);

            double periodTotalPoints = 0;

            // Calculate story points by label for this period
            Map<String, Double> periodLabelPoints = calculatePointsByLabelInPeriod(periodTickets, ticketsByLabel);

            // Add story points for each label
            for (String label : sortedLabels) {
                double points = periodLabelPoints.getOrDefault(label, 0.0);
                row.add(String.format("%.1f", points));
                periodTotalPoints += points;

                // Update label totals
                labelPointsTotals.put(label, labelPointsTotals.getOrDefault(label, 0.0) + points);
            }

            row.add(String.format("%.1f", periodTotalPoints));
            tableData.addRow(row);

            // Update grand total
            grandTotalPoints += periodTotalPoints;
        }

        // Add totals row
        List<String> totalsRow = new ArrayList<>();
        totalsRow.add("**Total**");

        for (String label : sortedLabels) {
            totalsRow.add(String.format("%.1f", labelPointsTotals.getOrDefault(label, 0.0)));
        }

        totalsRow.add(String.format("%.1f", grandTotalPoints));
        tableData.addRow(totalsRow);

        return tableGenerator.generateTable(tableData);
    }

    /**
     * Counts tickets by label for a specific period.
     */
    private Map<String, Integer> countTicketsByLabelInPeriod(List<ITicket> periodTickets,
                                                             Map<String, List<ITicket>> ticketsByLabel) {
        Map<String, Integer> result = new HashMap<>();

        // For each label, count how many of its tickets are in this period
        for (Map.Entry<String, List<ITicket>> entry : ticketsByLabel.entrySet()) {
            String label = entry.getKey();
            List<ITicket> labelTickets = entry.getValue();

            // Count tickets that are both in this label and in this period
            int count = 0;
            for (ITicket ticket : labelTickets) {
                if (periodTickets.contains(ticket)) {
                    count++;
                }
            }

            result.put(label, count);
        }

        return result;
    }

    /**
     * Calculates story points by label for a specific period.
     */
    private Map<String, Double> calculatePointsByLabelInPeriod(List<ITicket> periodTickets,
                                                               Map<String, List<ITicket>> ticketsByLabel) {
        Map<String, Double> result = new HashMap<>();

        // For each label, sum the points of its tickets that are in this period
        for (Map.Entry<String, List<ITicket>> entry : ticketsByLabel.entrySet()) {
            String label = entry.getKey();
            List<ITicket> labelTickets = entry.getValue();

            // Sum points for tickets that are both in this label and in this period
            double points = 0.0;
            for (ITicket ticket : labelTickets) {
                if (periodTickets.contains(ticket)) {
                    points += ticket.getWeight();
                }
            }

            result.put(label, points);
        }

        return result;
    }

    /**
     * Groups tickets by time period.
     */
    private Map<String, List<ITicket>> groupTicketsByPeriod(List<ITicket> tickets, TimelinePeriod period) {
        Map<String, List<ITicket>> result = new TreeMap<>();

        for (ITicket ticket : tickets) {
            try {
                // Get the closed date
                String closedDateStr = ticket.getFieldsAsJSON().getString("dateClosed");
                Date closedDate = parseDate(closedDateStr);

                if (closedDate == null) {
                    continue;
                }

                // Format the period key
                String periodKey = formatDateToPeriod(closedDate, period);

                // Add to the appropriate period
                result.computeIfAbsent(periodKey, k -> new ArrayList<>()).add(ticket);

            } catch (Exception e) {
                LOGGER.warn("Error processing ticket for timeline: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Attempts to parse a date string using multiple common formats.
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

        LOGGER.warn("Could not parse date: {}", dateStr);
        return null;
    }

    /**
     * Formats a date to the appropriate period string.
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
}