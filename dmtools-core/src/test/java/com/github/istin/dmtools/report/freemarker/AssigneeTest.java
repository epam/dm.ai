// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.Config;
import com.thedeanda.lorem.LoremIpsum;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

public class AssigneeTest {

    private Assignee assignee;

    @Before
    public void setUp() {
        assignee = new Assignee("John Doe", "john.doe@example.com");
    }

    @Test
    public void testGetNameDemoPageTrue() {
        try (var loremIpsumMock = mockStatic(LoremIpsum.class)) {
            Config.DEMO_PAGE = true;
            LoremIpsum loremIpsum = Mockito.mock(LoremIpsum.class);
            loremIpsumMock.when(LoremIpsum::getInstance).thenReturn(loremIpsum);
            Mockito.when(loremIpsum.getName()).thenReturn("Demo Name");

            String name = assignee.getName();
            assertEquals("Demo Name", name);
        }
    }

    @Test
    public void testGetNameDemoPageFalse() {
        Config.DEMO_PAGE = false;
        String name = assignee.getName();
        assertEquals("John Doe", name);
    }

    @Test
    public void testSetName() {
        assignee.setName("Jane Doe");
        assertEquals("Jane Doe", assignee.getName());
    }

    @Test
    public void testGetEmail() {
        String email = assignee.getEmail();
        assertEquals("john.doe@example.com", email);
    }

    @Test
    public void testSetEmail() {
        assignee.setEmail("jane.doe@example.com");
        assertEquals("jane.doe@example.com", assignee.getEmail());
    }
}