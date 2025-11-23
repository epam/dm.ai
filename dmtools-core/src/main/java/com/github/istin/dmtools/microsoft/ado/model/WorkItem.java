package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.LLMOptimizedJson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Represents an Azure DevOps Work Item.
 * Maps ADO work items to DMTools ITicket interface.
 */
public class WorkItem extends JSONModel implements ITicket {

    private static final Set<String> BLACKLISTED_FIELDS = Set.of("url", "id", "self", "_links", "rev", "commentVersionRef");

    public WorkItem() {
    }

    public WorkItem(String json) throws JSONException {
        super(json);
    }

    public WorkItem(JSONObject json) {
        super(json);
    }

    /**
     * Get the work item ID (numeric).
     */
    public Integer getId() {
        return getInt("id");
    }

    /**
     * Get the work item fields as a JSONObject.
     */
    public JSONObject getFieldsObject() {
        return getJSONObject("fields");
    }

    /**
     * Get a field value from the fields object.
     */
    private Object getField(String fieldName) {
        JSONObject fields = getFieldsObject();
        if (fields == null) {
            return null;
        }
        return fields.opt(fieldName);
    }

    private String getFieldAsString(String fieldName) {
        Object value = getField(fieldName);
        return value != null ? value.toString() : null;
    }

    @Override
    public String getTicketKey() {
        // ADO uses numeric IDs, but we'll use them as string keys
        Integer id = getId();
        return id != null ? id.toString() : null;
    }

    @Override
    public String getStatus() throws IOException {
        return getFieldAsString("System.State");
    }

    @Override
    public Status getStatusModel() throws IOException {
        String state = getFieldAsString("System.State");
        if (state == null) {
            return null;
        }
        return new WorkItemStatus(state);
    }

    @Override
    public String getIssueType() throws IOException {
        return getFieldAsString("System.WorkItemType");
    }

    @Override
    public String getPriority() throws IOException {
        Object priority = getField("Microsoft.VSTS.Common.Priority");
        if (priority != null) {
            // ADO uses numeric priority (1=highest, 4=lowest)
            int priorityNum = Integer.parseInt(priority.toString());
            switch (priorityNum) {
                case 1: return "Critical";
                case 2: return "High";
                case 3: return "Medium";
                case 4: return "Low";
                default: return "Medium";
            }
        }
        return "Medium";
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        try {
            return TicketPriority.byName(getPriority());
        } catch (IOException e) {
            return TicketPriority.NotSet;
        }
    }

    @Override
    public String getTicketTitle() throws IOException {
        return getFieldAsString("System.Title");
    }

    @Override
    public String getTicketDescription() {
        String description = getFieldAsString("System.Description");
        if (description == null) {
            description = getFieldAsString("Microsoft.VSTS.TCM.ReproSteps");
        }
        return description;
    }

    @Override
    public String getTicketDependenciesDescription() {
        return null;
    }

    @Override
    public Date getCreated() {
        String createdDate = getFieldAsString("System.CreatedDate");
        if (createdDate != null) {
            return parseDate(createdDate);
        }
        return null;
    }

    @Override
    public Long getUpdatedAsMillis() {
        String changedDate = getFieldAsString("System.ChangedDate");
        if (changedDate != null) {
            Date date = parseDate(changedDate);
            return date != null ? date.getTime() : null;
        }
        return null;
    }

    @Override
    public String getTicketLink() {
        JSONObject links = getJSONObject("_links");
        if (links != null) {
            JSONObject html = links.optJSONObject("html");
            if (html != null) {
                return html.optString("href");
            }
        }
        // Fallback: construct URL from base path and ID
        String url = getString("url");
        if (url != null && url.contains("/_apis/")) {
            String basePath = url.substring(0, url.indexOf("/_apis/"));
            return basePath + "/_workitems/edit/" + getId();
        }
        return url;
    }

    @Override
    public IUser getCreator() {
        String createdBy = getFieldAsString("System.CreatedBy");
        if (createdBy != null) {
            return new WorkItemUser(createdBy);
        }
        return null;
    }

    @Override
    public Resolution getResolution() {
        String reason = getFieldAsString("System.Reason");
        if (reason != null) {
            return new WorkItemResolution(reason);
        }
        return null;
    }

    @Override
    public JSONArray getTicketLabels() {
        String tags = getFieldAsString("System.Tags");
        if (tags == null || tags.trim().isEmpty()) {
            return new JSONArray();
        }
        // ADO tags are semicolon-separated
        String[] tagArray = tags.split(";");
        JSONArray result = new JSONArray();
        for (String tag : tagArray) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                result.put(trimmed);
            }
        }
        return result;
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        return getFieldsObject();
    }

    @Override
    public Fields getFields() {
        // ADO doesn't use Jira Fields model, return null
        return null;
    }

    @Override
    public ReportIteration getIteration() {
        String iterationPath = getFieldAsString("System.IterationPath");
        if (iterationPath != null) {
            return new WorkItemIteration(iterationPath);
        }
        return null;
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        ReportIteration iteration = getIteration();
        if (iteration != null) {
            List<ReportIteration> list = new ArrayList<>();
            list.add(iteration);
            return list;
        }
        return List.of();
    }

    @Override
    public double getProgress() throws IOException {
        String state = getFieldAsString("System.State");
        if (state == null) {
            return 0.0;
        }
        // Simple progress calculation based on state
        switch (state.toLowerCase()) {
            case "new":
            case "proposed":
                return 0.0;
            case "active":
            case "committed":
            case "in progress":
                return 0.5;
            case "resolved":
            case "ready":
                return 0.9;
            case "closed":
            case "done":
            case "completed":
                return 1.0;
            default:
                return 0.3;
        }
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        // TODO: Implement attachment support
        return List.of();
    }

    @Override
    public double getWeight() {
        Object effort = getField("Microsoft.VSTS.Scheduling.Effort");
        if (effort == null) {
            effort = getField("Microsoft.VSTS.Scheduling.StoryPoints");
        }
        if (effort != null) {
            try {
                return Double.parseDouble(effort.toString());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    @Override
    public String getKey() {
        return getTicketKey();
    }

    private String llmFormatted;

    @Override
    public String toText() {
        if (llmFormatted == null) {
            llmFormatted = LLMOptimizedJson.formatWellFormed(toString(), BLACKLISTED_FIELDS);
        }
        return llmFormatted;
    }

    /**
     * Parse ISO 8601 date string to Date object using DateUtils.
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return DateUtils.smartParseDate(dateStr);
    }

    /**
     * Simple Status implementation for ADO work item states.
     */
    private static class WorkItemStatus extends Status {
        private final String stateName;
        private final String statusCategory;

        public WorkItemStatus(String name) {
            super(createStatusJson(name));
            this.stateName = name;
            this.statusCategory = calculateStatusCategory(name);
        }

        private static JSONObject createStatusJson(String name) {
            try {
                JSONObject json = new JSONObject();
                json.put("name", name);
                JSONObject categoryJson = new JSONObject();
                categoryJson.put("name", calculateStatusCategory(name));
                json.put("statusCategory", categoryJson);
                return json;
            } catch (JSONException e) {
                return new JSONObject();
            }
        }

        @Override
        public String getName() {
            return stateName;
        }

        public String getStatusCategory() {
            return statusCategory;
        }

        private static String calculateStatusCategory(String name) {
            if (name == null) {
                return "Unknown";
            }
            String lower = name.toLowerCase();
            if (lower.contains("new") || lower.contains("proposed")) {
                return "To Do";
            } else if (lower.contains("active") || lower.contains("progress") || lower.contains("committed")) {
                return "In Progress";
            } else if (lower.contains("closed") || lower.contains("done") || lower.contains("completed") || lower.contains("resolved")) {
                return "Done";
            }
            return "In Progress";
        }
    }

    /**
     * Simple User implementation for ADO users.
     */
    private static class WorkItemUser implements IUser {
        private final String displayName;

        public WorkItemUser(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getID() {
            return displayName; // ADO uses display names in fields
        }

        @Override
        public String getFullName() {
            return displayName;
        }

        @Override
        public String getEmailAddress() {
            return null; // Not available in basic field
        }
    }

    /**
     * Simple Resolution implementation for ADO.
     */
    private static class WorkItemResolution extends Resolution {
        private final String reason;

        public WorkItemResolution(String reason) {
            super(createResolutionJson(reason));
            this.reason = reason;
        }

        private static JSONObject createResolutionJson(String reason) {
            try {
                JSONObject json = new JSONObject();
                json.put("name", reason);
                return json;
            } catch (JSONException e) {
                return new JSONObject();
            }
        }

        @Override
        public String getName() {
            return reason;
        }
    }

    /**
     * Simple Iteration implementation for ADO iteration paths.
     */
    private static class WorkItemIteration implements ReportIteration {
        private final String path;

        public WorkItemIteration(String path) {
            this.path = path;
        }

        @Override
        public String getIterationName() {
            if (path == null) {
                return null;
            }
            // Get last segment of path (e.g., "Project\\Sprint 1" -> "Sprint 1")
            String[] parts = path.split("\\\\");
            return parts[parts.length - 1];
        }

        @Override
        public int getId() {
            return 0; // ADO iterations don't have numeric IDs in the path
        }

        @Override
        public Date getStartDate() {
            return null; // Not available from path alone
        }

        @Override
        public Date getEndDate() {
            return null; // Not available from path alone
        }

        @Override
        public boolean isReleased() {
            return false; // Not available from path alone
        }
    }
}

