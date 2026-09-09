// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * A GitHub issue exposed as a tracker {@link ITicket}.
 *
 * <p>GitHub issues have no native status/priority/iteration fields, so those
 * are derived from the open/closed state, labels, and milestone. The composite
 * ticket key is {@code owner/repo#number}.</p>
 */
public class GitHubTicket extends GitHubIssue implements ITicket {

    private final String owner;
    private final String repo;

    public GitHubTicket(String owner, String repo) {
        super();
        this.owner = owner;
        this.repo = repo;
    }

    public GitHubTicket(String owner, String repo, String json) throws JSONException {
        super(json);
        this.owner = owner;
        this.repo = repo;
    }

    public GitHubTicket(String owner, String repo, JSONObject json) {
        super(json);
        this.owner = owner;
        this.repo = repo;
    }

    /** The composite tracker key: {@code owner/repo#number}. */
    public String getCompositeKey() {
        Integer number = getNumber();
        return owner + "/" + repo + "#" + number;
    }

    @Override
    public String getKey() {
        return getCompositeKey();
    }

    @Override
    public String getTicketKey() {
        return getCompositeKey();
    }

    @Override
    public String getTicketLink() {
        return getHtmlUrl();
    }

    @Override
    public String getStatus() {
        return getState();
    }

    @Override
    public Status getStatusModel() {
        String state = getState();
        if (state == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.put("name", state);
        return new Status(json);
    }

    @Override
    public String getIssueType() {
        return "Issue";
    }

    /**
     * Priority is carried by a {@code priority:<level>} or {@code p<N>} label
     * when present; GitHub has no native priority field.
     */
    @Override
    public String getPriority() {
        for (String label : getLabels()) {
            String lower = label.toLowerCase();
            if (lower.startsWith("priority:")) {
                String value = label.substring("priority:".length()).trim();
                return value.isEmpty() ? null : capitalize(value);
            }
            if (lower.matches("p[0-4]")) {
                switch (lower) {
                    case "p0": return "Critical";
                    case "p1": return "High";
                    case "p2": return "Medium";
                    case "p3": return "Low";
                    case "p4": return "Low";
                    default: return null;
                }
            }
        }
        return null;
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        String priority = getPriority();
        return priority == null ? TicketPriority.NotSet : TicketPriority.byName(priority);
    }

    @Override
    public String getTicketTitle() {
        return getTitle();
    }

    @Override
    public String getTicketDescription() {
        return getBody();
    }

    @Override
    public String getTicketDependenciesDescription() {
        return null;
    }

    @Override
    public Date getCreated() {
        Long createdAt = getCreatedAt();
        return createdAt != null ? new Date(createdAt) : null;
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        return getJSONObject();
    }

    @Override
    public Long getUpdatedAsMillis() {
        return getUpdatedAt();
    }

    @Override
    public IUser getCreator() {
        return getAuthor();
    }

    @Override
    public Resolution getResolution() {
        // GitHub issues carry no resolution; closed == resolved.
        return null;
    }

    @Override
    public JSONArray getTicketLabels() {
        JSONArray result = new JSONArray();
        for (String label : getLabels()) {
            result.put(label);
        }
        return result;
    }

    @Override
    public Fields getFields() {
        // GitHub issues have no Jira-style custom fields bag.
        return null;
    }

    /** GitHub milestones are the closest analog of iterations. */
    @Override
    public ReportIteration getIteration() {
        JSONObject milestone = getJSONObject("milestone");
        if (milestone == null) {
            return null;
        }
        return new MilestoneIteration(milestone);
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        ReportIteration iteration = getIteration();
        return iteration != null ? Collections.singletonList(iteration) : Collections.emptyList();
    }

    @Override
    public double getProgress() {
        String state = getState();
        return "closed".equalsIgnoreCase(state) ? 1.0 : 0.0;
    }

    @Override
    public double getWeight() {
        return 0.0;
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        // GitHub issue attachments are inline links, not first-class objects.
        return Collections.emptyList();
    }

    @Override
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getCompositeKey()).append(" ").append(getTitle() != null ? getTitle() : "");
        String body = getBody();
        if (body != null && !body.isEmpty()) {
            sb.append("\n\n").append(body);
        }
        return sb.toString();
    }

    /** Maps a GitHub milestone JSON object onto {@link ReportIteration}. */
    private static class MilestoneIteration implements ReportIteration {
        private final JSONObject milestone;

        MilestoneIteration(JSONObject milestone) {
            this.milestone = milestone;
        }

        @Override
        public String getIterationName() {
            return milestone.optString("title", null);
        }

        @Override
        public int getId() {
            return milestone.optInt("number", 0);
        }

        @Override
        public Date getStartDate() {
            return null;
        }

        @Override
        public Date getEndDate() {
            String dueOn = milestone.optString("due_on", null);
            if (dueOn == null || dueOn.isEmpty()) {
                return null;
            }
            return com.github.istin.dmtools.common.utils.DateUtils.parseIsoDate(dueOn);
        }

        @Override
        public boolean isReleased() {
            return "closed".equalsIgnoreCase(milestone.optString("state", ""));
        }
    }
}
