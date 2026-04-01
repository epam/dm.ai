// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChangelogAssessmentTest {

    @Mock
    private JiraClient mockJiraClient;
    @Mock
    private TrackerClient mockTrackerClient;
    @Mock
    private Ticket mockTicket;
    @Mock
    private IChangelog mockChangelog;
    @Mock
    private IHistory mockHistory;
    @Mock
    private IHistoryItem mockHistoryItem;
    @Mock
    private IUser mockUser;
    @Mock
    private Employees mockEmployees;
    @Mock
    private IEmployees mockIEmployees;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testFindCreatedDate() throws IOException {
        when(mockJiraClient.performTicket(anyString(), any(String[].class))).thenReturn(mockTicket);
        when(mockTicket.getFields()).thenReturn(mock(Fields.class));
        when(mockTicket.getFields().getCreated()).thenReturn(Calendar.getInstance().getTime());
        when(mockTicket.getFields().getCreator()).thenReturn(mock(Assignee.class));
        when(mockTicket.getFields().getCreator().getDisplayName()).thenReturn("Creator Name");

        List<KeyTime> result = ChangelogAssessment.findCreatedDate(mockJiraClient, "TEST-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Creator Name", result.get(0).getWho());
    }



    @Test
    public void testWhoReportedTheTicket() {
        when(mockTicket.getFields()).thenReturn(mock(Fields.class));
        when(mockTicket.getFields().getCreator()).thenReturn(mock(Assignee.class));
        when(mockTicket.getFields().getCreator().getDisplayName()).thenReturn("Creator Name");
        when(mockEmployees.contains(anyString())).thenReturn(true);

        String result = ChangelogAssessment.whoReportedTheTicket(mockTicket, mockEmployees);

        assertNotNull(result);
        assertEquals("Creator Name", result);
    }

    @Test
    public void testIsAssigneeFieldWasEverChanged() {
        // Create concrete lists instead of mocking them
        List<IHistoryItem> historyItems = new ArrayList<>();
        historyItems.add(mockHistoryItem);

        List<IHistory> histories = new ArrayList<>();
        histories.add(mockHistory);

        // Use doReturn().when() for methods that return generic types
        doReturn(historyItems).when(mockHistory).getHistoryItems();

        // This can remain as it was, since it doesn't involve generic types
        when(mockHistoryItem.getField()).thenReturn("assignee");

        boolean result = ChangelogAssessment.isAssigneeFieldWasEverChanged(histories);

        assertTrue(result);
    }

    @Test
    public void testFieldWasChangedByUser() throws IOException {
        // Create concrete lists instead of mocking them
        List<IHistory> histories = new ArrayList<>();
        histories.add(mockHistory);

        List<IHistoryItem> historyItems = new ArrayList<>();
        historyItems.add(mockHistoryItem);

        // Use doReturn().when() for methods that return generic types
        doReturn(mockChangelog).when(mockJiraClient).getChangeLog(anyString(), any(Ticket.class));
        doReturn(histories).when(mockChangelog).getHistories();
        doReturn(historyItems).when(mockHistory).getHistoryItems();

        // These can remain as they were, since they don't involve generic types
        when(mockHistoryItem.getField()).thenReturn("field");
        when(mockHistory.getAuthor()).thenReturn(mockUser);
        when(mockUser.toString()).thenReturn("User");

        boolean result = ChangelogAssessment.fieldWasChangedByUser(mockJiraClient, "TEST-1", "field", "User", mockTicket);

        assertTrue(result);
    }

    @Test
    public void testIsFirstTimeRight() throws IOException {
        // Create concrete lists instead of mocking them
        List<IHistory> histories = new ArrayList<>();
        histories.add(mockHistory);

        List<IHistoryItem> historyItems = new ArrayList<>();
        historyItems.add(mockHistoryItem);

        // Use doReturn().when() for methods that return generic types
        doReturn(mockChangelog).when(mockTrackerClient).getChangeLog(anyString(), any(ITicket.class));
        doReturn(histories).when(mockChangelog).getHistories();
        doReturn(historyItems).when(mockHistory).getHistoryItems();

        // These can remain as they were, since they don't involve generic types
        when(mockHistoryItem.getField()).thenReturn("status");
        when(mockHistoryItem.getToAsString()).thenReturn("Quality");

        boolean result = ChangelogAssessment.isFirstTimeRight(mockTrackerClient, "TEST-1", mockTicket, new String[]{"In Progress"}, new String[]{"Quality"});

        assertTrue(result);
    }
}