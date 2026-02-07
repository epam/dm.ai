package com.github.istin.dmtools.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.reporting.datasource.*;
import com.github.istin.dmtools.reporting.metrics.MetricFactory;
import com.github.istin.dmtools.reporting.model.*;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Main report generator using JSON configuration.
 * Uses TrackerClient abstraction - works with Jira, ADO, Rally, etc.
 */
public class ReportGenerator {
    private static final Logger logger = LogManager.getLogger(ReportGenerator.class);

    private final TrackerClient trackerClient;
    private final SourceCode sourceCode;
    private final Set<String> weightMetricLabels = new HashSet<>();
    private final Map<String, Double> metricDividers = new HashMap<>();

    public ReportGenerator(TrackerClient trackerClient, SourceCode sourceCode) {
        this.trackerClient = trackerClient;
        this.sourceCode = sourceCode;
    }

    /**
     * Generate multiple reports (one per time grouping), collecting data only once.
     * Returns the list of generated reports and the file paths they were written to.
     */
    public List<ReportResult> generateReports(ReportConfig config) throws Exception {
        logger.info("Starting multi-grouping report generation: {}", config.getReportName());

        // Resolve endDate: if not specified, use today
        String resolvedEndDate = config.getEndDate();
        if (resolvedEndDate == null || resolvedEndDate.isEmpty()) {
            resolvedEndDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            logger.info("No endDate specified, using today: {}", resolvedEndDate);
        }

        // Build employees from config (if provided)
        IEmployees employees = null;
        if (config.getEmployees() != null || config.getAliases() != null) {
            employees = new ReportEmployees(config.getEmployees(), config.getAliases());
        }

        // Initialize factories
        DataSourceFactory dataSourceFactory = new DataSourceFactory();
        MetricFactory metricFactory = new MetricFactory(trackerClient, sourceCode, employees);

        // 1. Collect data ONCE (expensive: API calls)
        Map<String, Map<String, DataSourceResult>> dataBySourceAndMetric =
            collectDataFromAllSources(config, trackerClient, sourceCode, dataSourceFactory, metricFactory);

        List<TimeGroupingConfig> groupings = config.getTimeGroupings();
        boolean multiGrouping = config.isMultiGrouping();

        List<ReportResult> results = new ArrayList<>();

        // 2. For each time grouping, generate a separate report
        for (TimeGroupingConfig grouping : groupings) {
            List<TimePeriod> periods = generateTimePeriods(config, grouping);

            // Build time period results
            List<TimePeriodResult> periodResults = new ArrayList<>();
            for (TimePeriod period : periods) {
                TimePeriodResult periodResult = buildPeriodResult(period, dataBySourceAndMetric, config);
                periodResults.add(periodResult);
            }

            // Calculate aggregated results
            AggregatedResult aggregated = calculateAggregated(periodResults, config);

            // Build report name: add grouping type suffix if multi-grouping
            String reportName = config.getReportName();
            if (multiGrouping) {
                reportName = reportName + " (" + grouping.getType() + ")";
            }

            ReportOutput output = new ReportOutput(
                reportName,
                LocalDateTime.now().toString(),
                config.getStartDate(),
                resolvedEndDate,
                periodResults,
                aggregated
            );
            if (!weightMetricLabels.isEmpty()) {
                output.setWeightMetrics(new ArrayList<>(weightMetricLabels));
            }

            // Write output with grouping suffix
            String suffix = multiGrouping ? "_" + grouping.getType() : "";
            String jsonPath = writeOutput(output, dataBySourceAndMetric, config, suffix);

            results.add(new ReportResult(output, jsonPath));
            logger.info("Generated report for grouping '{}': {}", grouping.getType(), jsonPath);
        }

        return results;
    }

    /**
     * Generate a single report (backward-compatible). Uses the first time grouping.
     */
    public ReportOutput generateReport(ReportConfig config) throws Exception {
        logger.info("Starting report generation: {}", config.getReportName());
        logger.debug("Using tracker client: {}", trackerClient != null ? trackerClient.getClass().getSimpleName() : "null");

        List<ReportResult> results = generateReports(config);
        return results.isEmpty() ? null : results.get(0).getOutput();
    }

    private Map<String, Map<String, DataSourceResult>> collectDataFromAllSources(
        ReportConfig config,
        TrackerClient trackerClient,
        SourceCode sourceCode,
        DataSourceFactory dataSourceFactory,
        MetricFactory metricFactory
    ) throws Exception {
        Map<String, Map<String, DataSourceResult>> results = new ConcurrentHashMap<>();

        // Process each data source
        for (DataSourceConfig sourceConfig : config.getDataSources()) {
            Map<String, DataSourceResult> sourceResults = new ConcurrentHashMap<>();

            // Create data source
            DataSource dataSource = dataSourceFactory.createDataSource(
                sourceConfig.getName(),
                sourceConfig.getParams(),
                trackerClient,
                sourceCode
            );

            // Process each metric
            for (MetricConfig metricConfig : sourceConfig.getMetrics()) {
                Metric metric = metricFactory.createMetric(
                    metricConfig.getName(),
                    metricConfig.getParams(),
                    sourceConfig.getName(),
                    sourceConfig.getParams()
                );

                DataSourceResult result = new DataSourceResult();

                // Collect KeyTimes
                dataSource.performMetricCollection(metric, (keyTimes, rawMetadata, itemKey) -> {
                    result.addKeyTimes(itemKey, keyTimes);
                    result.addMetadata(itemKey, rawMetadata);
                    logger.debug("Collected {} KeyTimes for item: {}", keyTimes.size(), itemKey);
                });

                String metricLabel = (String) metricConfig.getParams().getOrDefault("label", metricConfig.getName());
                logger.info("Metric '{}': collected {} items", metricLabel, result.getAllKeyTimes().size());
                sourceResults.put(metricLabel, result);

                if (metric.isWeight()) {
                    weightMetricLabels.add(metricLabel);
                }
                if (metric.getDivider() != 1.0) {
                    metricDividers.put(metricLabel, metric.getDivider());
                }
            }

            // Merge into existing results: combine KeyTimes when same metric label
            // appears from multiple data sources (e.g. "Pull Requests Merged" from 2 repos)
            Map<String, DataSourceResult> existingMap =
                results.computeIfAbsent(sourceConfig.getName(), k -> new ConcurrentHashMap<>());
            for (Map.Entry<String, DataSourceResult> entry : sourceResults.entrySet()) {
                existingMap.merge(entry.getKey(), entry.getValue(), (existing, incoming) -> {
                    // Merge incoming keyTimes into existing result
                    for (Map.Entry<String, List<KeyTime>> ktEntry : incoming.getAllKeyTimes().entrySet()) {
                        existing.mergeKeyTimes(ktEntry.getKey(), ktEntry.getValue());
                    }
                    return existing;
                });
            }
        }

        return results;
    }

    private TimePeriodResult buildPeriodResult(
        TimePeriod period,
        Map<String, Map<String, DataSourceResult>> dataBySourceAndMetric,
        ReportConfig config
    ) throws Exception {
        Map<String, MetricSummary> metrics = new HashMap<>();
        List<DatasetItem> dataset = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar periodStart = Calendar.getInstance();
        periodStart.setTime(sdf.parse(period.getStart()));
        // Set start to beginning of day
        periodStart.set(Calendar.HOUR_OF_DAY, 0);
        periodStart.set(Calendar.MINUTE, 0);
        periodStart.set(Calendar.SECOND, 0);
        periodStart.set(Calendar.MILLISECOND, 0);

        Calendar periodEnd = Calendar.getInstance();
        periodEnd.setTime(sdf.parse(period.getEnd()));
        // Set end to end of day so KeyTimes during the entire last day are included
        periodEnd.set(Calendar.HOUR_OF_DAY, 23);
        periodEnd.set(Calendar.MINUTE, 59);
        periodEnd.set(Calendar.SECOND, 59);
        periodEnd.set(Calendar.MILLISECOND, 999);

        for (Map.Entry<String, Map<String, DataSourceResult>> sourceEntry :
                dataBySourceAndMetric.entrySet()) {
            String sourceName = sourceEntry.getKey();
            Map<String, DataSourceResult> metricResults = sourceEntry.getValue();

            for (Map.Entry<String, DataSourceResult> metricEntry : metricResults.entrySet()) {
                String metricLabel = metricEntry.getKey();
                DataSourceResult result = metricEntry.getValue();

                for (Map.Entry<String, List<KeyTime>> itemEntry : result.getAllKeyTimes().entrySet()) {
                    String itemKey = itemEntry.getKey();
                    List<KeyTime> allKeyTimes = itemEntry.getValue();

                    if (!allKeyTimes.isEmpty()) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String firstKeyTimeDate = dateFormat.format(allKeyTimes.get(0).getWhen().getTime());
                        logger.debug("Item {}: {} KeyTimes, first at: {}", itemKey, allKeyTimes.size(), firstKeyTimeDate);
                    }

                    List<KeyTime> periodKeyTimes = filterKeyTimesByPeriod(allKeyTimes, periodStart, periodEnd);

                    logger.debug("Item {}: {} KeyTimes after filtering for period {} to {}",
                        itemKey, periodKeyTimes.size(), period.getStart(), period.getEnd());

                    if (!periodKeyTimes.isEmpty()) {
                        if (config.getOutput() != null && config.getOutput().isSaveRawMetadata()) {
                            Map<String, MetricKeyTimes> itemMetrics = new HashMap<>();
                            itemMetrics.put(metricLabel, convertKeyTimesToMetricKeyTimes(periodKeyTimes));

                            Map<String, Object> metadata = convertJSONToMap(result.getMetadata(itemKey));

                            DatasetItem datasetItem = new DatasetItem(sourceName, metadata, itemMetrics);
                            dataset.add(datasetItem);
                        }

                        MetricSummary summary = metrics.computeIfAbsent(metricLabel, k -> new MetricSummary(0, 0.0, new ArrayList<>()));
                        aggregateKeyTimesIntoSummary(periodKeyTimes, summary, metricLabel);
                    }
                }
            }
        }

        double score = calculateScore(metrics, config.getAggregation() != null ? config.getAggregation().getFormula() : null);

        // Calculate per-period contributor breakdown
        Map<String, ContributorMetrics> contributorBreakdown = calculateContributorBreakdownForPeriod(dataset);

        return new TimePeriodResult(
            period.getName(),
            period.getStart(),
            period.getEnd(),
            metrics,
            score,
            dataset,
            contributorBreakdown
        );
    }

    private List<KeyTime> filterKeyTimesByPeriod(List<KeyTime> keyTimes, Calendar start, Calendar end) {
        return keyTimes.stream()
            .filter(kt -> {
                Calendar when = kt.getWhen();
                return !when.before(start) && !when.after(end);
            })
            .collect(Collectors.toList());
    }

    private void aggregateKeyTimesIntoSummary(List<KeyTime> keyTimes, MetricSummary summary, String metricLabel) {
        double divider = metricDividers.getOrDefault(metricLabel, 1.0);
        summary.setCount(summary.getCount() + keyTimes.size());
        summary.setTotalWeight(summary.getTotalWeight() +
            keyTimes.stream().mapToDouble(KeyTime::getWeight).sum() / divider);

        Set<String> contributors = new HashSet<>(summary.getContributors());
        keyTimes.forEach(kt -> contributors.add(kt.getWho()));
        summary.setContributors(new ArrayList<>(contributors));
    }

    private MetricKeyTimes convertKeyTimesToMetricKeyTimes(List<KeyTime> keyTimes) {
        List<KeyTimeData> keyTimeData = keyTimes.stream()
            .map(kt -> new KeyTimeData(
                kt.getWhen().toInstant().toString(),
                kt.getWho(),
                kt.getWeight()
            ))
            .collect(Collectors.toList());

        return new MetricKeyTimes(keyTimeData);
    }

    private Map<String, Object> convertJSONToMap(JSONObject json) {
        if (json == null) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();
        for (String key : json.keySet()) {
            map.put(key, json.get(key));
        }
        return map;
    }

    private double calculateScore(Map<String, MetricSummary> metrics, String formula) {
        if (formula == null || formula.isEmpty()) {
            return 0.0;
        }

        String evaluatedFormula = formula;

        for (Map.Entry<String, MetricSummary> entry : metrics.entrySet()) {
            String metricName = entry.getKey();
            double value = entry.getValue().getTotalWeight();
            evaluatedFormula = evaluatedFormula.replaceAll(
                "\\$\\{" + Pattern.quote(metricName) + "}",
                String.valueOf(value)
            );
        }

        return 0.0;  // TODO: implement proper formula evaluation
    }

    private AggregatedResult calculateAggregated(List<TimePeriodResult> periods, ReportConfig config) {
        Map<String, ContributorMetrics> byContributor = new HashMap<>();
        ContributorMetrics total = new ContributorMetrics();

        // Aggregate by contributor across all periods
        for (TimePeriodResult period : periods) {
            for (DatasetItem item : period.getDataset()) {
                // For each metric in this item
                for (Map.Entry<String, MetricKeyTimes> metricEntry : item.getMetrics().entrySet()) {
                    String metricLabel = metricEntry.getKey();

                    double divider = metricDividers.getOrDefault(metricLabel, 1.0);

                    for (KeyTimeData keyTime : metricEntry.getValue().getKeyTimes()) {
                        String contributor = keyTime.getWho();
                        double weight = keyTime.getWeight() / divider;

                        // Get or create contributor metrics
                        ContributorMetrics contributorMetrics = byContributor.computeIfAbsent(
                            contributor,
                            k -> new ContributorMetrics()
                        );

                        // Get or create metric summary for this contributor
                        MetricSummary summary = contributorMetrics.getMetrics().computeIfAbsent(
                            metricLabel,
                            k -> new MetricSummary(0, 0.0, new ArrayList<>())
                        );

                        // Update summary
                        summary.setCount(summary.getCount() + 1);
                        summary.setTotalWeight(summary.getTotalWeight() + weight);
                        if (!summary.getContributors().contains(contributor)) {
                            summary.getContributors().add(contributor);
                        }

                        // Update total
                        MetricSummary totalSummary = total.getMetrics().computeIfAbsent(
                            metricLabel,
                            k -> new MetricSummary(0, 0.0, new ArrayList<>())
                        );
                        totalSummary.setCount(totalSummary.getCount() + 1);
                        totalSummary.setTotalWeight(totalSummary.getTotalWeight() + weight);
                        if (!totalSummary.getContributors().contains(contributor)) {
                            totalSummary.getContributors().add(contributor);
                        }
                    }
                }
            }
        }

        // Calculate scores for each contributor
        String formula = config.getAggregation() != null ? config.getAggregation().getFormula() : null;
        for (ContributorMetrics metrics : byContributor.values()) {
            try {
                metrics.setScore(calculateScore(metrics.getMetrics(), formula));
            } catch (Exception e) {
                metrics.setScore(0.0);
            }
        }

        // Calculate total score
        try {
            total.setScore(calculateScore(total.getMetrics(), formula));
        } catch (Exception e) {
            total.setScore(0.0);
        }

        return new AggregatedResult(byContributor, total);
    }

    /**
     * Calculate contributor breakdown for a single time period
     * This enables per-period drill-down by contributor
     */
    private Map<String, ContributorMetrics> calculateContributorBreakdownForPeriod(List<DatasetItem> dataset) {
        Map<String, ContributorMetrics> contributorBreakdown = new HashMap<>();

        for (DatasetItem item : dataset) {
            for (Map.Entry<String, MetricKeyTimes> metricEntry : item.getMetrics().entrySet()) {
                String metricLabel = metricEntry.getKey();

                double divider = metricDividers.getOrDefault(metricLabel, 1.0);

                for (KeyTimeData keyTime : metricEntry.getValue().getKeyTimes()) {
                    String contributor = keyTime.getWho();

                    // Get or create contributor metrics
                    ContributorMetrics contributorMetrics = contributorBreakdown.computeIfAbsent(
                        contributor,
                        k -> new ContributorMetrics()
                    );

                    // Get or create metric summary for this contributor
                    MetricSummary summary = contributorMetrics.getMetrics().computeIfAbsent(
                        metricLabel,
                        k -> new MetricSummary(0, 0.0, new ArrayList<>())
                    );

                    // Update summary
                    summary.setCount(summary.getCount() + 1);
                    summary.setTotalWeight(summary.getTotalWeight() + keyTime.getWeight() / divider);
                    if (!summary.getContributors().contains(contributor)) {
                        summary.getContributors().add(contributor);
                    }
                }
            }
        }

        return contributorBreakdown;
    }

    List<TimePeriod> generateTimePeriods(ReportConfig config, TimeGroupingConfig grouping) throws Exception {
        if ("static".equals(grouping.getType())) {
            return grouping.getPeriods();
        }

        // Auto-generate periods based on type
        String startDateStr = config.getStartDate();
        String endDateStr = config.getEndDate();

        if (startDateStr == null) {
            throw new IllegalArgumentException("startDate is required for auto-generated periods");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar start = Calendar.getInstance();
        start.setTime(sdf.parse(startDateStr));

        // Apply dayShift
        int dayShift = grouping.getDayShift();
        if (dayShift > 0) {
            start.add(Calendar.DAY_OF_MONTH, dayShift);
        }

        // If endDate is not specified, use today
        Calendar end = Calendar.getInstance();
        if (endDateStr != null && !endDateStr.isEmpty()) {
            end.setTime(sdf.parse(endDateStr));
        } else {
            logger.info("No endDate specified, using today: {}", sdf.format(end.getTime()));
        }

        String type = grouping.getType();
        switch (type) {
            case "daily":
                return generateDailyPeriods(start, end, sdf);
            case "weekly":
                return generateWeeklyPeriods(start, end, sdf);
            case "bi-weekly":
                return generateBiWeeklyPeriods(start, end, sdf);
            case "monthly":
                return generateMonthlyPeriods(start, end, sdf);
            case "quarterly":
                return generateQuarterlyPeriods(start, end, sdf);
            case "yearly":
                return generateYearlyPeriods(start, end, sdf);
            default:
                throw new IllegalArgumentException("Unknown time grouping type: " + type);
        }
    }

    private List<TimePeriod> generateDailyPeriods(Calendar start, Calendar end, SimpleDateFormat sdf) {
        List<TimePeriod> periods = new ArrayList<>();
        Calendar current = (Calendar) start.clone();

        while (!current.after(end)) {
            Calendar dayEnd = (Calendar) current.clone();
            dayEnd.set(Calendar.HOUR_OF_DAY, 23);
            dayEnd.set(Calendar.MINUTE, 59);
            dayEnd.set(Calendar.SECOND, 59);

            if (dayEnd.after(end)) {
                dayEnd = (Calendar) end.clone();
            }

            String name = sdf.format(current.getTime());
            periods.add(new TimePeriod(name, sdf.format(current.getTime()), sdf.format(dayEnd.getTime())));

            current.add(Calendar.DAY_OF_MONTH, 1);
        }

        return periods;
    }

    private List<TimePeriod> generateWeeklyPeriods(Calendar start, Calendar end, SimpleDateFormat sdf) {
        List<TimePeriod> periods = new ArrayList<>();
        Calendar current = (Calendar) start.clone();

        int weekNum = 1;
        while (!current.after(end)) {
            Calendar weekEnd = (Calendar) current.clone();
            weekEnd.add(Calendar.DAY_OF_MONTH, 6);

            if (weekEnd.after(end)) {
                weekEnd = (Calendar) end.clone();
            }

            String name = "Week " + weekNum + " (" + sdf.format(current.getTime()) + ")";
            periods.add(new TimePeriod(name, sdf.format(current.getTime()), sdf.format(weekEnd.getTime())));

            current.add(Calendar.DAY_OF_MONTH, 7);
            weekNum++;
        }

        return periods;
    }

    private List<TimePeriod> generateBiWeeklyPeriods(Calendar start, Calendar end, SimpleDateFormat sdf) {
        List<TimePeriod> periods = new ArrayList<>();
        Calendar current = (Calendar) start.clone();

        int periodNum = 1;
        while (!current.after(end)) {
            Calendar periodEnd = (Calendar) current.clone();
            periodEnd.add(Calendar.DAY_OF_MONTH, 13); // 14 days - 1

            if (periodEnd.after(end)) {
                periodEnd = (Calendar) end.clone();
            }

            String name = "Bi-Week " + periodNum + " (" + sdf.format(current.getTime()) + ")";
            periods.add(new TimePeriod(name, sdf.format(current.getTime()), sdf.format(periodEnd.getTime())));

            current.add(Calendar.DAY_OF_MONTH, 14);
            periodNum++;
        }

        return periods;
    }

    private List<TimePeriod> generateMonthlyPeriods(Calendar start, Calendar end, SimpleDateFormat sdf) {
        List<TimePeriod> periods = new ArrayList<>();
        Calendar current = (Calendar) start.clone();

        while (!current.after(end)) {
            Calendar monthEnd = (Calendar) current.clone();
            monthEnd.set(Calendar.DAY_OF_MONTH, current.getActualMaximum(Calendar.DAY_OF_MONTH));
            monthEnd.set(Calendar.HOUR_OF_DAY, 23);
            monthEnd.set(Calendar.MINUTE, 59);
            monthEnd.set(Calendar.SECOND, 59);

            if (monthEnd.after(end)) {
                monthEnd = (Calendar) end.clone();
            }

            String monthName = new SimpleDateFormat("MMMM yyyy").format(current.getTime());
            periods.add(new TimePeriod(monthName, sdf.format(current.getTime()), sdf.format(monthEnd.getTime())));

            current.add(Calendar.MONTH, 1);
            current.set(Calendar.DAY_OF_MONTH, 1);
        }

        return periods;
    }

    List<TimePeriod> generateQuarterlyPeriods(Calendar start, Calendar end, SimpleDateFormat sdf) {
        List<TimePeriod> periods = new ArrayList<>();
        Calendar current = (Calendar) start.clone();

        while (!current.after(end)) {
            int month = current.get(Calendar.MONTH);
            int year = current.get(Calendar.YEAR);

            // Calculate quarter end: last day of the quarter's final month
            int quarterEndMonth;
            if (month <= Calendar.MARCH) {
                quarterEndMonth = Calendar.MARCH;
            } else if (month <= Calendar.JUNE) {
                quarterEndMonth = Calendar.JUNE;
            } else if (month <= Calendar.SEPTEMBER) {
                quarterEndMonth = Calendar.SEPTEMBER;
            } else {
                quarterEndMonth = Calendar.DECEMBER;
            }

            Calendar quarterEnd = Calendar.getInstance();
            quarterEnd.set(year, quarterEndMonth, 1);
            quarterEnd.set(Calendar.DAY_OF_MONTH, quarterEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
            quarterEnd.set(Calendar.HOUR_OF_DAY, 23);
            quarterEnd.set(Calendar.MINUTE, 59);
            quarterEnd.set(Calendar.SECOND, 59);

            if (quarterEnd.after(end)) {
                quarterEnd = (Calendar) end.clone();
            }

            int quarterNum = (month / 3) + 1;
            String name = "Q" + quarterNum + " " + year;
            periods.add(new TimePeriod(name, sdf.format(current.getTime()), sdf.format(quarterEnd.getTime())));

            // Move to start of next quarter
            current.set(year, quarterEndMonth, 1);
            current.add(Calendar.MONTH, 1);
            current.set(Calendar.DAY_OF_MONTH, 1);
        }

        return periods;
    }

    List<TimePeriod> generateYearlyPeriods(Calendar start, Calendar end, SimpleDateFormat sdf) {
        List<TimePeriod> periods = new ArrayList<>();
        Calendar current = (Calendar) start.clone();

        while (!current.after(end)) {
            int year = current.get(Calendar.YEAR);

            Calendar yearEnd = Calendar.getInstance();
            yearEnd.set(year, Calendar.DECEMBER, 31);
            yearEnd.set(Calendar.HOUR_OF_DAY, 23);
            yearEnd.set(Calendar.MINUTE, 59);
            yearEnd.set(Calendar.SECOND, 59);

            if (yearEnd.after(end)) {
                yearEnd = (Calendar) end.clone();
            }

            String name = String.valueOf(year);
            periods.add(new TimePeriod(name, sdf.format(current.getTime()), sdf.format(yearEnd.getTime())));

            // Move to Jan 1 of next year
            current.set(year + 1, Calendar.JANUARY, 1);
        }

        return periods;
    }

    /**
     * Write report output to JSON file. Returns the file path written.
     */
    private String writeOutput(
        ReportOutput output,
        Map<String, Map<String, DataSourceResult>> dataBySourceAndMetric,
        ReportConfig config,
        String suffix
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String reportName = config.getReportName().replaceAll("\\s+", "_");

        // Get output path from config, default to "reports/"
        String outputPath = "reports/";
        if (config.getOutput() != null && config.getOutput().getOutputPath() != null) {
            outputPath = config.getOutput().getOutputPath();
            // Ensure path ends with /
            if (!outputPath.endsWith("/")) {
                outputPath = outputPath + "/";
            }
        }

        new File(outputPath).mkdirs();

        String path = outputPath + reportName + suffix + ".json";
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), output);
        logger.info("Report saved to: {}", path);

        return path;
    }

    /**
     * Result holder for a generated report: the output object and the JSON file path.
     */
    public static class ReportResult {
        private final ReportOutput output;
        private final String jsonPath;

        public ReportResult(ReportOutput output, String jsonPath) {
            this.output = output;
            this.jsonPath = jsonPath;
        }

        public ReportOutput getOutput() { return output; }
        public String getJsonPath() { return jsonPath; }
    }

    static class DataSourceResult {
        private final Map<String, List<KeyTime>> keyTimesByItem = new ConcurrentHashMap<>();
        private final Map<String, JSONObject> metadataByItem = new ConcurrentHashMap<>();

        public void addKeyTimes(String itemKey, List<KeyTime> keyTimes) {
            keyTimesByItem.put(itemKey, keyTimes);
        }

        public void mergeKeyTimes(String itemKey, List<KeyTime> keyTimes) {
            keyTimesByItem.merge(itemKey, keyTimes, (existing, incoming) -> {
                List<KeyTime> merged = new ArrayList<>(existing);
                merged.addAll(incoming);
                return merged;
            });
        }

        public void addMetadata(String itemKey, JSONObject metadata) {
            metadataByItem.put(itemKey, metadata);
        }

        public Map<String, List<KeyTime>> getAllKeyTimes() {
            return keyTimesByItem;
        }

        public JSONObject getMetadata(String itemKey) {
            return metadataByItem.get(itemKey);
        }
    }
}
