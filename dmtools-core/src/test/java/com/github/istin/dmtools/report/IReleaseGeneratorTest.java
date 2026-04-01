// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.common.timeline.Release;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IReleaseGeneratorTest {

    @Test
    public void testGenerate() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        List<Release> mockReleases = mock(List.class);
        when(releaseGenerator.generate()).thenReturn(mockReleases);

        List<Release> releases = releaseGenerator.generate();
        assertNotNull(releases);
        assertEquals(mockReleases, releases);
    }

    @Test
    public void testGetTypeOfReleases() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        when(releaseGenerator.getTypeOfReleases()).thenReturn(1);

        int typeOfReleases = releaseGenerator.getTypeOfReleases();
        assertEquals(1, typeOfReleases);
    }

    @Test
    public void testGetCurrentIteration() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        Release mockRelease = mock(Release.class);
        when(releaseGenerator.getCurrentIteration()).thenReturn(mockRelease);

        Release currentIteration = releaseGenerator.getCurrentIteration();
        assertNotNull(currentIteration);
        assertEquals(mockRelease, currentIteration);
    }

    @Test
    public void testGetStartSprint() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        when(releaseGenerator.getStartSprint()).thenReturn(5);

        int startSprint = releaseGenerator.getStartSprint();
        assertEquals(5, startSprint);
    }

    @Test
    public void testGetStartFixVersion() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        when(releaseGenerator.getStartFixVersion()).thenReturn(10);

        int startFixVersion = releaseGenerator.getStartFixVersion();
        assertEquals(10, startFixVersion);
    }

    @Test
    public void testGetExtraSprintTimeline() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        when(releaseGenerator.getExtraSprintTimeline()).thenReturn(2);

        int extraSprintTimeline = releaseGenerator.getExtraSprintTimeline();
        assertEquals(2, extraSprintTimeline);
    }

    @Test
    public void testGetStartDate() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        Calendar mockCalendar = mock(Calendar.class);
        when(releaseGenerator.getStartDate()).thenReturn(mockCalendar);

        Calendar startDate = releaseGenerator.getStartDate();
        assertNotNull(startDate);
        assertEquals(mockCalendar, startDate);
    }

    @Test
    public void testGetMaxTime() {
        IReleaseGenerator releaseGenerator = mock(IReleaseGenerator.class);
        when(releaseGenerator.getMaxTime()).thenReturn(1000L);

        long maxTime = releaseGenerator.getMaxTime();
        assertEquals(1000L, maxTime);
    }
}