// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsLinesOfCodeMetricSource;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class LinesOfCodeMetricTest {

    private static final String WORKSPACE = "workspace";
    private static final String REPO = "repo";
    private static final String BRANCH = "branch";
    private static final boolean IS_PERSONALIZED = true;
    private static final String EXPECTED_NAME = "Lines Of Code";

    private SourceCode sourceCode;
    private IEmployees employees;
    private LinesOfCodeMetric linesOfCodeMetric;

    @Before
    public void setUp() {
        sourceCode = mock(SourceCode.class);
        employees = mock(IEmployees.class);
        linesOfCodeMetric = new LinesOfCodeMetric(IS_PERSONALIZED, WORKSPACE, REPO, BRANCH, sourceCode, employees);
    }

    @Test
    public void testGetName() {
        assertEquals(EXPECTED_NAME, linesOfCodeMetric.getName());
    }

    @Test
    public void testConstructor() {
        PullRequestsLinesOfCodeMetricSource metricSource = Mockito.mock(PullRequestsLinesOfCodeMetricSource.class);
        LinesOfCodeMetric metric = new LinesOfCodeMetric(IS_PERSONALIZED, WORKSPACE, REPO, BRANCH, sourceCode, employees);
        assertEquals(EXPECTED_NAME, metric.getName());
    }
}