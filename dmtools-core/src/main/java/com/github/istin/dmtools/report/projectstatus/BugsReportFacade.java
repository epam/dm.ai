// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.data.TicketDataFetcher;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.tables.BugTableGenerator;
import com.github.istin.dmtools.report.projectstatus.presentation.tables.TableFactory;
import com.github.istin.dmtools.report.projectstatus.presentation.tables.TimelineTableGenerator;

import java.util.*;

import static com.github.istin.dmtools.report.projectstatus.BugsReportFacade.ReportType.CREATED_TIMELINE_BY_PRIORITY;

/**
 * Facade class responsible for generating various reports focused on bugs.
 */
public class BugsReportFacade {

    /**
     * Enum representing the different types of sections that can be included in a bug report,
     * differentiating between data fetched based on completion date vs. creation date.
     */
    public enum ReportType {
        COMPLETED_OVERVIEW, COMPLETED_DETAILS, COMPLETED_TIMELINE, // Based on completed tickets within the period
        CREATED_OVERVIEW, CREATED_DETAILS, CREATED_TIMELINE_BY_PRIORITY // Based on created tickets within the period
    }

    public static final ReportType[] ALL_TYPES_EXCEPT_DETAILS =
            EnumSet.complementOf(EnumSet.of(ReportType.COMPLETED_DETAILS, ReportType.CREATED_DETAILS, ReportType.CREATED_OVERVIEW, ReportType.CREATED_TIMELINE_BY_PRIORITY))
                   .toArray(new ReportType[0]);

    private final TicketDataFetcher dataFetcher;
    private final TicketSorter ticketSorter;
    private final TableFactory tableFactory;

    public BugsReportFacade(TrackerClient trackerClient, ReportConfiguration config) {
        this.dataFetcher = new TicketDataFetcher(trackerClient, config);
        this.ticketSorter = new TicketSorter(config);
        this.tableFactory = new TableFactory(config);
    }

    /**
     * Generates a comprehensive bug report including an overview, a detailed table, and a creation timeline.
     *
     * @param jql       The JQL query to fetch tickets.
     * @param startDate The start date for the report period.
     * @param timelinePeriod The period for grouping in the timeline (e.g., MONTH, WEEK).
     * @param countStoryPoints If true, count story points; otherwise, count individual tickets.
     * @param types Optional varargs specifying which report sections to include. If null or empty, all sections are generated.
     * @return A string containing the formatted bug report.
     * @throws Exception If there's an error fetching data or generating the report.
     */
    public String generateBugReport(String jql, Calendar startDate, TimelinePeriod timelinePeriod, boolean countStoryPoints, ReportType... types) throws Exception {
        Set<ReportType> requestedTypes;
        if (types == null || types.length == 0) {
            // Default to a standard set if none are specified
            requestedTypes = EnumSet.of(ReportType.COMPLETED_OVERVIEW, ReportType.COMPLETED_DETAILS, ReportType.COMPLETED_TIMELINE, CREATED_TIMELINE_BY_PRIORITY);
        } else {
            requestedTypes = EnumSet.copyOf(Arrays.asList(types));
        }

        // Determine which data sets are needed
        boolean needsCompleted = requestedTypes.contains(ReportType.COMPLETED_OVERVIEW) ||
                                 requestedTypes.contains(ReportType.COMPLETED_DETAILS) ||
                                 requestedTypes.contains(ReportType.COMPLETED_TIMELINE);

        boolean needsCreated = requestedTypes.contains(ReportType.CREATED_OVERVIEW) ||
                               requestedTypes.contains(ReportType.CREATED_DETAILS) ||
                               requestedTypes.contains(CREATED_TIMELINE_BY_PRIORITY);

        List<ITicket> bugsCompleted = null;
        List<ITicket> bugsCreated = null;

        // Fetch completed tickets if needed
        if (needsCompleted) {
            List<ITicket> allClosedTickets = dataFetcher.fetchCompletedTickets(jql, startDate, false);
            Map<Boolean, List<ITicket>> splitTickets = dataFetcher.splitTicketsByType(allClosedTickets);
            bugsCompleted = splitTickets.getOrDefault(true, Collections.emptyList());
            if (!bugsCompleted.isEmpty()) {
                ticketSorter.sortTickets(bugsCompleted);
            }
        }

        // Fetch created tickets if needed
        if (needsCreated) {
            List<ITicket> allTickets = dataFetcher.fetchCompletedTickets(jql, startDate, true); // true indicates fetch based on creation
            Map<Boolean, List<ITicket>> splitted = dataFetcher.splitTicketsByType(allTickets);
            bugsCreated = splitted.getOrDefault(true, Collections.emptyList());
             if (!bugsCreated.isEmpty()) {
                 ticketSorter.sortTickets(bugsCreated);
             }
        }

        // Generate the report sections based on requested types and available data
        StringBuilder report = new StringBuilder();
        BugTableGenerator bugGenerator = tableFactory.createBugTableGenerator(countStoryPoints);
        TimelineTableGenerator timelineGenerator = tableFactory.createTimelineTableGenerator();
        boolean addedSection = false; // To manage spacing between sections

        TimelinePeriod effectiveTimelinePeriod = (timelinePeriod == null) ? TimelinePeriod.MONTH : timelinePeriod;

        // --- Sections based on COMPLETED bugs --- 

        if (bugsCompleted != null && !bugsCompleted.isEmpty()) {
            if (requestedTypes.contains(ReportType.COMPLETED_OVERVIEW)) {
                report.append(bugGenerator.generateBugOverviewTable(bugsCompleted));
                addedSection = true;
            }

            if (requestedTypes.contains(ReportType.COMPLETED_DETAILS)) {
                if (addedSection) report.append("\n\n");
                report.append(bugGenerator.generateBugsTable(bugsCompleted));
                addedSection = true;
            }

            if (requestedTypes.contains(ReportType.COMPLETED_TIMELINE)) {
                if (addedSection) report.append("\n\n");
                report.append(timelineGenerator.generateTimelineTable(bugsCompleted, effectiveTimelinePeriod, countStoryPoints));
                addedSection = true;
            }
        } else if (needsCompleted) {
             if (addedSection) report.append("\n\n");
             report.append("No *completed* bugs found matching the criteria for the specified period.");
             addedSection = true;
        }

        // --- Sections based on CREATED bugs --- 

        if (bugsCreated != null && !bugsCreated.isEmpty()) {
             if (requestedTypes.contains(ReportType.CREATED_OVERVIEW)) {
                 if (addedSection) report.append("\n\n");
                 report.append("(Based on Creation Date)\n"); // Clarify data source
                 report.append(bugGenerator.generateBugOverviewTable(bugsCreated));
                 addedSection = true;
             }

             if (requestedTypes.contains(ReportType.CREATED_DETAILS)) {
                 if (addedSection) report.append("\n\n");
                 report.append("(Based on Creation Date)\n"); // Clarify data source
                 report.append(bugGenerator.generateBugsTable(bugsCreated));
                 addedSection = true;
             }

            if (requestedTypes.contains(ReportType.CREATED_TIMELINE_BY_PRIORITY)) {
                if (addedSection) report.append("\n\n");
                report.append(timelineGenerator.generateTimelineByCreationAndCategory(bugsCreated, effectiveTimelinePeriod, "priority", "Priority", countStoryPoints));
                // addedSection = true; // No need to set for the last possible section
            }
        } else if (needsCreated && requestedTypes.contains(CREATED_TIMELINE_BY_PRIORITY)) { // Only report 'not found' for the timeline part as others are optional additions
            if (addedSection) report.append("\n\n");
            report.append("No bugs *created* matching the criteria for the specified period to generate Timeline by Priority.");
           // addedSection = true; 
        }
        
        if (report.length() == 0) {
             return "No reports requested or no bugs found for the requested report types.";
        }

        return report.toString();
    }

} 