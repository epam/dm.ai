// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Data source for CSV files. Delegates metric collection to the metric's SourceCollector
 * (CsvMetricSource), similar to how PullRequestsDataSource works.
 */
public class CsvDataSource extends DataSource {

    private final Map<String, Object> params;

    public CsvDataSource(Map<String, Object> params) {
        this.params = params;
    }

    @Override
    public void performMetricCollection(Metric metric, KeyTimeCollector collector) throws Exception {
        SourceCollector sourceCollector = metric.getSourceCollector();
        if (sourceCollector == null) {
            return;
        }

        List<KeyTime> keyTimes = sourceCollector.performSourceCollection(
            metric.isPersonalized(),
            metric.getName()
        );

        for (KeyTime kt : keyTimes) {
            JSONObject metadata = new JSONObject();
            metadata.put("key", kt.getKey());
            metadata.put("who", kt.getWho());
            if (kt.getSummary() != null) {
                metadata.put("summary", kt.getSummary());
            }
            collector.collect(Arrays.asList(kt), metadata, kt.getKey());
        }
    }

    @Override
    public JSONObject extractRawMetadata(Object item) {
        return new JSONObject();
    }

    @Override
    public String getSourceName() {
        return "csv";
    }
}
