// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.google.gson.Gson;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CustomTestCasesTrackerParamsTest {

    private final Gson gson = new Gson();

    @Test
    public void testDeserializationWithTypeAndParams() {
        String json = "{\"customTestCasesTracker\":{\"type\":\"testrail\",\"params\":{\"projectNames\":[\"My Project\"],\"creationMode\":\"steps\"}}}";
        TestCasesGeneratorParams params = gson.fromJson(json, TestCasesGeneratorParams.class);

        assertNotNull(params.getCustomTestCasesTracker());
        assertEquals("testrail", params.getCustomTestCasesTracker().getType());
        assertNotNull(params.getCustomTestCasesTracker().getParams());
    }

    @Test
    public void testDeserializationWithoutCustomTracker() {
        String json = "{\"existingTestCasesJql\":\"project=TEST\"}";
        TestCasesGeneratorParams params = gson.fromJson(json, TestCasesGeneratorParams.class);

        assertNull("customTestCasesTracker should be null when not present", params.getCustomTestCasesTracker());
    }

    @Test
    public void testConstantKeyMatchesSerializedName() {
        assertEquals("customTestCasesTracker", CustomTestCasesTrackerParams._KEY);
        assertEquals("type", CustomTestCasesTrackerParams.TYPE);
        assertEquals("params", CustomTestCasesTrackerParams.PARAMS);
    }

    @Test
    public void testSettersAndGetters() {
        CustomTestCasesTrackerParams p = new CustomTestCasesTrackerParams();
        p.setType("testrail");
        JSONObject raw = new JSONObject();
        raw.put("projectNames", new org.json.JSONArray().put("My Project"));
        p.setParams(raw);

        assertEquals("testrail", p.getType());
        assertNotNull(p.getParams());
    }

    @Test
    public void testRoundTripSerializationWithCustomTrackerNested() {
        CustomTestCasesTrackerParams tracker = new CustomTestCasesTrackerParams();
        tracker.setType("testrail");
        JSONObject raw = new JSONObject();
        raw.put("creationMode", "steps");
        tracker.setParams(raw);

        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setCustomTestCasesTracker(tracker);

        String json = gson.toJson(params);
        TestCasesGeneratorParams deserialized = gson.fromJson(json, TestCasesGeneratorParams.class);

        assertNotNull(deserialized.getCustomTestCasesTracker());
        assertEquals("testrail", deserialized.getCustomTestCasesTracker().getType());
    }

    @Test
    public void testBackwardCompatibilityWithOldJson() {
        String oldJson = "{\"existingTestCasesJql\":\"project=TEST\",\"testCaseIssueType\":\"Test Case\",\"enableParallelTestCaseCheck\":false}";
        TestCasesGeneratorParams params = gson.fromJson(oldJson, TestCasesGeneratorParams.class);

        assertNull("Old JSON without customTestCasesTracker should deserialize to null", params.getCustomTestCasesTracker());
        assertEquals("project=TEST", params.getExistingTestCasesJql());
        assertFalse(params.isEnableParallelTestCaseCheck());
    }
}
