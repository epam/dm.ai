package com.github.istin.dmtools.atlassian.jira.utils;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ChangelogAssessment {
    private static final Logger logger = LogManager.getLogger(ChangelogAssessment.class);
    public static List<KeyTime> findCreatedDate(JiraClient jira, String key) throws IOException {
        Ticket ticket = jira.performTicket(key, new String[]{"created", "creator"});
        List<KeyTime> result = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(ticket.getFields().getCreated());
        KeyTime keyTime = new KeyTime(ticket.getKey(), calendar, ticket.getFields().getCreator().getDisplayName());
        result.add(keyTime);
        return result;
    }

    public static List<KeyTime> findDatesWhenTicketWasInStatus(TrackerClient jira, String key, ITicket ticket, String... statuses) throws IOException {
        return findDatesWhenTicketWasInStatus(null, null, false, jira, key, ticket, statuses);
    }

    public static List<KeyTime> findDatesWhenTicketWasInStatus(String customName, boolean isSetWeight, TrackerClient jira, String key, ITicket ticket, String... statuses) throws IOException {
        return findDatesWhenTicketWasInStatus(null, customName, isSetWeight, jira, key, ticket, statuses);
    }


    public static List<KeyTime> findDatesWhenTicketWasInStatus(String ignoreIfStatus, String customName, boolean isSetWeight, TrackerClient trackerClient, String key, ITicket ticket, String... statuses) throws IOException {
        IChangelog changeLog = trackerClient.getChangeLog(key, ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        List<KeyTime> result = new ArrayList<>();
        boolean ifStatusChanged = false;

        IUser firstAuthor = null;
        for (IHistory history : histories) {
            if (firstAuthor == null) {
                firstAuthor = history.getAuthor();
            }
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().equalsIgnoreCase(trackerClient.getDefaultStatusField())) {
                    String toString = historyItem.getToAsString();
                    if (toString != null) {
                        ifStatusChanged = true;
                        if (ignoreIfStatus != null && ignoreIfStatus.equalsIgnoreCase(toString)) {
                            result.clear();
                        }
                        for (String status : statuses) {
                            if (toString.equalsIgnoreCase(status)) {
                                Calendar created = history.getCreated();
                                String displayName = history.getAuthor().getFullName();
                                KeyTime keyTime = new KeyTime(key, created, customName != null ? customName : displayName);
                                if (isSetWeight) {
                                    keyTime.setWeight(ticket.getWeight());
                                }
                                result.add(keyTime);
                            }
                        }
                    }
                }
            }
        }
        if (!ifStatusChanged) {
            ITicket latestStatusTicket = trackerClient.performTicket(key, trackerClient.getDefaultQueryFields());
            String initialStatus = latestStatusTicket.getStatus();
            Calendar createdCalendar = Calendar.getInstance();
            createdCalendar.setTime(latestStatusTicket.getCreated());
            for (String status : statuses) {
                if (initialStatus.equalsIgnoreCase(status)) {
                    IUser creator = latestStatusTicket.getCreator();
                    if (creator == null) {
                        creator = firstAuthor;
                    }
                    KeyTime keyTime = new KeyTime(key, createdCalendar, creator != null ? creator.getFullName() : null);
                    if (isSetWeight) {
                        keyTime.setWeight(ticket.getWeight());
                    }
                    result.add(keyTime);
                }
            }
        }

        return result;
    }

    public static Pair<IUser, IHistoryItem> findSourceStatusForRequestedOne(TrackerClient trackerClient, String key, ITicket ticket, String targetStatus) throws IOException {
        IChangelog changeLog = trackerClient.getChangeLog(key, ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().equalsIgnoreCase(trackerClient.getDefaultStatusField())) {
                    String toString = historyItem.getToAsString();
                    if (toString != null) {
                        if (toString.equalsIgnoreCase(targetStatus)) {
                            return new ImmutablePair<>(history.getAuthor(), historyItem);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String findWhoIsResponsible(TrackerClient trackerClient, Employees employees, ITicket ticket, String ... inProgressStatuses) throws IOException {
        Pair<String, IHistoryItem> lastAssigneeForStatus = ChangelogAssessment.findLastAssigneeForStatus(trackerClient, ticket.getKey(), ticket, employees, inProgressStatuses);
        String result = lastAssigneeForStatus.getKey();
        if (result == null) {
            result = ChangelogAssessment.findWhoFromEmployeeMovedToStatus(trackerClient, ticket.getKey(), ticket, employees, inProgressStatuses);
        }
        if (result == null) {
            return Employees.UNKNOWN;
        }
        return result;
    }

    public static String findWhoFromEmployeeMovedToStatus(TrackerClient trackerClient, String key, ITicket ticket,  IEmployees teamToFilter, String... targetStatuses) throws IOException {
        IChangelog changeLog = trackerClient.getChangeLog(key, ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        String lastAssignee = null;
        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().equalsIgnoreCase(trackerClient.getDefaultStatusField())) {
                    String toString = historyItem.getToAsString();
                    if (toString != null) {
                        for (String targetStatus : targetStatuses) {
                            if (toString.equalsIgnoreCase(targetStatus)) {
                                String fullName = history.getAuthor().getFullName();
                                if (teamToFilter != null) {
                                    if (teamToFilter.contains(fullName)) {
                                        lastAssignee = teamToFilter.transformName(fullName);
                                    }
                                } else {
                                    lastAssignee = fullName;
                                }
                            }
                        }
                    }
                }
            }
        }
        return lastAssignee;
    }

    public static String whoReportedTheTicket(Ticket ticket, IEmployees employees) {
        String who = null;
        Assignee creator = ticket.getFields().getCreator();
        Assignee reporter = ticket.getFields().getReporter();
        if (employees != null) {
            String creatorDisplayName = creator.getDisplayName();
            if (employees.contains(creatorDisplayName)) {
                who = creatorDisplayName;
            } else if (reporter != null) {
                String reporterDisplayName = reporter.getDisplayName();
                if (employees.contains(reporterDisplayName)) {
                    who = reporterDisplayName;
                }
            }
        }
        if (who == null && employees != null) {
            who = IEmployees.UNKNOWN;
        }
        return who;
    }

    public static Pair<String, IHistoryItem> findLastAssigneeForStatus(TrackerClient trackerClient, String key, ITicket ticket, IEmployees teamToFilter, String... targetStatuses) throws IOException {
        IChangelog changeLog = trackerClient.getChangeLog(key, ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        String lastAssignee = null;
        IHistoryItem lastStatusChangeHistoryItem = null;
        boolean isTargetStatus = false;
        IUser creator = ticket.getCreator();
        if (creator != null) {
            if (teamToFilter != null) {
                if (teamToFilter.contains(creator.getFullName())) {
                    lastAssignee = teamToFilter.transformName(creator.getFullName());
                }
            } else {
                lastAssignee = creator.getFullName();
            }
        }

        if (!isAssigneeFieldWasEverChanged(histories)) {
            Assignee assignee = ticket.getFields().getAssignee();
            if (assignee != null) {
                if (teamToFilter != null) {
                    if (teamToFilter.contains(assignee.getFullName())) {
                        lastAssignee = teamToFilter.transformName(assignee.getFullName());
                    }
                } else {
                    lastAssignee = assignee.getFullName();
                }
            }
        }


        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().contains(Fields.ASSIGNEE) && (lastStatusChangeHistoryItem == null || isTargetStatus)) {
                    if (historyItem.getToAsString() != null ) {
                        if (teamToFilter != null) {
                            if (teamToFilter.contains(historyItem.getToAsString())) {
                                lastAssignee = teamToFilter.transformName(historyItem.getToAsString());
                            }
                        } else {
                            lastAssignee = historyItem.getToAsString();
                        }
                    }
                }
                if (targetStatuses != null && historyItem.getField().equalsIgnoreCase(trackerClient.getDefaultStatusField())) {
                    String toString = historyItem.getToAsString();
                    if (toString != null) {
                        boolean wasFound = false;
                        for (String targetStatus : targetStatuses) {
                            if (toString.equalsIgnoreCase(targetStatus)) {
                                lastStatusChangeHistoryItem = historyItem;
                                wasFound = true;
                                isTargetStatus = true;
                            }
                        }
                        if (!wasFound) {
                            isTargetStatus = false;
                        }
                    }
                }
            }
        }
        return new ImmutablePair<>(lastAssignee, lastStatusChangeHistoryItem);
    }

    public static boolean isAssigneeFieldWasEverChanged(List<IHistory> histories) {
        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().contains(Fields.ASSIGNEE)) {
                    return true;
                }
            }
        }
        return false;
    }


    public static boolean fieldWasChangedByUser(JiraClient jiraClient, String ticketKey, String field, String user, Ticket ticket) throws IOException {
        IChangelog changeLog = jiraClient.getChangeLog(ticketKey, ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().contains(field) && history.getAuthor().toString().contains(user)) {
                    logger.info("{} changed by {} {}", historyItem.getField(), history.getAuthor(), user);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isFirstTimeRight(TrackerClient jiraClient, String ticketKey, ITicket ticket, String[] inProgressStatuses, String[] qualityStatuses) throws IOException {
        IChangelog changeLog = jiraClient.getChangeLog(ticketKey, ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        boolean isWasInQualityStatus = false;
        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().toLowerCase().contains(Fields.STATUS)) {
                    String toString = historyItem.getToAsString();
                    if (toString != null) {
                        for (String qualityStatus : qualityStatuses) {
                            if (toString.toLowerCase().contains(qualityStatus.toLowerCase())) {
                                isWasInQualityStatus = true;
                                break;
                            }
                        }

                        if (isWasInQualityStatus) {
                            for (String inProgressStatus : inProgressStatuses) {
                                if (toString.toLowerCase().contains(inProgressStatus.toLowerCase())) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }
}