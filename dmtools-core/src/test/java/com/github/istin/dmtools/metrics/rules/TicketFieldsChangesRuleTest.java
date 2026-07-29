// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.rules;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class TicketFieldsChangesRuleTest {

    private TrackerClient mockTrackerClient;
    private IEmployees mockEmployees;
    private IChangelog mockChangeLog;
    private ITicket mockTicket;
    private Calendar changeDate;

    @Before
    public void setUp() throws Exception {
        mockTrackerClient = Mockito.mock(TrackerClient.class);
        mockEmployees = Mockito.mock(IEmployees.class);
        mockChangeLog = Mockito.mock(IChangelog.class);
        mockTicket = Mockito.mock(ITicket.class);
        changeDate = Calendar.getInstance();

        when(mockTicket.getKey()).thenReturn("TEST-1");
        when(mockTicket.getCreated()).thenReturn(new Date(1_600_000_000_000L));
        when(mockTrackerClient.getChangeLog(anyString(), any(ITicket.class))).thenReturn(mockChangeLog);
        doReturn(new ArrayList<IHistory>()).when(mockChangeLog).getHistories();

        when(mockEmployees.contains("Alice")).thenReturn(true);
        when(mockEmployees.contains("Bob")).thenReturn(true);
        when(mockEmployees.transformName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @After
    public void tearDown() {
        PropertyReader.clearOverrides();
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

    private IHistory history(String authorName, IHistoryItem... items) {
        IHistory history = Mockito.mock(IHistory.class);
        IUser author = authorName == null ? null : user(authorName);
        when(history.getAuthor()).thenReturn(author);
        when(history.getCreated()).thenReturn(changeDate);
        doReturn(Arrays.asList(items)).when(history).getHistoryItems();
        return history;
    }

    private void histories(IHistory... histories) {
        doReturn(Arrays.asList(histories)).when(mockChangeLog).getHistories();
    }

    private void setDividerOverride(String field, String value) {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("TICKET_FIELDS_CHANGED_DIVIDER_" + field.toUpperCase(), value);
        PropertyReader.setOverrides(overrides);
    }

    @Test
    public void testCheckExcludesCreatorByDefault() throws Exception {
        setDividerOverride("status", "1");
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(
                history("Bob", item("status", "Open", "Done")),
                history("Alice", item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
        assertEquals("TEST-1OpenDone", result.get(0).getKey());
        assertEquals(1.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testCheckIncludesOnlyCreatorWhenCollectionIfByCreator() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(
                history("Bob", item("status", "Open", "Done")),
                history("Alice", item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees, null, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getWho());
    }

    @Test
    public void testCreatorFilterModeAll() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(
                history("Bob", item("status", "Open", "Done")),
                history("Alice", item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees, null, false, false, false, true, "all");
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(2, result.size());
    }

    @Test
    public void testCreatorFilterModeOnly() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(
                history("Bob", item("status", "Open", "Done")),
                history("Alice", item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees, null, false, false, false, true, "only");
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getWho());
    }

    @Test
    public void testCreatorFilterModeExclude() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(
                history("Bob", item("status", "Open", "Done")),
                history("Alice", item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees, null, false, false, false, true, "exclude");
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testSkipsAuthorNotInEmployeesAndNullAuthor() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(
                history("Charlie", item("status", "Open", "Done")),
                history(null, item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testUnknownCreatorWhenCreatorIsNull() throws Exception {
        when(mockTicket.getCreator()).thenReturn(null);
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testUnknownCreatorWhenCreatorNotInEmployees() throws Exception {
        IUser charlie = user("Charlie");
        when(mockTicket.getCreator()).thenReturn(charlie);
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testJiraTicketUsesCreatorFromFields() throws Exception {
        Ticket jiraTicket = Mockito.mock(Ticket.class);
        Fields fields = Mockito.mock(Fields.class);
        Assignee creator = Mockito.mock(Assignee.class);
        Assignee reporter = Mockito.mock(Assignee.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);
        when(fields.getReporter()).thenReturn(reporter);
        when(creator.getDisplayName()).thenReturn("Alice");
        when(reporter.getDisplayName()).thenReturn("Bob");
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testJiraTicketFallsBackToReporter() throws Exception {
        Ticket jiraTicket = Mockito.mock(Ticket.class);
        Fields fields = Mockito.mock(Fields.class);
        Assignee creator = Mockito.mock(Assignee.class);
        Assignee reporter = Mockito.mock(Assignee.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);
        when(fields.getReporter()).thenReturn(reporter);
        when(creator.getDisplayName()).thenReturn("Charlie");
        when(reporter.getDisplayName()).thenReturn("Bob");
        histories(
                history("Bob", item("status", "Open", "Done")),
                history("Alice", item("status", "Open", "In Progress"))
        );

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getWho());
    }

    @Test
    public void testFilterFieldsAndSimilarityWeight() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob",
                item("description", "abc", "axc"),
                item("description", "same", "same"),
                item("status", "Open", "Done")
        ));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees, new String[]{"description"}, true, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getWeight() > 0);
        assertTrue(result.get(0).getWeight() < 1);
    }

    @Test
    public void testFilterFieldsWithDividerOverride() throws Exception {
        setDividerOverride("status", "2");
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees, new String[]{"status"}, false, false, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(1.5, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testFilterFieldsWithDefaultDivider() throws Exception {
        // guard against environment-provided values: use a field with no configured divider
        // and force an empty default divider so the built-in fallback (1d) applies
        Map<String, String> overrides = new HashMap<>();
        overrides.put("TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT", "");
        PropertyReader.setOverrides(overrides);

        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob", item("summary", "one", "two")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees, new String[]{"summary"}, false, false, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(2.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testFilterFieldsWithoutDivider() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees, new String[]{"status"}, false, false, false, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(1.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testNoFilterFieldsAppliesDividerAsFraction() throws Exception {
        setDividerOverride("status", "2");
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(0.5, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testNullFromToValuesAreNormalized() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob", item("status", null, null)));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1", result.get(0).getKey());
    }

    @Test
    public void testIncludeInitialCreatesBaselinesAndSkipsFieldsWithInitialHistory() throws Exception {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT", "");
        PropertyReader.setOverrides(overrides);

        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getTicketDescription()).thenReturn("description text");
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");
        // a history item that sets description from blank marks it as having initial history
        histories(history("Bob", item("description", "", "initial text")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees,
                new String[]{"description", "summary"}, false, false, true, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // 1 regular change (description set by Bob) + 1 initial baseline for summary
        assertEquals(2, result.size());
        KeyTime initial = null;
        for (KeyTime keyTime : result) {
            if (keyTime.getKey().equals("TEST-1:initial:summary")) {
                initial = keyTime;
            }
        }
        assertNotNull(initial);
        assertEquals("Alice", initial.getWho());
        assertEquals(2.0, initial.getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialDescriptionBranch() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getTicketDescription()).thenReturn("description text");

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees,
                new String[]{"description"}, false, false, true, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:initial:description", result.get(0).getKey());
        assertEquals(1.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialSkipsBlankInitialValues() throws Exception {
        when(mockTicket.getCreator()).thenReturn(null);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("   ");

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees,
                new String[]{"summary"}, false, false, true, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testIncludeInitialWithSimilarityKeepsZeroWeight() throws Exception {
        when(mockTicket.getCreator()).thenReturn(null);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees,
                new String[]{"summary"}, true, false, true, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:initial:summary", result.get(0).getKey());
        assertEquals(IEmployees.UNKNOWN, result.get(0).getWho());
        assertEquals(0.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialBlankChangeValueVariants() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("summary text");
        when(mockTicket.getFieldValueAsString("priority")).thenReturn("High");
        // "null" and "none" from-values count as blank: fields get initial history and no baseline
        histories(history("Bob",
                item("summary", "null", "something"),
                item("priority", "none", "Low"),
                item("labels", "existing", "updated")
        ));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees,
                new String[]{"summary", "priority"}, false, false, true, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // both fields have initial history, so no baselines; 2 regular changes (summary, priority)
        assertEquals(2, result.size());
        for (KeyTime keyTime : result) {
            assertTrue(!keyTime.getKey().contains(":initial:"));
        }
    }

    @Test
    public void testIncludeInitialWithNoFilterFieldsProducesNoBaselines() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob", item("status", "Open", "Done")));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(null, mockEmployees, null, false, false, true, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
    }

    @Test
    public void testGetRequiredExtraFields() {
        TicketFieldsChangesRule withoutFilters = new TicketFieldsChangesRule("custom", mockEmployees);
        assertEquals(Collections.singletonList("changelog"), withoutFilters.getRequiredExtraFields());

        TicketFieldsChangesRule withFilters = new TicketFieldsChangesRule("custom", mockEmployees,
                new String[]{"description", "summary"}, false, false);
        assertEquals(Arrays.asList("changelog", "description", "summary"), withFilters.getRequiredExtraFields());
    }

    @Test
    public void testFieldNotMatchingFilterIsSkipped() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        histories(history("Bob",
                item("status", "Open", "Done"),
                item(null, "a", "b")
        ));

        TicketFieldsChangesRule rule = new TicketFieldsChangesRule(mockEmployees, new String[]{"summary"}, false, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }
}
