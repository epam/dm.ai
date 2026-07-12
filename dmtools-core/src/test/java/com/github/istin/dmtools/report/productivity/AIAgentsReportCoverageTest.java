// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.AIProvider;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.metrics.rules.CommentsWrittenRule;
import com.github.istin.dmtools.report.HtmlInjection;
import com.github.istin.dmtools.report.ProductivityTools;
import com.github.istin.dmtools.report.freemarker.DevProductivityReport;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class AIAgentsReportCoverageTest {

    private AIAgentsReport report;
    private AIAgentsReportParams params;

    @Before
    public void setUp() {
        report = new AIAgentsReport();
        params = mock(AIAgentsReportParams.class);
        when(params.isWeight()).thenReturn(false);
        when(params.getEmployees()).thenReturn(null);
    }

    @After
    public void tearDown() {
        AIProvider.reset();
    }

    @Test
    public void testExtractRequestFound() {
        String text = "Hello there is response on your request: Please review this story AI Response is: done";
        assertEquals("Please review this story", AIAgentsReport.extractRequest(text));
    }

    @Test
    public void testExtractRequestMultiline() {
        String text = "there is response on your request:\nline one\nline two\nAI Response is: result";
        assertEquals("line one\nline two", AIAgentsReport.extractRequest(text));
    }

    @Test
    public void testExtractRequestNotFound() {
        assertEquals("", AIAgentsReport.extractRequest("no request pattern here"));
    }

    @Test
    public void testGetAiReturnsNull() {
        assertNull(report.getAi());
    }

    @Test
    public void testGenerateListOfMetrics() throws IOException {
        List<Metric> metrics = report.generateListOfMetrics(params);

        assertNotNull(metrics);
        assertEquals(5, metrics.size());
        assertEquals("Created Tests", metrics.get(0).getName());
        assertEquals("Use Amount Of BA Expert", metrics.get(1).getName());
        assertEquals("Use Amount Of AI Expert", metrics.get(2).getName());
        assertEquals("Use Amount TCs", metrics.get(3).getName());
        assertEquals("Test Ticket Linked", metrics.get(4).getName());
    }

    @Test
    public void testGenerateListOfMetricsWithWeight() throws IOException {
        when(params.isWeight()).thenReturn(true);
        List<Metric> metrics = report.generateListOfMetrics(params);

        assertEquals(5, metrics.size());
        for (Metric metric : metrics) {
            assertTrue(metric.isWeight());
        }
    }

    @Test
    public void testNumberOfAttachments() {
        List<Metric> metrics = new ArrayList<>();
        report.numberOfAttachments(params, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Number of Attachments", metrics.get(0).getName());
    }

    @Test
    public void testCreatedTests() {
        List<Metric> metrics = new ArrayList<>();
        report.createdTests(params, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Created Tests", metrics.get(0).getName());
    }

    @Test
    public void testTicketLinksChanged() {
        List<Metric> metrics = new ArrayList<>();
        report.ticketLinksChanged(params, metrics);

        assertEquals(1, metrics.size());
        assertEquals("Test Ticket Linked", metrics.get(0).getName());
    }

    @Test
    public void testCommentsRuleCreateKeyTimeWithNotifierId() throws Exception {
        CommentsWrittenRule rule = getCommentsRule("Use Amount Of AI Expert");

        BasicJiraClient jira = mock(BasicJiraClient.class);
        IUser user = mock(IUser.class);
        when(user.getFullName()).thenReturn("John Doe");
        when(jira.performProfile("user1")).thenReturn(user);

        try (MockedStatic<BasicJiraClient> mockedStatic = mockStatic(BasicJiraClient.class)) {
            mockedStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            ITicket ticket = mockTicket("PROJ-1");
            IComment comment = mockComment(
                    "[~accountid:user1] there is response on your request: Help me with tests AI Response is: ok",
                    new Date());

            KeyTime keyTime = rule.createKetTimeBasedOnComment(ticket, comment, "author");

            assertNotNull(keyTime);
            assertEquals("Other AI Responses_PROJ-1 John Doe", keyTime.getKey());
            assertEquals("author", keyTime.getWho());
        }
    }

    @Test
    public void testCommentsRuleCreateKeyTimeBAExpertPatternName() throws Exception {
        CommentsWrittenRule rule = getCommentsRule("Use Amount Of BA Expert");

        BasicJiraClient jira = mock(BasicJiraClient.class);
        IUser user = mock(IUser.class);
        when(user.getFullName()).thenReturn("Jane Smith");
        when(jira.performProfile("user2")).thenReturn(user);

        try (MockedStatic<BasicJiraClient> mockedStatic = mockStatic(BasicJiraClient.class)) {
            mockedStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            KeyTime keyTime = rule.createKetTimeBasedOnComment(mockTicket("PROJ-2"),
                    mockComment("[~accountid:user2] some comment", new Date()), "author");

            assertNotNull(keyTime);
            assertEquals("Feature Review AI Responses_PROJ-2 Jane Smith", keyTime.getKey());
        }
    }

    @Test
    public void testCommentsRuleCreateKeyTimeWithoutNotifierId() throws Exception {
        CommentsWrittenRule rule = getCommentsRule("Use Amount Of AI Expert");

        assertNull(rule.createKetTimeBasedOnComment(mockTicket("PROJ-1"),
                mockComment("plain comment without tag", new Date()), "author"));
        assertNull(rule.createKetTimeBasedOnComment(mockTicket("PROJ-1"),
                mockComment("[~accountid:null] tagged as null", new Date()), "author"));
        assertNull(rule.createKetTimeBasedOnComment(mockTicket("PROJ-1"),
                mockComment("", new Date()), "author"));
    }

    @Test
    public void testCommentsRuleCreateKeyTimeProfileLookupFails() throws Exception {
        CommentsWrittenRule rule = getCommentsRule("Use Amount Of AI Expert");

        try (MockedStatic<BasicJiraClient> mockedStatic = mockStatic(BasicJiraClient.class)) {
            mockedStatic.when(BasicJiraClient::getInstance).thenThrow(new IOException("boom"));

            ITicket ticket = mockTicket("PROJ-1");
            IComment comment = mockComment("[~accountid:user1] comment", new Date());

            assertThrows(RuntimeException.class,
                    () -> rule.createKetTimeBasedOnComment(ticket, comment, "author"));
        }
    }

    @Test
    public void testTicketLinksRuleSkipsNonTestCaseTickets() throws Exception {
        TrackerRule rule = getRule("Test Ticket Linked");

        ITicket ticket = mock(ITicket.class);
        when(ticket.getIssueType()).thenReturn("Bug");

        List<KeyTime> result = rule.check(mock(TrackerClient.class), ticket);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testTicketLinksRuleChecksTestCaseTickets() throws Exception {
        TrackerRule rule = getRule("Test Ticket Linked");

        ITicket ticket = mock(ITicket.class);
        when(ticket.getIssueType()).thenReturn("Test Case");
        when(ticket.getKey()).thenReturn("PROJ-3");
        when(ticket.getCreator()).thenReturn(null);

        IChangelog changelog = mock(IChangelog.class);
        when(changelog.getHistories()).thenReturn(Collections.emptyList());

        TrackerClient trackerClient = mock(TrackerClient.class);
        when(trackerClient.getChangeLog("PROJ-3", ticket)).thenReturn(changelog);

        List<KeyTime> result = rule.check(trackerClient, ticket);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testGenerateAnalyticsHtmlWithData() throws Exception {
        BasicJiraClient jira = mock(BasicJiraClient.class);
        IUser user1 = mock(IUser.class);
        when(user1.getFullName()).thenReturn("John Doe");
        IUser user2 = mock(IUser.class);
        when(user2.getFullName()).thenReturn("Jane Smith");
        when(jira.performProfile("user1")).thenReturn(user1);
        when(jira.performProfile("user2")).thenReturn(user2);

        AI ai = mock(AI.class);
        when(ai.chat(anyString())).thenReturn("<b>Summary of requests</b>");
        AIProvider.setCustomAI(ai);

        try (MockedStatic<BasicJiraClient> mockedStatic = mockStatic(BasicJiraClient.class)) {
            mockedStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            CommentsWrittenRule aiRule = getCommentsRule("Use Amount Of AI Expert");
            CommentsWrittenRule baRule = getCommentsRule("Use Amount Of BA Expert");

            Date now = new Date();
            Date longAgo = new Date(1000000L);

            // user1: two recent interactions with the AI expert pattern
            aiRule.createKetTimeBasedOnComment(mockTicket("PROJ-1"),
                    mockComment("[~accountid:user1] there is response on your request: First <request> & \"quotes\" AI Response is: a", now), "a");
            aiRule.createKetTimeBasedOnComment(mockTicket("PROJ-1"),
                    mockComment("[~accountid:user1] there is response on your request: Second request AI Response is: b", now), "a");
            // user2: one recent interaction with the AI expert pattern
            aiRule.createKetTimeBasedOnComment(mockTicket("PROJ-2"),
                    mockComment("[~accountid:user2] there is response on your request: Third request AI Response is: c", now), "a");
            // user2: one old interaction with the BA expert pattern (filtered out of the recent section)
            baRule.createKetTimeBasedOnComment(mockTicket("PROJ-3"),
                    mockComment("[~accountid:user2] old comment", longAgo), "a");

            String html = invokeGenerateAnalyticsHtml(jira);

            assertTrue(html.contains("All Unique Users"));
            assertTrue(html.contains("John Doe"));
            assertTrue(html.contains("Jane Smith"));
            assertTrue(html.contains("Users per Comment Pattern"));
            assertTrue(html.contains("Other AI Responses"));
            assertTrue(html.contains("Feature Review AI Responses"));
            assertTrue(html.contains("Interactions per User per Pattern"));
            assertTrue(html.contains("Top Users (Last 2 Weeks)"));
            assertTrue(html.contains("<b>Summary of requests</b>"));
            assertTrue(html.contains("Show All Requests (3)"));
            // request text is escaped in the requests section
            assertTrue(html.contains("First &lt;request&gt; &amp; &quot;quotes&quot;"));
        }
    }

    @Test
    public void testGenerateAnalyticsHtmlFallsBackToUserIdOnProfileError() throws Exception {
        BasicJiraClient jira = mock(BasicJiraClient.class);
        when(jira.performProfile(anyString())).thenThrow(new IOException("no profile"));

        try (MockedStatic<BasicJiraClient> mockedStatic = mockStatic(BasicJiraClient.class)) {
            mockedStatic.when(BasicJiraClient::getInstance).thenReturn(jira);

            CommentsWrittenRule baRule = getCommentsRule("Use Amount Of BA Expert");
            baRule.createKetTimeBasedOnComment(mockTicket("PROJ-1"),
                    mockComment("[~accountid:user9] comment", new Date(1000000L)), "a");

            String html = invokeGenerateAnalyticsHtml(jira);

            assertTrue(html.contains("user9"));
            // no recent interactions and no requests: those sections are not rendered
            assertFalse(html.contains("Top Users (Last 2 Weeks)"));
            assertFalse(html.contains("Show All Requests"));
        }
    }

    @Test
    public void testGenerateAnalyticsHtmlWithEmptyData() throws Exception {
        BasicJiraClient jira = mock(BasicJiraClient.class);

        String html = invokeGenerateAnalyticsHtml(jira);

        assertTrue(html.contains("All Unique Users"));
        assertTrue(html.contains("(0)"));
        assertFalse(html.contains("Top Users (Last 2 Weeks)"));
        assertFalse(html.contains("Show All Requests"));
    }

    @Test
    public void testRunJob() throws Exception {
        File generatedFile = File.createTempFile("aiAgentsReport", ".html");
        generatedFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(generatedFile)) {
            writer.write("<html>report content</html>");
        }

        when(params.getStartDate()).thenReturn("01.01.2024");
        when(params.getReportName()).thenReturn("AI Agents");
        when(params.isWeight()).thenReturn(true);
        when(params.isDarkMode()).thenReturn(true);
        when(params.getFormula()).thenReturn("formula");
        when(params.getInputJQL()).thenReturn("project = PROJ");
        when(params.getIgnoreTicketPrefixes()).thenReturn(new String[0]);

        BasicJiraClient jira = mock(BasicJiraClient.class);
        AtomicReference<HtmlInjection> injectionRef = new AtomicReference<>();

        try (MockedStatic<BasicJiraClient> jiraStatic = mockStatic(BasicJiraClient.class);
             MockedStatic<ProductivityTools> toolsStatic = mockStatic(ProductivityTools.class)) {
            jiraStatic.when(BasicJiraClient::getInstance).thenReturn(jira);
            toolsStatic.when(() -> ProductivityTools.generate(
                            any(TrackerClient.class), any(), anyString(), anyString(), anyString(),
                            anyList(), any(Release.Style.class), any(Employees.class),
                            any(String[].class), any(HtmlInjection.class)))
                    .thenAnswer(invocation -> {
                        injectionRef.set(invocation.getArgument(9));
                        return generatedFile;
                    });

            ResultItem resultItem = report.runJob(params);

            assertEquals("aiAgentsReport", resultItem.getKey());
            assertEquals("<html>report content</html>", resultItem.getResult());

            // exercise the HtmlInjection callback created inside runJob
            assertNotNull(injectionRef.get());
            DevProductivityReport productivityReport = new DevProductivityReport();
            String analyticsHtml = injectionRef.get().getHtmBeforeTimeline(productivityReport);
            assertTrue(analyticsHtml.contains("All Unique Users"));
            assertTrue(productivityReport.getIsDarkMode());

            // cover the exception-wrapping branch of the HtmlInjection callback:
            // with a registered user the profile lookup on the unstubbed mock returns null
            // and the callback wraps the failure in a RuntimeException
            getAllUsers().add("userX");
            assertThrows(RuntimeException.class,
                    () -> injectionRef.get().getHtmBeforeTimeline(new DevProductivityReport()));
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Set<String> getAllUsers() throws Exception {
        java.lang.reflect.Field field = AIAgentsReport.class.getDeclaredField("allUsers");
        field.setAccessible(true);
        return (java.util.Set<String>) field.get(report);
    }

    private CommentsWrittenRule getCommentsRule(String metricName) throws IOException {
        return (CommentsWrittenRule) getRule(metricName);
    }

    private TrackerRule getRule(String metricName) throws IOException {
        for (Metric metric : report.generateListOfMetrics(params)) {
            if (metric.getName().equals(metricName)) {
                return metric.getRule();
            }
        }
        throw new IllegalStateException("Metric not found: " + metricName);
    }

    private String invokeGenerateAnalyticsHtml(BasicJiraClient jira) throws Exception {
        Method method = AIAgentsReport.class.getDeclaredMethod("generateAnalyticsHtml", BasicJiraClient.class);
        method.setAccessible(true);
        return (String) method.invoke(report, jira);
    }

    private ITicket mockTicket(String key) throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.getKey()).thenReturn(key);
        return ticket;
    }

    private IComment mockComment(String body, Date created) {
        IComment comment = mock(IComment.class);
        when(comment.getBody()).thenReturn(body);
        when(comment.getCreated()).thenReturn(created);
        return comment;
    }
}
