// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.HashMap;
import java.util.Map;

public class ContributorMetrics {
    private Map<String, MetricSummary> metrics;
    private double score;
    
    public ContributorMetrics() { this.metrics = new HashMap<>(); }
    public ContributorMetrics(Map<String, MetricSummary> metrics, double score) {
        this.metrics = metrics; this.score = score;
    }
    
    public Map<String, MetricSummary> getMetrics() { return metrics; }
    public void setMetrics(Map<String, MetricSummary> metrics) { this.metrics = metrics; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
