// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;

import java.util.Collections;
import java.util.List;

public class TestCasesCreatorsRule extends TicketCreatorsRule {

    public TestCasesCreatorsRule(String project, Employees employees) {
        super(project, employees);
    }

    public TestCasesCreatorsRule(String project, String customName, Employees employees) {
        super(project, customName, employees);
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        if (IssueType.isTestCase(ticket.getIssueType())) {
            return super.check(trackerClient, ticket);
        } else {
            return Collections.emptyList();
        }
    }

}
