// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.excel.model.ExcelMetricConfig;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.PullRequestsApprovalsMetric;
import com.github.istin.dmtools.metrics.PullRequestsChangesMetric;
import com.github.istin.dmtools.metrics.PullRequestsCommentsMetric;
import com.github.istin.dmtools.metrics.PullRequestsMetric;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.IReleaseGenerator;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.report.timeinstatus.TimeInStatus;
import com.github.istin.dmtools.team.Employees;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DevProductivityReportCoverageTest {

    private DevProductivityReport report;
    private DevProductivityReportParams params;
    private Employees employees;

    @Before
    public void setUp() {
        report = new DevProductivityReport();
        params = mock(DevProductivityReportParams.class);
        employees = mock(Employees.class);

        when(params.isWeight()).thenReturn(false);
        when(params.getIgnoreTicketPrefixes()).thenReturn(null);
        when(params.getStatusesReadyForTesting()).thenReturn(new String[]{"Ready for Testing"});
        when(params.getStatusesInDevelopment()).thenReturn(new String[]{"In Development"});
        when(params.getStatusesInTesting()).thenReturn(new String[]{"In Testing"});
        when(params.getInitialStatus()).thenReturn("Open");
        when(params.getSources()).thenReturn(new JSONArray());
        when(params.getExcelMetricsParams()).thenReturn(null);
        when(params.getCommentsRegexResponsible()).thenReturn(null);
        when(employees.getUnknownNames()).thenReturn(new HashSet<>());
    }

    @Test
    public void testGetAiReturnsNull() {
        assertNull(report.getAi());
    }

    @Test
    public void testFindNameInCommentNoMatch() {
        assertNull(DevProductivityReport.findNameInComment("Merge request by \\*([\\w\\s-]+)\\*",
                "no author mentioned here"));
    }

    @Test
    public void testGenerateListOfMetricsWithoutSourcesAndExcel() throws Exception {
        List<Metric> metrics = report.generateListOfMetrics(params, employees, Calendar.getInstance());

        assertEquals(7, metrics.size());
        assertEquals("Stories Moved To Testing", metrics.get(0).getName());
        assertEquals("Bugs Moved To Testing", metrics.get(1).getName());
        assertEquals("Stories Moved To Testing FTR", metrics.get(2).getName());
        assertEquals("Bugs Moved To Testing FTR", metrics.get(3).getName());
        assertEquals("Bugfixing Time In Days", metrics.get(4).getName());
        assertEquals("Story Development Time In Days", metrics.get(5).getName());
        assertEquals("Vacation days", metrics.get(6).getName());
    }

    @Test
    public void testGenerateListOfMetricsWithSourcesAndExcel() throws Exception {
        SourceCode sourceCode = mockSourceCode();
        ExcelMetricConfig excelMetricConfig = mock(ExcelMetricConfig.class);
        when(excelMetricConfig.getMetricName()).thenReturn("Excel Metric");
        when(excelMetricConfig.getFileName()).thenReturn("/metric.xlsx");
        when(excelMetricConfig.getWhoColumn()).thenReturn("who");
        when(excelMetricConfig.getWhenColumn()).thenReturn("when");
        when(excelMetricConfig.getWeightColumn()).thenReturn("weight");
        when(excelMetricConfig.getWeightMultiplier()).thenReturn(1.0);
        when(params.getExcelMetricsParams()).thenReturn(Collections.singletonList(excelMetricConfig));

        try (MockedStatic<SourceCode.Impl> sourceCodeStatic = mockStatic(SourceCode.Impl.class)) {
            sourceCodeStatic.when(() -> SourceCode.Impl.getConfiguredSourceCodes(any()))
                    .thenReturn(Collections.singletonList(sourceCode));

            List<Metric> metrics = report.generateListOfMetrics(params, employees, Calendar.getInstance());

            assertEquals(13, metrics.size());
            List<String> names = new ArrayList<>();
            for (Metric metric : metrics) {
                names.add(metric.getName());
            }
            assertTrue(names.contains(PullRequestsMetric.NAME));
            assertTrue(names.contains(PullRequestsChangesMetric.NAME));
            assertTrue(names.contains(PullRequestsCommentsMetric.NAME_POSITIVE));
            assertTrue(names.contains(PullRequestsCommentsMetric.NAME_NEGATIVE));
            assertTrue(names.contains(PullRequestsApprovalsMetric.NAME));
            assertTrue(names.contains("Excel Metric"));
        }
    }

    @Test
    public void testPullRequestMetricsWithEmptySources() throws Exception {
        List<Metric> metrics = new ArrayList<>();
        Calendar startDate = Calendar.getInstance();

        report.pullRequests(params, metrics, employees, startDate);
        report.pullRequestsChanges(params, metrics, employees, startDate);
        report.pullRequestsComments(params, metrics, employees, startDate);
        report.pullRequestsApprovals(params, metrics, employees, startDate);

        assertTrue(metrics.isEmpty());
    }

    @Test
    public void testPullRequestMetricsWithSources() throws Exception {
        SourceCode sourceCode = mockSourceCode();
        try (MockedStatic<SourceCode.Impl> sourceCodeStatic = mockStatic(SourceCode.Impl.class)) {
            sourceCodeStatic.when(() -> SourceCode.Impl.getConfiguredSourceCodes(any()))
                    .thenReturn(Collections.singletonList(sourceCode));

            List<Metric> metrics = new ArrayList<>();
            Calendar startDate = Calendar.getInstance();
            report.pullRequests(params, metrics, employees, startDate);
            report.pullRequestsChanges(params, metrics, employees, startDate);
            report.pullRequestsComments(params, metrics, employees, startDate);
            report.pullRequestsApprovals(params, metrics, employees, startDate);

            assertEquals(5, metrics.size());
            assertEquals(PullRequestsMetric.NAME, metrics.get(0).getName());
            assertEquals(PullRequestsChangesMetric.NAME, metrics.get(1).getName());
            assertEquals(PullRequestsCommentsMetric.NAME_POSITIVE, metrics.get(2).getName());
            assertEquals(PullRequestsCommentsMetric.NAME_NEGATIVE, metrics.get(3).getName());
            assertEquals(PullRequestsApprovalsMetric.NAME, metrics.get(4).getName());
        }
    }

    @Test
    public void testStoriesMovedToTestingRuleIgnoresTask() throws Exception {
        when(params.getIgnoreTicketPrefixes()).thenReturn(new String[]{"Ignore"});
        TrackerRule rule = getRule("Stories Moved To Testing");

        ITicket ticket = mockTicket("PROJ-1", "Story", "Ignore this story");

        assertNull(rule.check(mock(TrackerClient.class), ticket));
    }

    @Test
    public void testStoriesMovedToTestingRuleSkipsNonStory() throws Exception {
        TrackerRule rule = getRule("Stories Moved To Testing");

        ITicket ticket = mockTicket("PROJ-1", "Bug", "Some bug");

        assertNull(rule.check(mock(TrackerClient.class), ticket));
    }

    @Test
    public void testStoriesMovedToTestingRuleForStory() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.STORY_POINTS);
        TrackerRule rule = getRule("Stories Moved To Testing");

        ITicket ticket = mockTicket("PROJ-1", "Story", "Some story");
        Fields fields = ticket.getFields();
        when(fields.getStoryPoints()).thenReturn(3);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(new ArrayList<>(Collections.singletonList(new KeyTime("PROJ-1", Calendar.getInstance()))));
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn("John Doe");

            List<KeyTime> result = rule.check(mock(TrackerClient.class), ticket);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John Doe", result.get(0).getWho());
            assertEquals(3.0, result.get(0).getWeight(), 0.001);
        }
    }

    @Test
    public void testStoriesMovedToTestingRuleEmptyKeyTimes() throws Exception {
        TrackerRule rule = getRule("Stories Moved To Testing");

        ITicket ticket = mockTicket("PROJ-1", "Story", "Some story");

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(new ArrayList<>());

            List<KeyTime> result = rule.check(mock(TrackerClient.class), ticket);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    public void testStoriesMovedToTestingFTRRuleNotFirstTimeRight() throws Exception {
        TrackerRule rule = getRule("Stories Moved To Testing FTR");

        ITicket ticket = mockTicket("PROJ-1", "Story", "Some story");

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.isFirstTimeRight(any(), anyString(), any(), any(), any()))
                    .thenReturn(false);

            assertNull(rule.check(mock(TrackerClient.class), ticket));
        }
    }

    @Test
    public void testStoriesMovedToTestingFTRRuleFirstTimeRight() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.STORY_POINTS);
        TrackerRule rule = getRule("Stories Moved To Testing FTR");

        ITicket ticket = mockTicket("PROJ-1", "Story", "Some story");
        when(ticket.getFields().getStoryPoints()).thenReturn(-1);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.isFirstTimeRight(any(), anyString(), any(), any(), any()))
                    .thenReturn(true);
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(new ArrayList<>(Collections.singletonList(new KeyTime("PROJ-1", Calendar.getInstance()))));
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn("John Doe");

            List<KeyTime> result = rule.check(mock(TrackerClient.class), ticket);

            assertNotNull(result);
            assertEquals(1, result.size());
            // story points -1 falls back to weight 1
            assertEquals(1.0, result.get(0).getWeight(), 0.001);
        }
    }

    @Test
    public void testBugsMovedToTestingRuleSkipsNonBug() throws Exception {
        TrackerRule rule = getRule("Bugs Moved To Testing");

        ITicket ticket = mockTicket("PROJ-1", "Story", "Some story");

        assertNull(rule.check(mock(TrackerClient.class), ticket));
    }

    @Test
    public void testBugsMovedToTestingRuleForBug() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.STORY_POINTS);
        TrackerRule rule = getRule("Bugs Moved To Testing");

        ITicket ticket = mockTicket("PROJ-1", "Bug", "Some bug");

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(new ArrayList<>(Collections.singletonList(new KeyTime("PROJ-1", Calendar.getInstance()))));
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn("Jane Smith");

            List<KeyTime> result = rule.check(mock(TrackerClient.class), ticket);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("Jane Smith", result.get(0).getWho());
            // not a story -> weight falls back to 1
            assertEquals(1.0, result.get(0).getWeight(), 0.001);
        }
    }

    @Test
    public void testBugsMovedToTestingFTRRuleNotFirstTimeRight() throws Exception {
        TrackerRule rule = getRule("Bugs Moved To Testing FTR");

        ITicket ticket = mockTicket("PROJ-1", "Bug", "Some bug");

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.isFirstTimeRight(any(), anyString(), any(), any(), any()))
                    .thenReturn(false);

            assertNull(rule.check(mock(TrackerClient.class), ticket));
        }
    }

    @Test
    public void testFindResponsibleDevDirectlyFromChangelog() throws Exception {
        try (MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn("John Doe");

            String who = report.findResponsibleDev(mock(TrackerClient.class), mockTicket("PROJ-1", "Story", "t"),
                    employees, "by \\*([\\w-]+)\\*", "In Development");

            assertEquals("John Doe", who);
        }
    }

    @Test
    public void testFindResponsibleDevUnknownWithoutRegex() throws Exception {
        try (MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn(Employees.UNKNOWN);

            String who = report.findResponsibleDev(mock(TrackerClient.class), mockTicket("PROJ-1", "Story", "t"),
                    employees, null, "In Development");

            assertEquals(Employees.UNKNOWN, who);
        }
    }

    @Test
    public void testFindResponsibleDevFromComments() throws Exception {
        TrackerClient trackerClient = mock(TrackerClient.class);
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        IComment matchingComment = mock(IComment.class);
        when(matchingComment.getBody()).thenReturn("Moved by *john-doe* please check");
        IComment nonMatchingComment = mock(IComment.class);
        when(nonMatchingComment.getBody()).thenReturn("plain comment");
        when(trackerClient.getComments("PROJ-1", ticket))
                .thenReturn(java.util.Arrays.asList(nonMatchingComment, matchingComment));
        when(employees.contains("john-doe")).thenReturn(true);
        when(employees.transformName("john-doe")).thenReturn("John Doe");

        try (MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn(Employees.UNKNOWN);

            String who = report.findResponsibleDev(trackerClient, ticket, employees,
                    "by \\*([\\w-]+)\\*", "In Development");

            assertEquals("John Doe", who);
        }
    }

    @Test
    public void testFindResponsibleDevFromCommentsNoMatch() throws Exception {
        TrackerClient trackerClient = mock(TrackerClient.class);
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        IComment comment = mock(IComment.class);
        when(comment.getBody()).thenReturn("Moved by *unknown-user*");
        when(trackerClient.getComments("PROJ-1", ticket)).thenReturn(Collections.singletonList(comment));
        when(employees.contains("unknown-user")).thenReturn(false);

        try (MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn(Employees.UNKNOWN);

            String who = report.findResponsibleDev(trackerClient, ticket, employees,
                    "by \\*([\\w-]+)\\*", "In Development");

            assertEquals(Employees.UNKNOWN, who);
        }
    }

    @Test
    public void testCalcWeightTimeSpentNoKeyTimes() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.TIME_SPENT);
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(new Date());

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(new ArrayList<>()))) {
            assertEquals(1.0, report.calcWeight(params, mock(TrackerClient.class), ticket), 0.001);
        }
    }

    @Test
    public void testCalcWeightTimeSpentSubTaskUsesParent() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.TIME_SPENT);
        ITicket ticket = mockTicket("PROJ-1", "Sub-task", "t");
        Ticket parent = mock(Ticket.class);
        when(parent.getCreated()).thenReturn(new Date());
        when(ticket.getFields().getParent()).thenReturn(parent);

        try (MockedConstruction<TimeInStatus> construction = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(new ArrayList<>()))) {
            assertEquals(1.0, report.calcWeight(params, mock(TrackerClient.class), ticket), 0.001);
            verify(construction.constructed().get(0)).check(eq(parent), anyList(), any());
        }
    }

    @Test
    public void testCalcWeightStoryPoints() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.STORY_POINTS);
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getFields().getStoryPoints()).thenReturn(5);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            assertEquals(5.0, report.calcWeight(params, mock(TrackerClient.class), ticket), 0.001);
        }
    }

    @Test
    public void testCalcWeightStoryPointsMissing() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.STORY_POINTS);
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getFields().getStoryPoints()).thenReturn(-1);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            assertEquals(1.0, report.calcWeight(params, mock(TrackerClient.class), ticket), 0.001);
        }
    }

    @Test
    public void testCalcWeightStoryPointsForNonStory() throws Exception {
        when(params.getCalcWeightType()).thenReturn(DevProductivityReportParams.CalcWeightType.STORY_POINTS);
        ITicket ticket = mockTicket("PROJ-1", "Bug", "t");

        assertEquals(1.0, report.calcWeight(params, mock(TrackerClient.class), ticket), 0.001);
    }

    @Test
    public void testCalcWeightDefaultType() throws Exception {
        when(params.getCalcWeightType()).thenReturn(null);

        assertEquals(1.0, report.calcWeight(params, mock(TrackerClient.class),
                mockTicket("PROJ-1", "Story", "t")), 0.001);
    }

    @Test
    public void testTimeSpentOnReturnsNullWithoutItems() throws Exception {
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(new Date());

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(new ArrayList<>()))) {
            assertNull(report.timeSpentOn(params, mock(TrackerClient.class), ticket, "Open", "In Development"));
        }
    }

    @Test
    public void testTimeSpentOnFetchesTicketWhenCreatedIsNull() throws Exception {
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(null);
        ITicket fullTicket = mockTicket("PROJ-1", "Story", "t");
        TrackerClient trackerClient = mock(TrackerClient.class);
        when(trackerClient.performTicket(eq("PROJ-1"), any())).thenReturn(fullTicket);

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(new ArrayList<>()))) {
            assertNull(report.timeSpentOn(params, trackerClient, ticket, "Open", "In Development"));
            verify(trackerClient).performTicket(eq("PROJ-1"), any());
        }
    }

    @Test
    public void testTimeSpentOnComputesKeyTime() throws Exception {
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(new Date());

        Calendar start = Calendar.getInstance();
        start.set(2024, Calendar.JANUARY, 8, 9, 0, 0); // Monday
        Calendar end = Calendar.getInstance();
        end.set(2024, Calendar.JANUARY, 10, 18, 0, 0); // Wednesday
        List<TimeInStatus.Item> items = new ArrayList<>();
        items.add(new TimeInStatus.Item(ticket, "In Development", start, end));
        // item with non-matching status is skipped
        items.add(new TimeInStatus.Item(ticket, "Code Review", start, end));

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(items));
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(Collections.singletonList(new KeyTime("PROJ-1", start, "John Doe")));

            List<KeyTime> result = report.timeSpentOn(params, mock(TrackerClient.class), ticket, "Open", "In Development");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("PROJ-1", result.get(0).getKey());
            assertEquals("John Doe", result.get(0).getWho());
            assertTrue(result.get(0).getWeight() > 0);
        }
    }

    @Test
    public void testTimeSpentOnReturnsNullWhenNoDatesInStatus() throws Exception {
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(new Date());

        Calendar start = Calendar.getInstance();
        start.set(2024, Calendar.JANUARY, 8, 9, 0, 0);
        Calendar end = Calendar.getInstance();
        end.set(2024, Calendar.JANUARY, 9, 18, 0, 0);
        List<TimeInStatus.Item> items = Collections.singletonList(
                new TimeInStatus.Item(ticket, "In Development", start, end));

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(items));
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(new ArrayList<>());

            assertNull(report.timeSpentOn(params, mock(TrackerClient.class), ticket, "Open", "In Development"));
        }
    }

    @Test
    public void testTimeSpentOnNormalizesNegativeDays() throws Exception {
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(new Date());

        // Friday evening -> Saturday morning: 0 days minus 1 weekend day = negative, normalized to 1
        Calendar start = Calendar.getInstance();
        start.set(2024, Calendar.JANUARY, 12, 18, 0, 0);
        Calendar end = Calendar.getInstance();
        end.set(2024, Calendar.JANUARY, 13, 9, 0, 0);
        List<TimeInStatus.Item> items = Collections.singletonList(
                new TimeInStatus.Item(ticket, "In Development", start, end));

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(items));
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(Collections.singletonList(new KeyTime("PROJ-1", start, "John Doe")));

            List<KeyTime> result = report.timeSpentOn(params, mock(TrackerClient.class), ticket, "Open", "In Development");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getWeight() > 0);
        }
    }

    @Test
    public void testTimeSpentOnLogsSuspiciouslyLongDuration() throws Exception {
        ITicket ticket = mockTicket("PROJ-1", "Story", "t");
        when(ticket.getCreated()).thenReturn(new Date());

        Calendar start = Calendar.getInstance();
        start.set(2024, Calendar.JANUARY, 1, 9, 0, 0);
        Calendar end = Calendar.getInstance();
        end.set(2024, Calendar.JANUARY, 25, 18, 0, 0);
        List<TimeInStatus.Item> items = Collections.singletonList(
                new TimeInStatus.Item(ticket, "In Development", start, end));

        try (MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(items));
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class)) {
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(Collections.singletonList(new KeyTime("PROJ-1", start, "John Doe")));

            List<KeyTime> result = report.timeSpentOn(params, mock(TrackerClient.class), ticket, "Open", "In Development");

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    public void testTimeSpentOnStoryDevelopmentRule() throws Exception {
        TrackerRule rule = getRule("Story Development Time In Days");

        ITicket story = mockTicket("PROJ-1", "Story", "Some story");
        when(story.getCreated()).thenReturn(new Date());

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        Calendar start = Calendar.getInstance();
        start.set(2024, Calendar.JANUARY, 8, 9, 0, 0);
        Calendar end = Calendar.getInstance();
        end.set(2024, Calendar.JANUARY, 9, 18, 0, 0);
        List<TimeInStatus.Item> items = Collections.singletonList(
                new TimeInStatus.Item(story, "In Development", start, end));

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ChangelogAssessment> changelogStatic = mockStatic(ChangelogAssessment.class);
             MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                     (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(items))) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            changelogStatic.when(() -> ChangelogAssessment.findDatesWhenTicketWasInStatus(
                            any(), anyBoolean(), any(), anyString(), any(), any(String[].class)))
                    .thenReturn(Collections.singletonList(new KeyTime("PROJ-1", start, "John Doe")));
            changelogStatic.when(() -> ChangelogAssessment.findWhoIsResponsible(any(), any(), any(), any(String[].class)))
                    .thenReturn("John Doe");

            List<KeyTime> result = rule.check(mock(TrackerClient.class), story);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("John Doe", result.get(0).getWho());
        }
    }

    @Test
    public void testTimeSpentOnStoryDevelopmentRuleSkipsNonStory() throws Exception {
        TrackerRule rule = getRule("Story Development Time In Days");

        assertNull(rule.check(mock(TrackerClient.class), mockTicket("PROJ-1", "Bug", "Some bug")));
    }

    @Test
    public void testTimeSpentOnBugfixingRuleForBugWithoutTimeSpent() throws Exception {
        TrackerRule rule = getRule("Bugfixing Time In Days");

        ITicket bug = mockTicket("PROJ-1", "Bug", "Some bug");
        when(bug.getCreated()).thenReturn(new Date());

        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performGettingSubtask("PROJ-1")).thenReturn(new ArrayList<>());

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedConstruction<TimeInStatus> ignored = mockConstruction(TimeInStatus.class,
                     (mock, context) -> when(mock.check(any(), anyList(), any())).thenReturn(new ArrayList<>()))) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            assertNull(rule.check(mock(TrackerClient.class), bug));
        }
    }

    @Test
    public void testTimeSpentOnBugfixingRuleSkipsNonBug() throws Exception {
        TrackerRule rule = getRule("Bugfixing Time In Days");

        assertNull(rule.check(mock(TrackerClient.class), mockTicket("PROJ-1", "Story", "Some story")));
    }

    @Test
    public void testRunJobWeeks() throws Exception {
        File generatedFile = File.createTempFile("devProductivityReport", ".html");
        generatedFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(generatedFile)) {
            writer.write("<html>dev report</html>");
        }

        when(params.getTimePeriodType()).thenReturn(DevProductivityReportParams.TimePeriodType.WEEKS);
        when(params.getStartDate()).thenReturn("01.01.2024");
        when(params.getReportName()).thenReturn("Team");
        when(params.isWeight()).thenReturn(true);
        when(params.getFormula()).thenReturn("formula");
        when(params.getInputJQL()).thenReturn("project = PROJ");
        when(params.getEmployees()).thenReturn(null);
        when(params.getIgnoreTicketPrefixes()).thenReturn(new String[0]);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        List<Metric> capturedMetrics = new ArrayList<>();
        String[] capturedTeam = new String[1];

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ProductivityTools> toolsStatic = mockStatic(ProductivityTools.class);
             MockedStatic<Employees> employeesStatic = mockStatic(Employees.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            employeesStatic.when(Employees::getDevelopers).thenReturn(employees);
            toolsStatic.when(() -> ProductivityTools.generate(
                            any(TrackerClient.class), any(IReleaseGenerator.class), anyString(), anyString(), anyString(),
                            anyList(), any(Release.Style.class), any(String[].class)))
                    .thenAnswer(invocation -> {
                        capturedTeam[0] = invocation.getArgument(2);
                        capturedMetrics.addAll(invocation.getArgument(5));
                        return generatedFile;
                    });

            ResultItem resultItem = report.runJob(params);

            assertEquals("devProductivityReport", resultItem.getKey());
            assertEquals("<html>dev report</html>", resultItem.getResult());
            assertEquals("Team_sp", capturedTeam[0]);
            assertEquals(7, capturedMetrics.size());
        }
    }

    @Test
    public void testRunJobQuartersWithEmployeesFile() throws Exception {
        File generatedFile = File.createTempFile("devProductivityReport", ".html");
        generatedFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(generatedFile)) {
            writer.write("<html>dev report quarters</html>");
        }

        when(params.getTimePeriodType()).thenReturn(DevProductivityReportParams.TimePeriodType.QUARTERS);
        when(params.getStartDate()).thenReturn("01.01.2024");
        when(params.getReportName()).thenReturn("Team");
        when(params.isWeight()).thenReturn(false);
        when(params.getFormula()).thenReturn("formula");
        when(params.getInputJQL()).thenReturn("project = PROJ");
        when(params.getEmployees()).thenReturn("devs.json");
        when(params.getIgnoreTicketPrefixes()).thenReturn(new String[0]);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        String[] capturedTeam = new String[1];

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ProductivityTools> toolsStatic = mockStatic(ProductivityTools.class);
             MockedStatic<Employees> employeesStatic = mockStatic(Employees.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            employeesStatic.when(() -> Employees.getDevelopers("devs.json")).thenReturn(employees);
            toolsStatic.when(() -> ProductivityTools.generate(
                            any(TrackerClient.class), any(IReleaseGenerator.class), anyString(), anyString(), anyString(),
                            anyList(), any(Release.Style.class), any(String[].class)))
                    .thenAnswer(invocation -> {
                        capturedTeam[0] = invocation.getArgument(2);
                        return generatedFile;
                    });

            ResultItem resultItem = report.runJob(params);

            assertEquals("devProductivityReport", resultItem.getKey());
            assertEquals("<html>dev report quarters</html>", resultItem.getResult());
            assertEquals("Team", capturedTeam[0]);
        }
    }

    private TrackerRule getRule(String metricName) throws Exception {
        for (Metric metric : report.generateListOfMetrics(params, employees, Calendar.getInstance())) {
            if (metric.getName().equals(metricName)) {
                return metric.getRule();
            }
        }
        throw new IllegalStateException("Metric not found: " + metricName);
    }

    private SourceCode mockSourceCode() {
        SourceCode sourceCode = mock(SourceCode.class);
        when(sourceCode.getDefaultWorkspace()).thenReturn("workspace");
        when(sourceCode.getDefaultRepository()).thenReturn("repository");
        return sourceCode;
    }

    private ITicket mockTicket(String key, String issueType, String title) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.getKey()).thenReturn(key);
        when(ticket.getIssueType()).thenReturn(issueType);
        when(ticket.getTicketTitle()).thenReturn(title);

        IssueType type = mock(IssueType.class);
        when(type.getName()).thenReturn(issueType);
        Fields fields = mock(Fields.class);
        when(fields.getIssueType()).thenReturn(type);
        when(ticket.getFields()).thenReturn(fields);
        return ticket;
    }
}
