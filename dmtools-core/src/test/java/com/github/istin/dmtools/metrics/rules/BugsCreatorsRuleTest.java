// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class BugsCreatorsRuleTest {

    private BugsCreatorsRule bugsCreatorsRule;
    private TrackerClient trackerClient;
    private ITicket ticket;
    private Employees employees;

    @Before
    public void setUp() {
        employees = mock(Employees.class);
        trackerClient = mock(TrackerClient.class);
        ticket = mock(ITicket.class);
        bugsCreatorsRule = new BugsCreatorsRule("project", employees);
    }

    @Test
    public void testCheckWithBugIssueType() throws Exception {
        when(ticket.getIssueType()).thenReturn("bug");
        List<KeyTime> expected = Collections.singletonList(mock(KeyTime.class));
        BugsCreatorsRule spyRule = Mockito.spy(bugsCreatorsRule);
        doReturn(expected).when(spyRule).check(trackerClient, ticket);

        List<KeyTime> result = spyRule.check(trackerClient, ticket);

        assertEquals(expected, result);
        verify(spyRule, times(1)).check(trackerClient, ticket);
    }

    @Test
    public void testCheckWithNonBugIssueType() throws Exception {
        when(ticket.getIssueType()).thenReturn("task");

        List<KeyTime> result = bugsCreatorsRule.check(trackerClient, ticket);

        assertEquals(Collections.emptyList(), result);
        verify(ticket, times(1)).getIssueType();
    }
}