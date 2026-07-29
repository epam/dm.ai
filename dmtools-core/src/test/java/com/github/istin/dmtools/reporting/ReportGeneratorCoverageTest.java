// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.reporting.model.*;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Coverage tests for ReportGenerator end-to-end flows:
 * generateReports/generateReport pipeline, aggregation formula resolution,
 * alias normalization, link templates, rate-limit delay calculation branches,
 * and result merging across data sources.
 */
class ReportGeneratorCoverageTest {

    @TempDir
    Path tempDir;

    // --- End-to-end generateReport with raw metadata, weights, divider, aggregation ---

    @Test
    void testGenerateReport_endToEnd_commitsWithWeightsDividerAndAggregation() throws Exception {
        SourceCode sourceCode = mockSourceCodeWithCommits(
            List.of(
                mockCommit("hash1", "Alice", 2025, Calendar.JANUARY, 10),
                mockCommit("hash2", "Bob", 2025, Calendar.JANUARY, 20)
            )
        );

        ReportConfig config = createBaseConfig("Test Report");
        config.setEmployees(List.of("Alice", "Bob"));
        config.setDataSources(List.of(createCommitsDataSource("Commits", true, true, 2)));
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setSaveRawMetadata(true);
        outputConfig.setOutputPath(tempDir.resolve("out").toString()); // no trailing slash on purpose
        config.setOutput(outputConfig);

        AggregationConfig aggregation = new AggregationConfig();
        aggregation.setFormula("${Commits} * 2");
        aggregation.setLabel("Overall Score");
        config.setAggregation(aggregation);

        config.setCustomCharts(List.of(new CustomChartConfig("Commits Chart", "line", List.of("Commits"))));

        ReportGenerator generator = new ReportGenerator(null, sourceCode, null);
        ReportOutput output = generator.generateReport(config);

        assertNotNull(output);
        assertEquals("Test Report", output.getReportName());
        assertEquals("2025-01-01", output.getStartDate());
        assertEquals("2025-01-31", output.getEndDate());

        assertEquals(1, output.getTimePeriods().size());
        TimePeriodResult period = output.getTimePeriods().get(0);
        MetricSummary commits = period.getMetrics().get("Commits");
        assertNotNull(commits);
        assertEquals(2, commits.getCount());
        // each commit weighs 1.0, divider 2 -> total weight 1.0
        assertEquals(1.0, commits.getTotalWeight(), 0.001);
        // weight metric: score uses totalWeight -> 1.0 * 2
        assertEquals(2.0, period.getScore(), 0.001);

        assertEquals(2, period.getDataset().size(), "Raw metadata dataset should contain both commits");
        assertTrue(period.getContributorBreakdown().containsKey("Alice"));
        assertTrue(period.getContributorBreakdown().containsKey("Bob"));

        AggregatedResult aggregated = output.getAggregated();
        assertTrue(aggregated.getByContributor().containsKey("Alice"));
        assertEquals(1, aggregated.getByContributor().get("Alice").getMetrics().get("Commits").getCount());
        assertEquals(0.5, aggregated.getByContributor().get("Alice").getMetrics().get("Commits").getTotalWeight(), 0.001);
        assertEquals(2, aggregated.getTotal().getMetrics().get("Commits").getCount());
        assertEquals(2.0, aggregated.getTotal().getScore(), 0.001);

        assertEquals(List.of("Commits"), output.getWeightMetrics());
        assertEquals(2.0, output.getMetricDividers().get("Commits"), 0.001);
        assertEquals(1, output.getCustomCharts().size());
        assertEquals("${Commits} * 2", output.getAggregationFormula());
        assertEquals("Overall Score", output.getAggregationLabel());

        Path jsonFile = tempDir.resolve("out").resolve("Test_Report.json");
        assertTrue(Files.exists(jsonFile), "Report JSON should be written to output path");
    }

    @Test
    void testGenerateReports_multiGrouping_producesSuffixedReports() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = createBaseConfig("Multi Report");
        config.setDataSources(new ArrayList<>());
        config.setEndDate("2025-01-03");

        TimeGroupingConfig staticGrouping = createStaticGrouping("2025-01-01", "2025-01-31");
        TimeGroupingConfig dailyGrouping = new TimeGroupingConfig();
        dailyGrouping.setType("daily");
        config.setTimeGroupings(List.of(staticGrouping, dailyGrouping));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setOutputPath(tempDir.toString() + "/"); // trailing slash branch
        config.setOutput(outputConfig);

        List<ReportGenerator.ReportResult> results = generator.generateReports(config);

        assertEquals(2, results.size());
        assertEquals("Multi Report (static)", results.get(0).getOutput().getReportName());
        assertEquals("Multi Report (daily)", results.get(1).getOutput().getReportName());
        assertTrue(results.get(0).getJsonPath().endsWith("Multi_Report_static.json"));
        assertTrue(results.get(1).getJsonPath().endsWith("Multi_Report_daily.json"));
        assertTrue(Files.exists(Path.of(results.get(0).getJsonPath())));
        assertTrue(Files.exists(Path.of(results.get(1).getJsonPath())));
        // daily grouping over Jan 1-3 -> 3 periods
        assertEquals(3, results.get(1).getOutput().getTimePeriods().size());
        assertEquals("2025-01-01", results.get(1).getOutput().getTimePeriods().get(0).getName());
    }

    @Test
    void testGenerateReports_noEndDate_defaultsToToday() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = createBaseConfig("Today Report");
        config.setEndDate(null); // trigger the "default to today" branch
        config.setDataSources(new ArrayList<>());
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        List<ReportGenerator.ReportResult> results = generator.generateReports(config);

        assertEquals(1, results.size());
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        assertEquals(today, results.get(0).getOutput().getEndDate());
        assertNull(results.get(0).getOutput().getAggregationFormula(),
            "No aggregation configured -> formula should stay null");
    }

    @Test
    void testGenerateReport_noTimeGroupings_returnsNull() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = createBaseConfig("Empty Report");
        config.setDataSources(new ArrayList<>());

        assertNull(generator.generateReport(config),
            "Without time groupings no report is produced");
    }

    // --- Aggregation formula resolution ---

    @Test
    void testGenerateReports_aggregationFormulaFromFile() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        Path formulaFile = tempDir.resolve("formula.txt");
        Files.writeString(formulaFile, "  ${Commits} + 1  \n");

        ReportConfig config = createBaseConfig("Formula File Report");
        config.setDataSources(new ArrayList<>());
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        AggregationConfig aggregation = new AggregationConfig();
        aggregation.setFormula("${inline}");
        aggregation.setFormulaFile(formulaFile.toString());
        config.setAggregation(aggregation);

        List<ReportGenerator.ReportResult> results = generator.generateReports(config);

        ReportOutput output = results.get(0).getOutput();
        assertEquals("${Commits} + 1", output.getAggregationFormula(),
            "Formula file content (trimmed) should win over the inline formula");
        assertEquals("All Metrics Score", output.getAggregationLabel(),
            "Missing aggregation label should fall back to the default");
        assertEquals("${Commits} + 1", config.getAggregation().getFormula(),
            "Resolved formula should be written back into the config");
    }

    @Test
    void testGenerateReports_missingFormulaFile_fallsBackToInlineFormula() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = createBaseConfig("Missing Formula Report");
        config.setDataSources(new ArrayList<>());
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        AggregationConfig aggregation = new AggregationConfig();
        aggregation.setFormula("${Commits}");
        aggregation.setFormulaFile(tempDir.resolve("does-not-exist.txt").toString());
        config.setAggregation(aggregation);

        List<ReportGenerator.ReportResult> results = generator.generateReports(config);

        assertEquals("${Commits}", results.get(0).getOutput().getAggregationFormula(),
            "Missing formula file should fall back to the inline formula");
    }

    // --- Alias normalization ---

    @Test
    void testGenerateReports_aliasesAndCatchAllGroup_normalizeContributors() throws Exception {
        SourceCode sourceCode = mockSourceCodeWithCommits(
            List.of(
                mockCommit("hash1", "Alice", 2025, Calendar.JANUARY, 10),
                mockCommit("hash2", "Bob", 2025, Calendar.JANUARY, 12)
            )
        );

        ReportConfig config = createBaseConfig("Alias Report");
        config.setAliases(Map.of("Team Lead", List.of("Alice")));
        config.setCatchAllGroup("Humans");
        config.setDataSources(List.of(createCommitsDataSource("Commits", false, true, 1)));
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setSaveRawMetadata(true);
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        ReportGenerator generator = new ReportGenerator(null, sourceCode);
        ReportOutput output = generator.generateReport(config);

        Map<String, ContributorMetrics> byContributor = output.getAggregated().getByContributor();
        assertTrue(byContributor.containsKey("Team Lead"), "Alias should map Alice -> Team Lead");
        assertTrue(byContributor.containsKey("Humans"), "Catch-all group should absorb unmatched Bob");
        assertFalse(byContributor.containsKey("Alice"));
        assertFalse(byContributor.containsKey("Bob"));
    }

    // --- Link templates ---

    @Test
    void testGenerateReports_pullRequestsSource_setsLinkTemplate() throws Exception {
        SourceCode sourceCode = mock(SourceCode.class);
        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repo");
        when(sourceCode.getDefaultBranch()).thenReturn("main");

        IPullRequest pullRequest = mock(IPullRequest.class);
        IUser author = mock(IUser.class);
        when(author.getFullName()).thenReturn("Author");
        when(pullRequest.getAuthor()).thenReturn(author);
        when(pullRequest.getId()).thenReturn(42);
        when(pullRequest.getTitle()).thenReturn("Some PR");
        Calendar closedDate = Calendar.getInstance();
        closedDate.set(2025, Calendar.JANUARY, 15, 12, 0, 0);
        when(pullRequest.getClosedDate()).thenReturn(closedDate.getTimeInMillis());
        when(sourceCode.pullRequests(eq("workspace"), eq("repo"),
            eq(IPullRequest.PullRequestState.STATE_MERGED), eq(true), any(Calendar.class)))
            .thenReturn(List.of(pullRequest));

        MetricConfig metric = new MetricConfig();
        metric.setName("PullRequestsMetricSource");
        metric.setParams(new HashMap<>(Map.of("label", "PRs Merged")));

        DataSourceConfig dataSource = new DataSourceConfig();
        dataSource.setName("pullRequests");
        dataSource.setParams(new HashMap<>(Map.of("workspace", "workspace", "repository", "repo")));
        dataSource.setMetrics(List.of(metric));

        ReportConfig config = createBaseConfig("Links Report");
        config.setDataSources(List.of(dataSource));
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        ReportGenerator generator = new ReportGenerator(null, sourceCode);
        ReportOutput output = generator.generateReport(config);

        assertEquals("https://github.com/workspace/repo/pull/{key}",
            output.getLinkTemplates().get("PRs Merged"));
    }

    @Test
    void testBuildLinkTemplate_sourceTypeVariants() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);
        Method method = ReportGenerator.class.getDeclaredMethod(
            "buildLinkTemplate", String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> gitlab = new HashMap<>(Map.of(
            "sourceType", "gitlab", "workspace", "ws", "repository", "rp"));
        assertEquals("https://gitlab.com/ws/rp/pull/{key}",
            method.invoke(generator, "pullRequests", gitlab));
        assertEquals("https://gitlab.com/ws/rp/commit/{key}",
            method.invoke(generator, "commits", gitlab));

        Map<String, Object> bitbucket = new HashMap<>(Map.of(
            "sourceType", "bitbucket", "workspace", "ws", "repository", "rp"));
        assertEquals("https://bitbucket.org/ws/rp/pull/{key}",
            method.invoke(generator, "pullRequests", bitbucket));

        Map<String, Object> unknown = new HashMap<>(Map.of(
            "sourceType", "gerrit", "workspace", "ws", "repository", "rp"));
        assertEquals("https://github.com/ws/rp/commit/{key}",
            method.invoke(generator, "commits", unknown),
            "Unknown sourceType should default to the GitHub base URL");

        assertNull(method.invoke(generator, "commits", null), "Null params -> no template");
        assertNull(method.invoke(generator, "tracker", gitlab), "Tracker sources -> no template");
        assertNull(method.invoke(generator, "commits",
            new HashMap<>(Map.of("sourceType", "gitlab"))),
            "Missing workspace/repository -> no template");
    }

    // --- Merging results from duplicate data sources ---

    @Test
    void testGenerateReports_sameLabelAcrossDuplicateSources_mergesKeyTimes() throws Exception {
        SourceCode sourceCode = mock(SourceCode.class);
        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repo");
        when(sourceCode.getDefaultBranch()).thenReturn("main");
        ICommit mainCommit = mockCommit("hash1", "Alice", 2025, Calendar.JANUARY, 10);
        ICommit devCommit = mockCommit("hash2", "Bob", 2025, Calendar.JANUARY, 12);
        when(sourceCode.getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null))
            .thenReturn(List.of(mainCommit));
        when(sourceCode.getCommitsFromBranch("workspace", "repo", "dev", "2025-01-01", null))
            .thenReturn(List.of(devCommit));

        DataSourceConfig mainSource = createCommitsDataSource("Commits", false, false, 1);
        DataSourceConfig devSource = createCommitsDataSource("Commits", false, false, 1);
        devSource.setParams(new HashMap<>(Map.of(
            "workspace", "workspace", "repository", "repo", "branch", "dev")));

        ReportConfig config = createBaseConfig("Merge Report");
        config.setDataSources(List.of(mainSource, devSource));
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setSaveRawMetadata(true);
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        ReportGenerator generator = new ReportGenerator(null, sourceCode);
        ReportOutput output = generator.generateReport(config);

        MetricSummary commits = output.getAggregated().getTotal().getMetrics().get("Commits");
        assertNotNull(commits, "Commits from both branches should merge under the same label");
        assertEquals(2, commits.getCount());
        assertEquals(2, output.getTimePeriods().get(0).getDataset().size());
    }

    // --- Group-by metrics via CSV data source ---

    @Test
    void testGenerateReports_groupByCsvMetric_excludedFromContributorBreakdown() throws Exception {
        Path csvFile = tempDir.resolve("usage.csv");
        Files.writeString(csvFile, String.join("\n",
            "Date,Model,Tokens",
            "2025-01-10,gpt-4,100",
            "2025-01-12,claude,200"));

        MetricConfig metric = new MetricConfig();
        metric.setName("CsvMetricSource");
        Map<String, Object> metricParams = new HashMap<>();
        metricParams.put("label", "AI Tokens");
        metricParams.put("weightColumn", "Tokens");
        metricParams.put("whoColumn", "Model");
        metricParams.put("groupByField", "Model");
        metricParams.put("isPersonalized", true);
        metric.setParams(metricParams);

        DataSourceConfig dataSource = new DataSourceConfig();
        dataSource.setName("csv");
        dataSource.setParams(new HashMap<>(Map.of("filePath", csvFile.toString())));
        dataSource.setMetrics(List.of(metric));

        ReportConfig config = createBaseConfig("CSV Report");
        config.setDataSources(List.of(dataSource));
        config.setTimeGroupings(List.of(createStaticGrouping("2025-01-01", "2025-01-31")));

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.setSaveRawMetadata(true);
        outputConfig.setOutputPath(tempDir.toString());
        config.setOutput(outputConfig);

        ReportGenerator generator = new ReportGenerator(null, null);
        ReportOutput output = generator.generateReport(config);

        assertEquals(List.of("AI Tokens"), output.getGroupByMetrics());

        MetricSummary total = output.getAggregated().getTotal().getMetrics().get("AI Tokens");
        assertNotNull(total);
        assertEquals(2, total.getCount());
        assertEquals(300.0, total.getTotalWeight(), 0.001);

        assertTrue(output.getAggregated().getByContributor().isEmpty(),
            "Group-by metric categories must not appear as contributors");
        assertTrue(output.getTimePeriods().get(0).getContributorBreakdown().isEmpty(),
            "Group-by metrics are skipped in per-period contributor breakdown");
    }

    // --- Period generation gaps ---

    @Test
    void testGenerateTimePeriods_daily() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-01-03");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("daily");

        List<TimePeriod> periods = generator.generateTimePeriods(config, grouping);

        assertEquals(3, periods.size());
        assertEquals("2025-01-01", periods.get(0).getName());
        assertEquals("2025-01-01", periods.get(0).getStart());
        assertEquals("2025-01-01", periods.get(0).getEnd());
        assertEquals("2025-01-03", periods.get(2).getName());
    }

    // --- calculateRateLimitDelayMs branches ---

    @Test
    void testCalculateRateLimitDelayMs_prefersRetryAfterHeader() {
        ReportGenerator generator = new ReportGenerator(null, null);
        Response response = mock(Response.class);
        when(response.header("Retry-After")).thenReturn("5");
        when(response.header("X-RateLimit-Reset")).thenReturn(null);

        long delayMs = generator.calculateRateLimitDelayMs(
            new RestClient.RateLimitException("rate limit", "rate limit", response, 429), 1);

        assertEquals(5000L, delayMs, "Retry-After seconds should be converted to milliseconds");
    }

    @Test
    void testCalculateRateLimitDelayMs_resetTimestampInPast_returnsZero() {
        ReportGenerator generator = new ReportGenerator(null, null);
        Response response = mock(Response.class);
        when(response.header("Retry-After")).thenReturn("0"); // not > 0 -> ignored
        when(response.header("X-RateLimit-Reset"))
            .thenReturn(String.valueOf(System.currentTimeMillis() / 1000L - 100L));

        long delayMs = generator.calculateRateLimitDelayMs(
            new RestClient.RateLimitException("rate limit", "rate limit", response, 429), 1);

        assertEquals(0L, delayMs, "Reset timestamp in the past should yield no delay");
    }

    @Test
    void testCalculateRateLimitDelayMs_unparseableHeaders_fallsBackToDefault() {
        ReportGenerator generator = new ReportGenerator(null, null);
        Response response = mock(Response.class);
        when(response.header("Retry-After")).thenReturn("not-a-number");
        when(response.header("X-RateLimit-Reset")).thenReturn("also-not-a-number");

        long delayMs = generator.calculateRateLimitDelayMs(
            new RestClient.RateLimitException("rate limit", "rate limit", response, 429), 1);

        assertEquals(60000L, delayMs, "Invalid rate-limit metadata should fall back to 60s");
    }

    @Test
    void testCalculateRateLimitDelayMs_rateLimitMessageWithoutMetadata_fallsBackToDefault() {
        ReportGenerator generator = new ReportGenerator(null, null);

        assertEquals(60000L, generator.calculateRateLimitDelayMs(
            new IOException("Too many requests"), 1));
        assertEquals(60000L, generator.calculateRateLimitDelayMs(
            new IOException("Request was throttled by the server"), 1));
        assertEquals(60000L, generator.calculateRateLimitDelayMs(
            new IOException("HTTP 429"), 1));
    }

    @Test
    void testCalculateRateLimitDelayMs_nonRateLimitError_usesExponentialBackoff() {
        ReportGenerator generator = new ReportGenerator(null, null);

        long delayMs = generator.calculateRateLimitDelayMs(new IOException("connection reset"), 1);

        // base delay 1000ms with +/-30% jitter
        assertTrue(delayMs >= 500L && delayMs <= 2000L,
            "Non-rate-limit errors should use the retry policy backoff, got " + delayMs);
    }

    @Test
    void testCalculateRateLimitDelayMs_nullMessage_usesExponentialBackoff() {
        ReportGenerator generator = new ReportGenerator(null, null);

        long delayMs = generator.calculateRateLimitDelayMs(new IOException(), 1);

        assertTrue(delayMs >= 500L && delayMs <= 2000L,
            "Null message is not a rate-limit error, got " + delayMs);
    }

    // --- Helpers ---

    private ReportConfig createBaseConfig(String reportName) {
        ReportConfig config = new ReportConfig();
        config.setReportName(reportName);
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-01-31");
        return config;
    }

    private TimeGroupingConfig createStaticGrouping(String start, String end) {
        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("static");
        grouping.setPeriods(List.of(new TimePeriod("Period", start, end)));
        return grouping;
    }

    private DataSourceConfig createCommitsDataSource(String label, boolean isWeight,
                                                     boolean isPersonalized, int divider) {
        MetricConfig metric = new MetricConfig();
        metric.setName("CommitsMetricSource");
        Map<String, Object> metricParams = new HashMap<>();
        metricParams.put("label", label);
        metricParams.put("isWeight", isWeight);
        metricParams.put("isPersonalized", isPersonalized);
        metricParams.put("divider", divider);
        metric.setParams(metricParams);

        DataSourceConfig dataSource = new DataSourceConfig();
        dataSource.setName("commits");
        dataSource.setParams(new HashMap<>(Map.of(
            "workspace", "workspace", "repository", "repo", "branch", "main")));
        dataSource.setMetrics(List.of(metric));
        return dataSource;
    }

    private SourceCode mockSourceCodeWithCommits(List<ICommit> commits) throws Exception {
        SourceCode sourceCode = mock(SourceCode.class);
        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repo");
        when(sourceCode.getDefaultBranch()).thenReturn("main");
        when(sourceCode.getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null))
            .thenReturn(commits);
        return sourceCode;
    }

    private ICommit mockCommit(String hash, String authorName, int year, int month, int day) {
        ICommit commit = mock(ICommit.class);
        IUser author = mock(IUser.class);
        when(author.getFullName()).thenReturn(authorName);
        when(commit.getAuthor()).thenReturn(author);
        when(commit.getHash()).thenReturn(hash);
        Calendar date = Calendar.getInstance();
        date.set(year, month, day, 12, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        when(commit.getCommitterDate()).thenReturn(date);
        when(commit.getMessage()).thenReturn("Commit " + hash);
        when(commit.getUrl()).thenReturn("https://github.test/commit/" + hash);
        return commit;
    }
}
