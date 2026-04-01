// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.timeinstatus;

import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TimeInStatusTest {

    private TrackerClient<ITicket> trackerClientMock;
    private TimeInStatus timeInStatus;
    private ITicket ticketMock;
    private IChangelog changelogMock;
    private IHistory historyMock;
    private IHistoryItem historyItemMock;

    @Before
    public void setUp() {
        trackerClientMock = mock(TrackerClient.class);
        timeInStatus = new TimeInStatus(trackerClientMock);
        ticketMock = mock(ITicket.class);
        changelogMock = mock(IChangelog.class);
        historyMock = mock(IHistory.class);
        historyItemMock = mock(IHistoryItem.class);
    }

    @Test
    public void testGetFinalStatuses() {
        List<String> finalStatuses = timeInStatus.getFinalStatuses();
        assertNotNull(finalStatuses);
        assertTrue(finalStatuses.contains("done"));
        assertTrue(finalStatuses.contains(Status.REJECTED.toLowerCase()));
        assertTrue(finalStatuses.contains(Status.CANCELLED.toLowerCase()));
    }

    @Test
    public void testSetFinalStatuses() {
        List<String> newFinalStatuses = Arrays.asList("completed", "closed");
        timeInStatus.setFinalStatuses(newFinalStatuses);
        assertEquals(newFinalStatuses, timeInStatus.getFinalStatuses());
    }

}