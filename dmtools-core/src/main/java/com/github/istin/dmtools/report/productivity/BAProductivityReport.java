// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.WeeksReleaseGenerator;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.BasicFigmaClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.rules.*;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BAProductivityReport extends AbstractJob<BAProductivityReportParams, ResultItem> {

    @Override
    public ResultItem runJob(BAProductivityReportParams baProductivityReportParams) throws Exception {
        WeeksReleaseGenerator releaseGenerator = new WeeksReleaseGenerator(baProductivityReportParams.getStartDate());
        String formula = baProductivityReportParams.getFormula();
        String response = FileUtils.readFileToString(ProductivityTools.generate(BasicJiraClient.getInstance(), releaseGenerator, baProductivityReportParams.getReportName() + (baProductivityReportParams.isWeight() ? "_sp" : ""), formula, baProductivityReportParams.getInputJQL(), generateListOfMetrics(baProductivityReportParams), Release.Style.BY_SPRINTS, Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()), baProductivityReportParams.getIgnoreTicketPrefixes()));
        return new ResultItem("baProductivityReport", response);
    }

    @Override
    public AI getAi() {
        return null;
    }

    protected List<Metric> generateListOfMetrics(BAProductivityReportParams baProductivityReportParams) throws IOException {
        List<Metric> listOfCustomMetrics = new ArrayList<>();
        createdFeatures(baProductivityReportParams, listOfCustomMetrics);
        createdStories(baProductivityReportParams, listOfCustomMetrics);
        numberOfAttachments(baProductivityReportParams, listOfCustomMetrics);
        numberOfComponents(baProductivityReportParams, listOfCustomMetrics);
        movedToDone(baProductivityReportParams, listOfCustomMetrics);
        movedToReopened(baProductivityReportParams, listOfCustomMetrics);
        fieldsChanged(baProductivityReportParams, listOfCustomMetrics);
        ProductivityUtils.vacationDays(listOfCustomMetrics, Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()));
        String[] figmaFiles = baProductivityReportParams.getFigmaFiles();
        if (figmaFiles != null && figmaFiles.length > 0) {
            ProductivityUtils.figmaComments(listOfCustomMetrics, Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()), BasicFigmaClient.getInstance(), figmaFiles);
        }
        return listOfCustomMetrics;
    }

    protected void fieldsChanged(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Fields Changed", baProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()))));
    }

    protected void createdFeatures(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Created Features", baProductivityReportParams.isWeight(), new TicketCreatorsRule(baProductivityReportParams.getFeatureProjectCode(), Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()))));
    }

    protected void createdStories(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Created Stories", baProductivityReportParams.isWeight(), new TicketCreatorsRule(baProductivityReportParams.getStoryProjectCode(), Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()))));
    }

    protected void numberOfAttachments(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Number of Attachments", baProductivityReportParams.isWeight(), new TicketAttachmentRule(null, Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()))));
    }

    protected void numberOfComponents(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Number of Components", baProductivityReportParams.isWeight(), new ComponentsRule(baProductivityReportParams.getFeatureProjectCode(), Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees()))));
    }

    protected void movedToDone(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {
        Boolean weight = baProductivityReportParams.isWeight();
        listOfCustomMetrics.add(new Metric("Tasks Moved To Done", weight, new TicketMovedToStatusRule(baProductivityReportParams.getStatusesDone(), null, false) {
            @Override
            public List<KeyTime> check(TrackerClient jiraClient, ITicket ticket) throws Exception {
                if (ProductivityUtils.isIgnoreTask(baProductivityReportParams.getIgnoreTicketPrefixes(), ticket)) return null;
                if (ProductivityUtils.isStory(baProductivityReportParams, ticket)) {
                    List<KeyTime> keyTimes = super.check(jiraClient, ticket);
                    if (keyTimes != null && !keyTimes.isEmpty()) {
                        keyTimes = keyTimes.subList(0, 1);
                        KeyTime keyTime = keyTimes.get(0);
                        keyTime.setWho(findResponsibleBA(baProductivityReportParams, jiraClient, ticket, baProductivityReportParams.getStatusesInProgress(), baProductivityReportParams.getStatusesDone()));
                    }
                    return keyTimes;
                } else {
                    return null;
                }
            }

        }));
    }

    public String findResponsibleBA(BAProductivityReportParams baProductivityReportParams, TrackerClient trackerClient, ITicket ticket, String[] inProgressStatuses, String[] doneStatuses) throws IOException {
        Employees businessAnalysts = Employees.getBusinessAnalysts(baProductivityReportParams.getEmployees());
        String whoIsResponsible = ChangelogAssessment.findWhoIsResponsible(trackerClient, businessAnalysts, ticket, inProgressStatuses);
        if (whoIsResponsible.equalsIgnoreCase(Employees.UNKNOWN)) {
            whoIsResponsible = ChangelogAssessment.findWhoIsResponsible(trackerClient, businessAnalysts, ticket, doneStatuses);
        }
        return whoIsResponsible;
    }

    protected void movedToReopened(BAProductivityReportParams baProductivityReportParams, List<Metric> listOfCustomMetrics) {

        listOfCustomMetrics.add(new Metric("Items Moved to Reopened", baProductivityReportParams.isWeight(), new TicketMovedToStatusRule(baProductivityReportParams.getStatusesDone(), false) {
            @Override
            public List<KeyTime> check(TrackerClient jiraClient, ITicket ticket) throws Exception {
                if (ProductivityUtils.isIgnoreTask(baProductivityReportParams.getIgnoreTicketPrefixes(), ticket)) return null;
                List<KeyTime> keyTimes = super.check(jiraClient, ticket);
                if (keyTimes != null && !keyTimes.isEmpty()) {
                    boolean isNotFirstTimeRight = !ChangelogAssessment.isFirstTimeRight(jiraClient, ticket.getTicketKey(), ticket, baProductivityReportParams.getStatusesInProgress(), baProductivityReportParams.getStatusesDone());
                    if (isNotFirstTimeRight) {
                        keyTimes = keyTimes.subList(0, 1);
                        KeyTime keyTime = keyTimes.get(0);
                        keyTime.setWho(findResponsibleBA(baProductivityReportParams, jiraClient, ticket, baProductivityReportParams.getStatusesInProgress(), baProductivityReportParams.getStatusesDone()));
                        return keyTimes;
                    } else {
                        return null;
                    }
                }
                return keyTimes;
            }
        }));
    }

}