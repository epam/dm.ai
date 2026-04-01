// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import com.github.istin.dmtools.vacation.Vacation;
import com.github.istin.dmtools.vacation.Vacations;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class VacationsMetricSourceTest {

    private IEmployees employeesMock;
    private VacationsMetricSource vacationsMetricSource;
    private List<String> peopleToFilterOut;
    private Vacation vacationMock;
    private Vacations vacationsMock;

    @Before
    public void setUp() {
        employeesMock = Mockito.mock(IEmployees.class);
        vacationMock = Mockito.mock(Vacation.class);
        vacationsMock = Mockito.mock(Vacations.class);

        peopleToFilterOut = Arrays.asList("John Doe", "Jane Doe");
        vacationsMetricSource = new VacationsMetricSource(employeesMock, peopleToFilterOut, true);

        when(vacationsMock.getVacations(peopleToFilterOut)).thenReturn(Arrays.asList(vacationMock));
        //Vacations.setInstance(vacationsMock);
    }

    @Test
    public void testConvertVacationHoursToSPs() throws Exception {
        double result = vacationsMetricSource.convertVacationHoursToSPs(40, "John Doe");
        assertEquals(40.0, result, 0.01);
    }
}