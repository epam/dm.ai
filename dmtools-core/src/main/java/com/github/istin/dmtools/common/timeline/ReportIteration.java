// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import java.util.Calendar;
import java.util.Date;

public interface ReportIteration {

    String getIterationName();

    int getId();

    Date getStartDate();

    Date getEndDate();

    boolean isReleased();

    class Impl {
        public static boolean isMatchedToIterationTimeline(ReportIteration reportIteration, Calendar date) {
            Date startDate = reportIteration.getStartDate();
            Date endDate = reportIteration.getEndDate();
            return startDate.getTime() <= date.getTimeInMillis() && endDate.getTime() >= date.getTimeInMillis();
        }
    }
}
