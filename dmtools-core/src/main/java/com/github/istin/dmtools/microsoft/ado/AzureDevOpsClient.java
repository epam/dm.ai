// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

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
            category = "work_item_management",
            aliases = {"tracker_get_ticket"}
    )
    public WorkItem performTicket(
            @MCPParam(name = "id", description = "The work item ID (numeric)", required = true, example = "12345", aliases = {"key"})
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
            // Note: Cannot use $expand with fields parameter in ADO API
            // Attachments will be extracted from description HTML
        } else {
            // When no fields filter is used, include relations for attachments
            request.param("$expand", "relations");
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
     * Enrich a work item with relations (attachments, links, etc.) by fetching them separately.
     * This is needed because ADO API doesn't allow $expand=relations with fields parameter.
     * 
     * @param workItem The work item to enrich
     * @throws IOException if the API call fails
     */
    public void enrichWorkItemWithRelations(WorkItem workItem) throws IOException {
        if (workItem == null) {
            return;
        }

        String workItemId = workItem.getTicketKey();
        if (workItemId == null) {
            return;
        }

        log("Fetching relations for work item: " + workItemId);

        // Fetch work item with only relations (no fields filter)
        String path = String.format("/%s/_apis/wit/workitems/%s", project, workItemId);
        GenericRequest request = new GenericRequest(this, path(path))
                .param("api-version", API_VERSION)
                .param("$expand", "relations");

        String response = request.execute();
        JSONObject fullWorkItem = new JSONObject(response);

        // Extract relations and add them to the work item
        JSONArray relations = fullWorkItem.optJSONArray("relations");
        if (relations != null) {
            workItem.getJSONObject().put("relations", relations);
            log("Added " + relations.length() + " relations to work item " + workItemId);
        } else {
            log("No relations found for work item " + workItemId);
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
            case "summary": return "System.Title"; // Jira field name -> ADO equivalent
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
            category = "search",
            aliases = {"tracker_search"}
    )
    public List<WorkItem> searchAndPerform(
            @MCPParam(name = "wiql", description = "WIQL query string", required = true, example = "SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug'", aliases = {"jql", "query"})
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
            // Note: Cannot use $expand with fields parameter in ADO API
        } else {
            // When no fields filter is used, include relations for attachments
            request.param("$expand", "relations");
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
            category = "comment_management",
            aliases = {"tracker_get_comments"}
    )
    public List<? extends IComment> getComments(
            @MCPParam(name = "id", description = "The work item ID", required = true, aliases = {"key"})
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
            category = "comment_management",
            aliases = {"tracker_post_comment"}
    )
    public void postComment(
            @MCPParam(name = "id", description = "The work item ID", required = true, aliases = {"key"})
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
            category = "work_item_management",
            aliases = {"tracker_assign_ticket"}
    )
    public String assignTo(
            @MCPParam(name = "id", description = "The work item ID", required = true, aliases = {"key"})
            String workItemId,
            @MCPParam(name = "userEmail", description = "The user email or display name", required = true, aliases = {"accountId"})
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

    @MCPTool(
            name = "ado_update_tags",
            description = "Update the tags of a work item (semicolon-separated string)",
            integration = "ado",
            category = "work_item_management"
    )
    public String updateTags(
            @MCPParam(name = "id", description = "The work item ID", required = true)
            String workItemId,
            @MCPParam(name = "tags", description = "Tags as semicolon-separated string (e.g., 'tag1;tag2;tag3')", required = true)
            String tags
    ) throws IOException {
        return updateWorkItem(workItemId, fields -> {
            fields.set("System.Tags", tags);
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

    @Override
    public String resolveFieldName(String ticketKey, String fieldName) throws IOException {
        // ADO field names use namespace prefixes like "System.FieldName" or "Custom.FieldName"
        // If fieldName already contains a dot (namespace prefix), use as-is
        if (fieldName.contains(".")) {
            return fieldName;
        }
        
        // For common field names without prefix, assume System namespace
        // Common ADO System fields: Title, Description, State, AssignedTo, CreatedBy, CreatedDate, etc.
        return "System." + fieldName;
    }

    // ========== State/Status Operations ==========

    @Override
    @MCPTool(
            name = "ado_move_to_state",
            description = "Move a work item to a specific state",
            integration = "ado",
            category = "work_item_management",
            aliases = {"tracker_move_to_status"}
    )
    public String moveToStatus(
            @MCPParam(name = "id", description = "The work item ID", required = true, aliases = {"key"})
            String workItemId,
            @MCPParam(name = "state", description = "The target state name", required = true, example = "Active", aliases = {"statusName"})
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
            category = "work_item_management",
            aliases = {"tracker_link_tickets"}
    )
    public String linkIssueWithRelationship(
            @MCPParam(name = "sourceId", description = "The source work item ID", required = true, aliases = {"sourceKey"})
            String sourceId,
            @MCPParam(name = "targetId", description = "The target work item ID to link to", required = true, aliases = {"anotherKey"})
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
     * 
     * Supports both simple names (e.g., "parent", "child") and full ADO relation types
     * (e.g., "System.LinkTypes.Hierarchy-Reverse")
     */
    private String mapRelationshipType(String relationship) {
        if (relationship == null) {
            return "System.LinkTypes.Related";
        }

        String relLower = relationship.toLowerCase();
        
        // If already a full ADO relation type, return as-is
        if (relLower.startsWith("system.linktypes.") || relLower.startsWith("microsoft.vsts.common.")) {
            return relationship; // Return original case-preserved value
        }

        // Map simple names to ADO relation types
        switch (relLower) {
            case "parent":
                // source is parent, target is child → use Hierarchy-Reverse
                return "System.LinkTypes.Hierarchy-Reverse";
            case "child":
                // source is child, target is parent → use Hierarchy-Forward
                return "System.LinkTypes.Hierarchy-Forward";
            case "blocks":
                return "System.LinkTypes.Dependency-Forward";
            case "blocked by":
            case "blockedby":
                return "System.LinkTypes.Dependency-Reverse";
            case "tested by":
            case "testedby":
            case "is tested by":
            case "istestedby":
            case "tests":
                // When linking from story to test case: story is tested by test case
                return "Microsoft.VSTS.Common.TestedBy-Forward";
            case "related":
            case "relates to":
            default:
                return "System.LinkTypes.Related";
        }
    }

    // ========== Create Operations ==========

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        // Delegate to the MCP tool method with fieldsJson=null
        // Map parameters: project -> projectName, issueType -> workItemType, summary -> title
        return createWorkItemWithFieldsJson(project, issueType, summary, description, null, fieldsInitializer);
    }

    @MCPTool(
            name = "ado_create_work_item",
            description = "Create a new work item in Azure DevOps",
            integration = "ado",
            category = "work_item_management",
            aliases = {"tracker_create_ticket"}
    )
    public String createWorkItemWithFieldsJson(
            @MCPParam(name = "project", description = "The project name", required = true)
            String projectName,
            @MCPParam(name = "workItemType", description = "The work item type (Bug, Task, User Story, etc.)", required = true, aliases = {"issueType"})
            String workItemType,
            @MCPParam(name = "title", description = "The work item title", required = true, aliases = {"summary"})
            String title,
            @MCPParam(name = "description", description = "The work item description (HTML)", required = false)
            String description,
            @MCPParam(name = "fieldsJson", description = "Additional fields as JSON object (e.g., {\"Microsoft.VSTS.Common.Priority\": 1})", required = false)
            JSONObject fieldsJson
    ) throws IOException {
        return createWorkItemWithFieldsJson(projectName, workItemType, title, description, fieldsJson, null);
    }

    /**
     * Internal method that supports both fieldsJson and FieldsInitializer
     */
    private String createWorkItemWithFieldsJson(
            String projectName,
            String workItemType,
            String title,
            String description,
            JSONObject fieldsJson,
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

        // Add custom fields from fieldsJson (for JavaScript compatibility)
        if (fieldsJson != null) {
            for (String key : fieldsJson.keySet()) {
                // Skip System.Title and System.Description as they're already added above
                if (!"System.Title".equals(key) && !"System.Description".equals(key) && !"System.WorkItemType".equals(key)) {
                    Object value = fieldsJson.get(key);
                    JSONObject op = new JSONObject();
                    op.put("op", "add");
                    op.put("path", "/fields/" + key);
                    op.put("value", value);
                    patchOps.put(op);
                }
            }
        }

        // Add custom fields from FieldsInitializer (for Java compatibility)
        if (fieldsInitializer != null) {
            Map<String, Object> customFields = new HashMap<>();
            fieldsInitializer.init(new TrackerTicketFields() {
                @Override
                public void set(String key, Object object) {
                    customFields.put(key, object);
                }
            });

            for (Map.Entry<String, Object> entry : customFields.entrySet()) {
                // Skip if already added from fieldsJson
                if (fieldsJson == null || !fieldsJson.has(entry.getKey())) {
                    JSONObject op = new JSONObject();
                    op.put("op", "add");
                    op.put("path", "/fields/" + entry.getKey());
                    op.put("value", entry.getValue());
                    patchOps.put(op);
                }
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
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
        if (!(ticket instanceof WorkItem)) {
            return Collections.emptyList();
        }
        WorkItem workItem = (WorkItem) ticket;
        if (workItem.getJSONObject().optJSONArray("relations") == null) {
            enrichWorkItemWithRelations(workItem);
        }
        JSONArray relations = workItem.getJSONObject().optJSONArray("relations");
        if (relations == null) {
            return Collections.emptyList();
        }
        List<ITicket> testCases = new ArrayList<>();
        for (int i = 0; i < relations.length(); i++) {
            JSONObject relation = relations.getJSONObject(i);
            String url = relation.optString("url");
            if (url != null && url.contains("/workItems/")) {
                String id = url.substring(url.lastIndexOf("/") + 1);
                ITicket linkedTicket = performTicket(id, getExtendedQueryFields());
                if (linkedTicket != null && linkedTicket.getIssueType().equalsIgnoreCase(testCaseIssueType)) {
                    testCases.add(linkedTicket);
                }
            }
        }
        return testCases;
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
        if (initiator == null || initiator.isEmpty()) {
            return "";
        }
        
        try {
            // Try to get user by email to create proper mention
            AdoUser user = getUserByEmail(initiator);
            String userId = user.getID();
            String displayName = user.getDisplayName();
            
            // Format ADO mention: <a href="#" data-vss-mention="version:2.0,USER_ID">@Display Name</a>
            return String.format("<a href=\"#\" data-vss-mention=\"version:2.0,%s\">@%s</a>", userId, displayName);
        } catch (Exception e) {
            // Fallback: if user lookup fails, just return email in plain text
            logger.warn("Failed to create ADO mention for {}: {}", initiator, e.getMessage());
            return initiator;
        }
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
            category = "file_management",
            aliases = {"tracker_download_attachment"}
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
            name = "ado_get_user_by_email",
            description = "Get user information by email address in Azure DevOps",
            integration = "ado",
            category = "user_management",
            aliases = {"tracker_get_user_by_email"}
    )
    public AdoUser getUserByEmail(
            @MCPParam(name = "email", description = "User email address", required = true)
            String email
    ) throws IOException {
        // Use Graph API to search for user by email
        // Graph API endpoint: https://vssps.dev.azure.com/{organization}/_apis/graph/users?api-version=7.1-preview.1
        String graphUrl = "https://vssps.dev.azure.com/" + organization + "/_apis/graph/users";
        GenericRequest request = new GenericRequest(this, graphUrl)
                .param("api-version", "7.1-preview.1");
        
        String response = request.execute();
        JSONObject result = new JSONObject(response);
        JSONArray users = result.optJSONArray("value");
        
        if (users != null) {
            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.getJSONObject(i);
                String principalName = user.optString("principalName");
                String mailAddress = user.optString("mailAddress");
                
                if (email.equalsIgnoreCase(principalName) || email.equalsIgnoreCase(mailAddress)) {
                    return new AdoUser(user);
                }
            }
        }
        
        // Fallback: try to get user by UPN (User Principal Name) directly
        // This is more efficient if the email format matches UPN
        try {
            String userDescriptor = getUserDescriptorByEmail(email);
            if (userDescriptor != null) {
                String userUrl = "https://vssps.dev.azure.com/" + organization + "/_apis/graph/users/" + userDescriptor;
                GenericRequest userRequest = new GenericRequest(this, userUrl)
                        .param("api-version", "7.1-preview.1");
                String userResponse = userRequest.execute();
                return new AdoUser(new JSONObject(userResponse));
            }
        } catch (Exception e) {
            logger.warn("Failed to get user descriptor for email: {}", email, e);
        }
        
        throw new IOException("User not found with email: " + email);
    }
    
    /**
     * Get user descriptor (a unique identifier) by email.
     * This is used for more specific user lookups.
     */
    private String getUserDescriptorByEmail(String email) throws IOException {
        // Use Identities API to find user by email
        String identitiesUrl = "https://vssps.dev.azure.com/" + organization + "/_apis/identities";
        GenericRequest request = new GenericRequest(this, identitiesUrl)
                .param("searchFilter", "MailAddress")
                .param("filterValue", email)
                .param("api-version", "7.1-preview.1");
        
        String response = request.execute();
        JSONObject result = new JSONObject(response);
        JSONArray identities = result.optJSONArray("value");
        
        if (identities != null && identities.length() > 0) {
            JSONObject identity = identities.getJSONObject(0);
            return identity.optString("descriptor");
        }
        
        return null;
    }

    @MCPTool(
            name = "ado_get_my_profile",
            description = "Get the current user's profile information from Azure DevOps",
            integration = "ado",
            category = "user_management",
            aliases = {"tracker_get_my_profile"}
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

    // ========== Pull Request / Git Operations ==========

    /**
     * Build a Git API path for pull request operations.
     * ADO Git API: {project}/_apis/git/repositories/{repository}/pullrequests/...
     */
    private String gitPrPath(String repository, String suffix) {
        return String.format("/%s/_apis/git/repositories/%s/pullrequests%s", project, repository, suffix);
    }

    /**
     * Execute a PATCH request with regular application/json content type (not json-patch+json).
     * Used for Git API operations which require standard JSON, unlike work item operations.
     */
    private String patchJson(GenericRequest request) throws IOException {
        request.header("Content-Type", "application/json");
        return patch(request);
    }

    @MCPTool(
            name = "ado_list_prs",
            description = "List pull requests in an Azure DevOps Git repository by status. Status can be 'active', 'completed', or 'abandoned'.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_list_prs"}
    )
    public String listPullRequests(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "status", description = "The status of pull requests to list: 'active', 'completed', or 'abandoned'. 'open'/'opened' accepted as synonym for 'active'.", required = true, example = "active")
            String status) throws IOException {
        // Normalize status synonyms for cross-platform compatibility
        if ("open".equalsIgnoreCase(status) || "opened".equalsIgnoreCase(status)) {
            status = "active";
        } else if ("closed".equalsIgnoreCase(status) || "merged".equalsIgnoreCase(status) || "declined".equalsIgnoreCase(status)) {
            status = "completed";
        }
        String path = path(gitPrPath(repository, ""));
        GenericRequest request = new GenericRequest(this, path)
                .param("searchCriteria.status", status)
                .param("api-version", API_VERSION);
        return execute(request);
    }

    @MCPTool(
            name = "ado_get_pr",
            description = "Get details of an Azure DevOps pull request including title, description, status, author, reviewers, branches, and merge info.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_get_pr"}
    )
    public String getPullRequest(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID (numeric)", required = true, example = "1")
            String pullRequestId) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);
        return execute(request);
    }

    @MCPTool(
            name = "ado_get_pr_comments",
            description = "Get all comment threads for an Azure DevOps pull request. Each thread contains comments, file context for inline comments, and status (active, fixed, closed, etc.).",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_get_pr_comments"}
    )
    public String getPullRequestThreads(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);
        return execute(request);
    }

    @MCPTool(
            name = "ado_add_pr_comment",
            description = "Add a general comment to an Azure DevOps pull request (creates a new thread). For inline code comments, use ado_add_inline_comment instead.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_add_pr_comment"}
    )
    public String addPullRequestComment(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "text", description = "The comment text to add (Markdown supported)", required = true, example = "Looks good!")
            String text) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject comment = new JSONObject();
        comment.put("parentCommentId", 0);
        comment.put("content", text);
        comment.put("commentType", 1); // 1 = text

        JSONArray comments = new JSONArray();
        comments.put(comment);

        JSONObject thread = new JSONObject();
        thread.put("comments", comments);
        thread.put("status", 1); // 1 = active

        request.setBody(thread.toString());
        return post(request);
    }

    @MCPTool(
            name = "ado_reply_to_pr_thread",
            description = "Reply to an existing comment thread in an Azure DevOps pull request. Use the threadId from ado_get_pr_comments.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_reply_to_pr_thread"}
    )
    public String replyToPullRequestThread(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "threadId", description = "The ID of the thread to reply to (from ado_get_pr_comments)", required = true, example = "42")
            String threadId,
            @MCPParam(name = "text", description = "The reply text (Markdown supported)", required = true, example = "Fixed in the latest commit.")
            String text) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads/" + threadId + "/comments"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject comment = new JSONObject();
        comment.put("content", text);
        comment.put("parentCommentId", 1);
        comment.put("commentType", 1); // 1 = text

        request.setBody(comment.toString());
        return post(request);
    }

    @MCPTool(
            name = "ado_add_inline_comment",
            description = "Create a new inline code comment on a specific file and line range in an Azure DevOps pull request. Creates a new thread with file context.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_add_inline_comment"}
    )
    public String addInlineComment(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "filePath", description = "The relative file path in the repository (must start with /)", required = true, example = "/src/main/java/com/example/Foo.java", aliases = {"path"})
            String filePath,
            @MCPParam(name = "line", description = "The line number to comment on (1-based)", required = true, example = "42")
            String line,
            @MCPParam(name = "text", description = "The comment text (Markdown supported)", required = true, example = "This should be refactored.")
            String text,
            @MCPParam(name = "startLine", description = "For multi-line comments: the first line of the range. Must be less than or equal to line.", required = false, example = "40")
            String startLine,
            @MCPParam(name = "side", description = "Which diff side to comment on: 'right' (new code, default) or 'left' (old code)", required = false, example = "right")
            String side) throws IOException {
        // Ensure filePath starts with /
        if (!filePath.startsWith("/")) {
            filePath = "/" + filePath;
        }

        int lineNum;
        try {
            lineNum = Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid line: expected a numeric line number, but got: '" + line + "'", e);
        }

        int startLineNum = lineNum;
        if (startLine != null && !startLine.trim().isEmpty()) {
            try {
                startLineNum = Integer.parseInt(startLine);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid startLine: expected a numeric line number, but got: '" + startLine + "'", e);
            }
        }

        // Determine position context based on side
        boolean isLeftSide = "left".equalsIgnoreCase(side);

        JSONObject rightFileStart = new JSONObject();
        JSONObject rightFileEnd = new JSONObject();
        JSONObject leftFileStart = new JSONObject();
        JSONObject leftFileEnd = new JSONObject();

        if (isLeftSide) {
            leftFileStart.put("line", startLineNum);
            leftFileStart.put("offset", 1);
            leftFileEnd.put("line", lineNum);
            leftFileEnd.put("offset", 1);
        } else {
            rightFileStart.put("line", startLineNum);
            rightFileStart.put("offset", 1);
            rightFileEnd.put("line", lineNum);
            rightFileEnd.put("offset", 1);
        }

        JSONObject threadContext = new JSONObject();
        threadContext.put("filePath", filePath);
        if (isLeftSide) {
            threadContext.put("leftFileStart", leftFileStart);
            threadContext.put("leftFileEnd", leftFileEnd);
        } else {
            threadContext.put("rightFileStart", rightFileStart);
            threadContext.put("rightFileEnd", rightFileEnd);
        }

        JSONObject comment = new JSONObject();
        comment.put("parentCommentId", 0);
        comment.put("content", text);
        comment.put("commentType", 1);

        JSONArray comments = new JSONArray();
        comments.put(comment);

        JSONObject thread = new JSONObject();
        thread.put("comments", comments);
        thread.put("threadContext", threadContext);
        thread.put("status", 1); // 1 = active

        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);
        request.setBody(thread.toString());
        return post(request);
    }

    @MCPTool(
            name = "ado_resolve_pr_thread",
            description = "Resolve (close) a comment thread in an Azure DevOps pull request. Sets the thread status to 'fixed'. Other statuses: 'active', 'closed', 'byDesign', 'pending', 'wontFix'.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_resolve_pr_thread"}
    )
    public String resolveThread(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "threadId", description = "The ID of the thread to resolve (from ado_get_pr_comments)", required = true, example = "42")
            String threadId,
            @MCPParam(name = "status", description = "The new status: 'fixed' (default/resolved), 'closed', 'byDesign', 'wontFix', 'pending', 'active'", required = false, example = "fixed")
            String status) throws IOException {
        // Map string status to ADO numeric status
        int statusCode = mapThreadStatus(status != null && !status.trim().isEmpty() ? status : "fixed");

        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads/" + threadId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject body = new JSONObject();
        body.put("status", statusCode);
        request.setBody(body.toString());
        return patchJson(request);
    }

    /**
     * Map thread status string to ADO numeric status code.
     * ADO thread status codes: 0=unknown, 1=active, 2=fixed, 3=wontFix, 4=closed, 5=byDesign, 6=pending
     */
    private int mapThreadStatus(String status) {
        return switch (status.toLowerCase()) {
            case "active" -> 1;
            case "fixed", "resolved" -> 2;
            case "wontfix", "wont_fix" -> 3;
            case "closed" -> 4;
            case "bydesign", "by_design" -> 5;
            case "pending" -> 6;
            default -> 2; // default to fixed
        };
    }

    @MCPTool(
            name = "ado_update_pr_comment",
            description = "Update (edit) an existing comment in a pull request thread. Requires both threadId and commentId.",
            integration = "ado",
            category = "pull_requests"
    )
    public String updatePullRequestComment(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "threadId", description = "The ID of the thread containing the comment", required = true, example = "42")
            String threadId,
            @MCPParam(name = "commentId", description = "The ID of the comment to update", required = true, example = "1")
            String commentId,
            @MCPParam(name = "text", description = "The new comment text (replaces existing content)", required = true, example = "Updated analysis.")
            String text) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads/" + threadId + "/comments/" + commentId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject body = new JSONObject();
        body.put("content", text);
        request.setBody(body.toString());
        return patchJson(request);
    }

    @MCPTool(
            name = "ado_delete_pr_comment",
            description = "Delete a comment from a pull request thread. Requires both threadId and commentId.",
            integration = "ado",
            category = "pull_requests"
    )
    public String deletePullRequestComment(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "threadId", description = "The ID of the thread containing the comment", required = true, example = "42")
            String threadId,
            @MCPParam(name = "commentId", description = "The ID of the comment to delete", required = true, example = "2")
            String commentId) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/threads/" + threadId + "/comments/" + commentId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);
        return delete(request);
    }

    @MCPTool(
            name = "ado_get_pr_diff",
            description = "Get the diff/changes for an Azure DevOps pull request. Returns the list of changed files with change types.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_get_pr_diff"}
    )
    public String getPullRequestDiffStat(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId) throws IOException {
        // First get the PR iterations to find the latest iteration
        String iterationsPath = path(gitPrPath(repository, "/" + pullRequestId + "/iterations"));
        GenericRequest iterRequest = new GenericRequest(this, iterationsPath)
                .param("api-version", API_VERSION);
        String iterResponse = execute(iterRequest);
        JSONObject iterResult = new JSONObject(iterResponse);
        JSONArray iterations = iterResult.optJSONArray("value");

        if (iterations == null || iterations.isEmpty()) {
            return new JSONObject().put("changes", new JSONArray()).toString();
        }

        // Get changes from the latest iteration
        int latestIteration = iterations.length();
        String changesPath = path(gitPrPath(repository, "/" + pullRequestId + "/iterations/" + latestIteration + "/changes"));
        GenericRequest changesRequest = new GenericRequest(this, changesPath)
                .param("api-version", API_VERSION);
        return execute(changesRequest);
    }

    @MCPTool(
            name = "ado_merge_pr",
            description = "Complete (merge) an Azure DevOps pull request. Sets status to 'completed' with the specified merge strategy.",
            integration = "ado",
            category = "pull_requests",
            aliases = {"source_code_merge_pr"}
    )
    public String completePullRequest(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID to complete/merge", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "mergeStrategy", description = "The merge strategy: 'squash' (default), 'noFastForward', 'rebase', 'rebaseMerge'", required = false, example = "squash")
            String mergeStrategy,
            @MCPParam(name = "deleteSourceBranch", description = "Whether to delete the source branch after merging (default: true)", required = false, example = "true")
            String deleteSourceBranch,
            @MCPParam(name = "commitMessage", description = "Optional merge commit message", required = false, example = "Merging feature branch")
            String commitMessage) throws IOException {
        // First get the PR to obtain lastMergeSourceCommit (required for completion)
        String prResponse = getPullRequest(repository, pullRequestId);
        JSONObject pr = new JSONObject(prResponse);
        JSONObject lastMergeSourceCommit = pr.optJSONObject("lastMergeSourceCommit");

        String path = path(gitPrPath(repository, "/" + pullRequestId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject body = new JSONObject();
        body.put("status", "completed");

        if (lastMergeSourceCommit != null) {
            body.put("lastMergeSourceCommit", lastMergeSourceCommit);
        }

        JSONObject completionOptions = new JSONObject();
        completionOptions.put("mergeStrategy", (mergeStrategy != null && !mergeStrategy.trim().isEmpty()) ? mergeStrategy : "squash");
        completionOptions.put("deleteSourceBranch",
                deleteSourceBranch == null || deleteSourceBranch.trim().isEmpty() || Boolean.parseBoolean(deleteSourceBranch));
        if (commitMessage != null && !commitMessage.trim().isEmpty()) {
            completionOptions.put("mergeCommitMessage", commitMessage);
        }
        body.put("completionOptions", completionOptions);

        request.setBody(body.toString());
        return patchJson(request);
    }

    @MCPTool(
            name = "ado_add_pr_label",
            description = "Add a label (tag) to an Azure DevOps pull request.",
            integration = "ado",
            category = "pull_requests"
    )
    public String addPullRequestLabel(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "label", description = "The label name to add", required = true, example = "needs-review")
            String label) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/labels"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION + "-preview.1");

        JSONObject body = new JSONObject();
        body.put("name", label);
        request.setBody(body.toString());
        return post(request);
    }

    @MCPTool(
            name = "ado_remove_pr_label",
            description = "Remove a label (tag) from an Azure DevOps pull request. Requires the labelId (from ado_get_pr or ado_list_prs labels array).",
            integration = "ado",
            category = "pull_requests"
    )
    public String removePullRequestLabel(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "labelId", description = "The label ID to remove (from the PR labels array, not the label name)", required = true, example = "e10671ab-...")
            String labelId) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/labels/" + labelId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION + "-preview.1");
        return delete(request);
    }

    @MCPTool(
            name = "ado_get_pr_reviewers",
            description = "Get all reviewers for an Azure DevOps pull request with their vote status. Vote: 10=approved, 5=approved with suggestions, 0=no vote, -5=waiting for author, -10=rejected.",
            integration = "ado",
            category = "pull_requests"
    )
    public String getPullRequestReviewers(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/reviewers"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);
        return execute(request);
    }

    @MCPTool(
            name = "ado_add_pr_reviewer",
            description = "Add a reviewer to an Azure DevOps pull request. Optionally set their initial vote.",
            integration = "ado",
            category = "pull_requests"
    )
    public String addPullRequestReviewer(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "reviewerId", description = "The reviewer's ID (GUID from ado_get_user_by_email or uniqueName)", required = true, example = "ab1c2d3e-...")
            String reviewerId,
            @MCPParam(name = "vote", description = "Optional initial vote: 10=approve, 5=approve with suggestions, 0=no vote, -5=wait for author, -10=reject", required = false, example = "0")
            String vote) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/reviewers/" + reviewerId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject body = new JSONObject();
        if (vote != null && !vote.trim().isEmpty()) {
            body.put("vote", Integer.parseInt(vote));
        }
        request.setBody(body.toString());
        return put(request);
    }

    @MCPTool(
            name = "ado_set_pr_vote",
            description = "Set the current user's vote on a pull request. Vote values: 10=approve, 5=approve with suggestions, 0=reset/no vote, -5=wait for author, -10=reject.",
            integration = "ado",
            category = "pull_requests"
    )
    public String setPullRequestVote(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "reviewerId", description = "The reviewer's ID (GUID) — use ado_get_my_profile or ado_get_user_by_email to get it", required = true, example = "ab1c2d3e-...")
            String reviewerId,
            @MCPParam(name = "vote", description = "Vote value: 10=approve, 5=approve with suggestions, 0=reset, -5=wait for author, -10=reject", required = true, example = "10")
            String vote) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/reviewers/" + reviewerId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject body = new JSONObject();
        body.put("vote", Integer.parseInt(vote));
        request.setBody(body.toString());
        return put(request);
    }

    @MCPTool(
            name = "ado_update_pr",
            description = "Update pull request properties such as title, description, or status. Use status='abandoned' to abandon a PR, or 'active' to reactivate.",
            integration = "ado",
            category = "pull_requests"
    )
    public String updatePullRequest(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId,
            @MCPParam(name = "title", description = "New title for the pull request", required = false, example = "Updated PR title")
            String title,
            @MCPParam(name = "description", description = "New description for the pull request (Markdown supported)", required = false, example = "Updated description")
            String description,
            @MCPParam(name = "status", description = "New status: 'active' or 'abandoned'", required = false, example = "active")
            String status) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);

        JSONObject body = new JSONObject();
        if (title != null && !title.trim().isEmpty()) {
            body.put("title", title);
        }
        if (description != null && !description.trim().isEmpty()) {
            body.put("description", description);
        }
        if (status != null && !status.trim().isEmpty()) {
            body.put("status", status);
        }
        request.setBody(body.toString());
        return patchJson(request);
    }

    @MCPTool(
            name = "ado_get_pr_work_items",
            description = "Get work items linked to an Azure DevOps pull request.",
            integration = "ado",
            category = "pull_requests"
    )
    public String getPullRequestWorkItems(
            @MCPParam(name = "repository", description = "The Git repository name", required = true, example = "ai-native-sdlc-blueprint")
            String repository,
            @MCPParam(name = "pullRequestId", description = "The pull request ID", required = true, example = "1")
            String pullRequestId) throws IOException {
        String path = path(gitPrPath(repository, "/" + pullRequestId + "/workitems"));
        GenericRequest request = new GenericRequest(this, path)
                .param("api-version", API_VERSION);
        return execute(request);
    }

    /**
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
        
        if (contentType != null && !contentType.isEmpty()) {
            // Use the explicitly set Content-Type (supports both json-patch and regular json)
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

