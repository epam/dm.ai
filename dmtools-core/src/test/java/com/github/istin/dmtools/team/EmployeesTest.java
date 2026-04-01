// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.team;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EmployeesTest {

    private Employees employees;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        employees = Employees.getInstance();
    }

    @Test
    public void testContains() {
        Employees spyEmployees = spy(employees);
        doNothing().when(spyEmployees).init();
        when(spyEmployees.getLevel("Jane Doe")).thenReturn(2);

        boolean result = spyEmployees.contains("Jane Doe");
        assertTrue(result);
    }


    @Test
    public void testTransformName() {
        Employees spyEmployees = spy(employees);
        doReturn("Main Name").when(spyEmployees).convertNameIfAlias("Alias Name");

        String result = spyEmployees.transformName("Alias Name");
        assertEquals("Main Name", result);
    }

    @Test
    public void testIsBot() {
        assertTrue(employees.isBot("DM_scripts"));
        assertFalse(employees.isBot("John Doe"));
    }

    @Test
    public void testGetLevel() {
        Employees spyEmployees = spy(employees);
        doNothing().when(spyEmployees).init();
        JSONArray mockEmployees = new JSONArray();
        JSONObject mockEmployee = new JSONObject();
        mockEmployee.put("Employee", "John Doe");
        mockEmployee.put("Level", "A3");
        mockEmployees.put(mockEmployee);
        spyEmployees.employees = mockEmployees;

        int level = spyEmployees.getLevel("John Doe");
        assertEquals(3, level);
    }

}