package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Fields extends JSONModel implements TrackerClient.TrackerTicketFields {

    public static final String SUMMARY = "summary";
    public static final String DESCRIPTION = "description";
    public static final String COMPONENTS = "components";
    public static final String STATUS = "status";
    public static final String PRIORITY = "priority";
    public static final String ATTACHMENT = "attachment";
    public static final String WATCHES = "watches";
    public static final String ISSUETYPE = "issuetype";
    public static final String RESOLUTION = "resolution";
    public static final String STORY_POINTS = "customfield_10004";
    public static final String FIXVERSIONS = "fixVersions";
    public static final String ISSUE_LINKS = "issuelinks";
    public static final String LABELS = "labels";
    public static final String ASSIGNEE = "assignee";
    public static final String CREATOR = "creator";
    public static final String REPORTER = "reporter";
    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String DUEDATE = "duedate";
    public static final String PARENT = "parent";
    public static final String AFFECTS_VERSIONS = "versions";

    public Fields() {
    }

    public Fields(String json) throws JSONException {
        super(json);
    }

    public Fields(JSONObject json) {
        super(json);
    }

    public Status getStatus() {
        return getModel(Status.class, STATUS);
    }

    public Priority getPriority() {
        return getModel(Priority.class, PRIORITY);
    }

    public List<Attachment> getAttachments() {
        return getModels(Attachment.class, "attachment");
    }

    public Calendar getDueDate() {
        return DateUtils.calendar(DateUtils.smartParseDate(getDueDateAsString()));
    }

    public String getDueDateAsString() {
        return getString(Fields.DUEDATE);
    }

    public void setDueDate(Calendar dueDate) {
        set(Fields.DUEDATE, DateUtils.formatToJiraDate(dueDate));
    }

    public String getSummary() {
        String summary = getString(SUMMARY);
        if (summary == null) {
            return "";
        }
        return summary;
    }

    public String getDescription() {
        return getString(DESCRIPTION);
    }

    public IssueType getIssueType() {
        return getModel(IssueType.class, ISSUETYPE);
    }

    public Project getProject() {
        return getModel(Project.class, "project");
    }

    public JSONArray getLabels() {
        return getJSONArray("labels");
    }

    public JSONArray getLabelsByKey(String key) {
        JSONArray jsonArray = getJSONObject().optJSONArray(key);
        if (jsonArray == null) {
            jsonArray = new JSONArray();
        }
        return jsonArray;
    }

    public boolean isLabelsContains(String label) {
        JSONArray labels = getLabels();
        if (labels != null) {
            for (int i = 0; i < labels.length(); i++) {
                if (labels.getString(i).equalsIgnoreCase(label)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<IssueLink> getIssueLinks() {
        return getModels(IssueLink.class, "issuelinks");
    }

    public List<Component> getComponents() {
        return getModels(Component.class, Fields.COMPONENTS);
    }

    public Resolution getResolution() {
        return getModel(Resolution.class, Fields.RESOLUTION);
    }

    public Ticket getParent() {
        return getModel(Ticket.class, PARENT);
    }


    public List<FixVersion> getFixVersions() {
        return getModels(FixVersion.class, Fields.FIXVERSIONS);
    }

    public List<FixVersion> getAffectsVersions() {
        return getModels(FixVersion.class, Fields.AFFECTS_VERSIONS);
    }

    public Assignee getAssignee() {
        return getModel(Assignee.class, ASSIGNEE);
    }

    public String getAssigneeName() {
        Assignee assignee = getAssignee();
        if (assignee == null) {
            return "Unassigned";
        }
        return assignee.getFullName();
    }

    public Assignee getCreator() {
        return getModel(Assignee.class, CREATOR);
    }

    public Assignee getReporter() {
        return getModel(Assignee.class, REPORTER);
    }

    public Date getCreated() {
        return getCreatedUtils(this);
    }

    @Nullable
    public static Date getCreatedUtils(JSONModel model) {
        String created = model.getString("created");
        return DateUtils.smartParseDate(created);
    }

    public Date getUpdated() {
        String created = getString("updated");
        if (created == null) {
            return null;
        }
        return DateUtils.smartParseDate(created);
    }

    public Long getUpdatedAsMillis() {
        Date updated = getUpdated();
        if (updated != null) {
            return updated.getTime();
        }
        return null;
    }

    public int getStoryPoints() {
        if (IssueType.isBug(getIssueType().getName())) {
            return 1;
        }
        PropertyReader propertyReader = new PropertyReader();
        String[] defaultTicketStoryPointsFields = propertyReader.getDefaultTicketStoryPointsFields();
        if (defaultTicketStoryPointsFields != null) {
            for (String field : defaultTicketStoryPointsFields) {
                int sp = getJSONObject().optInt(field, -1);
                if (sp != -1) {
                    return sp;
                }
            }
        }
        return getJSONObject().optInt(Fields.STORY_POINTS, propertyReader.getDefaultTicketWeightIfNoSPs());
    }
}