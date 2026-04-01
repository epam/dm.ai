// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TransitionTest {

    private Transition transition;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        transition = new Transition(mockJsonObject);
    }

    @Test
    public void testGetId() {
        when(mockJsonObject.getString("id")).thenReturn("123");
        assertEquals("123", transition.getId());
    }

    @Test
    public void testGetValue() {
        when(mockJsonObject.getString("name")).thenReturn("TransitionName");
        assertEquals("TransitionName", transition.getValue());
    }

    @Test
    public void testSetId() {
        when(mockJsonObject.put("id", "456")).thenReturn(mockJsonObject);
        JSONObject result = transition.setId("456");
        assertEquals(mockJsonObject, result);
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"id\":\"789\",\"name\":\"TestName\"}";
        Transition transitionFromString = new Transition(jsonString);
        assertEquals("789", transitionFromString.getId());
        assertEquals("TestName", transitionFromString.getValue());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "101");
        jsonObject.put("name", "AnotherName");
        Transition transitionFromObject = new Transition(jsonObject);
        assertEquals("101", transitionFromObject.getId());
        assertEquals("AnotherName", transitionFromObject.getValue());
    }

    @Test
    public void testGetToStatusName_WithToObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "11");
        jsonObject.put("name", "To Do");
        jsonObject.put("to", new JSONObject().put("name", "Backlog"));
        Transition t = new Transition(jsonObject);
        assertEquals("Backlog", t.getToStatusName());
    }

    @Test
    public void testGetToStatusName_WithoutToObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "11");
        jsonObject.put("name", "To Do");
        Transition t = new Transition(jsonObject);
        assertEquals(null, t.getToStatusName());
    }

    @Test
    public void testGetToStatusName_TransitionNameDiffersFromTargetStatus() {
        // Simulates the exact bug scenario: transition "To Do" leads to status "Backlog"
        String jsonString = "{\"id\":\"11\",\"name\":\"To Do\",\"to\":{\"name\":\"Backlog\"}}";
        Transition t = new Transition(jsonString);
        assertEquals("To Do", t.getValue());
        assertEquals("Backlog", t.getToStatusName());
    }
}