// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.excel;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.Calendar;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ExcelMetricSourceTest extends TestCase {

    private ExcelMetricSource excelMetricSource;

    @Mock
    IEmployees employees;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(employees.contains(anyString())).thenReturn(true);
        when(employees.transformName(anyString())).thenAnswer((Answer<String>) invocation -> {
            return invocation.getArgument(0);
        });

        // Initialize ExcelMetricSource using the test file
        excelMetricSource = new ExcelMetricSource(employees, "/Test_Excel.xlsx", "Employee", "Date", "Amount", 1.0d);
    }

    @Test
    public void testPerformSourceCollection() throws Exception {
        // Perform the collection
        List<KeyTime> keyTimes = excelMetricSource.performSourceCollection(true, "metricName");

        // Assertions to verify the content of keyTimes
        assertNotNull(keyTimes);
        assertEquals(2, keyTimes.size());

        Calendar calendar = Calendar.getInstance();

        // Verify first row
        KeyTime keyTime1 = keyTimes.get(0);
        assertEquals("Name 1", keyTime1.getWho());
        assertEquals(3.0, keyTime1.getWeight());
        calendar.setTime(keyTime1.getWhen().getTime());
        assertEquals(2023, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH));
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH));

        // Verify second row
        KeyTime keyTime2 = keyTimes.get(1);
        assertEquals("Name 2", keyTime2.getWho());
        assertEquals(2.0, keyTime2.getWeight());
        calendar.setTime(keyTime2.getWhen().getTime());
        assertEquals(2024, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH));
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH));
    }
}