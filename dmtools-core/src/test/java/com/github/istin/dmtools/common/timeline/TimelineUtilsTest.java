// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TimelineUtilsTest {

    private TimelineUtils timelineUtils;

    @Before
    public void setUp() {
        timelineUtils = new TimelineUtils();
    }

    @Test
    public void testGetDefaultCurrentQuarter() {
        int startYear = 2020;
        int quarter = TimelineUtils.getDefaultCurrentQuarter(startYear);
        assertTrue(quarter > 0);
    }

    @Test
    public void testGetDefaultCurrentYear() {
        int currentYear = TimelineUtils.getDefaultCurrentYear();
        assertEquals(Calendar.getInstance().get(Calendar.YEAR), currentYear);
    }

    @Test
    public void testGenerateYears() {
        int startYear = 2020;
        List<Release> releases = TimelineUtils.generateYears(startYear);
        assertNotNull(releases);
        assertFalse(releases.isEmpty());
    }

    @Test
    public void testGenerateQuartersAndMonths() {
        int startYear = 2020;
        List<Release> releases = TimelineUtils.generateQuartersAndMonths(startYear);
        assertNotNull(releases);
        assertFalse(releases.isEmpty());
    }

    @Test
    public void testGenerateYearsAndQuarters() {
        int startYear = 2020;
        List<Release> releases = TimelineUtils.generateYearsAndQuarters(startYear);
        assertNotNull(releases);
        assertFalse(releases.isEmpty());
    }

    @Test
    public void testCreateCalendarForStartYear() {
        int startYear = 2020;
        Calendar calendar = TimelineUtils.createCalendarForStartYear(startYear);
        assertNotNull(calendar);
        assertEquals(startYear, calendar.get(Calendar.YEAR));
    }

}