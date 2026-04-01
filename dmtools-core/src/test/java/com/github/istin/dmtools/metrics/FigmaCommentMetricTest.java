// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.metrics.source.CommonSourceCollector;
import com.github.istin.dmtools.metrics.source.FigmaCommentsMetricSource;
import com.github.istin.dmtools.team.IEmployees;

public class FigmaCommentMetricTest {

    private FigmaClient figmaClientMock;
    private IEmployees employeesMock;
    private CommonSourceCollector commonSourceCollectorMock;

    @Before
    public void setUp() {
        figmaClientMock = mock(FigmaClient.class);
        employeesMock = mock(IEmployees.class);
        commonSourceCollectorMock = mock(CommonSourceCollector.class);
    }

    @Test
    public void testConstructorWithFigmaClient() {
        FigmaCommentMetric metric = new FigmaCommentMetric(true, figmaClientMock, "file1", "file2");
        assertEquals("Figma Comment", metric.getName());
    }

    @Test
    public void testConstructorWithEmployeesAndFigmaClient() {
        FigmaCommentMetric metric = new FigmaCommentMetric(true, employeesMock, figmaClientMock, "file1", "file2");
        assertEquals("Figma Comment", metric.getName());
    }

    @Test
    public void testConstructorWithCommonSourceCollector() {
        FigmaCommentMetric metric = new FigmaCommentMetric(true, employeesMock, commonSourceCollectorMock);
        assertEquals("Figma Comment", metric.getName());
    }

    @Test
    public void testGetName() {
        FigmaCommentMetric metric = new FigmaCommentMetric(true, employeesMock, commonSourceCollectorMock);
        assertEquals("Figma Comment", metric.getName());
    }
}