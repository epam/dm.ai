// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class SprintTest {

    private Sprint sprint;
    private Date startDate;
    private Date endDate;

    @Before
    public void setUp() {
        startDate = new Date();
        endDate = new Date(startDate.getTime() + 1000000); // 1,000,000 milliseconds later
        sprint = new Sprint(1, startDate, endDate, 10);
    }

    @Test
    public void testGetCapacity() {
        assertEquals(10, sprint.getCapacity());
    }

    @Test
    public void testSetCapacity() {
        sprint.setCapacity(20);
        assertEquals(20, sprint.getCapacity());
    }

    @Test
    public void testGetNumber() {
        assertEquals(1, sprint.getNumber());
    }

    @Test
    public void testSetNumber() {
        sprint.setNumber(2);
        assertEquals(2, sprint.getNumber());
    }

    @Test
    public void testSetStartDateAsString() {
        String newStartDate = "2023-10-01";
        sprint.setStartDateAsString(newStartDate);
        assertEquals(newStartDate, sprint.getStartDateAsString());
    }

    @Test
    public void testSetEndDateAsString() {
        String newEndDate = "2023-10-10";
        sprint.setEndDateAsString(newEndDate);
        assertEquals(newEndDate, sprint.getEndDateAsString());
    }

    @Test
    public void testSetIsCurrent() {
        sprint.setIsCurrent(true);
        assertTrue(sprint.getIsCurrent());
    }

    @Test
    public void testGetDays() {
        List<String> days = sprint.getDays();
        assertNotNull(days);
        assertFalse(days.isEmpty());
    }

    @Test
    public void testIsMatchedToSprintTimelines() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        assertTrue(sprint.isMatchedToSprintTimelines(calendar));

        calendar.setTime(endDate);
        assertTrue(sprint.isMatchedToSprintTimelines(calendar));

        calendar.setTime(new Date(endDate.getTime() + 1000000));
        assertFalse(sprint.isMatchedToSprintTimelines(calendar));
    }

    @Test
    public void testGetWeeks() {
        List<Week> weeks = sprint.getWeeks();
        assertNotNull(weeks);
    }

    @Test
    public void testIsReleased() {
        Sprint pastSprint = new Sprint(2, new Date(startDate.getTime() - 2000000), new Date(startDate.getTime() - 1000000), 10);
        assertTrue(pastSprint.isReleased());

        assertFalse(sprint.isReleased());
    }

    @Test
    public void testGetIterationName() {
        assertEquals("1", sprint.getIterationName());
    }

    @Test
    public void testGetId() {
        assertEquals(1, sprint.getId());
    }
}