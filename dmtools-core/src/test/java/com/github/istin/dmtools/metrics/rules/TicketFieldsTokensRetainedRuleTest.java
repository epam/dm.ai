// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class TicketFieldsTokensRetainedRuleTest {

    private TrackerClient mockTrackerClient;
    private IEmployees mockEmployees;
    private IChangelog mockChangeLog;
    private ITicket mockTicket;
    private Calendar changeDate;
    private Calendar laterChangeDate;
    private final Claude35TokenCounter tokenCounter = new Claude35TokenCounter();

    @Before
    public void setUp() throws Exception {
        mockTrackerClient = Mockito.mock(TrackerClient.class);
        mockEmployees = Mockito.mock(IEmployees.class);
        mockChangeLog = Mockito.mock(IChangelog.class);
        mockTicket = Mockito.mock(ITicket.class);
        changeDate = Calendar.getInstance();
        laterChangeDate = Calendar.getInstance();
        laterChangeDate.add(Calendar.HOUR, 1);

        when(mockTicket.getKey()).thenReturn("TEST-1");
        when(mockTicket.getCreated()).thenReturn(new Date(1_600_000_000_000L));
        when(mockTrackerClient.getChangeLog(anyString(), any(ITicket.class))).thenReturn(mockChangeLog);
        doReturn(new ArrayList<IHistory>()).when(mockChangeLog).getHistories();

        when(mockEmployees.contains("Alice")).thenReturn(true);
        when(mockEmployees.contains("Bob")).thenReturn(true);
        when(mockEmployees.transformName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private IUser user(String fullName) {
        IUser user = Mockito.mock(IUser.class);
        when(user.getFullName()).thenReturn(fullName);
        return user;
    }

    private IHistoryItem item(String field, String from, String to) {
        IHistoryItem item = Mockito.mock(IHistoryItem.class);
        when(item.getField()).thenReturn(field);
        when(item.getFromAsString()).thenReturn(from);
        when(item.getToAsString()).thenReturn(to);
        return item;
    }

    private IHistory history(String authorName, Calendar created, IHistoryItem... items) {
        IHistory history = Mockito.mock(IHistory.class);
        IUser author = authorName == null ? null : user(authorName);
        when(history.getAuthor()).thenReturn(author);
        when(history.getCreated()).thenReturn(created);
        doReturn(Arrays.asList(items)).when(history).getHistoryItems();
        return history;
    }

    private IHistory history(String authorName, IHistoryItem... items) {
        return history(authorName, changeDate, items);
    }

    private void histories(IHistory... histories) {
        doReturn(Arrays.asList(histories)).when(mockChangeLog).getHistories();
    }

    private TicketFieldsTokensRetainedRule rule(String[] filterFields) {
        return new TicketFieldsTokensRetainedRule(mockEmployees, filterFields, false, "all");
    }

    @Test
    public void testIdenticalValueRetainsAllTokens() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("hello world");
        histories(history("Bob", item("description", "", "hello world")));

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:retained:description:Bob", result.get(0).getKey());
        assertEquals("Bob", result.get(0).getWho());
        assertEquals(tokenCounter.countTokens("hello world"), result.get(0).getWeight(), 0.0001);
        assertEquals(changeDate, result.get(0).getWhen());
    }

    @Test
    public void testSimilarityReducesWeight() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("aaaa xxxx yyyy");
        histories(history("Bob", item("description", "", "aaaa bbbb cccc")));

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getWeight() > 0);
        assertTrue(result.get(0).getWeight() < tokenCounter.countTokens("aaaa bbbb cccc"));
    }

    @Test
    public void testZeroSimilarityProducesNoResult() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("bbbb");
        histories(history("Bob", item("description", "", "aaaa")));

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testBlankFinalValueSkipsField() throws Exception {
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("   ");
        histories(history("Bob", item("summary", "", "some text")));

        List<KeyTime> result = rule(new String[]{"summary"}).check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testNullToAsStringIsSkipped() throws Exception {
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("final text");
        histories(history("Bob", item("summary", "old", null)));

        List<KeyTime> result = rule(new String[]{"summary"}).check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testLatestVersionPerAuthorWins() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("zzzz");
        // newer change first: the older one must not replace it
        histories(
                history("Bob", laterChangeDate, item("description", "", "zzzz")),
                history("Bob", changeDate, item("description", "", "aaaa"))
        );

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(tokenCounter.countTokens("zzzz"), result.get(0).getWeight(), 0.0001);
        assertEquals(laterChangeDate, result.get(0).getWhen());
    }

    @Test
    public void testLaterVersionReplacesEarlierOne() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("zzzz");
        // older change first: the newer one must replace it
        histories(
                history("Bob", changeDate, item("description", "", "aaaa")),
                history("Bob", laterChangeDate, item("description", "", "zzzz"))
        );

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(tokenCounter.countTokens("zzzz"), result.get(0).getWeight(), 0.0001);
        assertEquals(laterChangeDate, result.get(0).getWhen());
    }

    @Test
    public void testNullHistoryCreatedFallsBackToTicketCreated() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("hello");
        histories(history("Bob", (Calendar) null, item("description", "", "hello")));

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(DateUtils.calendar(mockTicket.getCreated()).getTimeInMillis(),
                result.get(0).getWhen().getTimeInMillis());
    }

    @Test
    public void testNullAuthorAndAuthorNotInEmployeesAreSkipped() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("hello");
        histories(
                history(null, item("description", "", "hello")),
                history("Charlie", item("description", "", "hello"))
        );

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testNonMatchingFieldsAndNullFieldAreSkipped() throws Exception {
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");
        histories(history("Bob",
                item("status", "Open", "Done"),
                item(null, "a", "b"),
                item("summary", "", "summary text")
        ));

        List<KeyTime> result = rule(new String[]{"summary"}).check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:retained:summary:Bob", result.get(0).getKey());
    }

    @Test
    public void testCreatorFilterModeDefaultsToAll() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getTicketDescription()).thenReturn("hello world");
        histories(
                history("Alice", item("description", "", "hello world")),
                history("Bob", item("description", "", "hello world"))
        );

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"description"}, false, null).check(mockTrackerClient, mockTicket);

        assertEquals(2, result.size());
    }

    @Test
    public void testCreatorFilterModeOnlyWithJiraTicket() throws Exception {
        Ticket jiraTicket = jiraTicket("Alice");
        when(jiraTicket.getTicketDescription()).thenReturn("hello world");
        histories(
                history("Alice", item("description", "", "hello world")),
                history("Bob", item("description", "", "hello world"))
        );

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"description"}, false, "only").check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getWho());
    }

    @Test
    public void testCreatorFilterModeExcludeWithJiraTicket() throws Exception {
        Ticket jiraTicket = jiraTicket("Alice");
        when(jiraTicket.getTicketDescription()).thenReturn("hello world");
        histories(
                history("Alice", item("description", "", "hello world")),
                history("Bob", item("description", "", "hello world"))
        );

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"description"}, false, "exclude").check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testIncludeInitialAddsCreatorBaseline() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("initial summary text");
        histories(history("Bob", item("status", "Open", "Done")));

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary"}, true, "all").check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:retained:summary:Alice", result.get(0).getKey());
        assertEquals("Alice", result.get(0).getWho());
        assertEquals(tokenCounter.countTokens("initial summary text"), result.get(0).getWeight(), 0.0001);
        assertEquals(DateUtils.calendar(mockTicket.getCreated()).getTimeInMillis(),
                result.get(0).getWhen().getTimeInMillis());
    }

    @Test
    public void testIncludeInitialSkipsFieldWithInitialHistory() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("something");
        histories(history("Bob", item("summary", "", "something")));

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary"}, true, "all").check(mockTrackerClient, mockTicket);

        // field has initial history, so no baseline for the creator; only Bob's change remains
        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testIncludeInitialBlankValueVariantsMarkInitialHistory() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("x");
        when(mockTicket.getFieldValueAsString("priority")).thenReturn("y");
        histories(history("Bob",
                item("summary", "null", "x"),
                item("priority", " none ", "y")
        ));

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary", "priority"}, true, "all").check(mockTrackerClient, mockTicket);

        // "null" and "none" from-values count as blank: both fields have initial history, no baselines
        assertEquals(2, result.size());
        for (KeyTime keyTime : result) {
            assertEquals("Bob", keyTime.getWho());
        }
    }

    @Test
    public void testIncludeInitialNoBaselineWhenCreatorAlreadyHasVersion() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");
        histories(history("Alice", item("summary", "", "summary text")));

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary"}, true, "all").check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:retained:summary:Alice", result.get(0).getKey());
    }

    @Test
    public void testBaselineWhoUnknownWhenCreatorIsNull() throws Exception {
        when(mockTicket.getCreator()).thenReturn(null);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary"}, true, "all").check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:retained:summary:Unknown", result.get(0).getKey());
        assertEquals(IEmployees.UNKNOWN, result.get(0).getWho());
    }

    @Test
    public void testBaselineWhoUnknownWhenCreatorNotInEmployees() throws Exception {
        IUser charlie = user("Charlie");
        when(mockTicket.getCreator()).thenReturn(charlie);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary"}, true, "all").check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(IEmployees.UNKNOWN, result.get(0).getWho());
    }

    @Test
    public void testJiraTicketReporterUsedAsBaselineFallback() throws Exception {
        Ticket jiraTicket = jiraTicket("Alice");
        when(jiraTicket.getCreator()).thenReturn(null);
        when(jiraTicket.getFieldValueAsString("summary")).thenReturn("summary text");

        List<KeyTime> result = new TicketFieldsTokensRetainedRule(mockEmployees,
                new String[]{"summary"}, true, "all").check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:retained:summary:Alice", result.get(0).getKey());
    }

    @Test
    public void testEmptyHistoriesProduceNoResults() throws Exception {
        when(mockTicket.getTicketDescription()).thenReturn("hello world");

        List<KeyTime> result = rule(new String[]{"description"}).check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testGetRequiredExtraFields() {
        TicketFieldsTokensRetainedRule withoutFilters = new TicketFieldsTokensRetainedRule(
                mockEmployees, null, false, "all");
        assertEquals(Collections.singletonList("changelog"), withoutFilters.getRequiredExtraFields());

        TicketFieldsTokensRetainedRule withFilters = new TicketFieldsTokensRetainedRule(
                mockEmployees, new String[]{"description", "summary"}, false, "all");
        assertEquals(Arrays.asList("changelog", "description", "summary"), withFilters.getRequiredExtraFields());
    }

    private Ticket jiraTicket(String creatorDisplayName) {
        Ticket jiraTicket = Mockito.mock(Ticket.class);
        Fields fields = Mockito.mock(Fields.class);
        Assignee creator = Mockito.mock(Assignee.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getCreated()).thenReturn(new Date(1_600_000_000_000L));
        when(jiraTicket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);
        when(creator.getDisplayName()).thenReturn(creatorDisplayName);
        return jiraTicket;
    }
}
