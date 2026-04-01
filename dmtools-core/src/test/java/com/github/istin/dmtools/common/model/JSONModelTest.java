// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JSONModelTest {

    private JSONModel jsonModel;
    private JSONObject mockJSONObject;

    @Before
    public void setUp() {
        mockJSONObject = mock(JSONObject.class);
        jsonModel = new JSONModel(mockJSONObject);
    }

    @Test
    public void testConstructorWithString() throws JSONException {
        String jsonString = "{\"key\":\"value\"}";
        JSONModel model = new JSONModel(jsonString);
        assertNotNull(model.getJSONObject());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullJSONObject() {
        new JSONModel((JSONObject) null);
    }

    @Test
    public void testSet() throws JSONException {
        jsonModel.set("key", "value");
        verify(mockJSONObject).put("key", "value");
    }

    @Test
    public void testSetNullValue() throws JSONException {
        jsonModel.set("key", null);
        verify(mockJSONObject).remove("key");
    }

    @Test
    public void testSetArray() throws JSONException {
        jsonModel.setArray("key", "value1", "value2");
        verify(mockJSONObject).put(eq("key"), any(JSONArray.class));
    }

    @Test
    public void testSetModel() throws JSONException {
        JSONModel model = new JSONModel();
        jsonModel.setModel("key", model);
        verify(mockJSONObject).put(eq("key"), any(JSONObject.class));
    }

    @Test
    public void testGet() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.get("key")).thenReturn("value");
        assertEquals("value", jsonModel.get("key"));
    }

    @Test
    public void testGetString() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getString("key")).thenReturn("value");
        assertEquals("value", jsonModel.getString("key"));
    }

    @Test
    public void testGetInt() {
        when(mockJSONObject.optInt("key")).thenReturn(42);
        assertEquals(42, jsonModel.getInt("key"));
    }

    @Test
    public void testGetDouble() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getDouble("key")).thenReturn(42.0);
        assertEquals(Double.valueOf(42.0), jsonModel.getDouble("key"));
    }

    @Test
    public void testGetBoolean() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getBoolean("key")).thenReturn(true);
        assertTrue(jsonModel.getBoolean("key"));
    }

    @Test
    public void testGetLong() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getLong("key")).thenReturn(42L);
        assertEquals(Long.valueOf(42L), jsonModel.getLong("key"));
    }

    @Test
    public void testGetJSONObject() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getJSONObject("key")).thenReturn(new JSONObject());
        assertNotNull(jsonModel.getJSONObject("key"));
    }

    @Test
    public void testGetJSONArray() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getJSONArray("key")).thenReturn(new JSONArray());
        assertNotNull(jsonModel.getJSONArray("key"));
    }

    @Test
    public void testGetJSONArraySize() throws JSONException {
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getJSONArray("key")).thenReturn(new JSONArray("[1,2,3]"));
        assertEquals(Integer.valueOf(3), jsonModel.getJSONArraySize("key"));
    }

    @Test
    public void testGetModel() {
        JSONModel model = jsonModel.getModel(JSONModel.class, "key");
        assertNull(model);
    }

    @Test
    public void testGetModels() {
        List<JSONModel> models = jsonModel.getModels(JSONModel.class, "key");
        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    public void testConvertToModels() {
        JSONArray jsonArray = new JSONArray("[{\"key\":\"value\"}]");
        List<JSONModel> models = JSONModel.convertToModels(JSONModel.class, jsonArray);
        assertNotNull(models);
        assertEquals(1, models.size());
    }

    @Test
    public void testCopyFields() {
        JSONModel targetModel = new JSONModel();
        jsonModel.copyFields(targetModel, "key");
        assertNull(targetModel.get("key"));
    }

    @Test
    public void testGetStringArray() {
        JSONArray jsonArray = new JSONArray("[\"value1\", \"value2\"]");
        when(mockJSONObject.isNull("key")).thenReturn(false);
        when(mockJSONObject.getJSONArray("key")).thenReturn(jsonArray);
        String[] result = jsonModel.getStringArray("key");
        assertNotNull(result);
        assertEquals(2, result.length);
    }

    @Test
    public void testToString() {
        when(mockJSONObject.toString()).thenReturn("{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", jsonModel.toString());
    }
}