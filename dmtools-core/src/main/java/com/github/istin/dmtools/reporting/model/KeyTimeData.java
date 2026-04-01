// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

public class KeyTimeData {
    private String when;
    private String who;
    private double weight;
    
    public KeyTimeData() {}
    public KeyTimeData(String when, String who, double weight) {
        this.when = when; this.who = who; this.weight = weight;
    }
    
    public String getWhen() { return when; }
    public void setWhen(String when) { this.when = when; }
    public String getWho() { return who; }
    public void setWho(String who) { this.who = who; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
}
