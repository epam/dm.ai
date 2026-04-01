// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TicketMovedToStatusRule implements TrackerRule<ITicket> {

    private String[] statuses;

    private String customName = null;

    private boolean toCheckResolutionForDone = true;


    public TicketMovedToStatusRule(String[] statuses) {
        this.statuses = statuses;
    }

    public TicketMovedToStatusRule(String status) {
        this(new String[]{status});
    }

    public TicketMovedToStatusRule(String[] statuses, boolean toCheckResolutionForDone) {
        this.statuses = statuses;
        this.toCheckResolutionForDone = toCheckResolutionForDone;
    }

    public TicketMovedToStatusRule(String status, boolean toCheckResolutionForDone) {
        this(new String[]{status}, toCheckResolutionForDone);
    }

    public TicketMovedToStatusRule(String[] statuses, String customName) {
        this.statuses = statuses;
        this.customName = customName;
    }

    public TicketMovedToStatusRule(String status, String customName) {
        this(new String[]{status}, customName);
    }

    public TicketMovedToStatusRule(String[] statuses, String customName, boolean toCheckResolutionForDone) {
        this.statuses = statuses;
        this.customName = customName;
        this.toCheckResolutionForDone = toCheckResolutionForDone;
    }

    public TicketMovedToStatusRule(String status, String customName, boolean toCheckResolutionForDone) {
        this(new String[]{status}, customName, toCheckResolutionForDone);
    }

    @Override
    public List<KeyTime> check(TrackerClient jiraClient, ITicket ticket) throws Exception {
        Resolution resolution = ticket.getResolution();
        if (toCheckResolutionForDone && statuses[0].equalsIgnoreCase("done")) {
            if (resolution != null) {
                if (!resolution.isRejected()) {
                    return ChangelogAssessment.findDatesWhenTicketWasInStatus(customName, true, jiraClient, ticket.getKey(), ticket, statuses);
                } else {
                    return new ArrayList<>();
                }
            } else {
                return ChangelogAssessment.findDatesWhenTicketWasInStatus(customName, true, jiraClient, ticket.getKey(), ticket, statuses);
            }
        } else {
            return ChangelogAssessment.findDatesWhenTicketWasInStatus(customName, true, jiraClient, ticket.getKey(), ticket, statuses);
        }
    }

    public String[] getStatuses() {
        return statuses;
    }

    @Override
    public List<String> getRequiredExtraFields() {
        return Collections.singletonList("changelog");
    }
}
