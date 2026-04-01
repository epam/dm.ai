// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.excel;

import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.team.IEmployees;

public class ExcelMetric extends Metric {

    public ExcelMetric(String metricName, IEmployees employees, String fileName, String whoColumn, String whenColumn, String weightColumn, double weightMultiplier) {
        super(metricName, true, true, null, new ExcelMetricSource(employees, fileName, whoColumn, whenColumn, weightColumn, weightMultiplier));
    }

}
