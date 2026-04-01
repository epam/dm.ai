// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsMetricSource;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class PullRequestsMetricTest {

    private PullRequestsMetric pullRequestsMetric;
    private SourceCode sourceCode;
    private IEmployees employees;
    private Calendar startDate;

    @Before
    public void setUp() {
        sourceCode = mock(SourceCode.class);
        employees = mock(IEmployees.class);
        startDate = Calendar.getInstance();
        pullRequestsMetric = new PullRequestsMetric(true, "workspace", "repo", sourceCode, employees, startDate);
    }

    @Test
    public void testGetName() {
        assertEquals("Pull Requests", pullRequestsMetric.getName());
    }

    @Test
    public void testConstructorInitialization() {
        PullRequestsMetricSource metricSource = new PullRequestsMetricSource("workspace", "repo", sourceCode, employees, startDate);
        PullRequestsMetric metric = new PullRequestsMetric(true, "workspace", "repo", sourceCode, employees, startDate);

        assertEquals("Pull Requests", metric.getName());
        // Additional assertions can be added here to verify the state of the object
    }
}