// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.Config;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.Sprint;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.CombinedCustomRunnableMetrics;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.report.freemarker.DevProductivityReport;
import com.github.istin.dmtools.report.freemarker.GenericCell;
import com.github.istin.dmtools.report.freemarker.GenericRow;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.report.productivity.ProductivityDataResult;
import com.github.istin.dmtools.team.Employees;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional coverage-oriented tests for {@link ProductivityTools} exercising
 * the tracker search/performer flow, source-collector and combined metrics,
 * release/iteration matching, analytics report building and helper methods.
 */
public class ProductivityToolsCoverageTest {

    private TrackerClient trackerClient;
    private IReleaseGenerator releaseGenerator;
    private Employees employees;
    private boolean previousDemoSite;

    @Before
    public void setUp() {
        trackerClient = mock(TrackerClient.class);
        releaseGenerator = mock(IReleaseGenerator.class);
        employees = mock(Employees.class);
        when(employees.transformName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        // Config.DEMO_SITE is a global flag that other tests may leave enabled; it makes
        // MockedNames.mock() replace developer names with random values, so pin it here.
        previousDemoSite = Config.DEMO_SITE;
        Config.DEMO_SITE = false;
    }

    @After
    public void tearDown() {
        Config.DEMO_SITE = previousDemoSite;
    }

    private static Calendar cal(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static Release releaseWithSprint(int releaseId, int sprintNumber, Date start, Date end) {
        Sprint sprint = new Sprint(sprintNumber, start, end, -1);
        return new Release(releaseId, "Release " + releaseId, Collections.singletonList(sprint));
    }

    private void stubDefaultReleases() {
        Release release1 = releaseWithSprint(1, 1, cal(2024, 1, 1).getTime(), cal(2024, 1, 14).getTime());
        Release release2 = releaseWithSprint(2, 5, cal(2024, 3, 1).getTime(), cal(2024, 3, 14).getTime());
        when(releaseGenerator.getStartDate()).thenReturn(cal(2024, 1, 1));
        when(releaseGenerator.generate()).thenReturn(Arrays.asList(release1, release2));
        Release current = new Release();
        current.setId(5);
        when(releaseGenerator.getCurrentIteration()).thenReturn(current);
    }

    private ITicket ticket(String title, String key) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketTitle()).thenReturn(title);
        when(ticket.getTicketKey()).thenReturn(key);
        return ticket;
    }

    @Test
    public void testBuildReportWithTrackerMetricsAndReleases() throws Exception {
        stubDefaultReleases();
        when(trackerClient.getBasePath()).thenReturn("http://jira");
        when(trackerClient.getDefaultQueryFields()).thenReturn(new String[]{"summary"});

        ITicket realTicket = ticket("Implement feature", "KEY-1");
        ITicket ignoredTicket = ticket("ignore this task", "KEY-2");
        doAnswer(invocation -> {
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0);
            performer.perform(realTicket);
            performer.perform(ignoredTicket);
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));

        // rule-based weight metric: one key time in sprint 1, one in sprint 5, one before the report start date
        TrackerRule<ITicket> storiesRule = (tracker, t) -> Arrays.asList(
                new KeyTime("K1", cal(2024, 1, 5), "dev1"),
                new KeyTime("K2", cal(2024, 3, 5), "dev1"),
                new KeyTime("K3", cal(2023, 12, 15), "dev2")
        );
        Metric storiesMetric = new Metric("Stories", true, storiesRule);

        // rule-based metric returning null productivity items
        Metric emptyMetric = new Metric("Items", false, (TrackerRule<ITicket>) (tracker, t) -> null);

        // source-collector based metric
        SourceCollector sourceCollector = mock(SourceCollector.class);
        when(sourceCollector.performSourceCollection(eq(false), eq("Collected")))
                .thenReturn(Collections.singletonList(new KeyTime("C1", cal(2024, 1, 5), "dev3")));
        Metric collectedMetric = new Metric("Collected", false, sourceCollector);

        // combined runnable metric wrapping another source-collector metric
        SourceCollector subSourceCollector = mock(SourceCollector.class);
        when(subSourceCollector.performSourceCollection(eq(false), eq("Sub")))
                .thenReturn(Collections.singletonList(new KeyTime("S1", cal(2024, 1, 5), "dev4")));
        Metric subMetric = new Metric("Sub", true, subSourceCollector);
        Metric combinedMetric = new CombinedCustomRunnableMetrics("Combined", subMetric);

        List<Metric> metrics = Arrays.asList(storiesMetric, emptyMetric, collectedMetric, combinedMetric);

        DevProductivityReport report = ProductivityTools.buildReport(
                trackerClient, releaseGenerator, "team", "${Stories}", "project=X",
                metrics, Release.Style.BY_SPRINTS, employees, new String[]{"ignore"});

        assertNotNull(report);
        assertEquals("team " + ProductivityTools.REPORT_NAME, report.getName());
        assertEquals("http://jira/issues/?jql=project=X", report.getFilterUrl());
        assertEquals(1, report.getTicketCounter());
        assertTrue(report.getHeaders().contains("Stories"));
        assertTrue(report.getHeaders().contains("Items"));
        assertTrue(report.getHeaders().contains("Collected"));
        assertTrue(report.getHeaders().contains("Combined"));
        assertTrue(report.isBySprints());
        assertFalse(report.isByWeeks());
        // allMetrics chart plus one chart per developer
        assertTrue(report.getListDevCharts().size() >= 3);
        // key time before the report start date must be collected as unmatched (keyed by developer)
        assertNotNull(report.getUnmatchedValues().get("dev2"));
        assertEquals(1, report.getUnmatchedValues().get("dev2").size());
        assertEquals("K3", report.getUnmatchedValues().get("dev2").get(0).getKey());
        // rows are created per developer
        boolean dev1RowFound = false;
        for (GenericRow row : report.getRows()) {
            if ("dev1".equals(row.getCells().get(0).getText())) {
                dev1RowFound = true;
            }
        }
        assertTrue(dev1RowFound);
        verify(trackerClient, times(1)).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));
        verify(employees, times(3)).transformName(anyString());
        verify(sourceCollector, times(1)).performSourceCollection(false, "Collected");
        verify(subSourceCollector, times(1)).performSourceCollection(false, "Sub");
    }

    @Test
    public void testBuildReportWithNonNumericIterationName() throws Exception {
        Sprint sprint = mock(Sprint.class);
        when(sprint.getIterationName()).thenReturn("Iteration-X");
        when(sprint.getId()).thenReturn(1);
        when(sprint.getStartDate()).thenReturn(cal(2024, 1, 1).getTime());
        when(sprint.getEndDate()).thenReturn(cal(2024, 1, 14).getTime());
        Release release = new Release(1, "Release 1", Collections.singletonList(sprint));

        when(releaseGenerator.getStartDate()).thenReturn(cal(2024, 1, 1));
        when(releaseGenerator.generate()).thenReturn(Collections.singletonList(release));
        Release current = new Release();
        current.setId(1);
        when(releaseGenerator.getCurrentIteration()).thenReturn(current);

        SourceCollector sourceCollector = mock(SourceCollector.class);
        when(sourceCollector.performSourceCollection(eq(false), eq("Stories")))
                .thenReturn(Collections.singletonList(new KeyTime("K1", cal(2024, 1, 5), "dev1")));
        Metric metric = new Metric("Stories", true, sourceCollector);

        DevProductivityReport report = ProductivityTools.buildReport(
                null, releaseGenerator, "team", "1", null,
                Collections.singletonList(metric), Release.Style.BY_SPRINTS, null, null);

        assertNotNull(report);
        assertEquals("", report.getFilterUrl());
        DevChart devChart = null;
        for (DevChart chart : report.getListDevCharts()) {
            if ("dev1".equals(chart.getId())) {
                devChart = chart;
            }
        }
        assertNotNull(devChart);
        assertEquals(1, devChart.getReportIterationDataList().size());
        assertEquals("Iteration-X", devChart.getReportIterationDataList().get(0).getIterationName());
    }

    @Test
    public void testBuildReportByWeeksWithNullTrackerAndNullJql() throws Exception {
        Release release = releaseWithSprint(1, 1, cal(2024, 1, 1).getTime(), cal(2024, 1, 14).getTime());
        when(releaseGenerator.getStartDate()).thenReturn(cal(2024, 1, 1));
        when(releaseGenerator.generate()).thenReturn(Collections.singletonList(release));

        DevProductivityReport report = ProductivityTools.buildReport(
                null, releaseGenerator, "team", "1", null,
                new ArrayList<>(), Release.Style.BY_WEEKS, null, null);

        assertNotNull(report);
        assertEquals("", report.getFilterUrl());
        assertFalse(report.isBySprints());
        assertTrue(report.isByWeeks());
        assertEquals(0, report.getTicketCounter());
        assertEquals(1, report.getListDevCharts().size());
    }

    @Test
    public void testGenerateWithHtmlInjection() throws Exception {
        when(releaseGenerator.getStartDate()).thenReturn(Calendar.getInstance());
        when(releaseGenerator.generate()).thenReturn(new ArrayList<>());
        when(trackerClient.getBasePath()).thenReturn("http://jira");

        HtmlInjection htmlInjection = mock(HtmlInjection.class);
        when(htmlInjection.getHtmBeforeTimeline(any(DevProductivityReport.class))).thenReturn("<div>injected</div>");

        File result = ProductivityTools.generate(trackerClient, releaseGenerator, "team", "1", "jql",
                new ArrayList<>(), Release.Style.BY_SPRINTS, employees, new String[]{}, htmlInjection);

        assertNotNull(result);
        verify(htmlInjection, times(1)).getHtmBeforeTimeline(any(DevProductivityReport.class));
    }

    @Test
    public void testGenerateWithoutEmployeesAndInjection() throws Exception {
        when(releaseGenerator.getStartDate()).thenReturn(Calendar.getInstance());
        when(releaseGenerator.generate()).thenReturn(new ArrayList<>());
        when(trackerClient.getBasePath()).thenReturn("http://jira");

        File result = ProductivityTools.generate(trackerClient, releaseGenerator, "team", "1", null,
                new ArrayList<>(), Release.Style.BY_SPRINTS, new String[]{});

        assertNotNull(result);
    }

    @Test
    public void testBuildReportWithAnalytics() throws Exception {
        stubDefaultReleases();
        when(trackerClient.getBasePath()).thenReturn("http://jira");
        when(trackerClient.getDefaultQueryFields()).thenReturn(new String[]{"summary"});
        when(trackerClient.getComments(anyString(), any(ITicket.class))).thenReturn(new ArrayList<>());

        ITicket realTicket = ticket("Implement feature", "KEY-1");
        doAnswer(invocation -> {
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0);
            performer.perform(realTicket);
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));

        Metric metric = new Metric("Stories", true,
                (TrackerRule<ITicket>) (tracker, t) -> Collections.singletonList(new KeyTime("K1", cal(2024, 1, 5), "dev1")));

        Map<String, String> patternNames = new LinkedHashMap<>();
        patternNames.put(".*deploy.*", "Deployments");

        ProductivityDataResult result = ProductivityTools.buildReportWithAnalytics(
                trackerClient, releaseGenerator, "team", "${Stories}", "project=X",
                Collections.singletonList(metric), Release.Style.BY_SPRINTS, employees,
                new String[]{"ignore"}, patternNames, true, "request:(.*)", name -> name);

        assertNotNull(result);
        assertEquals("team", result.getReportName());
        assertEquals("http://jira/issues/?jql=project=X", result.getFilter());
        assertEquals(1, result.getTicketsCount());
        assertNotNull(result.getAnalytics());
        assertEquals("Deployments", result.getAnalytics().getPatternNames().get(".*deploy.*"));
        assertNotNull(result.getProductivityReport());
        assertTrue(result.getProductivityReport().getHeaders().contains("Deployments"));
        // search is performed twice: inside buildReport and in the ticket counting pass
        verify(trackerClient, times(2)).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));
    }

    @Test
    public void testBuildReportWithAnalyticsSkipsPatternsWithoutEmployees() throws Exception {
        when(releaseGenerator.getStartDate()).thenReturn(Calendar.getInstance());
        when(releaseGenerator.generate()).thenReturn(new ArrayList<>());

        Map<String, String> patternNames = new LinkedHashMap<>();
        patternNames.put(".*deploy.*", "Deployments");

        ProductivityDataResult result = ProductivityTools.buildReportWithAnalytics(
                null, releaseGenerator, "team", "1", null,
                new ArrayList<>(), Release.Style.BY_SPRINTS, null,
                null, patternNames, false, null, null);

        assertNotNull(result);
        assertEquals(0, result.getTicketsCount());
        assertEquals("", result.getFilter());
        assertNotNull(result.getAnalytics());
        assertEquals("Deployments", result.getAnalytics().getPatternNames().get(".*deploy.*"));
    }

    @Test
    public void testCheckEmployeesTransformsNames() {
        when(employees.transformName("John")).thenReturn("John Doe");

        KeyTime keyTime = new KeyTime("K1", Calendar.getInstance(), "John");
        ProductivityTools.checkEmployees(employees, Collections.singletonList(keyTime));

        assertEquals("John Doe", keyTime.getWho());
        // null list must be tolerated
        ProductivityTools.checkEmployees(employees, null);
    }

    @Test
    public void testClassIsInstantiable() {
        assertNotNull(new ProductivityTools());
    }

    @Test
    public void testMeasureTimeBothBranches() {
        long now = ProductivityTools.getCurrentTimeForMeasurements();
        assertTrue(now > 0);

        long fast = ProductivityTools.measureTime("fastAction", now);
        assertTrue(fast >= now);

        long slow = ProductivityTools.measureTime("slowAction", now - 1000);
        assertTrue(slow >= now);
    }

    @Test
    public void testFindRowFoundNotFoundAndNullText() {
        List<GenericRow> rows = new ArrayList<>();
        GenericRow nullTextRow = new GenericRow();
        nullTextRow.getCells().add(new GenericCell());
        GenericRow matchingRow = new GenericRow();
        matchingRow.getCells().add(new GenericCell("dev1"));
        rows.add(nullTextRow);
        rows.add(matchingRow);

        assertSame(matchingRow, ProductivityTools.findRow(rows, "dev1"));
        assertNull(ProductivityTools.findRow(rows, "missing"));
        assertNull(ProductivityTools.findRow(new ArrayList<>(), "dev1"));
    }
}
