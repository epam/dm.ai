// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.estimations;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class JEstimatorParamsTest {

    @Test
    public void testDefaultConstructor() {
        JEstimatorParams params = new JEstimatorParams();
        assertEquals(null, params.getReportName());
        assertEquals(null, params.getJQL());
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"reportName\":\"Test Report\",\"jql\":\"SELECT * FROM table\"}";
        JEstimatorParams params = new JEstimatorParams(jsonString);
        assertEquals("Test Report", params.getReportName());
        assertEquals("SELECT * FROM table", params.getJQL());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("reportName", "Test Report");
        jsonObject.put("jql", "SELECT * FROM table");
        JEstimatorParams params = new JEstimatorParams(jsonObject);
        assertEquals("Test Report", params.getReportName());
        assertEquals("SELECT * FROM table", params.getJQL());
    }

    @Test
    public void testConstructorWithInvalidJSONString() {
        String invalidJsonString = "invalid json";
        assertThrows(JSONException.class, () -> {
            new JEstimatorParams(invalidJsonString);
        });
    }
}