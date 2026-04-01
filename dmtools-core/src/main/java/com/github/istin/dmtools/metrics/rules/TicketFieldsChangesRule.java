// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.simmetrics.StringMetric;
import org.simmetrics.metrics.StringMetrics;

public class TicketFieldsChangesRule implements TrackerRule<ITicket> {

    private String customName;
    private final IEmployees employees;
    private final String[] filterFields;
    private boolean isSimilarity = false;
    private boolean isCollectionIfByCreator = false;
    private String creatorFilterMode = null;
    private boolean includeInitial = false;
    private boolean useDivider = true;
    private PropertyReader propertyReader;

    public TicketFieldsChangesRule(IEmployees employees) {
        this(employees, null, false, false, false, true);
    }

    public TicketFieldsChangesRule(IEmployees employees, String[] filterFields, boolean isSimilarity, boolean isCollectionIfByCreator) {
        this(employees, filterFields, isSimilarity, isCollectionIfByCreator, false, true);
    }

    public TicketFieldsChangesRule(IEmployees employees, String[] filterFields, boolean isSimilarity, boolean isCollectionIfByCreator, boolean includeInitial, boolean useDivider) {
        this.employees = employees;
        this.filterFields = filterFields;
        this.isSimilarity = isSimilarity;
        this.isCollectionIfByCreator = isCollectionIfByCreator;
        this.includeInitial = includeInitial;
        this.useDivider = useDivider;
        this.propertyReader = new PropertyReader();
    }

    public TicketFieldsChangesRule(String customName, IEmployees employees) {
        this(customName, employees, null, false, false, false, true);
    }

    public TicketFieldsChangesRule(String customName, IEmployees employees, String[] filterFields, boolean isSimilarity, boolean isCollectionIfByCreator) {
        this(customName, employees, filterFields, isSimilarity, isCollectionIfByCreator, false, true);
    }

    public TicketFieldsChangesRule(String customName, IEmployees employees, String[] filterFields, boolean isSimilarity, boolean isCollectionIfByCreator, boolean includeInitial, boolean useDivider) {
        this.customName = customName;
        this.employees = employees;
        this.filterFields = filterFields;
        this.isSimilarity = isSimilarity;
        this.isCollectionIfByCreator = isCollectionIfByCreator;
        this.includeInitial = includeInitial;
        this.useDivider = useDivider;
        this.propertyReader = new PropertyReader();
    }

    public TicketFieldsChangesRule(String customName, IEmployees employees, String[] filterFields, boolean isSimilarity,
                                   boolean isCollectionIfByCreator, boolean includeInitial, boolean useDivider,
                                   String creatorFilterMode) {
        this.customName = customName;
        this.employees = employees;
        this.filterFields = filterFields;
        this.isSimilarity = isSimilarity;
        this.isCollectionIfByCreator = isCollectionIfByCreator;
        this.includeInitial = includeInitial;
        this.useDivider = useDivider;
        this.creatorFilterMode = creatorFilterMode;
        this.propertyReader = new PropertyReader();
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        IChangelog changeLog = trackerClient.getChangeLog(ticket.getKey(), ticket);

        // Determine who reported the ticket (works across Jira/ADO/Rally)
        String who;
        if (ticket instanceof Ticket) {
            // Jira-specific logic for better accuracy
            who = ChangelogAssessment.whoReportedTheTicket((Ticket) ticket, employees);
        } else {
            // Generic fallback for non-Jira tickets (ADO, Rally, etc.)
            IUser creator = ticket.getCreator();
            if (creator != null && employees != null) {
                String creatorName = creator.getFullName();
                who = employees.contains(creatorName) ? creatorName : IEmployees.UNKNOWN;
            } else {
                who = IEmployees.UNKNOWN;
            }
        }

        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        String lastAssignee = null;
        List<KeyTime> result = new ArrayList<>();
        Set<String> fieldsWithInitialHistory = new HashSet<>();

        if (includeInitial && filterFields != null && filterFields.length > 0) {
            for (IHistory history : histories) {
                List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
                for (IHistoryItem historyItem : items) {
                    String field = historyItem.getField();
                    if (!matchesFilter(field)) {
                        continue;
                    }
                    String fromAsString = historyItem.getFromAsString();
                    String toAsString = historyItem.getToAsString();
                    if (isBlankChangeValue(fromAsString) && !isBlankChangeValue(toAsString)) {
                        fieldsWithInitialHistory.add(field.toLowerCase());
                    }
                }
            }
        }
        for (IHistory history : histories) {
            IUser author = history.getAuthor();
            if (author != null && employees.contains(author.getFullName())) {
                String authorName = employees.transformName(author.getFullName());
                if (!shouldIncludeAuthor(authorName, who)) {
                    continue;
                }
                List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
                for (IHistoryItem historyItem : items) {
                    double weight = 1;
                    String fromAsString = historyItem.getFromAsString();
                    if (fromAsString == null) {
                        fromAsString = "";
                    }
                    String toAsString = historyItem.getToAsString();
                    if (toAsString == null) {
                        toAsString = "";
                    }
                    String field = historyItem.getField();
                    if (filterFields != null) {
                        if (!matchesFilter(field)) {
                            continue;
                        }

                        if (isSimilarity) {
                            StringMetric metric = StringMetrics.levenshtein();

                            float similarityResult = metric.compare(fromAsString, toAsString);
                            //System.out.println("Similarity Before: " + historyItem.getFromAsString());
                            //System.out.println("Similarity After: " + historyItem.getToAsString());
                            if (Float.isNaN(similarityResult)) {
                                continue;
                            }
                            if (similarityResult < 0f) similarityResult = 0f;
                            if (similarityResult > 1f) similarityResult = 1f;
                            // Debug output removed - use logger if needed for troubleshooting
                            weight = 1 - similarityResult;
                            if (weight <= 0) {
                                continue;
                            }
                        } else {
                            if (useDivider) {
                                Double ticketFieldsChangedDivider = propertyReader.getTicketFieldsChangedDivider(field);
                                if (ticketFieldsChangedDivider == null) {
                                    weight = weight + 1d;
                                } else {
                                    weight = weight + 1d / ticketFieldsChangedDivider;
                                }
                            } else {
                                weight = 1d;
                            }
                        }
                    } else {
                        Double ticketFieldsChangedDivider = propertyReader.getTicketFieldsChangedDivider(field);
                        if (ticketFieldsChangedDivider != null) {
                            weight = weight / ticketFieldsChangedDivider;
                        }
                    }
                    KeyTime keyTime = new KeyTime(ticket.getKey() + fromAsString + toAsString, history.getCreated(), authorName);
                    keyTime.setWeight(weight);
                    result.add(keyTime);
                }
            }
        }
        if (includeInitial && filterFields != null && filterFields.length > 0) {
            ITicket resolvedTicket = ticket;
            List<String> preFetchedFields = getRequiredExtraFields();
            boolean needExtended = false;
            for (String field : filterFields) {
                if (preFetchedFields.stream().anyMatch(f -> f.equalsIgnoreCase(field))) {
                    continue;
                }
                String initialValue = null;
                if ("description".equalsIgnoreCase(field)) {
                    initialValue = ticket.getTicketDescription();
                } else {
                    initialValue = ticket.getFieldValueAsString(field);
                }
                if (initialValue == null || initialValue.trim().isEmpty()) {
                    needExtended = true;
                    break;
                }
            }
            if (needExtended) {
                try {
                    resolvedTicket = trackerClient.performTicket(ticket.getKey(), trackerClient.getExtendedQueryFields());
                } catch (Exception ignored) { }
            }

            String creatorName = null;
            try {
                IUser creator = resolvedTicket.getCreator();
                if (creator != null) {
                    creatorName = employees.transformName(creator.getFullName());
                }
            } catch (Exception ignored) { }
            String baselineWho = creatorName;
            if ((baselineWho == null || baselineWho.isEmpty()) && who != null && !who.isEmpty()) {
                baselineWho = employees.transformName(who);
            }
            if (baselineWho == null || baselineWho.isEmpty() || !employees.contains(baselineWho)) {
                baselineWho = IEmployees.UNKNOWN;
            }

            for (String field : filterFields) {
                if (fieldsWithInitialHistory.contains(field.toLowerCase())) {
                    continue;
                }
                String initialValue = null;
                if ("description".equalsIgnoreCase(field)) {
                    initialValue = resolvedTicket.getTicketDescription();
                } else {
                    initialValue = resolvedTicket.getFieldValueAsString(field);
                }
                if (initialValue == null || initialValue.trim().isEmpty()) {
                    continue;
                }
                double weight = 0.0;
                if (!isSimilarity) {
                    if (useDivider) {
                        Double ticketFieldsChangedDivider = propertyReader.getTicketFieldsChangedDivider(field);
                        // Match the same weighting logic as regular changes:
                        // base 1 + divider adjustment
                        weight = 1d;
                        if (ticketFieldsChangedDivider == null) {
                            weight = weight + 1d;
                        } else {
                            weight = weight + 1d / ticketFieldsChangedDivider;
                        }
                    } else {
                        weight = 1d;
                    }
                }
                KeyTime keyTime = new KeyTime(ticket.getKey() + ":initial:" + field, DateUtils.calendar(resolvedTicket.getCreated()), baselineWho);
                keyTime.setWeight(weight);
                result.add(keyTime);
            }
        }
        return result;
    }

    @Override
    public List<String> getRequiredExtraFields() {
        List<String> fields = new ArrayList<>();
        fields.add("changelog");
        if (filterFields != null) {
            fields.addAll(java.util.Arrays.asList(filterFields));
        }
        return fields;
    }

    private boolean matchesFilter(String field) {
        if (filterFields == null) return true;
        if (field == null) return false;
        for (String fieldToMap : filterFields) {
            if (fieldToMap.equalsIgnoreCase(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlankChangeValue(String value) {
        if (value == null) return true;
        String v = value.trim();
        if (v.isEmpty()) return true;
        return "null".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v);
    }

    private boolean shouldIncludeAuthor(String authorName, String creatorName) {
        String mode = creatorFilterMode;
        if (mode == null || mode.isEmpty()) {
            if (isCollectionIfByCreator) {
                return authorName.equalsIgnoreCase(creatorName);
            }
            return !authorName.equalsIgnoreCase(creatorName);
        }
        switch (mode.toLowerCase()) {
            case "all":
                return true;
            case "only":
                return authorName.equalsIgnoreCase(creatorName);
            case "exclude":
            default:
                return !authorName.equalsIgnoreCase(creatorName);
        }
    }

}
