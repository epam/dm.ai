// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.documentation.DocumentationGeneratorParams;
import com.github.istin.dmtools.estimations.JEstimatorParams;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class JobParamsTest {

    @Test
    public void testDefaultConstructor() {
        JobParams jobParams = new JobParams();
        assertNotNull(jobParams);
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"name\":\"testName\",\"params\":{}}";
        JobParams jobParams = new JobParams(jsonString);
        assertNotNull(jobParams);
        assertEquals("testName", jobParams.getName());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "testName");
        jsonObject.put("params", new JSONObject());
        JobParams jobParams = new JobParams(jsonObject);
        assertNotNull(jobParams);
        assertEquals("testName", jobParams.getName());
    }

    @Test
    public void testGetName() {
        JobParams jobParams = new JobParams();
        jobParams.setName("testName");
        assertEquals("testName", jobParams.getName());
    }

    @Test
    public void testSetName() {
        JobParams jobParams = new JobParams();
        jobParams.setName("testName");
        assertEquals("testName", jobParams.getName());
    }

    @Test
    public void testGetParams() {
        JobParams jobParams = new JobParams();
        JSONObject params = new JSONObject();
        jobParams.setParams(new JSONModel(params));
        assertEquals(params.toString(), jobParams.getParams().toString());
    }

    @Test
    public void testGetParamsByClass() {
        JobParams jobParams = new JobParams();
        JSONModel mockModel = mock(JSONModel.class);
        when(mockModel.getJSONObject()).thenReturn(new JSONObject());
        jobParams.setParams(mockModel);
        Object result = jobParams.getParamsByClass(JSONModel.class);
        assertNotNull(result);
    }

    @Test
    public void testSetParams() {
        JobParams jobParams = new JobParams();
        JSONModel mockModel = mock(JSONModel.class);
        when(mockModel.getJSONObject()).thenReturn(new JSONObject());
        jobParams.setParams(mockModel);
        assertNotNull(jobParams.getParams());
    }

}