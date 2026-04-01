// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.metrics.FigmaCommentMetric;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.VacationMetric;
import com.github.istin.dmtools.team.Employees;
import org.apache.poi.poifs.property.Parent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProductivityUtilsTest {

    private Employees employees;
    private FigmaClient figmaClient;
    private ITicket ticket;
    private ProductivityJobParams productivityJobParams;

    @Before
    public void setUp() {
        employees = mock(Employees.class);
        figmaClient = mock(FigmaClient.class);
        ticket = mock(ITicket.class);
        productivityJobParams = mock(ProductivityJobParams.class);
    }

    @Test
    public void testVacationDays() {
        List<Metric> metrics = new ArrayList<>();
        List<Metric> result = ProductivityUtils.vacationDays(metrics, employees);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof VacationMetric);
    }

    @Test
    public void testFigmaComments() {
        List<Metric> metrics = new ArrayList<>();
        String[] files = {"file1", "file2"};
        List<Metric> result = ProductivityUtils.figmaComments(metrics, employees, figmaClient, files);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof FigmaCommentMetric);
    }

    @Test
    public void testIsSubTaskLinkedToStory() {
        when(ticket.getFields()).thenReturn(mock(Fields.class));
        when(ticket.getFields().getIssueType()).thenReturn(mock(IssueType.class));
        when(ticket.getFields().getIssueType().getName()).thenReturn("Sub-task");
        when(ticket.getFields().getParent()).thenReturn(mock(Ticket.class));
        when(ticket.getFields().getParent().getIssueType()).thenReturn("Story");

        boolean result = ProductivityUtils.isSubTaskLinkedToStory(productivityJobParams, ticket);
        assertTrue(result);
    }

    @Test
    public void testIsSubTaskLinkedToBug() {
        when(ticket.getFields()).thenReturn(mock(Fields.class));
        when(ticket.getFields().getIssueType()).thenReturn(mock(IssueType.class));
        when(ticket.getFields().getIssueType().getName()).thenReturn("Sub-task");
        when(ticket.getFields().getParent()).thenReturn(mock(Ticket.class));
        when(ticket.getFields().getParent().getIssueType()).thenReturn("Bug");

        boolean result = ProductivityUtils.isSubTaskLinkedToBug(ticket);
        assertTrue(result);
    }

    @Test
    public void testIsIgnoreTask() throws IOException {
        String[] ignorePrefixes = {"IGNORE"};
        when(ticket.getTicketTitle()).thenReturn("IGNORE-123 Task");

        boolean result = ProductivityUtils.isIgnoreTask(ignorePrefixes, ticket);
        assertTrue(result);
    }
}