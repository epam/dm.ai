// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.expert;

import com.github.istin.dmtools.job.Params;
import com.google.gson.Gson;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExpertParamsTest {

    private ExpertParams expertParams;

    @Before
    public void setUp() {
        expertParams = new ExpertParams();
    }

    @Test
    public void testGetProjectContext() {
        expertParams.setProjectContext("TestProjectContext");
        assertEquals("TestProjectContext", expertParams.getProjectContext());
    }

    @Test
    public void testGetRequest() {
        expertParams.setRequest("TestRequest");
        assertEquals("TestRequest", expertParams.getRequest());
    }

    @Test
    public void testSetProjectContext() {
        expertParams.setProjectContext("NewProjectContext");
        assertEquals("NewProjectContext", expertParams.getProjectContext());
    }

    @Test
    public void testSetRequest() {
        expertParams.setRequest("NewRequest");
        assertEquals("NewRequest", expertParams.getRequest());
    }

    @Test
    public void testGetOutputTypeDefault() {
        assertEquals(Params.OutputType.comment, expertParams.getOutputType());
    }

    @Test
    public void testGetOutputType() {
        expertParams.setOutputType(Params.OutputType.field);
        assertEquals(Params.OutputType.field, expertParams.getOutputType());
    }


    @Test
    public void testGetFieldName() {
        expertParams.setFieldName("TestFieldName");
        assertEquals("TestFieldName", expertParams.getFieldName());
    }

    @Test
    public void testSetFieldName() {
        expertParams.setFieldName("NewFieldName");
        assertEquals("NewFieldName", expertParams.getFieldName());
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"projectContext\":\"JsonProjectContext\",\"request\":\"JsonRequest\"}";
        Gson gson = new Gson();
        ExpertParams params = gson.fromJson(jsonString, ExpertParams.class);
        assertEquals("JsonProjectContext", params.getProjectContext());
        assertEquals("JsonRequest", params.getRequest());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("projectContext", "JsonObjectProjectContext");
        jsonObject.put("request", "JsonObjectRequest");
        Gson gson = new Gson();
        ExpertParams params = gson.fromJson(jsonObject.toString(), ExpertParams.class);
        assertEquals("JsonObjectProjectContext", params.getProjectContext());
        assertEquals("JsonObjectRequest", params.getRequest());
    }

    @Test
    public void testAttachResponseAsFileDefault() {
        assertEquals(false, expertParams.isAttachResponseAsFile());
    }

    @Test
    public void testGetAttachResponseAsFile() {
        expertParams.setAttachResponseAsFile(false);
        assertEquals(false, expertParams.isAttachResponseAsFile());
    }

    @Test
    public void testSetAttachResponseAsFile() {
        expertParams.setAttachResponseAsFile(false);
        assertEquals(false, expertParams.isAttachResponseAsFile());
        expertParams.setAttachResponseAsFile(true);
        assertEquals(true, expertParams.isAttachResponseAsFile());
    }

    @Test
    public void testAttachResponseAsFileFromJson() throws JSONException {
        String jsonString = "{\"attachResponseAsFile\":false,\"request\":\"TestRequest\"}";
        Gson gson = new Gson();
        ExpertParams params = gson.fromJson(jsonString, ExpertParams.class);
        assertEquals(false, params.isAttachResponseAsFile());
        assertEquals("TestRequest", params.getRequest());
    }
}