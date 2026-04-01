// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.estimations;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.model.ITicket;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JEstimatorTest {

    private JEstimator.AIEstimatedTicket aiEstimatedTicket;
    private ITicket ticketMock;

    @Before
    public void setUp() {
        ticketMock = mock(ITicket.class);
        aiEstimatedTicket = new JEstimator.AIEstimatedTicket("storyPointsField", ticketMock);
    }

    @Test
    public void testGetAiEstimation() {
        aiEstimatedTicket.setAiEstimation(5.0);
        assertEquals(Double.valueOf(5.0), aiEstimatedTicket.getAiEstimation());
    }

    @Test
    public void testSetAiEstimation() {
        aiEstimatedTicket.setAiEstimation(10.0);
        assertEquals(Double.valueOf(10.0), aiEstimatedTicket.getAiEstimation());
    }

    @Test
    public void testGetWeight() {
        Fields fieldsMock = mock(Fields.class);
        when(ticketMock.getFields()).thenReturn(fieldsMock);
        when(fieldsMock.getInt("storyPointsField")).thenReturn(3);

        assertEquals(3.0, aiEstimatedTicket.getWeight(), 0.0);
    }

    @Test
    public void testGetTicketDescription() {
        when(ticketMock.getTicketDescription()).thenReturn("Description");
        assertEquals("Description", aiEstimatedTicket.getTicketDescription());

        when(ticketMock.getTicketDescription()).thenReturn(null);
        assertEquals("", aiEstimatedTicket.getTicketDescription());
    }

}