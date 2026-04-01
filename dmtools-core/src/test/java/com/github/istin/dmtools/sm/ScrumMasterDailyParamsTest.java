// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sm;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ScrumMasterDailyParamsTest {

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"jql\":\"testJql\", \"confluencePage\":\"testPage\"}";
        ScrumMasterDailyParams params = new ScrumMasterDailyParams(jsonString);
        assertEquals("testJql", params.getJql());
        assertEquals("testPage", params.getConfluencePage());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("jql", "testJql");
        jsonObject.put("confluencePage", "testPage");
        ScrumMasterDailyParams params = new ScrumMasterDailyParams(jsonObject);
        assertEquals("testJql", params.getJql());
        assertEquals("testPage", params.getConfluencePage());
    }

    @Test
    public void testGetConfluencePage() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("confluencePage", "testPage");
        ScrumMasterDailyParams params = new ScrumMasterDailyParams(jsonObject);
        assertEquals("testPage", params.getConfluencePage());
    }

    @Test
    public void testGetJql() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("jql", "testJql");
        ScrumMasterDailyParams params = new ScrumMasterDailyParams(jsonObject);
        assertEquals("testJql", params.getJql());
    }

    @Test
    public void testConstructorWithInvalidJSONString() {
        String invalidJsonString = "{\"jql\":\"testJql\", \"confluencePage\":}";
        assertThrows(JSONException.class, () -> new ScrumMasterDailyParams(invalidJsonString));
    }
}