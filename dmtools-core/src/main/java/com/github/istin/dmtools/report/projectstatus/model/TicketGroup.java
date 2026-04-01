// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.model;

import com.github.istin.dmtools.common.model.ITicket;

import java.util.ArrayList;
import java.util.List;

public class TicketGroup {
    private final String name;
    private final List<ITicket> tickets;
    private final String description;

    public TicketGroup(String name, String description) {
        this.name = name;
        this.description = description;
        this.tickets = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<ITicket> getTickets() {
        return tickets;
    }

    public void addTicket(ITicket ticket) {
        tickets.add(ticket);
    }

    public void addAllTickets(List<ITicket> ticketsToAdd) {
        tickets.addAll(ticketsToAdd);
    }

    public int getTicketCount() {
        return tickets.size();
    }

    public double getTotalStoryPoints() {
        double total = 0;
        for (ITicket ticket : tickets) {
            try {
                total += ticket.getWeight();
            } catch (Exception e) {
                // Handle exception
            }
        }
        return total;
    }
}