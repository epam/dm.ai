// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.common.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AssigneeTest {

    private Assignee assignee;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = mock(JSONObject.class);
        when(jsonObject.getString("emailAddress")).thenReturn("test@example.com");
        when(jsonObject.getString("displayName")).thenReturn("Test User");
        when(jsonObject.getString("accountId")).thenReturn("12345");
        when(jsonObject.getBoolean("active")).thenReturn(true);
        assignee = new Assignee(jsonObject);
    }

    @Test
    public void testGetEmailAddress() {
        assertEquals("test@example.com", assignee.getEmailAddress());
    }

    @Test
    public void testGetHtmlEmailAddress() {
        String expectedHtml = "<a href=\"mailto:test@example.com\">Test User</a>";
        assertEquals(expectedHtml, assignee.getHtmlEmailAddress());
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("Test User", assignee.getDisplayName());
    }

    @Test
    public void testGetID() {
        assertEquals("12345", assignee.getID());
    }

    @Test
    public void testGetFullName() {
        assertEquals("Test User", assignee.getFullName());
    }

    @Test
    public void testGetName() {
        when(jsonObject.getString("name")).thenReturn("testname");
        assertEquals("testname", assignee.getName());
    }

    @Test
    public void testGetAccountId() {
        assertEquals("12345", assignee.getAccountId());
    }

    @Test
    public void testGetActive() {
        assertTrue(assignee.getActive());
    }

    @Test
    public void testHashCode() {
        assertEquals("Test User".hashCode(), assignee.hashCode());
    }

    @Test
    public void testEquals() {
        Assignee anotherAssignee = new Assignee(jsonObject);
        assertTrue(assignee.equals(anotherAssignee));
    }

}