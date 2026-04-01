// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.report.model.KeyTime;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CombinedCustomRunnableMetrics extends Metric {
    private final Metric[] metrics;

    public CombinedCustomRunnableMetrics(String name, Metric ... metrics) {
        super(name, true, (TrackerRule) (jiraClient, ticket) -> null);
        this.metrics = metrics;
    }

    public CombinedCustomRunnableMetrics(String name, List<Metric> metrics) {
        this(name, metrics.toArray(new Metric[metrics.size()-1]));
    }

    @Override
    public void perform(Map<String, Map<String, List<KeyTime>>> metricAndMap, Set<String> combinedPeople) throws Exception {
        for (Metric metric : metrics) {
            metric.perform(metricAndMap, combinedPeople);
        }
    }

}
