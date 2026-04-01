// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import java.util.List;

public class Backlog {

    public List<Ticket> getTickets() {
        return tickets;
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }

    private List<Ticket> tickets;

    public int getScopeSP() {
        return scopeSP;
    }

    public void setScopeSP(int scopeSP) {
        this.scopeSP = scopeSP;
    }

    public Backlog(List<Ticket> tickets, int scopeSP) {
        this.tickets = tickets;
        this.scopeSP = scopeSP;
    }

    private int scopeSP;

    public Backlog() {
    }

}
