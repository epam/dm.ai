// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class TaskTest {

    private Task task;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = mock(JSONObject.class);
        task = new Task(jsonObject);
    }

    @Test
    public void testGetTextWithContent() {
        JSONObject content = mock(JSONObject.class);
        when(jsonObject.getJSONObject("content")).thenReturn(content);
        when(content.getString("raw")).thenReturn("raw text");

        String result = task.getText();

        assertEquals("raw text", result);
    }

    @Test
    public void testGetTextWithoutContent() {
        when(jsonObject.getJSONObject("content")).thenReturn(null);
        when(jsonObject.getString("text")).thenReturn("default text");

        String result = task.getText();

        assertEquals("default text", result);
    }

    @Test
    public void testGetState() {
        when(jsonObject.getString("state")).thenReturn("open");

        String result = task.getState();

        assertEquals("open", result);
    }

    @Test
    public void testGetBody() {
        Task spyTask = Mockito.spy(task);
        doReturn("body text").when(spyTask).getText();

        String result = spyTask.getBody();

        assertEquals("body text", result);
    }

    @Test
    public void testGetAuthor() {
        Assignee author = mock(Assignee.class);
        Task spyTask = Mockito.spy(task);
        doReturn(author).when(spyTask).getModel(Assignee.class, "author");

        IUser result = spyTask.getAuthor();

        assertEquals(author, result);
    }

    @Test
    public void testGetAuthorFallbackToUser() {
        Assignee user = mock(Assignee.class);
        Task spyTask = Mockito.spy(task);
        doReturn(null).when(spyTask).getModel(Assignee.class, "author");
        doReturn(user).when(spyTask).getModel(Assignee.class, "user");

        IUser result = spyTask.getAuthor();

        assertEquals(user, result);
    }

    @Test
    public void testGetAuthorReturnsNull() {
        Task spyTask = Mockito.spy(task);
        doReturn(null).when(spyTask).getModel(Assignee.class, "author");
        doReturn(null).when(spyTask).getModel(Assignee.class, "user");

        IUser result = spyTask.getAuthor();

        assertNull(result);
    }
}