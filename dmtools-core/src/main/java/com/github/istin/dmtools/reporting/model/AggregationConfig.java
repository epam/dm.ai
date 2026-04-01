// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

public class AggregationConfig {
    private String formula;
    private String formulaFile;
    private String label;
    public String getFormula() { return formula; }
    public void setFormula(String formula) { this.formula = formula; }
    public String getFormulaFile() { return formulaFile; }
    public void setFormulaFile(String formulaFile) { this.formulaFile = formulaFile; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
