// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsChangesMetricSource;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class PullRequestsChangesMetricTest {

    private PullRequestsChangesMetric pullRequestsChangesMetric;
    private SourceCode sourceCode;
    private IEmployees employees;
    private Calendar startDate;

    @Before
    public void setUp() {
        sourceCode = mock(SourceCode.class);
        employees = mock(IEmployees.class);
        startDate = Calendar.getInstance();
        pullRequestsChangesMetric = new PullRequestsChangesMetric(
                true, "workspace", "repo", sourceCode, employees, startDate);
    }

    @Test
    public void testGetName() {
        assertEquals("Lines Changes in Pull Requests", pullRequestsChangesMetric.getName());
    }

    @Test
    public void testConstructorInitialization() {
        PullRequestsChangesMetricSource metricSource = Mockito.mock(PullRequestsChangesMetricSource.class);
        PullRequestsChangesMetric metric = new PullRequestsChangesMetric(
                false, "workspace", "repo", sourceCode, employees, startDate);

        assertEquals("Lines Changes in Pull Requests", metric.getName());
    }
}