package com.github.istin.dmtools.reporting.model;

import java.util.ArrayList;
import java.util.List;

public class ReportOutput {
    private String reportName;
    private String generatedAt;
    private String startDate;
    private String endDate;
    private List<TimePeriodResult> timePeriods;
    private AggregatedResult aggregated;
    private List<String> weightMetrics;
    
    public ReportOutput() {}
    public ReportOutput(String reportName, String generatedAt, String startDate, String endDate,
                        List<TimePeriodResult> timePeriods, AggregatedResult aggregated) {
        this.reportName = reportName; this.generatedAt = generatedAt;
        this.startDate = startDate; this.endDate = endDate;
        this.timePeriods = timePeriods; this.aggregated = aggregated;
    }
    
    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public List<TimePeriodResult> getTimePeriods() { return timePeriods; }
    public void setTimePeriods(List<TimePeriodResult> timePeriods) { this.timePeriods = timePeriods; }
    public AggregatedResult getAggregated() { return aggregated; }
    public void setAggregated(AggregatedResult aggregated) { this.aggregated = aggregated; }
    public List<String> getWeightMetrics() { return weightMetrics; }
    public void setWeightMetrics(List<String> weightMetrics) { this.weightMetrics = weightMetrics; }
}
