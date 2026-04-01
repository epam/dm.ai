// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WeekTest {

    private Sprint mockSprint;
    private Week week;

    @Before
    public void setUp() {
        mockSprint = mock(Sprint.class);
        week = new Week();
    }

    @Test
    public void testCreateBasedOnSprint() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 1, 0, 0, 0);
        Date sprintStartDate = calendar.getTime();

        when(mockSprint.getStartDate()).thenReturn(sprintStartDate);

        List<Week> weeks = Week.createBasedOnSprint(mockSprint);

        assertNotNull(weeks);
        assertEquals(2, weeks.size());

        Week week1 = weeks.get(0);
        Week week2 = weeks.get(1);

        assertEquals("30.12", week1.getStartDateAsString());
        assertEquals("05.01", week1.getEndDateAsString());

        assertEquals("06.01", week2.getStartDateAsString());
        assertEquals("12.01", week2.getEndDateAsString());
    }

    @Test
    public void testGetStartDateAsString() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 1);
        week.setStartDate(calendar.getTime());

        assertEquals("01.01", week.getStartDateAsString());
    }

    @Test
    public void testGetEndDateAsString() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 7);
        week.setEndDate(calendar.getTime());

        assertEquals("07.01", week.getEndDateAsString());
    }

    @Test
    public void testIsReleased() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2020, Calendar.JANUARY, 1);
        week.setEndDate(calendar.getTime());

        assertTrue(week.isReleased());

        Calendar instance = Calendar.getInstance();
        instance.setTimeInMillis(week.getCurrentTime());
        instance.add(Calendar.DATE, 1);
        week.setEndDate(instance.getTime());

        assertFalse(week.isReleased());
    }

    @Test
    public void testIsMatchedToWeekTimelinesOrLess() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 7);
        week.setEndDate(calendar.getTime());

        Calendar testDate = Calendar.getInstance();
        testDate.set(2023, Calendar.JANUARY, 6);

        assertTrue(week.isMatchedToWeekTimelinesOrLess(testDate));

        testDate.set(2023, Calendar.JANUARY, 8);

        assertFalse(week.isMatchedToWeekTimelinesOrLess(testDate));
    }

    @Test
    public void testGetIterationName() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 1);
        week.setStartDate(calendar.getTime());
        calendar.set(2023, Calendar.JANUARY, 7);
        week.setEndDate(calendar.getTime());

        assertEquals("01.01-07.01", week.getIterationName());
    }

    @Test
    public void testGetId() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 1);
        week.setStartDate(calendar.getTime());

        assertEquals(1, week.getId());
    }
}