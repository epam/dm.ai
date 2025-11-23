package com.github.istin.dmtools.microsoft.ado;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.TrackerClient.TrackerTicketFields;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import com.github.istin.dmtools.microsoft.ado.model.WorkItemComment;
import com.github.istin.dmtools.microsoft.ado.model.WorkItemChangelog;
import com.github.istin.dmtools.microsoft.ado.model.AdoUser;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Azure DevOps REST API Client.
 * Implements TrackerClient interface to provide work item management capabilities.
 *
 * API Documentation: https://learn.microsoft.com/en-us/rest/api/azure/devops/
 */
public abstract class AzureDevOpsClient extends AbstractRestClient implements TrackerClient<WorkItem> {

    private static final Logger logger = LogManager.getLogger(AzureDevOpsClient.class);
    private static final String API_VERSION = "7.0";

    private final String organization;
    private final String project;
    private boolean isLogEnabled = true;

    /**
     * Constructor for AzureDevOpsClient.
     *
     * @param organization ADO organization name
     * @param project ADO project name
     * @param patToken Personal Access Token for authentication
     * @throws IOException if initialization fails
     */
    public AzureDevOpsClient(String organization, String project, String patToken) throws IOException {
        super("https://dev.azure.com/" + organization, encodePatToken(patToken));
        this.organization = organization;
        this.project = project;
    }

    /**
     * Encode PAT token for Basic authentication.
     * Format: Base64(":{PAT}")
     */
    private static String encodePatToken(String patToken) {
        String credentials = ":" + patToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("Authorization", authorization)
                .header("Content-Type", "application/json");
    }

    @Override
    public String path(String path) {
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return basePath + path;
    }

    @Override
    public String getCacheFolderName() {
        return "cacheAzureDevOpsClient";
    }

    @Override
    public void setLogEnabled(boolean isLogEnabled) {
        this.isLogEnabled = isLogEnabled;
    }

    protected void log(String message) {
        if (isLogEnabled) {
            logger.info(message);
        }
    }

    // ========== Work Item Operations ==========

    @Override
    @MCPTool(
            name = "ado_get_work_item",
            description = "Get a specific Azure DevOps work item by ID with optional field filtering",
            integration = "ado",
            category = "work_item_management"
    )
    public WorkItem performTicket(
            @MCPParam(name = "id", description = "The work item ID (numeric)", required = true, example = "12345")
            String workItemId,
            @MCPParam(name = "fields", description = "Optional array of fields to include in the response", required = false)
            String[] fields
    ) throws IOException {
        GenericRequest request = createGetWorkItemRequest(workItemId, fields);
        String response = request.execute();

        if (response.contains("errorCode")) {
            log("Work item not found: " + workItemId);
            return null;
        }

        return createTicket(response);
    }

    protected GenericRequest createGetWorkItemRequest(String workItemId, String[] fields) {
        String path = String.format("/%s/_apis/wit/workitems/%s", project, workItemId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION);

        if (fields != null && fields.length > 0) {
            // Resolve field names if needed
            String[] resolvedFields = resolveFieldNames(fields);
            request.param("fields", String.join(",", resolvedFields));
        }

        return request;
    }

    @Override
    public WorkItem createTicket(String body) {
        try {
            return new WorkItem(body);
        } catch (Exception e) {
            logger.error("Failed to parse work item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve user-friendly field names to ADO field names.
     * Maps common names to System.*, Microsoft.VSTS.*, etc.
     */
    protected String[] resolveFieldNames(String[] fields) {
        String[] resolved = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            resolved[i] = resolveFieldName(fields[i]);
        }
        return resolved;
    }

    protected String resolveFieldName(String fieldName) {
        // If already in full format, return as-is
        if (fieldName.contains(".")) {
            return fieldName;
        }

        // Map common field names
        switch (fieldName.toLowerCase()) {
            case "id": return "System.Id";
            case "title": return "System.Title";
            case "description": return "System.Description";
            case "state": return "System.State";
            case "assignedto": return "System.AssignedTo";
            case "createdby": return "System.CreatedBy";
            case "createddate": return "System.CreatedDate";
            case "changeddate": return "System.ChangedDate";
            case "workitemtype": return "System.WorkItemType";
            case "priority": return "Microsoft.VSTS.Common.Priority";
            case "tags": return "System.Tags";
            case "areapath": return "System.AreaPath";
            case "iterationpath": return "System.IterationPath";
            case "storypoints": return "Microsoft.VSTS.Scheduling.StoryPoints";
            case "effort": return "Microsoft.VSTS.Scheduling.Effort";
            default: return fieldName; // Return as-is if not recognized
        }
    }

    // ========== Search and Query Operations ==========

    @Override
    @MCPTool(
            name = "ado_search_by_wiql",
            description = "Search for work items using WIQL (Work Item Query Language)",
            integration = "ado",
            category = "search"
    )
    public List<WorkItem> searchAndPerform(
            @MCPParam(name = "wiql", description = "WIQL query string", required = true, example = "SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug'")
            String wiqlQuery,
            @MCPParam(name = "fields", description = "Optional array of fields to include", required = false)
            String[] fields
    ) throws Exception {
        List<WorkItem> results = new ArrayList<>();
        searchAndPerform(results::add, wiqlQuery, fields);
        return results;
    }

    @Override
    public void searchAndPerform(JiraClient.Performer<WorkItem> performer, String wiqlQuery, String[] fields) throws Exception {
        // Execute WIQL query to get work item IDs
        String path = String.format("/%s/_apis/wit/wiql", project);
        GenericRequest wiqlRequest = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION);

        JSONObject wiqlBody = new JSONObject();
        wiqlBody.put("query", wiqlQuery);
        wiqlRequest.setBody(wiqlBody.toString());

        String response = wiqlRequest.post();
        JSONObject result = new JSONObject(response);
        JSONArray workItems = result.optJSONArray("workItems");

        if (workItems == null || workItems.length() == 0) {
            log("No work items found for query: " + wiqlQuery);
            return;
        }

        // Get detailed work items
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < workItems.length(); i++) {
            JSONObject wi = workItems.getJSONObject(i);
            ids.add(wi.getInt("id"));
        }

        // Batch fetch work items (ADO supports up to 200 IDs per request)
        int batchSize = 200;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<Integer> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            List<WorkItem> workItemsBatch = getWorkItemsBatch(batch, fields);

            for (WorkItem workItem : workItemsBatch) {
                performer.perform(workItem);
            }
        }
    }

    /**
     * Fetch multiple work items in a single request.
     */
    private List<WorkItem> getWorkItemsBatch(List<Integer> ids, String[] fields) throws IOException {
        // Convert Integer list to comma-separated string
        StringBuilder idsBuilder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) idsBuilder.append(",");
            idsBuilder.append(ids.get(i));
        }

        String path = String.format("/%s/_apis/wit/workitems", project);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION)
                .param("ids", idsBuilder.toString());

        if (fields != null && fields.length > 0) {
            String[] resolvedFields = resolveFieldNames(fields);
            request.param("fields", String.join(",", resolvedFields));
        }

        String response = request.execute();
        JSONObject result = new JSONObject(response);
        JSONArray workItems = result.optJSONArray("value");

        List<WorkItem> items = new ArrayList<>();
        if (workItems != null) {
            for (int i = 0; i < workItems.length(); i++) {
                items.add(new WorkItem(workItems.getJSONObject(i)));
            }
        }

        return items;
    }

    // ========== Comment Operations ==========

    @Override
    @MCPTool(
            name = "ado_get_comments",
            description = "Get all comments for a work item",
            integration = "ado",
            category = "comment_management"
    )
    public List<? extends IComment> getComments(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            ITicket ticket
    ) throws IOException {
        String path = String.format("/%s/_apis/wit/workItems/%s/comments", project, workItemId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION + "-preview");

        String response = request.execute();
        JSONObject result = new JSONObject(response);
        JSONArray comments = result.optJSONArray("comments");

        List<WorkItemComment> commentList = new ArrayList<>();
        if (comments != null) {
            for (int i = 0; i < comments.length(); i++) {
                commentList.add(new WorkItemComment(comments.getJSONObject(i)));
            }
        }

        return commentList;
    }

    @Override
    @MCPTool(
            name = "ado_post_comment",
            description = "Post a comment to a work item",
            integration = "ado",
            category = "comment_management"
    )
    public void postComment(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            @MCPParam(name = "comment", description = "The comment text", required = true)
            String comment
    ) throws IOException {
        String path = String.format("/%s/_apis/wit/workItems/%s/comments", project, workItemId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION + "-preview");

        JSONObject commentBody = new JSONObject();
        commentBody.put("text", comment);
        request.setBody(commentBody.toString());

        request.post();
        log("Posted comment to work item: " + workItemId);
    }

    @Override
    public void postCommentIfNotExists(String workItemId, String comment) throws IOException {
        List<? extends IComment> comments = getComments(workItemId, null);

        for (IComment existingComment : comments) {
            if (existingComment.getBody().contains(comment)) {
                log("Comment already exists on work item: " + workItemId);
                return;
            }
        }

        postComment(workItemId, comment);
    }

    @Override
    public void deleteCommentIfExists(String workItemId, String comment) throws IOException {
        // ADO comments cannot be deleted via API, only hidden or edited
        log("ADO does not support comment deletion via API");
    }

    // ========== Assignment Operations ==========

    @Override
    @MCPTool(
            name = "ado_assign_work_item",
            description = "Assign a work item to a user",
            integration = "ado",
            category = "work_item_management"
    )
    public String assignTo(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            @MCPParam(name = "userEmail", description = "The user email or display name", required = true)
            String userIdentity
    ) throws IOException {
        return updateWorkItem(workItemId, fields -> {
            fields.set("System.AssignedTo", userIdentity);
        });
    }

    // ========== Update Operations ==========

    @Override
    @MCPTool(
            name = "ado_update_description",
            description = "Update the description of a work item",
            integration = "ado",
            category = "work_item_management"
    )
    public String updateDescription(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            @MCPParam(name = "description", description = "The new description (HTML format)", required = true)
            String description
    ) throws IOException {
        return updateWorkItem(workItemId, fields -> {
            fields.set("System.Description", description);
        });
    }

    @Override
    public String updateTicket(String workItemId, FieldsInitializer fieldsInitializer) throws IOException {
        return updateWorkItem(workItemId, fieldsInitializer);
    }

    /**
     * Update a work item using JSON Patch format.
     */
    private String updateWorkItem(String workItemId, FieldsInitializer fieldsInitializer) throws IOException {
        // Collect field updates using TrackerTicketFields interface
        Map<String, Object> updates = new HashMap<>();
        if (fieldsInitializer != null) {
            fieldsInitializer.init(new TrackerTicketFields() {
                @Override
                public void set(String key, Object object) {
                    updates.put(key, object);
                }
            });
        }

        // Build JSON Patch operations
        JSONArray patchOps = new JSONArray();
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            JSONObject op = new JSONObject();
            op.put("op", "add"); // or "replace"
            op.put("path", "/fields/" + entry.getKey());
            op.put("value", entry.getValue());
            patchOps.put(op);
        }

        String path = String.format("/%s/_apis/wit/workitems/%s", project, workItemId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION);
        request.setBody(patchOps.toString());

        // Set Content-Type header for JSON Patch
        request.header("Content-Type", "application/json-patch+json");

        String response = patch(request);

        log("Updated work item: " + workItemId);
        return response;
    }

    // ========== State/Status Operations ==========

    @Override
    @MCPTool(
            name = "ado_move_to_state",
            description = "Move a work item to a specific state",
            integration = "ado",
            category = "work_item_management"
    )
    public String moveToStatus(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            @MCPParam(name = "state", description = "The target state name", required = true, example = "Active")
            String stateName
    ) throws IOException {
        return updateWorkItem(workItemId, fields -> {
            fields.set("System.State", stateName);
        });
    }

    // ========== Label/Tag Operations ==========

    @Override
    public void addLabelIfNotExists(com.github.istin.dmtools.common.model.ITicket ticket, String label) throws IOException {
        JSONArray labels = ticket.getTicketLabels();
        if (labels == null) {
            labels = new JSONArray();
        }

        boolean found = false;
        for (int i = 0; i < labels.length(); i++) {
            if (label.equalsIgnoreCase(labels.getString(i))) {
                found = true;
                break;
            }
        }

        if (!found) {
            // Add new tag to existing tags (semicolon-separated)
            StringBuilder tagsBuilder = new StringBuilder();
            for (int i = 0; i < labels.length(); i++) {
                if (i > 0) tagsBuilder.append(";");
                tagsBuilder.append(labels.getString(i));
            }
            if (labels.length() > 0) {
                tagsBuilder.append(";");
            }
            tagsBuilder.append(label);

            updateWorkItem(ticket.getTicketKey(), fields -> {
                fields.set("System.Tags", tagsBuilder.toString());
            });
        }
    }

    @Override
    public void deleteLabelInTicket(WorkItem ticket, String label) throws IOException {
        JSONArray labels = ticket.getTicketLabels();
        if (labels == null) {
            return;
        }

        StringBuilder tagsBuilder = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < labels.length(); i++) {
            String tag = labels.getString(i);
            if (!tag.equalsIgnoreCase(label)) {
                if (!first) tagsBuilder.append(";");
                tagsBuilder.append(tag);
                first = false;
            }
        }

        updateWorkItem(ticket.getTicketKey(), fields -> {
            fields.set("System.Tags", tagsBuilder.toString());
        });
    }

    // ========== Link/Relationship Operations ==========

    @Override
    @MCPTool(
            name = "ado_link_work_items",
            description = "Link two work items with a relationship (e.g., Parent-Child, Related, Tested By)",
            integration = "ado",
            category = "work_item_management"
    )
    public String linkIssueWithRelationship(
            @MCPParam(name = "sourceId", description = "The source work item ID", required = true)
            String sourceId,
            @MCPParam(name = "targetId", description = "The target work item ID to link to", required = true)
            String targetId,
            @MCPParam(name = "relationship", description = "Relationship type (e.g., 'parent', 'child', 'related', 'tested by', 'tests')", required = true, example = "parent")
            String relationship
    ) throws IOException {
        // ADO uses relation types like "System.LinkTypes.Hierarchy-Forward"
        String relationType = mapRelationshipType(relationship);

        JSONArray patchOps = new JSONArray();
        JSONObject op = new JSONObject();
        op.put("op", "add");
        op.put("path", "/relations/-");

        JSONObject value = new JSONObject();
        value.put("rel", relationType);
        value.put("url", basePath + "/_apis/wit/workItems/" + targetId);
        op.put("value", value);

        patchOps.put(op);

        String path = String.format("/%s/_apis/wit/workitems/%s", project, sourceId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION);
        request.setBody(patchOps.toString());
        request.header("Content-Type", "application/json-patch+json");

        String response = patch(request);

        log("Linked work items: " + sourceId + " -> " + targetId);
        return response;
    }

    /**
     * Map generic relationship types to ADO relation types.
     * 
     * In Azure DevOps:
     * - Hierarchy-Forward: source is child, target is parent (child → parent)
     * - Hierarchy-Reverse: source is parent, target is child (parent → child)
     */
    private String mapRelationshipType(String relationship) {
        if (relationship == null) {
            return "System.LinkTypes.Related";
        }

        switch (relationship.toLowerCase()) {
            case "parent":
                // source is parent, target is child → use Hierarchy-Reverse
                return "System.LinkTypes.Hierarchy-Reverse";
            case "child":
                // source is child, target is parent → use Hierarchy-Forward
                return "System.LinkTypes.Hierarchy-Forward";
            case "blocks":
                return "System.LinkTypes.Dependency-Forward";
            case "blocked by":
                return "System.LinkTypes.Dependency-Reverse";
            case "tested by":
            case "tests":
                // When linking from story to test case: story is tested by test case
                return "Microsoft.VSTS.Common.TestedBy-Forward";
            case "related":
            default:
                return "System.LinkTypes.Related";
        }
    }

    // ========== Create Operations ==========

    @Override
    @MCPTool(
            name = "ado_create_work_item",
            description = "Create a new work item in Azure DevOps",
            integration = "ado",
            category = "work_item_management"
    )
    public String createTicketInProject(
            @MCPParam(name = "project", description = "The project name", required = true)
            String projectName,
            @MCPParam(name = "workItemType", description = "The work item type (Bug, Task, User Story, etc.)", required = true)
            String workItemType,
            @MCPParam(name = "title", description = "The work item title", required = true)
            String title,
            @MCPParam(name = "description", description = "The work item description (HTML)", required = false)
            String description,
            FieldsInitializer fieldsInitializer
    ) throws IOException {
        // Build JSON Patch operations for creation
        JSONArray patchOps = new JSONArray();

        // Add title
        JSONObject titleOp = new JSONObject();
        titleOp.put("op", "add");
        titleOp.put("path", "/fields/System.Title");
        titleOp.put("value", title);
        patchOps.put(titleOp);

        // Add description if provided
        if (description != null && !description.isEmpty()) {
            JSONObject descOp = new JSONObject();
            descOp.put("op", "add");
            descOp.put("path", "/fields/System.Description");
            descOp.put("value", description);
            patchOps.put(descOp);
        }

        // Add custom fields
        if (fieldsInitializer != null) {
            Map<String, Object> customFields = new HashMap<>();
            fieldsInitializer.init(new TrackerTicketFields() {
                @Override
                public void set(String key, Object object) {
                    customFields.put(key, object);
                }
            });

            for (Map.Entry<String, Object> entry : customFields.entrySet()) {
                JSONObject op = new JSONObject();
                op.put("op", "add");
                op.put("path", "/fields/" + entry.getKey());
                op.put("value", entry.getValue());
                patchOps.put(op);
            }
        }

        String path = String.format("/%s/_apis/wit/workitems/$%s", projectName, workItemType);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION);
        request.setBody(patchOps.toString());
        request.header("Content-Type", "application/json-patch+json");

        String response = patch(request);

        log("Created work item: " + title);
        return response;
    }

    // ========== Changelog/History Operations ==========

    @Override
    @MCPTool(
            name = "ado_get_changelog",
            description = "Get the complete history/changelog of a work item",
            integration = "ado",
            category = "history"
    )
    public IChangelog getChangeLog(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            @MCPParam(name = "ticket", description = "Optional work item object (can be null)", required = false)
            com.github.istin.dmtools.common.model.ITicket ticket
    ) throws IOException {
        // Get work item updates (revisions) from ADO API
        String path = String.format("/%s/_apis/wit/workitems/%s/updates", project, workItemId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION);

        String response = request.execute();
        
        if (response == null || response.trim().isEmpty()) {
            log("No changelog data found for work item: " + workItemId);
            return new WorkItemChangelog(new JSONObject().put("value", new JSONArray()));
        }

        try {
            return new WorkItemChangelog(response);
        } catch (Exception e) {
            logger.error("Failed to parse changelog for work item: " + workItemId, e);
            throw new IOException("Failed to parse changelog: " + e.getMessage(), e);
        }
    }

    // ========== Metadata Operations ==========

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException {
        // Get iteration paths for the project
        String path = String.format("/%s/_apis/wit/classificationnodes/iterations", projectCode);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION)
                .param("$depth", 2);

        // TODO: Parse and return iteration paths
        throw new UnsupportedOperationException("getFixVersions not yet implemented for ADO");
    }

    @Override
    public List<? extends com.github.istin.dmtools.common.model.ITicket> getTestCases(com.github.istin.dmtools.common.model.ITicket ticket) throws IOException {
        throw new UnsupportedOperationException("getTestCases not implemented for ADO");
    }

    // ========== Attachment Operations ==========

    @Override
    public void attachFileToTicket(String workItemId, String name, String contentType, File file) throws IOException {
        // TODO: Implement file attachment
        throw new UnsupportedOperationException("attachFileToTicket not yet implemented for ADO");
    }

    // ========== Helper Methods ==========

    @Override
    public String tag(String initiator) {
        return "[ADO]";
    }

    @Override
    public String getTextFieldsOnly(com.github.istin.dmtools.common.model.ITicket ticket) {
        StringBuilder text = new StringBuilder();
        try {
            text.append(ticket.getTicketTitle()).append("\n");
            String description = ticket.getTicketDescription();
            if (description != null) {
                // Strip HTML tags
                text.append(description.replaceAll("<[^>]*>", ""));
            }
        } catch (IOException e) {
            logger.error("Error getting text fields", e);
        }
        return text.toString();
    }

    @Override
    public String buildUrlToSearch(String query) {
        // ADO doesn't have a simple URL-based search, return work items page
        return basePath + "/" + project + "/_workitems";
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    @Override
    public String getTicketBrowseUrl(String workItemId) {
        return basePath + "/" + project + "/_workitems/edit/" + workItemId;
    }

    @Override
    public String[] getDefaultQueryFields() {
        return new String[]{
            "System.Id",
            "System.Title",
            "System.State",
            "System.WorkItemType",
            "System.AssignedTo",
            "System.CreatedDate",
            "System.ChangedDate",
            "Microsoft.VSTS.Common.Priority"
        };
    }

    @Override
    public String[] getExtendedQueryFields() {
        return new String[]{
            "System.Id",
            "System.Title",
            "System.Description",
            "System.State",
            "System.WorkItemType",
            "System.AssignedTo",
            "System.CreatedBy",
            "System.CreatedDate",
            "System.ChangedDate",
            "System.Tags",
            "System.AreaPath",
            "System.IterationPath",
            "Microsoft.VSTS.Common.Priority",
            "Microsoft.VSTS.Scheduling.StoryPoints",
            "Microsoft.VSTS.Scheduling.Effort"
        };
    }

    @Override
    public String getDefaultStatusField() {
        return "System.State";
    }

    @Override
    public TextType getTextType() {
        return TextType.HTML;
    }

    // ========== Image/Attachment Helper Methods ==========

    @Override
    public boolean isValidImageUrl(String url) throws IOException {
        // Check if URL is from this ADO instance and is an image attachment
        return url.startsWith(getBasePath()) &&
               ((url.endsWith("png") || url.endsWith("jpg") || url.endsWith("jpeg") ||
                 url.endsWith("gif") || url.endsWith("bmp") || url.endsWith("svg")) ||
                isImageAttachment(url));
    }

    /**
     * Check if the URL points to an image attachment by checking Content-Type header.
     */
    private boolean isImageAttachment(String attachmentUrl) throws IOException {
        try {
            okhttp3.Request request = sign(new okhttp3.Request.Builder()
                    .url(attachmentUrl)
                    .head()) // Use HEAD request to only get headers
                    .build();

            try (okhttp3.Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }

                // Get the content type from the HTTP header
                String contentType = response.header("Content-Type");
                // Check if the content type indicates an image
                return contentType != null && contentType.startsWith("image/");
            }
        } catch (Exception e) {
            logger.warn("Failed to check if URL is image attachment: {}", attachmentUrl, e);
            return false;
        }
    }

    @Override
    @MCPTool(
            name = "ado_download_attachment",
            description = "Download an ADO work item attachment by URL and save it as a file",
            integration = "ado",
            category = "file_management"
    )
    public File convertUrlToFile(
            @MCPParam(name = "href", description = "The attachment URL to download", required = true)
            String href
    ) throws IOException {
        File targetFile = getCachedFile(href);

        // Ensure the parent directory exists before downloading
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created && !parentDir.exists()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
            log("Created directory for download: " + parentDir.getAbsolutePath());
        }

        return RestClient.Impl.downloadFile(this, new GenericRequest(this, href), targetFile);
    }

    @MCPTool(
            name = "ado_get_my_profile",
            description = "Get the current user's profile information from Azure DevOps",
            integration = "ado",
            category = "user_management"
    )
    public IUser getMyProfile() throws IOException {
        // Use the Profile API endpoint which is at app.vssps.visualstudio.com (not dev.azure.com)
        // This is the correct endpoint for getting current user profile with PAT token
        String profileUrl = "https://app.vssps.visualstudio.com/_apis/profile/profiles/me";
        GenericRequest request = new GenericRequest(this, profileUrl)
                .param("api-version", "7.1");
        
        // Override the base path for this specific request since profile API uses different base URL
        // We need to use the authorization header but with the profile API base URL
        String response = executeProfileRequest(profileUrl, request);
        JSONObject result = new JSONObject(response);
        
        // The profile response contains user information
        return new AdoUser(result);
    }
    
    /**
     * Execute a request to the Profile API which uses a different base URL.
     * Profile API is at app.vssps.visualstudio.com instead of dev.azure.com
     * 
     * @param url The base URL (must not contain query parameters)
     * @param request The GenericRequest (not used directly, but kept for API compatibility)
     * @return Response body as string
     * @throws IOException if request fails
     * @throws IllegalArgumentException if URL is invalid
     */
    private String executeProfileRequest(String url, GenericRequest request) throws IOException {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        
        HttpUrl urlWithApiVersion = httpUrl.newBuilder()
                .addQueryParameter("api-version", "7.1")
                .build();
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(urlWithApiVersion)
                .header("Authorization", authorization)
                .header("User-Agent", "DMTools");
        
        Request httpRequest = requestBuilder.get().build();
        
        try (okhttp3.Response response = getClient().newCall(httpRequest).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : "";
            } else {
                throw printAndCreateException(httpRequest, response);
            }
        }
    }

    /**
     * Get the cached file for an attachment URL.
     * Creates a unique filename based on MD5 hash of the URL.
     */
    private File getCachedFile(String url) {
        String value = org.apache.commons.codec.digest.DigestUtils.md5Hex(url);
        String imageExtension = RestClient.Impl.getFileImageExtension(url);
        return new File(getCacheFolderName() + "/" + value + imageExtension);
    }

    // ========== Helper Methods ==========

    /**
     * Override patch method to support JSON Patch content type required by Azure DevOps.
     * Azure DevOps requires "application/json-patch+json" for PATCH requests.
     * 
     * This override maintains retry logic from the base class for recoverable connection errors.
     */
    @Override
    public String patch(GenericRequest genericRequest) throws IOException {
        return patch(genericRequest, 0);
    }
    
    /**
     * Private patch method with retry logic for recoverable connection errors.
     * Implements exponential backoff retry mechanism like the base class.
     * 
     * @param genericRequest The request to execute
     * @param retryCount Current retry attempt (0 for first attempt)
     * @return Response body as string
     * @throws IOException if request fails after all retries
     */
    private String patch(GenericRequest genericRequest, int retryCount) throws IOException {
        String url = genericRequest.url();
        
        // Check if a custom Content-Type header is set
        String contentType = genericRequest.getHeaders().get("Content-Type");
        MediaType mediaType;
        
        if (contentType != null && contentType.contains("json-patch")) {
            // Use the custom Content-Type for JSON Patch
            mediaType = MediaType.parse(contentType);
        } else {
            // Default to JSON Patch for ADO work item operations
            mediaType = MediaType.parse("application/json-patch+json; charset=utf-8");
        }
        
        RequestBody body = RequestBody.create(mediaType, genericRequest.getBody());
        
        Request.Builder requestBuilder = sign(new Request.Builder())
                .url(url)
                .header("User-Agent", "DMTools");
        
        // Apply custom headers (including Content-Type if specified)
        for (Map.Entry<String, String> header : genericRequest.getHeaders().entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }
        
        Request request = requestBuilder
                .patch(body)
                .build();
        
        try (okhttp3.Response response = getClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : "";
            } else {
                throw AbstractRestClient.printAndCreateException(request, response);
            }
        } catch (IOException e) {
            logger.warn("PATCH connection error for URL: {} - Error: {} (Attempt: {}/3)", url, e.getMessage(), retryCount + 1);
            
            // Check if it's a recoverable connection error
            boolean isRecoverableError = isRecoverableConnectionError(e);
            
            // Maximum of 3 attempts (2 retries)
            final int MAX_RETRIES = 2;
            
            if (isRecoverableError && retryCount < MAX_RETRIES) {
                logger.info("Retrying PATCH request after connection error: {} (Retry {}/{})", e.getClass().getSimpleName(), retryCount + 1, MAX_RETRIES);
                try {
                    // Exponential backoff: 200ms, 400ms, 800ms
                    long waitTime = 200L * (long) Math.pow(2, retryCount);
                    logger.debug("Waiting {}ms before PATCH retry", waitTime);
                    Thread.sleep(waitTime);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("PATCH request interrupted during retry", interruptedException);
                }
                return patch(genericRequest, retryCount + 1);
            } else {
                if (!isRecoverableError) {
                    logger.error("Non-recoverable PATCH connection error for URL: {}", url, e);
                } else if (retryCount >= MAX_RETRIES) {
                    logger.error("Max PATCH retries ({}) exceeded for URL: {}. Final error: {}", MAX_RETRIES, url, e.getMessage());
                }
                throw e;
            }
        }
    }
}

