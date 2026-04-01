// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.excel.model;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExcelMetricConfigTest {

    private ExcelMetricConfig excelMetricConfig;

    @Before
    public void setUp() {
        excelMetricConfig = new ExcelMetricConfig();
    }

    @Test
    public void testGetAndSetMetricName() {
        String metricName = "Test Metric";
        excelMetricConfig.setMetricName(metricName);
        assertEquals(metricName, excelMetricConfig.getMetricName());
    }

    @Test
    public void testGetAndSetFileName() {
        String fileName = "TestFile.xlsx";
        excelMetricConfig.setFileName(fileName);
        assertEquals(fileName, excelMetricConfig.getFileName());
    }

    @Test
    public void testGetAndSetWhoColumn() {
        String whoColumn = "A";
        excelMetricConfig.setWhoColumn(whoColumn);
        assertEquals(whoColumn, excelMetricConfig.getWhoColumn());
    }

    @Test
    public void testGetAndSetWhenColumn() {
        String whenColumn = "B";
        excelMetricConfig.setWhenColumn(whenColumn);
        assertEquals(whenColumn, excelMetricConfig.getWhenColumn());
    }

    @Test
    public void testGetAndSetWeightColumn() {
        String weightColumn = "C";
        excelMetricConfig.setWeightColumn(weightColumn);
        assertEquals(weightColumn, excelMetricConfig.getWeightColumn());
    }

    @Test
    public void testGetAndSetWeightMultiplier() {
        double weightMultiplier = 2.5;
        excelMetricConfig.setWeightMultiplier(weightMultiplier);
        assertEquals(weightMultiplier, excelMetricConfig.getWeightMultiplier(), 0.0);
    }

    @Test
    public void testDefaultWeightMultiplier() {
        assertEquals(1.0, excelMetricConfig.getWeightMultiplier(), 0.0);
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ExcelMetricConfig.METRIC_NAME, "Metric");
        jsonObject.put(ExcelMetricConfig.FILE_NAME, "File");
        jsonObject.put(ExcelMetricConfig.WHO_COLUMN, "Who");
        jsonObject.put(ExcelMetricConfig.WHEN_COLUMN, "When");
        jsonObject.put(ExcelMetricConfig.WEIGHT_COLUMN, "Weight");
        jsonObject.put(ExcelMetricConfig.WEIGHT_MULTIPLIER, 3.0);

        ExcelMetricConfig config = new ExcelMetricConfig(jsonObject);
        assertEquals("Metric", config.getMetricName());
        assertEquals("File", config.getFileName());
        assertEquals("Who", config.getWhoColumn());
        assertEquals("When", config.getWhenColumn());
        assertEquals("Weight", config.getWeightColumn());
        assertEquals(3.0, config.getWeightMultiplier(), 0.0);
    }

    @Test
    public void testConstructorWithJSONString() throws Exception {
        String jsonString = "{\"metricName\":\"Metric\",\"fileName\":\"File\",\"whoColumn\":\"Who\",\"whenColumn\":\"When\",\"weightColumn\":\"Weight\",\"weightMultiplier\":3.0}";
        ExcelMetricConfig config = new ExcelMetricConfig(jsonString);
        assertEquals("Metric", config.getMetricName());
        assertEquals("File", config.getFileName());
        assertEquals("Who", config.getWhoColumn());
        assertEquals("When", config.getWhenColumn());
        assertEquals("Weight", config.getWeightColumn());
        assertEquals(3.0, config.getWeightMultiplier(), 0.0);
    }
}