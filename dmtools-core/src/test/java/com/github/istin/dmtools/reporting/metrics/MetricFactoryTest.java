// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.Metric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricFactory
 * Tests metric creation from configuration
 */
@ExtendWith(MockitoExtension.class)
class MetricFactoryTest {

    @Mock
    private TrackerClient mockTrackerClient;

    @Mock
    private SourceCode mockSourceCode;

    private MetricFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MetricFactory(mockTrackerClient, mockSourceCode);
    }

    @Test
    void testCreateTicketMovedToStatusRule_singleStatus() throws Exception {
        // Given: Configuration for TicketMovedToStatusRule with single status
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));
        params.put("label", "Completed Tickets");
        params.put("isWeight", true);

        // When: Create metric
        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        // Then: Metric should be created with correct properties
        assertNotNull(metric, "Metric should not be null");
        assertEquals("Completed Tickets", metric.getName(), "Metric name should match label");
        assertTrue(metric.isWeight(), "Metric should have isWeight=true");
        assertNotNull(metric.getRule(), "Metric should have a rule");
    }

    @Test
    void testCreateTicketMovedToStatusRule_multipleStatuses() throws Exception {
        // Given: Configuration with multiple statuses
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("In Progress", "Pull Request", "Review", "Done"));
        params.put("label", "All Active Statuses");
        params.put("isWeight", false);

        // When
        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        // Then
        assertNotNull(metric);
        assertEquals("All Active Statuses", metric.getName());
        assertFalse(metric.isWeight());
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateBugsCreatorsRule() throws Exception {
        // Given: Configuration for BugsCreatorsRule
        Map<String, Object> params = new HashMap<>();
        params.put("project", "DMC");
        params.put("label", "Bugs Created");
        params.put("isWeight", false);

        // When
        Metric metric = factory.createMetric("BugsCreatorsRule", params, "tracker");

        // Then
        assertNotNull(metric);
        assertEquals("Bugs Created", metric.getName());
        assertFalse(metric.isWeight());
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreatePullRequestsMetricSource() throws Exception {
        // Given: Configuration for PullRequestsMetricSource
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "IstiN");
        params.put("repository", "dmtools");
        params.put("since", "2024-01-01");
        params.put("label", "Pull Requests");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When
        Metric metric = factory.createMetric("PullRequestsMetricSource", params, "pullRequests");

        // Then
        assertNotNull(metric);
        assertEquals("Pull Requests", metric.getName());
        assertFalse(metric.isWeight());
        assertTrue(metric.isPersonalized());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateMetric_unknownDataSourceType_shouldThrowException() {
        // Given: Unknown data source type
        Map<String, Object> params = new HashMap<>();
        params.put("label", "Unknown Metric");

        // When/Then: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> factory.createMetric("SomeMetric", params, "unknownSource")
        );

        assertTrue(exception.getMessage().contains("Unknown data source type"));
    }

    @Test
    void testCreateMetric_unknownTrackerRule_shouldThrowException() {
        // Given: Unknown tracker rule name
        Map<String, Object> params = new HashMap<>();
        params.put("label", "Unknown Rule");

        // When/Then: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> factory.createMetric("UnknownRule", params, "tracker")
        );

        assertTrue(exception.getMessage().contains("Unknown tracker rule"));
    }

    @Test
    void testCreateMetric_statusesAsArray() throws Exception {
        // Given: Statuses as array (from JSON deserialization)
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", new String[]{"Done", "Closed"});
        params.put("label", "Finished");
        params.put("isWeight", true);

        // When
        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        // Then
        assertNotNull(metric);
        assertEquals("Finished", metric.getName());
        assertTrue(metric.isWeight());
    }

    @Test
    void testCreateMetric_defaultValues() throws Exception {
        // Given: Minimal configuration (using defaults)
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));
        // No label - should use metric name
        // No isWeight - should default to false

        // When
        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        // Then
        assertNotNull(metric);
        // Label defaults to metric name
        assertEquals("TicketMovedToStatusRule", metric.getName());
        // isWeight defaults to false
        assertFalse(metric.isWeight());
    }

    @Test
    void testCreateMetric_withLabelOverride() throws Exception {
        // Given: Configuration with custom label
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));
        params.put("label", "Custom Label");
        params.put("isWeight", true);

        // When
        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        // Then
        assertEquals("Custom Label", metric.getName(), "Label should override metric name");
    }

    @Test
    void testCreateMetric_pullRequests_requiresSourceCode() {
        // Given: PullRequestsMetricSource without SourceCode configured
        MetricFactory factoryWithoutSourceCode = new MetricFactory(mockTrackerClient, null);
        Map<String, Object> params = new HashMap<>();
        params.put("label", "PRs");

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> factoryWithoutSourceCode.createMetric("PullRequestsMetricSource", params, "pullRequests")
        );

        assertTrue(exception.getMessage().contains("SourceCode is not configured"));
    }

    @Test
    void testParseDateParam_validDate() throws Exception {
        // This is testing internal behavior - we verify it works via PullRequestsMetricSource
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("since", "2024-01-01");
        params.put("label", "PRs");
        params.put("isWeight", false);
        params.put("isPersonalized", false);

        // When: Create metric with date parameter
        Metric metric = factory.createMetric("PullRequestsMetricSource", params, "pullRequests");

        // Then: Should not throw exception - date was parsed successfully
        assertNotNull(metric);
    }

    @Test
    void testStartDate_fallbackFromSince() throws Exception {
        // Given: "since" is used (backward compat)
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("since", "2024-06-01");
        params.put("label", "PRs");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When
        Metric metric = factory.createMetric("PullRequestsMetricSource", params, "pullRequests");

        // Then
        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testStartDate_paramOverridesSince() throws Exception {
        // Given: Both "startDate" and "since" are present - startDate should win
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("startDate", "2024-01-01");
        params.put("since", "2023-01-01");
        params.put("label", "PRs");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When
        Metric metric = factory.createMetric("PullRequestsMetricSource", params, "pullRequests");

        // Then: Should create metric successfully using startDate
        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testStartDate_fallbackToReportStartDate() throws Exception {
        // Given: No startDate or since in params, but reportStartDate is set
        MetricFactory factoryWithReportDate = new MetricFactory(mockTrackerClient, mockSourceCode, null, "2024-03-01");
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("label", "PRs");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When
        Metric metric = factoryWithReportDate.createMetric("PullRequestsMetricSource", params, "pullRequests");

        // Then: Should use reportStartDate as fallback
        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testStartDate_noDateAtAll() throws Exception {
        // Given: No date params at all
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("label", "PRs");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When: No date available - should still create metric (null date)
        Metric metric = factory.createMetric("PullRequestsMetricSource", params, "pullRequests");

        // Then
        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreatePullRequestsCommentsMetricSource() throws Exception {
        // Given: Configuration for PullRequestsCommentsMetricSource
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "IstiN");
        params.put("repository", "dmtools");
        params.put("since", "2024-01-01");
        params.put("label", "PR Comments");
        params.put("isWeight", false);
        params.put("isPersonalized", true);
        params.put("isPositive", true);

        // When
        Metric metric = factory.createMetric("PullRequestsCommentsMetricSource", params, "pullRequests");

        // Then
        assertNotNull(metric);
        assertEquals("PR Comments", metric.getName());
        assertFalse(metric.isWeight());
        assertTrue(metric.isPersonalized());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreatePullRequestsCommentsMetricSource_defaultIsPositive() throws Exception {
        // Given: No isPositive specified - should default to true
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "IstiN");
        params.put("repository", "dmtools");
        params.put("label", "PR Comments");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When
        Metric metric = factory.createMetric("PullRequestsCommentsMetricSource", params, "pullRequests");

        // Then
        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreatePullRequestsApprovalsMetricSource() throws Exception {
        // Given: Configuration for PullRequestsApprovalsMetricSource
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "IstiN");
        params.put("repository", "dmtools");
        params.put("since", "2024-01-01");
        params.put("label", "PR Approvals");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        // When
        Metric metric = factory.createMetric("PullRequestsApprovalsMetricSource", params, "pullRequests");

        // Then
        assertNotNull(metric);
        assertEquals("PR Approvals", metric.getName());
        assertFalse(metric.isWeight());
        assertTrue(metric.isPersonalized());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateMetric_dataSourceParamsProvideStartDate() throws Exception {
        // Given: startDate comes from dataSource params, not metric params
        Map<String, Object> metricParams = new HashMap<>();
        metricParams.put("label", "PRs");
        metricParams.put("isWeight", false);
        metricParams.put("isPersonalized", true);

        Map<String, Object> dataSourceParams = new HashMap<>();
        dataSourceParams.put("workspace", "test");
        dataSourceParams.put("repository", "repo");
        dataSourceParams.put("startDate", "2024-01-01");

        // When
        Metric metric = factory.createMetric("PullRequestsMetricSource", metricParams, "pullRequests", dataSourceParams);

        // Then: Should use startDate from dataSource params
        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreatePullRequestsMergedByMetricSource() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "IstiN");
        params.put("repository", "dmtools");
        params.put("label", "PRs Merged");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        Metric metric = factory.createMetric("PullRequestsMergedByMetricSource", params, "pullRequests");

        assertNotNull(metric);
        assertEquals("PRs Merged", metric.getName());
        assertTrue(metric.isPersonalized());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreatePullRequestsDeclinedMetricSource() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "IstiN");
        params.put("repository", "dmtools");
        params.put("label", "PRs Declined");
        params.put("isWeight", false);
        params.put("isPersonalized", true);

        Metric metric = factory.createMetric("PullRequestsDeclinedMetricSource", params, "pullRequests");

        assertNotNull(metric);
        assertEquals("PRs Declined", metric.getName());
        assertTrue(metric.isPersonalized());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCsvMetricSource() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "/Test_Csv.csv");
        params.put("whenColumn", "Date");
        params.put("weightColumn", "Total Tokens");
        params.put("defaultWho", "Test Person");
        params.put("label", "Tokens");
        params.put("isWeight", true);
        params.put("isPersonalized", true);

        Metric metric = factory.createMetric("CsvMetricSource", params, "csv");

        assertNotNull(metric);
        assertEquals("Tokens", metric.getName());
        assertTrue(metric.isWeight());
        assertTrue(metric.isPersonalized());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCsvMetricSource_withDataSourceParams() throws Exception {
        Map<String, Object> metricParams = new HashMap<>();
        metricParams.put("weightColumn", "Cost");
        metricParams.put("label", "Cost");
        metricParams.put("isWeight", true);
        metricParams.put("isPersonalized", true);

        Map<String, Object> dataSourceParams = new HashMap<>();
        dataSourceParams.put("filePath", "/Test_Csv.csv");
        dataSourceParams.put("whenColumn", "Date");
        dataSourceParams.put("defaultWho", "User");

        Metric metric = factory.createMetric("CsvMetricSource", metricParams, "csv", dataSourceParams);

        assertNotNull(metric);
        assertEquals("Cost", metric.getName());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCsvMetricSource_missingFilePath() {
        Map<String, Object> params = new HashMap<>();
        params.put("weightColumn", "Value");
        params.put("label", "Test");
        params.put("isWeight", false);
        params.put("isPersonalized", false);

        assertThrows(IllegalArgumentException.class,
            () -> factory.createMetric("CsvMetricSource", params, "csv"));
    }

    @Test
    void testCreateCsvMetricSource_missingWeightColumn() {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "/Test_Csv.csv");
        params.put("label", "Test");
        params.put("isWeight", false);
        params.put("isPersonalized", false);

        assertThrows(IllegalArgumentException.class,
            () -> factory.createMetric("CsvMetricSource", params, "csv"));
    }
}
