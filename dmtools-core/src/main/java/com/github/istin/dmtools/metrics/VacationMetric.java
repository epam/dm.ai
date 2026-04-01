// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.metrics.source.CommonSourceCollector;
import com.github.istin.dmtools.metrics.source.VacationsMetricSource;
import com.github.istin.dmtools.team.IEmployees;
import org.jetbrains.annotations.NotNull;

public class VacationMetric extends Metric {

    private static final String NAME = "Vacation ";
    private boolean isDays;

    public VacationMetric(boolean isPersonlized, boolean isDays) {
        this(isPersonlized, isDays, null);
    }

    public VacationMetric(boolean isPersonlized, boolean isDays, IEmployees employees) {
        this(isPersonlized, isDays, employees, new VacationsMetricSource(employees, null, isDays));
    }

    public VacationMetric(boolean isPersonlized, boolean isDays, IEmployees employees, CommonSourceCollector commonSourceCollector) {
        super(getName(isDays), true, isPersonlized, null, commonSourceCollector);
        this.isDays = isDays;
    }

    @NotNull
    public static String getName(boolean isDays) {
        return isDays ? NAME + "days" : NAME + "SPs";
    }

    public boolean isDays() {
        return isDays;
    }

    @Override
    public String getName() {
        return getName(isDays);
    }
}
