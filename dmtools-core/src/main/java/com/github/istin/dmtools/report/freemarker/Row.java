// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import java.util.ArrayList;
import java.util.List;

public class Row {

    private List<Ticket> tickets;

    public Row() {
    }

    public Row(List<Ticket> tickets) {
        this.tickets = tickets;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }

    public void addTicket(Ticket ticket) {
        if (tickets == null) {
            tickets = new ArrayList<>();
        }
        tickets.add(ticket);
    }

    public void placeholder(int duration) {
        Ticket placeholder = new Ticket();
        placeholder.setDuration(duration);
        addTicket(placeholder);
    }
}
