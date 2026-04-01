// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class RemoteLink extends JSONModel implements ITicket {

    private static String GLOBAL_ID = "globalId";

    public RemoteLink() {
    }

    public RemoteLink(String json) throws JSONException {
        super(json);
    }

    public RemoteLink(JSONObject json) {
        super(json);
    }

    public String getRelationship() {
        return getString("relationship");
    }

    public String getUrl() {
        JSONObject object = getJSONObject("object");
        if (object == null) {
            return null;
        }
        return object.getString("url");
    }

    public String getTitle() {
        JSONObject object = getJSONObject("object");
        if (object == null) {
            return "Can't get Title";
        }
        return object.getString("title");
    }

    public boolean isBlocker() {
        return Relationship.IS_BLOCKED_BY.equalsIgnoreCase(getRelationship());
    }

    @Override
    public String getStatus() throws IOException {
        Status statusModel = getStatusModel();
        if (statusModel == null) {
            return null;
        }
        return statusModel.getName();
    }

    @Override
    public String getPriority() throws IOException {
        return null;
    }

    @Override
    public Status getStatusModel() throws IOException {
        return null;
    }

    @Override
    public String getTicketKey() {
        return getTitle();
    }

    @Override
    public String getIssueType() throws IOException {
        return "Unknown";
    }

    @Override
    public String getTicketLink() {
        return getUrl();
    }

    public String getHtmlTicketLink() {
        return "<a href=\""+getTicketLink()+"\">" + getTicketKey() + "</a>";
    }

    public String getGlobalId() {
        return getString(GLOBAL_ID);
    }

    @Override
    public String getTicketTitle() throws IOException {
        return "Unknown";
    }

    @Override
    public String getTicketDescription() {
        return "Unknown";
    }

    @Override
    public String getTicketDependenciesDescription() {
        return null;
    }

    @Override
    public Date getCreated() {
        return null;
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        return null;
    }

    @Override
    public Long getUpdatedAsMillis() {
        return null;
    }

    @Override
    public IUser getCreator() {
        return null;
    }

    @Override
    public Resolution getResolution() {
        return null;
    }

    @Override
    public JSONArray getTicketLabels() {
        return null;
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
        return List.of();
    }

    @Override
    public double getProgress() throws IOException {
        return new ITicket.ITicketProgress.Impl().calc(this);
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        return Collections.emptyList();
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        return null;
    }

    @Override
    public String toText() {
        return toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ITicket) {
            return getTicketKey().equals(((ITicket) obj).getTicketKey());
        }
        return super.equals(obj);
    }

    @Override
    public double getWeight() {
        return 1;
    }

    @Override
    public String getKey() {
        return getTicketKey();
    }
}