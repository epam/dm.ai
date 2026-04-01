// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.Config;
import com.thedeanda.lorem.LoremIpsum;

import java.util.ArrayList;
import java.util.List;

public class Dependency implements Comparable<Dependency> {

    private String key;

    private String url;

    private String status;

    public List<Ticket> getBlockedItems() {
        return blockedItems;
    }

    private List<Ticket> blockedItems = new ArrayList<>();

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    private String created;

    public Assignee getAssignee() {
        return assignee;
    }

    public void setAssignee(Assignee assignee) {
        this.assignee = assignee;
    }

    private Assignee assignee;

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    private int priority = Integer.MAX_VALUE;

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    private String dueDate;

    public String getTitle() {
        if (Config.DEMO_PAGE) {
            return LoremIpsum.getInstance().getTitle(2, 10);
        }
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    private String title;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    private String type;

    public Dependency() {
    }

    public Dependency(String key, String url, String status, String type) {
        this.key = key;
        this.url = url;
        this.status = status;
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public int compareTo(Dependency dependency) {
        return this.priority - dependency.priority;
    }
}
