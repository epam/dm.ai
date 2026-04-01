// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class TicketMovedToStatusRuleTest {

    private TrackerClient mockTrackerClient;
    private ITicket mockTicket;
    private Resolution mockResolution;

    @Before
    public void setUp() {
        mockTrackerClient = Mockito.mock(TrackerClient.class);
        mockTicket = Mockito.mock(ITicket.class);
        mockResolution = Mockito.mock(Resolution.class);
    }


    @Test
    public void testCheckWithResolutionRejected() throws Exception {
        when(mockTicket.getResolution()).thenReturn(mockResolution);
        when(mockResolution.isRejected()).thenReturn(true);

        TicketMovedToStatusRule rule = new TicketMovedToStatusRule(new String[]{"done"});
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(new ArrayList<>(), result);
    }


    @Test
    public void testGetStatuses() {
        String[] statuses = {"done", "in progress"};
        TicketMovedToStatusRule rule = new TicketMovedToStatusRule(statuses);

        assertEquals(statuses, rule.getStatuses());
    }
}