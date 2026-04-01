// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JSONUtilsTest {

    @Test
    public void testSerializeNull() {
        assertEquals("null", JSONUtils.serializeResult(null));
    }

    @Test
    public void testSerializeJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        String result = JSONUtils.serializeResult(jsonObject);
        assertTrue(result.contains("\"key\": \"value\""));
    }

    @Test
    public void testSerializeJSONArray() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put("value1");
        jsonArray.put("value2");
        String result = JSONUtils.serializeResult(jsonArray);
        assertTrue(result.contains("\"value1\""));
        assertTrue(result.contains("\"value2\""));
    }

    @Test
    public void testSerializeJSONModel() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        JSONModel model = new JSONModel(jsonObject);
        String result = JSONUtils.serializeResult(model);
        assertTrue(result.contains("\"key\": \"value\""));
    }

    @Test
    public void testSerializePrimitive() {
        assertEquals("test", JSONUtils.serializeResult("test"));
        assertEquals("123", JSONUtils.serializeResult(123));
        assertEquals("true", JSONUtils.serializeResult(true));
    }

    @Test
    public void testSerializeList() {
        List<String> list = Arrays.asList("item1", "item2");
        String result = JSONUtils.serializeResult(list);
        assertTrue(result.contains("\"item1\""));
        assertTrue(result.contains("\"item2\""));
    }

    @Test
    public void testSerializeListWithJSONModel() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        JSONModel model = new JSONModel(jsonObject);
        List<Object> list = Arrays.asList(model, "plain string");
        String result = JSONUtils.serializeResult(list);
        assertTrue(result.contains("\"key\": \"value\""));
        assertTrue(result.contains("\"plain string\""));
    }

    public static class TestPojo {
        private String name;
        private int age;

        public TestPojo(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    @Test
    public void testSerializePojo() {
        TestPojo pojo = new TestPojo("John", 30);
        String result = JSONUtils.serializeResult(pojo);
        assertTrue(result.contains("\"name\": \"John\""));
        assertTrue(result.contains("\"age\": 30"));
    }

    @Test
    public void testSerializeListWithPojos() {
        List<TestPojo> list = Arrays.asList(new TestPojo("John", 30), new TestPojo("Jane", 25));
        String result = JSONUtils.serializeResult(list);
        assertTrue(result.contains("\"name\": \"John\""));
        assertTrue(result.contains("\"name\": \"Jane\""));
    }

    @Test
    public void testSerializeListWithNulls() {
        List<String> list = Arrays.asList("item1", null, "item2");
        String result = JSONUtils.serializeResult(list);
        assertTrue(result.contains("\"item1\""));
        assertTrue(result.contains("null"));
        assertTrue(result.contains("\"item2\""));
    }
}

