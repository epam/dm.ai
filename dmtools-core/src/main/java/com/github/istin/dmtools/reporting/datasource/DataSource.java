// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.Metric;
import org.json.JSONObject;

public abstract class DataSource {
    public abstract void performMetricCollection(Metric metric, KeyTimeCollector collector) throws Exception;
    public abstract JSONObject extractRawMetadata(Object item);
    public abstract String getSourceName();

    /**
     * Returns the resolved SourceCode provider for this data source, or null for non-code data sources.
     * Used by the reporting pipeline to route per-source-type metric collection
     * (e.g. a data source with sourceType=gitlab uses GitLab even when another provider is the global default).
     */
    public SourceCode getSourceCode() {
        return null;
    }
}
