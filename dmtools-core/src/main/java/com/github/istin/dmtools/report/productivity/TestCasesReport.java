// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.WeeksReleaseGenerator;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.rules.TestCasesCreatorsRule;
import com.github.istin.dmtools.metrics.rules.TicketAttachmentRule;
import com.github.istin.dmtools.metrics.rules.TicketFieldsChangesRule;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.team.Employees;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestCasesReport extends AbstractJob<TestCasesReportParams, ResultItem> {

    @Override
    public ResultItem runJob(TestCasesReportParams qaProductivityReportParams) throws Exception {
        WeeksReleaseGenerator releaseGenerator = new WeeksReleaseGenerator(qaProductivityReportParams.getStartDate());
        String formula = qaProductivityReportParams.getFormula();
        String response = FileUtils.readFileToString(ProductivityTools.generate(BasicJiraClient.getInstance(), releaseGenerator, qaProductivityReportParams.getReportName() + (qaProductivityReportParams.isWeight() ? "_sp" : ""), formula, qaProductivityReportParams.getInputJQL(), generateListOfMetrics(qaProductivityReportParams), Release.Style.BY_SPRINTS, Employees.getTesters(qaProductivityReportParams.getEmployees()), qaProductivityReportParams.getIgnoreTicketPrefixes()));
        return new ResultItem("testReport", response);
    }

    @Override
    public AI getAi() {
        return null;
    }

    protected List<Metric> generateListOfMetrics(TestCasesReportParams qaProductivityReportParams) throws IOException {
        List<Metric> listOfCustomMetrics = new ArrayList<>();
        numberOfAttachments(qaProductivityReportParams, listOfCustomMetrics);
        createdTests(qaProductivityReportParams, listOfCustomMetrics);
        summaryChanged(qaProductivityReportParams, listOfCustomMetrics);
        descriptionChanged(qaProductivityReportParams, listOfCustomMetrics);
        priorityChanged(qaProductivityReportParams, listOfCustomMetrics);
        ticketLinksChanged(qaProductivityReportParams, listOfCustomMetrics);
        return listOfCustomMetrics;
    }

    protected void summaryChanged(TestCasesReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Summary Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"summary"}, true, false)));
    }

    protected void descriptionChanged(TestCasesReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Description Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"description"}, true, false)));
    }

    protected void priorityChanged(TestCasesReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Priority Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"priority"}, false, false)));
    }

    protected void ticketLinksChanged(TestCasesReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Ticket Links Changed", qaProductivityReportParams.isWeight(), new TicketFieldsChangesRule(Employees.getTesters(qaProductivityReportParams.getEmployees()), new String[]{"link"}, false, true)));
    }


    protected void numberOfAttachments(TestCasesReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Number of Attachments", qaProductivityReportParams.isWeight(), new TicketAttachmentRule(null, Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }


    protected void createdTests(TestCasesReportParams qaProductivityReportParams, List<Metric> listOfCustomMetrics) {
        listOfCustomMetrics.add(new Metric("Created Tests", qaProductivityReportParams.isWeight(), new TestCasesCreatorsRule(qaProductivityReportParams.getTestCasesProjectCode(), Employees.getTesters(qaProductivityReportParams.getEmployees()))));
    }

}