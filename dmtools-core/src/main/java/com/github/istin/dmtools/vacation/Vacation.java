// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.vacation;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;

public class Vacation extends JSONModel {

    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String START_DATE = "START DATE";
    public static final String END_DATE = "END DATE";
    public static final String DURATION = "DURATION";

    /** Immutable, thread-safe date formatter for the canonical {@code MM/dd/yy} format. */
    public static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yy");


    public Vacation() {
    }

    public Vacation(String json) throws JSONException {
        super(json);
    }

    public Vacation(JSONObject json) {
        super(json);
    }

    public Double getDuration() {
        return Double.parseDouble(getString(DURATION).replace(",","."));
    }

    public String getStartDate() {
        return getString(START_DATE);
    }

    public Date getStartDateAsDate() {
        String startDate = getStartDate();
        try {
            return toDate(LocalDate.parse(startDate, DateTimeFormatter.ofPattern("MM/dd/yy")));
        } catch (DateTimeParseException e) {
            try {
                return toDate(LocalDate.parse(startDate, DateTimeFormatter.ofPattern("dd.MM.yy")));
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    public Calendar getStartDateAsCalendar() {
        Date date = getStartDateAsDate();
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    public Calendar getEndDateAsCalendar() {
        Date date = getEndDateAsDate();
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    public String getEndDate() {
        return getString(END_DATE);
    }

    public Date getEndDateAsDate() {
        String endDate = getEndDate();
        try {
            return toDate(LocalDate.parse(endDate, DEFAULT_FORMATTER));
        } catch (DateTimeParseException e) {
            try {
                return toDate(LocalDate.parse(endDate, DateTimeFormatter.ofPattern("dd/MM/yy")));
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private static Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public String getName() {
        return getString(EMPLOYEE);
    }


}
