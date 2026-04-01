// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.tracker.TrackerClient;

public class TicketLinksCell extends GenericCell {

    public TicketLinksCell(TrackerClient trackerClient, String ... ticketKeys) {
        super(tickets(trackerClient, ticketKeys));
    }

    public static String tickets(TrackerClient trackerClient, String ... ticketKeys) {
        if (ticketKeys == null || ticketKeys.length == 0) {
            return "0";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String key : ticketKeys) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append("<br/>");
            }
            stringBuilder.append("<a href=\""+ trackerClient.getTicketBrowseUrl(key) +"\">" + key + "</a>");
        }
        return stringBuilder.toString();
    }
}
