// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.github.istin.dmtools.metrics.source.CommonSourceCollector;
import com.github.istin.dmtools.metrics.source.VacationsMetricSource;
import com.github.istin.dmtools.team.IEmployees;

public class VacationMetricTest {

    @Test
    public void testGetNameStaticMethod() {
        assertEquals("Vacation days", VacationMetric.getName(true));
        assertEquals("Vacation SPs", VacationMetric.getName(false));
    }

    @Test
    public void testIsDays() {
        VacationMetric metricDays = new VacationMetric(true, true);
        assertTrue(metricDays.isDays());

        VacationMetric metricSPs = new VacationMetric(true, false);
        assertTrue(!metricSPs.isDays());
    }

    @Test
    public void testGetNameInstanceMethod() {
        VacationMetric metricDays = new VacationMetric(true, true);
        assertEquals("Vacation days", metricDays.getName());

        VacationMetric metricSPs = new VacationMetric(true, false);
        assertEquals("Vacation SPs", metricSPs.getName());
    }

    @Test
    public void testConstructorWithEmployees() {
        IEmployees employeesMock = mock(IEmployees.class);
        VacationMetric metric = new VacationMetric(true, true, employeesMock);
        assertEquals("Vacation days", metric.getName());
    }

    @Test
    public void testConstructorWithEmployeesAndSourceCollector() {
        IEmployees employeesMock = mock(IEmployees.class);
        CommonSourceCollector sourceCollectorMock = mock(CommonSourceCollector.class);
        VacationMetric metric = new VacationMetric(true, true, employeesMock, sourceCollectorMock);
        assertEquals("Vacation days", metric.getName());
    }
}