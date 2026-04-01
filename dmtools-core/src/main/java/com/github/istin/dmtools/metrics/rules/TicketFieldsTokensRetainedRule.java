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
import java.util.*;
import org.simmetrics.StringMetric;
import org.simmetrics.metrics.StringMetrics;

/**
 * Estimates how many tokens from each author's latest edit remain in the final field value.
 * For each author, we take their latest value for the field and compute:
 * retainedTokens = tokens(authorValue) * similarity(authorValue, finalValue)
 */
public class TicketFieldsTokensRetainedRule implements TrackerRule<ITicket> {

    private final IEmployees employees;
    private final String[] filterFields;
    private final boolean includeInitial;
    private final String creatorFilterMode;
    private final Claude35TokenCounter tokenCounter;

    public TicketFieldsTokensRetainedRule(IEmployees employees, String[] filterFields, boolean includeInitial, String creatorFilterMode) {
        this.employees = employees;
        this.filterFields = filterFields;
        this.includeInitial = includeInitial;
        this.creatorFilterMode = creatorFilterMode == null || creatorFilterMode.isEmpty() ? "all" : creatorFilterMode;
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
                    if (!matchesFilter(field)) continue;
                    String fromAsString = historyItem.getFromAsString();
                    String toAsString = historyItem.getToAsString();
                    if (isBlankChangeValue(fromAsString) && !isBlankChangeValue(toAsString)) {
                        fieldsWithInitialHistory.add(field.toLowerCase());
                    }
                }
            }
        }

        ITicket resolvedTicket = ticket;
        List<String> preFetchedFields = getRequiredExtraFields();
        boolean needExtended = false;
        for (String field : filterFields) {
            if (preFetchedFields.stream().anyMatch(f -> f.equalsIgnoreCase(field))) {
                continue;
            }
            String initialValue = getFieldValue(ticket, field);
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
            String finalValue = getFieldValue(resolvedTicket, field);
            if (finalValue == null || finalValue.trim().isEmpty()) {
                continue;
            }

            Map<String, Version> latestByAuthor = new HashMap<>();
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
                    if (!matchesFilter(historyItem.getField())) {
                        continue;
                    }
                    if (!field.equalsIgnoreCase(historyItem.getField())) {
                        continue;
                    }
                    String toAsString = historyItem.getToAsString();
                    if (toAsString == null) toAsString = "";
                    Calendar created = history.getCreated();
                    Version existing = latestByAuthor.get(authorName);
                    if (existing == null || (created != null && existing.created != null && created.after(existing.created))) {
                        latestByAuthor.put(authorName, new Version(toAsString, created));
                    }
                }
            }

            if (includeInitial && !fieldsWithInitialHistory.contains(field.toLowerCase())) {
                String initialValue = getFieldValue(resolvedTicket, field);
                if (initialValue != null && !initialValue.trim().isEmpty()
                    && !latestByAuthor.containsKey(baselineWho)
                    && shouldIncludeAuthor(baselineWho, who)) {
                    latestByAuthor.put(baselineWho, new Version(initialValue, DateUtils.calendar(resolvedTicket.getCreated())));
                }
            }

            for (Map.Entry<String, Version> entry : latestByAuthor.entrySet()) {
                String authorName = entry.getKey();
                Version version = entry.getValue();
                if (version == null || version.value == null) continue;
                int tokens = tokenCounter.countTokens(version.value);
                if (tokens <= 0) continue;
                float similarity = metric.compare(version.value, finalValue);
                if (Float.isNaN(similarity)) continue;
                if (similarity < 0f) similarity = 0f;
                if (similarity > 1f) similarity = 1f;
                double weight = tokens * similarity;
                if (weight <= 0) continue;
                Calendar date = version.created != null ? version.created : DateUtils.calendar(resolvedTicket.getCreated());
                KeyTime keyTime = new KeyTime(ticket.getKey() + ":retained:" + field + ":" + authorName, date, authorName);
                keyTime.setWeight(weight);
                result.add(keyTime);
            }
        }

        return result;
    }

    @Override
    public List<String> getRequiredExtraFields() {
        List<String> fields = new ArrayList<>(Arrays.asList("changelog"));
        if (filterFields != null) {
            fields.addAll(Arrays.asList(filterFields));
        }
        return fields;
    }

    private String getFieldValue(ITicket ticket, String field) {
        if ("description".equalsIgnoreCase(field)) {
            return ticket.getTicketDescription();
        }
        return ticket.getFieldValueAsString(field);
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

    private boolean shouldIncludeAuthor(String authorName, String creatorName) {
        if ("all".equalsIgnoreCase(creatorFilterMode)) return true;
        if ("only".equalsIgnoreCase(creatorFilterMode)) return authorName.equalsIgnoreCase(creatorName);
        return !authorName.equalsIgnoreCase(creatorName);
    }

    private static boolean isBlankChangeValue(String value) {
        if (value == null) return true;
        String v = value.trim();
        if (v.isEmpty()) return true;
        return "null".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v);
    }

    private static class Version {
        final String value;
        final Calendar created;

        Version(String value, Calendar created) {
            this.value = value;
            this.created = created;
        }
    }
}
