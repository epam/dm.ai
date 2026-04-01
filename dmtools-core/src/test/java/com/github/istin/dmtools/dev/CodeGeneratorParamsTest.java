// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class CodeGeneratorParamsTest {

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"confluenceRootPage\":\"rootPage\",\"eachPagePrefix\":\"prefix\",\"sources\":[],\"role\":\"admin\"}";
        CodeGeneratorParams params = new CodeGeneratorParams(jsonString);

        assertEquals("rootPage", params.getConfluenceRootPage());
        assertEquals("prefix", params.getEachPagePrefix());
        assertNotNull(params.getSources());
        assertEquals("admin", params.getRole());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("confluenceRootPage", "rootPage");
        jsonObject.put("eachPagePrefix", "prefix");
        jsonObject.put("sources", new JSONArray());
        jsonObject.put("role", "admin");

        CodeGeneratorParams params = new CodeGeneratorParams(jsonObject);

        assertEquals("rootPage", params.getConfluenceRootPage());
        assertEquals("prefix", params.getEachPagePrefix());
        assertNotNull(params.getSources());
        assertEquals("admin", params.getRole());
    }

    @Test
    public void testGetConfluenceRootPage() {
        CodeGeneratorParams params = mock(CodeGeneratorParams.class);
        when(params.getConfluenceRootPage()).thenReturn("rootPage");

        assertEquals("rootPage", params.getConfluenceRootPage());
    }

    @Test
    public void testGetEachPagePrefix() {
        CodeGeneratorParams params = mock(CodeGeneratorParams.class);
        when(params.getEachPagePrefix()).thenReturn("prefix");

        assertEquals("prefix", params.getEachPagePrefix());
    }

    @Test
    public void testGetSources() {
        CodeGeneratorParams params = mock(CodeGeneratorParams.class);
        JSONArray jsonArray = new JSONArray();
        when(params.getSources()).thenReturn(jsonArray);

        assertEquals(jsonArray, params.getSources());
    }

    @Test
    public void testGetRole() {
        CodeGeneratorParams params = mock(CodeGeneratorParams.class);
        when(params.getRole()).thenReturn("admin");

        assertEquals("admin", params.getRole());
    }
}