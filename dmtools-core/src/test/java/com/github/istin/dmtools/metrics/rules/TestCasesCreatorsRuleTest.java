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

public class TestCasesCreatorsRuleTest {

    private TestCasesCreatorsRule testCasesCreatorsRule;
    private TrackerClient trackerClient;
    private ITicket ticket;
    private Employees employees;

    @Before
    public void setUp() {
        employees = mock(Employees.class);
        testCasesCreatorsRule = new TestCasesCreatorsRule("TestProject", employees);
        trackerClient = mock(TrackerClient.class);
        ticket = mock(ITicket.class);
    }

    @Test
    public void testCheckWithTestCaseIssueType() throws Exception {
        when(ticket.getIssueType()).thenReturn("Test Case");
        List<KeyTime> expected = Collections.singletonList(mock(KeyTime.class));
        TestCasesCreatorsRule spyRule = spy(testCasesCreatorsRule);
        doReturn(expected).when(spyRule).check(trackerClient, ticket);

        List<KeyTime> result = spyRule.check(trackerClient, ticket);

        assertEquals(expected, result);
        verify(spyRule, times(1)).check(trackerClient, ticket);
    }

    @Test
    public void testCheckWithNonTestCaseIssueType() throws Exception {
        when(ticket.getIssueType()).thenReturn("Bug");

        List<KeyTime> result = testCasesCreatorsRule.check(trackerClient, ticket);

        assertEquals(Collections.emptyList(), result);
    }
}