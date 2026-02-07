package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;

import java.util.*;

public class Metric {

    private String name;
    private boolean isPersonalized = false;
    private TrackerRule rule;

    private SourceCollector sourceCollector;

    private boolean isWeight = false;
    private double divider = 1.0;

    public Metric(String name, TrackerRule rule) {
        this.name = name;
        this.rule = rule;
    }

    public Metric(String name, boolean isWeight, TrackerRule rule) {
        this.name = name;
        this.rule = rule;
        this.isWeight = isWeight;
    }

    public Metric(String name, boolean isWeight, SourceCollector sourceCollector) {
        this.name = name;
        this.isWeight = isWeight;
        this.sourceCollector = sourceCollector;
    }

    public Metric(String name, boolean isWeight, boolean isPersonalized, SourceCollector sourceCollector) {
        this.name = name;
        this.isWeight = isWeight;
        this.sourceCollector = sourceCollector;
        this.isPersonalized = isPersonalized;
    }

    public Metric(String name, boolean isWeight, boolean isPersonalized, TrackerRule rule, SourceCollector sourceCollector) {
        this.name = name;
        this.isPersonalized = isPersonalized;
        this.rule = rule;
        this.isWeight = isWeight;
        this.sourceCollector = sourceCollector;
    }

    public String getName() {
        return name;
    }

    public TrackerRule getRule() {
        return rule;
    }

    public boolean isWeight() {
        return isWeight;
    }

    public void setWeight(boolean weight) {
        isWeight = weight;
    }

    public SourceCollector getSourceCollector() {
        return sourceCollector;
    }

    public boolean isPersonalized() {
        return isPersonalized;
    }

    public double getDivider() {
        return divider;
    }

    public void setDivider(double divider) {
        this.divider = divider;
    }

    public void perform(Map<String, Map<String, List<KeyTime>>> metricAndMap, Set<String> combinedPeople) throws Exception {
        List<KeyTime> keyTimes = getSourceCollector().performSourceCollection(isPersonalized, getName());
        for (KeyTime keyTime : keyTimes) {
            addKeyTime(metricAndMap, combinedPeople, keyTime, keyTime.getWho());
        }
    }

    public void addKeyTime(Map<String, Map<String, List<KeyTime>>> metricAndMap, Set<String> combinedPeople, KeyTime keyTime, String keyTimeOwner) {
        List<KeyTime> keyTimes = new ArrayList<>();
        keyTimes.add(keyTime);

        Map<String, List<KeyTime>> listMap = metricAndMap.computeIfAbsent(getName(), k -> new HashMap<>());
        List<KeyTime> items = listMap.computeIfAbsent(keyTimeOwner, k -> new ArrayList<>());
        items.addAll(keyTimes);
        combinedPeople.add(keyTime.getWho());
    }
}
