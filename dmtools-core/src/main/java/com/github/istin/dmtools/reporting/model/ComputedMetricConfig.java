// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a computed metric that derives its value from other metrics via formula.
 * Example: { "label": "Input Tokens", "formula": "${Total Tokens} - ${Output Tokens}", "isWeight": true }
 *
 * Supported operators: +, -, *, /
 * Metric references: ${Metric Label}
 */
@Getter
@Setter
public class ComputedMetricConfig {
    private String label;
    private String formula;
    private boolean isWeight;
    private boolean isPersonalized;

    public ComputedMetricConfig() {}

    public ComputedMetricConfig(String label, String formula, boolean isWeight, boolean isPersonalized) {
        this.label = label;
        this.formula = formula;
        this.isWeight = isWeight;
        this.isPersonalized = isPersonalized;
    }
}
