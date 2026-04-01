// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.team;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class IEmployeesTest {

    private IEmployees employees;

    @Before
    public void setUp() {
        employees = Mockito.mock(IEmployees.class);
    }

    @Test
    public void testContains() {
        String fullName = "John Doe";
        Mockito.when(employees.contains(fullName)).thenReturn(true);

        assertTrue(employees.contains(fullName));

        Mockito.when(employees.contains("Jane Doe")).thenReturn(false);

        assertFalse(employees.contains("Jane Doe"));
    }

    @Test
    public void testTransformName() {
        String sourceFullName = "John Doe";
        String transformedName = "Doe, John";
        Mockito.when(employees.transformName(sourceFullName)).thenReturn(transformedName);

        assertEquals(transformedName, employees.transformName(sourceFullName));
    }

    @Test
    public void testIsBot() {
        String sourceFullName = "Bot123";
        Mockito.when(employees.isBot(sourceFullName)).thenReturn(true);

        assertTrue(employees.isBot(sourceFullName));

        Mockito.when(employees.isBot("John Doe")).thenReturn(false);

        assertFalse(employees.isBot("John Doe"));
    }
}