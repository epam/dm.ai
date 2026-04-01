// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.data.TicketDataFetcher;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.model.TableType;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.tables.*;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class ProjectReportFacade {
    private final TicketDataFetcher dataFetcher;
    private final TicketSorter ticketSorter;
    private final TableFactory tableFactory;

    public ProjectReportFacade(TrackerClient trackerClient, ReportConfiguration config) {
        this.dataFetcher = new TicketDataFetcher(trackerClient, config);
        this.ticketSorter = new TicketSorter(config);
        this.tableFactory = new TableFactory(config);
    }

    public String generateSummaryReport(String jql, Calendar startDate) throws Exception {
        List<ITicket> tickets = dataFetcher.fetchCompletedTickets(jql, startDate,false);
        ticketSorter.sortTickets(tickets);

        StringBuilder report = new StringBuilder();

        // Add summary table
        SummaryTableGenerator summaryGenerator = tableFactory.createSummaryTableGenerator();
        report.append(summaryGenerator.generateSummaryTable(tickets));

        // Add story points distribution
        StoryPointsTableGenerator storyPointsGenerator = tableFactory.createStoryPointsTableGenerator();
        report.append(storyPointsGenerator.generateStoryPointsDistributionTable(tickets));

        // Add role-based distribution
        RoleDistributionTableGenerator roleGenerator = tableFactory.createRoleDistributionTableGenerator();
        report.append(roleGenerator.generateRoleBasedDistributionTable(tickets));

        // Split tickets into bugs and non-bugs
        Map<Boolean, List<ITicket>> splitTickets = dataFetcher.splitTicketsByType(tickets);
        List<ITicket> bugs = splitTickets.get(true);
        List<ITicket> tasksAndStories = splitTickets.get(false);

        // Add bug overview if there are bugs
        if (!bugs.isEmpty()) {
            BugTableGenerator bugGenerator = tableFactory.createBugTableGenerator(false);
            report.append(bugGenerator.generateBugOverviewTable(bugs));
            report.append(bugGenerator.generateBugsTable(bugs));
        }

        // Add tasks and stories table
        if (!tasksAndStories.isEmpty()) {
            TasksAndStoriesTableGenerator tasksGenerator = tableFactory.createTasksAndStoriesTableGenerator();
            report.append(tasksGenerator.generateTasksAndStoriesTable(tasksAndStories));
        }

        // Add role-specific details
        report.append(roleGenerator.generateRoleSpecificTables(tickets));

        return report.toString();
    }

    public String generateCustomReport(String jql, Calendar startDate, List<TableType> tableTypes) throws Exception {
        List<ITicket> tickets = dataFetcher.fetchCompletedTickets(jql, startDate, false);
        ticketSorter.sortTickets(tickets);

        StringBuilder report = new StringBuilder();

        for (TableType type : tableTypes) {
            switch (type) {
                case SUMMARY:
                    SummaryTableGenerator summaryGenerator = tableFactory.createSummaryTableGenerator();
                    report.append(summaryGenerator.generateSummaryTable(tickets));
                    break;
                case STORY_POINTS_DISTRIBUTION:
                    StoryPointsTableGenerator storyPointsGenerator = tableFactory.createStoryPointsTableGenerator();
                    report.append(storyPointsGenerator.generateStoryPointsDistributionTable(tickets));
                    break;
                case ROLE_DISTRIBUTION:
                    RoleDistributionTableGenerator roleGenerator = tableFactory.createRoleDistributionTableGenerator();
                    report.append(roleGenerator.generateRoleBasedDistributionTable(tickets));
                    break;
                case ROLE_SPECIFIC:
                    RoleDistributionTableGenerator roleSpecificGenerator = tableFactory.createRoleDistributionTableGenerator();
                    report.append(roleSpecificGenerator.generateRoleSpecificTables(tickets));
                    break;
                case BUG_OVERVIEW:
                case BUGS_TABLE:
                    Map<Boolean, List<ITicket>> splitTickets = dataFetcher.splitTicketsByType(tickets);
                    List<ITicket> bugs = splitTickets.get(true);
                    if (!bugs.isEmpty()) {
                        BugTableGenerator bugGenerator = tableFactory.createBugTableGenerator(false);
                        if (type == TableType.BUG_OVERVIEW) {
                            report.append(bugGenerator.generateBugOverviewTable(bugs));
                        } else {
                            report.append(bugGenerator.generateBugsTable(bugs));
                        }
                    }
                    break;
                case TASKS_AND_STORIES:
                    Map<Boolean, List<ITicket>> splitForTasks = dataFetcher.splitTicketsByType(tickets);
                    List<ITicket> tasksAndStories = splitForTasks.get(false);
                    if (!tasksAndStories.isEmpty()) {
                        TasksAndStoriesTableGenerator tasksGenerator = tableFactory.createTasksAndStoriesTableGenerator();
                        report.append(tasksGenerator.generateTasksAndStoriesTable(tasksAndStories));
                    }
                    break;
                case TIMELINE:
                    TimelineTableGenerator timelineGenerator = tableFactory.createTimelineTableGenerator();
                    report.append(timelineGenerator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false));
                    break;
            }
        }

        return report.toString();
    }

    public String generateBugReport(String jql, Calendar startDate) throws Exception {
        List<ITicket> tickets = dataFetcher.fetchCompletedTickets(jql, startDate, false);
        Map<Boolean, List<ITicket>> splitTickets = dataFetcher.splitTicketsByType(tickets);
        List<ITicket> bugs = splitTickets.get(true);

        if (bugs.isEmpty()) {
            return "No bugs found in the specified period.";
        }

        ticketSorter.sortTickets(bugs);

        StringBuilder report = new StringBuilder();
        BugTableGenerator bugGenerator = tableFactory.createBugTableGenerator(false);
        report.append(bugGenerator.generateBugOverviewTable(bugs));
        report.append(bugGenerator.generateBugsTable(bugs));

        return report.toString();
    }

    public String generateRoleBasedReport(String jql, Calendar startDate) throws Exception {
        List<ITicket> tickets = dataFetcher.fetchCompletedTickets(jql, startDate, false);
        ticketSorter.sortTickets(tickets);

        StringBuilder report = new StringBuilder();
        RoleDistributionTableGenerator roleGenerator = tableFactory.createRoleDistributionTableGenerator();
        report.append(roleGenerator.generateRoleBasedDistributionTable(tickets));
        report.append(roleGenerator.generateRoleSpecificTables(tickets));

        return report.toString();
    }

    public String generateTimelineReport(String jql, Calendar startDate, TimelinePeriod period) throws Exception {
        List<ITicket> tickets = dataFetcher.fetchCompletedTickets(jql, startDate, false);
        ticketSorter.sortTickets(tickets);

        StringBuilder report = new StringBuilder();
        report.append("# Deliverables Timeline Report\n\n");

        TimelineTableGenerator timelineGenerator = tableFactory.createTimelineTableGenerator();
        report.append(timelineGenerator.generateTimelineTable(tickets, period, true));

        return report.toString();
    }

    /**
     * Generates a label analysis report
     *
     * @param jql JQL query to fetch tickets
     * @param startDate Start date for the report
     * @param period Time period for timeline analysis
     * @param focusLabels Optional list of specific labels to focus on (null for all labels)
     * @return A formatted report with label analysis
     */
    public String generateLabelAnalysisReport(String jql, Calendar startDate, TimelinePeriod period, List<String> focusLabels) throws Exception {
        List<ITicket> tickets = dataFetcher.fetchCompletedTickets(jql, startDate, false);
        ticketSorter.sortTickets(tickets);

        LabelAnalysisGenerator labelAnalysisGenerator = tableFactory.createLabelAnalysisGenerator();
        return labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);
    }
}