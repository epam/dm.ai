// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

public class TimePeriod {
    private String name;
    private String start;
    private String end;
    
    public TimePeriod() {}
    public TimePeriod(String name, String start, String end) {
        this.name = name; this.start = start; this.end = end;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
}
