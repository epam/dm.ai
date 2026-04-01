// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.reporting.model.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReportGeneratorJob
 * Tests JSON deserialization and basic report generation workflow
 */
class ReportGeneratorJobTest {

    private Gson gson;
    private ReportGeneratorJob job;

    @BeforeEach
    void setUp() {
        gson = new Gson();
        job = new ReportGeneratorJob();
    }

    @Test
    void testParamsDeserialization_shouldDeserializeAllFields() {
        // Given: JSON configuration matching the actual config file structure
        String json = """
            {
                "reportName": "Test Report",
                "startDate": "2024-01-01",
                "endDate": "2024-12-31",
                "dataSources": [
                    {
                        "name": "tracker",
                        "params": {
                            "jql": "project = DMC"
                        },
                        "metrics": [
                            {
                                "name": "TicketMovedToStatusRule",
                                "params": {
                                    "statuses": ["Done"],
                                    "label": "Completed",
                                    "isWeight": true
                                }
                            }
                        ]
                    }
                ],
                "timeGrouping": {
                    "type": "static",
                    "periods": [
                        {
                            "name": "Q1 2024",
                            "start": "2024-01-01",
                            "end": "2024-03-31"
                        }
                    ]
                },
                "aggregation": {
                    "formula": "${Completed}"
                },
                "output": {
                    "mode": "combined",
                    "saveRawMetadata": true
                }
            }
            """;

        // When: Deserialize JSON to Params
        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        // Then: All fields should be properly deserialized
        assertNotNull(params, "Params should not be null");
        assertEquals("Test Report", params.getReportName(), "Report name should match");
        assertEquals("2024-01-01", params.getStartDate(), "Start date should match");
        assertEquals("2024-12-31", params.getEndDate(), "End date should match");

        // Verify data sources
        assertNotNull(params.getDataSources(), "Data sources should not be null");
        assertEquals(1, params.getDataSources().size(), "Should have 1 data source");

        DataSourceConfig dataSource = params.getDataSources().get(0);
        assertEquals("tracker", dataSource.getName(), "Data source name should be 'tracker'");
        assertNotNull(dataSource.getParams(), "Data source params should not be null");
        assertEquals("project = DMC", dataSource.getParams().get("jql"), "JQL should match");

        // Verify metrics
        assertNotNull(dataSource.getMetrics(), "Metrics should not be null");
        assertEquals(1, dataSource.getMetrics().size(), "Should have 1 metric");

        MetricConfig metric = dataSource.getMetrics().get(0);
        assertEquals("TicketMovedToStatusRule", metric.getName(), "Metric name should match");
        assertEquals("Completed", metric.getParams().get("label"), "Metric label should match");
        assertEquals(true, metric.getParams().get("isWeight"), "isWeight should be true");

        // Verify time grouping (backward-compatible single getter)
        assertNotNull(params.getTimeGrouping(), "Time grouping should not be null");
        assertEquals("static", params.getTimeGrouping().getType(), "Time grouping type should be 'static'");
        assertNotNull(params.getTimeGrouping().getPeriods(), "Periods should not be null");
        assertEquals(1, params.getTimeGrouping().getPeriods().size(), "Should have 1 period");

        TimePeriod period = params.getTimeGrouping().getPeriods().get(0);
        assertEquals("Q1 2024", period.getName(), "Period name should match");
        assertEquals("2024-01-01", period.getStart(), "Period start should match");
        assertEquals("2024-03-31", period.getEnd(), "Period end should match");

        // Verify list getter also works
        List<TimeGroupingConfig> groupings = params.getTimeGroupings();
        assertNotNull(groupings);
        assertEquals(1, groupings.size());

        // Verify aggregation
        assertNotNull(params.getAggregation(), "Aggregation should not be null");
        assertEquals("${Completed}", params.getAggregation().getFormula(), "Formula should match");

        // Verify output config
        assertNotNull(params.getOutput(), "Output config should not be null");
        assertEquals("combined", params.getOutput().getMode(), "Output mode should be 'combined'");
        assertTrue(params.getOutput().isSaveRawMetadata(), "saveRawMetadata should be true");
    }

    @Test
    void testParamsDeserialization_withMultipleDataSources() {
        // Given: JSON with multiple data sources
        String json = """
            {
                "reportName": "Multi-Source Report",
                "startDate": "2024-01-01",
                "endDate": "2024-12-31",
                "dataSources": [
                    {
                        "name": "tracker",
                        "params": {"jql": "project = DMC"},
                        "metrics": [
                            {"name": "BugsCreatorsRule", "params": {"label": "Bugs"}}
                        ]
                    },
                    {
                        "name": "pullRequests",
                        "params": {"repository": "IstiN/dmtools"},
                        "metrics": [
                            {"name": "PullRequestsMetricSource", "params": {"label": "PRs"}}
                        ]
                    }
                ],
                "timeGrouping": {
                    "type": "static",
                    "periods": [
                        {"name": "Q1", "start": "2024-01-01", "end": "2024-03-31"}
                    ]
                },
                "aggregation": {"formula": "0"},
                "output": {"mode": "combined", "saveRawMetadata": false}
            }
            """;

        // When
        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        // Then
        assertEquals(2, params.getDataSources().size(), "Should have 2 data sources");
        assertEquals("tracker", params.getDataSources().get(0).getName());
        assertEquals("pullRequests", params.getDataSources().get(1).getName());
        assertEquals("IstiN/dmtools", params.getDataSources().get(1).getParams().get("repository"));
    }

    @Test
    void testParamsDeserialization_withMultipleMetricsPerSource() {
        // Given: JSON with multiple metrics per data source
        String json = """
            {
                "reportName": "Multi-Metric Report",
                "startDate": "2024-01-01",
                "endDate": "2024-12-31",
                "dataSources": [
                    {
                        "name": "tracker",
                        "params": {"jql": "project = DMC"},
                        "metrics": [
                            {
                                "name": "TicketMovedToStatusRule",
                                "params": {
                                    "statuses": ["In Progress"],
                                    "label": "Started",
                                    "isWeight": true
                                }
                            },
                            {
                                "name": "TicketMovedToStatusRule",
                                "params": {
                                    "statuses": ["Done"],
                                    "label": "Completed",
                                    "isWeight": true
                                }
                            }
                        ]
                    }
                ],
                "timeGrouping": {
                    "type": "static",
                    "periods": [
                        {"name": "Q1", "start": "2024-01-01", "end": "2024-03-31"}
                    ]
                },
                "aggregation": {"formula": "${Started} + ${Completed}"},
                "output": {"mode": "combined", "saveRawMetadata": true}
            }
            """;

        // When
        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        // Then
        List<MetricConfig> metrics = params.getDataSources().get(0).getMetrics();
        assertEquals(2, metrics.size(), "Should have 2 metrics");

        assertEquals("Started", metrics.get(0).getParams().get("label"));
        assertEquals("Completed", metrics.get(1).getParams().get("label"));

        // Verify statuses array deserialization
        Object statuses0 = metrics.get(0).getParams().get("statuses");
        assertNotNull(statuses0, "Statuses should not be null");
        assertTrue(statuses0 instanceof List, "Statuses should be a List");

        @SuppressWarnings("unchecked")
        List<String> statusList = (List<String>) statuses0;
        assertEquals(1, statusList.size());
        assertEquals("In Progress", statusList.get(0));
    }

    @Test
    void testParamsDeserialization_nullSafety() {
        // Given: Minimal JSON configuration
        String json = """
            {
                "reportName": "Minimal Report",
                "startDate": "2024-01-01",
                "endDate": "2024-12-31",
                "dataSources": [],
                "timeGrouping": {
                    "type": "static",
                    "periods": [
                        {"name": "Q1", "start": "2024-01-01", "end": "2024-03-31"}
                    ]
                }
            }
            """;

        // When
        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        // Then: Should handle null/missing fields gracefully
        assertNotNull(params);
        assertEquals("Minimal Report", params.getReportName());
        assertNotNull(params.getDataSources());
        assertEquals(0, params.getDataSources().size());
        assertNull(params.getAggregation(), "Aggregation can be null");
        assertNull(params.getOutput(), "Output can be null");
    }

    @Test
    void testReportOutput_structure() {
        // Given: Create a ReportOutput manually
        ReportOutput output = new ReportOutput(
            "Test Report",
            "2024-02-07T10:00:00",
            "2024-01-01",
            "2024-12-31",
            List.of(
                new TimePeriodResult(
                    "Q1 2024",
                    "2024-01-01",
                    "2024-03-31",
                    java.util.Collections.emptyMap(),
                    0.0,
                    List.of()
                )
            ),
            new AggregatedResult(
                java.util.Collections.emptyMap(),
                new ContributorMetrics()
            )
        );

        // Then: Verify structure
        assertEquals("Test Report", output.getReportName());
        assertEquals("2024-02-07T10:00:00", output.getGeneratedAt());
        assertEquals(1, output.getTimePeriods().size());
        assertNotNull(output.getAggregated());
    }

    @Test
    void testMetricSummary_aggregation() {
        // Given: Create MetricSummary
        MetricSummary summary = new MetricSummary(5, 13.5, List.of("John Doe", "Jane Smith"));

        // Then
        assertEquals(5, summary.getCount());
        assertEquals(13.5, summary.getTotalWeight(), 0.001);
        assertEquals(2, summary.getContributors().size());
        assertTrue(summary.getContributors().contains("John Doe"));
    }

    @Test
    void testTimePeriod_dateRange() {
        // Given
        TimePeriod period = new TimePeriod("Q1 2024", "2024-01-01", "2024-03-31");

        // Then
        assertEquals("Q1 2024", period.getName());
        assertEquals("2024-01-01", period.getStart());
        assertEquals("2024-03-31", period.getEnd());
    }

    // --- New tests for multi-grouping Params ---

    @Test
    void testParamsDeserialization_arrayTimeGrouping() {
        String json = """
            {
                "reportName": "Multi-Grouping",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31",
                "dataSources": [],
                "timeGrouping": [
                    {"type": "bi-weekly"},
                    {"type": "monthly"},
                    {"type": "weekly", "dayShift": 2},
                    {"type": "quarterly"}
                ],
                "output": {
                    "mode": "combined",
                    "saveRawMetadata": true,
                    "outputPath": "agents/reports/output",
                    "visualizer": "default"
                }
            }
            """;

        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        assertNotNull(params);
        List<TimeGroupingConfig> groupings = params.getTimeGroupings();
        assertEquals(4, groupings.size());
        assertEquals("bi-weekly", groupings.get(0).getType());
        assertEquals("monthly", groupings.get(1).getType());
        assertEquals("weekly", groupings.get(2).getType());
        assertEquals(2, groupings.get(2).getDayShift());
        assertEquals("quarterly", groupings.get(3).getType());

        // Backward-compatible getter returns first
        assertEquals("bi-weekly", params.getTimeGrouping().getType());

        // Output with visualizer
        assertNotNull(params.getOutput());
        assertEquals("default", params.getOutput().getVisualizer());
    }

    @Test
    void testParamsDeserialization_singleObjectTimeGrouping_backwardCompatible() {
        String json = """
            {
                "reportName": "Single",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31",
                "dataSources": [],
                "timeGrouping": {"type": "bi-weekly"}
            }
            """;

        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        List<TimeGroupingConfig> groupings = params.getTimeGroupings();
        assertEquals(1, groupings.size());
        assertEquals("bi-weekly", groupings.get(0).getType());
        assertEquals("bi-weekly", params.getTimeGrouping().getType());
    }

    @Test
    void testParamsDeserialization_nullTimeGrouping() {
        String json = """
            {
                "reportName": "No Grouping",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31",
                "dataSources": []
            }
            """;

        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        List<TimeGroupingConfig> groupings = params.getTimeGroupings();
        assertNotNull(groupings);
        assertTrue(groupings.isEmpty());
        assertNull(params.getTimeGrouping());
    }

    @Test
    void testParamsDeserialization_visualizerNone() {
        String json = """
            {
                "reportName": "No Viz",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31",
                "dataSources": [],
                "timeGrouping": {"type": "monthly"},
                "output": {
                    "mode": "combined",
                    "visualizer": "none"
                }
            }
            """;

        ReportGeneratorJob.Params params = gson.fromJson(json, ReportGeneratorJob.Params.class);

        assertNotNull(params.getOutput());
        assertEquals("none", params.getOutput().getVisualizer());
    }
}
