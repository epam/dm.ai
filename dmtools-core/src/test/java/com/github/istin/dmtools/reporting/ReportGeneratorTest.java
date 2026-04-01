// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.reporting.model.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    /**
     * Helper method to create a KeyTime for testing
     */
    private KeyTime createKeyTime(String key, Calendar when, String who) {
        KeyTime kt = new KeyTime(key, when, who);
        kt.setWeight(1.0);
        return kt;
    }
}
