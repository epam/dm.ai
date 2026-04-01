// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.metrics.FigmaCommentMetric;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.VacationMetric;
import com.github.istin.dmtools.team.Employees;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ProductivityUtils {

    static List<Metric> vacationDays(List<Metric> listOfCustomMetrics, Employees employees) {
        listOfCustomMetrics.add(new VacationMetric(true, true, employees));
        return listOfCustomMetrics;
    }

    static List<Metric> figmaComments(List<Metric> listOfCustomMetrics, Employees employees, FigmaClient figmaClient, String... files) {
        listOfCustomMetrics.add(new FigmaCommentMetric(true, employees, figmaClient, files));
        return listOfCustomMetrics;
    }

    public static boolean isStory(ProductivityJobParams productivityReportParams, ITicket ticket) throws Exception {
        return !IssueType.isBug(ticket.getIssueType()) && !IssueType.isSubTask(ticket.getFields().getIssueType().getName()) && thereIsNoSubtasks(productivityReportParams, ticket) || isSubTaskLinkedToStory(productivityReportParams, ticket);
    }

    public static  boolean isBug(ProductivityJobParams productivityJobParams, ITicket ticket) throws Exception {
        return IssueType.isBug(ticket.getIssueType()) && thereIsNoSubtasks(productivityJobParams, ticket) || isSubTaskLinkedToBug(ticket);
    }

    public static boolean isSubTaskLinkedToStory(ProductivityJobParams productivityReportParams, ITicket ticket) {
        if (IssueType.isSubTask(ticket.getFields().getIssueType().getName())) {
            return IssueType.isStory(ticket.getFields().getParent().getIssueType()) || IssueType.isTask(ticket.getFields().getParent().getIssueType());
        }
        return false;
    }

    public static boolean isSubTaskLinkedToBug(ITicket ticket) {
        if (IssueType.isSubTask(ticket.getFields().getIssueType().getName())) {
            return IssueType.isBug(ticket.getFields().getParent().getIssueType());
        }
        return false;
    }

    public static boolean thereIsNoSubtasks(ProductivityJobParams productivityReportParams, ITicket ticket) throws Exception {
        List subTasks = ((JiraClient<Ticket>) BasicJiraClient.getInstance()).performGettingSubtask(ticket.getTicketKey());
        subTasks = (List) subTasks.stream().filter(new Predicate() {
            @Override
            public boolean test(Object object) {
                try {
                    return !isIgnoreTask(productivityReportParams.getIgnoreTicketPrefixes(), ((ITicket) object));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }).collect(Collectors.toList());
        return subTasks.isEmpty();
    }

    public static boolean isIgnoreTask(String[] ignoreTicketPrefixes, ITicket ticket) throws IOException {
        String cleanedText = ticket.getTicketTitle().replace((char)160, ' ').trim();
        if (ignoreTicketPrefixes == null) {
            return false;
        }
        for (String ignoreTicketPrefix : ignoreTicketPrefixes) {
            if (cleanedText.toLowerCase().startsWith(ignoreTicketPrefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
