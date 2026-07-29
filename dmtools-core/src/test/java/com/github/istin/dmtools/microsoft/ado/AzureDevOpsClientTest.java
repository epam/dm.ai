// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.ado;

import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.microsoft.ado.model.AdoUser;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AzureDevOpsClient} work item, comment, label, link,
 * user, changelog, pull request and low-level HTTP (patch) operations.
 *
 * Uses a testable subclass that stubs execute/post/put/delete with URL-keyed
 * canned responses and injects a mocked OkHttpClient for the methods that
 * build raw OkHttp calls (patch, isImageAttachment, profile API, downloads).
 */
public class AzureDevOpsClientTest {

    private TestableAzureDevOpsClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = new TestableAzureDevOpsClient("TestOrg", "TestProject", "fake-pat");
    }

    private static Response buildOkResponse(int code, String contentType, String body) {
        Request request = new Request.Builder().url("https://dev.azure.com/TestOrg/").build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Error")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .addHeader("Content-Type", contentType)
                .build();
    }

    private void mockOkHttp(Response... responses) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(okHttpClient.connectionPool()).thenReturn(new okhttp3.ConnectionPool());
        if (responses.length == 1) {
            when(call.execute()).thenReturn(responses[0]);
        } else {
            when(call.execute()).thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
        }
        client.setOkHttpClient(okHttpClient);
    }

    private static String workItemJson(int id, String title, String state, String type) {
        return new JSONObject()
                .put("id", id)
                .put("fields", new JSONObject()
                        .put("System.Title", title)
                        .put("System.State", state)
                        .put("System.WorkItemType", type))
                .toString();
    }

    // ========== Basics: sign, path, cache folder, logging ==========

    @Test
    void testSign_addsAuthorizationAndContentTypeHeaders() {
        Request.Builder builder = new Request.Builder().url("https://dev.azure.com/TestOrg/x");
        Request request = client.sign(builder).build();

        assertTrue(request.header("Authorization").startsWith("Basic "));
        assertEquals("application/json", request.header("Content-Type"));
    }

    @Test
    void testPath_prependsSlashWhenMissing() {
        assertEquals("https://dev.azure.com/TestOrg/foo", client.path("foo"));
        assertEquals("https://dev.azure.com/TestOrg/foo", client.path("/foo"));
    }

    @Test
    void testGetCacheFolderName() throws IOException {
        assertEquals("cacheAzureDevOpsClient", new TestableAzureDevOpsClient("o", "p", "t", false).getCacheFolderName());
    }

    @Test
    void testSetLogEnabled_andLog() {
        client.setLogEnabled(false);
        client.log("should not be logged");
        client.setLogEnabled(true);
        client.log("should be logged");
        // no assertion needed: exercising both branches must not throw
    }

    // ========== performTicket / createGetWorkItemRequest / createTicket ==========

    @Test
    void testPerformTicket_returnsParsedWorkItem() throws IOException {
        client.setMockResponse("/_apis/wit/workitems/123", workItemJson(123, "My Bug", "Active", "Bug"));

        WorkItem result = client.performTicket("123", null);

        assertNotNull(result);
        assertEquals("123", result.getTicketKey());
        assertEquals("Bug", result.getIssueType());
        // No fields filter -> $expand=relations must be requested
        assertTrue(client.getLastCalledUrl().contains("$expand=relations"), client.getLastCalledUrl());
    }

    @Test
    void testPerformTicket_errorResponse_returnsNull() throws IOException {
        client.setMockResponse("/_apis/wit/workitems/999", "{\"errorCode\":404,\"message\":\"not found\"}");

        assertNull(client.performTicket("999", null));
    }

    @Test
    void testPerformTicket_withFields_resolvesFieldNames() throws IOException {
        client.setMockResponse("/_apis/wit/workitems/123", workItemJson(123, "T", "New", "Task"));

        client.performTicket("123", new String[]{"title", "System.State", "unknownField"});

        String url = client.getLastCalledUrl();
        assertTrue(url.contains("fields="), url);
        assertTrue(url.contains("System.Title"), url);
        assertTrue(url.contains("System.State"), url);
        assertTrue(url.contains("unknownField"), url);
        assertFalse(url.contains("$expand"), "fields filter must not use $expand: " + url);
    }

    @Test
    void testCreateTicket_invalidJson_returnsNull() {
        assertNull(client.createTicket("not a json"));
    }

    @Test
    void testCreateTicket_validJson_returnsWorkItem() {
        WorkItem item = client.createTicket(workItemJson(1, "T", "New", "Task"));
        assertNotNull(item);
        assertEquals("1", item.getTicketKey());
    }

    // ========== enrichWorkItemWithRelations ==========

    @Test
    void testEnrichWorkItemWithRelations_nullWorkItem_noOp() throws IOException {
        client.enrichWorkItemWithRelations(null);
        assertEquals("", client.getLastCalledUrl());
    }

    @Test
    void testEnrichWorkItemWithRelations_mergesRelations() throws IOException {
        WorkItem item = client.createTicket(workItemJson(42, "T", "New", "Task"));
        String fullResponse = new JSONObject()
                .put("relations", new JSONArray().put(new JSONObject().put("rel", "AttachedFile").put("url", "http://x")))
                .toString();
        client.setMockResponse("/_apis/wit/workitems/42", fullResponse);

        client.enrichWorkItemWithRelations(item);

        JSONArray relations = item.getJSONObject().optJSONArray("relations");
        assertNotNull(relations);
        assertEquals(1, relations.length());
    }

    @Test
    void testEnrichWorkItemWithRelations_noRelationsInResponse_noOp() throws IOException {
        WorkItem item = client.createTicket(workItemJson(43, "T", "New", "Task"));
        client.setMockResponse("/_apis/wit/workitems/43", "{\"id\":43}");

        client.enrichWorkItemWithRelations(item);

        assertNull(item.getJSONObject().optJSONArray("relations"));
    }

    // ========== resolveFieldName(s) ==========

    @Test
    void testResolveFieldName_dottedName_returnedAsIs() {
        assertEquals("Custom.MyField", client.resolveFieldName("Custom.MyField"));
    }

    @Test
    void testResolveFieldName_knownAliases() {
        assertEquals("System.Id", client.resolveFieldName("id"));
        assertEquals("System.Title", client.resolveFieldName("title"));
        assertEquals("System.Title", client.resolveFieldName("summary"));
        assertEquals("System.Description", client.resolveFieldName("description"));
        assertEquals("System.State", client.resolveFieldName("state"));
        assertEquals("System.AssignedTo", client.resolveFieldName("assignedto"));
        assertEquals("System.CreatedBy", client.resolveFieldName("createdby"));
        assertEquals("System.CreatedDate", client.resolveFieldName("createddate"));
        assertEquals("System.ChangedDate", client.resolveFieldName("changeddate"));
        assertEquals("System.WorkItemType", client.resolveFieldName("workitemtype"));
        assertEquals("Microsoft.VSTS.Common.Priority", client.resolveFieldName("priority"));
        assertEquals("System.Tags", client.resolveFieldName("tags"));
        assertEquals("System.AreaPath", client.resolveFieldName("areapath"));
        assertEquals("System.IterationPath", client.resolveFieldName("iterationpath"));
        assertEquals("Microsoft.VSTS.Scheduling.StoryPoints", client.resolveFieldName("storypoints"));
        assertEquals("Microsoft.VSTS.Scheduling.Effort", client.resolveFieldName("effort"));
        assertEquals("System.Title", client.resolveFieldName("TITLE"), "matching is case-insensitive");
    }

    @Test
    void testResolveFieldName_unknown_returnedAsIs() {
        assertEquals("somethingelse", client.resolveFieldName("somethingelse"));
    }

    @Test
    void testResolveFieldNames_array() {
        String[] resolved = client.resolveFieldNames(new String[]{"id", "state"});
        assertArrayEquals(new String[]{"System.Id", "System.State"}, resolved);
    }

    @Test
    void testResolveFieldName_twoArgVersion() throws IOException {
        assertEquals("Custom.Field", client.resolveFieldName("123", "Custom.Field"));
        assertEquals("System.Priority", client.resolveFieldName("123", "Priority"));
    }

    // ========== searchAndPerform ==========

    @Test
    void testSearchAndPerform_noResults_returnsEmptyList() throws Exception {
        client.setMockPostResponse("/_apis/wit/wiql", "{\"workItems\":[]}");

        List<WorkItem> results = client.searchAndPerform("SELECT [System.Id] FROM WorkItems", null);

        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchAndPerform_withResults_fetchesBatchAndPerforms() throws Exception {
        client.setMockPostResponse("/_apis/wit/wiql",
                "{\"workItems\":[{\"id\":1},{\"id\":2}]}");
        client.setMockResponse("/_apis/wit/workitems?",
                "{\"value\":[" + workItemJson(1, "A", "New", "Task") + ","
                        + workItemJson(2, "B", "New", "Bug") + "]}");

        List<WorkItem> results = client.searchAndPerform("SELECT [System.Id] FROM WorkItems", new String[]{"title"});

        assertEquals(2, results.size());
        assertTrue(client.getLastCalledUrl().contains("ids=1%2C2"), client.getLastCalledUrl());
        assertTrue(client.getLastCalledUrl().contains("fields="), client.getLastCalledUrl());
    }

    @Test
    void testSearchAndPerform_moreThan200Ids_usesMultipleBatches() throws Exception {
        JSONArray workItems = new JSONArray();
        for (int i = 1; i <= 201; i++) {
            workItems.put(new JSONObject().put("id", i));
        }
        client.setMockPostResponse("/_apis/wit/wiql", new JSONObject().put("workItems", workItems).toString());
        client.setMockResponse("/_apis/wit/workitems?",
                "{\"value\":[" + workItemJson(1, "A", "New", "Task") + "]}");

        List<WorkItem> results = client.searchAndPerform("SELECT [System.Id] FROM WorkItems", null);

        // Both batch calls hit the same canned response (one item each)
        assertEquals(2, results.size());
    }

    // ========== Comments ==========

    @Test
    void testGetComments_returnsParsedComments() throws IOException {
        String response = "{\"comments\":[{\"text\":\"first comment\"},{\"text\":\"second comment\"}]}";
        client.setMockResponse("/comments", response);

        List<? extends IComment> comments = client.getComments("123", null);

        assertEquals(2, comments.size());
        assertEquals("first comment", comments.get(0).getBody());
    }

    @Test
    void testGetComments_noComments_returnsEmptyList() throws IOException {
        client.setMockResponse("/comments", "{\"count\":0}");

        assertTrue(client.getComments("123", null).isEmpty());
    }

    @Test
    void testPostComment_postsTextBody() throws IOException {
        client.setMockPostResponse("/comments", "{\"id\":1}");

        client.postComment("123", "hello world");

        String body = client.getLastPostedBody("/comments");
        assertEquals("hello world", new JSONObject(body).getString("text"));
    }

    @Test
    void testPostCommentIfNotExists_existingComment_skipsPost() throws IOException {
        client.setMockResponse("/comments", "{\"comments\":[{\"text\":\"please deploy to prod now\"}]}");

        client.postCommentIfNotExists("123", "deploy to prod");

        assertNull(client.getLastPostedBody("/comments"), "no POST expected when comment already exists");
    }

    @Test
    void testPostCommentIfNotExists_missingComment_posts() throws IOException {
        client.setMockResponse("/comments", "{\"comments\":[{\"text\":\"unrelated\"}]}");
        client.setMockPostResponse("/comments", "{\"id\":2}");

        client.postCommentIfNotExists("123", "new note");

        assertNotNull(client.getLastPostedBody("/comments"));
    }

    @Test
    void testDeleteCommentIfExists_isNoOp() throws IOException {
        client.deleteCommentIfExists("123", "anything");
        assertEquals("", client.getLastCalledUrl(), "ADO comment deletion must not call the API");
    }

    // ========== Update operations (JSON Patch via mocked OkHttp) ==========

    private String capturePatchedBody() throws IOException {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(client.getOkHttpClient(), atLeastOnce()).newCall(captor.capture());
        Request request = captor.getValue();
        assertEquals("PATCH", request.method());
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    @Test
    void testAssignTo_patchesAssignedToField() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "{\"id\":123}"));

        String response = client.assignTo("123", "user@example.com");

        assertEquals("{\"id\":123}", response);
        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("/fields/System.AssignedTo", ops.getJSONObject(0).getString("path"));
        assertEquals("user@example.com", ops.getJSONObject(0).getString("value"));
    }

    @Test
    void testUpdateDescription_patchesDescriptionField() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.updateDescription("123", "<p>new desc</p>");

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("/fields/System.Description", ops.getJSONObject(0).getString("path"));
    }

    @Test
    void testUpdateTags_patchesTagsField() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.updateTags("123", "tag1;tag2");

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("/fields/System.Tags", ops.getJSONObject(0).getString("path"));
        assertEquals("tag1;tag2", ops.getJSONObject(0).getString("value"));
    }

    @Test
    void testMoveToStatus_patchesStateField() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.moveToStatus("123", "Closed");

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("/fields/System.State", ops.getJSONObject(0).getString("path"));
        assertEquals("Closed", ops.getJSONObject(0).getString("value"));
    }

    @Test
    void testUpdateTicket_nullInitializer_sendsEmptyPatch() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.updateTicket("123", null);

        assertEquals("[]", capturePatchedBody());
    }

    @Test
    void testUpdateTicket_withInitializer_sendsAllFields() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.updateTicket("123", fields -> {
            fields.set("System.State", "Active");
            fields.set("Microsoft.VSTS.Common.Priority", 1);
        });

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals(2, ops.length());
    }

    // ========== Labels ==========

    @Test
    void testAddLabelIfNotExists_labelAlreadyPresent_noPatch() throws IOException {
        WorkItem item = client.createTicket(new JSONObject()
                .put("id", 1)
                .put("fields", new JSONObject().put("System.Tags", "alpha; beta"))
                .toString());

        client.addLabelIfNotExists(item, "BETA");

        assertNull(client.getOkHttpClient(), "no HTTP expected when label already exists");
    }

    @Test
    void testAddLabelIfNotExists_newLabel_appendsWithSemicolon() throws IOException {
        WorkItem item = client.createTicket(new JSONObject()
                .put("id", 1)
                .put("fields", new JSONObject().put("System.Tags", "alpha; beta"))
                .toString());
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.addLabelIfNotExists(item, "gamma");

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("alpha;beta;gamma", ops.getJSONObject(0).getString("value"));
    }

    @Test
    void testAddLabelIfNotExists_nullLabels_startsFresh() throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketLabels()).thenReturn(null);
        when(ticket.getTicketKey()).thenReturn("7");
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.addLabelIfNotExists(ticket, "only");

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("only", ops.getJSONObject(0).getString("value"));
    }

    @Test
    void testDeleteLabelInTicket_removesMatchingTagCaseInsensitively() throws IOException {
        WorkItem item = client.createTicket(new JSONObject()
                .put("id", 1)
                .put("fields", new JSONObject().put("System.Tags", "alpha; Beta; gamma"))
                .toString());
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.deleteLabelInTicket(item, "BETA");

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("alpha;gamma", ops.getJSONObject(0).getString("value"));
    }

    // ========== linkIssueWithRelationship / mapRelationshipType ==========

    private void assertRelationshipPatched(String relationship, String expectedRel) throws IOException {
        client.setOkHttpClient(null);
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.linkIssueWithRelationship("1", "2", relationship);

        JSONArray ops = new JSONArray(capturePatchedBody());
        JSONObject value = ops.getJSONObject(0).getJSONObject("value");
        assertEquals(expectedRel, value.getString("rel"));
        assertEquals("https://dev.azure.com/TestOrg/_apis/wit/workItems/2", value.getString("url"));
        assertEquals("/relations/-", ops.getJSONObject(0).getString("path"));
    }

    @Test
    void testLinkIssueWithRelationship_parent_mapsToHierarchyReverse() throws IOException {
        assertRelationshipPatched("parent", "System.LinkTypes.Hierarchy-Reverse");
    }

    @Test
    void testLinkIssueWithRelationship_child_mapsToHierarchyForward() throws IOException {
        assertRelationshipPatched("child", "System.LinkTypes.Hierarchy-Forward");
    }

    @Test
    void testLinkIssueWithRelationship_blocks_mapsToDependencyForward() throws IOException {
        assertRelationshipPatched("blocks", "System.LinkTypes.Dependency-Forward");
    }

    @Test
    void testLinkIssueWithRelationship_blockedBy_mapsToDependencyReverse() throws IOException {
        assertRelationshipPatched("blocked by", "System.LinkTypes.Dependency-Reverse");
        assertRelationshipPatched("blockedby", "System.LinkTypes.Dependency-Reverse");
    }

    @Test
    void testLinkIssueWithRelationship_testedBy_mapsToTestedByForward() throws IOException {
        assertRelationshipPatched("tested by", "Microsoft.VSTS.Common.TestedBy-Forward");
        assertRelationshipPatched("tests", "Microsoft.VSTS.Common.TestedBy-Forward");
    }

    @Test
    void testLinkIssueWithRelationship_relatedAndNullAndUnknown_mapToRelated() throws IOException {
        assertRelationshipPatched("related", "System.LinkTypes.Related");
        assertRelationshipPatched(null, "System.LinkTypes.Related");
        assertRelationshipPatched("something-unknown", "System.LinkTypes.Related");
    }

    @Test
    void testLinkIssueWithRelationship_fullType_passedThroughAsIs() throws IOException {
        assertRelationshipPatched("System.LinkTypes.Hierarchy-Forward", "System.LinkTypes.Hierarchy-Forward");
        assertRelationshipPatched("Microsoft.VSTS.Common.TestedBy-Reverse", "Microsoft.VSTS.Common.TestedBy-Reverse");
    }

    // ========== Create work item ==========

    @Test
    void testCreateTicketInProject_delegatesToCreateWithFieldsJson() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "{\"id\":500}"));

        String response = client.createTicketInProject("TestProject", "Bug", "Summary", "Desc",
                fields -> fields.set("Microsoft.VSTS.Common.Priority", 2));

        assertEquals("{\"id\":500}", response);
        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals("/fields/System.Title", ops.getJSONObject(0).getString("path"));
        assertEquals("Summary", ops.getJSONObject(0).getString("value"));
        assertEquals("/fields/System.Description", ops.getJSONObject(1).getString("path"));
        assertEquals("/fields/Microsoft.VSTS.Common.Priority", ops.getJSONObject(2).getString("path"));
    }

    @Test
    void testCreateWorkItemWithFieldsJson_noDescription_noDescOp() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.createWorkItemWithFieldsJson("TestProject", "Task", "Title only", "", null);

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals(1, ops.length());
        assertEquals("/fields/System.Title", ops.getJSONObject(0).getString("path"));
    }

    @Test
    void testCreateWorkItemWithFieldsJson_fieldsJson_skipsTitleAndDescription() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        JSONObject fieldsJson = new JSONObject()
                .put("System.Title", "should be skipped")
                .put("System.Description", "should be skipped")
                .put("System.WorkItemType", "should be skipped")
                .put("Microsoft.VSTS.Common.Priority", 1);

        client.createWorkItemWithFieldsJson("TestProject", "Bug", "Real title", "Real desc", fieldsJson);

        JSONArray ops = new JSONArray(capturePatchedBody());
        assertEquals(3, ops.length());
        assertEquals("/fields/System.Title", ops.getJSONObject(0).getString("path"));
        assertEquals("Real title", ops.getJSONObject(0).getString("value"));
        assertEquals("/fields/System.Description", ops.getJSONObject(1).getString("path"));
        assertEquals("/fields/Microsoft.VSTS.Common.Priority", ops.getJSONObject(2).getString("path"));
    }

    @Test
    void testCreateWorkItemWithFieldsJson_initializerFieldsNotDuplicatedFromJson() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        JSONObject fieldsJson = new JSONObject().put("Microsoft.VSTS.Common.Priority", 1);

        String response = client.createTicketInProject("TestProject", "Bug", "T", null, fields -> {
            fields.set("Microsoft.VSTS.Common.Priority", 9);
            fields.set("System.Tags", "x");
        });

        assertEquals("ok", response);
        JSONArray ops = new JSONArray(capturePatchedBody());
        // Title + Priority(from json) + Tags(initializer); initializer priority skipped (dup)
        assertEquals(3, ops.length());
    }

    // ========== Changelog ==========

    @Test
    void testGetChangeLog_returnsChangelog() throws IOException {
        client.setMockResponse("/updates", "{\"value\":[{\"id\":1}]}");

        IChangelog changelog = client.getChangeLog("123", null);

        assertNotNull(changelog);
        assertEquals(1, changelog.getHistories().size());
    }

    @Test
    void testGetChangeLog_emptyResponse_returnsEmptyChangelog() throws IOException {
        client.setMockResponse("/updates", "   ");

        IChangelog changelog = client.getChangeLog("123", null);

        assertNotNull(changelog);
        assertTrue(changelog.getHistories().isEmpty());
    }

    @Test
    void testGetChangeLog_invalidJson_throwsIOException() throws IOException {
        client.setMockResponse("/updates", "not-json");

        assertThrows(IOException.class, () -> client.getChangeLog("123", null));
    }

    // ========== Unsupported operations ==========

    @Test
    void testGetFixVersions_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> client.getFixVersions("TestProject"));
    }

    @Test
    void testAttachFileToTicket_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> client.attachFileToTicket("1", "f.txt", "text/plain", new File("f.txt")));
    }

    // ========== getTestCases ==========

    @Test
    void testGetTestCases_nonWorkItemTicket_returnsEmptyList() throws IOException {
        ITicket ticket = mock(ITicket.class);
        assertTrue(client.getTestCases(ticket, "Test Case").isEmpty());
    }

    @Test
    void testGetTestCases_workItemWithoutRelations_enrichesAndReturnsEmpty() throws IOException {
        WorkItem item = client.createTicket(workItemJson(55, "Story", "Active", "User Story"));
        // Enrichment response has no relations
        client.setMockResponse("/_apis/wit/workitems/55", "{\"id\":55}");

        List<? extends ITicket> testCases = client.getTestCases(item, "Test Case");

        assertTrue(testCases.isEmpty());
    }

    @Test
    void testGetTestCases_linkedTestCase_isReturned() throws IOException {
        JSONObject itemJson = new JSONObject()
                .put("id", 55)
                .put("fields", new JSONObject().put("System.WorkItemType", "User Story"))
                .put("relations", new JSONArray()
                        .put(new JSONObject().put("url", "https://dev.azure.com/TestOrg/_apis/wit/workItems/77"))
                        .put(new JSONObject().put("url", "https://other.example.com/not-a-workitem")));
        WorkItem item = client.createTicket(itemJson.toString());
        client.setMockResponse("/_apis/wit/workitems/77", workItemJson(77, "TC", "Active", "Test Case"));

        List<? extends ITicket> testCases = client.getTestCases(item, "Test Case");

        assertEquals(1, testCases.size());
        assertEquals("77", testCases.get(0).getTicketKey());
    }

    @Test
    void testGetTestCases_linkedNonTestCase_isFilteredOut() throws IOException {
        JSONObject itemJson = new JSONObject()
                .put("id", 55)
                .put("fields", new JSONObject().put("System.WorkItemType", "User Story"))
                .put("relations", new JSONArray()
                        .put(new JSONObject().put("url", "https://dev.azure.com/TestOrg/_apis/wit/workItems/78")));
        WorkItem item = client.createTicket(itemJson.toString());
        client.setMockResponse("/_apis/wit/workitems/78", workItemJson(78, "Bug", "Active", "Bug"));

        assertTrue(client.getTestCases(item, "Test Case").isEmpty());
    }

    // ========== tag / getUserByEmail / getMyProfile / testConnection ==========

    @Test
    void testTag_nullOrEmpty_returnsEmptyString() throws IOException {
        assertEquals("", client.tag(null));
        assertEquals("", client.tag(""));
    }

    @Test
    void testTag_userFound_returnsMentionMarkup() throws IOException {
        String usersResponse = "{\"value\":[{\"principalName\":\"jane@example.com\","
                + "\"displayName\":\"Jane Doe\",\"descriptor\":\"aad.xyz\"}]}";
        client.setMockResponse("/_apis/graph/users?", usersResponse);

        String tag = client.tag("jane@example.com");

        assertEquals("<a href=\"#\" data-vss-mention=\"version:2.0,aad.xyz\">@Jane Doe</a>", tag);
    }

    @Test
    void testTag_lookupFails_returnsPlainInitiator() throws IOException {
        // No canned response -> execute() throws IOException -> fallback
        String tag = client.tag("nobody@example.com");
        assertEquals("nobody@example.com", tag);
    }

    @Test
    void testGetUserByEmail_matchByMailAddress() throws IOException {
        String usersResponse = "{\"value\":[{\"principalName\":\"other@example.com\","
                + "\"mailAddress\":\"jane@example.com\",\"displayName\":\"Jane\"}]}";
        client.setMockResponse("/_apis/graph/users?", usersResponse);

        AdoUser user = client.getUserByEmail("JANE@example.com");

        assertEquals("Jane", user.getDisplayName());
    }

    @Test
    void testGetUserByEmail_fallbackViaDescriptor() throws IOException {
        client.setMockResponse("/_apis/graph/users?", "{\"value\":[]}");
        client.setMockResponse("/_apis/identities", "{\"value\":[{\"descriptor\":\"aad.desc\"}]}");
        client.setMockResponse("/_apis/graph/users/aad.desc",
                "{\"displayName\":\"Desc User\",\"mailAddress\":\"u@example.com\"}");

        AdoUser user = client.getUserByEmail("u@example.com");

        assertEquals("Desc User", user.getDisplayName());
    }

    @Test
    void testGetUserByEmail_notFound_throwsIOException() throws IOException {
        client.setMockResponse("/_apis/graph/users?", "{\"value\":[]}");
        client.setMockResponse("/_apis/identities", "{\"value\":[]}");

        assertThrows(IOException.class, () -> client.getUserByEmail("ghost@example.com"));
    }

    @Test
    void testGetMyProfile_returnsAdoUser() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json",
                "{\"displayName\":\"Me Myself\",\"emailAddress\":\"me@example.com\"}"));

        IUser profile = client.getMyProfile();

        assertEquals("Me Myself", profile.getFullName());
        assertEquals("me@example.com", profile.getEmailAddress());
    }

    @Test
    void testGetMyProfile_httpError_throwsIOException() throws IOException {
        mockOkHttp(buildOkResponse(401, "application/json", "unauthorized"));

        assertThrows(IOException.class, () -> client.getMyProfile());
    }

    @Test
    void testTestConnection_success() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json",
                "{\"displayName\":\"Me\",\"emailAddress\":\"me@example.com\"}"));

        Map<String, Object> result = client.testConnection();

        assertEquals(true, result.get("success"));
        assertEquals("Me", result.get("user"));
        assertEquals("me@example.com", result.get("email"));
    }

    @Test
    void testTestConnection_failure() throws IOException {
        mockOkHttp(buildOkResponse(500, "application/json", "boom"));

        Map<String, Object> result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertTrue(result.get("message").toString().contains("failed"));
        assertNotNull(result.get("error"));
    }

    // ========== Text helpers / URLs / query fields ==========

    @Test
    void testGetTextFieldsOnly_stripsHtml() {
        WorkItem item = client.createTicket(new JSONObject()
                .put("id", 1)
                .put("fields", new JSONObject()
                        .put("System.Title", "My Title")
                        .put("System.Description", "<p>Hello <b>world</b></p>"))
                .toString());

        String text = client.getTextFieldsOnly(item);

        assertEquals("My Title\nHello world", text);
    }

    @Test
    void testUrlBuilders() {
        assertEquals("https://dev.azure.com/TestOrg/TestProject/_workitems", client.buildUrlToSearch("anything"));
        assertEquals("https://dev.azure.com/TestOrg", client.getBasePath());
        assertEquals("https://dev.azure.com/TestOrg/TestProject/_workitems/edit/123",
                client.getTicketBrowseUrl("123"));
    }

    @Test
    void testQueryFieldsAndStatusAndTextType() {
        assertArrayEquals(new String[]{
                "System.Id", "System.Title", "System.State", "System.WorkItemType",
                "System.AssignedTo", "System.CreatedDate", "System.ChangedDate",
                "Microsoft.VSTS.Common.Priority"}, client.getDefaultQueryFields());

        String[] extended = client.getExtendedQueryFields();
        assertTrue(extended.length > client.getDefaultQueryFields().length);
        assertEquals("System.Tags", extended[9]);

        assertEquals("System.State", client.getDefaultStatusField());
        assertEquals(TrackerClient.TextType.HTML, client.getTextType());
    }

    // ========== isValidImageUrl / isImageAttachment ==========

    @Test
    void testIsValidImageUrl_imageExtension_noHttpNeeded() throws IOException {
        assertTrue(client.isValidImageUrl("https://dev.azure.com/TestOrg/att/123.png"));
        assertTrue(client.isValidImageUrl("https://dev.azure.com/TestOrg/att/123.gif"));
        assertFalse(client.isValidImageUrl("https://evil.example.com/att/123.png"),
                "URL from a different host must be rejected");
    }

    @Test
    void testIsValidImageUrl_noExtension_checksContentTypeHeader() throws IOException {
        mockOkHttp(buildOkResponse(200, "image/png", ""));

        assertTrue(client.isValidImageUrl("https://dev.azure.com/TestOrg/att/download"));
    }

    @Test
    void testIsValidImageUrl_nonImageContentType_returnsFalse() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", ""));

        assertFalse(client.isValidImageUrl("https://dev.azure.com/TestOrg/att/download"));
    }

    @Test
    void testIsValidImageUrl_unsuccessfulHeadRequest_returnsFalse() throws IOException {
        mockOkHttp(buildOkResponse(500, "text/plain", "boom"));

        assertFalse(client.isValidImageUrl("https://dev.azure.com/TestOrg/att/download"));
    }

    @Test
    void testIsValidImageUrl_headRequestThrows_returnsFalse() throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("connection refused"));
        client.setOkHttpClient(okHttpClient);

        assertFalse(client.isValidImageUrl("https://dev.azure.com/TestOrg/att/download"));
    }

    // ========== convertUrlToFile ==========

    @Test
    void testConvertUrlToFile_downloadsToCacheFolder(@TempDir Path tempDir) throws IOException {
        client.setCacheFolder(tempDir.toString());
        mockOkHttp(buildOkResponse(200, "image/png", "fake-image-bytes"));

        File downloaded = client.convertUrlToFile("https://dev.azure.com/TestOrg/att/file.png");

        assertTrue(downloaded.exists());
        assertTrue(downloaded.getAbsolutePath().startsWith(tempDir.toString()));
        assertEquals("fake-image-bytes", Files.readString(downloaded.toPath()));
    }

    // ========== Low-level patch() ==========

    @Test
    void testPatch_errorResponse_throwsIOException() throws IOException {
        mockOkHttp(buildOkResponse(400, "application/json", "{\"message\":\"bad request\"}"));

        GenericRequest request = new GenericRequest(client, "https://dev.azure.com/TestOrg/x")
                .param("api-version", "7.0");
        request.setBody("[]");

        assertThrows(IOException.class, () -> client.patch(request));
    }

    @Test
    void testPatch_recoverableError_retriesAndSucceeds() throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute())
                .thenThrow(new IOException("connection reset"))
                .thenReturn(buildOkResponse(200, "application/json", "{\"retried\":true}"));
        client.setOkHttpClient(okHttpClient);

        GenericRequest request = new GenericRequest(client, "https://dev.azure.com/TestOrg/x");
        request.setBody("[]");

        assertEquals("{\"retried\":true}", client.patch(request));
        verify(call, times(2)).execute();
    }

    @Test
    void testPatch_nonRecoverableError_throwsImmediately() throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("some weird failure"));
        client.setOkHttpClient(okHttpClient);

        GenericRequest request = new GenericRequest(client, "https://dev.azure.com/TestOrg/x");
        request.setBody("[]");

        assertThrows(IOException.class, () -> client.patch(request));
        verify(call, times(1)).execute();
    }

    @Test
    void testPatch_recoverableErrorExhaustsRetries_throws() throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("connection reset"));
        client.setOkHttpClient(okHttpClient);

        GenericRequest request = new GenericRequest(client, "https://dev.azure.com/TestOrg/x");
        request.setBody("[]");

        assertThrows(IOException.class, () -> client.patch(request));
        verify(call, times(3)).execute();
    }

    // ========== Pull request operations ==========

    @Test
    void testListPullRequests_statusNormalization() throws IOException {
        client.setMockResponse("/pullrequests", "{\"value\":[]}");

        client.listPullRequests("repo", "open");
        assertTrue(client.getLastCalledUrl().contains("searchCriteria.status=active"), client.getLastCalledUrl());

        client.listPullRequests("repo", "merged");
        assertTrue(client.getLastCalledUrl().contains("searchCriteria.status=completed"), client.getLastCalledUrl());

        client.listPullRequests("repo", "abandoned");
        assertTrue(client.getLastCalledUrl().contains("searchCriteria.status=abandoned"), client.getLastCalledUrl());
    }

    @Test
    void testGetPullRequest_returnsApiResponse() throws IOException {
        client.setMockResponse("/pullrequests/9", "{\"pullRequestId\":9}");

        assertEquals("{\"pullRequestId\":9}", client.getPullRequest("repo", "9"));
        assertTrue(client.getLastCalledUrl().contains("/git/repositories/repo/pullrequests/9"),
                client.getLastCalledUrl());
    }

    @Test
    void testGetPullRequestThreads() throws IOException {
        client.setMockResponse("/pullrequests/9/threads", "{\"value\":[]}");

        assertEquals("{\"value\":[]}", client.getPullRequestThreads("repo", "9"));
    }

    @Test
    void testAddPullRequestComment_postsThreadBody() throws IOException {
        client.setMockPostResponse("/pullrequests/9/threads", "{\"id\":100}");

        String response = client.addPullRequestComment("repo", "9", "Looks good!");

        assertEquals("{\"id\":100}", response);
        JSONObject thread = new JSONObject(client.getLastPostedBody("/pullrequests/9/threads"));
        assertEquals(1, thread.getInt("status"));
        JSONObject comment = thread.getJSONArray("comments").getJSONObject(0);
        assertEquals("Looks good!", comment.getString("content"));
        assertEquals(0, comment.getInt("parentCommentId"));
        assertEquals(1, comment.getInt("commentType"));
    }

    @Test
    void testReplyToPullRequestThread_postsReplyBody() throws IOException {
        client.setMockPostResponse("/threads/42/comments", "{\"id\":101}");

        client.replyToPullRequestThread("repo", "9", "42", "Fixed.");

        JSONObject comment = new JSONObject(client.getLastPostedBody("/threads/42/comments"));
        assertEquals("Fixed.", comment.getString("content"));
        assertEquals(1, comment.getInt("parentCommentId"));
    }

    @Test
    void testAddInlineComment_defaultRightSide() throws IOException {
        client.setMockPostResponse("/pullrequests/9/threads", "{\"id\":1}");

        client.addInlineComment("repo", "9", "src/Foo.java", "42", "Fix this", null, null);

        JSONObject thread = new JSONObject(client.getLastPostedBody("/pullrequests/9/threads"));
        JSONObject context = thread.getJSONObject("threadContext");
        assertEquals("/src/Foo.java", context.getString("filePath"), "leading slash must be added");
        assertEquals(42, context.getJSONObject("rightFileEnd").getInt("line"));
        assertEquals(42, context.getJSONObject("rightFileStart").getInt("line"));
        assertFalse(context.has("leftFileStart"));
    }

    @Test
    void testAddInlineComment_leftSideWithStartLine() throws IOException {
        client.setMockPostResponse("/pullrequests/9/threads", "{\"id\":1}");

        client.addInlineComment("repo", "9", "/src/Foo.java", "42", "Old code", "40", "left");

        JSONObject context = new JSONObject(client.getLastPostedBody("/pullrequests/9/threads"))
                .getJSONObject("threadContext");
        assertEquals(40, context.getJSONObject("leftFileStart").getInt("line"));
        assertEquals(42, context.getJSONObject("leftFileEnd").getInt("line"));
        assertFalse(context.has("rightFileStart"));
    }

    @Test
    void testAddInlineComment_invalidLine_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> client.addInlineComment("repo", "9", "/f.java", "abc", "x", null, null));
    }

    @Test
    void testAddInlineComment_invalidStartLine_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> client.addInlineComment("repo", "9", "/f.java", "10", "x", "xyz", null));
    }

    @Test
    void testResolveThread_mapsStatuses() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));
        client.resolveThread("repo", "9", "42", null);
        assertEquals(2, new JSONObject(capturePatchedBody()).getInt("status"), "default is fixed");

        client.resolveThread("repo", "9", "42", "active");
        assertEquals(1, new JSONObject(capturePatchedBody()).getInt("status"));

        client.resolveThread("repo", "9", "42", "wontFix");
        assertEquals(3, new JSONObject(capturePatchedBody()).getInt("status"));

        client.resolveThread("repo", "9", "42", "closed");
        assertEquals(4, new JSONObject(capturePatchedBody()).getInt("status"));

        client.resolveThread("repo", "9", "42", "byDesign");
        assertEquals(5, new JSONObject(capturePatchedBody()).getInt("status"));

        client.resolveThread("repo", "9", "42", "pending");
        assertEquals(6, new JSONObject(capturePatchedBody()).getInt("status"));

        client.resolveThread("repo", "9", "42", "weird");
        assertEquals(2, new JSONObject(capturePatchedBody()).getInt("status"), "unknown defaults to fixed");
    }

    @Test
    void testUpdatePullRequestComment_patchesContent() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.updatePullRequestComment("repo", "9", "42", "7", "Updated text");

        assertEquals("Updated text", new JSONObject(capturePatchedBody()).getString("content"));
    }

    @Test
    void testDeletePullRequestComment_callsDelete() throws IOException {
        client.setMockDeleteResponse("/threads/42/comments/7", "");

        client.deletePullRequestComment("repo", "9", "42", "7");

        assertTrue(client.getLastCalledUrl().contains("/pullrequests/9/threads/42/comments/7"),
                client.getLastCalledUrl());
    }

    @Test
    void testGetPullRequestDiffStat_noIterations_returnsEmptyChanges() throws IOException {
        client.setMockResponse("/iterations", "{\"value\":[]}");

        String result = client.getPullRequestDiffStat("repo", "9");

        assertEquals(0, new JSONObject(result).getJSONArray("changes").length());
    }

    @Test
    void testGetPullRequestDiffStat_withIterations_fetchesLatestIterationChanges() throws IOException {
        client.setMockResponse("/pullrequests/9/iterations?",
                "{\"value\":[{\"id\":1},{\"id\":2}]}");
        client.setMockResponse("/iterations/2/changes", "{\"changes\":[{\"item\":\"f.java\"}]}");

        String result = client.getPullRequestDiffStat("repo", "9");

        assertEquals(1, new JSONObject(result).getJSONArray("changes").length());
        assertTrue(client.getLastCalledUrl().contains("/iterations/2/changes"), client.getLastCalledUrl());
    }

    @Test
    void testCompletePullRequest_defaultsToSquashAndDeleteBranch() throws IOException {
        client.setMockResponse("/pullrequests/9?",
                "{\"pullRequestId\":9,\"lastMergeSourceCommit\":{\"commitId\":\"abc\"}}");
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.completePullRequest("repo", "9", null, null, null);

        JSONObject body = new JSONObject(capturePatchedBody());
        assertEquals("completed", body.getString("status"));
        assertEquals("abc", body.getJSONObject("lastMergeSourceCommit").getString("commitId"));
        JSONObject options = body.getJSONObject("completionOptions");
        assertEquals("squash", options.getString("mergeStrategy"));
        assertTrue(options.getBoolean("deleteSourceBranch"));
        assertFalse(options.has("mergeCommitMessage"));
    }

    @Test
    void testCompletePullRequest_customOptions() throws IOException {
        client.setMockResponse("/pullrequests/9?", "{\"pullRequestId\":9}");
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.completePullRequest("repo", "9", "rebase", "false", "Merged it");

        JSONObject body = new JSONObject(capturePatchedBody());
        assertFalse(body.has("lastMergeSourceCommit"), "absent in PR response -> not added");
        JSONObject options = body.getJSONObject("completionOptions");
        assertEquals("rebase", options.getString("mergeStrategy"));
        assertFalse(options.getBoolean("deleteSourceBranch"));
        assertEquals("Merged it", options.getString("mergeCommitMessage"));
    }

    @Test
    void testAddPullRequestLabel_postsLabelName() throws IOException {
        client.setMockPostResponse("/labels", "{\"id\":\"l1\"}");

        client.addPullRequestLabel("repo", "9", "needs-review");

        assertEquals("needs-review", new JSONObject(client.getLastPostedBody("/labels")).getString("name"));
        assertTrue(client.getLastCalledUrl().contains("api-version=7.0-preview.1"), client.getLastCalledUrl());
    }

    @Test
    void testRemovePullRequestLabel_callsDelete() throws IOException {
        client.setMockDeleteResponse("/labels/abc-123", "");

        client.removePullRequestLabel("repo", "9", "abc-123");

        assertTrue(client.getLastCalledUrl().contains("/pullrequests/9/labels/abc-123"), client.getLastCalledUrl());
    }

    @Test
    void testGetPullRequestReviewers() throws IOException {
        client.setMockResponse("/reviewers", "{\"value\":[]}");

        assertEquals("{\"value\":[]}", client.getPullRequestReviewers("repo", "9"));
    }

    @Test
    void testAddPullRequestReviewer_withVote() throws IOException {
        client.setMockPutResponse("/reviewers/u1", "{}");

        client.addPullRequestReviewer("repo", "9", "u1", "10");

        assertEquals(10, new JSONObject(client.getLastPutBody("/reviewers/u1")).getInt("vote"));
    }

    @Test
    void testAddPullRequestReviewer_withoutVote_omitsVote() throws IOException {
        client.setMockPutResponse("/reviewers/u1", "{}");

        client.addPullRequestReviewer("repo", "9", "u1", null);

        assertFalse(new JSONObject(client.getLastPutBody("/reviewers/u1")).has("vote"));
    }

    @Test
    void testSetPullRequestVote_putsVote() throws IOException {
        client.setMockPutResponse("/reviewers/u1", "{}");

        client.setPullRequestVote("repo", "9", "u1", "-10");

        assertEquals(-10, new JSONObject(client.getLastPutBody("/reviewers/u1")).getInt("vote"));
    }

    @Test
    void testUpdatePullRequest_onlyProvidedFieldsPatched() throws IOException {
        mockOkHttp(buildOkResponse(200, "application/json", "ok"));

        client.updatePullRequest("repo", "9", "New title", null, "abandoned");

        JSONObject body = new JSONObject(capturePatchedBody());
        assertEquals("New title", body.getString("title"));
        assertFalse(body.has("description"));
        assertEquals("abandoned", body.getString("status"));
    }

    @Test
    void testGetPullRequestWorkItems() throws IOException {
        client.setMockResponse("/workitems", "{\"value\":[]}");

        assertEquals("{\"value\":[]}", client.getPullRequestWorkItems("repo", "9"));
        assertTrue(client.getLastCalledUrl().contains("/pullrequests/9/workitems"), client.getLastCalledUrl());
    }

    // ========== Testable subclass ==========

    private static class TestableAzureDevOpsClient extends AzureDevOpsClient {

        private final Map<String, String> getResponses = new LinkedHashMap<>();
        private final Map<String, String> postResponses = new LinkedHashMap<>();
        private final Map<String, String> putResponses = new LinkedHashMap<>();
        private final Map<String, String> deleteResponses = new LinkedHashMap<>();
        private final Map<String, String> postedBodies = new LinkedHashMap<>();
        private final Map<String, String> putBodies = new LinkedHashMap<>();
        private String lastCalledUrl = "";
        private OkHttpClient okHttpClient;
        private String cacheFolder;

        TestableAzureDevOpsClient(String org, String project, String pat) throws IOException {
            this(org, project, pat, true);
        }

        TestableAzureDevOpsClient(String org, String project, String pat, boolean defaultCache) throws IOException {
            super(org, project, pat);
            this.cacheFolder = defaultCache ? null : super.getCacheFolderName();
        }

        void setMockResponse(String pathFragment, String response) {
            getResponses.put(pathFragment, response);
        }

        void setMockPostResponse(String pathFragment, String response) {
            postResponses.put(pathFragment, response);
        }

        void setMockPutResponse(String pathFragment, String response) {
            putResponses.put(pathFragment, response);
        }

        void setMockDeleteResponse(String pathFragment, String response) {
            deleteResponses.put(pathFragment, response);
        }

        String getLastPostedBody(String pathFragment) {
            return postedBodies.get(pathFragment);
        }

        String getLastPutBody(String pathFragment) {
            return putBodies.get(pathFragment);
        }

        String getLastCalledUrl() {
            return lastCalledUrl;
        }

        void setOkHttpClient(OkHttpClient okHttpClient) {
            this.okHttpClient = okHttpClient;
        }

        OkHttpClient getOkHttpClient() {
            return okHttpClient;
        }

        void setCacheFolder(String cacheFolder) {
            this.cacheFolder = cacheFolder;
        }

        private String lookup(Map<String, String> responses, String url) throws IOException {
            for (Map.Entry<String, String> entry : responses.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            throw new IOException("No mock response configured for URL: " + url);
        }

        @Override
        public String getCacheFolderName() {
            return cacheFolder != null ? cacheFolder : super.getCacheFolderName();
        }

        @Override
        public OkHttpClient getClient() {
            return okHttpClient != null ? okHttpClient : super.getClient();
        }

        @Override
        public String execute(GenericRequest request) throws IOException {
            lastCalledUrl = request.url();
            return lookup(getResponses, lastCalledUrl);
        }

        @Override
        public String post(GenericRequest request) throws IOException {
            lastCalledUrl = request.url();
            String response = lookup(postResponses, lastCalledUrl);
            for (String key : postResponses.keySet()) {
                if (lastCalledUrl.contains(key)) {
                    postedBodies.put(key, request.getBody());
                    break;
                }
            }
            return response;
        }

        @Override
        public String put(GenericRequest request) throws IOException {
            lastCalledUrl = request.url();
            String response = lookup(putResponses, lastCalledUrl);
            for (String key : putResponses.keySet()) {
                if (lastCalledUrl.contains(key)) {
                    putBodies.put(key, request.getBody());
                    break;
                }
            }
            return response;
        }

        @Override
        public String delete(GenericRequest request) throws IOException {
            lastCalledUrl = request.url();
            return lookup(deleteResponses, lastCalledUrl);
        }
    }
}
