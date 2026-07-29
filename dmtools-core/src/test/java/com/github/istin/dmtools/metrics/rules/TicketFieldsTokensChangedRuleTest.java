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
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class TicketFieldsTokensChangedRuleTest {

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

    private Ticket jiraTicketWithReporter(String creatorName) {
        Ticket jiraTicket = Mockito.mock(Ticket.class);
        Fields fields = Mockito.mock(Fields.class);
        Assignee creator = Mockito.mock(Assignee.class);
        Assignee reporter = Mockito.mock(Assignee.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getCreated()).thenReturn(new Date(1_600_000_000_000L));
        when(jiraTicket.getFields()).thenReturn(fields);
        when(fields.getCreator()).thenReturn(creator);
        when(fields.getReporter()).thenReturn(reporter);
        when(creator.getDisplayName()).thenReturn(creatorName);
        when(reporter.getDisplayName()).thenReturn("Someone Else");
        return jiraTicket;
    }

    // --- modes ---

    @Test
    public void testMixedModeDefault() throws Exception {
        histories(history("Bob", item("description", "", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
        assertTrue(result.get(0).getKey().startsWith("TEST-1:description:"));
        // |9 - 0| * (1 - similarity("", "alpha")) = 9 * (1 - 0)
        assertEquals(9.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testMixedModeNullAndEmptyModeFallback() throws Exception {
        histories(history("Bob", item("description", "", "alpha")));

        TicketFieldsTokensChangedRule nullMode = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, false, null);
        assertEquals(9.0, nullMode.check(mockTrackerClient, mockTicket).get(0).getWeight(), 0.0001);

        TicketFieldsTokensChangedRule emptyMode = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, false, "", null);
        assertEquals(9.0, emptyMode.check(mockTrackerClient, mockTicket).get(0).getWeight(), 0.0001);
    }

    @Test
    public void testMixedModeWithSimilarity() throws Exception {
        histories(history("Bob", item("description", "alpha", "alpha beta")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, false, "mixed", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        // |11 - 9| * (1 - similarity) where 0 < similarity < 1
        assertTrue(result.get(0).getWeight() > 0);
        assertTrue(result.get(0).getWeight() < 2.0);
    }

    @Test
    public void testDeltaMode() throws Exception {
        histories(history("Bob",
                item("description", "alpha", "alpha beta"),
                item("summary", "alpha beta", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(2, result.size());
        assertEquals(2.0, result.get(0).getWeight(), 0.0001);
        assertEquals(2.0, result.get(1).getWeight(), 0.0001);
    }

    @Test
    public void testAddedMode() throws Exception {
        histories(history("Bob",
                item("description", "alpha", "alpha beta"),
                item("summary", "alpha beta", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "added", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // shrinking change produces zero weight and is skipped
        assertEquals(1, result.size());
        assertTrue(result.get(0).getKey().startsWith("TEST-1:description:"));
        assertEquals(2.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testRemovedMode() throws Exception {
        histories(history("Bob",
                item("description", "alpha", "alpha beta"),
                item("summary", "alpha beta", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "removed", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getKey().startsWith("TEST-1:summary:"));
        assertEquals(2.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testRewrittenMode() throws Exception {
        histories(history("Bob", item("description", "aaaa", "bbbb")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "rewritten", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        // max(9, 9) * (1 - 0) — no common characters, similarity is 0
        assertEquals(9.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testContributionMode() throws Exception {
        histories(history("Bob", item("description", "aaaa", "bbbb cccc")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "contribution", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        // max(11 - 9, 0) + min(9, 11) * (1 - 0) = 2 + 9
        assertEquals(11.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testUnknownModeFallsBackToMixed() throws Exception {
        histories(history("Bob", item("description", "", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "unknown-mode", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(9.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testZeroWeightChangeIsSkipped() throws Exception {
        histories(history("Bob", item("description", "same text", "same text")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testNullFromToValuesAreNormalized() throws Exception {
        histories(history("Bob", item("description", null, null)));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // both normalized to "" -> 0 tokens -> zero weight -> skipped
        assertEquals(0, result.size());
    }

    // --- filtering ---

    @Test
    public void testSkipsAuthorNotInEmployeesAndNullAuthor() throws Exception {
        histories(
                history("Charlie", item("description", "", "alpha")),
                history(null, item("description", "", "alpha"))
        );

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees, null, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testFieldNotMatchingFilterIsSkipped() throws Exception {
        histories(history("Bob",
                item("status", "Open", "Done"),
                item(null, "a", "b")
        ));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary"}, false, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testNullFilterFieldsMatchesAllFields() throws Exception {
        histories(history("Bob",
                item(null, "", "alpha"),
                item("summary", "", "alpha")
        ));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(2, result.size());
    }

    @Test
    public void testAuthorNameIsTransformed() throws Exception {
        when(mockEmployees.transformName("Bob")).thenReturn("Robert");
        histories(history("Bob", item("description", "", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees, null, false);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("Robert", result.get(0).getWho());
    }

    // --- creator filter modes (require a Jira Ticket so "who" is resolved) ---

    @Test
    public void testDefaultExcludesCreatorForJiraTicket() throws Exception {
        Ticket jiraTicket = jiraTicketWithReporter("Alice");
        histories(
                history("Bob", item("description", "", "alpha")),
                history("Alice", item("description", "", "alpha"))
        );

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees, null, false);
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testCollectionIfByCreatorIncludesOnlyCreator() throws Exception {
        Ticket jiraTicket = jiraTicketWithReporter("Alice");
        histories(
                history("Bob", item("description", "", "alpha")),
                history("Alice", item("description", "", "alpha"))
        );

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees, null, true);
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getWho());
    }

    @Test
    public void testCreatorFilterModeAll() throws Exception {
        Ticket jiraTicket = jiraTicketWithReporter("Alice");
        histories(
                history("Bob", item("description", "", "alpha")),
                history("Alice", item("description", "", "alpha"))
        );

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", "all");
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(2, result.size());
    }

    @Test
    public void testCreatorFilterModeOnly() throws Exception {
        Ticket jiraTicket = jiraTicketWithReporter("Alice");
        histories(
                history("Bob", item("description", "", "alpha")),
                history("Alice", item("description", "", "alpha"))
        );

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", "only");
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getWho());
    }

    @Test
    public void testCreatorFilterModeExclude() throws Exception {
        Ticket jiraTicket = jiraTicketWithReporter("Alice");
        histories(
                history("Bob", item("description", "", "alpha")),
                history("Alice", item("description", "", "alpha"))
        );

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, false, "delta", "exclude");
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    @Test
    public void testWhoResolutionFailureIsIgnored() throws Exception {
        Ticket jiraTicket = Mockito.mock(Ticket.class);
        when(jiraTicket.getKey()).thenReturn("TEST-1");
        when(jiraTicket.getFields()).thenReturn(null);
        histories(history("Bob", item("description", "", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees, null, false);
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        // NPE inside whoReportedTheTicket is caught; who stays null and Bob is included
        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).getWho());
    }

    // --- includeInitial baselines ---

    @Test
    public void testIncludeInitialDescriptionBaseline() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getTicketDescription()).thenReturn("alpha beta");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, true, "mixed", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:initial:description", result.get(0).getKey());
        assertEquals("Alice", result.get(0).getWho());
        // computeWeight(0, 11, 0) = |11 - 0| * (1 - 0)
        assertEquals(11.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialOtherFieldBaseline() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("alpha");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary"}, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:initial:summary", result.get(0).getKey());
        assertEquals(9.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialSkipsFieldsWithInitialHistory() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("beta");
        // from blank -> non-blank marks the field as having initial history
        histories(history("Bob", item("summary", "", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary"}, false, true, "delta", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // only the regular change, no baseline for summary
        assertEquals(1, result.size());
        assertTrue(!result.get(0).getKey().contains(":initial:"));
    }

    @Test
    public void testIncludeInitialBlankChangeValueVariants() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("beta");
        when(mockTicket.getFieldValueAsString("priority")).thenReturn("High");
        // "null", "none" and whitespace-only from-values count as blank
        histories(history("Bob",
                item("summary", "null", "alpha beta"),
                item("priority", "none", "Low"),
                item("labels", "  ", "x")
        ));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary", "priority"}, false, true, "delta", null);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // 2 regular changes; both fields have initial history -> no baselines
        assertEquals(2, result.size());
        for (KeyTime keyTime : result) {
            assertTrue(!keyTime.getKey().contains(":initial:"));
        }
    }

    @Test
    public void testIncludeInitialSkipsBlankInitialValue() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("   ");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary"}, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(0, result.size());
    }

    @Test
    public void testIncludeInitialUnknownCreator() throws Exception {
        when(mockTicket.getCreator()).thenReturn(null);
        when(mockTicket.getTicketDescription()).thenReturn("alpha");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(IEmployees.UNKNOWN, result.get(0).getWho());
    }

    @Test
    public void testIncludeInitialCreatorNotInEmployees() throws Exception {
        IUser charlie = user("Charlie");
        when(mockTicket.getCreator()).thenReturn(charlie);
        when(mockTicket.getTicketDescription()).thenReturn("alpha");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(IEmployees.UNKNOWN, result.get(0).getWho());
    }

    @Test
    public void testIncludeInitialCreatorThrowsFallsBackToUnknown() throws Exception {
        when(mockTicket.getCreator()).thenThrow(new RuntimeException("boom"));
        when(mockTicket.getTicketDescription()).thenReturn("alpha");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals(IEmployees.UNKNOWN, result.get(0).getWho());
    }

    @Test
    public void testIncludeInitialWithNoFilterFieldsProducesNoBaselines() throws Exception {
        histories(history("Bob", item("description", "", "alpha")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                null, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertTrue(!result.get(0).getKey().contains(":initial:"));
    }

    @Test
    public void testIncludeInitialExtendedFetchWhenFieldNotPreFetched() throws Exception {
        ITicket extendedTicket = Mockito.mock(ITicket.class);
        IUser alice = user("Alice");
        when(extendedTicket.getCreator()).thenReturn(alice);
        when(extendedTicket.getCreated()).thenReturn(new Date(1_600_000_000_000L));
        when(extendedTicket.getFieldValueAsString("summary")).thenReturn("alpha beta");
        when(mockTicket.getFieldValueAsString("summary")).thenReturn(null);
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"*"});
        when(mockTrackerClient.performTicket(anyString(), any(String[].class))).thenReturn(extendedTicket);

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary"}, false, true) {
            @Override
            public List<String> getRequiredExtraFields() {
                return Collections.singletonList("changelog");
            }
        };
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:initial:summary", result.get(0).getKey());
        assertEquals("Alice", result.get(0).getWho());
        assertEquals(11.0, result.get(0).getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialExtendedFetchFailureIsIgnored() throws Exception {
        when(mockTicket.getFieldValueAsString("summary")).thenReturn(null);
        when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{"*"});
        when(mockTrackerClient.performTicket(anyString(), any(String[].class)))
                .thenThrow(new IOException("boom"));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"summary"}, false, true) {
            @Override
            public List<String> getRequiredExtraFields() {
                return Collections.singletonList("changelog");
            }
        };
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // initial value stays null on the original ticket -> no baseline
        assertEquals(0, result.size());
    }

    @Test
    public void testIncludeInitialCombinesBaselinesAndChanges() throws Exception {
        IUser alice = user("Alice");
        when(mockTicket.getCreator()).thenReturn(alice);
        when(mockTicket.getTicketDescription()).thenReturn("alpha beta");
        when(mockTicket.getFieldValueAsString("summary")).thenReturn("alpha");
        histories(history("Bob", item("description", "", "gamma")));

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description", "summary"}, false, true, "delta", "all");
        List<KeyTime> result = rule.check(mockTrackerClient, mockTicket);

        // 1 regular change (description) + 1 baseline (summary); description has initial history
        assertEquals(2, result.size());
        KeyTime initial = null;
        for (KeyTime keyTime : result) {
            if (keyTime.getKey().equals("TEST-1:initial:summary")) {
                initial = keyTime;
            }
        }
        assertNotNull(initial);
        assertEquals("Alice", initial.getWho());
        assertEquals(9.0, initial.getWeight(), 0.0001);
    }

    @Test
    public void testIncludeInitialFallsBackToReporterAsBaselineWho() throws Exception {
        Ticket jiraTicket = jiraTicketWithReporter("Alice");
        when(jiraTicket.getCreator()).thenReturn(null);
        when(jiraTicket.getTicketDescription()).thenReturn("alpha");

        TicketFieldsTokensChangedRule rule = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description"}, false, true);
        List<KeyTime> result = rule.check(mockTrackerClient, jiraTicket);

        assertEquals(1, result.size());
        assertEquals("TEST-1:initial:description", result.get(0).getKey());
        // creator is null, so the resolved reporter ("Alice") becomes the baseline owner
        assertEquals("Alice", result.get(0).getWho());
    }

    // --- getRequiredExtraFields ---

    @Test
    public void testGetRequiredExtraFields() {
        TicketFieldsTokensChangedRule withoutFilters = new TicketFieldsTokensChangedRule(mockEmployees, null, false);
        assertEquals(Collections.singletonList("changelog"), withoutFilters.getRequiredExtraFields());

        TicketFieldsTokensChangedRule withFilters = new TicketFieldsTokensChangedRule(mockEmployees,
                new String[]{"description", "summary"}, false, false, "mixed", null);
        assertEquals(Arrays.asList("changelog", "description", "summary"), withFilters.getRequiredExtraFields());
    }
}
