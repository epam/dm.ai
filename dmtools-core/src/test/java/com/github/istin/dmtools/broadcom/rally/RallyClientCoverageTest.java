// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally;

import com.github.istin.dmtools.broadcom.rally.model.RallyIssue;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RallyClientCoverageTest {

    private RallyClient rallyClient;
    private static final String BASE_PATH = "http://example.com";
    private static final String AUTHORIZATION = "auth-token";

    private static final String EMPTY_RESULT = "{\"QueryResult\":{\"TotalResultCount\":0,\"PageSize\":500,\"Results\":[]}}";

    /** Minimal valid 1x1 PNG image. */
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
            0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
            (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
            0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Before
    public void setUp() throws IOException {
        rallyClient = Mockito.mock(RallyClient.class, Mockito.withSettings()
                .useConstructor(BASE_PATH, AUTHORIZATION)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        doReturn(new String[]{"FormattedID"}).when(rallyClient).getDefaultQueryFields();
    }

    /**
     * Routes stubbed execute() calls by URL fragment; unmatched URLs get an empty QueryResult.
     */
    private void stubExecute(Map<String, String> responses) throws IOException {
        doAnswer(invocation -> {
            String url = ((GenericRequest) invocation.getArgument(0)).url();
            for (Map.Entry<String, String> entry : responses.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return EMPTY_RESULT;
        }).when(rallyClient).execute(any(GenericRequest.class));
    }

    private void stubExecute(String urlFragment, String response) throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put(urlFragment, response);
        stubExecute(responses);
    }

    private static String searchResult(String... issues) {
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < issues.length; i++) {
            if (i > 0) {
                results.append(",");
            }
            results.append(issues[i]);
        }
        return "{\"QueryResult\":{\"TotalResultCount\":" + issues.length +
                ",\"PageSize\":500,\"Results\":[" + results + "]}}";
    }

    private static String issue(String formattedId) {
        return issue(formattedId, "HierarchicalRequirement", "Open");
    }

    private static String issue(String formattedId, String type, String status) {
        return "{\"FormattedID\":\"" + formattedId + "\"," +
                "\"_type\":\"" + type + "\"," +
                "\"_ref\":\"http://rally/slm/webservice/v2.0/artifact/" + formattedId + "\"," +
                "\"FlowState\":{\"Name\":\"" + status + "\"}," +
                "\"Project\":{\"Name\":\"TestProject\"}," +
                "\"LastUpdateDate\":\"2024-01-15T10:00:00.000Z\"," +
                "\"RevisionHistory\":{\"_ref\":\"http://rally/revisionhistory/" + formattedId + "\"}}";
    }

    private static String issueWithTags(String formattedId, String... tags) {
        StringBuilder tagArray = new StringBuilder();
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                tagArray.append(",");
            }
            tagArray.append("{\"Name\":\"").append(tags[i]).append("\",\"_ref\":\"http://rally/tag/")
                    .append(tags[i]).append("\"}");
        }
        return "{\"FormattedID\":\"" + formattedId + "\"," +
                "\"_type\":\"HierarchicalRequirement\"," +
                "\"_ref\":\"http://rally/slm/webservice/v2.0/artifact/" + formattedId + "\"," +
                "\"LastUpdateDate\":\"2024-01-15T10:00:00.000Z\"," +
                "\"Tags\":{\"_tagsNameArray\":[" + tagArray + "]}}";
    }

    // ------------------------------------------------------------------
    // testConnection
    // ------------------------------------------------------------------

    @Test
    public void testConnectionSuccess() throws IOException {
        stubExecute("user", "{\"User\":{\"_refObjectName\":\"John Doe\",\"_ref\":\"http://rally/user/1\"}}");
        Map<String, Object> result = rallyClient.testConnection();
        assertEquals(true, result.get("success"));
        assertEquals("John Doe", result.get("user"));
    }

    @Test
    public void testConnectionUnexpectedFormat() throws IOException {
        stubExecute("user", "{\"SomethingElse\":{}}");
        Map<String, Object> result = rallyClient.testConnection();
        assertEquals(false, result.get("success"));
        assertEquals("Unexpected response format from Rally API", result.get("message"));
    }

    @Test
    public void testConnectionEmptyResponse() throws IOException {
        stubExecute("user", "");
        Map<String, Object> result = rallyClient.testConnection();
        assertEquals(false, result.get("success"));
        assertEquals("Empty response from Rally API", result.get("message"));
    }

    @Test
    public void testConnectionException() throws IOException {
        doThrow(new IOException("boom")).when(rallyClient).execute(any(GenericRequest.class));
        Map<String, Object> result = rallyClient.testConnection();
        assertEquals(false, result.get("success"));
        assertTrue(((String) result.get("message")).contains("boom"));
        assertEquals("IOException", result.get("error"));
    }

    // ------------------------------------------------------------------
    // search / getIssue / performTicket
    // ------------------------------------------------------------------

    @Test
    public void testSearchWithFields() throws IOException {
        stubExecute("search/enhanced", "some-body");
        String response = rallyClient.search("(FormattedID = US1)", new String[]{"Name"});
        assertEquals("some-body", response);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).execute(captor.capture());
        String url = captor.getValue().url();
        assertTrue(url.contains("search/enhanced?query=(FormattedID = US1)"));
        assertTrue(url.contains("fetch=Name"));
    }

    @Test
    public void testGetIssueFound() throws IOException {
        stubExecute("search/enhanced", searchResult(issue("US1")));
        RallyIssue issue = rallyClient.getIssue("US1", new String[]{"FormattedID"});
        assertNotNull(issue);
        assertEquals("US1", issue.getTicketKey());
    }

    @Test
    public void testGetIssueEmptyResultsReturnsNull() throws IOException {
        stubExecute("search/enhanced", EMPTY_RESULT);
        assertNull(rallyClient.getIssue("US1", new String[]{"FormattedID"}));
    }

    @Test
    public void testGetIssueNoMatchingKeyReturnsNull() throws IOException {
        stubExecute("search/enhanced", searchResult(issue("US2")));
        assertNull(rallyClient.getIssue("US1", new String[]{"FormattedID"}));
    }

    @Test
    public void testPerformTicket() throws IOException {
        stubExecute("search/enhanced", searchResult(issue("DE7", "Defect", "Open")));
        RallyIssue ticket = rallyClient.performTicket("DE7", new String[]{"FormattedID"});
        assertNotNull(ticket);
        assertEquals("DE7", ticket.getTicketKey());
        assertEquals("Defect", ticket.getType());
    }

    @Test
    public void testCreateTicketFromBody() {
        RallyIssue ticket = rallyClient.createTicket(issue("US9"));
        assertEquals("US9", ticket.getTicketKey());
    }

    // ------------------------------------------------------------------
    // getChangeLog
    // ------------------------------------------------------------------

    @Test
    public void testGetChangeLogWithRallyIssue() throws IOException {
        RallyIssue ticket = new RallyIssue(issue("US1"));
        stubExecute("/revisions", "{\"QueryResult\":{\"Results\":[{\"_ref\":\"http://rally/revision/1\"}]}}");
        IChangelog changelog = rallyClient.getChangeLog("US1", ticket);
        assertNotNull(changelog);
        assertEquals(1, changelog.getHistories().size());
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("http://rally/revisionhistory/US1/revisions"));
    }

    @Test
    public void testGetChangeLogWithForeignTicket() throws IOException {
        ITicket foreignTicket = mock(ITicket.class);
        when(foreignTicket.getTicketKey()).thenReturn("US1");
        when(foreignTicket.getUpdatedAsMillis()).thenReturn(1700000000000L);
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("search/enhanced", searchResult(issue("US1")));
        responses.put("/revisions", "{\"QueryResult\":{\"Results\":[{\"_ref\":\"http://rally/revision/1\"},{\"_ref\":\"http://rally/revision/2\"}]}}");
        stubExecute(responses);
        IChangelog changelog = rallyClient.getChangeLog("US1", foreignTicket);
        assertNotNull(changelog);
        assertEquals(2, changelog.getHistories().size());
    }

    // ------------------------------------------------------------------
    // labels / tags
    // ------------------------------------------------------------------

    @Test
    public void testDeleteLabelInTicket() throws IOException {
        RallyIssue ticket = new RallyIssue(issueWithTags("US1", "keep", "remove"));
        doReturn("updated").when(rallyClient).put(any(GenericRequest.class));
        rallyClient.deleteLabelInTicket(ticket, "remove");
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).put(captor.capture());
        assertEquals("http://rally/slm/webservice/v2.0/artifact/US1", captor.getValue().url());
    }

    @Test
    public void testAddLabelIfNotExistsAlreadyPresent() throws IOException {
        RallyIssue ticket = new RallyIssue(issueWithTags("US1", "existing"));
        rallyClient.addLabelIfNotExists(ticket, "existing");
        verify(rallyClient, never()).execute(any(GenericRequest.class));
        verify(rallyClient, never()).put(any(GenericRequest.class));
    }

    @Test
    public void testAddLabelIfNotExistsTagFoundInRally() throws IOException {
        RallyIssue ticket = new RallyIssue(issueWithTags("US1", "old"));
        stubExecute("Tag?query", "{\"QueryResult\":{\"Results\":[{\"Name\":\"newtag\",\"_ref\":\"http://rally/tag/new\"}]}}");
        doReturn("updated").when(rallyClient).put(any(GenericRequest.class));
        rallyClient.addLabelIfNotExists(ticket, "newtag");
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).put(captor.capture());
        assertEquals("http://rally/slm/webservice/v2.0/artifact/US1", captor.getValue().url());
        verify(rallyClient, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testAddLabelIfNotExistsTagCreated() throws IOException {
        RallyIssue ticket = new RallyIssue(issueWithTags("US1", "old"));
        stubExecute("Tag?query", EMPTY_RESULT);
        doReturn("{\"CreateResult\":{\"Object\":{\"_ref\":\"http://rally/tag/created\"}}}")
                .when(rallyClient).post(any(GenericRequest.class));
        doReturn("updated").when(rallyClient).put(any(GenericRequest.class));
        rallyClient.addLabelIfNotExists(ticket, "newtag");
        ArgumentCaptor<GenericRequest> postCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).post(postCaptor.capture());
        assertTrue(postCaptor.getValue().url().contains("Tag/create"));
        verify(rallyClient).put(any(GenericRequest.class));
    }

    @Test
    public void testAddLabelIfNotExistsNullLabelsAndNullTagRef() throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketLabels()).thenReturn(null);
        doReturn(null).when(rallyClient).findOrCreateTag("newtag");
        rallyClient.addLabelIfNotExists(ticket, "newtag");
        verify(rallyClient).findOrCreateTag("newtag");
        verify(rallyClient, never()).put(any(GenericRequest.class));
    }

    @Test
    public void testFindOrCreateTagExisting() throws IOException {
        stubExecute("Tag?query", "{\"QueryResult\":{\"Results\":[{\"Name\":\"bug\",\"_ref\":\"http://rally/tag/bug\"}]}}");
        String ref = rallyClient.findOrCreateTag("bug");
        assertEquals("http://rally/tag/bug", ref);
        verify(rallyClient, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testFindOrCreateTagCreatesNew() throws IOException {
        stubExecute("Tag?query", EMPTY_RESULT);
        doReturn("{\"CreateResult\":{\"Object\":{\"_ref\":\"http://rally/tag/brand-new\"}}}")
                .when(rallyClient).post(any(GenericRequest.class));
        String ref = rallyClient.findOrCreateTag("brand-new");
        assertEquals("http://rally/tag/brand-new", ref);
    }

    // ------------------------------------------------------------------
    // searchAndPerform
    // ------------------------------------------------------------------

    @Test
    public void testSearchAndPerformCollectsTickets() throws Exception {
        stubExecute("search/enhanced", searchResult(issue("US1"), issue("US2")));
        List<RallyIssue> tickets = rallyClient.searchAndPerform("(Project.Name = \"X\")", new String[]{"FormattedID"});
        assertEquals(2, tickets.size());
        assertEquals("US1", tickets.get(0).getTicketKey());
        assertEquals("US2", tickets.get(1).getTicketKey());
    }

    @Test
    public void testSearchAndPerformWithErrorsReturnsEarly() throws Exception {
        stubExecute("search/enhanced", "{\"QueryResult\":{\"Errors\":[\"bad query\"],\"Results\":[]}}");
        List<RallyIssue> performed = new ArrayList<>();
        rallyClient.searchAndPerform(ticket -> {
            performed.add(ticket);
            return false;
        }, "(bad)", new String[]{"FormattedID"});
        assertTrue(performed.isEmpty());
    }

    @Test
    public void testSearchAndPerformZeroTotalReturns() throws Exception {
        stubExecute("search/enhanced", EMPTY_RESULT);
        List<RallyIssue> performed = new ArrayList<>();
        rallyClient.searchAndPerform(ticket -> {
            performed.add(ticket);
            return false;
        }, "(nothing)", new String[]{"FormattedID"});
        assertTrue(performed.isEmpty());
    }

    @Test
    public void testSearchAndPerformFiltersByType() throws Exception {
        stubExecute("search/enhanced", searchResult(
                issue("US1", "HierarchicalRequirement", "Open"),
                issue("DE1", "Defect", "Open")));
        List<RallyIssue> performed = new ArrayList<>();
        rallyClient.searchAndPerform(ticket -> {
            performed.add(ticket);
            return false;
        }, "(Project.Name = \"X\") _byTypes=Defect", new String[]{"FormattedID"});
        assertEquals(1, performed.size());
        assertEquals("DE1", performed.get(0).getTicketKey());
    }

    @Test
    public void testSearchAndPerformPerformerBreakStopsIteration() throws Exception {
        stubExecute("search/enhanced", searchResult(issue("US1"), issue("US2")));
        List<RallyIssue> performed = new ArrayList<>();
        rallyClient.searchAndPerform(ticket -> {
            performed.add(ticket);
            return true;
        }, "(Project.Name = \"X\")", new String[]{"FormattedID"});
        assertEquals(1, performed.size());
    }

    @Test
    public void testSearchAndPerformPagination() throws Exception {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("start=3", "{\"QueryResult\":{\"TotalResultCount\":3,\"PageSize\":1,\"Results\":[" + issue("US3") + "]}}");
        responses.put("start=2", "{\"QueryResult\":{\"TotalResultCount\":3,\"PageSize\":1,\"Results\":[" + issue("US2") + "]}}");
        responses.put("search/enhanced", "{\"QueryResult\":{\"TotalResultCount\":3,\"PageSize\":1,\"Results\":[" + issue("US1") + "]}}");
        stubExecute(responses);
        List<RallyIssue> performed = new ArrayList<>();
        rallyClient.searchAndPerform(ticket -> {
            performed.add(ticket);
            return false;
        }, "(Project.Name = \"X\")", new String[]{"FormattedID"});
        assertEquals(2, performed.size());
        assertEquals("US1", performed.get(0).getTicketKey());
        assertEquals("US2", performed.get(1).getTicketKey());
        verify(rallyClient, times(3)).execute(any(GenericRequest.class));
    }

    @Test
    public void testSearchWithPageRetriesOnInvalidJson() throws IOException {
        String valid = "{\"QueryResult\":{\"TotalResultCount\":1,\"PageSize\":500,\"Results\":[]}}";
        doReturn("not a json", valid).when(rallyClient).execute(any(GenericRequest.class));
        assertEquals(1, rallyClient.search("(q)", 1, new String[]{"FormattedID"}).getQueryResult().getTotalResultCount());
        verify(rallyClient, times(2)).execute(any(GenericRequest.class));
    }

    @Test(expected = JSONException.class)
    public void testSearchWithPageRethrowsOnPersistentInvalidJson() throws IOException {
        doReturn("not a json").when(rallyClient).execute(any(GenericRequest.class));
        rallyClient.search("(q)", 1, new String[]{"FormattedID"});
    }

    @Test
    public void testSearchWithCacheExpiration() throws IOException {
        rallyClient.setCacheExpirationForJQLInHours("(expiring)", 1);
        stubExecute("search/enhanced", searchResult(issue("US1")));
        assertEquals(1, rallyClient.search("(expiring)", 1, new String[]{"FormattedID"}).getQueryResult().getTotalResultCount());
    }

    // ------------------------------------------------------------------
    // comments
    // ------------------------------------------------------------------

    @Test
    public void testGetComments() throws IOException {
        RallyIssue ticket = new RallyIssue(issue("US1"));
        stubExecute("/Discussion", "{\"QueryResult\":{\"Results\":[{\"Text\":\"first\"},{\"Text\":\"second\"}]}}");
        List<? extends IComment> comments = rallyClient.getComments("US1", ticket);
        assertEquals(2, comments.size());
        assertEquals("first", comments.get(0).getBody());
        assertEquals("second", comments.get(1).getBody());
    }

    @Test
    public void testGetCommentsResolvesTicketWhenNull() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("search/enhanced", searchResult(issue("US1")));
        responses.put("/Discussion", "{\"QueryResult\":{\"Results\":[{\"Text\":\"hello\"}]}}");
        stubExecute(responses);
        List<? extends IComment> comments = rallyClient.getComments("US1", null);
        assertEquals(1, comments.size());
        assertEquals("hello", comments.get(0).getBody());
    }

    @Test
    public void testPostCommentIfNotExistsSkipsDuplicate() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("search/enhanced", searchResult(issue("US1")));
        responses.put("/Discussion", "{\"QueryResult\":{\"Results\":[{\"Text\":\"<p>hello</p>\"}]}}");
        stubExecute(responses);
        rallyClient.postCommentIfNotExists("US1", "hello");
        verify(rallyClient, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testPostCommentIfNotExistsPostsNew() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("search/enhanced", searchResult(issue("US1")));
        responses.put("/Discussion", "{\"QueryResult\":{\"Results\":[{\"Text\":\"other\"}]}}");
        stubExecute(responses);
        doReturn("created").when(rallyClient).post(any(GenericRequest.class));
        rallyClient.postCommentIfNotExists("US1", "hello");
        verify(rallyClient, times(1)).post(any(GenericRequest.class));
    }

    @Test
    public void testPostComment() throws IOException {
        stubExecute("search/enhanced", searchResult(issue("US1")));
        doReturn("created").when(rallyClient).post(any(GenericRequest.class));
        rallyClient.postComment("US1", "hello world");
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).post(captor.capture());
        assertTrue(captor.getValue().url().contains("conversationpost/create"));
    }

    @Test
    public void testDeleteCommentIfExistsMatch() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("search/enhanced", searchResult(issue("US1")));
        responses.put("/Discussion", "{\"QueryResult\":{\"Results\":[{\"Text\":\"bye\",\"_ref\":\"http://rally/comment/9\"}]}}");
        stubExecute(responses);
        doReturn("deleted").when(rallyClient).delete(any(GenericRequest.class));
        rallyClient.deleteCommentIfExists("US1", "bye");
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).delete(captor.capture());
        assertEquals("http://rally/comment/9", captor.getValue().url());
    }

    @Test
    public void testDeleteCommentIfExistsNoMatch() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("search/enhanced", searchResult(issue("US1")));
        responses.put("/Discussion", "{\"QueryResult\":{\"Results\":[{\"Text\":\"keep\",\"_ref\":\"http://rally/comment/1\"}]}}");
        stubExecute(responses);
        rallyClient.deleteCommentIfExists("US1", "bye");
        verify(rallyClient, never()).delete(any(GenericRequest.class));
    }

    // ------------------------------------------------------------------
    // moveToStatus
    // ------------------------------------------------------------------

    @Test
    public void testMoveToStatusAlreadyInStatus() throws IOException {
        stubExecute("search/enhanced", searchResult(issue("US1", "HierarchicalRequirement", "Done")));
        assertNull(rallyClient.moveToStatus("US1", "Done"));
        verify(rallyClient, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testMoveToStatusSuccess() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("flowstate", "{\"QueryResult\":{\"Results\":[{\"_ref\":\"http://rally/flowstate/5\",\"_refObjectName\":\"Done\"}]}}");
        responses.put("search/enhanced", searchResult(issue("US1", "HierarchicalRequirement", "Open")));
        stubExecute(responses);
        doReturn("moved").when(rallyClient).post(any(GenericRequest.class));
        String result = rallyClient.moveToStatus("US1", "Done");
        assertEquals("moved", result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(rallyClient).post(captor.capture());
        assertEquals("http://rally/slm/webservice/v2.0/artifact/US1", captor.getValue().url());
    }

    @Test
    public void testMoveToStatusNoMatchingFlowState() throws IOException {
        Map<String, String> responses = new LinkedHashMap<>();
        responses.put("flowstate", "{\"QueryResult\":{\"Results\":[{\"_ref\":\"http://rally/flowstate/5\",\"_refObjectName\":\"Other\"}]}}");
        responses.put("search/enhanced", searchResult(issue("US1", "HierarchicalRequirement", "Open")));
        stubExecute(responses);
        assertNull(rallyClient.moveToStatus("US1", "Done"));
        verify(rallyClient, never()).post(any(GenericRequest.class));
    }

    // ------------------------------------------------------------------
    // iterations / fix versions
    // ------------------------------------------------------------------

    @Test
    public void testIterations() throws IOException {
        stubExecute("iteration?fetch", "{\"QueryResult\":{\"Results\":[{\"Name\":\"Sprint 1\"},{\"Name\":\"Sprint 2\"}]}}");
        List<com.github.istin.dmtools.broadcom.rally.model.Iteration> iterations =
                rallyClient.iterations("TestProject", Calendar.getInstance());
        assertEquals(2, iterations.size());
        assertEquals("Sprint 1", iterations.get(0).getName());
        assertEquals("Sprint 2", iterations.get(1).getName());
    }

    @Test
    public void testGetFixVersions() throws IOException {
        stubExecute("iteration?fetch", "{\"QueryResult\":{\"Results\":[{\"Name\":\"Sprint 1\"}]}}");
        List<? extends ReportIteration> fixVersions = rallyClient.getFixVersions("TestProject");
        assertEquals(1, fixVersions.size());
        assertEquals("Sprint 1", fixVersions.get(0).getIterationName());
    }

    // ------------------------------------------------------------------
    // urls / files
    // ------------------------------------------------------------------

    @Test
    public void testGetLastTwoSegmentsEdgeCases() {
        assertNull(RallyClient.getLastTwoSegments(null));
        assertEquals("single", RallyClient.getLastTwoSegments("single"));
        assertEquals("b/c", RallyClient.getLastTwoSegments("a/b/c"));
    }

    @Test
    public void testGetTicketBrowseUrlBuildsFromRef() {
        String url = rallyClient.getTicketBrowseUrl("http://rally/slm/webservice/v2.0/defect/12345");
        assertEquals(BASE_PATH + "/#/?detail=/defect/12345&fdp=true", url);
    }

    @Test
    public void testGetCachedFile() {
        String url = "http://example.com/attachment/image.png";
        File cachedFile = rallyClient.getCachedFile(new GenericRequest(rallyClient, url));
        assertEquals(DigestUtils.md5Hex(url) + ".png", cachedFile.getName());
    }

    @Test
    public void testDownloadAttachmentCacheHitAbsoluteAndRelative() throws IOException {
        String absoluteUrl = "http://rally1.rallydev.com/attachment/image.png";
        String relativeUrl = BASE_PATH + "/attachment/other.png";
        File cachedAbsolute = rallyClient.getCachedFile(new GenericRequest(rallyClient, absoluteUrl));
        File cachedRelative = rallyClient.getCachedFile(new GenericRequest(rallyClient, relativeUrl));
        writePng(cachedAbsolute);
        writePng(cachedRelative);
        try {
            assertEquals(cachedAbsolute.getAbsolutePath(),
                    rallyClient.downloadAttachment(absoluteUrl).getAbsolutePath());
            assertEquals(cachedRelative.getAbsolutePath(),
                    rallyClient.downloadAttachment("/attachment/other.png").getAbsolutePath());
            assertEquals(cachedAbsolute.getAbsolutePath(),
                    rallyClient.convertUrlToFile(absoluteUrl).getAbsolutePath());
        } finally {
            cachedAbsolute.delete();
            cachedRelative.delete();
            cachedAbsolute.getParentFile().delete();
        }
    }

    @Test
    public void testDownloadImageAsBase64() throws IOException {
        String url = "http://rally1.rallydev.com/attachment/image.png";
        File cached = rallyClient.getCachedFile(new GenericRequest(rallyClient, url));
        writePng(cached);
        try {
            String base64 = rallyClient.downloadImageAsBase64(url);
            assertNotNull(base64);
            assertFalse(base64.isEmpty());
        } finally {
            cached.delete();
            cached.getParentFile().delete();
        }
    }

    private static void writePng(File file) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(PNG_BYTES);
        }
    }
}
