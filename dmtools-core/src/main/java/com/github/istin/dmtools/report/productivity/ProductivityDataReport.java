// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.WeeksReleaseGenerator;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.team.Employees;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ProductivityDataReport extends AbstractJob<ProductivityDataReportParams, ProductivityDataResult> {

    @Override
    public ProductivityDataResult runJob(ProductivityDataReportParams params) throws Exception {
        WeeksReleaseGenerator releaseGenerator = new WeeksReleaseGenerator(params.getStartDate());
        String formula = params.getFormula();
        TrackerClient<? extends ITicket> tracker = BasicJiraClient.getInstance();
        String team = params.getReportName() + (params.isWeight() != null && params.isWeight() ? "_sp" : "");
        String jql = params.getInputJQL();
        
        Employees employees;
        String employeesFile = params.getEmployees();
        if (employeesFile != null) {
            employees = Employees.getTesters(employeesFile);
        } else {
            employees = Employees.getTesters();
        }
        
        // Create user name resolver function if needed
        Function<String, String> userNameResolver = createUserNameResolver(tracker);
        
        // Generate list of base metrics (can be empty or include other metrics)
        List<Metric> baseMetrics = generateListOfMetrics(params, employees);
        
        // Get pattern configuration
        Map<String, String> patternNames = params.getCommentPatterns();
        boolean collectRequests = params.isCollectRequests() != null && params.isCollectRequests();
        String requestExtractionPattern = params.getRequestExtractionPattern();
        
        // Build report with analytics
        ProductivityDataResult result = ProductivityTools.buildReportWithAnalytics(
                tracker,
                releaseGenerator,
                team,
                formula,
                jql,
                baseMetrics,
                Release.Style.BY_SPRINTS,
                employees,
                params.getIgnoreTicketPrefixes(),
                patternNames,
                collectRequests,
                requestExtractionPattern,
                userNameResolver);
        
        return result;
    }

    @Override
    public AI getAi() {
        return null;
    }

    protected List<Metric> generateListOfMetrics(ProductivityDataReportParams params, Employees employees) throws IOException {
        List<Metric> listOfCustomMetrics = new ArrayList<>();
        // Add other metrics here if needed
        // For now, analytics metrics will be added by buildReportWithAnalytics
        return listOfCustomMetrics;
    }

    private Function<String, String> createUserNameResolver(TrackerClient tracker) {
        if (tracker instanceof BasicJiraClient) {
            BasicJiraClient jiraClient = (BasicJiraClient) tracker;
            return userId -> {
                try {
                    return jiraClient.performProfile(userId).getFullName();
                } catch (Exception e) {
                    return userId;
                }
            };
        }
        return null;
    }
}

