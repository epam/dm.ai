// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.IUser;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HistoryTest {

    private History history;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        history = new History(mockJson);
    }

    @Test
    public void testGetItems() {
        List<HistoryItem> items = history.getItems();
        assertNotNull(items);
    }

    @Test
    public void testGetCreated() throws Exception {
        String dateStr = "2023-10-01T12:00:00.000+0000";
        when(mockJson.getString("created")).thenReturn(dateStr);

        Calendar expectedCalendar = Calendar.getInstance();
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(dateStr);
        expectedCalendar.setTime(date);

        Calendar actualCalendar = history.getCreated();
        assertEquals(expectedCalendar.getTime(), actualCalendar.getTime());
    }

    @Test
    public void testGetHistoryItems() {
        List<? extends IHistoryItem> historyItems = history.getHistoryItems();
        assertNotNull(historyItems);
    }

}