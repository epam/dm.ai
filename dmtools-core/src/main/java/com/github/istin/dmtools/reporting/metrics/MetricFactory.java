package com.github.istin.dmtools.reporting.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.metrics.source.FigmaCommentsMetricSource;
import com.github.istin.dmtools.metrics.rules.BugsCreatorsRule;
import com.github.istin.dmtools.metrics.rules.CommentsWrittenRule;
import com.github.istin.dmtools.metrics.rules.TicketFieldsChangesRule;
import com.github.istin.dmtools.metrics.rules.TicketFieldsTokensChangedRule;
import com.github.istin.dmtools.metrics.rules.TicketFieldsTokensRetainedRule;
import com.github.istin.dmtools.metrics.rules.TicketMovedToStatusRule;
import com.github.istin.dmtools.metrics.rules.TicketCreatorsRule;
import com.github.istin.dmtools.metrics.source.PullRequestsMetricSource;
import com.github.istin.dmtools.metrics.source.PullRequestsLOCMetricSource;
import com.github.istin.dmtools.metrics.source.PullRequestsCommentsMetricSource;
import com.github.istin.dmtools.metrics.source.PullRequestsApprovalsMetricSource;
import com.github.istin.dmtools.metrics.source.PullRequestsMergedByMetricSource;
import com.github.istin.dmtools.metrics.source.PullRequestsDeclinedMetricSource;
import com.github.istin.dmtools.metrics.source.SourceCodeCommitsMetricSource;
import com.github.istin.dmtools.csv.CsvMetricSource;
import com.github.istin.dmtools.team.Employees;
import com.github.istin.dmtools.team.IEmployees;
import com.github.istin.dmtools.figma.FigmaClient;

import java.util.*;

/**
 * Factory for creating Metric instances from configuration
 */
public class MetricFactory {
    private final TrackerClient trackerClient;
    private final SourceCode sourceCode;
    private final FigmaClient figmaClient;
    private final IEmployees employees;
    private final String reportStartDate;

    public MetricFactory(TrackerClient trackerClient, SourceCode sourceCode) {
        this(trackerClient, sourceCode, null, null, null);
    }

    public MetricFactory(TrackerClient trackerClient, SourceCode sourceCode, IEmployees employees) {
        this(trackerClient, sourceCode, null, employees, null);
    }

    public MetricFactory(TrackerClient trackerClient, SourceCode sourceCode, IEmployees employees, String reportStartDate) {
        this(trackerClient, sourceCode, null, employees, reportStartDate);
    }

    public MetricFactory(TrackerClient trackerClient, SourceCode sourceCode, FigmaClient figmaClient, IEmployees employees, String reportStartDate) {
        this.trackerClient = trackerClient;
        this.sourceCode = sourceCode;
        this.figmaClient = figmaClient;
        this.employees = employees != null ? employees : Employees.getInstance();
        this.reportStartDate = reportStartDate;
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
        } else if ("figma".equals(dataSourceType)) {
            SourceCollector collector = createFigmaCollector(metricName, params);
            metric = new Metric(label, isWeight, isPersonalized, collector);
        } else if ("csv".equals(dataSourceType)) {
            SourceCollector collector = createCsvCollector(params);
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

            case "CommentsWrittenRule": {
                String commentsRegex = (String) params.getOrDefault("commentsRegex", null);
                return new CommentsWrittenRule(employees, commentsRegex);
            }

            case "TicketFieldsChangesRule": {
                Object fieldsObj = params.get("filterFields");
                String[] filterFields = null;
                if (fieldsObj instanceof List) {
                    List<String> list = (List<String>) fieldsObj;
                    filterFields = list.toArray(new String[0]);
                } else if (fieldsObj instanceof String[]) {
                    filterFields = (String[]) fieldsObj;
                } else if (fieldsObj instanceof String) {
                    filterFields = new String[]{(String) fieldsObj};
                }
                boolean isSimilarity = (boolean) params.getOrDefault("isSimilarity", false);
                boolean isCollectionIfByCreator = (boolean) params.getOrDefault("isCollectionIfByCreator", false);
                boolean includeInitial = (boolean) params.getOrDefault("includeInitial", false);
                boolean useDivider = (boolean) params.getOrDefault("useDivider", true);
                String creatorFilterMode = (String) params.getOrDefault("creatorFilterMode", null);
                String customName = (String) params.getOrDefault("label", null);
                if (customName != null) {
                    if (creatorFilterMode != null) {
                        return new TicketFieldsChangesRule(customName, employees, filterFields, isSimilarity, isCollectionIfByCreator, includeInitial, useDivider, creatorFilterMode);
                    }
                    return new TicketFieldsChangesRule(customName, employees, filterFields, isSimilarity, isCollectionIfByCreator, includeInitial, useDivider);
                }
                if (creatorFilterMode != null) {
                    return new TicketFieldsChangesRule(null, employees, filterFields, isSimilarity, isCollectionIfByCreator, includeInitial, useDivider, creatorFilterMode);
                }
                return new TicketFieldsChangesRule(employees, filterFields, isSimilarity, isCollectionIfByCreator, includeInitial, useDivider);
            }

            case "TicketFieldsTokensChangedRule": {
                Object fieldsObj = params.get("filterFields");
                String[] filterFields = null;
                if (fieldsObj instanceof List) {
                    List<String> list = (List<String>) fieldsObj;
                    filterFields = list.toArray(new String[0]);
                } else if (fieldsObj instanceof String[]) {
                    filterFields = (String[]) fieldsObj;
                } else if (fieldsObj instanceof String) {
                    filterFields = new String[]{(String) fieldsObj};
                }
                boolean isCollectionIfByCreator = (boolean) params.getOrDefault("isCollectionIfByCreator", false);
                boolean includeInitial = (boolean) params.getOrDefault("includeInitial", false);
                String mode = (String) params.getOrDefault("mode", "mixed");
                String creatorFilterMode = (String) params.getOrDefault("creatorFilterMode", null);
                return new TicketFieldsTokensChangedRule(employees, filterFields, isCollectionIfByCreator, includeInitial, mode, creatorFilterMode);
            }

            case "TicketFieldsTokensRetainedRule": {
                Object fieldsObj = params.get("filterFields");
                String[] filterFields = null;
                if (fieldsObj instanceof List) {
                    List<String> list = (List<String>) fieldsObj;
                    filterFields = list.toArray(new String[0]);
                } else if (fieldsObj instanceof String[]) {
                    filterFields = (String[]) fieldsObj;
                } else if (fieldsObj instanceof String) {
                    filterFields = new String[]{(String) fieldsObj};
                }
                boolean includeInitial = (boolean) params.getOrDefault("includeInitial", false);
                String creatorFilterMode = (String) params.getOrDefault("creatorFilterMode", "all");
                return new TicketFieldsTokensRetainedRule(employees, filterFields, includeInitial, creatorFilterMode);
            }

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

        // Resolve startDate: params["startDate"] -> params["since"] (backward compat) -> reportStartDate
        String startDateStr = (String) params.getOrDefault("startDate", null);
        if (startDateStr == null) startDateStr = (String) params.getOrDefault("since", null);
        if (startDateStr == null) startDateStr = reportStartDate;

        switch (metricName) {
            case "PullRequestsMetricSource": {
                Calendar sd = parseDateParam(startDateStr);
                return new PullRequestsMetricSource(workspace, repository, sourceCode, employees, sd);
            }

            case "CommitsMetricSource":
                return new SourceCodeCommitsMetricSource(workspace, repository, branch, startDateStr, sourceCode, employees);

            case "LinesOfCodeMetricSource":
                return new PullRequestsLOCMetricSource(workspace, repository, branch, startDateStr, sourceCode, employees);

            case "PullRequestsCommentsMetricSource": {
                Calendar sd = parseDateParam(startDateStr);
                boolean isPositive = (boolean) params.getOrDefault("isPositive", true);
                return new PullRequestsCommentsMetricSource(isPositive, workspace, repository, sourceCode, employees, sd);
            }

            case "PullRequestsApprovalsMetricSource": {
                Calendar sd = parseDateParam(startDateStr);
                return new PullRequestsApprovalsMetricSource(workspace, repository, sourceCode, employees, sd);
            }

            case "PullRequestsMergedByMetricSource": {
                Calendar sd = parseDateParam(startDateStr);
                return new PullRequestsMergedByMetricSource(workspace, repository, sourceCode, employees, sd);
            }

            case "PullRequestsDeclinedMetricSource": {
                Calendar sd = parseDateParam(startDateStr);
                return new PullRequestsDeclinedMetricSource(workspace, repository, sourceCode, employees, sd);
            }

            default:
                throw new IllegalArgumentException("Unknown source collector: " + metricName);
        }
    }

    private SourceCollector createFigmaCollector(String metricName, Map<String, Object> params) {
        if (figmaClient == null) {
            throw new IllegalArgumentException("Figma client is not configured");
        }
        String[] files = parseFiles(params != null ? params.get("files") : null);
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Figma data source requires 'files' parameter");
        }
        switch (metricName) {
            case "FigmaCommentsMetricSource":
            case "FigmaCommentMetric":
                return new FigmaCommentsMetricSource(employees, null, figmaClient, files);
            default:
                throw new IllegalArgumentException("Unknown figma source collector: " + metricName);
        }
    }

    private SourceCollector createCsvCollector(Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("CSV data source requires 'filePath' parameter");
        }
        String whoColumn = (String) params.getOrDefault("whoColumn", null);
        String whenColumn = (String) params.getOrDefault("whenColumn", "Date");
        String weightColumn = (String) params.get("weightColumn");
        if (weightColumn == null || weightColumn.isEmpty()) {
            throw new IllegalArgumentException("CSV metric requires 'weightColumn' parameter");
        }
        double weightMultiplier = parseDivider(params.get("weightMultiplier"));
        String defaultWho = (String) params.getOrDefault("defaultWho", null);
        String dateFormat = (String) params.getOrDefault("dateFormat", null);
        return new CsvMetricSource(employees, filePath, whoColumn, whenColumn, weightColumn, weightMultiplier, defaultWho, dateFormat);
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

    private String[] parseFiles(Object filesParam) {
        if (filesParam == null) return null;
        if (filesParam instanceof String[]) return (String[]) filesParam;
        if (filesParam instanceof List) {
            List<?> raw = (List<?>) filesParam;
            List<String> out = new ArrayList<>();
            for (Object v : raw) {
                if (v == null) continue;
                String s = v.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out.isEmpty() ? null : out.toArray(new String[0]);
        }
        String s = filesParam.toString().trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out.isEmpty() ? null : out.toArray(new String[0]);
    }
}
