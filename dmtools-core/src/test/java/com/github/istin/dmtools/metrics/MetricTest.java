// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MetricTest {

    private Metric metric;
    private TrackerRule mockRule;
    private SourceCollector mockSourceCollector;
    private KeyTime mockKeyTime;
    private final String metricName = "Test Metric";

    @BeforeEach
    public void setUp() {
        mockRule = mock(TrackerRule.class);
        mockSourceCollector = mock(SourceCollector.class);
        mockKeyTime = mock(KeyTime.class);
        metric = new Metric(metricName, true, false, mockRule, mockSourceCollector);
    }

    @Test
    public void testGetMetricName() {
        assertEquals(metricName, metric.getName());
    }

    @Test
    public void testGetRule() {
        assertEquals(mockRule, metric.getRule());
    }

    @Test
    public void testIsWeight() {
        assertTrue(metric.isWeight());
    }

    @Test
    public void testSetWeight() {
        metric.setWeight(false);
        assertFalse(metric.isWeight());
    }

    @Test
    public void testGetSourceCollector() {
        assertEquals(mockSourceCollector, metric.getSourceCollector());
    }

    @Test
    public void testPerform() throws Exception {
        List<KeyTime> keyTimes = Collections.singletonList(mockKeyTime);
        when(mockSourceCollector.performSourceCollection(false, metricName)).thenReturn(keyTimes);
        when(mockKeyTime.getWho()).thenReturn("TestOwner");

        Map<String, Map<String, List<KeyTime>>> metricAndMap = new HashMap<>();
        Set<String> combinedPeople = new HashSet<>();

        metric.perform(metricAndMap, combinedPeople);

        assertTrue(metricAndMap.containsKey(metricName));
        assertTrue(metricAndMap.get(metricName).containsKey("TestOwner"));
        assertEquals(1, metricAndMap.get(metricName).get("TestOwner").size());
        assertTrue(combinedPeople.contains("TestOwner"));
    }

    @Test
    public void testAddKeyTime() {
        Map<String, Map<String, List<KeyTime>>> metricAndMap = new HashMap<>();
        Set<String> combinedPeople = new HashSet<>();
        when(mockKeyTime.getWho()).thenReturn("TestOwner");

        metric.addKeyTime(metricAndMap, combinedPeople, mockKeyTime, "TestOwner");

        assertTrue(metricAndMap.containsKey(metricName));
        assertTrue(metricAndMap.get(metricName).containsKey("TestOwner"));
        assertEquals(1, metricAndMap.get(metricName).get("TestOwner").size());
        assertTrue(combinedPeople.contains("TestOwner"));
    }
}