// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.Sprint;
import com.github.istin.dmtools.report.model.KeyTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GenericReportTest {

    private GenericReport genericReport;

    @Before
    public void setUp() {
        genericReport = new GenericReport();
    }

    @Test
    public void testGetIsNotWiki() {
        assertTrue(genericReport.getIsNotWiki());
    }

    @Test
    public void testSetIsNotWiki() {
        genericReport.setIsNotWiki(false);
        assertFalse(genericReport.getIsNotWiki());
    }

    @Test
    public void testIsBySprints() {
        assertTrue(genericReport.isBySprints());
    }

    @Test
    public void testSetBySprints() {
        genericReport.setBySprints(false);
        assertFalse(genericReport.isBySprints());
    }

    @Test
    public void testIsByWeeks() {
        assertFalse(genericReport.isByWeeks());
    }

    @Test
    public void testSetByWeeks() {
        genericReport.setByWeeks(true);
        assertTrue(genericReport.isByWeeks());
    }

    @Test
    public void testGetIsChart() {
        assertFalse(genericReport.getIsChart());
    }

    @Test
    public void testSetChart() {
        genericReport.setChart(true);
        assertTrue(genericReport.getIsChart());
    }

    @Test
    public void testGetChartColors() {
        assertNull(genericReport.getChartColors());
    }

    @Test
    public void testSetChartColors() {
        String colors = "red,blue,green";
        genericReport.setChartColors(colors);
        assertEquals(colors, genericReport.getChartColors());
    }

    @Test
    public void testGetRows() {
        assertNotNull(genericReport.getRows());
        assertTrue(genericReport.getRows().isEmpty());
    }

    @Test
    public void testSetRows() {
        List<GenericRow> rows = new ArrayList<>();
        genericReport.setRows(rows);
        assertEquals(rows, genericReport.getRows());
    }

    @Test
    public void testGetBacklog() {
        assertNull(genericReport.getBacklog());
    }

    @Test
    public void testSetBacklog() {
        Backlog backlog = mock(Backlog.class);
        genericReport.setBacklog(backlog);
        assertEquals(backlog, genericReport.getBacklog());
    }

    @Test
    public void testGetRoadmap() {
        assertNull(genericReport.getRoadmap());
    }

    @Test
    public void testSetRoadmap() {
        Roadmap roadmap = mock(Roadmap.class);
        genericReport.setRoadmap(roadmap);
        assertEquals(roadmap, genericReport.getRoadmap());
    }

    @Test
    public void testGetReleases() {
        assertNull(genericReport.getReleases());
    }

    @Test
    public void testSetReleases() {
        List<Release> releases = new ArrayList<>();
        genericReport.setReleases(releases);
        assertEquals(releases, genericReport.getReleases());
    }

    @Test
    public void testAddTicket() {
        Ticket ticket = mock(Ticket.class);
        genericReport.addTicket(ticket);
        assertTrue(genericReport.getTickets().contains(ticket));
    }

    @Test
    public void testGetTickets() {
        assertNotNull(genericReport.getTickets());
        assertTrue(genericReport.getTickets().isEmpty());
    }

    @Test
    public void testAddQuestion() {
        Question question = mock(Question.class);
        genericReport.addQuestion(question);
        assertTrue(genericReport.getQuestions().contains(question));
    }

    @Test
    public void testGetQuestions() {
        assertNotNull(genericReport.getQuestions());
        assertTrue(genericReport.getQuestions().isEmpty());
    }

    @Test
    public void testIsShowReleases() {
        assertTrue(genericReport.isShowReleases());
    }

    @Test
    public void testSetShowReleases() {
        genericReport.setShowReleases(false);
        assertFalse(genericReport.isShowReleases());
    }

    @Test
    public void testGetBackendDeliverables() {
        assertNotNull(genericReport.getBackendDeliverables());
        assertTrue(genericReport.getBackendDeliverables().isEmpty());
    }

    @Test
    public void testSetBackendDeliverables() {
        List<Dependency> dependencies = new ArrayList<>();
        genericReport.setBackendDeliverables(dependencies);
        assertEquals(dependencies, genericReport.getBackendDeliverables());
    }

    @Test
    public void testGetProductClarifications() {
        assertNotNull(genericReport.getProductClarifications());
        assertTrue(genericReport.getProductClarifications().isEmpty());
    }

    @Test
    public void testSetProductClarifications() {
        List<Dependency> dependencies = new ArrayList<>();
        genericReport.setProductClarifications(dependencies);
        assertEquals(dependencies, genericReport.getProductClarifications());
    }

    @Test
    public void testGetSolutionBlockers() {
        assertNotNull(genericReport.getSolutionBlockers());
        assertTrue(genericReport.getSolutionBlockers().isEmpty());
    }

    @Test
    public void testSetSolutionBlockers() {
        List<Dependency> dependencies = new ArrayList<>();
        genericReport.setSolutionBlockers(dependencies);
        assertEquals(dependencies, genericReport.getSolutionBlockers());
    }

    @Test
    public void testGetRequirementsBlockers() {
        assertNotNull(genericReport.getRequirementsBlockers());
        assertTrue(genericReport.getRequirementsBlockers().isEmpty());
    }

    @Test
    public void testSetRequirementsBlockers() {
        List<Dependency> dependencies = new ArrayList<>();
        genericReport.setRequirementsBlockers(dependencies);
        assertEquals(dependencies, genericReport.getRequirementsBlockers());
    }

    @Test
    public void testClearDependenciesDuplicates() {
        Dependency dep1 = mock(Dependency.class);
        Dependency dep2 = mock(Dependency.class);
        when(dep1.getKey()).thenReturn("key1");
        when(dep2.getKey()).thenReturn("key1");

        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(dep1);
        dependencies.add(dep2);

        genericReport.setBackendDeliverables(dependencies);
        genericReport.clearDependenciesDuplicates();

        assertEquals(1, genericReport.getBackendDeliverables().size());
    }

    @Test
    public void testMakeSprintShifts() {
        Sprint sprint = mock(Sprint.class);
        when(sprint.getNumber()).thenReturn(1);

        Release release = mock(Release.class);
        when(release.getSprints()).thenReturn(List.of(sprint));

        genericReport.setReleases(List.of(release));
        genericReport.makeSprintShifts(2);

        verify(sprint).setNumber(3);
    }

    @Test
    public void testAddUmatched() {
        KeyTime keyTime = mock(KeyTime.class);
        String metricName = "metric";

        genericReport.addUmatched(keyTime, metricName);
        Map<String, List<KeyTime>> unmatchedValues = genericReport.getUnmatchedValues();

        assertTrue(unmatchedValues.containsKey(metricName));
        assertTrue(unmatchedValues.get(metricName).contains(keyTime));
    }

    @Test
    public void testRemoveUmatched() {
        KeyTime keyTime = mock(KeyTime.class);
        String metricName = "metric";

        genericReport.addUmatched(keyTime, metricName);
        genericReport.removeUmatched(keyTime, metricName);

        Map<String, List<KeyTime>> unmatchedValues = genericReport.getUnmatchedValues();
        assertFalse(unmatchedValues.get(metricName).contains(keyTime));
    }

    @Test
    public void testGetUnmatchedValues() {
        assertNotNull(genericReport.getUnmatchedValues());
        assertTrue(genericReport.getUnmatchedValues().isEmpty());
    }
}