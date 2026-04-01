// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class LineTest {

    @Test
    public void testLineDefaultConstructor() {
        Line line = new Line();
        assertNotNull(line);
    }

    @Test
    public void testLineConstructorWithJSONString() {
        String jsonString = "{\"key\":\"value\"}";
        try {
            Line line = new Line(jsonString);
            assertNotNull(line);
        } catch (JSONException e) {
            fail("JSONException was thrown");
        }
    }

    @Test
    public void testLineConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        Line line = new Line(jsonObject);
        assertNotNull(line);
    }
}