// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.common.timeline.Release;
import com.github.istin.dmtools.common.timeline.Sprint;
import com.github.istin.dmtools.report.model.KeyTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericReport extends SimpleReport {

    private String chartColors;

    public boolean getIsNotWiki() {
        return isNotWiki;
    }

    public void setIsNotWiki(boolean notWiki) {
        isNotWiki = notWiki;
    }

    private boolean isNotWiki = true;

    private Roadmap roadmap;

    private boolean isShowReleases = true;

    public boolean isBySprints() {
        return isBySprints;
    }

    public boolean isByWeeks() {
        return isByWeeks;
    }

    public void setBySprints(boolean bySprints) {
        isBySprints = bySprints;
    }

    public void setByWeeks(boolean byWeeks) {
        isByWeeks = byWeeks;
    }

    private boolean isBySprints = true;

    private boolean isByWeeks = false;

    public boolean getIsChart() {
        return isChart;
    }

    public void setChart(boolean chart) {
        isChart = chart;
    }

    public String getChartColors() {
        return this.chartColors;
    }

    public void setChartColors(String chartColors) {
        this.chartColors = chartColors;
    }

    private boolean isChart = false;

    public List<GenericRow> getRows() {
        return rows;
    }

    public void setRows(List<GenericRow> rows) {
        this.rows = rows;
    }

    private List<GenericRow> rows = new ArrayList<>();

    private List<Ticket> tickets = new ArrayList<>();

    private List<Question> questions = new ArrayList<>();

    public Backlog getBacklog() {
        return backlog;
    }

    public void setBacklog(Backlog backlog) {
        this.backlog = backlog;
    }

    private Backlog backlog;

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public void setRoadmap(Roadmap roadmap) {
        this.roadmap = roadmap;
    }

    public List<Release> getReleases() {
        return releases;
    }

    public void setReleases(List<Release> releases) {
        this.releases = releases;
    }

    private List<Release> releases;

    public void addTicket(Ticket reportTicket) {
        tickets.add(reportTicket);
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void addQuestion(Question question) {
        questions.add(question);
    }

    public boolean isShowReleases() {
        return isShowReleases;
    }

    public void setShowReleases(boolean showReleases) {
        isShowReleases = showReleases;
    }

    private List<Dependency> productClarifications = new ArrayList<>();

    private List<Dependency> solutionBlockers = new ArrayList<>();
    private List<Dependency> requirementsBlockers = new ArrayList<>();

    public List<Dependency> getBackendDeliverables() {
        return backendDeliverables;
    }

    public void setBackendDeliverables(List<Dependency> backendDeliverables) {
        this.backendDeliverables = backendDeliverables;
    }

    private List<Dependency> backendDeliverables = new ArrayList<>();

    public List<Dependency> getProductClarifications() {
        return productClarifications;
    }

    public void setProductClarifications(List<Dependency> productClarifications) {
        this.productClarifications = productClarifications;
    }

    public List<Dependency> getSolutionBlockers() {
        return solutionBlockers;
    }

    public void setSolutionBlockers(List<Dependency> solutionBlockers) {
        this.solutionBlockers = solutionBlockers;
    }

    public List<Dependency> getRequirementsBlockers() {
        return requirementsBlockers;
    }

    public void setRequirementsBlockers(List<Dependency> requirementsBlockers) {
        this.requirementsBlockers = requirementsBlockers;
    }

    public void clearDependenciesDuplicates() {
        removeDuplicates(this.backendDeliverables);
        removeDuplicates(this.requirementsBlockers);
        removeDuplicates(this.solutionBlockers);
        removeDuplicates(this.productClarifications);
    }

    private void removeDuplicates(List<Dependency> list) {

        for (int i = 0; i < list.size(); i++) {
            Dependency dependency = list.get(i);
            List<Integer> indexesToRemove = new ArrayList<>();
            for (int j = i+1; j < list.size(); j++) {
                Dependency nextDependency = list.get(j);
                if (dependency.getKey().equalsIgnoreCase(nextDependency.getKey())) {
                    indexesToRemove.add(j);
                }
            }
            for (int index : indexesToRemove) {
                list.remove(index);
            }
        }
    }

    public void makeSprintShifts(int sprintShift) {
        List<Release> releases = getReleases();
        for (Release release : releases) {
            List<Sprint> sprints = release.getSprints();
            for (Sprint sprint : sprints) {
                sprint.setNumber(sprint.getNumber() + sprintShift);
            }
        }
    }

    private Map<String, List<KeyTime>> unmatchedValues = new HashMap<>();

    public void addUmatched(KeyTime keyTime, String metricName) {
        List<KeyTime> keyTimes = unmatchedValues.computeIfAbsent(metricName, k -> new ArrayList<>());
        if (keyTimes.contains(keyTime)) {
            return;
        }
        keyTimes.add(keyTime);
    }

    public void removeUmatched(KeyTime keyTime, String metricName) {
        unmatchedValues.computeIfAbsent(metricName, k -> new ArrayList<>()).remove(keyTime);
    }

    public Map<String, List<KeyTime>> getUnmatchedValues() {
        return unmatchedValues;
    }
}