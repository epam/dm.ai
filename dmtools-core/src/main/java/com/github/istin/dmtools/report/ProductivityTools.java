// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.CombinedCustomRunnableMetrics;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.report.freemarker.DevProductivityReport;
import com.github.istin.dmtools.report.freemarker.GenericCell;
import com.github.istin.dmtools.report.freemarker.GenericRow;
import com.github.istin.dmtools.report.freemarker.cells.DevItemsSumCell;
import com.github.istin.dmtools.report.freemarker.cells.DevProductivityCell;
import com.github.istin.dmtools.report.freemarker.cells.DevStoriesSPSumCell;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.report.productivity.AnalyticsCommentsRule;
import com.github.istin.dmtools.report.productivity.ProductivityAnalyticsData;
import com.github.istin.dmtools.report.productivity.ProductivityDataResult;
import com.github.istin.dmtools.report.productivity.ProductivityUtils;
import com.github.istin.dmtools.team.Employees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.script.ScriptException;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @deprecated Use {@link com.github.istin.dmtools.reporting.ReportGenerator} instead.
 * This class will be removed in version 3.0.
 *
 * <p>ProductivityTools is being replaced by a new JSON-configurable reporting system
 * that provides:
 * <ul>
 *   <li>Multiple data sources (tracker, pull requests, commits, etc.)</li>
 *   <li>JavaScript custom metrics alongside Java metrics</li>
 *   <li>Parallel execution for better performance</li>
 *   <li>Structured JSON output for easy integration</li>
 *   <li>Flexible time grouping options</li>
 * </ul>
 *
 * <p>See package {@link com.github.istin.dmtools.reporting} for the new API.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class ProductivityTools {
    private static final Logger logger = LogManager.getLogger(ProductivityTools.class);
    public static final String REPORT_NAME = "Dev Productivity";

    public static File generate(TrackerClient tracker, IReleaseGenerator releaseGenerator, String team, String formula, String jql, List<Metric> listOfCustomMetrics, Release.Style style, String[] ignorePrefixes) throws Exception {
        return generate(tracker, releaseGenerator, team, formula, jql, listOfCustomMetrics, style, null, ignorePrefixes);
    }

    public static File generate(TrackerClient tracker, IReleaseGenerator releaseGenerator, String team, String formula, String jql, List<Metric> listOfCustomMetrics, Release.Style style, Employees employees, String[] ignorePrefixes, HtmlInjection htmlInjection) throws Exception {
        DevProductivityReport productivityReport = buildReport(tracker, releaseGenerator, team, formula, jql, listOfCustomMetrics, style, employees, ignorePrefixes);
        if (htmlInjection != null) {
            productivityReport.setHtmlBeforeTimeline(htmlInjection.getHtmBeforeTimeline(productivityReport));
        }
        return new ReportUtils().write(team + "_" + REPORT_NAME, "dev_productivity", productivityReport, null);
    }

    public static File generate(TrackerClient tracker, IReleaseGenerator releaseGenerator, String team, String formula, String jql, List<Metric> listOfCustomMetrics, Release.Style style, Employees employees, String[] ignorePrefixes) throws Exception {
        return generate(tracker, releaseGenerator, team, formula, jql, listOfCustomMetrics, style, employees, ignorePrefixes, null);
    }

    @NotNull
    public static DevProductivityReport buildReport(final TrackerClient tracker, IReleaseGenerator releaseGenerator, String team, String formula, String jql, List<Metric> listOfCustomMetrics, Release.Style style, Employees employees, String[] ignorePrefixes) throws Exception {
        final Map<String, Map<String,List<KeyTime>>> customMetricsProductivityMap = new HashMap<>();
        Set<String> combinedMetrics = new HashSet<>();
        DevProductivityReport productivityReport = new DevProductivityReport();
        productivityReport.setName(team + " " + REPORT_NAME);
        String basePath = "";
        if (tracker != null) {
            basePath = tracker.getBasePath();
            productivityReport.setFilter(basePath + "/issues/?jql=" + jql);
        } else {
            productivityReport.setFilter("");
        }
        List<String> headers = productivityReport.getHeaders();

        for (Metric m : listOfCustomMetrics) {
            if (!headers.contains(m.getName())) {
                headers.add(m.getName());
            }
            customMetricsProductivityMap.put(m.getName(), new HashMap<>());
        }

        final int[] ticketCounter = {0};

        if (jql != null && !jql.isEmpty() && tracker != null) {
            tracker.searchAndPerform(new JiraClient.Performer<ITicket>() {

                @Override
                public boolean perform(ITicket ticket) throws Exception {
                    //custom metrics
                    if (ProductivityUtils.isIgnoreTask(ignorePrefixes, ticket)) return false;

                    for (Metric m : listOfCustomMetrics) {
                        if (m.getSourceCollector() == null) {
                            List<KeyTime> productivityItem = m.getRule().check(tracker, ticket);
                            if (employees != null) {
                                checkEmployees(employees, productivityItem);
                            }
                            if (productivityItem != null && !productivityItem.isEmpty()) {
                                for (KeyTime keyTime : productivityItem) {
                                    List<KeyTime> items = customMetricsProductivityMap.get(m.getName()).computeIfAbsent(keyTime.getWho(), k -> new ArrayList<>());
                                    items.add(keyTime);
                                    combinedMetrics.add(keyTime.getWho());
                                }
                            }
                        }
                    }

                    ticketCounter[0]++;
//                    System.out.println(ticketCounter[0]);
                    return false;
                }

            }, jql, tracker.getDefaultQueryFields());

        }
        for (Metric m : listOfCustomMetrics) {
            if (m.getSourceCollector() != null || m instanceof CombinedCustomRunnableMetrics) {
                m.perform(customMetricsProductivityMap, combinedMetrics);
            }
        }

        long maxTime = 0;
        Calendar date = releaseGenerator.getStartDate();

        for (String metricName : combinedMetrics) {
            //custom metrics
            for (Metric m : listOfCustomMetrics) {
                List<KeyTime> productivityItems = customMetricsProductivityMap.get(m.getName()).get(metricName);
                if (productivityItems != null && !productivityItems.isEmpty()) {
                    productivityItems.sort(Comparator.comparing(KeyTime::getWhen));
                    Iterator<KeyTime> productivityItemsIterator = productivityItems.iterator();
                    KeyTime keyTime;
                    while (productivityItemsIterator.hasNext()) {
                        keyTime = productivityItemsIterator.next();
                        if (keyTime.getWhen().compareTo(date) <0) {
                            productivityReport.addUmatched(keyTime, metricName);
                            productivityItemsIterator.remove();
                        }
                    }
                    if (!productivityItems.isEmpty()) {
                        maxTime = Math.max(maxTime, productivityItems.get(productivityItems.size() - 1).getWhen().getTimeInMillis());
                    }
                } else {
                    customMetricsProductivityMap.get(m.getName()).put(metricName, new ArrayList<>());
                }
            }
        }


        List<Release> releases = releaseGenerator.generate();


        productivityReport.setReleases(releases);

        List<DevChart> listCharts = new ArrayList<>();
        DevChart allMetrics = new DevChart(team);
        listCharts.add(allMetrics);

        for (Release release : releases) {
            for (ReportIteration reportIteration : release.getIterationsByStyle(style)) {
                for (String metricName : combinedMetrics) {
                    Stream<DevChart> stream = listCharts.stream();
                    if (stream == null) {
                        continue;
                    }
                    Optional<DevChart> devChartOptional = stream.filter(employeeChart1 -> {
                        String devName = employeeChart1.getDevName();
                        if (devName == null) {
                            return false;
                        }
                        return devName.equals(metricName);
                    }).findFirst();
                    DevChart devChart = null;
                    if (!devChartOptional.isPresent()) {
                        devChart = new DevChart(metricName);
                        listCharts.add(devChart);
                    } else {
                        devChart = devChartOptional.get();
                    }

                    DevProductivityCell devProductivityCell = new DevProductivityCell(basePath);
                    devProductivityCell.setCountPrint(false);
                    devProductivityCell.setWeightPrint(true);

                    GenericRow genericRow = findRow(productivityReport.getRows(), metricName);
                    List<GenericCell> cells = null;
                    if (genericRow == null) {
                        genericRow = new GenericRow();
                        cells = genericRow.getCells();
                        cells.add(new GenericCell(MockedNames.getInstance().mock(metricName)));
                        for (Metric m : listOfCustomMetrics) {
                            if (m.isWeight()) {
                                cells.add(new DevStoriesSPSumCell(basePath));
                            } else {
                                cells.add(new DevItemsSumCell(basePath));
                            }
                        }

                        productivityReport.getRows().add(genericRow);
                    }
                    cells = genericRow.getCells();

                    //custom metrics

                    for (int i = 0; i < listOfCustomMetrics.size(); i++) {
                        Metric m = listOfCustomMetrics.get(i);
                        List<KeyTime> keyTimeList = customMetricsProductivityMap.get(m.getName()).get(metricName);
                        for (KeyTime keyTime : keyTimeList) {
                            long timeStart = getCurrentTimeForMeasurements();
                            if (ReportIteration.Impl.isMatchedToIterationTimeline(reportIteration, keyTime.getWhen())) {
                                timeStart = measureTime("isMatchedToIterationTimeline", timeStart);
                                devProductivityCell.add(keyTime);
                                timeStart = measureTime("isMatchedToIterationTimeline add Key Time", timeStart);
                                ((DevProductivityCell) cells.get(i+1)).add(keyTime);
                                timeStart = measureTime("isMatchedToIterationTimeline add Key Time to cells +1", timeStart);
                                addToDevChart(listOfCustomMetrics, reportIteration, releaseGenerator, m, keyTime, devChart, formula);
                                timeStart = measureTime("addToDevChart", timeStart);
                                addToDevChart(listOfCustomMetrics, reportIteration, releaseGenerator, m, keyTime, allMetrics, formula);
                                timeStart = measureTime("addToAllMetrics", timeStart);
                            } else {
                                addToDevChart(listOfCustomMetrics, reportIteration, releaseGenerator, m, null, devChart, formula);
                                timeStart = measureTime("addToDevChart null", timeStart);
                                addToDevChart(listOfCustomMetrics, reportIteration, releaseGenerator, m, null, allMetrics, formula);
                                timeStart = measureTime("addToAllMetrics null", timeStart);
                            }
                        }
                    }
                    cells.add(devProductivityCell);
                }
            }
        }

        productivityReport.setBySprints(style == Release.Style.BY_SPRINTS);
        productivityReport.setByWeeks(style == Release.Style.BY_WEEKS);

        productivityReport.setTicketsCount(ticketCounter[0]);
        productivityReport.setListDevCharts(listCharts);

        Collections.sort(listCharts, new Comparator<DevChart>() {
            @Override
            public int compare(DevChart o1, DevChart o2) {
                int i = o1.getLevel().compareTo(o2.getLevel());
                if (i == 0) {
                    try {
                        return Double.valueOf(o1.getAvgScore()).compareTo(Double.valueOf(o2.getAvgScore()));
                    } catch (ScriptException e) {
                        throw new RuntimeException(e);
                    }
                }
                return i;
            }
        });


        productivityReport.makeSprintShifts(releaseGenerator.getExtraSprintTimeline());
        productivityReport.shiftTimelineStarts(16);
        
        // Calculate all scores for JSON serialization
        calculateAllScores(productivityReport);
        
        return productivityReport;
    }
    
    private static void calculateAllScores(DevProductivityReport productivityReport) {
        if (productivityReport.getListDevCharts() != null) {
            for (DevChart devChart : productivityReport.getListDevCharts()) {
                devChart.calculateAllScores();
            }
        }
    }

    @NotNull
    public static ProductivityDataResult buildReportWithAnalytics(
            final TrackerClient tracker,
            IReleaseGenerator releaseGenerator,
            String team,
            String formula,
            String jql,
            List<Metric> listOfCustomMetrics,
            Release.Style style,
            Employees employees,
            String[] ignorePrefixes,
            Map<String, String> patternNames,
            boolean collectRequests,
            String requestExtractionPattern,
            Function<String, String> userNameResolver) throws Exception {
        
        ProductivityAnalyticsData analyticsData = new ProductivityAnalyticsData();
        analyticsData.setPatternNames(patternNames != null ? new LinkedHashMap<>(patternNames) : new LinkedHashMap<>());
        
        // Create a new list of metrics that includes analytics collection
        List<Metric> metricsWithAnalytics = new ArrayList<>(listOfCustomMetrics);
        
        // Add analytics metrics for each pattern
        if (patternNames != null && !patternNames.isEmpty() && employees != null) {
            for (Map.Entry<String, String> patternEntry : patternNames.entrySet()) {
                String patternRegex = patternEntry.getKey();
                String patternName = patternEntry.getValue();
                
                AnalyticsCommentsRule analyticsRule = new AnalyticsCommentsRule(
                        employees,
                        patternRegex,
                        analyticsData,
                        patternName,
                        collectRequests,
                        requestExtractionPattern,
                        userNameResolver);
                
                // Create metric with analytics rule
                // Use pattern name as metric name, weight from first metric if available
                boolean isWeight = !listOfCustomMetrics.isEmpty() && listOfCustomMetrics.get(0).isWeight();
                Metric analyticsMetric = new Metric(patternName, isWeight, analyticsRule);
                metricsWithAnalytics.add(analyticsMetric);
            }
        }
        
        // Build the report using existing method (ensures backward compatibility)
        DevProductivityReport productivityReport = buildReport(
                tracker, releaseGenerator, team, formula, jql, metricsWithAnalytics,
                style, employees, ignorePrefixes);
        
        // Count tickets by doing a separate pass (buildReport also counts but doesn't expose it)
        final int[] ticketCounter = {0};
        if (jql != null && !jql.isEmpty() && tracker != null) {
            tracker.searchAndPerform(new JiraClient.Performer<ITicket>() {
                @Override
                public boolean perform(ITicket ticket) throws Exception {
                    if (ProductivityUtils.isIgnoreTask(ignorePrefixes, ticket)) return false;
                    ticketCounter[0]++;
                    return false;
                }
            }, jql, tracker.getDefaultQueryFields());
        }
        int ticketsCount = ticketCounter[0];
        
        // Ensure all scores are calculated for JSON serialization
        calculateAllScores(productivityReport);
        
        // Build filter string from tracker and jql
        String filter = "";
        if (tracker != null && jql != null && !jql.isEmpty()) {
            String basePath = tracker.getBasePath();
            filter = basePath + "/issues/?jql=" + jql;
        }
        
        // Create result object
        ProductivityDataResult result = new ProductivityDataResult();
        result.setAnalytics(analyticsData);
        result.setProductivityReport(productivityReport);
        result.setTicketsCount(ticketsCount);
        result.setReportName(team);
        result.setFilter(filter);
        
        return result;
    }

    protected static void checkEmployees(Employees employees, List<KeyTime> productivityItem) {
        if (productivityItem != null) {
            for (KeyTime keyTime : productivityItem) {
                String who = keyTime.getWho();
                keyTime.setWho(employees.transformName(who));
            }
        }
    }

    protected static long getCurrentTimeForMeasurements() {
        return System.currentTimeMillis();
    }

    protected static long measureTime(String action, long timeStart) {
        long newTime = getCurrentTimeForMeasurements();
        long timeSpend = newTime - timeStart;
        if (timeSpend > 10) {
            logger.info("{} {}", action, timeSpend);
        }
        return newTime;
    }


    protected static void addToDevChart(List<Metric> metrics, ReportIteration reportIteration, IReleaseGenerator releaseGenerator, Metric metric, KeyTime keyTime, DevChart devChart, String formula) {
        List<DevChart.ReportIterationData> reportIterationDataList = devChart.reportIterationDataList;
        DevChart.ReportIterationData reportIterationData = null;
        long timeStart = getCurrentTimeForMeasurements();
        String iterationName = reportIteration.getIterationName();
        try {
            iterationName = (Integer.parseInt(iterationName) + releaseGenerator.getExtraSprintTimeline()) + "";
        } catch (Exception ignored) {}
        timeStart = measureTime("conversion to int", timeStart);


        for (int i = reportIterationDataList.size()-1; i >= 0; i--) {
            DevChart.ReportIterationData devChartSprint = reportIterationDataList.get(i);
            if (devChartSprint.getIterationName().equalsIgnoreCase(iterationName)) {
                reportIterationData = devChartSprint;
                break;
            }
        }

        //TODO convert to vs search
//        for (DevChart.ReportIterationData devChartSprint : reportIterationDataList) {
//            if (devChartSprint.getIterationName().equalsIgnoreCase(iterationName)) {
//                reportIterationData = devChartSprint;
//                break;
//            }
//        }

        timeStart = measureTime("find reportIterationData", timeStart);
        Release currentIteration = releaseGenerator.getCurrentIteration();
        if (reportIterationData == null) {
            int id = reportIteration.getId() + releaseGenerator.getExtraSprintTimeline();
            int currentIterationId = currentIteration.getId();
            reportIterationData = new DevChart.ReportIterationData(id, iterationName, currentIterationId, formula);
            reportIterationData.customMetricsHeaders = metrics;
            if (devChart.reportIterationDataList.size() > 0) {
                int prevIteration = devChart.reportIterationDataList.get(devChart.reportIterationDataList.size() - 1).getReportIterationId();
                if (prevIteration != id - 1) {
                    for (int i = prevIteration + 1; i != id; i++) {
                        devChart.reportIterationDataList.add(new DevChart.ReportIterationData(i, iterationName, currentIterationId, formula));
                    }
                }
            }
            devChart.reportIterationDataList.add(reportIterationData);
        }

        timeStart = measureTime("add to reportIterationDataList", timeStart);

        List<KeyTime> keyTimes = reportIterationData.customMetrics.computeIfAbsent(metric, k -> new ArrayList<>());
        if (keyTime != null) {
            keyTimes.add(keyTime);
        }
        timeStart = measureTime("add all keyTimes", timeStart);
    }

    protected static GenericRow findRow(List<GenericRow> rows, String dev) {
        for (GenericRow genericRow : rows) {
            String text = genericRow.getCells().get(0).getText();
            if (text != null && text.equals(dev)) {
                return genericRow;
            }
        }
        return null;
    }
}
