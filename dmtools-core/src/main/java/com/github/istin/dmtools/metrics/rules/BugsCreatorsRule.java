package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.Collections;
import java.util.List;

public class BugsCreatorsRule extends TicketCreatorsRule {

    public BugsCreatorsRule(String project, IEmployees employees) {
        super(project, employees);
    }

    public BugsCreatorsRule(String project, String customName, IEmployees employees) {
        super(project, customName, employees);
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        if (IssueType.isBug(ticket.getIssueType())) {
            return super.check(trackerClient, ticket);
        } else {
            return Collections.emptyList();
        }
    }

}
