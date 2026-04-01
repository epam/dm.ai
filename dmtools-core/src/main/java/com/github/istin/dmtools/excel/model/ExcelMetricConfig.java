// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.excel.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class ExcelMetricConfig extends JSONModel {

    public static final String METRIC_NAME = "metricName";
    public static final String FILE_NAME = "fileName";
    public static final String WHO_COLUMN = "whoColumn";
    public static final String WHEN_COLUMN = "whenColumn";
    public static final String WEIGHT_COLUMN = "weightColumn";
    public static final String WEIGHT_MULTIPLIER = "weightMultiplier";

    public ExcelMetricConfig() {
    }

    public ExcelMetricConfig(String json) throws JSONException {
        super(json);
    }

    public ExcelMetricConfig(JSONObject json) {
        super(json);
    }

    public String getMetricName() {
        return getString(METRIC_NAME);
    }

    public String getFileName() {
        return getString(FILE_NAME);
    }

    public String getWhoColumn() {
        return getString(WHO_COLUMN);
    }

    public String getWhenColumn() {
        return getString(WHEN_COLUMN);
    }

    public String getWeightColumn() {
        return getString(WEIGHT_COLUMN);
    }

    public double getWeightMultiplier() {
        Double multiplier = getDouble(WEIGHT_MULTIPLIER);
        if (multiplier == null) {
            return 1.0d;
        }
        return multiplier;
    }

    public ExcelMetricConfig setMetricName(String metricName) {
        set(METRIC_NAME, metricName);
        return this;
    }

    public ExcelMetricConfig setFileName(String fileName) {
        set(FILE_NAME, fileName);
        return this;
    }

    public ExcelMetricConfig setWhoColumn(String whoColumn) {
        set(WHO_COLUMN, whoColumn);
        return this;
    }

    public ExcelMetricConfig setWhenColumn(String whenColumn) {
        set(WHEN_COLUMN, whenColumn);
        return this;
    }

    public ExcelMetricConfig setWeightColumn(String weightColumn) {
        set(WEIGHT_COLUMN, weightColumn);
        return this;
    }

    public ExcelMetricConfig setWeightMultiplier(double weightMultiplier) {
        set(WEIGHT_MULTIPLIER, weightMultiplier);
        return this;
    }
}
