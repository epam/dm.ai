// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.Map;

public class DatasetItem {
    private String source;
    private Map<String, Object> metadata;
    private Map<String, MetricKeyTimes> metrics;
    
    public DatasetItem() {}
    public DatasetItem(String source, Map<String, Object> metadata, Map<String, MetricKeyTimes> metrics) {
        this.source = source; this.metadata = metadata; this.metrics = metrics;
    }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Map<String, MetricKeyTimes> getMetrics() { return metrics; }
    public void setMetrics(Map<String, MetricKeyTimes> metrics) { this.metrics = metrics; }
}
