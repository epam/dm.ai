// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.report.DevChart;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class DevProductivityReport extends GenericReport {

    @Setter
    private List<String> headers = new ArrayList<>();

    @Setter
    private boolean isDarkMode;

    public boolean getIsDarkMode() {
        return isDarkMode;
    }

    private int ticketCounter;

    @Setter
    private String htmlBeforeTimeline = "";

    @Setter
    private List<DevChart> listDevCharts = new ArrayList<>();


    public void setTicketsCount(int ticketCounter) {
        this.ticketCounter = ticketCounter;
    }

    public void shiftTimelineStarts(int shiftTimelineStarts) {

    }
}
