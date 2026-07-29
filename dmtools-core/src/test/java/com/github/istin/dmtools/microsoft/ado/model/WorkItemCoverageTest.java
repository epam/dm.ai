// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage tests for {@link WorkItem} exercising every public accessor,
 * all field-mapping branches (TeamProject/AreaPath, priority mapping,
 * progress by state, tag splitting, attachment extraction from relations
 * and embedded HTML images) and the private helper classes exposed
 * through the ITicket interface.
 */
class WorkItemCoverageTest {

    private static JSONObject fullJson() {
        JSONObject fields = new JSONObject();
        fields.put("System.TeamProject", "MyProject");
        fields.put("System.AreaPath", "MyProject\\Area\\Sub");
        fields.put("System.State", "Active");
        fields.put("System.WorkItemType", "Bug");
        fields.put("Microsoft.VSTS.Common.Priority", 2);
        fields.put("System.Title", "Sample title");
        fields.put("System.Description", "Sample description");
        fields.put("System.CreatedDate", "2024-01-15T10:30:00Z");
        fields.put("System.ChangedDate", "2024-02-20T12:00:00Z");
        fields.put("System.CreatedBy", "John Doe");
        fields.put("System.Reason", "Fixed");
        fields.put("System.Tags", "tag1; tag2 ;;tag3");
        fields.put("System.IterationPath", "MyProject\\Sprint 1");
        fields.put("Microsoft.VSTS.Scheduling.Effort", "5.5");

        JSONObject json = new JSONObject();
        json.put("id", 123);
        json.put("fields", fields);
        json.put("url", "https://dev.azure.com/org/_apis/wit/workItems/123");
        JSONObject links = new JSONObject();
        JSONObject html = new JSONObject();
        html.put("href", "https://dev.azure.com/org/MyProject/_workitems/edit/123");
        links.put("html", html);
        json.put("_links", links);
        return json;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Default constructor produces empty model")
    void defaultConstructor() {
        WorkItem item = new WorkItem();
        assertEquals(0, item.getId());
        assertEquals("0", item.getTicketKey());
        assertNull(item.getFieldsObject());
        assertNull(item.getFieldsAsJSON());
    }

    @Test
    @DisplayName("String JSON constructor parses content")
    void stringConstructor() {
        WorkItem item = new WorkItem(fullJson().toString());
        assertEquals(123, item.getId());
        assertEquals("123", item.getTicketKey());
        assertEquals("123", item.getKey());
    }

    @Test
    @DisplayName("JSONObject constructor parses content")
    void jsonObjectConstructor() {
        WorkItem item = new WorkItem(fullJson());
        assertEquals(123, item.getId());
    }

    // -------------------------------------------------------------------------
    // getProject
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getProject prefers System.TeamProject")
    void getProjectFromTeamProject() {
        WorkItem item = new WorkItem(fullJson());
        assertEquals("MyProject", item.getProject());
    }

    @Test
    @DisplayName("getProject falls back to AreaPath first segment")
    void getProjectFromAreaPath() {
        JSONObject json = fullJson();
        json.getJSONObject("fields").remove("System.TeamProject");
        WorkItem item = new WorkItem(json);
        assertEquals("MyProject", item.getProject());
    }

    @Test
    @DisplayName("getProject returns whole AreaPath when no backslash")
    void getProjectFromAreaPathNoBackslash() {
        JSONObject json = fullJson();
        JSONObject fields = json.getJSONObject("fields");
        fields.remove("System.TeamProject");
        fields.put("System.AreaPath", "PlainProject");
        WorkItem item = new WorkItem(json);
        assertEquals("PlainProject", item.getProject());
    }

    @Test
    @DisplayName("getProject returns null when neither field present")
    void getProjectNull() {
        WorkItem item = new WorkItem(new JSONObject().put("id", 1));
        assertNull(item.getProject());
    }

    // -------------------------------------------------------------------------
    // Status / issue type / title
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStatus and getStatusModel map System.State")
    void getStatusAndModel() throws Exception {
        WorkItem item = new WorkItem(fullJson());
        assertEquals("Active", item.getStatus());
        Status status = item.getStatusModel();
        assertNotNull(status);
        assertEquals("Active", status.getName());
    }

    @Test
    @DisplayName("getStatusModel returns null when state missing")
    void getStatusModelNull() throws Exception {
        WorkItem item = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertNull(item.getStatus());
        assertNull(item.getStatusModel());
    }

    @Test
    @DisplayName("Status category mapping: new -> To Do")
    void statusCategoryToDo() throws Exception {
        JSONObject fields = new JSONObject().put("System.State", "Proposed");
        WorkItem item = new WorkItem(new JSONObject().put("fields", fields));
        Status status = item.getStatusModel();
        assertEquals("To Do", status.getJSONObject().getJSONObject("statusCategory").getString("name"));
    }

    @Test
    @DisplayName("Status category mapping: done states -> Done")
    void statusCategoryDone() throws Exception {
        JSONObject fields = new JSONObject().put("System.State", "Closed");
        WorkItem item = new WorkItem(new JSONObject().put("fields", fields));
        Status status = item.getStatusModel();
        assertEquals("Done", status.getJSONObject().getJSONObject("statusCategory").getString("name"));
    }

    @Test
    @DisplayName("Status category mapping: unknown -> In Progress")
    void statusCategoryUnknown() throws Exception {
        JSONObject fields = new JSONObject().put("System.State", "CustomState");
        WorkItem item = new WorkItem(new JSONObject().put("fields", fields));
        Status status = item.getStatusModel();
        assertEquals("In Progress", status.getJSONObject().getJSONObject("statusCategory").getString("name"));
    }

    @Test
    @DisplayName("getIssueType and getTicketTitle read fields")
    void getIssueTypeAndTitle() throws Exception {
        WorkItem item = new WorkItem(fullJson());
        assertEquals("Bug", item.getIssueType());
        assertEquals("Sample title", item.getTicketTitle());
    }

    // -------------------------------------------------------------------------
    // Priority
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getPriority maps numeric priorities 1-4")
    void getPriorityMapping() throws Exception {
        assertEquals("Critical", itemWithPriority(1).getPriority());
        assertEquals("High", itemWithPriority(2).getPriority());
        assertEquals("Medium", itemWithPriority(3).getPriority());
        assertEquals("Low", itemWithPriority(4).getPriority());
    }

    @Test
    @DisplayName("getPriority returns Medium for out-of-range, non-numeric and missing")
    void getPriorityDefaults() throws Exception {
        assertEquals("Medium", itemWithPriority(7).getPriority());
        assertEquals("Medium", itemWithPriority("not-a-number").getPriority());
        WorkItem noPriority = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertEquals("Medium", noPriority.getPriority());
    }

    @Test
    @DisplayName("getPriorityAsEnum maps to TicketPriority enum")
    void getPriorityAsEnum() {
        assertEquals(ITicket.TicketPriority.Critical, itemWithPriority(1).getPriorityAsEnum());
        assertEquals(ITicket.TicketPriority.Low, itemWithPriority(4).getPriorityAsEnum());
    }

    private static WorkItem itemWithPriority(Object priority) {
        JSONObject fields = new JSONObject().put("Microsoft.VSTS.Common.Priority", priority);
        return new WorkItem(new JSONObject().put("fields", fields));
    }

    // -------------------------------------------------------------------------
    // Description / dates
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketDescription falls back to ReproSteps")
    void getTicketDescriptionFallback() {
        JSONObject fields = new JSONObject();
        fields.put("Microsoft.VSTS.TCM.ReproSteps", "steps here");
        WorkItem item = new WorkItem(new JSONObject().put("fields", fields));
        assertEquals("steps here", item.getTicketDescription());

        WorkItem empty = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertNull(empty.getTicketDescription());
        assertNull(empty.getTicketDependenciesDescription());
    }

    @Test
    @DisplayName("getCreated and getUpdatedAsMillis parse ISO dates")
    void getDates() {
        WorkItem item = new WorkItem(fullJson());
        Date created = item.getCreated();
        assertNotNull(created);
        Long updated = item.getUpdatedAsMillis();
        assertNotNull(updated);
        assertTrue(updated > created.getTime());
    }

    @Test
    @DisplayName("getCreated and getUpdatedAsMillis return null when missing")
    void getDatesMissing() {
        WorkItem item = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertNull(item.getCreated());
        assertNull(item.getUpdatedAsMillis());
    }

    // -------------------------------------------------------------------------
    // Ticket link
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketLink prefers _links.html.href")
    void getTicketLinkFromLinks() {
        WorkItem item = new WorkItem(fullJson());
        assertEquals("https://dev.azure.com/org/MyProject/_workitems/edit/123", item.getTicketLink());
    }

    @Test
    @DisplayName("getTicketLink constructs URL from base path when _links missing")
    void getTicketLinkFallbackConstructed() {
        JSONObject json = fullJson();
        json.remove("_links");
        WorkItem item = new WorkItem(json);
        assertEquals("https://dev.azure.com/org/_workitems/edit/123", item.getTicketLink());
    }

    @Test
    @DisplayName("getTicketLink returns raw url when it has no /_apis/ segment")
    void getTicketLinkRawUrl() {
        JSONObject json = new JSONObject();
        json.put("id", 5);
        json.put("url", "https://example.com/workitems/5");
        WorkItem item = new WorkItem(json);
        assertEquals("https://example.com/workitems/5", item.getTicketLink());
    }

    @Test
    @DisplayName("getTicketLink returns null when no links and no url")
    void getTicketLinkNull() {
        WorkItem item = new WorkItem(new JSONObject().put("id", 5));
        assertNull(item.getTicketLink());
    }

    // -------------------------------------------------------------------------
    // Creator / resolution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getCreator maps System.CreatedBy to IUser")
    void getCreator() {
        WorkItem item = new WorkItem(fullJson());
        IUser creator = item.getCreator();
        assertNotNull(creator);
        assertEquals("John Doe", creator.getID());
        assertEquals("John Doe", creator.getFullName());
        assertNull(creator.getEmailAddress());
    }

    @Test
    @DisplayName("getCreator returns null when CreatedBy missing")
    void getCreatorNull() {
        WorkItem item = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertNull(item.getCreator());
    }

    @Test
    @DisplayName("getResolution maps System.Reason")
    void getResolution() {
        WorkItem item = new WorkItem(fullJson());
        Resolution resolution = item.getResolution();
        assertNotNull(resolution);
        assertEquals("Fixed", resolution.getName());
    }

    @Test
    @DisplayName("getResolution returns null when Reason missing")
    void getResolutionNull() {
        WorkItem item = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertNull(item.getResolution());
    }

    // -------------------------------------------------------------------------
    // Labels / fields
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTicketLabels splits semicolon-separated tags and trims empties")
    void getTicketLabels() {
        WorkItem item = new WorkItem(fullJson());
        JSONArray labels = item.getTicketLabels();
        assertEquals(3, labels.length());
        assertEquals("tag1", labels.getString(0));
        assertEquals("tag2", labels.getString(1));
        assertEquals("tag3", labels.getString(2));
    }

    @Test
    @DisplayName("getTicketLabels returns empty array for missing or blank tags")
    void getTicketLabelsEmpty() {
        WorkItem missing = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertEquals(0, missing.getTicketLabels().length());

        JSONObject fields = new JSONObject().put("System.Tags", "   ");
        WorkItem blank = new WorkItem(new JSONObject().put("fields", fields));
        assertEquals(0, blank.getTicketLabels().length());
    }

    @Test
    @DisplayName("getFields returns null (ADO has no Jira Fields model)")
    void getFieldsNull() {
        WorkItem item = new WorkItem(fullJson());
        assertNull(item.getFields());
        assertEquals("Sample title", item.getFieldValueAsString("System.Title"));
        assertSame(item.getFieldsObject(), item.getFieldsAsJSON());
    }

    // -------------------------------------------------------------------------
    // Iteration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getIteration extracts last path segment")
    void getIteration() {
        WorkItem item = new WorkItem(fullJson());
        ReportIteration iteration = item.getIteration();
        assertNotNull(iteration);
        assertEquals("Sprint 1", iteration.getIterationName());
        assertEquals(0, iteration.getId());
        assertNull(iteration.getStartDate());
        assertNull(iteration.getEndDate());
        assertFalse(iteration.isReleased());
    }

    @Test
    @DisplayName("getIterations wraps single iteration or returns empty list")
    void getIterations() {
        WorkItem item = new WorkItem(fullJson());
        List<? extends ReportIteration> iterations = item.getIterations();
        assertEquals(1, iterations.size());
        assertEquals("Sprint 1", iterations.get(0).getIterationName());

        WorkItem noIteration = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertNull(noIteration.getIteration());
        assertTrue(noIteration.getIterations().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Progress
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getProgress maps states to progress values")
    void getProgress() throws Exception {
        assertEquals(0.0, itemWithState("New").getProgress());
        assertEquals(0.0, itemWithState("Proposed").getProgress());
        assertEquals(0.5, itemWithState("Active").getProgress());
        assertEquals(0.5, itemWithState("Committed").getProgress());
        assertEquals(0.5, itemWithState("In Progress").getProgress());
        assertEquals(0.9, itemWithState("Resolved").getProgress());
        assertEquals(0.9, itemWithState("Ready").getProgress());
        assertEquals(1.0, itemWithState("Closed").getProgress());
        assertEquals(1.0, itemWithState("Done").getProgress());
        assertEquals(1.0, itemWithState("Completed").getProgress());
        assertEquals(0.3, itemWithState("SomethingElse").getProgress());

        WorkItem noState = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertEquals(0.0, noState.getProgress());
    }

    private static WorkItem itemWithState(String state) {
        JSONObject fields = new JSONObject().put("System.State", state);
        return new WorkItem(new JSONObject().put("fields", fields));
    }

    // -------------------------------------------------------------------------
    // Weight
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getWeight reads Effort, falls back to StoryPoints, handles bad input")
    void getWeight() {
        assertEquals(5.5, new WorkItem(fullJson()).getWeight());

        JSONObject fields = new JSONObject().put("Microsoft.VSTS.Scheduling.StoryPoints", "3");
        WorkItem storyPoints = new WorkItem(new JSONObject().put("fields", fields));
        assertEquals(3.0, storyPoints.getWeight());

        JSONObject badFields = new JSONObject().put("Microsoft.VSTS.Scheduling.Effort", "abc");
        WorkItem bad = new WorkItem(new JSONObject().put("fields", badFields));
        assertEquals(0.0, bad.getWeight());

        WorkItem none = new WorkItem(new JSONObject().put("fields", new JSONObject()));
        assertEquals(0.0, none.getWeight());
    }

    // -------------------------------------------------------------------------
    // Attachments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getAttachments returns empty list without relations or description")
    void getAttachmentsEmpty() {
        WorkItem item = new WorkItem(new JSONObject().put("id", 1).put("fields", new JSONObject()));
        List<? extends IAttachment> attachments = item.getAttachments();
        assertNotNull(attachments);
        assertTrue(attachments.isEmpty());
    }

    @Test
    @DisplayName("getAttachments extracts AttachedFile relations and skips others")
    void getAttachmentsFromRelations() {
        JSONArray relations = new JSONArray();
        JSONObject attached = new JSONObject();
        attached.put("rel", "AttachedFile");
        attached.put("url", "https://dev.azure.com/org/_apis/wit/attachments/abc?fileName=doc.pdf");
        attached.put("attributes", new JSONObject().put("name", "doc.pdf"));
        relations.put(attached);

        JSONObject other = new JSONObject();
        other.put("rel", "System.LinkTypes.Hierarchy-Forward");
        other.put("url", "https://dev.azure.com/org/_apis/wit/workItems/5");
        relations.put(other);

        JSONObject json = fullJson();
        json.put("relations", relations);
        WorkItem item = new WorkItem(json);
        List<? extends IAttachment> attachments = item.getAttachments();
        assertEquals(1, attachments.size());
        assertEquals("doc.pdf", attachments.get(0).getName());
        assertEquals("https://dev.azure.com/org/_apis/wit/attachments/abc?fileName=doc.pdf",
                attachments.get(0).getUrl());
    }

    @Test
    @DisplayName("getAttachments extracts embedded ADO images from description HTML")
    void getAttachmentsEmbeddedImages() {
        String description = "<div><p>text</p>"
                + "<img src=\"https://dev.azure.com/org/_apis/wit/attachments/id1?fileName=shot.png\">"
                + "<img src=\"https://dev.azure.com/org/_apis/wit/attachments/id2&fileName=second.png\">"
                + "<img src=\"https://external.example.com/not-ado.png\">"
                + "<img alt=\"no src\"></div>";
        JSONObject fields = new JSONObject().put("System.Description", description);
        JSONObject json = new JSONObject().put("id", 9).put("fields", fields);
        WorkItem item = new WorkItem(json);
        List<? extends IAttachment> attachments = item.getAttachments();
        assertEquals(2, attachments.size());
        assertEquals("shot.png", attachments.get(0).getName());
        assertEquals("second.png", attachments.get(1).getName());
        assertTrue(attachments.get(0).getUrl().contains("/_apis/wit/attachments/id1"));
    }

    @Test
    @DisplayName("Embedded image without fileName param falls back to attachment id name")
    void getAttachmentsEmbeddedImageFallbackName() {
        String description = "<img src=\"https://dev.azure.com/org/_apis/wit/attachments/xyz123\">";
        JSONObject fields = new JSONObject().put("System.Description", description);
        WorkItem item = new WorkItem(new JSONObject().put("id", 9).put("fields", fields));
        List<? extends IAttachment> attachments = item.getAttachments();
        assertEquals(1, attachments.size());
        assertEquals("attachment_xyz123.png", attachments.get(0).getName());
    }

    @Test
    @DisplayName("Unterminated img tag does not break attachment extraction")
    void getAttachmentsMalformedHtml() {
        String description = "<img src=\"https://dev.azure.com/org/_apis/wit/attachments/id1?fileName=a.png\"";
        JSONObject fields = new JSONObject().put("System.Description", description);
        WorkItem item = new WorkItem(new JSONObject().put("id", 9).put("fields", fields));
        assertTrue(item.getAttachments().isEmpty());
    }

    // -------------------------------------------------------------------------
    // toText
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toText strips blacklisted fields and caches result")
    void toText() {
        WorkItem item = new WorkItem(fullJson());
        String text = item.toText();
        assertNotNull(text);
        assertFalse(text.isEmpty());
        // Blacklisted top-level fields must be removed
        assertFalse(text.contains("\"url\""));
        assertFalse(text.contains("\"_links\""));
        // Cached: second call returns the same instance
        assertSame(text, item.toText());
    }
}
