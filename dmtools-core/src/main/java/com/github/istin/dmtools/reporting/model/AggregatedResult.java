// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.Map;

public class AggregatedResult {
    private Map<String, ContributorMetrics> byContributor;
    private ContributorMetrics total;
    
    public AggregatedResult() {}
    public AggregatedResult(Map<String, ContributorMetrics> byContributor, ContributorMetrics total) {
        this.byContributor = byContributor; this.total = total;
    }
    
    public Map<String, ContributorMetrics> getByContributor() { return byContributor; }
    public void setByContributor(Map<String, ContributorMetrics> byContributor) { this.byContributor = byContributor; }
    public ContributorMetrics getTotal() { return total; }
    public void setTotal(ContributorMetrics total) { this.total = total; }
}
