// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sm;

import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;

import java.util.Calendar;

public class Change {

    private ITicket ticket;

    private Calendar when;

    private IUser who;

    private IHistoryItem historyItem;

    public ITicket getTicket() {
        return ticket;
    }

    public void setTicket(ITicket ticket) {
        this.ticket = ticket;
    }

    public Calendar getWhen() {
        return when;
    }

    public void setWhen(Calendar when) {
        this.when = when;
    }

    public IUser getWho() {
        return who;
    }

    public void setWho(IUser who) {
        this.who = who;
    }

    public IHistoryItem getHistoryItem() {
        return historyItem;
    }

    public void setHistoryItem(IHistoryItem historyItem) {
        this.historyItem = historyItem;
    }

    @Override
    public String toString() {
        return "Change{" +
                "ticket=" + ticket +
                ", when=" + when +
                ", who=" + who +
                ", historyItem=" + historyItem +
                '}';
    }
}
