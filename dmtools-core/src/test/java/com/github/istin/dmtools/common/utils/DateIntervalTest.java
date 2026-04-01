// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class DateIntervalTest {

    private Calendar from;
    private Calendar to;
    private DateInterval dateInterval;

    @Before
    public void setUp() {
        from = Mockito.mock(Calendar.class);
        to = Mockito.mock(Calendar.class);
        dateInterval = new DateInterval(from, to);
    }

    @Test
    public void testGetFrom() {
        assertEquals(from, dateInterval.getFrom());
    }

    @Test
    public void testGetTo() {
        assertEquals(to, dateInterval.getTo());
    }
}