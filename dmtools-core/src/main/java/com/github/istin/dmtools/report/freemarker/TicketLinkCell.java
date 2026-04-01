// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.model.ITicket;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;

public class TicketLinkCell extends GenericCell {

    public TicketLinkCell(String ticketKey, String ticketLink) {
        super(url(ticketKey, ticketLink));
    }

    public TicketLinkCell(Collection<? extends ITicket> tickets, Function<ITicket, String> function) throws IOException {
        super(urls(tickets, function));
    }

    private static String urls(Collection<? extends ITicket> tickets, Function<ITicket, String> function) throws IOException {
        StringBuilder buffer = new StringBuilder();
        for (ITicket blocker : tickets) {
            if (buffer.length() > 0) {
                buffer.append(",");
            }
            buffer.append(url(blocker.getTicketKey(), blocker.getTicketLink())).append("[").append(blocker.getStatus()).append("]").append(function.apply(blocker));
        }
        return buffer.toString();
    }

    @NotNull
    private static String url(String ticketKey, String ticketLink) {
        return "<a href=\"" + ticketLink + "\">" + ticketKey + "</a>";
    }
}
