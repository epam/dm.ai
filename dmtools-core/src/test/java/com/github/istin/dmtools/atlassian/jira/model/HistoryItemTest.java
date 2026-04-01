// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HistoryItemTest {

    private HistoryItem historyItem;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("field", "status");
        jsonObject.put("from", "10000");
        jsonObject.put("fromString", "Open");
        jsonObject.put("to", "10001");
        jsonObject.put("toString", "In Progress");

        historyItem = new HistoryItem(jsonObject);
    }

    @Test
    public void testGetField() {
        assertEquals("status", historyItem.getField());
    }

    @Test
    public void testGetFrom() {
        assertEquals("10000", historyItem.getFrom());
    }

    @Test
    public void testGetFromAsString() {
        assertEquals("Open", historyItem.getFromAsString());
    }

    @Test
    public void testGetFromString() {
        assertEquals("Open", historyItem.getFromString());
    }

    @Test
    public void testGetTo() {
        assertEquals("10001", historyItem.getTo());
    }

    @Test
    public void testGetToAsString() {
        assertEquals("In Progress", historyItem.getToAsString());
    }

    @Test
    public void testGetToString() {
        assertEquals("In Progress", historyItem.getToString());
    }

    @Test
    public void testEmptyConstructor() {
        HistoryItem emptyHistoryItem = new HistoryItem();
        assertNull(emptyHistoryItem.getField());
        assertNull(emptyHistoryItem.getFrom());
        assertNull(emptyHistoryItem.getFromAsString());
        assertNull(emptyHistoryItem.getTo());
        assertNull(emptyHistoryItem.getToAsString());
    }

    @Test(expected = JSONException.class)
    public void testInvalidJson() throws JSONException {
        new HistoryItem("{invalidJson}");
    }
}