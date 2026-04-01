// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.vacation;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VacationsTest {

    private Vacations vacations;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        vacations = Vacations.getInstance();
    }

    @Test
    public void testGetInstance() {
        Vacations instance1 = Vacations.getInstance();
        Vacations instance2 = Vacations.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testGetVacations() {
        List<String> people = List.of("John Doe", "Jane Smith");
        List<Vacation> vacationList = vacations.getVacations(people);
        assertNotNull(vacationList);
        // Further assertions can be added based on the expected behavior
    }

    @Test
    public void testParseVacationsFromJSON() {
        try (MockedStatic<Vacations> mockedStatic = mockStatic(Vacations.class)) {
            mockedStatic.when(() -> Vacations.convertInputStreamToString(any(InputStream.class)))
                        .thenReturn("[{\"name\":\"John Doe\"}]");
            List<Vacation> vacationList = vacations.parseVacationsFromJSON();
            assertNotNull(vacationList);
        }
    }

    @Test
    public void testParseVacationsFromXLSX() {
        List<Vacation> vacationList = vacations.parseVacationsFromXLSX();
        assertNotNull(vacationList);
        // Further assertions can be added based on the expected behavior
    }

}