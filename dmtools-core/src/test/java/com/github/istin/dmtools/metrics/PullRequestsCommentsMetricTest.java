// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsCommentsMetricSource;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class PullRequestsCommentsMetricTest {

    private PullRequestsCommentsMetric positiveMetric;
    private PullRequestsCommentsMetric negativeMetric;
    private SourceCode sourceCodeMock;
    private IEmployees employeesMock;
    private Calendar startDateMock;

    @Before
    public void setUp() {
        sourceCodeMock = Mockito.mock(SourceCode.class);
        employeesMock = Mockito.mock(IEmployees.class);
        startDateMock = Mockito.mock(Calendar.class);

        positiveMetric = new PullRequestsCommentsMetric(true, true, "workspace", "repo", sourceCodeMock, employeesMock, startDateMock);
        negativeMetric = new PullRequestsCommentsMetric(true, false, "workspace", "repo", sourceCodeMock, employeesMock, startDateMock);
    }

    @Test
    public void testGetName_Positive() {
        assertEquals(PullRequestsCommentsMetric.NAME_POSITIVE, positiveMetric.getName());
    }

    @Test
    public void testGetName_Negative() {
        assertEquals(PullRequestsCommentsMetric.NAME_NEGATIVE, negativeMetric.getName());
    }
}