// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.reporting.datasource.DataSourceFactory;
import com.github.istin.dmtools.reporting.metrics.MetricFactory;
import com.github.istin.dmtools.reporting.model.*;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportGenerator
 * Tests core reporting logic including KeyTime filtering, period generation, dayShift
 */
class ReportGeneratorTest {

    @Test
    void testFilterKeyTimesByPeriod_normalPeriod() throws Exception {
        // Given: ReportGenerator with filterKeyTimesByPeriod method
        ReportGenerator generator = new ReportGenerator(null, null);
        Method filterMethod = ReportGenerator.class.getDeclaredMethod(
            "filterKeyTimesByPeriod",
            List.class,
            Calendar.class,
            Calendar.class
        );
        filterMethod.setAccessible(true);

        // Create test KeyTimes
        List<KeyTime> keyTimes = new ArrayList<>();

        // KeyTime before period (should be filtered out)
        Calendar beforePeriod = Calendar.getInstance();
        beforePeriod.set(2024, Calendar.DECEMBER, 15);
        keyTimes.add(createKeyTime("BEFORE", beforePeriod, "John"));

        // KeyTime within period (should pass)
        Calendar withinPeriod = Calendar.getInstance();
        withinPeriod.set(2025, Calendar.JANUARY, 15);
        keyTimes.add(createKeyTime("WITHIN", withinPeriod, "Jane"));

        // KeyTime after period (should be filtered out)
        Calendar afterPeriod = Calendar.getInstance();
        afterPeriod.set(2025, Calendar.FEBRUARY, 15);
        keyTimes.add(createKeyTime("AFTER", afterPeriod, "Bob"));

        // Period: January 2025
        Calendar periodStart = Calendar.getInstance();
        periodStart.set(2025, Calendar.JANUARY, 1, 0, 0, 0);

        Calendar periodEnd = Calendar.getInstance();
        periodEnd.set(2025, Calendar.JANUARY, 31, 23, 59, 59);

        // When: Filter KeyTimes
        @SuppressWarnings("unchecked")
        List<KeyTime> filtered = (List<KeyTime>) filterMethod.invoke(
            generator,
            keyTimes,
            periodStart,
            periodEnd
        );

        // Then: Only WITHIN KeyTime should remain
        assertEquals(1, filtered.size(), "Should have 1 KeyTime after filtering");
        assertEquals("WITHIN", filtered.get(0).getKey(), "Should be the WITHIN KeyTime");
    }

    @Test
    void testFilterKeyTimesByPeriod_unlimitedEndDate() throws Exception {
        // Given: ReportGenerator with filterKeyTimesByPeriod method
        ReportGenerator generator = new ReportGenerator(null, null);
        Method filterMethod = ReportGenerator.class.getDeclaredMethod(
            "filterKeyTimesByPeriod",
            List.class,
            Calendar.class,
            Calendar.class
        );
        filterMethod.setAccessible(true);

        // Create test KeyTimes
        List<KeyTime> keyTimes = new ArrayList<>();

        // KeyTime before start date (should be filtered out)
        Calendar beforeStart = Calendar.getInstance();
        beforeStart.set(2024, Calendar.DECEMBER, 15);
        keyTimes.add(createKeyTime("BEFORE", beforeStart, "John"));

        // KeyTime after start date in 2025 (should pass)
        Calendar in2025 = Calendar.getInstance();
        in2025.set(2025, Calendar.JANUARY, 15);
        keyTimes.add(createKeyTime("JAN", in2025, "Jane"));

        // KeyTime much later in 2025 (should pass with unlimited end)
        Calendar muchLater = Calendar.getInstance();
        muchLater.set(2025, Calendar.NOVEMBER, 15);
        keyTimes.add(createKeyTime("NOV", muchLater, "Bob"));

        // Period: 2025-01-01 to 9999-12-31 (unlimited end date)
        Calendar periodStart = Calendar.getInstance();
        periodStart.set(2025, Calendar.JANUARY, 1, 0, 0, 0);

        Calendar periodEnd = Calendar.getInstance();
        periodEnd.set(9999, Calendar.DECEMBER, 31, 23, 59, 59);

        // When: Filter KeyTimes with unlimited end date
        @SuppressWarnings("unchecked")
        List<KeyTime> filtered = (List<KeyTime>) filterMethod.invoke(
            generator,
            keyTimes,
            periodStart,
            periodEnd
        );

        // Then: All KeyTimes after start date should remain (not limited by end date)
        assertEquals(2, filtered.size(), "Should have 2 KeyTimes (everything from 2025-01-01)");
        assertTrue(filtered.stream().anyMatch(kt -> "JAN".equals(kt.getKey())),
            "Should include JAN KeyTime");
        assertTrue(filtered.stream().anyMatch(kt -> "NOV".equals(kt.getKey())),
            "Should include NOV KeyTime");
        assertFalse(filtered.stream().anyMatch(kt -> "BEFORE".equals(kt.getKey())),
            "Should not include BEFORE KeyTime");
    }

    @Test
    void testFilterKeyTimesByPeriod_allKeyTimesBeforePeriod() throws Exception {
        // Given: All KeyTimes before period
        ReportGenerator generator = new ReportGenerator(null, null);
        Method filterMethod = ReportGenerator.class.getDeclaredMethod(
            "filterKeyTimesByPeriod",
            List.class,
            Calendar.class,
            Calendar.class
        );
        filterMethod.setAccessible(true);

        List<KeyTime> keyTimes = new ArrayList<>();

        Calendar oldDate = Calendar.getInstance();
        oldDate.set(2024, Calendar.JANUARY, 15);
        keyTimes.add(createKeyTime("OLD1", oldDate, "John"));
        keyTimes.add(createKeyTime("OLD2", oldDate, "Jane"));

        Calendar periodStart = Calendar.getInstance();
        periodStart.set(2025, Calendar.JANUARY, 1);

        Calendar periodEnd = Calendar.getInstance();
        periodEnd.set(2025, Calendar.JANUARY, 31);

        // When
        @SuppressWarnings("unchecked")
        List<KeyTime> filtered = (List<KeyTime>) filterMethod.invoke(
            generator,
            keyTimes,
            periodStart,
            periodEnd
        );

        // Then: All should be filtered out
        assertEquals(0, filtered.size(), "All KeyTimes should be filtered out");
    }

    @Test
    void testFilterKeyTimesByPeriod_emptyList() throws Exception {
        // Given: Empty KeyTimes list
        ReportGenerator generator = new ReportGenerator(null, null);
        Method filterMethod = ReportGenerator.class.getDeclaredMethod(
            "filterKeyTimesByPeriod",
            List.class,
            Calendar.class,
            Calendar.class
        );
        filterMethod.setAccessible(true);

        List<KeyTime> keyTimes = new ArrayList<>();

        Calendar periodStart = Calendar.getInstance();
        periodStart.set(2025, Calendar.JANUARY, 1);

        Calendar periodEnd = Calendar.getInstance();
        periodEnd.set(2025, Calendar.JANUARY, 31);

        // When
        @SuppressWarnings("unchecked")
        List<KeyTime> filtered = (List<KeyTime>) filterMethod.invoke(
            generator,
            keyTimes,
            periodStart,
            periodEnd
        );

        // Then: Should return empty list
        assertNotNull(filtered, "Filtered list should not be null");
        assertEquals(0, filtered.size(), "Filtered list should be empty");
    }

    @Test
    void testFilterKeyTimesByPeriod_onBoundary() throws Exception {
        // Given: KeyTimes exactly on period boundaries
        ReportGenerator generator = new ReportGenerator(null, null);
        Method filterMethod = ReportGenerator.class.getDeclaredMethod(
            "filterKeyTimesByPeriod",
            List.class,
            Calendar.class,
            Calendar.class
        );
        filterMethod.setAccessible(true);

        List<KeyTime> keyTimes = new ArrayList<>();

        // Exactly on start date
        Calendar onStart = Calendar.getInstance();
        onStart.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        onStart.set(Calendar.MILLISECOND, 0);
        keyTimes.add(createKeyTime("START", onStart, "John"));

        // Exactly on end date
        Calendar onEnd = Calendar.getInstance();
        onEnd.set(2025, Calendar.JANUARY, 31, 23, 59, 59);
        onEnd.set(Calendar.MILLISECOND, 0);
        keyTimes.add(createKeyTime("END", onEnd, "Jane"));

        Calendar periodStart = Calendar.getInstance();
        periodStart.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        periodStart.set(Calendar.MILLISECOND, 0);

        Calendar periodEnd = Calendar.getInstance();
        periodEnd.set(2025, Calendar.JANUARY, 31, 23, 59, 59);
        periodEnd.set(Calendar.MILLISECOND, 0);

        // When
        @SuppressWarnings("unchecked")
        List<KeyTime> filtered = (List<KeyTime>) filterMethod.invoke(
            generator,
            keyTimes,
            periodStart,
            periodEnd
        );

        // Then: Both boundary KeyTimes should be included
        assertEquals(2, filtered.size(), "Boundary KeyTimes should be included");
    }

    // --- New tests for quarterly, yearly, dayShift, and multi-grouping ---

    @Test
    void testGenerateQuarterlyPeriods() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Calendar start = Calendar.getInstance();
        start.setTime(sdf.parse("2025-01-01"));

        Calendar end = Calendar.getInstance();
        end.setTime(sdf.parse("2025-12-31"));

        List<TimePeriod> periods = generator.generateQuarterlyPeriods(start, end, sdf);

        assertEquals(4, periods.size(), "Full year should have 4 quarters");
        assertEquals("Q1 2025", periods.get(0).getName());
        assertEquals("2025-01-01", periods.get(0).getStart());
        assertEquals("2025-03-31", periods.get(0).getEnd());

        assertEquals("Q2 2025", periods.get(1).getName());
        assertEquals("2025-04-01", periods.get(1).getStart());
        assertEquals("2025-06-30", periods.get(1).getEnd());

        assertEquals("Q3 2025", periods.get(2).getName());
        assertEquals("2025-07-01", periods.get(2).getStart());
        assertEquals("2025-09-30", periods.get(2).getEnd());

        assertEquals("Q4 2025", periods.get(3).getName());
        assertEquals("2025-10-01", periods.get(3).getStart());
        assertEquals("2025-12-31", periods.get(3).getEnd());
    }

    @Test
    void testGenerateQuarterlyPeriods_partialYear() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Calendar start = Calendar.getInstance();
        start.setTime(sdf.parse("2025-03-15"));

        Calendar end = Calendar.getInstance();
        end.setTime(sdf.parse("2025-08-20"));

        List<TimePeriod> periods = generator.generateQuarterlyPeriods(start, end, sdf);

        assertEquals(3, periods.size(), "Should have 3 partial quarters");
        // Q1 starts from March 15
        assertEquals("Q1 2025", periods.get(0).getName());
        assertEquals("2025-03-15", periods.get(0).getStart());
        assertEquals("2025-03-31", periods.get(0).getEnd());

        // Q2 full
        assertEquals("Q2 2025", periods.get(1).getName());
        assertEquals("2025-04-01", periods.get(1).getStart());
        assertEquals("2025-06-30", periods.get(1).getEnd());

        // Q3 truncated at end date
        assertEquals("Q3 2025", periods.get(2).getName());
        assertEquals("2025-07-01", periods.get(2).getStart());
        assertEquals("2025-08-20", periods.get(2).getEnd());
    }

    @Test
    void testGenerateYearlyPeriods() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Calendar start = Calendar.getInstance();
        start.setTime(sdf.parse("2023-01-01"));

        Calendar end = Calendar.getInstance();
        end.setTime(sdf.parse("2025-12-31"));

        List<TimePeriod> periods = generator.generateYearlyPeriods(start, end, sdf);

        assertEquals(3, periods.size(), "Should have 3 years");
        assertEquals("2023", periods.get(0).getName());
        assertEquals("2023-01-01", periods.get(0).getStart());
        assertEquals("2023-12-31", periods.get(0).getEnd());

        assertEquals("2024", periods.get(1).getName());
        assertEquals("2024-01-01", periods.get(1).getStart());
        assertEquals("2024-12-31", periods.get(1).getEnd());

        assertEquals("2025", periods.get(2).getName());
        assertEquals("2025-01-01", periods.get(2).getStart());
        assertEquals("2025-12-31", periods.get(2).getEnd());
    }

    @Test
    void testGenerateYearlyPeriods_partialYear() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Calendar start = Calendar.getInstance();
        start.setTime(sdf.parse("2025-06-15"));

        Calendar end = Calendar.getInstance();
        end.setTime(sdf.parse("2025-09-30"));

        List<TimePeriod> periods = generator.generateYearlyPeriods(start, end, sdf);

        assertEquals(1, periods.size(), "Should have 1 partial year");
        assertEquals("2025", periods.get(0).getName());
        assertEquals("2025-06-15", periods.get(0).getStart());
        assertEquals("2025-09-30", periods.get(0).getEnd());
    }

    @Test
    void testDayShift_shiftsStartDate() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01"); // Wednesday
        config.setEndDate("2025-01-31");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("weekly");
        grouping.setDayShift(2); // Shift by 2 days -> starts from Friday Jan 3

        List<TimePeriod> periods = generator.generateTimePeriods(config, grouping);

        assertFalse(periods.isEmpty());
        // First period should start on Jan 3 (shifted by 2 days)
        assertEquals("2025-01-03", periods.get(0).getStart());
    }

    @Test
    void testDayShift_zero_noShift() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-01-31");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("weekly");
        grouping.setDayShift(0);

        List<TimePeriod> periods = generator.generateTimePeriods(config, grouping);

        assertFalse(periods.isEmpty());
        assertEquals("2025-01-01", periods.get(0).getStart());
    }

    @Test
    void testDayShift_withBiWeekly() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-02-28");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("bi-weekly");
        grouping.setDayShift(5);

        List<TimePeriod> periods = generator.generateTimePeriods(config, grouping);

        assertFalse(periods.isEmpty());
        // Shifted by 5 days: Jan 6
        assertEquals("2025-01-06", periods.get(0).getStart());
    }

    @Test
    void testGenerateTimePeriods_staticIgnoresDayShift() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-12-31");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("static");
        grouping.setDayShift(5); // Should be ignored for static

        List<TimePeriod> staticPeriods = new ArrayList<>();
        staticPeriods.add(new TimePeriod("Q1", "2025-01-01", "2025-03-31"));
        grouping.setPeriods(staticPeriods);

        List<TimePeriod> periods = generator.generateTimePeriods(config, grouping);

        assertEquals(1, periods.size());
        assertEquals("2025-01-01", periods.get(0).getStart());
    }

    @Test
    void testGenerateTimePeriods_unknownTypeThrows() {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-12-31");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("invalid-type");

        assertThrows(IllegalArgumentException.class, () ->
            generator.generateTimePeriods(config, grouping)
        );
    }

    @Test
    void testGenerateTimePeriods_nullEndDate_defaultsToToday() throws Exception {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        // endDate is null

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("monthly");

        List<TimePeriod> periods = generator.generateTimePeriods(config, grouping);

        assertFalse(periods.isEmpty(), "Should generate periods up to today");
        assertEquals("2025-01-01", periods.get(0).getStart());

        // Last period end should be today or later (within the month containing today)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new java.util.Date());
        String lastEnd = periods.get(periods.size() - 1).getEnd();
        assertTrue(lastEnd.compareTo(today) >= 0 || lastEnd.equals(today),
            "Last period end (" + lastEnd + ") should be >= today (" + today + ")");
    }

    @Test
    void testGenerateTimePeriods_nullStartDate_throws() {
        ReportGenerator generator = new ReportGenerator(null, null);

        ReportConfig config = new ReportConfig();
        // startDate is null
        config.setEndDate("2025-12-31");

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("weekly");

        assertThrows(IllegalArgumentException.class, () ->
            generator.generateTimePeriods(config, grouping)
        );
    }

    @Test
    void testFormulaEvaluator_subtraction() {
        java.util.Map<String, Double> values = new java.util.HashMap<>();
        values.put("Total Tokens (M)", 100.0);
        values.put("Output Tokens (M)", 30.0);

        double result = com.github.istin.dmtools.reporting.formula.FormulaEvaluator.evaluate(
            "${Total Tokens (M)} - ${Output Tokens (M)}", values
        );
        assertEquals(70.0, result, 0.01);
    }

    @Test
    void testFormulaEvaluator_multiplication() {
        java.util.Map<String, Double> values = new java.util.HashMap<>();
        values.put("A", 5.0);
        values.put("B", 3.0);

        double result = com.github.istin.dmtools.reporting.formula.FormulaEvaluator.evaluate(
            "${A} * ${B}", values
        );
        assertEquals(15.0, result, 0.01);
    }

    @Test
    void testFormulaEvaluator_missingMetric_defaultsToZero() {
        java.util.Map<String, Double> values = new java.util.HashMap<>();
        values.put("A", 50.0);

        double result = com.github.istin.dmtools.reporting.formula.FormulaEvaluator.evaluate(
            "${A} - ${Missing}", values
        );
        assertEquals(50.0, result, 0.01);
    }

    @Test
    void testApplyComputedMetrics() {
        java.util.Map<String, MetricSummary> metrics = new java.util.HashMap<>();
        metrics.put("Total", new MetricSummary(10, 100.0, new ArrayList<>(List.of("Alice"))));
        metrics.put("Output", new MetricSummary(10, 30.0, new ArrayList<>(List.of("Alice"))));

        List<ComputedMetricConfig> computed = List.of(
            new ComputedMetricConfig("Input", "${Total} - ${Output}", true, true)
        );

        java.util.Set<String> weightLabels = new java.util.HashSet<>(List.of("Total", "Output"));
        com.github.istin.dmtools.reporting.formula.ComputedMetricsApplier.applyToMetrics(
            computed, metrics, weightLabels
        );

        assertTrue(metrics.containsKey("Input"));
        assertEquals(70.0, metrics.get("Input").getTotalWeight(), 0.01);
        assertTrue(weightLabels.contains("Input"));
    }

    @Test
    void testCollectDataFromAllSources_retriesOnlyInterruptedGitHubMetric() throws Exception {
        SourceCode sourceCode = mock(SourceCode.class);
        IPullRequest pullRequest = mockPullRequest("123", "PR title");
        IActivity approvalActivity = mock(IActivity.class);
        IUser approver = mock(IUser.class);
        when(approver.getFullName()).thenReturn("Reviewer");
        when(approvalActivity.getApproval()).thenReturn(approver);

        Response rateLimitResponse = mock(Response.class);
        when(rateLimitResponse.header("Retry-After")).thenReturn(null);
        when(rateLimitResponse.header("X-RateLimit-Reset"))
            .thenReturn(String.valueOf(System.currentTimeMillis() / 1000L));

        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repo");
        when(sourceCode.getDefaultBranch()).thenReturn("main");
        when(sourceCode.pullRequests(eq("workspace"), eq("repo"), eq(IPullRequest.PullRequestState.STATE_MERGED), eq(true), any(Calendar.class)))
            .thenReturn(List.of(pullRequest))
            .thenThrow(new RestClient.RateLimitException("rate limit", "rate limit", rateLimitResponse, 429))
            .thenReturn(List.of(pullRequest));
        when(sourceCode.pullRequestActivities("workspace", "repo", "123"))
            .thenReturn(List.of(approvalActivity));

        ReportGenerator generator = new TestableReportGenerator(sourceCode);

        Map<String, Map<String, ReportGenerator.DataSourceResult>> results =
            invokeCollectDataFromAllSources(generator, sourceCode, createPullRequestReportConfig());

        assertEquals(1, results.size());
        assertTrue(results.containsKey("pullRequests"));
        assertEquals(2, results.get("pullRequests").size());
        assertTrue(results.get("pullRequests").containsKey("PullRequestsMetricSource"));
        assertTrue(results.get("pullRequests").containsKey("PullRequestsApprovalsMetricSource"));
        verify(sourceCode, times(3))
            .pullRequests(eq("workspace"), eq("repo"), eq(IPullRequest.PullRequestState.STATE_MERGED), eq(true), any(Calendar.class));
        verify(sourceCode, times(1)).pullRequestActivities("workspace", "repo", "123");
    }

    @Test
    void testCollectDataFromAllSources_usesFallbackDelayWhenRateLimitMetadataMissing() throws Exception {
        SourceCode sourceCode = mock(SourceCode.class);
        ICommit commit = mock(ICommit.class);
        IUser author = mock(IUser.class);
        Calendar commitDate = Calendar.getInstance();
        commitDate.set(2025, Calendar.JANUARY, 15, 10, 0, 0);
        commitDate.set(Calendar.MILLISECOND, 0);

        when(author.getFullName()).thenReturn("Author");
        when(commit.getAuthor()).thenReturn(author);
        when(commit.getHash()).thenReturn("abc123");
        when(commit.getCommitterDate()).thenReturn(commitDate);
        when(commit.getMessage()).thenReturn("Commit message");
        when(commit.getUrl()).thenReturn("https://github.test/commit/abc123");

        Response rateLimitResponse = mock(Response.class);
        when(rateLimitResponse.header("Retry-After")).thenReturn("invalid");
        when(rateLimitResponse.header("X-RateLimit-Reset")).thenReturn("invalid");

        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repo");
        when(sourceCode.getDefaultBranch()).thenReturn("main");
        when(sourceCode.getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null))
            .thenThrow(new RestClient.RateLimitException("rate limit", "rate limit", rateLimitResponse, 429))
            .thenReturn(List.of(commit));

        TestableReportGenerator generator = new TestableReportGenerator(sourceCode);

        Map<String, Map<String, ReportGenerator.DataSourceResult>> results =
            invokeCollectDataFromAllSources(generator, sourceCode, createCommitsReportConfig());

        assertEquals(1, results.size());
        assertTrue(results.containsKey("commits"));
        assertTrue(results.get("commits").containsKey("CommitsMetricSource"));
        assertEquals(List.of(60000L), generator.getObservedDelays());
        verify(sourceCode, times(2)).getCommitsFromBranch("workspace", "repo", "main", "2025-01-01", null);
    }

    @Test
    void testCollectDataFromAllSources_skipsMetricOnNonRetryableError() throws Exception {
        SourceCode sourceCode = mock(SourceCode.class);
        IPullRequest pullRequest = mockPullRequest("123", "PR title");
        IActivity approvalActivity = mock(IActivity.class);
        IUser approver = mock(IUser.class);
        when(approver.getFullName()).thenReturn("Reviewer");
        when(approvalActivity.getApproval()).thenReturn(approver);

        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repo");
        when(sourceCode.getDefaultBranch()).thenReturn("main");

        // PullRequestsMetricSource throws a non-retryable 404
        when(sourceCode.pullRequests(eq("workspace"), eq("repo"), eq(IPullRequest.PullRequestState.STATE_MERGED), eq(true), any(Calendar.class)))
            .thenThrow(new RestClient.RestClientException("Not Found", "Not Found", 404));

        // PullRequestsApprovalsMetricSource succeeds normally
        when(sourceCode.pullRequestActivities("workspace", "repo", "123"))
            .thenReturn(List.of(approvalActivity));

        ReportGenerator generator = new TestableReportGenerator(sourceCode);

        // Should NOT throw — the 404 metric is skipped, the rest of the job continues
        Map<String, Map<String, ReportGenerator.DataSourceResult>> results =
            invokeCollectDataFromAllSources(generator, sourceCode, createPullRequestReportConfig());

        // Both metric keys are present; the failed one returns an empty result
        assertTrue(results.containsKey("pullRequests"));
        Map<String, ReportGenerator.DataSourceResult> sourceResults = results.get("pullRequests");
        assertTrue(sourceResults.containsKey("PullRequestsMetricSource"), "Failed metric key should still be present");
        assertEquals(0, sourceResults.get("PullRequestsMetricSource").getAllKeyTimes().size(),
            "Failed metric should yield empty result, not crash");
        assertTrue(sourceResults.containsKey("PullRequestsApprovalsMetricSource"),
            "Subsequent metric should still be collected after a sibling failure");
    }


    @Test
    void testCalculateRateLimitDelayMs_usesGitHubResetHeaderBeyondDefaultRetryCap() {
        TestableReportGenerator generator = new TestableReportGenerator(mock(SourceCode.class));
        Response rateLimitResponse = mock(Response.class);
        long resetTimeSeconds = (System.currentTimeMillis() / 1000L) + 120L;

        when(rateLimitResponse.header("Retry-After")).thenReturn(null);
        when(rateLimitResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(resetTimeSeconds));

        long delayMs = generator.calculateRateLimitDelayMs(
            new RestClient.RateLimitException("rate limit", "rate limit", rateLimitResponse, 429),
            1
        );

        assertTrue(delayMs >= 119000L, "Delay should honor the reset timestamp rather than fall back to 60s");
        assertTrue(delayMs <= 122000L, "Delay should stay close to the reset timestamp plus buffer");
    }

    /**
     * Helper method to create a KeyTime for testing
     */
    private KeyTime createKeyTime(String key, Calendar when, String who) {
        KeyTime kt = new KeyTime(key, when, who);
        kt.setWeight(1.0);
        return kt;
    }

    private IPullRequest mockPullRequest(String id, String title) {
        IPullRequest pullRequest = mock(IPullRequest.class);
        IUser author = mock(IUser.class);
        when(author.getFullName()).thenReturn("Author");
        when(pullRequest.getAuthor()).thenReturn(author);
        when(pullRequest.getId()).thenReturn(Integer.valueOf(id));
        when(pullRequest.getTitle()).thenReturn(title);
        when(pullRequest.getClosedDate()).thenReturn(System.currentTimeMillis());
        return pullRequest;
    }

    private ReportConfig createPullRequestReportConfig() {
        MetricConfig pullRequestsMetric = new MetricConfig();
        pullRequestsMetric.setName("PullRequestsMetricSource");
        pullRequestsMetric.setParams(new HashMap<>());

        MetricConfig approvalsMetric = new MetricConfig();
        approvalsMetric.setName("PullRequestsApprovalsMetricSource");
        approvalsMetric.setParams(new HashMap<>());

        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setName("pullRequests");
        dataSourceConfig.setParams(new HashMap<>(Map.of(
            "workspace", "workspace",
            "repository", "repo"
        )));
        dataSourceConfig.setMetrics(List.of(pullRequestsMetric, approvalsMetric));

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setDataSources(List.of(dataSourceConfig));
        config.setTimeGroupings(Collections.singletonList(new TimeGroupingConfig()));
        return config;
    }

    private ReportConfig createCommitsReportConfig() {
        MetricConfig commitsMetric = new MetricConfig();
        commitsMetric.setName("CommitsMetricSource");
        commitsMetric.setParams(new HashMap<>());

        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setName("commits");
        dataSourceConfig.setParams(new HashMap<>(Map.of(
            "workspace", "workspace",
            "repository", "repo",
            "branch", "main"
        )));
        dataSourceConfig.setMetrics(List.of(commitsMetric));

        ReportConfig config = new ReportConfig();
        config.setStartDate("2025-01-01");
        config.setDataSources(List.of(dataSourceConfig));
        config.setTimeGroupings(Collections.singletonList(new TimeGroupingConfig()));
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, ReportGenerator.DataSourceResult>> invokeCollectDataFromAllSources(
        ReportGenerator generator,
        SourceCode sourceCode,
        ReportConfig config
    ) throws Exception {
        Method method = ReportGenerator.class.getDeclaredMethod(
            "collectDataFromAllSources",
            ReportConfig.class,
            com.github.istin.dmtools.common.tracker.TrackerClient.class,
            SourceCode.class,
            DataSourceFactory.class,
            MetricFactory.class
        );
        method.setAccessible(true);
        return (Map<String, Map<String, ReportGenerator.DataSourceResult>>) method.invoke(
            generator,
            config,
            null,
            sourceCode,
            new DataSourceFactory(),
            new MetricFactory(null, sourceCode, null, null, config.getStartDate())
        );
    }

    private static class TestableReportGenerator extends ReportGenerator {
        private final List<Long> observedDelays = new ArrayList<>();

        private TestableReportGenerator(SourceCode sourceCode) {
            super(null, sourceCode);
        }

        @Override
        protected void sleepBeforeRetry(long delayMs) {
            observedDelays.add(delayMs);
        }

        private List<Long> getObservedDelays() {
            return observedDelays;
        }
    }
}
