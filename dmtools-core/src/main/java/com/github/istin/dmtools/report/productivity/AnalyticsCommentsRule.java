// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.NotifierIdParser;
import com.github.istin.dmtools.metrics.rules.CommentsWrittenRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyticsCommentsRule extends CommentsWrittenRule {

    private final ProductivityAnalyticsData analyticsData;
    private final String patternRegex;
    private final String patternName;
    private final boolean collectRequests;
    private final String requestExtractionPattern;
    private final Function<String, String> userNameResolver;

    public AnalyticsCommentsRule(
            Employees employees,
            String commentsRegex,
            ProductivityAnalyticsData analyticsData,
            String patternName,
            boolean collectRequests,
            String requestExtractionPattern,
            Function<String, String> userNameResolver) {
        super(employees, commentsRegex);
        this.analyticsData = analyticsData;
        this.patternRegex = commentsRegex;
        this.patternName = patternName;
        this.collectRequests = collectRequests;
        this.requestExtractionPattern = requestExtractionPattern;
        this.userNameResolver = userNameResolver;
    }

    @Override
    public KeyTime createKetTimeBasedOnComment(ITicket ticket, IComment comment, String authorName) {
        String body = comment.getBody();
        String notifierId = NotifierIdParser.parseNotifierId(body);
        
        if (notifierId != null && !notifierId.isEmpty() && !notifierId.equalsIgnoreCase("null")) {
            // Update analytics
            analyticsData.getAllUsers().add(notifierId);
            analyticsData.getUsersPerPattern().computeIfAbsent(patternRegex, k -> new HashSet<>()).add(notifierId);
            analyticsData.getInteractionsPerPatternPerUser()
                    .computeIfAbsent(patternRegex, k -> new HashMap<>())
                    .merge(notifierId, 1, Integer::sum);

            analyticsData.getUserInteractions()
                    .computeIfAbsent(patternRegex, k -> new HashMap<>())
                    .computeIfAbsent(notifierId, k -> new ArrayList<>())
                    .add(new InteractionInfo(comment.getCreated(), ticket.getTicketKey()));

            if (collectRequests && requestExtractionPattern != null && !requestExtractionPattern.isEmpty()) {
                String extractedRequest = extractRequest(body, requestExtractionPattern);
                if (!extractedRequest.isEmpty()) {
                    analyticsData.getRequests().add(extractedRequest);
                }
            }

            try {
                String userName = notifierId;
                if (userNameResolver != null) {
                    String resolvedName = userNameResolver.apply(notifierId);
                    if (resolvedName != null && !resolvedName.isEmpty()) {
                        userName = resolvedName;
                    }
                }
                String keyTimeKey = patternName + "_" + ticket.getTicketKey() + " " + userName;
                return new KeyTime(keyTimeKey, DateUtils.calendar(comment.getCreated()), authorName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private String extractRequest(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()) {
            return "";
        }
        Pattern regexPattern = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher matcher = regexPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}

