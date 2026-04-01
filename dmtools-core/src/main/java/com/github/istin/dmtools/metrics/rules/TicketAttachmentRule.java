// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.Attachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;

import java.util.ArrayList;
import java.util.List;

import static com.github.istin.dmtools.common.utils.DateUtils.calendar;

public class TicketAttachmentRule implements TrackerRule<ITicket> {

    private String customName;
    private final Employees employees;

    public TicketAttachmentRule(Employees employees) {
        this.employees = employees;
    }

    public TicketAttachmentRule(String customName, Employees employees) {
        this.customName = customName;
        this.employees = employees;
    }

    @Override
    public List<KeyTime> check(TrackerClient trackerClient, ITicket ticket) throws Exception {
        List<KeyTime> keyTimes = new ArrayList<>();
        for (Attachment attachment : ticket.getFields().getAttachments()) {
            String who = null;
            if (employees != null) {
                if (employees.contains(attachment.getAuthor().getDisplayName())) {
                    who = employees.transformName(attachment.getAuthor().getDisplayName());
                }
            } else {
                who = attachment.getAuthor().getDisplayName();
            }

            if (who != null) {
                keyTimes.add(new KeyTime(ticket.getKey(), calendar(attachment.getCreated()), customName == null ? who : customName));
            }
        }
        return keyTimes;
    }

}
