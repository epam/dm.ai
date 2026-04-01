// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.Config;
import com.thedeanda.lorem.LoremIpsum;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

public class MockedNamesTest {

    private MockedNames mockedNames;

    @Before
    public void setUp() {
        mockedNames = MockedNames.getInstance();
    }

    @Test
    public void testGetInstance() {
        MockedNames instance1 = MockedNames.getInstance();
        MockedNames instance2 = MockedNames.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testMockWithDemoSiteTrue() {
        String name = "John Doe";
        String mockedName = mockedNames.mock(name);

        assertEquals(mockedNames.mock(name), mockedName);
    }

    @Test
    public void testMockWithSpecialNames() {
        assertEquals("android", mockedNames.mock("android"));
        assertEquals("iOS", mockedNames.mock("iOS"));
        assertEquals("web", mockedNames.mock("web"));
        assertEquals("QA", mockedNames.mock("QA"));
    }

}