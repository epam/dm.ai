// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.simmetrics.StringMetric;
import org.simmetrics.metrics.StringMetrics;

/**
 * Tracks amount of changed tokens in selected fields using different modes.
 *
 * Supported modes:
 * - mixed (default): |tokens(after) - tokens(before)| * (1 - similarity)
 * - delta: |tokens(after) - tokens(before)|
 * - added: max(tokens(after) - tokens(before), 0)
 * - removed: max(tokens(before) - tokens(after), 0)
 * - rewritten: max(tokens(before), tokens(after)) * (1 - similarity)
 * - contribution: max(tokens(after) - tokens(before), 0) + min(tokens(before), tokens(after)) * (1 - similarity)
 */
public class TicketFieldsTokensChangedRule implements TrackerRule<ITicket> {

    private final IEmployees employees;
    private final String[] filterFields;
    private final boolean isCollectionIfByCreator;
    private final boolean includeInitial;
    private final String mode;
    private final String creatorFilterMode;
    private final Claude35TokenCounter tokenCounter;

    public TicketFieldsTokensChangedRule(IEmployees employees, String[] filterFields, boolean isCollectionIfByCreator) {
        this(employees, filterFields, isCollectionIfByCreator, false, "mixed", null);
    }

    public TicketFieldsTokensChangedRule(IEmployees employees, String[] filterFields, boolean isCollectionIfByCreator, boolean includeInitial) {
        this(employees, filterFields, isCollectionIfByCreator, includeInitial, "mixed", null);
    }

    public TicketFieldsTokensChangedRule(IEmployees employees, String[] filterFields, boolean isCollectionIfByCreator,
                                         boolean includeInitial, String mode) {
        this(employees, filterFields, isCollectionIfByCreator, includeInitial, mode, null);
    }

    public TicketFieldsTokensChangedRule(IEmployees employees, String[] filterFields, boolean isCollectionIfByCreator,
                                         boolean includeInitial, String mode, String creatorFilterMode) {
        this.employees = employees;
        this.filterFields = filterFields;
        this.isCollectionIfByCreator = isCollectionIfByCreator;
        this.includeInitial = includeInitial;
        this.mode = mode == null || mode.isEmpty() ? "mixed" : mode;
        this.creatorFilterMode = creatorFilterMode;
        this.tokenCounter = new Claude35TokenCounter();
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        IChangelog changeLog = trackerClient.getChangeLog(ticket.getKey(), ticket);
        String who = null;
        try {
            if (ticket instanceof Ticket) {
                who = ChangelogAssessment.whoReportedTheTicket((Ticket) ticket, employees);
            }
        } catch (Exception ignored) { }

        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();
        List<KeyTime> result = new ArrayList<>();
        StringMetric metric = StringMetrics.levenshtein();
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
            if (author == null || !employees.contains(author.getFullName())) {
                continue;
            }
            String authorName = employees.transformName(author.getFullName());
            if (!shouldIncludeAuthor(authorName, who)) {
                continue;
            }

            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                String field = historyItem.getField();
                if (!matchesFilter(field)) {
                    continue;
                }

                String fromAsString = historyItem.getFromAsString();
                if (fromAsString == null) fromAsString = "";
                String toAsString = historyItem.getToAsString();
                if (toAsString == null) toAsString = "";

                int tokensBefore = tokenCounter.countTokens(fromAsString);
                int tokensAfter = tokenCounter.countTokens(toAsString);
                float similarity = requiresSimilarity() ? metric.compare(fromAsString, toAsString) : 0.0f;
                double weight = computeWeight(tokensBefore, tokensAfter, similarity);
                if (weight <= 0) {
                    continue;
                }

                KeyTime keyTime = new KeyTime(ticket.getKey() + ":" + field + ":" + history.getCreated(),
                    history.getCreated(), authorName);
                keyTime.setWeight(weight);
                result.add(keyTime);
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
                String initialValue;
                if ("description".equalsIgnoreCase(field)) {
                    initialValue = resolvedTicket.getTicketDescription();
                } else {
                    initialValue = resolvedTicket.getFieldValueAsString(field);
                }
                if (initialValue == null || initialValue.trim().isEmpty()) {
                    continue;
                }
                int tokensAfter = tokenCounter.countTokens(initialValue);
                if (tokensAfter <= 0) {
                    continue;
                }
                double weight = computeWeight(0, tokensAfter, 0.0f);
                KeyTime keyTime = new KeyTime(ticket.getKey() + ":initial:" + field,
                    DateUtils.calendar(resolvedTicket.getCreated()), baselineWho);
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

    private boolean requiresSimilarity() {
        String m = mode.toLowerCase();
        return "mixed".equals(m) || "rewritten".equals(m) || "contribution".equals(m);
    }

    private double computeWeight(int tokensBefore, int tokensAfter, float similarity) {
        String m = mode.toLowerCase();
        switch (m) {
            case "delta":
                return Math.abs(tokensAfter - tokensBefore);
            case "added":
                return Math.max(tokensAfter - tokensBefore, 0);
            case "removed":
                return Math.max(tokensBefore - tokensAfter, 0);
            case "rewritten":
                return Math.max(tokensBefore, tokensAfter) * (1.0d - similarity);
            case "contribution": {
                double added = Math.max(tokensAfter - tokensBefore, 0);
                double rewritten = Math.min(tokensBefore, tokensAfter) * (1.0d - similarity);
                return added + rewritten;
            }
            case "mixed":
            default:
                return Math.abs(tokensAfter - tokensBefore) * (1.0d - similarity);
        }
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
