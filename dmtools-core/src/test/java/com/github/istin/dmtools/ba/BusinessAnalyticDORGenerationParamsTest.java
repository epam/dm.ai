// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ba;

import com.github.istin.dmtools.job.BaseJobParams;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class BusinessAnalyticDORGenerationParamsTest {

    private BusinessAnalyticDORGenerationParams params;

    @Before
    public void setUp() {
        params = new BusinessAnalyticDORGenerationParams();
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"outputConfluencePage\":\"TestPage\"}";
        BusinessAnalyticDORGenerationParams paramsFromJson = new BusinessAnalyticDORGenerationParams(jsonString);
        assertNotNull(paramsFromJson);
        assertEquals("TestPage", paramsFromJson.getOutputConfluencePage());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("outputConfluencePage", "TestPage");
        BusinessAnalyticDORGenerationParams paramsFromJson = new BusinessAnalyticDORGenerationParams(jsonObject);
        assertNotNull(paramsFromJson);
        assertEquals("TestPage", paramsFromJson.getOutputConfluencePage());
    }

    @Test
    public void testGetOutputConfluencePage() {
        params.setOutputConfluencePage("TestPage");
        assertEquals("TestPage", params.getOutputConfluencePage());
    }

    @Test
    public void testSetOutputConfluencePage() {
        BaseJobParams result = params.setOutputConfluencePage("TestPage");
        assertEquals("TestPage", params.getOutputConfluencePage());
        assertEquals(params, result);
    }
}