// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ReportConfig JSON deserialization
 */
class ReportConfigTest {

    @Test
    void testDeserializeSimpleConfig() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"Test Report\",\n" +
            "  \"startDate\": \"2024-01-01\",\n" +
            "  \"endDate\": \"2024-01-31\",\n" +
            "  \"dataSources\": [\n" +
            "    {\n" +
            "      \"name\": \"tracker\",\n" +
            "      \"params\": {\n" +
            "        \"jql\": \"project=TEST\"\n" +
            "      },\n" +
            "      \"metrics\": [\n" +
            "        {\n" +
            "          \"name\": \"BugsCreatorsRule\",\n" +
            "          \"params\": {\n" +
            "            \"project\": \"TEST\",\n" +
            "            \"employees\": \"team_test\",\n" +
            "            \"label\": \"Bugs Created\",\n" +
            "            \"isWeight\": false\n" +
            "          }\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"timeGrouping\": {\n" +
            "    \"type\": \"weeks\"\n" +
            "  },\n" +
            "  \"aggregation\": {\n" +
            "    \"formula\": \"${Bugs Created}\"\n" +
            "  },\n" +
            "  \"output\": {\n" +
            "    \"mode\": \"combined\",\n" +
            "    \"saveRawMetadata\": true\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals("Test Report", config.getReportName());
        assertEquals("2024-01-01", config.getStartDate());
        assertEquals("2024-01-31", config.getEndDate());

        assertNotNull(config.getDataSources());
        assertEquals(1, config.getDataSources().size());

        DataSourceConfig dataSource = config.getDataSources().get(0);
        assertEquals("tracker", dataSource.getName());
        assertEquals("project=TEST", dataSource.getParams().get("jql"));

        assertNotNull(dataSource.getMetrics());
        assertEquals(1, dataSource.getMetrics().size());

        MetricConfig metric = dataSource.getMetrics().get(0);
        assertEquals("BugsCreatorsRule", metric.getName());
        assertEquals("Bugs Created", metric.getParams().get("label"));
        assertEquals(false, metric.getParams().get("isWeight"));

        // Backward-compatible single getter
        assertNotNull(config.getTimeGrouping());
        assertEquals("weeks", config.getTimeGrouping().getType());

        // New list getter - single object becomes list of 1
        assertNotNull(config.getTimeGroupings());
        assertEquals(1, config.getTimeGroupings().size());
        assertEquals("weeks", config.getTimeGroupings().get(0).getType());
        assertFalse(config.isMultiGrouping());

        assertNotNull(config.getAggregation());
        assertEquals("${Bugs Created}", config.getAggregation().getFormula());

        assertNotNull(config.getOutput());
        assertEquals("combined", config.getOutput().getMode());
        assertTrue(config.getOutput().isSaveRawMetadata());
    }

    @Test
    void testDeserializeStaticTimeGrouping() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"Static Periods\",\n" +
            "  \"startDate\": \"2024-01-01\",\n" +
            "  \"endDate\": \"2024-01-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": {\n" +
            "    \"type\": \"static\",\n" +
            "    \"periods\": [\n" +
            "      {\n" +
            "        \"name\": \"Week 1\",\n" +
            "        \"start\": \"2024-01-01\",\n" +
            "        \"end\": \"2024-01-07\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"name\": \"Week 2\",\n" +
            "        \"start\": \"2024-01-08\",\n" +
            "        \"end\": \"2024-01-14\"\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"output\": {\n" +
            "    \"mode\": \"combined\",\n" +
            "    \"saveRawMetadata\": false\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals("static", config.getTimeGrouping().getType());
        assertNotNull(config.getTimeGrouping().getPeriods());
        assertEquals(2, config.getTimeGrouping().getPeriods().size());

        TimePeriod period1 = config.getTimeGrouping().getPeriods().get(0);
        assertEquals("Week 1", period1.getName());
        assertEquals("2024-01-01", period1.getStart());
        assertEquals("2024-01-07", period1.getEnd());
    }

    @Test
    void testDeserializeJavaScriptMetric() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"JS Metrics\",\n" +
            "  \"startDate\": \"2024-01-01\",\n" +
            "  \"endDate\": \"2024-01-31\",\n" +
            "  \"dataSources\": [\n" +
            "    {\n" +
            "      \"name\": \"tracker\",\n" +
            "      \"params\": {\n" +
            "        \"jql\": \"project=TEST\"\n" +
            "      },\n" +
            "      \"metrics\": [\n" +
            "        {\n" +
            "          \"name\": \"js\",\n" +
            "          \"params\": {\n" +
            "            \"path\": \"agents/metrics/complexity.js\",\n" +
            "            \"label\": \"Complexity Score\",\n" +
            "            \"isWeight\": true\n" +
            "          }\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"timeGrouping\": {\n" +
            "    \"type\": \"months\"\n" +
            "  },\n" +
            "  \"output\": {\n" +
            "    \"mode\": \"combined\",\n" +
            "    \"saveRawMetadata\": true\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals(1, config.getDataSources().size());

        MetricConfig metric = config.getDataSources().get(0).getMetrics().get(0);
        assertEquals("js", metric.getName());
        assertEquals("agents/metrics/complexity.js", metric.getParams().get("path"));
        assertEquals("Complexity Score", metric.getParams().get("label"));
        assertEquals(true, metric.getParams().get("isWeight"));
    }

    @Test
    void testDeserializeMultipleDataSources() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"Multi-Source\",\n" +
            "  \"startDate\": \"2024-01-01\",\n" +
            "  \"endDate\": \"2024-01-31\",\n" +
            "  \"dataSources\": [\n" +
            "    {\n" +
            "      \"name\": \"tracker\",\n" +
            "      \"params\": {\n" +
            "        \"jql\": \"project=TEST\"\n" +
            "      },\n" +
            "      \"metrics\": []\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"pullRequests\",\n" +
            "      \"params\": {\n" +
            "        \"repository\": \"org/repo\",\n" +
            "        \"since\": \"2024-01-01\"\n" +
            "      },\n" +
            "      \"metrics\": []\n" +
            "    }\n" +
            "  ],\n" +
            "  \"timeGrouping\": {\n" +
            "    \"type\": \"weeks\"\n" +
            "  },\n" +
            "  \"output\": {\n" +
            "    \"mode\": \"separate\",\n" +
            "    \"saveRawMetadata\": true\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals(2, config.getDataSources().size());

        assertEquals("tracker", config.getDataSources().get(0).getName());
        assertEquals("pullRequests", config.getDataSources().get(1).getName());
        assertEquals("org/repo", config.getDataSources().get(1).getParams().get("repository"));

        assertEquals("separate", config.getOutput().getMode());
    }

    @Test
    void testDeserializeArrayTimeGrouping() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"Multi-Grouping Report\",\n" +
            "  \"startDate\": \"2025-01-01\",\n" +
            "  \"endDate\": \"2025-12-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": [\n" +
            "    {\"type\": \"bi-weekly\"},\n" +
            "    {\"type\": \"monthly\"},\n" +
            "    {\"type\": \"weekly\", \"dayShift\": 2},\n" +
            "    {\"type\": \"quarterly\"}\n" +
            "  ],\n" +
            "  \"output\": {\n" +
            "    \"mode\": \"combined\",\n" +
            "    \"saveRawMetadata\": true,\n" +
            "    \"outputPath\": \"agents/reports/output\",\n" +
            "    \"visualizer\": \"default\"\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals("Multi-Grouping Report", config.getReportName());

        // Array should produce a list of 4
        List<TimeGroupingConfig> groupings = config.getTimeGroupings();
        assertNotNull(groupings);
        assertEquals(4, groupings.size());
        assertTrue(config.isMultiGrouping());

        assertEquals("bi-weekly", groupings.get(0).getType());
        assertEquals("monthly", groupings.get(1).getType());
        assertEquals("weekly", groupings.get(2).getType());
        assertEquals(2, groupings.get(2).getDayShift());
        assertEquals("quarterly", groupings.get(3).getType());

        // Backward-compatible getter returns first
        assertEquals("bi-weekly", config.getTimeGrouping().getType());

        // Output config with visualizer
        assertNotNull(config.getOutput());
        assertEquals("default", config.getOutput().getVisualizer());
        assertEquals("agents/reports/output", config.getOutput().getOutputPath());
    }

    @Test
    void testDeserializeSingleObjectTimeGrouping_backwardCompatible() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"Single Grouping\",\n" +
            "  \"startDate\": \"2025-01-01\",\n" +
            "  \"endDate\": \"2025-12-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": {\n" +
            "    \"type\": \"bi-weekly\"\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertFalse(config.isMultiGrouping());
        assertEquals(1, config.getTimeGroupings().size());
        assertEquals("bi-weekly", config.getTimeGrouping().getType());
        assertEquals(0, config.getTimeGrouping().getDayShift());
    }

    @Test
    void testDeserializeTimeGroupingWithDayShift() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"DayShift Test\",\n" +
            "  \"startDate\": \"2025-01-01\",\n" +
            "  \"endDate\": \"2025-12-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": {\n" +
            "    \"type\": \"weekly\",\n" +
            "    \"dayShift\": 3\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals("weekly", config.getTimeGrouping().getType());
        assertEquals(3, config.getTimeGrouping().getDayShift());
    }

    @Test
    void testDeserializeVisualizerNone() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"No Viz\",\n" +
            "  \"startDate\": \"2025-01-01\",\n" +
            "  \"endDate\": \"2025-12-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": {\"type\": \"monthly\"},\n" +
            "  \"output\": {\n" +
            "    \"mode\": \"combined\",\n" +
            "    \"visualizer\": \"none\"\n" +
            "  }\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertEquals("none", config.getOutput().getVisualizer());
    }

    @Test
    void testSetTimeGrouping_backwardCompatibleSetter() {
        ReportConfig config = new ReportConfig();

        TimeGroupingConfig grouping = new TimeGroupingConfig();
        grouping.setType("monthly");

        config.setTimeGrouping(grouping);

        assertEquals(1, config.getTimeGroupings().size());
        assertEquals("monthly", config.getTimeGrouping().getType());
        assertFalse(config.isMultiGrouping());
    }

    @Test
    void testSetTimeGroupings_multipleGroupings() {
        ReportConfig config = new ReportConfig();

        TimeGroupingConfig g1 = new TimeGroupingConfig();
        g1.setType("weekly");
        TimeGroupingConfig g2 = new TimeGroupingConfig();
        g2.setType("monthly");

        config.setTimeGroupings(Arrays.asList(g1, g2));

        assertEquals(2, config.getTimeGroupings().size());
        assertTrue(config.isMultiGrouping());
        assertEquals("weekly", config.getTimeGrouping().getType());
    }

    @Test
    void testDeserializeCustomCharts() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"Custom Charts Test\",\n" +
            "  \"startDate\": \"2025-01-01\",\n" +
            "  \"endDate\": \"2025-12-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": {\"type\": \"monthly\"},\n" +
            "  \"customCharts\": [\n" +
            "    {\n" +
            "      \"title\": \"Questions Quality\",\n" +
            "      \"type\": \"ratio\",\n" +
            "      \"metrics\": [\"Questions Created\", \"Irrelevant Questions\"]\n" +
            "    },\n" +
            "    {\n" +
            "      \"title\": \"Dev vs QA Output\",\n" +
            "      \"type\": \"comparison\",\n" +
            "      \"metrics\": [\"Dev Completed\", \"Test Cases\", \"Bugs Created\"]\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertNotNull(config.getCustomCharts());
        assertEquals(2, config.getCustomCharts().size());

        CustomChartConfig chart1 = config.getCustomCharts().get(0);
        assertEquals("Questions Quality", chart1.getTitle());
        assertEquals("ratio", chart1.getType());
        assertEquals(2, chart1.getMetrics().size());
        assertEquals("Questions Created", chart1.getMetrics().get(0));
        assertEquals("Irrelevant Questions", chart1.getMetrics().get(1));

        CustomChartConfig chart2 = config.getCustomCharts().get(1);
        assertEquals("Dev vs QA Output", chart2.getTitle());
        assertEquals("comparison", chart2.getType());
        assertEquals(3, chart2.getMetrics().size());
    }

    @Test
    void testDeserializeWithoutCustomCharts_backwardCompat() throws Exception {
        String json = "{\n" +
            "  \"reportName\": \"No Custom Charts\",\n" +
            "  \"startDate\": \"2025-01-01\",\n" +
            "  \"endDate\": \"2025-12-31\",\n" +
            "  \"dataSources\": [],\n" +
            "  \"timeGrouping\": {\"type\": \"monthly\"}\n" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        ReportConfig config = mapper.readValue(json, ReportConfig.class);

        assertNotNull(config);
        assertNull(config.getCustomCharts());
    }

    @Test
    void testCustomChartConfig_programmatic() {
        CustomChartConfig chartConfig = new CustomChartConfig();
        chartConfig.setTitle("Test Chart");
        chartConfig.setType("ratio");
        chartConfig.setMetrics(Arrays.asList("Metric A", "Metric B"));

        assertEquals("Test Chart", chartConfig.getTitle());
        assertEquals("ratio", chartConfig.getType());
        assertEquals(2, chartConfig.getMetrics().size());
    }

    @Test
    void testCustomChartConfig_constructor() {
        CustomChartConfig chartConfig = new CustomChartConfig("My Chart", "comparison", Arrays.asList("M1", "M2", "M3"));

        assertEquals("My Chart", chartConfig.getTitle());
        assertEquals("comparison", chartConfig.getType());
        assertEquals(3, chartConfig.getMetrics().size());
    }
}
