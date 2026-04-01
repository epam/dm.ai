// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.metrics.Metric;
import org.json.JSONObject;

public abstract class DataSource {
    public abstract void performMetricCollection(Metric metric, KeyTimeCollector collector) throws Exception;
    public abstract JSONObject extractRawMetadata(Object item);
    public abstract String getSourceName();
}
