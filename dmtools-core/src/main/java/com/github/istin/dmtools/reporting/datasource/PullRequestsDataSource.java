// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;
import org.json.JSONObject;
import java.util.*;

public class PullRequestsDataSource extends DataSource {
    private final SourceCode sourceCode;
    private final Map<String, Object> params;

    public PullRequestsDataSource(SourceCode sourceCode, Map<String, Object> params) {
        this.sourceCode = sourceCode;
        this.params = params;
    }

    @Override
    public void performMetricCollection(Metric metric, KeyTimeCollector collector) throws Exception {
        SourceCollector sourceCollector = metric.getSourceCollector();
        if (sourceCollector == null || sourceCode == null) {
            return;
        }

        List<KeyTime> keyTimes = sourceCollector.performSourceCollection(
            metric.isPersonalized(),
            metric.getName()
        );

        String workspace = (String) params.get("workspace");
        String repository = (String) params.get("repository");

        for (KeyTime kt : keyTimes) {
            JSONObject metadata = new JSONObject();
            metadata.put("key", kt.getKey());
            metadata.put("who", kt.getWho());
            if (kt.getSummary() != null) {
                metadata.put("summary", kt.getSummary());
            }
            // Build link: use KeyTime link if set, otherwise use SourceCode.getPullRequestUrl()
            String link = kt.getLink();
            if (link == null && workspace != null && repository != null) {
                link = sourceCode.getPullRequestUrl(workspace, repository, kt.getKey());
            }
            if (link != null) {
                metadata.put("link", link);
            }
            collector.collect(Arrays.asList(kt), metadata, kt.getKey());
        }
    }


    @Override
    public SourceCode getSourceCode() {
        return sourceCode;
    }

    @Override
    public JSONObject extractRawMetadata(Object item) {
        if (item instanceof JSONModel) {
            return ((JSONModel) item).getJSONObject();
        }
        return new JSONObject();
    }

    @Override
    public String getSourceName() {
        return "pullRequests";
    }
}
