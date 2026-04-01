// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.List;
import java.util.Map;

public class TimePeriodResult {
    private String name;
    private String startDate;
    private String endDate;
    private Map<String, MetricSummary> metrics;
    private double score;
    private List<DatasetItem> dataset;
    private Map<String, ContributorMetrics> contributorBreakdown;  // Per-contributor metrics for this period

    public TimePeriodResult() {}

    public TimePeriodResult(String name, String startDate, String endDate,
                            Map<String, MetricSummary> metrics, double score, List<DatasetItem> dataset) {
        this.name = name; this.startDate = startDate; this.endDate = endDate;
        this.metrics = metrics; this.score = score; this.dataset = dataset;
    }

    public TimePeriodResult(String name, String startDate, String endDate,
                            Map<String, MetricSummary> metrics, double score, List<DatasetItem> dataset,
                            Map<String, ContributorMetrics> contributorBreakdown) {
        this.name = name; this.startDate = startDate; this.endDate = endDate;
        this.metrics = metrics; this.score = score; this.dataset = dataset;
        this.contributorBreakdown = contributorBreakdown;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Map<String, MetricSummary> getMetrics() { return metrics; }
    public void setMetrics(Map<String, MetricSummary> metrics) { this.metrics = metrics; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public List<DatasetItem> getDataset() { return dataset; }
    public void setDataset(List<DatasetItem> dataset) { this.dataset = dataset; }
    public Map<String, ContributorMetrics> getContributorBreakdown() { return contributorBreakdown; }
    public void setContributorBreakdown(Map<String, ContributorMetrics> contributorBreakdown) { this.contributorBreakdown = contributorBreakdown; }
}
