// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import com.github.istin.dmtools.common.utils.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class IPullRequestTest {

    private IPullRequest pullRequestMock;

    @Before
    public void setUp() {
        pullRequestMock = Mockito.mock(IPullRequest.class);
    }

    @Test
    public void testUpgradeTitleIfWip() {
        when(pullRequestMock.getTitle()).thenReturn("[WIP] Initial Title");
        String newTitle = "New Title";
        String upgradedTitle = IPullRequest.Utils.upgradeTitleIfWip(pullRequestMock, newTitle);
        assertEquals("[WIP] New Title", upgradedTitle);
    }

    @Test
    public void testIsWIP() {
        when(pullRequestMock.getTitle()).thenReturn("[WIP] Work in Progress");
        assertTrue(IPullRequest.Utils.isWIP(pullRequestMock));
    }

    @Test
    public void testUpgradeTitleToWIP() {
        String newTitle = "Feature Implementation";
        String upgradedTitle = IPullRequest.Utils.upgradeTitleToWIP(newTitle);
        assertEquals("[WIP] Feature Implementation", upgradedTitle);
    }

    @Test
    public void testGetCreatedDateAsCalendar() {
        Long createdDate = 1633036800000L; // Example timestamp
        when(pullRequestMock.getCreatedDate()).thenReturn(createdDate);
        Calendar calendar = IPullRequest.Utils.getCreatedDateAsCalendar(pullRequestMock);
        assertEquals(createdDate.longValue(), calendar.getTimeInMillis());
    }

    @Test
    public void testGetClosedDateAsCalendar() {
        Long closedDate = 1633123200000L; // Example timestamp
        when(pullRequestMock.getClosedDate()).thenReturn(closedDate);
        Calendar calendar = IPullRequest.Utils.getClosedDateAsCalendar(pullRequestMock);
        assertEquals(closedDate.longValue(), calendar.getTimeInMillis());
    }


    @Test
    public void testGetUpdatedDateAsCalendar() {
        Long updatedDate = 1633209600000L; // Example timestamp
        when(pullRequestMock.getUpdatedDate()).thenReturn(updatedDate);
        Calendar calendar = IPullRequest.Utils.getUpdatedDateAsCalendar(pullRequestMock);
        assertEquals(updatedDate.longValue(), calendar.getTimeInMillis());
    }
}