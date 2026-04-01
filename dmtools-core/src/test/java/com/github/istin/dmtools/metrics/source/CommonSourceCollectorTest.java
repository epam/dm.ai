// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class CommonSourceCollectorTest {

    private CommonSourceCollector commonSourceCollector;
    private IEmployees mockEmployees;

    @Before
    public void setUp() {
        mockEmployees = Mockito.mock(IEmployees.class);
        commonSourceCollector = new CommonSourceCollector(mockEmployees) {
            @Override
            public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
                return List.of();
            }
        };
    }

    @Test
    public void testIsNameIgnoredWhenEmployeesIsNull() {
        commonSourceCollector = new CommonSourceCollector(null) {
            @Override
            public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
                return List.of();
            }
        };
        assertFalse(commonSourceCollector.isNameIgnored("John Doe"));
    }

    @Test
    public void testIsNameIgnoredWhenNameIsBot() {
        Mockito.when(mockEmployees.isBot("BotName")).thenReturn(true);
        assertTrue(commonSourceCollector.isNameIgnored("BotName"));
    }

    @Test
    public void testIsNameIgnoredWhenNameIsNotBot() {
        Mockito.when(mockEmployees.isBot("John Doe")).thenReturn(false);
        assertFalse(commonSourceCollector.isNameIgnored("John Doe"));
    }

    @Test
    public void testIsTeamContainsTheNameWhenEmployeesIsNull() {
        commonSourceCollector = new CommonSourceCollector(null) {
            @Override
            public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
                return List.of();
            }
        };
        assertFalse(commonSourceCollector.isTeamContainsTheName("John Doe"));
    }

    @Test
    public void testIsTeamContainsTheNameWhenNameIsContained() {
        Mockito.when(mockEmployees.contains("John Doe")).thenReturn(true);
        assertTrue(commonSourceCollector.isTeamContainsTheName("John Doe"));
    }

    @Test
    public void testIsTeamContainsTheNameWhenNameIsNotContained() {
        Mockito.when(mockEmployees.contains("John Doe")).thenReturn(false);
        assertFalse(commonSourceCollector.isTeamContainsTheName("John Doe"));
    }

    @Test
    public void testGetEmployees() {
        assertEquals(mockEmployees, commonSourceCollector.getEmployees());
    }

    @Test
    public void testTransformNameWhenEmployeesIsNull() {
        commonSourceCollector = new CommonSourceCollector(null) {
            @Override
            public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
                return List.of();
            }
        };
        assertEquals("John Doe", commonSourceCollector.transformName("John Doe"));
    }

    @Test
    public void testTransformNameWhenEmployeesIsNotNull() {
        Mockito.when(mockEmployees.transformName("John Doe")).thenReturn("Transformed Name");
        assertEquals("Transformed Name", commonSourceCollector.transformName("John Doe"));
    }
}