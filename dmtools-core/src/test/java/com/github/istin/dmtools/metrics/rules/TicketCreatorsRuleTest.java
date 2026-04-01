// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class TicketCreatorsRuleTest {

    private TicketCreatorsRule ticketCreatorsRule;
    private Employees employees;
    private TrackerClient trackerClient;
    private ITicket ticket;

    @Before
    public void setUp() {
        employees = mock(Employees.class);
        trackerClient = mock(TrackerClient.class);
        ticket = mock(ITicket.class);
        ticketCreatorsRule = new TicketCreatorsRule("TEST", employees);
    }


    @Test
    public void testCheckWithNonMatchingProject() throws Exception {
        when(ticket.getKey()).thenReturn("NONMATCH-123");

        List<KeyTime> result = ticketCreatorsRule.check(trackerClient, ticket);

        assertNull(result);
    }

}