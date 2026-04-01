// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class DiagramTest {

    private Diagram diagram;
    private JSONObject jsonObject;

    @Before
    public void setUp() {
        jsonObject = new JSONObject();
        jsonObject.put("type", "exampleType");
        jsonObject.put("code", "exampleCode");
    }

    @Test
    public void testDefaultConstructor() {
        diagram = new Diagram();
        assertNotNull(diagram);
    }

    @Test
    public void testConstructorWithJSONString() {
        try {
            diagram = new Diagram(jsonObject.toString());
            assertNotNull(diagram);
        } catch (JSONException e) {
            fail("JSONException should not have been thrown");
        }
    }

    @Test
    public void testConstructorWithJSONObject() {
        diagram = new Diagram(jsonObject);
        assertNotNull(diagram);
    }

    @Test
    public void testGetType() {
        diagram = new Diagram(jsonObject);
        assertEquals("exampleType", diagram.getType());
    }

    @Test
    public void testGetCode() {
        diagram = new Diagram(jsonObject);
        assertEquals("exampleCode", diagram.getCode());
    }
}