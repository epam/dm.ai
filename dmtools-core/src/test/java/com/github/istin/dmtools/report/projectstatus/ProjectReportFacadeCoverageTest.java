// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.model.TableType;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage tests for {@link ProjectReportFacade}. The facade builds its
 * collaborators (TicketDataFetcher, TicketSorter, TableFactory) internally, so
 * the tests drive it end-to-end with a mocked {@link TrackerClient}: the
 * search performer is fed mocked tickets and the changelog stub marks them as
 * completed ("Done") after the report start date. The real table generators
 * then render the reports from those tickets.
 */
public class ProjectReportFacadeCoverageTest {

    private TrackerClient trackerClient;
    private ProjectReportFacade facade;
    private Calendar startDate;
    private Calendar closedDate;

    @Before
    public void setUp() throws Exception {
        trackerClient = mock(TrackerClient.class);
        lenient().when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"key", "status"});
        lenient().when(trackerClient.getDefaultStatusField()).thenReturn("status");

        ReportConfiguration config = ReportConfiguration.builder()
                .completedStatuses(new String[]{"Done"})
                .rolePrefixes(new String[]{"QA", "BA"})
                .priorityOrder(Arrays.asList("Blocker", "Major", "Minor", "Trivial"))
                .issueTypeOrder(Arrays.asList("Bug", "Story", "Task"))
                .roleDescriptions(new HashMap<>())
                .build();

        facade = new ProjectReportFacade(trackerClient, config);

        startDate = Calendar.getInstance();
        startDate.set(2024, Calendar.JANUARY, 1, 0, 0, 0);

        closedDate = Calendar.getInstance();
        closedDate.set(2024, Calendar.JUNE, 15, 12, 0, 0);
    }

    // --- helpers -------------------------------------------------------------

    private ITicket mockTicket(String key, String issueType, String priority, String title,
                               double weight, String... labels) throws Exception {
        ITicket ticket = mock(ITicket.class);
        lenient().when(ticket.getKey()).thenReturn(key);
        lenient().when(ticket.getIssueType()).thenReturn(issueType);
        lenient().when(ticket.getPriority()).thenReturn(priority);
        lenient().when(ticket.getTicketTitle()).thenReturn(title);
        lenient().when(ticket.getWeight()).thenReturn(weight);
        lenient().when(ticket.getTicketDescription()).thenReturn("Description for " + key);

        // The fetcher puts "dateClosed" into this object, so it must be a real JSONObject.
        lenient().when(ticket.getFieldsAsJSON()).thenReturn(new JSONObject());

        JSONArray labelsJson = new JSONArray();
        for (String label : labels) {
            labelsJson.put(label);
        }
        lenient().when(ticket.getTicketLabels()).thenReturn(labelsJson);
        return ticket;
    }

    private void stubChangelogCompletedAfterStartDate() throws Exception {
        IUser author = mock(IUser.class);
        when(author.getFullName()).thenReturn("John Doe");

        IHistoryItem statusItem = mock(IHistoryItem.class);
        when(statusItem.getField()).thenReturn("status");
        when(statusItem.getToAsString()).thenReturn("Done");

        IHistory history = mock(IHistory.class);
        when(history.getAuthor()).thenReturn(author);
        when(history.getCreated()).thenReturn(closedDate);
        doReturn(Collections.singletonList(statusItem)).when(history).getHistoryItems();

        IChangelog changelog = mock(IChangelog.class);
        doReturn(Collections.singletonList(history)).when(changelog).getHistories();

        when(trackerClient.getChangeLog(anyString(), any(ITicket.class))).thenReturn(changelog);
    }

    @SuppressWarnings("unchecked")
    private void stubSearchReturns(ITicket... tickets) throws Exception {
        doAnswer(invocation -> {
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0);
            for (ITicket ticket : tickets) {
                performer.perform(ticket);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));
    }

    private ITicket bugTicket() throws Exception {
        return mockTicket("BUG-1", "Bug", "Major", "QA: Fix login issue", 3.0, "frontend");
    }

    private ITicket storyTicket() throws Exception {
        return mockTicket("STORY-1", "Story", "Minor", "Implement dashboard", 5.0, "backend");
    }

    // --- generateSummaryReport -------------------------------------------------

    @Test
    public void testGenerateSummaryReport_withBugsAndStories_includesAllSections() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateSummaryReport("project = X", startDate);

        assertNotNull(report);
        assertTrue(report.contains("### Summary by Issue Type and Priority"));
        assertTrue(report.contains("### Bug Overview by Priority"));
        assertTrue(report.contains("### Bugs Details"));
        assertTrue(report.contains("BUG-1"));
        assertTrue(report.contains("### Tasks And Stories Work Items"));
        assertTrue(report.contains("STORY-1"));
        assertTrue(report.contains("### Work Distribution by Role Category"));
        assertTrue(report.contains("## Role-Specific Work Details"));
    }

    @Test
    public void testGenerateSummaryReport_withOnlyStories_skipsBugSections() throws Exception {
        stubSearchReturns(storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateSummaryReport("project = X", startDate);

        assertNotNull(report);
        assertFalse(report.contains("### Bug Overview by Priority"));
        assertFalse(report.contains("### Bugs Details"));
        assertTrue(report.contains("### Tasks And Stories Work Items"));
    }

    @Test
    public void testGenerateSummaryReport_withNoTickets_rendersEmptyTables() throws Exception {
        stubSearchReturns();

        String report = facade.generateSummaryReport("project = X", startDate);

        assertNotNull(report);
        assertTrue(report.contains("### Summary by Issue Type and Priority"));
        assertFalse(report.contains("### Bugs Details"));
        assertFalse(report.contains("### Tasks And Stories Work Items"));
        assertTrue(report.contains("## Role-Specific Work Details"));
    }

    // --- generateCustomReport ----------------------------------------------------

    @Test
    public void testGenerateCustomReport_withAllTableTypes_includesEverySection() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateCustomReport("project = X", startDate,
                Arrays.asList(TableType.values()));

        assertNotNull(report);
        assertTrue(report.contains("### Summary by Issue Type and Priority"));
        assertTrue(report.contains("### Work Distribution by Role Category"));
        assertTrue(report.contains("## Role-Specific Work Details"));
        assertTrue(report.contains("### Bug Overview by Priority"));
        assertTrue(report.contains("### Bugs Details"));
        assertTrue(report.contains("### Tasks And Stories Work Items"));
    }

    @Test
    public void testGenerateCustomReport_bugTablesWithoutBugs_areSkipped() throws Exception {
        stubSearchReturns(storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateCustomReport("project = X", startDate,
                Arrays.asList(TableType.BUG_OVERVIEW, TableType.BUGS_TABLE, TableType.TASKS_AND_STORIES));

        assertNotNull(report);
        assertFalse(report.contains("### Bug Overview by Priority"));
        assertFalse(report.contains("### Bugs Details"));
        assertTrue(report.contains("### Tasks And Stories Work Items"));
    }

    @Test
    public void testGenerateCustomReport_tasksAndStoriesWithoutTasks_areSkipped() throws Exception {
        stubSearchReturns(bugTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateCustomReport("project = X", startDate,
                Arrays.asList(TableType.TASKS_AND_STORIES, TableType.BUG_OVERVIEW, TableType.TIMELINE));

        assertNotNull(report);
        assertFalse(report.contains("### Tasks And Stories Work Items"));
        assertTrue(report.contains("### Bug Overview by Priority"));
    }

    // --- generateBugReport -------------------------------------------------------

    @Test
    public void testGenerateBugReport_withBugs_rendersBugTables() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateBugReport("project = X", startDate);

        assertNotNull(report);
        assertTrue(report.contains("### Bug Overview by Priority"));
        assertTrue(report.contains("### Bugs Details"));
        assertTrue(report.contains("BUG-1"));
        assertFalse(report.contains("STORY-1"));
    }

    @Test
    public void testGenerateBugReport_withoutBugs_returnsNoBugsMessage() throws Exception {
        stubSearchReturns(storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateBugReport("project = X", startDate);

        assertEquals("No bugs found in the specified period.", report);
    }

    // --- generateRoleBasedReport ---------------------------------------------------

    @Test
    public void testGenerateRoleBasedReport_includesRoleSections() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateRoleBasedReport("project = X", startDate);

        assertNotNull(report);
        assertTrue(report.contains("### Work Distribution by Role Category"));
        assertTrue(report.contains("## Role-Specific Work Details"));
        assertTrue(report.contains("### QA Work"));
    }

    // --- generateTimelineReport ------------------------------------------------------

    @Test
    public void testGenerateTimelineReport_includesTimelineHeader() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateTimelineReport("project = X", startDate, TimelinePeriod.MONTH);

        assertNotNull(report);
        assertTrue(report.startsWith("# Deliverables Timeline Report"));
        assertTrue(report.contains("2024-06"));
    }

    // --- generateLabelAnalysisReport ---------------------------------------------------

    @Test
    public void testGenerateLabelAnalysisReport_withFocusLabels_rendersAnalysis() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateLabelAnalysisReport("project = X", startDate,
                TimelinePeriod.MONTH, Collections.singletonList("frontend"));

        assertNotNull(report);
        assertTrue(report.contains("# Ticket Label Analysis"));
        assertTrue(report.contains("frontend"));
    }

    @Test
    public void testGenerateLabelAnalysisReport_withNullFocusLabels_throwsIllegalArgument() throws Exception {
        stubSearchReturns(bugTicket());
        stubChangelogCompletedAfterStartDate();

        try {
            facade.generateLabelAnalysisReport("project = X", startDate, TimelinePeriod.MONTH, null);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Focus labels"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException for null focus labels");
    }

    @Test
    public void testGenerateCustomReport_withTimelineAndStoryPointsTypes_rendersThem() throws Exception {
        stubSearchReturns(bugTicket(), storyTicket());
        stubChangelogCompletedAfterStartDate();

        String report = facade.generateCustomReport("project = X", startDate,
                Arrays.asList(TableType.TIMELINE, TableType.STORY_POINTS_DISTRIBUTION,
                        TableType.ROLE_DISTRIBUTION, TableType.ROLE_SPECIFIC, TableType.SUMMARY));

        assertNotNull(report);
        assertTrue(report.contains("### Summary by Issue Type and Priority"));
        assertTrue(report.contains("### Work Distribution by Role Category"));
        assertTrue(report.contains("## Role-Specific Work Details"));
    }
}
