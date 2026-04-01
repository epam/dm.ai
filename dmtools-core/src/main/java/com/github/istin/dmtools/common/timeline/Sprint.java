// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import com.github.istin.dmtools.common.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Sprint implements ReportIteration {

    private int number;

    private String startDateAsString;

    private String endDateAsString;

    private int capacity = -1;
    private Date startDate;
    private Date endDate;

    public boolean getIsCurrent() {
        return isCurrent;
    }

    private boolean isCurrent;

    public Sprint() {
    }

    public Sprint(int number, String startDateAsString, String endDateAsString, int capacity) {
        this.number = number;
        this.startDateAsString = startDateAsString;
        this.endDateAsString = endDateAsString;
        this.capacity = capacity;
    }

    public Sprint(int number, Date startDate, Date endDate, int capacity) {
        this.number = number;
        this.startDate = startDate;
        this.startDateAsString = DateUtils.formatToJiraDate(startDate.getTime());
        this.endDate = endDate;
        this.endDateAsString = DateUtils.formatToJiraDate(endDate.getTime());
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getStartDateAsString() {
        return startDateAsString;
    }

    public void setStartDateAsString(String startDate) {
        this.startDateAsString = startDate;
    }

    public String getEndDateAsString() {
        return endDateAsString;
    }

    public void setEndDateAsString(String endDateAsString) {
        this.endDateAsString = endDateAsString;
    }

    public void setIsCurrent(boolean isCurrent) {
        this.isCurrent = isCurrent;
    }

    public Sprint setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    public Sprint setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    public List<String> getDays() {
        List<String> list = new ArrayList<>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startDate.getTime());
        while (endDate.getTime() > calendar.getTimeInMillis()) {
            list.add(simpleDateFormat.format(new Date(calendar.getTimeInMillis())));
            calendar.add(Calendar.DATE, 1);
        }
        return list;
    }

    public boolean isMatchedToSprintTimelines(Calendar date) {
        return startDate.getTime() <= date.getTimeInMillis() && endDate.getTime() >= date.getTimeInMillis();
    }

    public List<Week> getWeeks() {
        return Week.createBasedOnSprint(this);
    }

    @Override
    public Date getStartDate() {
        return startDate;
    }

    @Override
    public Date getEndDate() {
        return endDate;
    }

    @Override
    public boolean isReleased() {
        return getEndDate().getTime() < System.currentTimeMillis();
    }

    @Override
    public String getIterationName() {
        return getNumber() + "";
    }

    @Override
    public int getId() {
        return getNumber();
    }
}
