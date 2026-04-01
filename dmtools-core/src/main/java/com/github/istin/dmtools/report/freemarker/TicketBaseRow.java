// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.broadcom.rally.model.RallyIssueType;
import com.github.istin.dmtools.common.model.ITicket;

import java.io.IOException;

public class TicketBaseRow extends GenericRow {

    public TicketBaseRow(ITicket ticket, ITicket.ITicketProgress ticketProgress, Integer defaultSPsValue) throws IOException {
        super(false);
        getCells().add(new LinkCell(ticket.getTicketKey(), ticket.getTicketLink()));
        getCells().add(new GenericCell(ticket.getPriority()));
        String issueType = ticket.getIssueType();
        if (issueType.equalsIgnoreCase(RallyIssueType.HIERARCHICAL_REQUIREMENT)) {
            issueType = "Story";
        }
        getCells().add(new GenericCell(issueType));
        getCells().add(new GenericCell(ticket.getTicketTitle()));
        double weight = ticket.getWeight();
        if (weight >= 0) {
            getCells().add(new GenericCell(String.valueOf(weight)));
        } else {
            if (defaultSPsValue == null) {
                getCells().add(new GenericCell("&nbsp;"));
            } else {
                getCells().add(new GenericCell(String.valueOf(defaultSPsValue)));
            }
        }
        getCells().add(new GenericCell("" + ticketProgress.calc(ticket)));
        getCells().add(new GenericCell(ticket.getStatus()));
    }

}
