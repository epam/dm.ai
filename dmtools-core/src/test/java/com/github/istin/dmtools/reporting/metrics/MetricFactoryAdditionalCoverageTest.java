// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for MetricFactory targeting branches not covered
 * by MetricFactoryTest (tracker rule variants, figma/csv collectors,
 * divider parsing, date fallback paths).
 */
@ExtendWith(MockitoExtension.class)
class MetricFactoryAdditionalCoverageTest {

    @Mock
    private TrackerClient mockTrackerClient;

    @Mock
    private SourceCode mockSourceCode;

    @Mock
    private FigmaClient mockFigmaClient;

    @Mock
    private IEmployees mockEmployees;

    private MetricFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MetricFactory(mockTrackerClient, mockSourceCode, mockEmployees);
    }

    @Test
    void testConstructorWithEmployees_createsTrackerMetric() throws Exception {
        // Given: Factory created via (trackerClient, sourceCode, employees) constructor
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));

        // When
        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        // Then
        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateCommentsWrittenRule_withRegex() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("commentsRegex", ".*approved.*");
        params.put("label", "Comments");

        Metric metric = factory.createMetric("CommentsWrittenRule", params, "tracker");

        assertNotNull(metric);
        assertEquals("Comments", metric.getName());
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateCommentsWrittenRule_withoutRegex() throws Exception {
        Map<String, Object> params = new HashMap<>();

        Metric metric = factory.createMetric("CommentsWrittenRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsChangesRule_filterFieldsAsList() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", List.of("Story Points", "Priority"));

        Metric metric = factory.createMetric("TicketFieldsChangesRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsChangesRule_filterFieldsAsStringArray() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", new String[]{"Story Points"});

        Metric metric = factory.createMetric("TicketFieldsChangesRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsChangesRule_filterFieldsAsString() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", "Priority");
        params.put("isSimilarity", true);
        params.put("isCollectionIfByCreator", true);
        params.put("includeInitial", true);
        params.put("useDivider", false);

        Metric metric = factory.createMetric("TicketFieldsChangesRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsChangesRule_withCustomName() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", List.of("Priority"));
        params.put("label", "Custom Changes");

        Metric metric = factory.createMetric("TicketFieldsChangesRule", params, "tracker");

        assertNotNull(metric);
        assertEquals("Custom Changes", metric.getName());
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsChangesRule_withCustomNameAndCreatorFilterMode() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", List.of("Priority"));
        params.put("label", "Custom Changes");
        params.put("creatorFilterMode", "author");

        Metric metric = factory.createMetric("TicketFieldsChangesRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsChangesRule_withCreatorFilterModeOnly() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", List.of("Priority"));
        params.put("creatorFilterMode", "author");

        Metric metric = factory.createMetric("TicketFieldsChangesRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsTokensChangedRule_filterFieldsAsList() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", List.of("Description"));
        params.put("mode", "added");
        params.put("creatorFilterMode", "author");
        params.put("isCollectionIfByCreator", true);
        params.put("includeInitial", true);

        Metric metric = factory.createMetric("TicketFieldsTokensChangedRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsTokensChangedRule_filterFieldsAsStringArray() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", new String[]{"Description"});

        Metric metric = factory.createMetric("TicketFieldsTokensChangedRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsTokensChangedRule_filterFieldsAsString() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", "Description");

        Metric metric = factory.createMetric("TicketFieldsTokensChangedRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsTokensRetainedRule_filterFieldsAsList() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", List.of("Description"));
        params.put("includeInitial", true);
        params.put("creatorFilterMode", "author");

        Metric metric = factory.createMetric("TicketFieldsTokensRetainedRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsTokensRetainedRule_filterFieldsAsStringArray() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", new String[]{"Description"});

        Metric metric = factory.createMetric("TicketFieldsTokensRetainedRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketFieldsTokensRetainedRule_filterFieldsAsString() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFields", "Description");

        Metric metric = factory.createMetric("TicketFieldsTokensRetainedRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketMovedToStatusRule_singleStringStatus() throws Exception {
        // Given: statuses as a plain String (neither List nor String[])
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", "Done");

        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketCreatorsRule_withProject() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("project", "DMC");
        params.put("label", "Creators");

        Metric metric = factory.createMetric("TicketCreatorsRule", params, "tracker");

        assertNotNull(metric);
        assertEquals("Creators", metric.getName());
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateTicketCreatorsRule_withoutProject() throws Exception {
        Map<String, Object> params = new HashMap<>();

        Metric metric = factory.createMetric("TicketCreatorsRule", params, "tracker");

        assertNotNull(metric);
        assertNotNull(metric.getRule());
    }

    @Test
    void testCreateMetric_dividerAsString() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));
        params.put("divider", "2.5");

        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        assertNotNull(metric);
        assertEquals(2.5, metric.getDivider(), 0.0001);
    }

    @Test
    void testCreateMetric_dividerAsInvalidString_fallsBackToOne() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));
        params.put("divider", "not-a-number");

        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        assertNotNull(metric);
        assertEquals(1.0, metric.getDivider(), 0.0001);
    }

    @Test
    void testCreateMetric_dividerAsNumber() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", List.of("Done"));
        params.put("divider", 3);

        Metric metric = factory.createMetric("TicketMovedToStatusRule", params, "tracker");

        assertNotNull(metric);
        assertEquals(3.0, metric.getDivider(), 0.0001);
    }

    @Test
    void testCreateLinesOfCodeMetricSource_withBranchNameRegex_commitsBased() throws Exception {
        // Given: branchNameRegex set -> commits-based LOC source
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("branchNameRegex", "feature/.*");
        params.put("commitMessageRegex", ".*");

        Metric metric = factory.createMetric("LinesOfCodeMetricSource", params, "commits");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateLinesOfCodeMetricSource_prDiffBased() throws Exception {
        // Given: no branchNameRegex -> PR diff-based LOC source
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("startDate", "2024-01-01");

        Metric metric = factory.createMetric("LinesOfCodeMetricSource", params, "pullRequests");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCommitsMetricSource() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("branch", "main");
        params.put("startDate", "2024-01-01");
        params.put("branchNameRegex", "feature/.*");
        params.put("commitMessageRegex", "DMC-.*");

        Metric metric = factory.createMetric("CommitsMetricSource", params, "commits");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateMetric_unknownSourceCollector_shouldThrowException() {
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> factory.createMetric("UnknownMetricSource", params, "pullRequests"));

        assertTrue(exception.getMessage().contains("Unknown source collector"));
    }

    @Test
    void testCreateMetric_invalidStartDate_returnsNullCalendar() throws Exception {
        // Given: unparseable date -> parseDateParam logs error and returns null
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", "test");
        params.put("repository", "repo");
        params.put("startDate", "not-a-date");

        Metric metric = factory.createMetric("PullRequestsMetricSource", params, "pullRequests");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateFigmaMetric_clientNotConfigured() {
        Map<String, Object> params = new HashMap<>();
        params.put("files", "abc123");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> factory.createMetric("FigmaCommentsMetricSource", params, "figma"));

        assertTrue(exception.getMessage().contains("Figma client is not configured"));
    }

    @Test
    void testCreateFigmaMetric_missingFiles() {
        MetricFactory figmaFactory = new MetricFactory(mockTrackerClient, mockSourceCode,
                mockFigmaClient, mockEmployees, null);
        Map<String, Object> params = new HashMap<>();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> figmaFactory.createMetric("FigmaCommentsMetricSource", params, "figma"));

        assertTrue(exception.getMessage().contains("requires 'files' parameter"));
    }

    @Test
    void testCreateFigmaMetric_emptyFilesString() {
        MetricFactory figmaFactory = new MetricFactory(mockTrackerClient, mockSourceCode,
                mockFigmaClient, mockEmployees, null);
        Map<String, Object> params = new HashMap<>();
        params.put("files", " , ,");

        assertThrows(IllegalArgumentException.class,
            () -> figmaFactory.createMetric("FigmaCommentsMetricSource", params, "figma"));
    }

    @Test
    void testCreateFigmaCommentsMetricSource_filesAsCommaSeparatedString() throws Exception {
        MetricFactory figmaFactory = new MetricFactory(mockTrackerClient, mockSourceCode,
                mockFigmaClient, mockEmployees, null);
        Map<String, Object> params = new HashMap<>();
        params.put("files", "file1, file2");
        params.put("label", "Figma Comments");

        Metric metric = figmaFactory.createMetric("FigmaCommentsMetricSource", params, "figma");

        assertNotNull(metric);
        assertEquals("Figma Comments", metric.getName());
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateFigmaCommentsMetricSource_filesAsList() throws Exception {
        MetricFactory figmaFactory = new MetricFactory(mockTrackerClient, mockSourceCode,
                mockFigmaClient, mockEmployees, null);
        List<Object> files = new ArrayList<>();
        files.add("file1");
        files.add(null);
        files.add("  ");
        files.add("file2");
        Map<String, Object> params = new HashMap<>();
        params.put("files", files);

        Metric metric = figmaFactory.createMetric("FigmaCommentMetric", params, "figma");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateFigmaCommentsMetricSource_filesAsStringArray() throws Exception {
        MetricFactory figmaFactory = new MetricFactory(mockTrackerClient, mockSourceCode,
                mockFigmaClient, mockEmployees, null);
        Map<String, Object> params = new HashMap<>();
        params.put("files", new String[]{"file1"});

        Metric metric = figmaFactory.createMetric("FigmaCommentsMetricSource", params, "figma");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateFigmaMetric_unknownCollector() {
        MetricFactory figmaFactory = new MetricFactory(mockTrackerClient, mockSourceCode,
                mockFigmaClient, mockEmployees, null);
        Map<String, Object> params = new HashMap<>();
        params.put("files", "file1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> figmaFactory.createMetric("UnknownFigmaSource", params, "figma"));

        assertTrue(exception.getMessage().contains("Unknown figma source collector"));
    }

    @Test
    void testCreateCsvMetricSource_filterValuesAsList() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "/Test_Csv.csv");
        params.put("weightColumn", "Value");
        params.put("filterColumn", "Type");
        List<Object> filterValues = new ArrayList<>();
        filterValues.add("Bug");
        filterValues.add(null);
        filterValues.add("  ");
        filterValues.add("Task");
        params.put("filterValues", filterValues);

        Metric metric = factory.createMetric("CsvMetricSource", params, "csv");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCsvMetricSource_filterValuesAsString() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "/Test_Csv.csv");
        params.put("weightColumn", "Value");
        params.put("filterColumn", "Type");
        params.put("filterValues", "Bug");

        Metric metric = factory.createMetric("CsvMetricSource", params, "csv");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCsvMetricSource_singleFilterValue() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "/Test_Csv.csv");
        params.put("weightColumn", "Value");
        params.put("filterColumn", "Type");
        params.put("filterValue", "Task");

        Metric metric = factory.createMetric("CsvMetricSource", params, "csv");

        assertNotNull(metric);
        assertNotNull(metric.getSourceCollector());
    }

    @Test
    void testCreateCsvMetricSource_groupByField() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", "/Test_Csv.csv");
        params.put("weightColumn", "Value");
        params.put("groupByField", "Type");
        params.put("weightMultiplier", "2.0");

        Metric metric = factory.createMetric("CsvMetricSource", params, "csv");

        assertNotNull(metric);
        assertTrue(metric.isGroupBy());
    }

    @Test
    void testCreateJsonlMetricSource_groupByFieldAndFilters() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("folderPath", "/path/to/jsonl");
        params.put("weightField", "count");
        params.put("groupByField", "repo");
        params.put("filterField", "type");
        params.put("filterValue", "commit");
        params.put("arrayField", "items");
        params.put("arrayFilterField", "kind");
        params.put("arrayFilterValue", "x");
        params.put("dateFormat", "yyyy-MM-dd");
        params.put("weightMultiplier", "1.5");

        Metric metric = factory.createMetric("JsonlMetricSource", params, "jsonl");

        assertNotNull(metric);
        assertTrue(metric.isGroupBy());
        assertNotNull(metric.getSourceCollector());
    }
}
