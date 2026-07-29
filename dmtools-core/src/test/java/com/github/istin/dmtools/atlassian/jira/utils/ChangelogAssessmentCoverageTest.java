// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage tests for {@link ChangelogAssessment} focused on the changelog
 * walking methods (status dates, responsibility detection, assignee tracking)
 * that the basic {@link ChangelogAssessmentTest} does not exercise.
 * All tracker interactions are mocked; tests assert real branching behavior.
 */
public class ChangelogAssessmentCoverageTest {

    private static final String KEY = "TEST-1";
    private static final String STATUS_FIELD = "status";

    @Mock
    private TrackerClient trackerClient;
    @Mock
    private Ticket ticket;
    @Mock
    private IChangelog changelog;
    @Mock
    private IUser user;
    @Mock
    private IEmployees employees;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(trackerClient.getDefaultStatusField()).thenReturn(STATUS_FIELD);
        doReturn(changelog).when(trackerClient).getChangeLog(anyString(), any());
        when(user.getFullName()).thenReturn("John Doe");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private IHistory history(IUser author, IHistoryItem... items) {
        IHistory history = mock(IHistory.class);
        when(history.getAuthor()).thenReturn(author);
        when(history.getCreated()).thenReturn(Calendar.getInstance());
        doReturn(new ArrayList<>(Arrays.asList(items))).when(history).getHistoryItems();
        return history;
    }

    private IHistoryItem item(String field, String toAsString) {
        IHistoryItem item = mock(IHistoryItem.class);
        when(item.getField()).thenReturn(field);
        when(item.getToAsString()).thenReturn(toAsString);
        return item;
    }

    private void setChangelogHistories(List<IHistory> histories) {
        doReturn(histories).when(changelog).getHistories();
    }

    private void setChangelogHistories(IHistory... histories) {
        setChangelogHistories(new ArrayList<>(Arrays.asList(histories)));
    }

    // ------------------------------------------------------------------
    // findDatesWhenTicketWasInStatus
    // ------------------------------------------------------------------

    @Test
    public void testFindDatesWhenTicketWasInStatusWithMatchingStatusChange() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                trackerClient, KEY, ticket, "In Progress");

        assertEquals(1, result.size());
        assertEquals(KEY, result.get(0).getKey());
        assertEquals("John Doe", result.get(0).getWho());
        assertEquals(1.0, result.get(0).getWeight(), 0.001);
    }

    @Test
    public void testFindDatesWhenTicketWasInStatusWithCustomNameAndWeight() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));
        when(ticket.getWeight()).thenReturn(2.5);

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                "customName", true, trackerClient, KEY, ticket, "In Progress");

        assertEquals(1, result.size());
        assertEquals("customName", result.get(0).getWho());
        assertEquals(2.5, result.get(0).getWeight(), 0.001);
    }

    @Test
    public void testFindDatesWhenTicketWasInStatusIgnoreIfStatusClearsResults() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "Done")),
                history(user, item(STATUS_FIELD, "Reopened")),
                history(user, item(STATUS_FIELD, "Done")));

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                "Reopened", null, false, trackerClient, KEY, ticket, "Done");

        // First "Done" is cleared by "Reopened", only the last "Done" remains
        assertEquals(1, result.size());
    }

    @Test
    public void testFindDatesWhenTicketWasInStatusUnmatchedStatusGivesEmptyResult() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                trackerClient, KEY, ticket, "Done");

        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindDatesWhenTicketWasInStatusFallsBackToTicketStatus() throws IOException {
        // No status item with non-null toString -> ifStatusChanged stays false
        setChangelogHistories(history(user, item(STATUS_FIELD, null)));
        when(ticket.getStatus()).thenReturn("Open");
        when(ticket.getCreated()).thenReturn(new Date());
        when(ticket.getCreator()).thenReturn(user);
        when(ticket.getWeight()).thenReturn(3.0);

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                null, "weighted", true, trackerClient, KEY, ticket, "Open");

        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).getWho());
        assertEquals(3.0, result.get(0).getWeight(), 0.001);
    }

    @Test
    public void testFindDatesWhenTicketWasInStatusFallbackPerformsTicketWhenTicketNull() throws IOException {
        // History with a non-status field: sets firstAuthor but no status change
        setChangelogHistories(history(user, item("assignee", "someone")));

        ITicket performedTicket = mock(ITicket.class);
        when(performedTicket.getStatus()).thenReturn("Open");
        when(performedTicket.getCreated()).thenReturn(new Date());
        when(performedTicket.getCreator()).thenReturn(null);
        when(trackerClient.getDefaultQueryFields()).thenReturn(new String[]{"status"});
        doReturn(performedTicket).when(trackerClient).performTicket(anyString(), any(String[].class));

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                trackerClient, KEY, null, "Open");

        assertEquals(1, result.size());
        // creator is null on the performed ticket, so the first history author is used
        assertEquals("John Doe", result.get(0).getWho());
    }

    @Test
    public void testFindDatesWhenTicketWasInStatusFallbackStatusNotMatched() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, null)));
        when(ticket.getStatus()).thenReturn("Closed");
        when(ticket.getCreated()).thenReturn(new Date());

        List<KeyTime> result = ChangelogAssessment.findDatesWhenTicketWasInStatus(
                trackerClient, KEY, ticket, "Open");

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // findSourceStatusForRequestedOne
    // ------------------------------------------------------------------

    @Test
    public void testFindSourceStatusForRequestedOneFound() throws IOException {
        IHistoryItem statusItem = item(STATUS_FIELD, "In Progress");
        setChangelogHistories(history(user, statusItem));

        Pair<IUser, IHistoryItem> result = ChangelogAssessment.findSourceStatusForRequestedOne(
                trackerClient, KEY, ticket, "in progress");

        assertNotNull(result);
        assertSame(user, result.getKey());
        assertSame(statusItem, result.getValue());
    }

    @Test
    public void testFindSourceStatusForRequestedOneNotFound() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "Done")),
                history(user, item(STATUS_FIELD, null)));

        Pair<IUser, IHistoryItem> result = ChangelogAssessment.findSourceStatusForRequestedOne(
                trackerClient, KEY, ticket, "In Progress");

        assertNull(result);
    }

    // ------------------------------------------------------------------
    // findWhoIsResponsible
    // ------------------------------------------------------------------

    @Test
    public void testFindWhoIsResponsibleReturnsLastAssignee() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "In Progress")),
                history(user, item("assignee", "Jane Smith")));
        when(ticket.getKey()).thenReturn(KEY);
        when(ticket.getCreator()).thenReturn(null);

        String result = ChangelogAssessment.findWhoIsResponsible(
                trackerClient, null, ticket, "In Progress");

        assertEquals("Jane Smith", result);
    }

    @Test
    public void testFindWhoIsResponsibleFallsBackToWhoMovedToStatus() throws IOException {
        // No assignee items at all, status moved by John Doe
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));
        when(ticket.getKey()).thenReturn(KEY);
        when(ticket.getCreator()).thenReturn(null);
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getAssignee()).thenReturn(null);

        String result = ChangelogAssessment.findWhoIsResponsible(
                trackerClient, null, ticket, "In Progress");

        assertEquals("John Doe", result);
    }

    @Test
    public void testFindWhoIsResponsibleReturnsUnknownWhenNobodyFound() throws IOException {
        // Status change by someone outside the team, no assignee information
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));
        when(ticket.getKey()).thenReturn(KEY);
        when(ticket.getCreator()).thenReturn(null);
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getAssignee()).thenReturn(null);
        when(employees.contains(anyString())).thenReturn(false);

        String result = ChangelogAssessment.findWhoIsResponsible(
                trackerClient, mock(Employees.class) /* not used for filtering inside */, ticket, "In Progress");

        // plain mock Employees returns false from contains() and null from transformName()
        assertEquals(Employees.UNKNOWN, result);
    }

    // ------------------------------------------------------------------
    // findWhoFromEmployeeMovedToStatus
    // ------------------------------------------------------------------

    @Test
    public void testFindWhoFromEmployeeMovedToStatusWithTeamFilter() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "In Progress")),
                history(user, item(STATUS_FIELD, "Done")));
        when(employees.contains("John Doe")).thenReturn(true);
        when(employees.transformName("John Doe")).thenReturn("jdoe");

        String result = ChangelogAssessment.findWhoFromEmployeeMovedToStatus(
                trackerClient, KEY, ticket, employees, "In Progress", "Done");

        assertEquals("jdoe", result);
    }

    @Test
    public void testFindWhoFromEmployeeMovedToStatusWithoutTeamFilter() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));

        String result = ChangelogAssessment.findWhoFromEmployeeMovedToStatus(
                trackerClient, KEY, ticket, null, "In Progress");

        assertEquals("John Doe", result);
    }

    @Test
    public void testFindWhoFromEmployeeMovedToStatusNotInTeamReturnsNull() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));
        when(employees.contains(anyString())).thenReturn(false);

        String result = ChangelogAssessment.findWhoFromEmployeeMovedToStatus(
                trackerClient, KEY, ticket, employees, "In Progress");

        assertNull(result);
    }

    @Test
    public void testFindWhoFromEmployeeMovedToStatusNoMatchingStatus() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "Done")));

        String result = ChangelogAssessment.findWhoFromEmployeeMovedToStatus(
                trackerClient, KEY, ticket, null, "In Progress");

        assertNull(result);
    }

    // ------------------------------------------------------------------
    // findLastAssigneeForStatus
    // ------------------------------------------------------------------

    @Test
    public void testFindLastAssigneeForStatusCreatorInTeam() throws IOException {
        setChangelogHistories(history(user, item(STATUS_FIELD, "In Progress")));
        when(ticket.getCreator()).thenReturn(user);
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getAssignee()).thenReturn(null);
        when(employees.contains("John Doe")).thenReturn(true);
        when(employees.transformName("John Doe")).thenReturn("jdoe");

        Pair<String, IHistoryItem> result = ChangelogAssessment.findLastAssigneeForStatus(
                trackerClient, KEY, ticket, employees, "In Progress");

        assertEquals("jdoe", result.getKey());
        assertNotNull(result.getValue());
    }

    @Test
    public void testFindLastAssigneeForStatusAssigneeFromFieldsWhenNeverChanged() throws IOException {
        setChangelogHistories(Collections.<IHistory>emptyList());
        when(ticket.getCreator()).thenReturn(null);
        Assignee assignee = mock(Assignee.class);
        when(assignee.getFullName()).thenReturn("Field Assignee");
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getAssignee()).thenReturn(assignee);

        Pair<String, IHistoryItem> result = ChangelogAssessment.findLastAssigneeForStatus(
                trackerClient, KEY, ticket, null, "In Progress");

        assertEquals("Field Assignee", result.getKey());
        assertNull(result.getValue());
    }

    @Test
    public void testFindLastAssigneeForStatusCreatorNotInTeamKeepsNull() throws IOException {
        setChangelogHistories(Collections.<IHistory>emptyList());
        when(ticket.getCreator()).thenReturn(user);
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getAssignee()).thenReturn(null);
        when(employees.contains(anyString())).thenReturn(false);

        Pair<String, IHistoryItem> result = ChangelogAssessment.findLastAssigneeForStatus(
                trackerClient, KEY, ticket, employees, "In Progress");

        assertNull(result.getKey());
    }

    @Test
    public void testFindLastAssigneeForStatusIgnoresAssigneeAfterNonTargetStatus() throws IOException {
        setChangelogHistories(
                history(user, item("assignee", "early")),
                history(user, item(STATUS_FIELD, "In Progress")),
                history(user, item("assignee", "late")),
                history(user, item(STATUS_FIELD, "Done")),
                history(user, item("assignee", "ignored")));
        when(ticket.getCreator()).thenReturn(null);

        Pair<String, IHistoryItem> result = ChangelogAssessment.findLastAssigneeForStatus(
                trackerClient, KEY, ticket, null, "In Progress");

        // "early" is captured (no status change yet), then "late" after the target
        // status; "ignored" comes after a non-target status and must be skipped
        assertEquals("late", result.getKey());
        assertNotNull(result.getValue());
    }

    @Test
    public void testFindLastAssigneeForStatusSkipsNullAssigneeAndUsesTeamFilter() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "In Progress")),
                history(user, item("assignee", null)),
                history(user, item("assignee", "Jane Smith")));
        when(ticket.getCreator()).thenReturn(null);
        when(employees.contains("Jane Smith")).thenReturn(true);
        when(employees.transformName("Jane Smith")).thenReturn("jsmith");

        Pair<String, IHistoryItem> result = ChangelogAssessment.findLastAssigneeForStatus(
                trackerClient, KEY, ticket, employees, "In Progress");

        assertEquals("jsmith", result.getKey());
    }

    @Test
    public void testFindLastAssigneeForStatusWithNullTargetStatuses() throws IOException {
        setChangelogHistories(history(user, item("assignee", "Jane Smith")));
        when(ticket.getCreator()).thenReturn(null);

        Pair<String, IHistoryItem> result = ChangelogAssessment.findLastAssigneeForStatus(
                trackerClient, KEY, ticket, null, (String[]) null);

        assertEquals("Jane Smith", result.getKey());
        assertNull(result.getValue());
    }

    // ------------------------------------------------------------------
    // isAssigneeFieldWasEverChanged / fieldWasChangedByUser negative paths
    // ------------------------------------------------------------------

    @Test
    public void testIsAssigneeFieldWasEverChangedReturnsFalse() {
        List<IHistory> histories = new ArrayList<>();
        histories.add(history(user, item(STATUS_FIELD, "Done")));

        assertFalse(ChangelogAssessment.isAssigneeFieldWasEverChanged(histories));
        assertFalse(ChangelogAssessment.isAssigneeFieldWasEverChanged(Collections.<IHistory>emptyList()));
    }

    @Test
    public void testFieldWasChangedByUserReturnsFalse() throws IOException {
        com.github.istin.dmtools.atlassian.jira.JiraClient jiraClient =
                mock(com.github.istin.dmtools.atlassian.jira.JiraClient.class);
        doReturn(changelog).when(jiraClient).getChangeLog(anyString(), any(Ticket.class));
        setChangelogHistories(history(user, item("summary", "new summary")));

        boolean result = ChangelogAssessment.fieldWasChangedByUser(
                jiraClient, KEY, "assignee", "someone else", ticket);

        assertFalse(result);
    }

    // ------------------------------------------------------------------
    // isFirstTimeRight
    // ------------------------------------------------------------------

    @Test
    public void testIsFirstTimeRightReturnsFalseWhenBackToInProgress() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "Quality Assurance")),
                history(user, item(STATUS_FIELD, "In Progress")));

        boolean result = ChangelogAssessment.isFirstTimeRight(
                trackerClient, KEY, ticket, new String[]{"In Progress"}, new String[]{"Quality"});

        assertFalse(result);
    }

    @Test
    public void testIsFirstTimeRightTrueWhenInProgressBeforeQuality() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, "In Progress")),
                history(user, item(STATUS_FIELD, "Quality Assurance")));

        boolean result = ChangelogAssessment.isFirstTimeRight(
                trackerClient, KEY, ticket, new String[]{"In Progress"}, new String[]{"Quality"});

        assertTrue(result);
    }

    @Test
    public void testIsFirstTimeRightIgnoresNullToStringAndNonStatusFields() throws IOException {
        setChangelogHistories(
                history(user, item(STATUS_FIELD, null)),
                history(user, item("assignee", "John Doe")));

        boolean result = ChangelogAssessment.isFirstTimeRight(
                trackerClient, KEY, ticket, new String[]{"In Progress"}, new String[]{"Quality"});

        assertTrue(result);
    }

    // ------------------------------------------------------------------
    // whoReportedTheTicket additional branches
    // ------------------------------------------------------------------

    @Test
    public void testWhoReportedTheTicketFallsBackToReporter() {
        Assignee creator = mock(Assignee.class);
        when(creator.getDisplayName()).thenReturn("Creator");
        Assignee reporter = mock(Assignee.class);
        when(reporter.getDisplayName()).thenReturn("Reporter");
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);
        when(fields.getReporter()).thenReturn(reporter);
        when(employees.contains("Creator")).thenReturn(false);
        when(employees.contains("Reporter")).thenReturn(true);

        String result = ChangelogAssessment.whoReportedTheTicket(ticket, employees);

        assertEquals("Reporter", result);
    }

    @Test
    public void testWhoReportedTheTicketReturnsUnknownWhenNobodyInTeam() {
        Assignee creator = mock(Assignee.class);
        when(creator.getDisplayName()).thenReturn("Creator");
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);
        when(fields.getReporter()).thenReturn(null);
        when(employees.contains(anyString())).thenReturn(false);

        String result = ChangelogAssessment.whoReportedTheTicket(ticket, employees);

        assertEquals(IEmployees.UNKNOWN, result);
    }

    @Test
    public void testWhoReportedTheTicketReturnsNullWhenEmployeesNull() {
        Assignee creator = mock(Assignee.class);
        Fields fields = mock(Fields.class);
        when(ticket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);

        String result = ChangelogAssessment.whoReportedTheTicket(ticket, null);

        assertNull(result);
    }
}
