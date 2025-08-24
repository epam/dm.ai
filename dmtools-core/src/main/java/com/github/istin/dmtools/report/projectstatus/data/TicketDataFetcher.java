package com.github.istin.dmtools.report.projectstatus.data;

import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class TicketDataFetcher {

    private final TrackerClient trackerClient;
    private final String[] completedStatuses;

    public TicketDataFetcher(TrackerClient trackerClient, ReportConfiguration config) {
        this.trackerClient = trackerClient;
        this.completedStatuses = config.getCompletedStatuses();
    }

    public List<ITicket> fetchCompletedTickets(String jql, Calendar startDate, boolean useCreationDate) throws Exception {
        List<ITicket> collectedWorkForPeriod = new ArrayList<>();

        // Search for tickets and collect those completed in the specified period
        trackerClient.searchAndPerform(ticket -> {
            if (useCreationDate) {
                if (DateUtils.calendar(ticket.getCreated()).after(startDate)) {
                    collectedWorkForPeriod.add(ticket);
                }
            } else {
                List<KeyTime> datesWhenTicketWasInStatus = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                        trackerClient, ticket.getKey(), ticket, completedStatuses);

                if (!datesWhenTicketWasInStatus.isEmpty()) {
                    KeyTime first = datesWhenTicketWasInStatus.getFirst();
                    Calendar when = first.getWhen();

                    // Check if it's after start date
                    if (when.after(startDate)) {
                        JSONObject fieldsAsJSON = ticket.getFieldsAsJSON();
                        fieldsAsJSON.put("dateClosed", DateUtils.formatToJiraDate(when));
                        collectedWorkForPeriod.add(ticket);
                    }
                }
            }
            return false;
        }, jql, trackerClient.getExtendedQueryFields());

        return collectedWorkForPeriod;
    }

    public Map<Boolean, List<ITicket>> splitTicketsByType(List<ITicket> tickets) {
        Map<Boolean, List<ITicket>> result = new HashMap<>();
        List<ITicket> bugs = new ArrayList<>();
        List<ITicket> storiesTasks = new ArrayList<>();

        for (ITicket ticket : tickets) {
            try {
                if (IssueType.isBug(ticket.getIssueType())) {
                    bugs.add(ticket);
                } else {
                    storiesTasks.add(ticket);
                }
            } catch (IOException e) {
                System.err.println("Error processing ticket type: " + e.getMessage());
            }
        }

        result.put(true, bugs);
        result.put(false, storiesTasks);
        return result;
    }
}