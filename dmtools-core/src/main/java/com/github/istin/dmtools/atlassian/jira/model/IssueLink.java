// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static com.github.istin.dmtools.atlassian.jira.model.Relationship.*;

public class IssueLink extends JSONModel implements ITicket, Key {

    public IssueLink() {
    }

    public IssueLink(String json) throws JSONException {
        super(json);
    }

    public IssueLink(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString("id");
    }

    public Ticket getInwardIssue() {
        return getModel(Ticket.class, "inwardIssue");
    }

    public Ticket getOutwardIssue() {
        return getModel(Ticket.class, "outwardIssue");
    }

    public String getInwardType() {
        return getJSONObject("type").optString("inward");
    }

    public String getOutwardType() {
        return getJSONObject("type").optString("outward");
    }

    public boolean isBlocker() {
        return inwardRelationshipIn(IS_BLOCKED_BY, IS_BLOCKED_BY_, DEPENDS_UPON, DEPENDS_ON, IS_BROKEN_BY)
                || Relationship.DEPENDS_ON.equalsIgnoreCase(getOutwardType()) && getOutwardIssue() != null;
    }

    public boolean isTest() {
        return inwardRelationshipIn(TESTED_BY) && getInwardIssue().getFields().getIssueType().isTest();
    }

    protected boolean inwardRelationshipIn(String... relationships) {
        for (String relationship : relationships) {
            if (relationship.equalsIgnoreCase(getInwardType()) && getInwardIssue() != null) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlocks() {
        return Relationship.BLOCKS.equalsIgnoreCase(getOutwardType()) && getOutwardIssue() != null
                || Relationship.BLOCKS_.equalsIgnoreCase(getOutwardType()) && getOutwardIssue() != null
                || Relationship.IS_A_PREREQUISITE_OF.equalsIgnoreCase(getOutwardType()) && getOutwardIssue() != null;
    }

    @Override
    public String getStatus() {
        try {
            if (getStatusModel() == null) {
                return "NONE";
            } else {
                return getStatusModel().getName();
            }
        } catch(IOException e){
            return "NONE";
        }
    }

    @Override
    public String getPriority() throws IOException{
        Ticket relatedIssue = getRelatedTicket();
        if (relatedIssue != null) {
            Priority priority = relatedIssue.getFields().getPriority();
            return priority == null ? "none" : priority.getName();
        } else {
            return "none";
        }
    }

    @Override
    public Status getStatusModel() throws IOException {
        Ticket relatedIssue = getRelatedTicket();
        if (relatedIssue != null) {
            return relatedIssue.getFields().getStatus();
        } else {
            return null;
        }
    }

    public Ticket getRelatedTicket() {
        Ticket relatedIssue = getInwardIssue();
        if (relatedIssue == null) {
            relatedIssue = getOutwardIssue();
        }
        return relatedIssue;
    }

    @Override
    public String getTicketKey() {
        Ticket inwardIssue = getInwardIssue();
        if (inwardIssue == null) {
            Ticket outwardIssue = getOutwardIssue();
            if (outwardIssue == null) {
                return "";
            }
            return outwardIssue.getKey();
        }
        return inwardIssue.getKey();
    }

    @Override
    public String getIssueType() {
        return getRelatedTicket().getIssueType();
    }

    @Override
    public String getTicketLink() {
        return Ticket.getBasePath(getString("self")) + "browse/" + getKey();
    }

    @Override
    public String getTicketTitle() throws IOException {
        Ticket inwardIssue = getInwardIssue();
        if (inwardIssue == null) {
            Ticket outwardIssue = getOutwardIssue();
            if (outwardIssue == null) {
                return "Unknown";
            }
            return outwardIssue.getFields().getSummary();
        }
        return inwardIssue.getFields().getSummary();
    }

    @Override
    public String getTicketDescription() {
        return getRelatedTicket().getTicketDescription();
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
        return getRelatedTicket().getCreator();
    }

    @Override
    public Resolution getResolution() {
        return getRelatedTicket().getResolution();
    }

    @Override
    public JSONArray getTicketLabels() {
        return getRelatedTicket().getTicketLabels();
    }

    @Override
    public Fields getFields() {
        return getRelatedTicket().getFields();
    }

    @Override
    public ReportIteration getIteration() {
        return getRelatedTicket().getIteration();
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        return getRelatedTicket().getIterations();
    }

    @Override
    public double getProgress() throws IOException {
        return new ITicket.ITicketProgress.Impl().calc(this);
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        return getRelatedTicket().getAttachments();
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        return getRelatedTicket().getPriorityAsEnum();
    }

    @Override
    public String toText() {
        return getRelatedTicket().toText();
    }

    public IssueType getTicketType() {
        Ticket inwardIssue = getInwardIssue();
        if (inwardIssue == null) {
            Ticket outwardIssue = getOutwardIssue();
            if (outwardIssue == null) {
                return null;
            }
            return outwardIssue.getFields().getIssueType();
        }
        return inwardIssue.getFields().getIssueType();
    }

    public String getHtmlTicketLink() {
        return "<a href=\""+getTicketLink()+"\">" + getTicketKey() + "</a>";
    }

    @Override
    public double getWeight() {
        return 1;
    }

    @Override
    public String getKey() {
        return getTicketKey();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Key) {
            return this.getKey().equals(((Key) o).getKey());
        }
        if (o instanceof ITicket) {
            return getTicketKey().equals(((ITicket) o).getTicketKey());
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }
}