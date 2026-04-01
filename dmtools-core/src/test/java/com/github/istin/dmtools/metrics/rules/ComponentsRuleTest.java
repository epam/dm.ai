// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.team.Employees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;

public class ComponentsRuleTest {

    @Mock
    private TrackerClient mockJiraClient;

    @Mock
    private ITicket mockTicket;

    @Mock
    private Employees mockEmployees;


    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    public void testRound() {
        double result = ComponentsRule.round(2.34567, 2);
        assertEquals(2.35, result, 0.0);
    }
}