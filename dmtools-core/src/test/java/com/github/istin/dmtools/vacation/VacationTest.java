// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.vacation;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class VacationTest {

    private Vacation vacation;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = mock(JSONObject.class);
        when(jsonObject.getString(Vacation.EMPLOYEE)).thenReturn("John Doe");
        when(jsonObject.getString(Vacation.START_DATE)).thenReturn("01/01/23");
        when(jsonObject.getString(Vacation.END_DATE)).thenReturn("01/10/23");
        when(jsonObject.getString(Vacation.DURATION)).thenReturn("9.0");

        vacation = new Vacation(jsonObject);
    }

    @Test
    public void testGetDuration() {
        Double expectedDuration = 9.0;
        Double actualDuration = vacation.getDuration();
        assertEquals(expectedDuration, actualDuration);
    }

    @Test
    public void testGetStartDate() {
        String expectedStartDate = "01/01/23";
        String actualStartDate = vacation.getStartDate();
        assertEquals(expectedStartDate, actualStartDate);
    }

    @Test
    public void testGetStartDateAsDate() throws ParseException {
        Date expectedDate = new SimpleDateFormat("MM/dd/yy").parse("01/01/23");
        Date actualDate = vacation.getStartDateAsDate();
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testGetStartDateAsCalendar() throws ParseException {
        Calendar expectedCalendar = Calendar.getInstance();
        expectedCalendar.setTime(new SimpleDateFormat("MM/dd/yy").parse("01/01/23"));
        Calendar actualCalendar = vacation.getStartDateAsCalendar();
        assertEquals(expectedCalendar, actualCalendar);
    }

    @Test
    public void testGetEndDate() {
        String expectedEndDate = "01/10/23";
        String actualEndDate = vacation.getEndDate();
        assertEquals(expectedEndDate, actualEndDate);
    }

    @Test
    public void testGetEndDateAsDate() throws ParseException {
        Date expectedDate = new SimpleDateFormat("MM/dd/yy").parse("01/10/23");
        Date actualDate = vacation.getEndDateAsDate();
        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testGetEndDateAsCalendar() throws ParseException {
        Calendar expectedCalendar = Calendar.getInstance();
        expectedCalendar.setTime(new SimpleDateFormat("MM/dd/yy").parse("01/10/23"));
        Calendar actualCalendar = vacation.getEndDateAsCalendar();
        assertEquals(expectedCalendar, actualCalendar);
    }

    @Test
    public void testGetName() {
        String expectedName = "John Doe";
        String actualName = vacation.getName();
        assertEquals(expectedName, actualName);
    }

    @Test
    public void testInvalidDateFormat() throws JSONException {
        when(jsonObject.getString(Vacation.START_DATE)).thenReturn("invalid date");
        Vacation invalidVacation = new Vacation(jsonObject);
        assertNull(invalidVacation.getStartDateAsDate());
    }
}