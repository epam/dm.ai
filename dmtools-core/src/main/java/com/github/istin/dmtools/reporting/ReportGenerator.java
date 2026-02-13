package com.github.istin.dmtools.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.reporting.datasource.*;
import com.github.istin.dmtools.reporting.formula.ComputedMetricsApplier;
import com.github.istin.dmtools.reporting.formula.FormulaEvaluator;
import com.github.istin.dmtools.reporting.metrics.MetricFactory;
import com.github.istin.dmtools.reporting.model.*;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Main report generator using JSON configuration.
 * Uses TrackerClient abstraction - works with Jira, ADO, Rally, etc.
 */
public class ReportGenerator {
    private static final Logger logger = LogManager.getLogger(ReportGenerator.class);

    private final TrackerClient trackerClient;
    private final SourceCode sourceCode;
    private final FigmaClient figmaClient;
    private final Set<String> weightMetricLabels = new HashSet<>();
    private final Map<String, Double> metricDividers = new HashMap<>();
    private final Map<String, String> metricLinkTemplates = new HashMap<>();

    public ReportGenerator(TrackerClient trackerClient, SourceCode sourceCode) {
        this.trackerClient = trackerClient;
        this.sourceCode = sourceCode;
        this.figmaClient = null;
    }

    public ReportGenerator(TrackerClient trackerClient, SourceCode sourceCode, FigmaClient figmaClient) {
        this.trackerClient = trackerClient;
        this.sourceCode = sourceCode;
        this.figmaClient = figmaClient;
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

        // Resolve aggregation formula (inline or file)
        String resolvedAggregationFormula = resolveAggregationFormula(config);
        String aggregationLabel = config.getAggregation() != null ? config.getAggregation().getLabel() : null;
        if (config.getAggregation() != null) {
            config.getAggregation().setFormula(resolvedAggregationFormula);
        }

        // Build employees from config (if provided)
        IEmployees employees = null;
        if (config.getEmployees() != null || config.getAliases() != null) {
            employees = new ReportEmployees(config.getEmployees(), config.getAliases());
        }

        // Initialize factories
        DataSourceFactory dataSourceFactory = new DataSourceFactory();
        MetricFactory metricFactory = new MetricFactory(trackerClient, sourceCode, figmaClient, employees, config.getStartDate());

        // 1. Collect data ONCE (expensive: API calls)
        Map<String, Map<String, DataSourceResult>> dataBySourceAndMetric =
            collectDataFromAllSources(config, trackerClient, sourceCode, dataSourceFactory, metricFactory);
        if (employees != null) {
            normalizeKeyTimesByAliases(dataBySourceAndMetric, employees);
        }

        List<TimeGroupingConfig> groupings = config.getTimeGroupings();
        boolean multiGrouping = config.isMultiGrouping();

        List<ReportResult> results = new ArrayList<>();

        // 2. For each time grouping, generate a separate report
        for (TimeGroupingConfig grouping : groupings) {
            List<TimePeriod> periods = generateTimePeriods(config, grouping);

            // Build time period results (single pass over keyTimes for performance)
            List<TimePeriodResult> periodResults = buildPeriodResults(periods, dataBySourceAndMetric, config);

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
            if (!metricLinkTemplates.isEmpty()) {
                output.setLinkTemplates(new HashMap<>(metricLinkTemplates));
            }
            if (!metricDividers.isEmpty()) {
                output.setMetricDividers(new HashMap<>(metricDividers));
            }
            if (config.getCustomCharts() != null && !config.getCustomCharts().isEmpty()) {
                output.setCustomCharts(config.getCustomCharts());
            }
            if (resolvedAggregationFormula != null && !resolvedAggregationFormula.isEmpty()) {
                output.setAggregationFormula(resolvedAggregationFormula);
                output.setAggregationLabel(aggregationLabel != null && !aggregationLabel.isEmpty()
                    ? aggregationLabel
                    : "All Metrics Score");
            }

            // Write output with grouping suffix
            String suffix = multiGrouping ? "_" + grouping.getType() : "";
            String jsonPath = writeOutput(output, dataBySourceAndMetric, config, suffix);

            results.add(new ReportResult(output, jsonPath));
            logger.info("Generated report for grouping '{}': {}", grouping.getType(), jsonPath);
        }

        return results;
    }

    private void normalizeKeyTimesByAliases(
        Map<String, Map<String, DataSourceResult>> dataBySourceAndMetric,
        IEmployees employees
    ) {
        if (employees == null || dataBySourceAndMetric == null) {
            return;
        }
        for (Map<String, DataSourceResult> metricResults : dataBySourceAndMetric.values()) {
            if (metricResults == null) continue;
            for (DataSourceResult result : metricResults.values()) {
                if (result == null) continue;
                for (List<KeyTime> keyTimes : result.getAllKeyTimes().values()) {
                    if (keyTimes == null) continue;
                    for (KeyTime kt : keyTimes) {
                        if (kt == null) continue;
                        String who = kt.getWho();
                        if (who == null) continue;
                        kt.setWho(employees.transformName(who));
                    }
                }
            }
        }
    }

    private String resolveAggregationFormula(ReportConfig config) {
        if (config == null || config.getAggregation() == null) {
            return null;
        }
        AggregationConfig agg = config.getAggregation();
        String formula = agg.getFormula();
        String formulaFile = agg.getFormulaFile();
        if (formulaFile != null && !formulaFile.trim().isEmpty()) {
            try {
                Path path = Paths.get(formulaFile);
                if (!path.isAbsolute()) {
                    path = Paths.get(System.getProperty("user.dir")).resolve(path);
                }
                if (Files.exists(path)) {
                    String content = Files.readString(path).trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                } else {
                    logger.warn("Aggregation formula file not found: {}", path);
                }
            } catch (Exception e) {
                logger.warn("Failed to read aggregation formula file '{}': {}", formulaFile, e.getMessage());
            }
        }
        return formula;
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

                // Build link template from data source params
                String linkTemplate = buildLinkTemplate(sourceConfig.getName(), sourceConfig.getParams());
                if (linkTemplate != null) {
                    metricLinkTemplates.put(metricLabel, linkTemplate);
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
                    // Merge incoming metadata (prevents '?' keys and empty summaries from other repos)
                    for (Map.Entry<String, JSONObject> mdEntry : incoming.getAllMetadata().entrySet()) {
                        existing.addMetadata(mdEntry.getKey(), mdEntry.getValue());
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

                    if (logger.isTraceEnabled() && !allKeyTimes.isEmpty()) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String firstKeyTimeDate = dateFormat.format(allKeyTimes.get(0).getWhen().getTime());
                        logger.trace("Item {}: {} KeyTimes, first at: {}", itemKey, allKeyTimes.size(), firstKeyTimeDate);
                    }

                    List<KeyTime> periodKeyTimes = filterKeyTimesByPeriod(allKeyTimes, periodStart, periodEnd);

                    if (logger.isTraceEnabled()) {
                        logger.trace("Item {}: {} KeyTimes after filtering for period {} to {}",
                            itemKey, periodKeyTimes.size(), period.getStart(), period.getEnd());
                    }

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

        // Apply computed metrics (formula-based) to period totals
        ComputedMetricsApplier.applyToMetrics(config.getComputedMetrics(), metrics, weightMetricLabels);

        double score = calculateScore(metrics, config.getAggregation() != null ? config.getAggregation().getFormula() : null);

        // Calculate per-period contributor breakdown
        Map<String, ContributorMetrics> contributorBreakdown = calculateContributorBreakdownForPeriod(dataset);
        ComputedMetricsApplier.applyToContributors(config.getComputedMetrics(), contributorBreakdown, weightMetricLabels);

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

    private List<TimePeriodResult> buildPeriodResults(
        List<TimePeriod> periods,
        Map<String, Map<String, DataSourceResult>> dataBySourceAndMetric,
        ReportConfig config
    ) throws Exception {
        int pCount = periods.size();
        List<Map<String, MetricSummary>> metricsByPeriod = new ArrayList<>(pCount);
        List<List<DatasetItem>> datasetsByPeriod = new ArrayList<>(pCount);
        for (int i = 0; i < pCount; i++) {
            metricsByPeriod.add(new HashMap<>());
            datasetsByPeriod.add(new ArrayList<>());
        }

        long[] periodStarts = new long[pCount];
        long[] periodEnds = new long[pCount];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < pCount; i++) {
            TimePeriod period = periods.get(i);
            Calendar ps = Calendar.getInstance();
            ps.setTime(sdf.parse(period.getStart()));
            ps.set(Calendar.HOUR_OF_DAY, 0);
            ps.set(Calendar.MINUTE, 0);
            ps.set(Calendar.SECOND, 0);
            ps.set(Calendar.MILLISECOND, 0);
            Calendar pe = Calendar.getInstance();
            pe.setTime(sdf.parse(period.getEnd()));
            pe.set(Calendar.HOUR_OF_DAY, 23);
            pe.set(Calendar.MINUTE, 59);
            pe.set(Calendar.SECOND, 59);
            pe.set(Calendar.MILLISECOND, 999);
            periodStarts[i] = ps.getTimeInMillis();
            periodEnds[i] = pe.getTimeInMillis();
        }

        boolean saveRaw = config.getOutput() != null && config.getOutput().isSaveRawMetadata();

        for (Map.Entry<String, Map<String, DataSourceResult>> sourceEntry : dataBySourceAndMetric.entrySet()) {
            String sourceName = sourceEntry.getKey();
            Map<String, DataSourceResult> metricResults = sourceEntry.getValue();

            for (Map.Entry<String, DataSourceResult> metricEntry : metricResults.entrySet()) {
                String metricLabel = metricEntry.getKey();
                DataSourceResult result = metricEntry.getValue();
                double divider = metricDividers.getOrDefault(metricLabel, 1.0);

                for (Map.Entry<String, List<KeyTime>> itemEntry : result.getAllKeyTimes().entrySet()) {
                    String itemKey = itemEntry.getKey();
                    List<KeyTime> allKeyTimes = itemEntry.getValue();
                    if (allKeyTimes == null || allKeyTimes.isEmpty()) {
                        continue;
                    }

                    Map<Integer, List<KeyTime>> byPeriod = saveRaw ? new HashMap<>() : null;

                    for (KeyTime kt : allKeyTimes) {
                        int pIdx = findPeriodIndex(kt.getWhen().getTimeInMillis(), periodStarts, periodEnds);
                        if (pIdx < 0) {
                            continue;
                        }

                        Map<String, MetricSummary> metrics = metricsByPeriod.get(pIdx);
                        MetricSummary summary = metrics.computeIfAbsent(metricLabel, k -> new MetricSummary(0, 0.0, new ArrayList<>()));
                        aggregateKeyTimeIntoSummary(kt, summary, metricLabel, divider);

                        if (saveRaw) {
                            byPeriod.computeIfAbsent(pIdx, k -> new ArrayList<>()).add(kt);
                        }
                    }

                    if (saveRaw && byPeriod != null && !byPeriod.isEmpty()) {
                        for (Map.Entry<Integer, List<KeyTime>> e : byPeriod.entrySet()) {
                            int pIdx = e.getKey();
                            List<KeyTime> periodKeyTimes = e.getValue();
                            if (periodKeyTimes.isEmpty()) continue;

                            Map<String, MetricKeyTimes> itemMetrics = new HashMap<>();
                            itemMetrics.put(metricLabel, convertKeyTimesToMetricKeyTimes(periodKeyTimes));
                            Map<String, Object> metadata = convertJSONToMap(result.getMetadata(itemKey));
                            DatasetItem datasetItem = new DatasetItem(sourceName, metadata, itemMetrics);
                            datasetsByPeriod.get(pIdx).add(datasetItem);
                        }
                    }
                }
            }
        }

        List<TimePeriodResult> periodResults = new ArrayList<>(pCount);
        for (int i = 0; i < pCount; i++) {
            TimePeriod period = periods.get(i);
            Map<String, MetricSummary> metrics = metricsByPeriod.get(i);
            List<DatasetItem> dataset = datasetsByPeriod.get(i);

            ComputedMetricsApplier.applyToMetrics(config.getComputedMetrics(), metrics, weightMetricLabels);
            double score = calculateScore(metrics, config.getAggregation() != null ? config.getAggregation().getFormula() : null);

            Map<String, ContributorMetrics> contributorBreakdown = calculateContributorBreakdownForPeriod(dataset);
            ComputedMetricsApplier.applyToContributors(config.getComputedMetrics(), contributorBreakdown, weightMetricLabels);

            periodResults.add(new TimePeriodResult(
                period.getName(),
                period.getStart(),
                period.getEnd(),
                metrics,
                score,
                dataset,
                contributorBreakdown
            ));
        }

        return periodResults;
    }

    private int findPeriodIndex(long timeMillis, long[] periodStarts, long[] periodEnds) {
        int idx = Arrays.binarySearch(periodStarts, timeMillis);
        if (idx >= 0) {
            return idx;
        }
        int insertPoint = -idx - 2;
        if (insertPoint >= 0 && insertPoint < periodStarts.length) {
            if (timeMillis <= periodEnds[insertPoint]) {
                return insertPoint;
            }
        }
        return -1;
    }

    private void aggregateKeyTimeIntoSummary(KeyTime kt, MetricSummary summary, String metricLabel, double divider) {
        summary.setCount(summary.getCount() + 1);
        summary.setTotalWeight(summary.getTotalWeight() + (kt.getWeight() / divider));
        Set<String> contributors = new HashSet<>(summary.getContributors());
        contributors.add(kt.getWho());
        summary.setContributors(new ArrayList<>(contributors));
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

        Map<String, Double> values = ComputedMetricsApplier.buildMetricValues(metrics, weightMetricLabels);
        return FormulaEvaluator.evaluate(formula, values);
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

        // Apply computed metrics to aggregated totals and per-contributor
        ComputedMetricsApplier.applyToMetrics(config.getComputedMetrics(), total.getMetrics(), weightMetricLabels);
        ComputedMetricsApplier.applyToContributors(config.getComputedMetrics(), byContributor, weightMetricLabels);

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

    private String buildLinkTemplate(String dataSourceType, Map<String, Object> params) {
        if (params == null) return null;
        String sourceType = (String) params.getOrDefault("sourceType", "");
        String workspace = (String) params.get("workspace");
        String repository = (String) params.get("repository");

        if ("pullRequests".equals(dataSourceType) && workspace != null && repository != null) {
            String base = getSourceCodeBaseUrl(sourceType, workspace, repository);
            return base + "/pull/{key}";
        } else if ("commits".equals(dataSourceType) && workspace != null && repository != null) {
            String base = getSourceCodeBaseUrl(sourceType, workspace, repository);
            return base + "/commit/{key}";
        }
        // For tracker data sources, use default tracker link (handled in FTL)
        return null;
    }

    private String getSourceCodeBaseUrl(String sourceType, String workspace, String repository) {
        if ("github".equalsIgnoreCase(sourceType)) {
            return "https://github.com/" + workspace + "/" + repository;
        } else if ("gitlab".equalsIgnoreCase(sourceType)) {
            return "https://gitlab.com/" + workspace + "/" + repository;
        } else if ("bitbucket".equalsIgnoreCase(sourceType)) {
            return "https://bitbucket.org/" + workspace + "/" + repository;
        }
        return "https://github.com/" + workspace + "/" + repository;
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

        public Map<String, JSONObject> getAllMetadata() {
            return metadataByItem;
        }

        public JSONObject getMetadata(String itemKey) {
            return metadataByItem.get(itemKey);
        }
    }
}
