// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.model;

import com.github.istin.dmtools.common.model.Key;

import java.util.Calendar;

public class KeyTime implements Key {

    private String key;

    public Calendar getWhen() {
        return when;
    }

    private Calendar when;

    private String who;

    private double weight = 1;
    private String link;
    private String summary;

    public KeyTime(String key, Calendar when) {
        this.key = key;
        this.when = when;
    }

    public KeyTime(String key, Calendar when, String who) {
        this.key = key;
        this.when = when;
        this.who = who;
    }

    public String getWho() {
        return who;
    }

    public void setWho(String who) {
        this.who = who;
    }

    public String getKey() {
        return key;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public void setWhen(Calendar when) {
        this.when = when;
    }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}