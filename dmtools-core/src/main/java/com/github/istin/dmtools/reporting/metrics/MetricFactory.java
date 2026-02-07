package com.github.istin.dmtools.reporting.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.metrics.rules.BugsCreatorsRule;
import com.github.istin.dmtools.metrics.rules.TicketMovedToStatusRule;
import com.github.istin.dmtools.metrics.rules.TicketCreatorsRule;
import com.github.istin.dmtools.metrics.source.PullRequestsMetricSource;
import com.github.istin.dmtools.metrics.source.PullRequestsLOCMetricSource;
import com.github.istin.dmtools.metrics.source.SourceCodeCommitsMetricSource;
import com.github.istin.dmtools.team.Employees;
import com.github.istin.dmtools.team.IEmployees;

import java.util.*;

/**
 * Factory for creating Metric instances from configuration
 */
public class MetricFactory {
    private final TrackerClient trackerClient;
    private final SourceCode sourceCode;
    private final IEmployees employees;

    public MetricFactory(TrackerClient trackerClient, SourceCode sourceCode) {
        this(trackerClient, sourceCode, null);
    }

    public MetricFactory(TrackerClient trackerClient, SourceCode sourceCode, IEmployees employees) {
        this.trackerClient = trackerClient;
        this.sourceCode = sourceCode;
        this.employees = employees != null ? employees : Employees.getInstance();
    }

    public Metric createMetric(String metricName, Map<String, Object> metricParams, String dataSourceType) throws Exception {
        return createMetric(metricName, metricParams, dataSourceType, null);
    }

    public Metric createMetric(String metricName, Map<String, Object> metricParams, String dataSourceType, Map<String, Object> dataSourceParams) throws Exception {
        // Merge: data source params as defaults, metric params override
        Map<String, Object> params = new HashMap<>();
        if (dataSourceParams != null) {
            params.putAll(dataSourceParams);
        }
        params.putAll(metricParams);

        String label = (String) params.getOrDefault("label", metricName);
        boolean isWeight = (boolean) params.getOrDefault("isWeight", false);
        boolean isPersonalized = (boolean) params.getOrDefault("isPersonalized", false);
        double divider = parseDivider(params.get("divider"));

        Metric metric;
        if ("tracker".equals(dataSourceType)) {
            TrackerRule rule = createTrackerRule(metricName, params);
            metric = new Metric(label, isWeight, rule);
        } else if ("pullRequests".equals(dataSourceType) || "commits".equals(dataSourceType)) {
            SourceCollector collector = createSourceCollector(metricName, params);
            metric = new Metric(label, isWeight, isPersonalized, collector);
        } else {
            throw new IllegalArgumentException("Unknown data source type: " + dataSourceType);
        }

        if (divider != 1.0) {
            metric.setDivider(divider);
        }
        return metric;
    }

    private double parseDivider(Object dividerParam) {
        if (dividerParam == null) return 1.0;
        if (dividerParam instanceof Number) return ((Number) dividerParam).doubleValue();
        try {
            return Double.parseDouble(dividerParam.toString());
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    private TrackerRule createTrackerRule(String metricName, Map<String, Object> params) {
        switch (metricName) {
            case "BugsCreatorsRule":
                String project = (String) params.get("project");
                return new BugsCreatorsRule(project, employees);

            case "TicketMovedToStatusRule":
                Object statusesObj = params.get("statuses");
                String[] statuses;
                if (statusesObj instanceof List) {
                    List<String> statusList = (List<String>) statusesObj;
                    statuses = statusList.toArray(new String[0]);
                } else if (statusesObj instanceof String[]) {
                    statuses = (String[]) statusesObj;
                } else {
                    statuses = new String[]{statusesObj.toString()};
                }
                return new TicketMovedToStatusRule(statuses);

            case "TicketCreatorsRule":
                String creatorProject = (String) params.getOrDefault("project", null);
                return new TicketCreatorsRule(creatorProject, employees);

            default:
                throw new IllegalArgumentException("Unknown tracker rule: " + metricName);
        }
    }

    private SourceCollector createSourceCollector(String metricName, Map<String, Object> params) {
        if (sourceCode == null) {
            throw new IllegalArgumentException("SourceCode is not configured");
        }

        String workspace = (String) params.getOrDefault("workspace", sourceCode.getDefaultWorkspace());
        String repository = (String) params.getOrDefault("repository", sourceCode.getDefaultRepository());
        String branch = (String) params.getOrDefault("branch", sourceCode.getDefaultBranch());
        String since = (String) params.getOrDefault("since", null);

        switch (metricName) {
            case "PullRequestsMetricSource":
                Calendar prStartDate = parseDateParam(params.get("since"));
                return new PullRequestsMetricSource(workspace, repository, sourceCode, employees, prStartDate);

            case "CommitsMetricSource":
                return new SourceCodeCommitsMetricSource(workspace, repository, branch, since, sourceCode, employees);

            case "LinesOfCodeMetricSource":
                return new PullRequestsLOCMetricSource(workspace, repository, branch, since, sourceCode, employees);

            default:
                throw new IllegalArgumentException("Unknown source collector: " + metricName);
        }
    }

    private Calendar parseDateParam(Object dateParam) {
        if (dateParam == null) {
            return null;
        }

        if (dateParam instanceof Calendar) {
            return (Calendar) dateParam;
        }

        String dateStr = dateParam.toString();
        Calendar cal = Calendar.getInstance();
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            cal.setTime(sdf.parse(dateStr));
        } catch (Exception e) {
            System.err.println("Failed to parse date: " + dateStr);
            return null;
        }
        return cal;
    }
}
