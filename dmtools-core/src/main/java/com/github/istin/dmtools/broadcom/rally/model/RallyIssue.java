// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.tracker.model.Workflow;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.common.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class RallyIssue extends JSONModel implements ITicket, Comparable<RallyIssue> {

    private String lastStatusesUpdate = "";

    public String getLastStatusesUpdate() {
        return lastStatusesUpdate;
    }

    public void setLastStatusesUpdate(String lastStatusesUpdate) {
        this.lastStatusesUpdate = lastStatusesUpdate;
    }

    public RallyIssue() {
    }

    public RallyIssue(String json) throws JSONException {
        super(json);
    }

    public RallyIssue(JSONObject json) {
        super(json);
    }

    public String getType() {
        return getString(RallyFields._TYPE);
    }

    public String getRef() {
        return getString(RallyFields._REF);
    }

    public Iteration getIteration() {
        return getModel(Iteration.class, RallyFields.ITERATION);
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        return List.of(getIteration());
    }

    public String getFormattedId() {
        return getString(RallyFields.FORMATTED_ID);
    }

    @Override
    public String getStatus() throws IOException {
        Status statusModel = getStatusModel();
        if (statusModel == null) {
            return null;
        }
        return statusModel.getName();
    }

    public String getIterationName() {
        Iteration iteration = getIteration();
        if (iteration != null) {
            return iteration.getName();
        }
        return null;
    }
    @Override
    public Status getStatusModel() throws IOException {
        return getModel(Status.class, RallyFields.FLOW_STATE);
    }

    @Override
    public String getTicketKey() {
        return getFormattedId();
    }

    @Override
    public String getIssueType() throws IOException {
        return getType();
    }

    @Override
    public String getTicketLink() {
        return getRef().replace("/slm/webservice/v2.x/","/#/?detail=/");
    }

    @Override
    public int compareTo(@NotNull RallyIssue o) {
        Integer comparedProgressResult = ITicketProgress.compare(this, o);
        if (comparedProgressResult != null) return comparedProgressResult;

        String iterationName = getIterationName();
        String secondIterationName = o.getIterationName();

        Integer compareResult = StringUtils.sortByTwoStrings(iterationName, secondIterationName);
        if (compareResult != null) return compareResult;

        Integer typeCompare = StringUtils.sortByTwoStrings(getType(), o.getType());
        if (typeCompare != null) return typeCompare;

        int workFlowComparison = compareWorkflow(o);
        if (workFlowComparison != 0) {
            return workFlowComparison;
        } else {
            return comparePriority(o);
        }
    }

    private int comparePriority(@NotNull RallyIssue o) {
        TicketPriority priorityAsEnum = this.getPriorityAsEnum();
        TicketPriority secondObjectPriority = o.getPriorityAsEnum();
        return priorityAsEnum.compareTo(secondObjectPriority);
    }

    private int compareWorkflow(@NotNull RallyIssue o) {
        try {
            Workflow workflow = Workflow.safeValueOf(this.getStatus());
            Workflow secondaryWorkflow = Workflow.safeValueOf(o.getStatus());
            return secondaryWorkflow.compareTo(workflow);
        } catch (IOException ignored) {
            return 0;
        }
    }


    @Override
    public String getPriority() throws IOException {
        String priority = getString(RallyFields.PRIORITY);
        if (priority != null && !priority.isEmpty()) {
            return priority;
        }
        PriorityUserStory priorityUserStory = getModel(PriorityUserStory.class, RallyFields.PRIORITY_USER_STORY);
        if (priorityUserStory != null) {
            return priorityUserStory.getPriority();
        }
        return null;
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        try {
            return TicketPriority.byName(getPriority());
        } catch (IOException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public String toText() {
        return getJSONObject().toString();
    }

    @Override
    public String getTicketTitle() throws IOException {
        return getString(RallyFields._REF_OBJECT_NAME);
    }

    @Override
    public String getTicketDescription() {
        return getString(RallyFields.DESCRIPTION);
    }

    public void setTicketDescription(String description) {
        set(RallyFields.DESCRIPTION, description);
    }

    @Override
    public String getTicketDependenciesDescription() {
        return getString(RallyFields.BLOCKED_REASON);
    }

    @Override
    public Date getCreated() {
        return DateUtils.parseRallyDate(getString(RallyFields.CREATION_DATE));
    }

    public Calendar getCreatedAsCalendar() {
        Date created = getCreated();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(created);
        return calendar;
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        return getJSONObject();
    }

    @Override
    public Long getUpdatedAsMillis() {
        Date date = DateUtils.parseRallyDate(getString(RallyFields.LAST_UPDATE_DATE));
        return date.getTime();
    }

    public String getLastTimeUpdated() {
        return DateUtils.formatToRallyDate(getUpdatedAsMillis());
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
        JSONObject jsonObject = getJSONObject(RallyFields.TAGS);
        JSONArray result = new JSONArray();
        if (jsonObject != null) {
            JSONArray tagsNameArray = jsonObject.optJSONArray("_tagsNameArray");
            if (tagsNameArray != null) {
                for (int i = 0; i < tagsNameArray.length(); i++) {
                    result.put(tagsNameArray.getJSONObject(i).getString("Name"));
                }
            }
        }
        return result;
    }

    public RallyIssue getWorkProduct() {
        return getModel(RallyIssue.class, "WorkProduct");
    }

    public JSONArray getTagsRefs() {
        JSONObject jsonObject = getJSONObject(RallyFields.TAGS);
        JSONArray result = new JSONArray();
        if (jsonObject != null) {
            JSONArray tagsNameArray = jsonObject.optJSONArray("_tagsNameArray");
            if (tagsNameArray != null) {
                for (int i = 0; i < tagsNameArray.length(); i++) {
                    result.put(new JSONObject().put("_ref", new RallyTag(tagsNameArray.getJSONObject(i)).getRef()));
                }
            }
        }
        return result;
    }

    public JSONArray getTagsRefsWithoutTag(String tag) {
        JSONObject jsonObject = getJSONObject(RallyFields.TAGS);
        JSONArray result = new JSONArray();
        if (jsonObject != null) {
            JSONArray tagsNameArray = jsonObject.optJSONArray("_tagsNameArray");
            if (tagsNameArray != null) {
                for (int i = 0; i < tagsNameArray.length(); i++) {
                    RallyTag rallyTag = new RallyTag(tagsNameArray.getJSONObject(i));
                    if (!rallyTag.getName().equalsIgnoreCase(tag)) {
                        result.put(new JSONObject().put("_ref", rallyTag.getRef()));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Fields getFields() {
        return null;
    }

    @Override
    public double getProgress() throws IOException {
        return new ITicket.ITicketProgress.Impl().calc(this);
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        return Collections.emptyList();
    }

    public String getAttachmentsRefs() {
        return getJSONObject("Attachments").optString(RallyFields._REF);
    }

    @Override
    public double getWeight() {
        Double storyPoints = getDouble(RallyFields.PLAN_ESTIMATE);
        if (storyPoints == null) {
            return new PropertyReader().getDefaultTicketWeightIfNoSPs();
        }
        return storyPoints;
    }

    @Override
    public String getKey() {
        return getTicketKey();
    }

    public RevisionHistory getRevisionHistory() {
        return getModel(RevisionHistory.class, RallyFields.REVISION_HISTORY);
    }

    public String getProjectName() {
        return getJSONObject(RallyFields.PROJECT).optString("Name");
    }

    public RallyIssue getTestFolder() {
        return getModel(RallyIssue.class, "TestFolder");
    }
}
