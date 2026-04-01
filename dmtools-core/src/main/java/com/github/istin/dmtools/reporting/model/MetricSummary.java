// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.List;

public class MetricSummary {
    private int count;
    private double totalWeight;
    private List<String> contributors;
    
    public MetricSummary() {}
    public MetricSummary(int count, double totalWeight, List<String> contributors) {
        this.count = count; this.totalWeight = totalWeight; this.contributors = contributors;
    }
    
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(double totalWeight) { this.totalWeight = totalWeight; }
    public List<String> getContributors() { return contributors; }
    public void setContributors(List<String> contributors) { this.contributors = contributors; }
}
