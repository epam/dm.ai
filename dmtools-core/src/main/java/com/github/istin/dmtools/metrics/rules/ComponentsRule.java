// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;

import java.util.Collections;
import java.util.List;

public class ComponentsRule extends TicketCreatorsRule {

    public ComponentsRule(String project, Employees employees) {
        super(project, employees);
    }

    public ComponentsRule(String project, String customName, Employees employees) {
        super(project, customName, employees);
    }

    @Override
    public List<KeyTime> check(TrackerClient jiraClient, ITicket ticket) throws Exception {
        List<KeyTime> keyTimes = super.check(jiraClient, ticket);
        double weight = ticket.getFields().getComponents().size();
        if (keyTimes != null && weight > 0) {
            keyTimes.get(0).setWeight(round(0.5 * Math.max(weight - 1, 1), 2));
            return keyTimes;
        } else {
            return Collections.emptyList();
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
