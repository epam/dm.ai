// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** A GitLab issue exposed through the tracker API. */
public class GitLabTicket extends JSONModel implements ITicket {

    private final String projectPath;

    public GitLabTicket(String projectPath, JSONObject json) {
        super(json);
        this.projectPath = projectPath;
    }

    public GitLabTicket(String projectPath, String json) {
        super(json);
        this.projectPath = projectPath;
    }

    public Integer getIid() {
        return getInt("iid");
    }

    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public String getKey() {
        return getTicketKey();
    }

    @Override
    public String getTicketKey() {
        return projectPath + "#" + getIid();
    }

    @Override
    public String getTicketLink() {
        return getString("web_url");
    }

    @Override
    public String getStatus() {
        return getString("state");
    }

    @Override
    public Status getStatusModel() {
        String state = getStatus();
        if (state == null) {
            return null;
        }
        return new Status(new JSONObject().put("name", state));
    }

    @Override
    public String getIssueType() {
        return "Issue";
    }

    @Override
    public String getPriority() {
        for (String label : getLabels()) {
            String lower = label.toLowerCase();
            String prefix = lower.startsWith("priority::") ? "priority::" : "priority:";
            if (lower.startsWith(prefix)) {
                String value = label.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value.substring(0, 1).toUpperCase() + value.substring(1);
            }
        }
        return null;
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        String priority = getPriority();
        return priority == null ? TicketPriority.NotSet : TicketPriority.byName(priority);
    }

    @Override
    public String getTicketTitle() {
        return getString("title");
    }

    @Override
    public String getTicketDescription() {
        return getString("description");
    }

    @Override
    public String getTicketDependenciesDescription() {
        return null;
    }

    @Override
    public Date getCreated() {
        String createdAt = getString("created_at");
        return createdAt == null ? null : DateUtils.parseIsoDate(createdAt);
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        return getJSONObject();
    }

    @Override
    public Long getUpdatedAsMillis() {
        String updatedAt = getString("updated_at");
        Date updated = updatedAt == null ? null : DateUtils.parseIsoDate(updatedAt);
        return updated == null ? null : updated.getTime();
    }

    @Override
    public IUser getCreator() {
        return getModel(GitLabUser.class, "author");
    }

    @Override
    public Resolution getResolution() {
        return null;
    }

    @Override
    public JSONArray getTicketLabels() {
        JSONArray labels = getJSONArray("labels");
        return labels == null ? new JSONArray() : labels;
    }

    @Override
    public Fields getFields() {
        return null;
    }

    @Override
    public ReportIteration getIteration() {
        return null;
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        return Collections.emptyList();
    }

    @Override
    public double getProgress() throws IOException {
        return "closed".equalsIgnoreCase(getStatus()) ? 1.0 : 0.0;
    }

    @Override
    public double getWeight() {
        return 0;
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        return Collections.emptyList();
    }

    @Override
    public String toText() {
        String title = getTicketTitle();
        String description = getTicketDescription();
        return getTicketKey() + " " + (title == null ? "" : title)
                + (description == null || description.isEmpty() ? "" : "\n\n" + description);
    }

    private List<String> getLabels() {
        JSONArray labels = getJSONArray("labels");
        if (labels == null) {
            return Collections.emptyList();
        }
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < labels.length(); i++) {
            String label = labels.optString(i, null);
            if (label != null) {
                result.add(label);
            }
        }
        return result;
    }
}
