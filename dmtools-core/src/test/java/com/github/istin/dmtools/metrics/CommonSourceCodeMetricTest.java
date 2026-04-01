// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class CommonSourceCodeMetricTest {

    private CommonSourceCodeMetric commonSourceCodeMetric;

    @Mock
    private SourceCode mockSourceCode;

    @Mock
    private IEmployees mockEmployees;

    @Mock
    private SourceCollector mockSourceCollector;

    @BeforeEach
    public void setUp() {
        commonSourceCodeMetric = new CommonSourceCodeMetric(
                "TestMetric",
                true,
                "TestWorkspace",
                "TestRepo",
                mockSourceCode,
                mockEmployees,
                mockSourceCollector
        );
    }

    @Test
    public void testGetWorkspace() {
        assertEquals("TestWorkspace", commonSourceCodeMetric.getWorkspace());
    }

    @Test
    public void testGetRepo() {
        assertEquals("TestRepo", commonSourceCodeMetric.getRepo());
    }

    @Test
    public void testGetSourceCode() {
        assertEquals(mockSourceCode, commonSourceCodeMetric.getSourceCode());
    }

    @Test
    public void testGetEmployees() {
        assertEquals(mockEmployees, commonSourceCodeMetric.getEmployees());
    }

}