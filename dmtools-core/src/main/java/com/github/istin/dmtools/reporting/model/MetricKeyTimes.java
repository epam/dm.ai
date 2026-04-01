// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;
import java.util.List;

public class MetricKeyTimes {
    private List<KeyTimeData> keyTimes;
    
    public MetricKeyTimes() {}
    public MetricKeyTimes(List<KeyTimeData> keyTimes) { this.keyTimes = keyTimes; }
    
    public List<KeyTimeData> getKeyTimes() { return keyTimes; }
    public void setKeyTimes(List<KeyTimeData> keyTimes) { this.keyTimes = keyTimes; }
}
