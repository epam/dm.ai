// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReleaseTest {

    private Release release;
    private Sprint sprint1;
    private Sprint sprint2;
    private Week week1;
    private Week week2;

    @Before
    public void setUp() {
        sprint1 = mock(Sprint.class);
        sprint2 = mock(Sprint.class);

        week1 = mock(Week.class);
        week2 = mock(Week.class);

        when(sprint1.getStartDate()).thenReturn(new Date(1000000000L));
        when(sprint1.getEndDate()).thenReturn(new Date(2000000000L));
        when(sprint2.getStartDate()).thenReturn(new Date(3000000000L));
        when(sprint2.getEndDate()).thenReturn(new Date(4000000000L));

        when(sprint1.getWeeks()).thenReturn(Arrays.asList(week1));
        when(sprint2.getWeeks()).thenReturn(Arrays.asList(week2));

        release = new Release(1, "Release 1", Arrays.asList(sprint1, sprint2));
    }

    @Test
    public void testGetSprints() {
        List<Sprint> sprints = release.getSprints();
        assertEquals(2, sprints.size());
        assertEquals(sprint1, sprints.get(0));
        assertEquals(sprint2, sprints.get(1));
    }

    @Test
    public void testGetIterationsByStyle() {
        List<? extends ReportIteration> iterations = release.getIterationsByStyle(Release.Style.BY_SPRINTS);
        assertEquals(2, iterations.size());

        iterations = release.getIterationsByStyle(Release.Style.BY_WEEKS);
        assertEquals(2, iterations.size());

        try {
            release.getIterationsByStyle(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetIterationName() {
        assertEquals("Release 1", release.getIterationName());
    }

    @Test
    public void testGetId() {
        assertEquals(1, release.getId());
    }

    @Test
    public void testGetStartDate() {
        assertEquals(new Date(1000000000L), release.getStartDate());
    }

    @Test
    public void testGetEndDate() {
        assertEquals(new Date(4000000000L), release.getEndDate());
    }

    @Test
    public void testGetStartDateAsCalendar() {
        Calendar calendar = release.getStartDateAsCalendar();
        assertEquals(1000000000L, calendar.getTimeInMillis());
    }

    @Test
    public void testGetEndDateAsCalendar() {
        Calendar calendar = release.getEndDateAsCalendar();
        assertEquals(4000000000L, calendar.getTimeInMillis());
    }

    @Test
    public void testIsReleased() {
        assertFalse(release.isReleased());
    }

    @Test
    public void testGetStartDateAsString() {
        when(sprint1.getStartDateAsString()).thenReturn("Start Date String");
        assertEquals("Start Date String", release.getStartDateAsString());
    }

    @Test
    public void testGetEndDateAsString() {
        when(sprint2.getEndDateAsString()).thenReturn("End Date String");
        assertEquals("End Date String", release.getEndDateAsString());
    }

    @Test
    public void testIsMatchedToReleaseTimelines() {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(1500000000L);
        assertTrue(release.isMatchedToReleaseTimelines(date));

        date.setTimeInMillis(5000000000L);
        assertFalse(release.isMatchedToReleaseTimelines(date));
    }

    @Test
    public void testBeforeReleaseStart() {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(500000000L);
        assertTrue(release.beforeReleaseStart(date));

        date.setTimeInMillis(1500000000L);
        assertFalse(release.beforeReleaseStart(date));
    }

    @Test
    public void testAfterReleaseEnds() {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(5000000000L);
        assertTrue(release.afterReleaseEnds(date));

        date.setTimeInMillis(3500000000L);
        assertFalse(release.afterReleaseEnds(date));
    }

}