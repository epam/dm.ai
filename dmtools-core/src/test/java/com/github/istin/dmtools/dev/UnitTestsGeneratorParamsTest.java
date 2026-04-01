// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.*;

public class UnitTestsGeneratorParamsTest {

    private UnitTestsGeneratorParams params;

    @Before
    public void setUp() {
        params = new UnitTestsGeneratorParams();
    }

    @Test
    public void testGetSrcFolder() {
        params.setSrcFolder("src/main/java");
        assertEquals("src/main/java", params.getSrcFolder());
    }

    @Test
    public void testGetRootTestsFolder() {
        params.setRootTestsFolder("src/test/java");
        assertEquals("src/test/java", params.getRootTestsFolder());
    }

    @Test
    public void testGetFileExtensions() {
        String[] extensions = {"java", "kt"};
        params.setFileExtensions(extensions);
        assertArrayEquals(extensions, params.getFileExtensions());
    }

    @Test
    public void testGetTestTemplate() {
        params.setTestTemplate("JUnit");
        assertEquals("JUnit", params.getTestTemplate());
    }

    @Test
    public void testGetPackageFilter() {
        params.setPackageFilter("com.example");
        assertEquals("com.example", params.getPackageFilter());
    }

    @Test
    public void testGetRole() {
        params.setRole("developer");
        assertEquals("developer", params.getRole());
    }

    @Test
    public void testGetRules() {
        params.setRules("rule1,rule2");
        assertEquals("rule1,rule2", params.getRules());
    }

    @Test
    public void testConstructorWithJsonString() throws JSONException {
        String jsonString = "{\"srcFolder\":\"src/main/java\",\"rootTestsFolder\":\"src/test/java\"}";
        UnitTestsGeneratorParams paramsFromJson = new UnitTestsGeneratorParams(jsonString);
        assertEquals("src/main/java", paramsFromJson.getSrcFolder());
        assertEquals("src/test/java", paramsFromJson.getRootTestsFolder());
    }

    @Test
    public void testConstructorWithJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("srcFolder", "src/main/java");
        jsonObject.put("rootTestsFolder", "src/test/java");
        UnitTestsGeneratorParams paramsFromJson = new UnitTestsGeneratorParams(jsonObject);
        assertEquals("src/main/java", paramsFromJson.getSrcFolder());
        assertEquals("src/test/java", paramsFromJson.getRootTestsFolder());
    }
}