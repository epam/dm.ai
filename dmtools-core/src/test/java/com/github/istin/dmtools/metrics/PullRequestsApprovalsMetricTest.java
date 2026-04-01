// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsApprovalsMetricSource;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class PullRequestsApprovalsMetricTest {

    private PullRequestsApprovalsMetric metric;
    private SourceCode sourceCode;
    private IEmployees employees;
    private Calendar startDate;

    @Before
    public void setUp() {
        sourceCode = mock(SourceCode.class);
        employees = mock(IEmployees.class);
        startDate = Calendar.getInstance();
        metric = new PullRequestsApprovalsMetric(true, "workspace", "repo", sourceCode, employees, startDate);
    }

    @Test
    public void testGetName() {
        assertEquals("Pull Requests Approvals", metric.getName());
    }

    @Test
    public void testConstructor() {
        PullRequestsApprovalsMetricSource metricSource = Mockito.mock(PullRequestsApprovalsMetricSource.class);
        PullRequestsApprovalsMetric metric = new PullRequestsApprovalsMetric(true, "workspace", "repo", sourceCode, employees, startDate);
        assertEquals("Pull Requests Approvals", metric.getName());
    }
}