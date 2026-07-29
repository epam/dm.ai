// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.timeinstatus;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TimeInStatusJobCoverageTest {

    private TrackerClient<ITicket> trackerClientMock;
    private BasicConfluence confluenceMock;

    @Before
    public void setUp() {
        trackerClientMock = mock(TrackerClient.class);
        confluenceMock = mock(BasicConfluence.class);
        when(trackerClientMock.getDefaultStatusField()).thenReturn("status");
        when(trackerClientMock.getDefaultQueryFields()).thenReturn(new String[]{"summary"});
    }

    private ITicket mockTicket(String key, Date created, JSONObject fields, List<IHistory> histories) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn(key);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.getTicketTitle()).thenReturn("Title of " + key);
        when(ticket.getTicketLink()).thenReturn("https://tracker.example.com/browse/" + key);
        when(ticket.getCreated()).thenReturn(created);
        when(ticket.getFieldsAsJSON()).thenReturn(fields);
        IChangelog changelog = mock(IChangelog.class);
        List historyList = histories;
        when(changelog.getHistories()).thenReturn(historyList);
        when(trackerClientMock.getChangeLog(eq(key), any(ITicket.class))).thenReturn(changelog);
        return ticket;
    }

    private IHistory mockStatusHistory(String toStatus, Calendar created) {
        IHistoryItem historyItem = mock(IHistoryItem.class);
        when(historyItem.getField()).thenReturn("status");
        when(historyItem.getToAsString()).thenReturn(toStatus);
        IHistory history = mock(IHistory.class);
        List historyItems = Collections.singletonList(historyItem);
        when(history.getHistoryItems()).thenReturn(historyItems);
        when(history.getCreated()).thenReturn(created);
        return history;
    }

    private void mockConfluencePages() throws Exception {
        Content rootPage = mock(Content.class);
        when(rootPage.getId()).thenReturn("100");
        Content endPage = mock(Content.class);
        when(endPage.getId()).thenReturn("200");
        when(confluenceMock.findContent(anyString())).thenReturn(rootPage);
        when(confluenceMock.findOrCreate(anyString(), anyString(), anyString())).thenReturn(endPage);
    }

    @Test
    public void testMainWithScopeExtraFieldsAndPublishing() throws Exception {
        long now = System.currentTimeMillis();
        Calendar t1 = Calendar.getInstance();
        t1.setTimeInMillis(now - 8 * 24L * 3600 * 1000);
        Calendar t2 = Calendar.getInstance();
        t2.setTimeInMillis(now - 6 * 24L * 3600 * 1000);

        JSONObject fields1 = new JSONObject();
        fields1.put("cfString", "plain value");
        fields1.put("cfInt", 7);
        fields1.put("cfObj", new JSONObject().put("value", "Option A"));
        fields1.put("cfOther", Arrays.asList(1, 2));

        ITicket ticket1 = mockTicket("KEY-1", new Date(now - 10 * 24L * 3600 * 1000), fields1,
                Arrays.asList(mockStatusHistory("in progress", t1), mockStatusHistory("draft", t2)));
        ITicket ticket2 = mockTicket("KEY-2", new Date(now - 5 * 24L * 3600 * 1000), new JSONObject(),
                new ArrayList<>());

        mockConfluencePages();

        TimeInStatusJob.Params params = new TimeInStatusJob.Params(
                "Root Page", "Prefix", "Test Scope Report",
                Arrays.asList(ticket1, ticket2), "draft", new String[]{"draft", "in progress"})
                .setExtraFields("cfString", "cfInt", "cfObj", "cfOther")
                .setExtraFieldsNames("String Field", "Int Field", "Object Field", "Other Field")
                .setFinalStatuses("done");

        TimeInStatusJob.main(trackerClientMock, confluenceMock, params);

        verify(trackerClientMock, times(2)).getChangeLog(anyString(), any(ITicket.class));
        // publish is called twice: chart and raw data
        verify(confluenceMock, times(2)).findContent("Root Page");
        verify(confluenceMock, times(2)).findOrCreate(anyString(), eq("100"), anyString());
        verify(confluenceMock, times(2)).updatePage(eq("200"), anyString(), eq("100"), anyString());
        assertTrue(new File("reports/Test_Scope_Report.html").exists());
        assertTrue(new File("reports/Test_Scope_Report_raw.html").exists());
    }

    @Test
    public void testMainWithFilter() throws Exception {
        long now = System.currentTimeMillis();
        ITicket ticket = mockTicket("KEY-10", new Date(now - 3 * 24L * 3600 * 1000), new JSONObject(),
                new ArrayList<>());

        doAnswer(invocation -> {
            JiraClient.Performer performer = invocation.getArgument(0);
            performer.perform(ticket);
            return null;
        }).when(trackerClientMock).searchAndPerform(any(JiraClient.Performer.class), eq("project = TEST"), any(String[].class));

        TimeInStatusJob.Params params = new TimeInStatusJob.Params(
                "Test Filter Report", "project = TEST", "open", new String[]{"open"});

        TimeInStatusJob.main(trackerClientMock, confluenceMock, params);

        verify(trackerClientMock).searchAndPerform(any(JiraClient.Performer.class), eq("project = TEST"), any(String[].class));
        verify(trackerClientMock).getChangeLog(eq("KEY-10"), any(ITicket.class));
        verifyNoInteractions(confluenceMock);
        assertTrue(new File("reports/Test_Filter_Report.html").exists());
    }

    @Test
    public void testPublish() throws Exception {
        mockConfluencePages();
        File reportFile = File.createTempFile("time_in_status_test", ".html");
        reportFile.deleteOnExit();
        java.nio.file.Files.write(reportFile.toPath(), "<html>body</html>".getBytes("UTF-8"));

        TimeInStatusJob.publish("Root Page", "Prefix", " Chart", reportFile, confluenceMock);

        verify(confluenceMock).findContent("Root Page");
        verify(confluenceMock).findOrCreate(eq("Prefix Time In Status Chart"), eq("100"), contains("structured-macro"));
        verify(confluenceMock).updatePage(eq("200"), eq("Prefix Time In Status Chart"), eq("100"), contains("body"));
    }

    @Test
    public void testParamsScopeConstructorAndGetters() {
        List<ITicket> scope = Collections.singletonList(mock(ITicket.class));
        TimeInStatusJob.Params params = new TimeInStatusJob.Params(
                "root", "prefix", "report", scope, "draft", new String[]{"draft"});

        assertEquals("root", params.getRootPageName());
        assertEquals("prefix", params.getPrefix());
        assertEquals("report", params.getReportName());
        assertNull(params.getFilter());
        assertEquals("draft", params.getInitialStatus());
        assertArrayEquals(new String[]{"draft"}, params.getStatusesToCheck());
        assertSame(scope, params.getScope());

        assertSame(params, params.setExtraFields("a", "b"));
        assertArrayEquals(new String[]{"a", "b"}, params.getExtraFields());
        assertSame(params, params.setExtraFieldsNames("A", "B"));
        assertArrayEquals(new String[]{"A", "B"}, params.getExtraFieldsNames());
        assertSame(params, params.setFinalStatuses("done"));
        assertArrayEquals(new String[]{"done"}, params.getFinalStatuses());
    }

    @Test
    public void testParamsFilterConstructors() {
        TimeInStatusJob.Params params = new TimeInStatusJob.Params(
                "report", "filter jql", "open", new String[]{"open"});
        assertNull(params.getRootPageName());
        assertNull(params.getPrefix());
        assertEquals("report", params.getReportName());
        assertEquals("filter jql", params.getFilter());
        assertEquals("open", params.getInitialStatus());
        assertArrayEquals(new String[]{"open"}, params.getStatusesToCheck());
        assertNull(params.getScope());

        TimeInStatusJob.Params paramsWithPage = new TimeInStatusJob.Params(
                "root", "prefix", "report", "filter jql", "open", new String[]{"open"});
        assertEquals("root", paramsWithPage.getRootPageName());
        assertEquals("prefix", paramsWithPage.getPrefix());
        assertEquals("filter jql", paramsWithPage.getFilter());
    }
}
