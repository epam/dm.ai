// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeyTimeTest {

    private KeyTime keyTime;
    private Calendar mockCalendar;

    @Before
    public void setUp() {
        mockCalendar = Mockito.mock(Calendar.class);
        keyTime = new KeyTime("testKey", mockCalendar, "testWho");
    }

    @Test
    public void testGetKey() {
        assertEquals("testKey", keyTime.getKey());
    }

    @Test
    public void testGetWhen() {
        assertEquals(mockCalendar, keyTime.getWhen());
    }

    @Test
    public void testSetWhen() {
        Calendar newCalendar = Mockito.mock(Calendar.class);
        keyTime.setWhen(newCalendar);
        assertEquals(newCalendar, keyTime.getWhen());
    }

    @Test
    public void testGetWho() {
        assertEquals("testWho", keyTime.getWho());
    }

    @Test
    public void testSetWho() {
        keyTime.setWho("newWho");
        assertEquals("newWho", keyTime.getWho());
    }

    @Test
    public void testGetWeight() {
        assertEquals(1.0, keyTime.getWeight(), 0.0);
    }

    @Test
    public void testSetWeight() {
        keyTime.setWeight(2.5);
        assertEquals(2.5, keyTime.getWeight(), 0.0);
    }

    @Test
    public void testConstructorWithTwoParameters() {
        KeyTime keyTimeTwoParams = new KeyTime("keyTwo", mockCalendar);
        assertEquals("keyTwo", keyTimeTwoParams.getKey());
        assertEquals(mockCalendar, keyTimeTwoParams.getWhen());
        assertNull(keyTimeTwoParams.getWho());
    }
}