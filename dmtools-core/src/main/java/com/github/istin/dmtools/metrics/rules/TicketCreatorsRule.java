package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TicketCreatorsRule implements TrackerRule<ITicket> {

    private String project;

    private String customName;
    private final IEmployees employees;

    public TicketCreatorsRule(String project, IEmployees employees) {
        this.project = project;
        this.employees = employees;
    }

    public TicketCreatorsRule(String project, String customName, IEmployees employees) {
        this.project = project;
        this.customName = customName;
        this.employees = employees;
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        if (project != null && !ticket.getKey().startsWith(project)) {
            return null;
        }
        Date created = ticket.getFields().getCreated();
        Calendar instance = Calendar.getInstance();
        instance.setTime(created);
        String who = ChangelogAssessment.whoReportedTheTicket((Ticket) ticket, employees);
        return Arrays.asList(new KeyTime(ticket.getKey(), instance, customName == null ? (employees != null ? employees.transformName(who) : who) : customName) );
    }

}
