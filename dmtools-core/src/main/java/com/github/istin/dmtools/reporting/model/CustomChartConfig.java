// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

import java.util.List;

public class CustomChartConfig {
    private String title;
    private String type;
    private List<String> metrics;

    public CustomChartConfig() {}

    public CustomChartConfig(String title, String type, List<String> metrics) {
        this.title = title;
        this.type = type;
        this.metrics = metrics;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<String> getMetrics() { return metrics; }
    public void setMetrics(List<String> metrics) { this.metrics = metrics; }
}
