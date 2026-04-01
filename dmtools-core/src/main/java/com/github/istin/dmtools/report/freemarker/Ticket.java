// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.Config;
import com.github.istin.dmtools.common.model.ITicket;
import com.thedeanda.lorem.LoremIpsum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Ticket implements Comparable<Ticket> {

    private int duration;

    private int percent;

    private int startSprint = -1;

    private String statusStyle;

    private String status;

    private String url;

    private String key;

    private String name;

    private String progress = "?";

    private List<Dependency> dependencies = new ArrayList<>();

    private List<String> labels = new ArrayList<>();

    private boolean isHasCapacity = true;

    public int getRelease() {
        return release;
    }

    private int release;

    private String dependenciesDescription;

    public Ticket() {

    }

    public static Ticket create(ITicket blockerItem) throws IOException {
        com.github.istin.dmtools.report.freemarker.Ticket ticket = new com.github.istin.dmtools.report.freemarker.Ticket();
        ticket.setName(blockerItem.getTicketTitle());
        ticket.setKey(blockerItem.getTicketKey());
        ticket.setUrl(blockerItem.getTicketLink());
        ticket.setStatus(blockerItem.getStatus());
        String ticketDependenciesDescription = blockerItem.getTicketDependenciesDescription();
        if (ticketDependenciesDescription == null) {
            ticketDependenciesDescription = "";
        }
        ticket.setDependenciesDescription(ticketDependenciesDescription);
        return ticket;
    }

    public Ticket(int duration, int percent, int startSprint, String statusStyle, String status, String url, String key, String name, String progress, List<Dependency> dependencies) {
        this.duration = duration;
        this.percent = percent;
        this.startSprint = startSprint;
        this.statusStyle = statusStyle;
        this.status = status;
        this.url = url;
        this.key = key;
        this.name = name;
        this.progress = progress;
        this.dependencies = dependencies;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public int getStartSprint() {
        return startSprint;
    }

    public void setStartSprint(int startSprint) {
        this.startSprint = startSprint;
    }

    public String getStatusStyle() {
        return statusStyle;
    }

    public void setStatusStyle(String statusStyle) {
        this.statusStyle = statusStyle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        if (Config.DEMO_PAGE) {
            return LoremIpsum.getInstance().getTitle(2, 10);
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public void addLabel(String highlighted) {
        labels.add(highlighted);
    }

    public boolean getIsHasCapacity() {
        return isHasCapacity;
    }

    public void setIsHasCapacity(boolean isHasCapacity) {
        this.isHasCapacity = isHasCapacity;
    }

    public void setRelease(int release) {
        this.release = release;
    }

    public String getDependenciesDescription() {
        if (Config.DEMO_PAGE) {
            return LoremIpsum.getInstance().getParagraphs(1, 2);
        }
        return dependenciesDescription;
    }

    public void setDependenciesDescription(String dependenciesDescription) {
        this.dependenciesDescription = dependenciesDescription;
    }

    public List<Dependency> getOpenedDependencies() {
        List<Dependency> openedDependencies = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            String s = dependency.getStatus();
            if (s.equalsIgnoreCase("done") ||
                    s.equalsIgnoreCase("rejected")) {
                continue;
            }
            openedDependencies.add(dependency);
        }
        return openedDependencies;
    }

    @Override
    public int compareTo(Ticket ticket) {
        int release = ticket.getRelease();
        return this.release - release;
    }
}
