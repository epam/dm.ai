// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.List;

public class TimeGroupingConfig {
    private String type;
    private List<TimePeriod> periods;
    private String implementation;
    private int dayShift; // 0 = default (start from startDate), 1 = shift 1 day, etc.

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<TimePeriod> getPeriods() { return periods; }
    public void setPeriods(List<TimePeriod> periods) { this.periods = periods; }
    public String getImplementation() { return implementation; }
    public void setImplementation(String implementation) { this.implementation = implementation; }
    public int getDayShift() { return dayShift; }
    public void setDayShift(int dayShift) { this.dayShift = dayShift; }
}
