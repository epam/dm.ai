// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.report.freemarker.DevProductivityReport;
import lombok.Data;

@Data
public class ProductivityDataResult {
    private ProductivityAnalyticsData analytics;
    private DevProductivityReport productivityReport;
    private int ticketsCount;
    private String reportName;
    private String filter;

    public ProductivityDataResult() {
    }
}

