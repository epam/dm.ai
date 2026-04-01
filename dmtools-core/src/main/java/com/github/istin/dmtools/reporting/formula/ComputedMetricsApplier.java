// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.formula;

import com.github.istin.dmtools.reporting.model.ComputedMetricConfig;
import com.github.istin.dmtools.reporting.model.ContributorMetrics;
import com.github.istin.dmtools.reporting.model.MetricSummary;

import java.util.*;

/**
 * Applies computed (formula-based) metrics to period and contributor metrics.
 */
public final class ComputedMetricsApplier {

    private ComputedMetricsApplier() {
    }

    public static void applyToMetrics(List<ComputedMetricConfig> computedMetrics,
                                      Map<String, MetricSummary> metrics,
                                      Set<String> weightMetricLabels) {
        if (computedMetrics == null || computedMetrics.isEmpty()) {
            return;
        }

        for (ComputedMetricConfig cm : computedMetrics) {
            Map<String, Double> values = buildMetricValues(metrics, weightMetricLabels);
            double computed = FormulaEvaluator.evaluate(cm.getFormula(), values);

            MetricSummary summary = new MetricSummary();
            if (cm.isWeight()) {
                summary.setTotalWeight(computed);
            } else {
                summary.setCount((int) Math.round(computed));
                summary.setTotalWeight(computed); // keep precision for UI if needed later
            }

            // Collect contributors from referenced metrics
            Set<String> contributors = new HashSet<>();
            for (Map.Entry<String, MetricSummary> e : metrics.entrySet()) {
                if (cm.getFormula().contains("${" + e.getKey() + "}") && e.getValue().getContributors() != null) {
                    contributors.addAll(e.getValue().getContributors());
                }
            }
            summary.setContributors(new ArrayList<>(contributors));

            metrics.put(cm.getLabel(), summary);
            if (cm.isWeight() && weightMetricLabels != null) {
                weightMetricLabels.add(cm.getLabel());
            }
        }
    }

    public static void applyToContributors(List<ComputedMetricConfig> computedMetrics,
                                           Map<String, ContributorMetrics> contributorBreakdown,
                                           Set<String> weightMetricLabels) {
        if (computedMetrics == null || computedMetrics.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ContributorMetrics> entry : contributorBreakdown.entrySet()) {
            applyToMetrics(computedMetrics, entry.getValue().getMetrics(), weightMetricLabels);
        }
    }

    public static Map<String, Double> buildMetricValues(Map<String, MetricSummary> metrics,
                                                        Set<String> weightMetricLabels) {
        Map<String, Double> values = new HashMap<>();
        if (metrics == null) {
            return values;
        }
        for (Map.Entry<String, MetricSummary> entry : metrics.entrySet()) {
            values.put(entry.getKey(), metricValue(entry.getKey(), entry.getValue(), weightMetricLabels));
        }
        return values;
    }

    private static double metricValue(String name, MetricSummary summary, Set<String> weightMetricLabels) {
        if (summary == null) {
            return 0.0;
        }
        boolean isWeight = weightMetricLabels != null && weightMetricLabels.contains(name);
        return isWeight ? summary.getTotalWeight() : summary.getCount();
    }
}
