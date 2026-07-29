// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.jira.model.Component;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.FixVersion;
import com.github.istin.dmtools.atlassian.jira.model.IssueType;
import com.github.istin.dmtools.atlassian.jira.model.IssueTypeScheme;
import com.github.istin.dmtools.atlassian.jira.model.Project;
import com.github.istin.dmtools.atlassian.jira.model.RemoteLink;
import com.github.istin.dmtools.atlassian.jira.model.SearchResult;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.atlassian.jira.model.Transition;
import com.github.istin.dmtools.atlassian.jira.model.WorkflowScheme;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.networking.RetryPolicy;
import kotlin.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional unit tests for {@link JiraClient} focused on previously uncovered
 * public methods: request building, ticket operations, field updates, search
 * flows (cloud + legacy server), project structure APIs and cache handling.
 *
 * <p>All HTTP transport is intercepted by stubbing the {@link RestClient}
 * methods ({@code execute/post/put/patch/delete(GenericRequest)}) on a Mockito
 * spy, so no real network calls are made.</p>
 */
public class JiraClientCoverageTest {

    private static final String BASE = "http://jira.example.com";
    private static final String THROW = "<throw>";

    /**
     * Concrete JiraClient used by the tests. The class name also determines
     * the cache folder ("cacheTestableJiraClient").
     */
    static class TestableJiraClient extends JiraClient<Ticket> {
        TestableJiraClient() throws IOException {
            super(BASE, "auth");
        }

        TestableJiraClient(String basePath) throws IOException {
            super(basePath, "auth");
        }

        TestableJiraClient(String basePath, String auth, int maxResults) throws IOException {
            super(basePath, auth, maxResults);
        }

        @Override
        public String getTextFieldsOnly(ITicket ticket) {
            return "";
        }

        @Override
        public String[] getDefaultQueryFields() {
            return new String[0];
        }

        @Override
        public String[] getExtendedQueryFields() {
            return new String[0];
        }

        @Override
        public TextType getTextType() {
            return null;
        }
    }

    /** Sequential responses for a URL fragment: after the last one is used, it repeats. */
    private static class Responses {
        private final List<String> values;
        private int index = 0;

        Responses(String... values) {
            this.values = Arrays.asList(values);
        }

        String next() throws IOException {
            String value = values.get(Math.min(index, values.size() - 1));
            if (index < values.size()) {
                index++;
            }
            if (value.startsWith(THROW)) {
                String message = value.length() > THROW.length() + 1 ? value.substring(THROW.length() + 1) : "boom";
                throw new IOException(message);
            }
            return value;
        }
    }

    private TestableJiraClient client;

    private final Map<String, Responses> getRoutes = new LinkedHashMap<>();
    private final Map<String, Responses> postRoutes = new LinkedHashMap<>();
    private final Map<String, Responses> putRoutes = new LinkedHashMap<>();
    private final Map<String, Responses> deleteRoutes = new LinkedHashMap<>();
    private final List<File> createdCacheFiles = new ArrayList<>();

    private String postResponse = "";
    private String putResponse = "";
    private String deleteResponse = "";

    @Before
    public void setUp() throws IOException {
        client = spy(new TestableJiraClient());
        stubTransport(client);
    }

    @After
    public void tearDown() {
        for (File file : createdCacheFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void stubTransport(JiraClient<Ticket> spy) throws IOException {
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            return dispatchGet(request.url());
        }).when(spy).execute(any(GenericRequest.class));
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            return dispatch(request.url(), postRoutes, postResponse);
        }).when(spy).post(any(GenericRequest.class));
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            return dispatch(request.url(), putRoutes, putResponse);
        }).when(spy).put(any(GenericRequest.class));
        doAnswer(invocation -> patchResponse).when(spy).patch(any(GenericRequest.class));
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            return dispatch(request.url(), deleteRoutes, deleteResponse);
        }).when(spy).delete(any(GenericRequest.class));
    }

    private String patchResponse = "";

    private String dispatchGet(String url) throws IOException {
        String routed = dispatch(url, getRoutes, null);
        if (routed != null) {
            return routed;
        }
        if (url.endsWith("serverInfo")) {
            return "{\"deploymentType\":\"Server\"}";
        }
        if (url.contains("api/latest/field")) {
            return "[]";
        }
        return "{}";
    }

    private String dispatch(String url, Map<String, Responses> routes, String defaultResponse) throws IOException {
        for (Map.Entry<String, Responses> entry : routes.entrySet()) {
            if (url.contains(entry.getKey())) {
                return entry.getValue().next();
            }
        }
        return defaultResponse;
    }

    private void routeGet(String urlFragment, String... responses) {
        getRoutes.put(urlFragment, new Responses(responses));
    }

    private void routePost(String urlFragment, String... responses) {
        postRoutes.put(urlFragment, new Responses(responses));
    }

    private void routePut(String urlFragment, String... responses) {
        putRoutes.put(urlFragment, new Responses(responses));
    }

    private void routeDelete(String urlFragment, String... responses) {
        deleteRoutes.put(urlFragment, new Responses(responses));
    }

    private GenericRequest lastExecuteRequest() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, atLeastOnce()).execute(captor.capture());
        return captor.getValue();
    }

    private GenericRequest lastPutRequest() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, atLeastOnce()).put(captor.capture());
        return captor.getValue();
    }

    private GenericRequest lastPostRequest() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, atLeastOnce()).post(captor.capture());
        return captor.getValue();
    }

    private GenericRequest lastDeleteRequest() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, atLeastOnce()).delete(captor.capture());
        return captor.getValue();
    }

    private GenericRequest findExecuteRequest(String urlFragment) throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().stream()
                .filter(request -> request.url().contains(urlFragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no execute request containing " + urlFragment));
    }

    private File createCacheFile(GenericRequest request, String content) throws IOException {
        File file = client.getCachedFile(request);
        Files.writeString(file.toPath(), content);
        createdCacheFiles.add(file);
        return file;
    }

    private static Ticket ticket(String key) {
        return new Ticket(new JSONObject().put("key", key).put("fields", new JSONObject()));
    }

    // ─── constructors & simple accessors ─────────────────────────────────────

    @Test
    public void testConstructorWithMaxResults() throws IOException {
        TestableJiraClient withMax = new TestableJiraClient(BASE, "auth", 25);
        assertEquals(BASE, withMax.getBasePath());
        assertNotNull(withMax.getClient());
    }

    @Test
    public void testConstructorWithLogger() throws IOException {
        JiraClient<Ticket> withLogger = new TestableJiraClient(BASE) {
        };
        assertNotNull(withLogger.getRetryPolicy());
    }

    @Test
    public void testGetSetRetryPolicy() {
        RetryPolicy custom = new RetryPolicy(1, 1L, 1L, 1.0, 0.0, null);
        client.setRetryPolicy(custom);
        assertSame(custom, client.getRetryPolicy());
        client.setRetryPolicy(null);
        assertNotNull(client.getRetryPolicy());
    }

    @Test
    public void testClearCloudJiraDetectionCache() {
        client.clearCloudJiraDetectionCache();
    }

    @Test
    public void testGetDefaultStatusField() {
        assertEquals("status", client.getDefaultStatusField());
    }

    @Test
    public void testParseJiraProjectVariants() {
        assertEquals("TEST", JiraClient.parseJiraProject("test-123"));
        assertEquals("ABC", JiraClient.parseJiraProject("ABC-1"));
    }

    @Test
    public void testSetCacheExpirationForJQL() throws Exception {
        client.setCacheExpirationForJQLInHours("project = TP", 1);
        routeGet("/search?", "{\"maxResults\":50,\"total\":0,\"issues\":[]}");
        SearchResult result = client.search("project = TP", 0, List.of("summary"));
        assertNotNull(result);
    }

    // ─── request builders ────────────────────────────────────────────────────

    @Test
    public void testRequestBuilders() throws IOException {
        assertEquals(BASE + "/rest/api/latest/issue/TP-1", client.getTicket("TP-1").url());
        assertEquals(BASE + "/rest/api/latest/issue/TP-1/worklog", client.getWorklog("TP-1").url());
        assertEquals(BASE + "/rest/api/latest/serverInfo", client.getConfig().url());
        assertEquals(BASE + "/rest/api/latest/filter/5", client.filter("5").url());
        assertEquals(BASE + "/rest/api/latest/issue/TP-1?expand=changelog&fields=summary",
                client.createChangelogRequest("TP-1").url());
        assertEquals(BASE + "/rest/api/latest/issue", client.createTicket().url());
        assertEquals(BASE + "/rest/api/latest/issue/TP-1/subtask", client.getSubtasks("TP-1").url());
        assertEquals(BASE + "/rest/api/latest/issue/TP-1/remotelink", client.getRemoteLinks("TP-1").url());
        assertEquals(BASE + "/rest/api/latest/issue/TP-1/transitions?expand=transitions.fields",
                client.transitions("TP-1").url());
        assertEquals(BASE + "/rest/api/latest/issue/TP-1/comment", client.comment("TP-1", null).url());
        FixVersion fixVersion = new FixVersion(new JSONObject().put("id", "100").put("name", "v1"));
        assertEquals(BASE + "/rest/api/latest/version/100", client.fixVersion(fixVersion).url());
    }

    @Test
    public void testCreatePerformTicketRequestWithFields() {
        GenericRequest request = client.createPerformTicketRequest("TP-1", new String[]{"summary", "labels"});
        assertTrue(request.url().contains("issue/TP-1?fields="));
        assertTrue(request.url().contains("summary"));
        assertTrue(request.url().contains("labels"));
    }

    @Test
    public void testCreatePerformTicketRequestWithoutFields() {
        GenericRequest request = client.createPerformTicketRequest("TP-1", new String[0]);
        assertEquals(BASE + "/rest/api/latest/issue/TP-1", request.url());
    }

    @Test
    public void testBuildUrlToSearch() {
        assertEquals(BASE + "/issues/?jql=project+%3D+TP", client.buildUrlToSearch("project = TP"));
    }

    @Test
    public void testStaticJqlBuilders() {
        assertEquals("key in (A-1,B-2)", JiraClient.buildJQL(Arrays.asList("A-1", "B-2")));
        List<Key> keys = Arrays.asList(ticket("A-1"), ticket("B-2"));
        assertEquals("key in (A-1,B-2)", JiraClient.buildJQLByKeys(keys));
        assertEquals("", JiraClient.buildJQLByKeys(Collections.emptyList()));
        assertEquals("key not in (A-1,B-2)", JiraClient.buildNotInJQLByKeys(keys));
        assertEquals("project not in (A,B)", JiraClient.buildJQLNotInProjects(keys));
        assertEquals("", JiraClient.buildJQLNotInProjects(Collections.emptyList()));
        assertEquals(BASE + "/issues/?jql=key+in+%28A-1%2CB-2%29", JiraClient.buildJQLUrl(BASE, keys));
        assertEquals(BASE + "/issues/?jql=key%3DA-1", client.buildJQLUrl("key=A-1"));
        assertEquals(BASE + "/issues/?jql=key%3DA-1", JiraClient.buildJQLUrl(BASE, "key=A-1"));
    }

    @Test
    public void testTagAndParseNotifierId() {
        String tagged = JiraClient.tag(BASE, "user1", "User One");
        assertTrue(tagged.contains("ViewProfile.jspa?name=user1"));
        assertTrue(tagged.contains("User One"));

        assertEquals("abc-123", JiraClient.parseNotifierId("[~accountid:abc-123]"));
        assertEquals("", JiraClient.parseNotifierId(null));
        assertEquals("", JiraClient.parseNotifierId(""));
        assertEquals("", JiraClient.parseNotifierId("plain text"));

        assertEquals("", client.tag(null));
        assertEquals("[~user]", client.tag("~user"));
        assertEquals("[~accountid:abc]", client.tag("abc"));
    }

    @Test
    public void testGetQueryMapAndFilterJQL() throws IOException {
        Map<String, String> map = JiraClient.getQueryMap("jql=project%3DTP&maxResults=50");
        assertEquals("project%3DTP", map.get("jql"));
        assertEquals("50", map.get("maxResults"));

        routeGet("filter/5", "{\"searchUrl\":\"http://jira.example.com/issues/?jql=project%3DTP&maxResults=50\"}");
        assertEquals("project%3DTP", client.filterJQL("5"));
    }

    @Test
    public void testCoerceFieldValue() {
        assertSame(Boolean.TRUE, JiraClient.coerceFieldValue("true"));
        assertSame(Boolean.FALSE, JiraClient.coerceFieldValue(" FALSE "));
        assertEquals(42, JiraClient.coerceFieldValue("42"));
        assertEquals(9999999999L, JiraClient.coerceFieldValue("9999999999"));
        assertEquals(1.5, JiraClient.coerceFieldValue("1.5"));
        Object object = JiraClient.coerceFieldValue("{\"a\":1}");
        assertTrue(object instanceof JSONObject);
        assertEquals(1, ((JSONObject) object).getInt("a"));
        Object array = JiraClient.coerceFieldValue("[1,2]");
        assertTrue(array instanceof JSONArray);
        assertEquals(2, ((JSONArray) array).length());
        // Wiki markup must NOT be treated as a JSON object
        String wiki = "{code:mermaid}\ngraph TD\n{code}";
        assertSame(wiki, JiraClient.coerceFieldValue(wiki));
        String partialArray = "[1,2] trailing";
        assertSame(partialArray, JiraClient.coerceFieldValue(partialArray));
        assertEquals("plain", JiraClient.coerceFieldValue("plain"));
        assertEquals("", JiraClient.coerceFieldValue(""));
        Object nonString = new JSONArray();
        assertSame(nonString, JiraClient.coerceFieldValue(nonString));
    }

    @Test
    public void testIsErrorResponse() {
        assertFalse(client.isErrorResponse(null));
        assertFalse(client.isErrorResponse(""));
        assertFalse(client.isErrorResponse("{\"issues\":[]}"));
        assertFalse(client.isErrorResponse("errorMessages mentioned in plain text"));
        assertFalse(client.isErrorResponse("{\"errorMessages\":[]}"));
        assertFalse(client.isErrorResponse("{\"errorMessages\":\"not an array\"}"));
        assertTrue(client.isErrorResponse("{\"errorMessages\":[\"boom\"]}"));
    }

    // ─── ticket operations ───────────────────────────────────────────────────

    @Test
    public void testDeleteIssueLink() throws IOException {
        client.deleteIssueLink("123");
        assertTrue(lastDeleteRequest().url().endsWith("issueLink/123"));
    }

    @Test
    public void testDeleteTicketEmptyResponse() throws IOException {
        deleteResponse = "";
        assertEquals(JiraClient.SUCCESS, client.deleteTicket("TP-1"));
        assertTrue(lastDeleteRequest().url().endsWith("issue/TP-1"));
    }

    @Test
    public void testDeleteTicketWithResponse() throws IOException {
        deleteResponse = "deleted";
        assertEquals("deleted", client.deleteTicket("TP-1"));
    }

    @Test
    public void testGetAccountByEmailFound() throws IOException {
        routeGet("user/search", "[{\"accountId\":\"acc1\",\"displayName\":\"John\",\"emailAddress\":\"j@e.com\"}]");
        Assignee assignee = client.getAccountByEmail("j@e.com");
        assertNotNull(assignee);
        assertEquals("j@e.com", assignee.getEmailAddress());
    }

    @Test
    public void testGetAccountByEmailNotFound() throws IOException {
        routeGet("user/search", "[]");
        assertNull(client.getAccountByEmail("nobody@e.com"));
    }

    @Test
    public void testAssignTo() throws IOException {
        putResponse = "";
        client.assignTo("TP-1", "account-123");
        GenericRequest request = lastPutRequest();
        assertTrue(request.url().endsWith("issue/TP-1/assignee"));
        assertTrue(request.getBody().contains("account-123"));
    }

    @Test
    public void testGetChangeLogPreloaded() throws IOException {
        Ticket ticket = new Ticket(new JSONObject()
                .put("key", "TP-1")
                .put("changelog", new JSONObject().put("histories", new JSONArray().put(new JSONObject().put("id", "h1")))));
        IChangelog changelog = client.getChangeLog("TP-1", ticket);
        assertNotNull(changelog);
        verify(client, never()).execute(any(GenericRequest.class));
    }

    @Test
    public void testGetChangeLogFetched() throws IOException {
        routeGet("expand=changelog", "{\"key\":\"TP-1\",\"changelog\":{\"histories\":[{\"id\":\"h1\"}]}}");
        IChangelog changelog = client.getChangeLog("TP-1", null);
        assertNotNull(changelog);
        assertTrue(lastExecuteRequest().url().contains("expand=changelog&fields=summary"));
    }

    @Test
    public void testGetChangeLogRetryAfterJsonError() throws IOException {
        routeGet("expand=changelog", "not json at all", "{\"key\":\"TP-1\",\"changelog\":{\"histories\":[]}}");
        IChangelog changelog = client.getChangeLog("TP-1", null);
        assertNotNull(changelog);
        verify(client, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testGetChangeLogClearsExpiredCache() throws IOException {
        GenericRequest request = client.createChangelogRequest("TP-1");
        File cachedFile = createCacheFile(request, "stale");
        assertTrue(cachedFile.setLastModified(System.currentTimeMillis() - 10_000_000L));

        Ticket ticket = new Ticket(new JSONObject()
                .put("key", "TP-1")
                .put("fields", new JSONObject().put("updated", "2099-01-01T00:00:00.000+0000")));
        routeGet("expand=changelog", "{\"key\":\"TP-1\",\"changelog\":{\"histories\":[]}}");

        IChangelog changelog = client.getChangeLog("TP-1", ticket);
        assertNotNull(changelog);
        assertFalse("expired cache file must be deleted", cachedFile.exists());
    }

    @Test
    public void testAddLabel() throws IOException {
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{\"labels\":[\"a\"]}}");
        putResponse = "";
        client.addLabel("TP-1", "b");
        GenericRequest request = lastPutRequest();
        assertTrue(request.getBody().contains("labels"));
        assertTrue(request.getBody().contains("b"));
    }

    @Test
    public void testAddLabelIfNotExistsNullLabels() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1").put("fields", new JSONObject()));
        putResponse = "";
        client.addLabelIfNotExists(ticket, "new-label");
        assertTrue(lastPutRequest().getBody().contains("new-label"));
    }

    @Test
    public void testAddLabelIfNotExistsExistingLabel() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1")
                .put("fields", new JSONObject().put("labels", new JSONArray().put("Keep"))));
        client.addLabelIfNotExists(ticket, "keep");
        verify(client, never()).put(any(GenericRequest.class));
    }

    @Test
    public void testDeleteLabelInTicket() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1")
                .put("fields", new JSONObject().put("labels", new JSONArray().put("a").put("B"))));
        putResponse = "";
        client.deleteLabelInTicket(ticket, "b");
        GenericRequest request = lastPutRequest();
        assertTrue(request.getBody().contains("a"));
        assertFalse(request.getBody().contains("\"B\""));
    }

    @Test
    public void testDeleteLabelInTicketNullLabels() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1").put("fields", new JSONObject()));
        client.deleteLabelInTicket(ticket, "b");
        verify(client, never()).put(any(GenericRequest.class));
    }

    @Test
    public void testRemoveLabel() throws IOException {
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{\"labels\":[\"a\",\"b\"]}}");
        putResponse = "";
        client.removeLabel("TP-1", "a");
        verify(client, atLeastOnce()).put(any(GenericRequest.class));
    }

    @Test
    public void testGetTestCases() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1")
                .put("fields", new JSONObject().put("issuelinks", new JSONArray()
                        .put(new JSONObject().put("inwardIssue", new JSONObject().put("key", "TP-2")
                                .put("fields", new JSONObject().put("issuetype", new JSONObject().put("name", "Test Case")))))
                        .put(new JSONObject().put("outwardIssue", new JSONObject().put("key", "TP-3")
                                .put("fields", new JSONObject().put("issuetype", new JSONObject().put("name", "Bug")))))
                        .put(new JSONObject().put("id", "5")))));
        List<? extends ITicket> testCases = client.getTestCases(ticket, "test case");
        assertEquals(1, testCases.size());
        assertEquals("TP-2", testCases.get(0).getKey());
    }

    @Test
    public void testGetTestCasesNullFields() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1"));
        assertTrue(client.getTestCases(ticket, "Test Case").isEmpty());
    }

    @Test
    public void testGetTestCasesNoLinks() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1").put("fields", new JSONObject()));
        assertTrue(client.getTestCases(ticket, "Test Case").isEmpty());
    }

    @Test
    public void testPerformTicket() throws IOException {
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{\"summary\":\"Hello\"}}");
        Ticket result = client.performTicket("TP-1", new String[]{"summary"});
        assertNotNull(result);
        assertEquals("TP-1", result.getKey());
        assertTrue(lastExecuteRequest().url().contains("fields=summary"));
    }

    @Test
    public void testPerformTicketErrorResponse() throws IOException {
        routeGet("issue/TP-1", "{\"errorMessages\":[\"Issue does not exist\"]}");
        assertNull(client.performTicket("TP-1", new String[]{"summary"}));
    }

    @Test
    public void testPerformTicketBlankFields() throws IOException {
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{}}");
        Ticket result = client.performTicket("TP-1", new String[]{"  "});
        assertNotNull(result);
        assertEquals(BASE + "/rest/api/latest/issue/TP-1", lastExecuteRequest().url());
    }

    @Test
    public void testPerformMyProfile() throws IOException {
        routeGet("myself", "{\"accountId\":\"me\",\"displayName\":\"John Doe\",\"emailAddress\":\"j@e.com\"}");
        IUser profile = client.performMyProfile();
        assertEquals("j@e.com", profile.getEmailAddress());
    }

    @Test
    public void testPerformProfile() throws IOException {
        routeGet("latest/user?", "{\"accountId\":\"u1\",\"displayName\":\"Jane\",\"emailAddress\":\"jane@e.com\"}");
        IUser profile = client.performProfile("u1");
        assertEquals("jane@e.com", profile.getEmailAddress());
        assertTrue(lastExecuteRequest().url().contains("accountId=u1"));
    }

    @Test
    public void testTestConnectionSuccess() throws IOException {
        routeGet("myself", "{\"displayName\":\"John Doe\",\"emailAddress\":\"j@e.com\"}");
        Map<String, Object> result = client.testConnection();
        assertEquals(true, result.get("success"));
        assertEquals("j@e.com", result.get("email"));
    }

    @Test
    public void testTestConnectionFailure() throws IOException {
        routeGet("myself", THROW);
        Map<String, Object> result = client.testConnection();
        assertEquals(false, result.get("success"));
        assertEquals("IOException", result.get("error"));
    }

    @Test
    public void testPerformGettingRemoteLinks() throws IOException {
        routeGet("remotelink", "[{\"id\":\"1\",\"url\":\"http://example.com/page\"}]");
        List<RemoteLink> links = client.performGettingRemoteLinks("TP-1");
        assertEquals(1, links.size());
    }

    @Test
    public void testGetComments() throws IOException {
        routeGet("issue/TP-1/comment", "{\"comments\":[{\"body\":\"c1\"},{\"body\":\"c2\"}],\"total\":2}");
        List<? extends IComment> comments = client.getComments("TP-1", null);
        assertEquals(2, comments.size());
    }

    @Test
    public void testGetCommentsWithTicketCacheValidation() throws IOException {
        Ticket ticket = new Ticket(new JSONObject().put("key", "TP-1")
                .put("fields", new JSONObject().put("updated", "2024-01-01T10:00:00.000+0000")));
        routeGet("issue/TP-1/comment", "{\"comments\":[{\"body\":\"c1\"}]}");
        List<? extends IComment> comments = client.getComments("TP-1", ticket);
        assertEquals(1, comments.size());
    }

    @Test
    public void testPostComment() throws IOException {
        client.postComment("TP-1", "hello world");
        GenericRequest request = lastPostRequest();
        assertTrue(request.url().endsWith("issue/TP-1/comment"));
        assertTrue(request.getBody().contains("hello world"));
    }

    @Test
    public void testPostCommentIfNotExistsSkipsExisting() throws IOException {
        routeGet("issue/TP-1/comment", "{\"comments\":[{\"body\":\"Existing comment\"}]}");
        client.postCommentIfNotExists("TP-1", "existing COMMENT");
        verify(client, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testPostCommentIfNotExistsPosts() throws IOException {
        routeGet("issue/TP-1/comment", "{\"comments\":[{\"body\":\"Other\"}]}");
        client.postCommentIfNotExists("TP-1", "Brand new");
        assertTrue(lastPostRequest().getBody().contains("Brand new"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteCommentIfExistsThrows() throws IOException {
        client.deleteCommentIfExists("TP-1", "comment");
    }

    @Test
    public void testGetFixVersionsAndFindVersion() throws IOException {
        routeGet("/versions", "[{\"id\":\"v1\",\"name\":\"1.0\"},{\"id\":\"v2\",\"name\":\"2.0\"}]");
        List<? extends ReportIteration> versions = client.getFixVersions("TP");
        assertEquals(2, versions.size());
        assertNotNull(client.findVersion("2.0", "TP"));
        assertNull(client.findVersion("3.0", "TP"));
    }

    @Test
    public void testCreateFixVersion() throws IOException {
        postResponse = "created";
        String result = client.createFixVersion("TP", "1.0", new Date(1_700_000_000_000L), new Date(1_800_000_000_000L));
        assertEquals("created", result);
        GenericRequest request = lastPostRequest();
        assertTrue(request.url().contains("/rest/api/2/project/TP/version"));
        assertTrue(request.getBody().contains("\"name\":\"1.0\""));
        assertTrue(request.getBody().contains("startDate"));
        assertTrue(request.getBody().contains("releaseDate"));
    }

    @Test
    public void testGetComponents() throws IOException {
        routeGet("/components", "[{\"id\":\"c1\",\"name\":\"Backend\"}]");
        List<Component> components = client.getComponents("TP");
        assertEquals(1, components.size());
    }

    @Test
    public void testUpdateFixVersion() throws IOException {
        FixVersion fixVersion = new FixVersion(new JSONObject().put("id", "100").put("name", "v1"));
        putResponse = "updated";
        assertEquals("updated", client.updateFixVersion(fixVersion));
        assertTrue(lastPutRequest().url().endsWith("version/100"));
    }

    @Test
    public void testMoveFixVersion() throws IOException {
        FixVersion fixVersion = new FixVersion(new JSONObject().put("id", "100").put("name", "v1"));
        FixVersion after = new FixVersion(new JSONObject().put("id", "200").put("name", "v2"));
        postResponse = "moved";
        assertEquals("moved", client.moveFixVersion(fixVersion, after));
        GenericRequest request = lastPostRequest();
        assertTrue(request.url().endsWith("version/" + fixVersion.getId() + "/move"));
        assertTrue(request.getBody().contains("/rest/api/latest/version/" + after.getId()));
    }

    @Test
    public void testGetStatusesAndFindStatus() throws IOException {
        routeGet("/statuses", "[{\"name\":\"Bug\",\"statuses\":[{\"name\":\"Open\"},{\"name\":\"Done\"}]}]");
        List<com.github.istin.dmtools.atlassian.jira.model.ProjectStatus> statuses = client.getStatuses("TP");
        assertEquals(1, statuses.size());

        Status found = client.findStatus("TP", "bug", "done");
        assertNotNull(found);
        assertEquals("Done", found.getName());
        assertNull(client.findStatus("TP", "bug", "Missing"));
        assertNull(client.findStatus("TP", "task", "Open"));
    }

    @Test
    public void testCreateTicketInProjectWithParent() throws IOException {
        routeGet("issue/TP-9", "{\"key\":\"TP-9\",\"fields\":{\"summary\":\"Parent\"}}");
        postResponse = "{\"key\":\"TP-10\",\"id\":\"10010\"}";
        String result = client.createTicketInProjectWithParent("TP", "Sub-task", "Child", "Desc", "TP-9");
        assertTrue(result.contains("TP-10"));
        GenericRequest request = lastPostRequest();
        assertTrue(request.getBody().contains("parent"));
        assertTrue(request.getBody().contains("TP-9"));
    }

    @Test
    public void testCreateTicketInProject() throws IOException {
        postResponse = "{\"key\":\"TP-1\",\"id\":\"10001\"}";
        String result = client.createTicketInProject("TP", "Bug", "Summary", "Description", null);
        assertTrue(result.contains("TP-1"));
        GenericRequest request = lastPostRequest();
        JSONObject fields = new JSONObject(request.getBody()).getJSONObject("fields");
        assertEquals("TP", fields.getJSONObject("project").getString("key"));
        assertEquals("Summary", fields.getString("summary"));
        assertEquals("Bug", fields.getJSONObject("issuetype").getString("name"));
    }

    @Test
    public void testCreateTicketInProjectWithCustomField() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_10016\",\"name\":\"Story Points\"}]");
        postResponse = "{\"key\":\"TP-2\"}";
        client.createTicketInProject("TP", "Story", "S", "D", fields -> fields.set("Story Points", 5));
        GenericRequest request = lastPostRequest();
        JSONObject fields = new JSONObject(request.getBody()).getJSONObject("fields");
        assertEquals(5, fields.getInt("customfield_10016"));
    }

    @Test
    public void testCreateTicketInProjectErrorMessages() throws IOException {
        postResponse = "{\"errorMessages\":[\"first error\",\"second error\"]}";
        IOException exception = assertThrows(IOException.class,
                () -> client.createTicketInProject("TP", "Bug", "S", "D", null));
        assertTrue(exception.getMessage().contains("1. first error"));
        assertTrue(exception.getMessage().contains("2. second error"));
    }

    @Test
    public void testCreateTicketInProjectFieldErrors() throws IOException {
        postResponse = "{\"errors\":{\"summary\":\"Summary is required\",\"customfield_1\":\"bad value\"}}";
        IOException exception = assertThrows(IOException.class,
                () -> client.createTicketInProject("TP", "Bug", "S", "D", null));
        assertTrue(exception.getMessage().contains("Summary (summary): Summary is required"));
        assertTrue(exception.getMessage().contains("Field customfield_1: bad value"));
    }

    @Test
    public void testCreateTicketInProjectSingleError() throws IOException {
        postResponse = "{\"errorMessages\":[\"only error\"]}";
        IOException exception = assertThrows(IOException.class,
                () -> client.createTicketInProjectMcp("TP", "Bug", "S", "D"));
        assertTrue(exception.getMessage().contains("Failed to create ticket - only error"));
    }

    @Test
    public void testCreateTicketInProjectWithJson() throws IOException {
        postResponse = "{\"key\":\"TP-6\"}";
        JSONObject fieldsJson = new JSONObject()
                .put("summary", "From JSON")
                .put("priority", new JSONObject().put("name", "High"));
        String result = client.createTicketInProjectWithJson("TP", fieldsJson);
        assertTrue(result.contains("TP-6"));
        JSONObject fields = new JSONObject(lastPostRequest().getBody()).getJSONObject("fields");
        assertEquals("TP", fields.getJSONObject("project").getString("key"));
        assertEquals("From JSON", fields.getString("summary"));
        assertEquals("High", fields.getJSONObject("priority").getString("name"));
    }

    @Test
    public void testCreateTicketInProjectWithJsonNull() throws IOException {
        postResponse = "{\"key\":\"TP-7\"}";
        client.createTicketInProjectWithJson("TP", null);
        JSONObject fields = new JSONObject(lastPostRequest().getBody()).getJSONObject("fields");
        assertEquals(1, fields.length());
        assertEquals("TP", fields.getJSONObject("project").getString("key"));
    }

    @Test
    public void testIssuesInParentByType() throws Exception {
        doAnswer(invocation -> {
            JiraClient.Performer<Ticket> performer = invocation.getArgument(0);
            performer.perform(ticket("TP-2"));
            return null;
        }).when(client).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));

        List<Ticket> tickets = client.issuesInParentByType("TP-1", "Bug", "summary");
        assertEquals(1, tickets.size());
        assertEquals("TP-2", tickets.get(0).getKey());
    }

    @Test
    public void testGetAllTicketsByJQL() throws Exception {
        doAnswer(invocation -> {
            JiraClient.Performer<Ticket> performer = invocation.getArgument(0);
            performer.perform(ticket("TP-2"));
            performer.perform(ticket("TP-3"));
            return null;
        }).when(client).searchAndPerform(any(JiraClient.Performer.class), anyString(), any(String[].class));

        Map<String, ITicket> result = client.getAllTicketsByJQL("project = TP", new String[]{"summary"});
        assertEquals(2, result.size());
        assertTrue(result.containsKey("TP-2"));
        assertTrue(result.containsKey("TP-3"));
    }

    @Test
    public void testUpdateTicket2() throws IOException {
        putResponse = "ok";
        assertEquals("ok", client.updateTicket2("TP-1", fields -> fields.set("summary", "New")));
        assertTrue(lastPutRequest().getBody().contains("New"));
    }

    @Test
    public void testUpdateTicket2NullInitializer() throws IOException {
        putResponse = "ok";
        assertEquals("ok", client.updateTicket2("TP-1", null));
        assertTrue(lastPutRequest().getBody().contains("fields"));
    }

    @Test
    public void testUpdateTicketParent() throws IOException {
        putResponse = "";
        client.updateTicketParent("TP-1", "TP-9");
        JSONObject body = new JSONObject(lastPutRequest().getBody());
        assertEquals("TP-9", body.getJSONObject("fields").getJSONObject("parent").getString("key"));
    }

    @Test
    public void testUpdateTicketWithInitializer() throws IOException {
        putResponse = "done";
        String result = client.updateTicket("TP-1", fields -> {
            fields.set("fixVersions", new JSONObject().put("key", "V1"));
            fields.set("comment", "plain text");
        });
        assertEquals("done", result);
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("V1"));
        assertTrue(body.contains("plain text"));
        assertTrue(body.contains("set"));
    }

    @Test
    public void testUpdateTicketWithJsonParams() throws IOException {
        putResponse = "updated";
        JSONObject params = new JSONObject().put("fields", new JSONObject().put("summary", "New Summary"));
        assertEquals("updated", client.updateTicket("TP-1", params));
        assertTrue(lastPutRequest().getBody().contains("New Summary"));
    }

    @Test
    public void testClearField() throws IOException {
        putResponse = "cleared";
        assertEquals("cleared", client.clearField("TP-1", "description"));
        assertTrue(lastPutRequest().getBody().contains("description"));
    }

    @Test
    public void testMoveToTransitionId() throws IOException {
        postResponse = "moved";
        assertEquals("moved", client.moveToTransitionId("TP-1", "11"));
        GenericRequest request = lastPostRequest();
        assertTrue(request.url().contains("transitions"));
        assertTrue(request.getBody().contains("\"id\":\"11\""));
    }

    @Test
    public void testMoveToTransitionIdWithResolution() throws IOException {
        postResponse = "resolved";
        assertEquals("resolved", client.moveToTransitionId("TP-1", "11", "Done"));
        String body = lastPostRequest().getBody();
        assertTrue(body.contains("resolution"));
        assertTrue(body.contains("Done"));
    }

    @Test
    public void testMoveToStatus() throws IOException {
        routeGet("transitions", "{\"transitions\":[{\"id\":\"11\",\"name\":\"Done\",\"to\":{\"name\":\"Done\"}}," +
                "{\"id\":\"12\",\"name\":\"Start Progress\",\"to\":{\"name\":\"In Progress\"}}]}");
        postResponse = "moved";
        assertEquals("moved", client.moveToStatus("TP-1", "done"));
        assertTrue(lastPostRequest().getBody().contains("\"id\":\"11\""));
    }

    @Test
    public void testMoveToStatusByTargetName() throws IOException {
        routeGet("transitions", "{\"transitions\":[{\"id\":\"12\",\"name\":\"Start Progress\",\"to\":{\"name\":\"In Progress\"}}]}");
        postResponse = "moved";
        assertEquals("moved", client.moveToStatus("TP-1", "In Progress"));
        assertTrue(lastPostRequest().getBody().contains("\"id\":\"12\""));
    }

    @Test
    public void testMoveToStatusNotFound() throws IOException {
        routeGet("transitions", "{\"transitions\":[{\"id\":\"11\",\"name\":\"Done\",\"to\":{\"name\":\"Done\"}}]}");
        assertNull(client.moveToStatus("TP-1", "No Such Status"));
    }

    @Test
    public void testMoveToStatusWithResolution() throws IOException {
        routeGet("transitions", "{\"transitions\":[{\"id\":\"11\",\"name\":\"Done\",\"to\":{\"name\":\"Done\"}}]}");
        postResponse = "resolved";
        assertEquals("resolved", client.moveToStatus("TP-1", "Done", "Fixed"));
        String body = lastPostRequest().getBody();
        assertTrue(body.contains("resolution"));
        assertTrue(body.contains("Fixed"));
    }

    @Test
    public void testSetTicketFixVersion() throws IOException {
        putResponse = "ok";
        assertEquals("ok", client.setTicketFixVersion("TP-1", "1.0"));
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("fixVersions"));
        assertTrue(body.contains("set"));
        assertTrue(body.contains("1.0"));
    }

    @Test
    public void testAddTicketFixVersion() throws IOException {
        putResponse = "ok";
        assertEquals("ok", client.addTicketFixVersion("TP-1", "2.0"));
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("add"));
        assertTrue(body.contains("2.0"));
    }

    @Test
    public void testRemoveTicketFixVersion() throws IOException {
        putResponse = "ok";
        assertEquals("ok", client.removeTicketFixVersion("TP-1", "2.0"));
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("remove"));
        assertTrue(body.contains("2.0"));
    }

    @Test
    public void testSetTicketPriority() throws IOException {
        putResponse = "priority-set";
        assertEquals("priority-set", client.setTicketPriority("TP-1", "High"));
        assertTrue(lastPutRequest().getBody().contains("High"));
    }

    @Test
    public void testGetTransitions() throws IOException {
        routeGet("transitions", "{\"transitions\":[{\"id\":\"11\",\"name\":\"Done\",\"to\":{\"name\":\"Done\"}}]}");
        List<Transition> transitions = client.getTransitions("TP-1");
        assertEquals(1, transitions.size());
        assertEquals("11", transitions.get(0).getId());
        assertEquals("Done", transitions.get(0).getValue());
        assertEquals("Done", transitions.get(0).getToStatusName());
    }

    @Test
    public void testDeleteRemoteLink() throws IOException {
        client.deleteRemoteLink("TP-1", "http://example.com/page?a=1");
        GenericRequest request = lastDeleteRequest();
        assertTrue(request.url().contains("issue/TP-1/remotelink?globalId="));
        assertTrue(request.url().contains("http"));
    }

    // ─── updateField family ──────────────────────────────────────────────────

    @Test
    public void testUpdateFieldSystemField() throws IOException {
        putResponse = "";
        String result = client.updateField("TP-1", "summary", "New summary");
        assertEquals("Field 'summary' updated successfully on ticket TP-1", result);
        assertTrue(lastPutRequest().getBody().contains("New summary"));
    }

    @Test
    public void testUpdateFieldReturnsResultBody() throws IOException {
        putResponse = "{\"ok\":true}";
        String result = client.updateField("TP-1", "summary", "x");
        assertEquals("{\"ok\":true}", result);
    }

    @Test
    public void testUpdateFieldErrorResponseThrows() {
        putResponse = "{\"errorMessages\":[\"cannot update\"]}";
        IOException exception = assertThrows(IOException.class,
                () -> client.updateField("TP-1", "summary", "x"));
        assertTrue(exception.getMessage().contains("cannot update"));
    }

    @Test
    public void testUpdateFieldNonJsonResultBody() throws IOException {
        putResponse = "plain text result";
        assertEquals("plain text result", client.updateField("TP-1", "summary", "x"));
    }

    @Test
    public void testUpdateFieldEmptyValueClearsField() throws IOException {
        putResponse = "cleared";
        assertEquals("cleared", client.updateField("TP-1", "description", ""));
    }

    @Test
    public void testUpdateFieldCustomFieldMultiSelect() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_10100\",\"name\":\"Multi\",\"schema\":{\"custom\":\"com.atlassian.jira.plugin.system.customfieldtypes:multiselect\"}}]");
        putResponse = "";
        String result = client.updateField("TP-1", "customfield_10100", "alpha");
        assertEquals("Field 'customfield_10100' updated successfully on ticket TP-1", result);
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("customfield_10100"));
        assertTrue(body.contains("\"value\":\"alpha\""));
    }

    @Test
    public void testUpdateFieldCustomFieldCollectionValue() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_10100\",\"name\":\"Multi\",\"schema\":{\"type\":\"array\",\"items\":\"option\"}}]");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", Arrays.asList("a", "b"));
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("\"value\":\"a\""));
        assertTrue(body.contains("\"value\":\"b\""));
    }

    @Test
    public void testUpdateFieldCustomFieldJsonArrayValue() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_10100\",\"name\":\"Multi\",\"schema\":{\"type\":\"array\",\"items\":\"option\"}}]");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", new JSONArray().put(new JSONObject().put("id", "5")).put("plain"));
        String body = lastPutRequest().getBody();
        assertTrue(body.contains("\"id\":\"5\""));
        assertTrue(body.contains("\"value\":\"plain\""));
    }

    @Test
    public void testUpdateFieldCustomFieldFromCreateMeta() throws IOException {
        routeGet("api/latest/field",
                "{\"projects\":[{\"issuetypes\":[{\"fields\":{\"other_key\":{\"id\":\"customfield_10100\",\"name\":\"Multi\"," +
                        "\"schema\":{\"custom\":\"x:checkboxes\"}}}}]}]}");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", new JSONObject().put("name", "opt"));
        assertTrue(lastPutRequest().getBody().contains("\"name\":\"opt\""));
    }

    @Test
    public void testUpdateFieldCustomFieldNotMultiOption() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_10100\",\"name\":\"Text\",\"schema\":{\"type\":\"string\"}}]");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", "raw value");
        assertTrue(lastPutRequest().getBody().contains("raw value"));
    }

    @Test
    public void testUpdateFieldByNameMultipleWithFailure() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_1\",\"name\":\"Dependencies\",\"active\":true}," +
                        "{\"id\":\"customfield_2\",\"name\":\"Dependencies\",\"active\":true}," +
                        "{\"id\":\"customfield_3\",\"name\":\"Dependencies\",\"active\":false}]");
        routePut("issue/TP-1", "", "{\"errorMessages\":[\"boom\"]}");
        String result = client.updateField("TP-1", "Dependencies", "value");
        assertTrue(result.contains("Updated 1 of 2"));
        assertTrue(result.contains("1 failed"));
        assertTrue(result.contains("customfield_1"));
    }

    @Test
    public void testUpdateFieldByNameSingle() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_7\",\"name\":\"Dependencies\"}]");
        putResponse = "";
        String result = client.updateField("TP-1", "Dependencies", "value");
        assertEquals("Field 'Dependencies' updated successfully on ticket TP-1", result);
    }

    @Test
    public void testUpdateFieldByNameSingleFailure() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_7\",\"name\":\"Dependencies\"}]");
        putResponse = "{\"errorMessages\":[\"nope\"]}";
        String result = client.updateField("TP-1", "Dependencies", "value");
        assertEquals("Failed to update field 'Dependencies' on ticket TP-1", result);
    }

    @Test
    public void testUpdateFieldByNameNotFound() throws IOException {
        routeGet("api/latest/field", "not a json array");
        String result = client.updateField("TP-1", "No Such Field", "value");
        assertEquals("No fields found with name 'No Such Field'", result);
    }

    @Test
    public void testUpdateFieldByNameNotFoundUsesNegativeCache() throws IOException {
        routeGet("api/latest/field", "[]");
        assertEquals("No fields found with name 'Ghost'", client.updateField("TP-1", "Ghost", "value"));
        // second call hits the negative-resolution cache
        assertEquals("No fields found with name 'Ghost'", client.updateField("TP-1", "Ghost", "value"));
    }

    @Test
    public void testUpdateFieldAsAdf() throws IOException {
        putResponse = "";
        String result = client.updateFieldAsAdf("TP-1", "description", "plain text");
        assertEquals("Field 'description' updated as ADF successfully on ticket TP-1", result);
        GenericRequest request = lastPutRequest();
        assertTrue(request.url().contains("/rest/api/3/issue/TP-1"));
        assertTrue(request.getBody().contains("doc"));
    }

    @Test
    public void testUpdateFieldAsAdfWithResultBody() throws IOException {
        putResponse = "adf-result";
        assertEquals("adf-result", client.updateFieldAsAdf("TP-1", "description", "plain text"));
    }

    @Test
    public void testUpdateFieldAsAdfEmptyValue() throws IOException {
        putResponse = "cleared";
        assertEquals("cleared", client.updateFieldAsAdf("TP-1", "description", ""));
    }

    @Test
    public void testUpdateAllFieldsWithName() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_1\",\"name\":\"Dependencies\"},{\"id\":\"customfield_2\",\"name\":\"Dependencies\"}]");
        routePut("issue/TP-1", "", "{\"errorMessages\":[\"boom\"]}");
        String result = client.updateAllFieldsWithName("TP-1", "Dependencies", "value");
        assertTrue(result.contains("Summary: Updated 1 of 2"));
        assertTrue(result.contains("1 failed"));
    }

    @Test
    public void testUpdateAllFieldsWithNameNotFound() throws IOException {
        routeGet("api/latest/field", "[]");
        assertEquals("No fields found with name 'Nothing'", client.updateAllFieldsWithName("TP-1", "Nothing", "v"));
    }

    @Test
    public void testGetAllFieldsWithName() throws IOException {
        routeGet("api/latest/field", "[]");
        assertEquals("No fields found with name 'Nothing'", client.getAllFieldsWithName("TP", "Nothing"));
    }

    @Test
    public void testGetAllFieldsWithNameSingle() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_1\",\"name\":\"Dependencies\"}]");
        JSONObject result = new JSONObject(client.getAllFieldsWithName("TP", "Dependencies"));
        assertEquals(1, result.getInt("count"));
        assertFalse(result.has("warning"));
    }

    @Test
    public void testGetAllFieldsWithNameMultiple() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_1\",\"name\":\"Dependencies\"},{\"id\":\"customfield_2\",\"name\":\"Dependencies\"}]");
        JSONObject result = new JSONObject(client.getAllFieldsWithName("TP", "Dependencies"));
        assertEquals(2, result.getInt("count"));
        assertTrue(result.has("warning"));
    }

    // ─── subtasks ────────────────────────────────────────────────────────────

    @Test
    public void testPerformGettingSubtaskNoSubtaskTypes() throws Exception {
        // all endpoints return empty/unparseable responses -> no subtask types found
        assertTrue(client.performGettingSubtask("TP-1").isEmpty());
        // second call short-circuits on the cached "not supported" flag
        assertTrue(client.performGettingSubtask("TP-1").isEmpty());
    }

    @Test
    public void testPerformGettingSubtaskCloud() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("createmeta", "{\"projects\":[{\"issuetypes\":[{\"name\":\"Subtask\"}]}]}");
        doReturn(new ArrayList<>(List.of(ticket("TP-2"))))
                .when(client).searchAndPerform(anyString(), any(String[].class));

        List<Ticket> subtasks = client.performGettingSubtask("TP-1");
        assertEquals(1, subtasks.size());
        assertEquals("TP-2", subtasks.get(0).getKey());
    }

    @Test
    public void testPerformGettingSubtaskCloudSearchFails() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("createmeta", "{\"projects\":[{\"issuetypes\":[{\"name\":\"Subtask\"}]}]}");
        doThrow(new IOException("search failed")).when(client).searchAndPerform(anyString(), any(String[].class));

        assertTrue(client.performGettingSubtask("TP-1").isEmpty());
    }

    @Test
    public void testPerformGettingSubtaskServer() throws Exception {
        routeGet("issuetype", "[{\"name\":\"Sub-task\"}]");
        routeGet("subtask", "[{\"key\":\"TP-2\",\"fields\":{}}]");

        List<Ticket> subtasks = client.performGettingSubtask("TP-1");
        assertEquals(1, subtasks.size());
        assertEquals("TP-2", subtasks.get(0).getKey());
    }

    @Test
    public void testPerformGettingSubtaskServerJqlFallback() throws Exception {
        routeGet("issuetype", "[{\"name\":\"Sub-task\"}]");
        routeGet("subtask", THROW);
        doReturn(new ArrayList<>(List.of(ticket("TP-3"))))
                .when(client).searchAndPerform(anyString(), any(String[].class));

        List<Ticket> subtasks = client.performGettingSubtask("TP-1");
        assertEquals(1, subtasks.size());
        assertEquals("TP-3", subtasks.get(0).getKey());
    }

    @Test
    public void testPerformGettingSubtaskServerAllFail() throws Exception {
        routeGet("issuetype", "[{\"name\":\"Sub-task\"}]");
        routeGet("subtask", THROW);
        doThrow(new IOException("jql failed")).when(client).searchAndPerform(anyString(), any(String[].class));

        assertTrue(client.performGettingSubtask("TP-1").isEmpty());
    }

    // ─── search flows ────────────────────────────────────────────────────────

    @Test
    public void testSearchAndPerformBlankSingleField() throws Exception {
        routeGet("/search?", "{\"maxResults\":50,\"total\":0,\"issues\":[]}");
        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"  "});
        assertNotNull(tickets);
        assertTrue(tickets.isEmpty());
    }

    @Test
    public void testSearchAndPerformCloud() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{\"summary\":\"s\"}}],\"isLast\":true}");

        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertEquals(1, tickets.size());
        assertEquals("TP-1", tickets.get(0).getKey());
    }

    @Test
    public void testSearchAndPerformCloudNullResults() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        doReturn(null).when(client).searchByPage(anyString(), nullable(String.class), anyList());

        RestClient.RestClientException exception = assertThrows(RestClient.RestClientException.class,
                () -> client.searchAndPerform("project = TP", new String[]{"summary"}));
        assertTrue(exception.getMessage().contains("null results"));
    }

    @Test
    public void testSearchAndPerformCloudErrorMessages() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"errorMessages\":[\"bad jql\"]}");

        RestClient.RestClientException exception = assertThrows(RestClient.RestClientException.class,
                () -> client.searchAndPerform("project = TP", new String[]{"summary"}));
        assertTrue(exception.getMessage().contains("bad jql"));
    }

    @Test
    public void testSearchAndPerformCloudPagination() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("nextPageToken=tok", "{\"issues\":[{\"key\":\"TP-2\",\"fields\":{}}],\"isLast\":true}");
        routeGet("search/jql", "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}],\"isLast\":false,\"nextPageToken\":\"tok\"}");

        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertEquals(2, tickets.size());
        assertEquals("TP-1", tickets.get(0).getKey());
        assertEquals("TP-2", tickets.get(1).getKey());
    }

    @Test
    public void testSearchAndPerformCloudPaginationStopsWithoutToken() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}],\"isLast\":false}");

        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertEquals(1, tickets.size());
    }

    @Test
    public void testSearchAndPerformCloudPaginationNullPage() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        SearchResult firstPage = new SearchResult(
                "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}],\"isLast\":false,\"nextPageToken\":\"tok\"}");
        doReturn(firstPage, null).when(client).searchByPage(anyString(), nullable(String.class), anyList());

        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertEquals(1, tickets.size());
    }

    @Test
    public void testSearchAndPerformCloudPaginationError() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        SearchResult firstPage = new SearchResult(
                "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}],\"isLast\":false,\"nextPageToken\":\"tok\"}");
        doReturn(firstPage).doThrow(new IOException("page failed"))
                .when(client).searchByPage(anyString(), nullable(String.class), anyList());

        RestClient.RestClientException exception = assertThrows(RestClient.RestClientException.class,
                () -> client.searchAndPerform("project = TP", new String[]{"summary"}));
        assertTrue(exception.getMessage().contains("Pagination failed"));
    }

    @Test
    public void testSearchAndPerformCloudProgressPerformer() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{}},{\"key\":\"TP-2\",\"fields\":{}}],\"isLast\":true}");

        List<Integer> indices = new ArrayList<>();
        JiraClient.ProgressPerformer performer = new JiraClient.ProgressPerformer() {
            @Override
            public boolean perform(Ticket ticket, int index, int start, int end) {
                indices.add(index);
                return false;
            }
        };
        client.searchAndPerform(performer, "project = TP", new String[]{"summary"});
        assertEquals(Arrays.asList(0, 1), indices);
    }

    @Test
    public void testSearchAndPerformCloudPerformerBreak() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{}},{\"key\":\"TP-2\",\"fields\":{}}],\"isLast\":false,\"nextPageToken\":\"tok\"}");

        List<String> processed = new ArrayList<>();
        client.searchAndPerform((JiraClient.Performer<Ticket>) ticket -> {
            processed.add(ticket.getKey());
            return true;
        }, "project = TP", new String[]{"summary"});
        assertEquals(List.of("TP-1"), processed);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testProgressPerformerUnsupportedOperation() throws Exception {
        JiraClient.ProgressPerformer performer = new JiraClient.ProgressPerformer() {
            @Override
            public boolean perform(Ticket ticket, int index, int start, int end) {
                return false;
            }
        };
        performer.perform(ticket("TP-1"));
    }

    @Test
    public void testSearchAndPerformLegacy() throws Exception {
        routeGet("/search?", "{\"maxResults\":50,\"total\":2,\"issues\":[{\"key\":\"TP-1\",\"fields\":{}},{\"key\":\"TP-2\",\"fields\":{}}]}");

        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertEquals(2, tickets.size());
    }

    @Test
    public void testSearchAndPerformLegacyPagination() throws Exception {
        routeGet("startAt=100", "{\"maxResults\":50,\"total\":120,\"issues\":[{\"key\":\"TP-3\",\"fields\":{}}]}");
        routeGet("startAt=50", "{\"maxResults\":50,\"total\":120,\"issues\":[{\"key\":\"TP-2\",\"fields\":{}}]}");
        routeGet("startAt=0", "{\"maxResults\":50,\"total\":120,\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}]}");

        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertEquals(3, tickets.size());
    }

    @Test
    public void testSearchAndPerformLegacyErrorMessages() throws Exception {
        routeGet("/search?", "{\"errorMessages\":[\"bad\"]}");
        List<String> processed = new ArrayList<>();
        client.searchAndPerform((JiraClient.Performer<Ticket>) ticket -> {
            processed.add(ticket.getKey());
            return false;
        }, "project = TP", new String[]{"summary"});
        assertTrue(processed.isEmpty());
    }

    @Test
    public void testSearchAndPerformLegacyEmpty() throws Exception {
        routeGet("/search?", "{\"maxResults\":50,\"total\":0,\"issues\":[]}");
        List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});
        assertTrue(tickets.isEmpty());
    }

    @Test
    public void testSearchAndPerformLegacyProgressPerformer() throws Exception {
        routeGet("/search?", "{\"maxResults\":50,\"total\":1,\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}]}");

        List<int[]> calls = new ArrayList<>();
        JiraClient.ProgressPerformer performer = new JiraClient.ProgressPerformer() {
            @Override
            public boolean perform(Ticket ticket, int index, int start, int end) {
                calls.add(new int[]{index, start, end});
                return false;
            }
        };
        client.searchAndPerform(performer, "project = TP", new String[]{"summary"});
        assertEquals(1, calls.size());
        assertEquals(0, calls.get(0)[0]);
        assertEquals(50, calls.get(0)[1]);
        assertEquals(1, calls.get(0)[2]);
    }

    @Test
    public void testSearchWithExpandAndExpiration() throws Exception {
        client.setCacheExpirationForJQLInHours("project = TP", 2);
        routeGet("/search?", "{\"maxResults\":50,\"total\":1,\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}]}");

        SearchResult result = client.search("project = TP", 0, List.of("summary", "changelog"));
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertTrue(findExecuteRequest("/search?").url().contains("expand=changelog"));
    }

    @Test
    public void testSearchRetryOnJsonError() throws Exception {
        routeGet("/search?", "garbage response", "{\"maxResults\":50,\"total\":0,\"issues\":[]}");
        SearchResult result = client.search("project = TP", 0, List.of("summary"));
        assertNotNull(result);
        verify(client, atLeast(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testSearchFailsAfterRetry() {
        routeGet("/search?", "garbage", "still garbage");
        assertThrows(JSONException.class, () -> client.search("project = TP", 0, List.of("summary")));
    }

    @Test
    public void testSearchByPageTokenAndMaxResults() throws IOException {
        TestableJiraClient withMax = spy(new TestableJiraClient(BASE, "auth", 10));
        stubTransport(withMax);
        routeGet("search/jql", "{\"issues\":[{\"key\":\"TP-1\",\"fields\":{\"summary\":\"s\"}}],\"isLast\":true}");

        SearchResult result = withMax.searchByPage("project = TP", "tok", List.of("summary", "changelog"));
        assertNotNull(result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(withMax, atLeastOnce()).execute(captor.capture());
        String url = captor.getAllValues().stream()
                .map(GenericRequest::url)
                .filter(value -> value.contains("search/jql"))
                .findFirst()
                .orElse("");
        assertTrue(url.contains("nextPageToken=tok"));
        assertTrue(url.contains("maxResults=10"));
        assertTrue(url.contains("expand=changelog"));
    }

    @Test
    public void testSearchByPageRetryOnJsonError() throws IOException {
        routeGet("search/jql", "garbage", "{\"issues\":[],\"isLast\":true}");
        SearchResult result = client.searchByPage("project = TP", null, List.of("summary"));
        assertNotNull(result);
        verify(client, atLeast(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testSearchByPageFailsAfterRetry() {
        routeGet("search/jql", "garbage", "still garbage");
        assertThrows(JSONException.class, () -> client.searchByPage("project = TP", null, List.of("summary")));
    }

    @Test
    public void testResolveFieldNamesForSearchWithProjectContext() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[],\"isLast\":true}");
        client.setProjectContext("CTX");

        client.searchAndPerform("status = Open", new String[]{"key,summary"});
        String url = lastExecuteRequest().url();
        assertTrue(url.contains("fields="));
    }

    @Test
    public void testResolveFieldNamesForSearchKnownKeysFallback() throws Exception {
        routeGet("latest/project", "[{\"key\":\"K1\"}]");
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[],\"isLast\":true}");

        client.searchAndPerform("status = Open", new String[]{"summary"});
        assertTrue(lastExecuteRequest().url().contains("search/jql"));
    }

    @Test
    public void testExtractProjectKeyFromJqlKeywordFilter() throws Exception {
        routeGet("latest/project", "[{\"key\":\"WAS\"},{\"key\":\"K2\"}]");
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[],\"isLast\":true}");

        // "WAS" is a JQL keyword and must be skipped during project extraction
        client.searchAndPerform("status was Open", new String[]{"summary"});
        assertTrue(lastExecuteRequest().url().contains("search/jql"));
    }

    @Test
    public void testExtractProjectKeyFromJqlTicketPattern() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[],\"isLast\":true}");

        client.searchAndPerform("key = TP-123", new String[]{"summary"});
        assertTrue(lastExecuteRequest().url().contains("search/jql"));
    }

    // ─── fields, issue types, relationships ──────────────────────────────────

    @Test
    public void testGetFieldsFallbackToCreateMeta() throws IOException {
        routeGet("api/latest/field", THROW);
        routeGet("createmeta", "[{\"id\":\"customfield_1\",\"name\":\"X\"}]");
        assertEquals("[{\"id\":\"customfield_1\",\"name\":\"X\"}]", client.getFields("TP"));
    }

    @Test
    public void testGetFieldsBothEndpointsFail() {
        routeGet("api/latest/field", THROW);
        routeGet("createmeta", THROW);
        assertThrows(RuntimeException.class, () -> client.getFields("TP"));
    }

    @Test
    public void testGetFieldCustomCodeAndResolveFieldName() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_10001\",\"name\":\"Story Points\"}]");
        assertEquals("customfield_10001", client.getFieldCustomCode("TP", "story points"));
        assertNull(client.getFieldCustomCode("TP", "Missing"));
        assertEquals("customfield_10001", client.resolveFieldName("TP-1", "Story Points"));
        assertEquals("Missing", client.resolveFieldName("TP-1", "Missing"));
    }

    @Test
    public void testGetAllFieldCustomCodesFiltersInactive() throws IOException {
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_1\",\"name\":\"D\",\"active\":true}," +
                        "{\"id\":\"customfield_2\",\"name\":\"D\",\"active\":false}]");
        assertEquals(List.of("customfield_1"), client.getAllFieldCustomCodes("TP", "d"));
    }

    @Test
    public void testGetIssueTypesCloud() throws IOException {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("createmeta", "{\"projects\":[{\"issuetypes\":[{\"name\":\"Story\"},{\"name\":\"Subtask\"}]}]}");
        List<IssueType> types = client.getIssueTypes("TP");
        assertEquals(2, types.size());
        assertEquals("Story", types.get(0).getName());
    }

    @Test
    public void testGetIssueTypesCloudParseFailure() throws IOException {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("createmeta", "not a json object");
        assertTrue(client.getIssueTypes("TP").isEmpty());
    }

    @Test
    public void testGetIssueTypesCloudDetectionByUrl() throws IOException {
        TestableJiraClient cloudClient = spy(new TestableJiraClient("http://example.atlassian.net"));
        stubTransport(cloudClient);
        routeGet("serverInfo", THROW);
        routeGet("createmeta", "{\"projects\":[{\"issuetypes\":[{\"name\":\"Story\"}]}]}");
        List<IssueType> types = cloudClient.getIssueTypes("TP");
        assertEquals(1, types.size());
    }

    @Test
    public void testGetIssueTypesServerDirectArray() throws IOException {
        routeGet("createmeta", "[{\"name\":\"Bug\"}]");
        List<IssueType> types = client.getIssueTypes("TP");
        assertEquals(1, types.size());
        assertEquals("Bug", types.get(0).getName());
    }

    @Test
    public void testGetIssueTypesServerAlternativeEndpoint() throws IOException {
        routeGet("createmeta", "{}");
        routeGet("issuetype", "[{\"name\":\"Task\"}]");
        List<IssueType> types = client.getIssueTypes("TP");
        assertEquals(1, types.size());
        assertEquals("Task", types.get(0).getName());
    }

    @Test
    public void testGetIssueTypesServerProjectFallback() throws IOException {
        routeGet("createmeta", "{}");
        routeGet("issuetype", THROW);
        routeGet("latest/project/TP", "{\"issueTypes\":[{\"name\":\"Epic\"}]}");
        List<IssueType> types = client.getIssueTypes("TP");
        assertEquals(1, types.size());
        assertEquals("Epic", types.get(0).getName());
    }

    @Test
    public void testGetIssueTypesAllFail() throws IOException {
        routeGet("createmeta", THROW);
        routeGet("issuetype", THROW);
        routeGet("latest/project/TP", THROW);
        assertTrue(client.getIssueTypes("TP").isEmpty());
    }

    @Test
    public void testGetRelationshipsAndByName() throws IOException {
        routeGet("issueLinkType",
                "{\"issueLinkTypes\":[{\"name\":\"Blocks\",\"inward\":\"is blocked by\",\"outward\":\"blocks\"}]}");
        List<IssueType> relationships = client.getRelationships();
        assertEquals(1, relationships.size());

        Pair<String, IssueType> byName = client.getRelationshipByName("blocks");
        assertEquals("inward", byName.getFirst());
        Pair<String, IssueType> byInward = client.getRelationshipByName("is blocked by");
        assertEquals("inward", byInward.getFirst());
        Pair<String, IssueType> byOutward = client.getRelationshipByName("blocks ");
        assertNull(byOutward);
        Pair<String, IssueType> outward = client.getRelationshipByName("Blocks".toLowerCase());
        assertNotNull(outward);
        assertNull(client.getRelationshipByName("no such relationship"));
    }

    @Test
    public void testLinkIssueWithRelationshipInward() throws IOException {
        routeGet("issueLinkType",
                "{\"issueLinkTypes\":[{\"name\":\"Blocks\",\"inward\":\"is blocked by\",\"outward\":\"blocks\"}]}");
        postResponse = "";
        client.linkIssueWithRelationship("TP-1", "TP-2", "is blocked by");
        JSONObject body = new JSONObject(lastPostRequest().getBody());
        assertEquals("TP-1", body.getJSONObject("outwardIssue").getString("key"));
        assertEquals("TP-2", body.getJSONObject("inwardIssue").getString("key"));
        assertEquals("Blocks", body.getJSONObject("type").getString("name"));
    }

    @Test
    public void testLinkIssueWithRelationshipOutward() throws IOException {
        routeGet("issueLinkType",
                "{\"issueLinkTypes\":[{\"name\":\"Link\",\"inward\":\"is linked by\",\"outward\":\"links to\"}]}");
        postResponse = "";
        client.linkIssueWithRelationship("TP-1", "TP-2", "links to");
        JSONObject body = new JSONObject(lastPostRequest().getBody());
        assertEquals("TP-1", body.getJSONObject("inwardIssue").getString("key"));
        assertEquals("TP-2", body.getJSONObject("outwardIssue").getString("key"));
    }

    // ─── uri parsing ─────────────────────────────────────────────────────────

    @Test
    public void testParseUris() throws Exception {
        doReturn(new ArrayList<>(List.of(ticket("TP-3"))))
                .when(client).searchAndPerform(anyString(), any(String[].class));

        Set<String> keys = client.parseUris("Work on TP-1 and TP-2 please");
        assertTrue(keys.contains("TP-1"));
        assertTrue(keys.contains("TP-2"));
        assertTrue("child tickets must be added", keys.contains("TP-3"));
    }

    @Test
    public void testParseUrisWithRestClientException() throws Exception {
        doThrow(new RestClient.RestClientException("error", "'TP-2' does not exist", 400))
                .doReturn(new ArrayList<Ticket>())
                .when(client).searchAndPerform(anyString(), any(String[].class));

        Set<String> keys = client.parseUris("TP-1 TP-2");
        assertTrue(keys.contains("TP-1"));
        assertFalse(keys.contains("TP-2"));
    }

    @Test
    public void testParseUrisWithAttachmentUrls() throws Exception {
        doReturn(new ArrayList<Ticket>()).when(client).searchAndPerform(anyString(), any(String[].class));

        String object = "TP-1 {\"filename\":\"pic.png\",\"content\":\"https://jira.example.com/attachment/content/12345\"}";
        Set<String> keys = client.parseUris(object);
        assertTrue(keys.contains("TP-1"));
        assertTrue(keys.stream().anyMatch(key -> key.endsWith("/attachment/content/12345")));
    }

    @Test
    public void testUriToObjectFile() throws Exception {
        File tempFile = File.createTempFile("jira-attachment", ".png");
        tempFile.deleteOnExit();
        doReturn(tempFile).when(client).convertUrlToFile(anyString());

        Object result = client.uriToObject(BASE + "/secure/attachment/12345");
        assertSame(tempFile, result);
    }

    @Test
    public void testUriToObjectTicket() throws Exception {
        routeGet("issue/TP-1/comment", "{\"comments\":[{\"body\":\"c1\"}]}");
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{\"summary\":\"s\"}}");

        Object result = client.uriToObject("TP-1");
        assertTrue(result instanceof Ticket);
        assertTrue(((Ticket) result).getJSONObject().has("_comments"));
    }

    @Test
    public void testUriToObjectNull() throws Exception {
        routeGet("issue/ZZ-9", "{\"errorMessages\":[\"nope\"]}");
        assertNull(client.uriToObject("ZZ-9"));
    }

    // ─── custom field name transformation ────────────────────────────────────

    @Test
    public void testTransformResponse() throws IOException {
        client.setTransformCustomFieldsToNames(true);
        routeGet("api/latest/field",
                "[{\"id\":\"customfield_10001\",\"name\":\"Dup\"}," +
                        "{\"id\":\"customfield_10002\",\"name\":\"Dup\"}," +
                        "{\"id\":\"customfield_10003\",\"name\":\"Unique\"}]");
        routeGet("issue/TP-1",
                "{\"key\":\"TP-1\",\"fields\":{\"customfield_10001\":\"v1\",\"customfield_10003\":\"v2\"}}");

        Ticket result = client.performTicket("TP-1", null);
        JSONObject fields = result.getFields().getJSONObject();
        assertEquals("v1", fields.getString("Dup (customfield_10001)"));
        assertEquals("v2", fields.getString("Unique"));
    }

    @Test
    public void testTransformResponseNoProjectContext() throws IOException {
        client.setTransformCustomFieldsToNames(true);
        routeGet("issue/BROKEN", "{\"key\":\"BROKEN\",\"fields\":{\"customfield_10001\":\"v1\"}}");

        Ticket result = client.performTicket("BROKEN", null);
        // no project context -> response passes through untransformed
        assertEquals("v1", result.getFields().getJSONObject().getString("customfield_10001"));
    }

    // ─── execute / cache handling ────────────────────────────────────────────

    @Test
    public void testExecuteCacheHit() throws IOException {
        TestableJiraClient realClient = new TestableJiraClient();
        GenericRequest request = realClient.getTicket("TP-9");
        File cachedFile = realClient.getCachedFile(request);
        Files.writeString(cachedFile.toPath(), "cached-body");
        createdCacheFiles.add(cachedFile);

        assertEquals("cached-body", realClient.execute(request));
    }

    @Test
    public void testExecuteCacheDisabledAndZeroRetries() throws IOException {
        TestableJiraClient realClient = new TestableJiraClient();
        realClient.setCacheGetRequestsEnabled(false);
        realClient.setRetryPolicy(new RetryPolicy(0, 1L, 1L, 1.0, 0.0, null));

        GenericRequest request = realClient.getTicket("TP-9");
        File cachedFile = realClient.getCachedFile(request);
        Files.writeString(cachedFile.toPath(), "stale");
        createdCacheFiles.add(cachedFile);
        assertTrue(cachedFile.setLastModified(System.currentTimeMillis() - 10_000_000L));

        assertThrows(IOException.class, () -> realClient.execute(request));
        assertFalse("stale cache must be cleared", cachedFile.exists());
    }

    @Test
    public void testPostZeroRetries() throws IOException {
        TestableJiraClient realClient = new TestableJiraClient();
        realClient.setRetryPolicy(new RetryPolicy(0, 1L, 1L, 1.0, 0.0, null));

        IOException exception = assertThrows(IOException.class,
                () -> realClient.post(new GenericRequest(realClient, BASE + "/rest/api/latest/issue")));
        assertTrue(exception.getMessage().contains("Unexpected error in POST request execution"));
    }

    @Test
    public void testExecuteUrlZeroRetries() throws IOException {
        TestableJiraClient realClient = new TestableJiraClient();
        realClient.setRetryPolicy(new RetryPolicy(0, 1L, 1L, 1.0, 0.0, null));

        assertThrows(IOException.class, () -> realClient.execute(BASE + "/rest/api/latest/myself"));
    }

    @Test
    public void testClearCacheDeletesFile() throws IOException {
        GenericRequest request = client.getTicket("TP-1");
        File cachedFile = createCacheFile(request, "cached");
        assertTrue(cachedFile.exists());
        client.clearCache(request);
        assertFalse(cachedFile.exists());
    }

    @Test
    public void testGetCachedFileForContentUrl() throws IOException {
        routeGet("attachment", "{\"filename\":\"pic.png\"}");
        File file = client.getCachedFile("http://jira.example.com/secure/attachment/content/12345");
        assertTrue(file.getName().endsWith("content_12345_pic.png"));
    }

    @Test
    public void testGetCachedFilePlain() throws IOException {
        File file = client.getCachedFile("http://jira.example.com/secure/file.png");
        assertTrue(file.getName().endsWith(".png"));
    }

    @Test
    public void testConvertUrlToFileAlreadyCached() throws IOException {
        TestableJiraClient realClient = new TestableJiraClient();
        String href = "http://jira.example.com/secure/file.png";
        File cachedFile = realClient.getCachedFile(href);
        Files.writeString(cachedFile.toPath(), "image-bytes");
        createdCacheFiles.add(cachedFile);

        File result = realClient.convertUrlToFile(href);
        assertEquals(cachedFile.getAbsolutePath(), result.getAbsolutePath());
    }

    // ─── project structure APIs ──────────────────────────────────────────────

    private static JSONObject projectDetails(String id, String key, String style, JSONArray issueTypes) {
        return new JSONObject()
                .put("id", id)
                .put("key", key)
                .put("name", key + " Name")
                .put("style", style)
                .put("issueTypes", issueTypes);
    }

    @Test
    public void testGetProjectIssueTypeSchemeNextGen() throws IOException {
        doReturn(projectDetails("100", "NG", "next-gen",
                new JSONArray().put(new JSONObject().put("id", "1")).put(new JSONObject().put("id", "2"))).toString())
                .when(client).executeGet(contains("project/NG"));

        IssueTypeScheme scheme = client.getProjectIssueTypeScheme("NG");
        assertEquals("100", scheme.getId());
        assertEquals("NG Name Issue Type Scheme", scheme.getName());
    }

    @Test
    public void testGetProjectWorkflowSchemeNextGen() throws IOException {
        doReturn(projectDetails("100", "NG", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("/rest/api/latest/project/NG"));
        doReturn("[{\"id\":\"it1\",\"statuses\":[{\"id\":\"s1\",\"name\":\"Open\"}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/NG/statuses"));

        WorkflowScheme scheme = client.getProjectWorkflowScheme("NG");
        assertEquals("100", scheme.getId());
        assertEquals("NG Name Workflow", scheme.getName());
    }

    @Test
    public void testCreateProjectIssueTypeExists() throws IOException {
        doReturn(projectDetails("100", "TP", "next-gen",
                new JSONArray().put(new JSONObject().put("id", "1").put("name", "Story"))).toString())
                .when(client).executeGet(contains("project/TP"));

        JSONObject result = new JSONObject(client.createProjectIssueType("TP", "story", "standard", "desc"));
        assertEquals("exists", result.getString("status"));
    }

    @Test
    public void testCreateProjectIssueTypeCreated() throws IOException {
        doReturn(projectDetails("100", "TP", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/TP"));
        doReturn("{\"id\":\"42\"}").when(client).executePost(contains("/rest/api/3/issuetype"), anyString());

        JSONObject result = new JSONObject(client.createProjectIssueType("TP", "Test Case", null, null));
        assertEquals("created", result.getString("status"));
        assertEquals("42", result.getString("id"));
    }

    @Test
    public void testCreateProjectIssueTypeSubtask() throws IOException {
        doReturn(projectDetails("100", "TP", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/TP"));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        doReturn("{\"id\":\"43\"}").when(client).executePost(contains("/rest/api/3/issuetype"), bodyCaptor.capture());

        client.createProjectIssueType("TP", "Subtask", "subtask", "d");
        JSONObject body = new JSONObject(bodyCaptor.getValue());
        assertEquals("subtask", body.getString("type"));
        assertEquals("100", body.getJSONObject("scope").getJSONObject("project").getString("id"));
    }

    @Test
    public void testCreateProjectIssueTypeAlreadyExistsError() throws IOException {
        doReturn(projectDetails("100", "TP", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/TP"));
        doThrow(new IOException("A field with this name already exists"))
                .when(client).executePost(contains("/rest/api/3/issuetype"), anyString());

        JSONObject result = new JSONObject(client.createProjectIssueType("TP", "Story", "standard", ""));
        assertEquals("exists", result.getString("status"));
    }

    @Test
    public void testCreateProjectIssueTypeOtherError() throws IOException {
        doReturn(projectDetails("100", "TP", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/TP"));
        doThrow(new IOException("permission denied"))
                .when(client).executePost(contains("/rest/api/3/issuetype"), anyString());

        assertThrows(IOException.class, () -> client.createProjectIssueType("TP", "Story", "standard", ""));
    }

    @Test
    public void testCopyProjectStructureNextGen() throws IOException {
        doReturn(projectDetails("100", "SRC", "next-gen", new JSONArray()
                        .put(new JSONObject().put("name", "A").put("description", "d"))
                        .put(new JSONObject().put("name", "B").put("subtask", true))
                        .put(new JSONObject().put("name", "C"))).toString())
                .when(client).executeGet(contains("project/SRC"));
        doReturn(projectDetails("200", "TGT", "next-gen",
                new JSONArray().put(new JSONObject().put("name", "A"))).toString())
                .when(client).executeGet(contains("project/TGT"));
        doReturn("{\"id\":\"9\"}").doThrow(new IOException("weird failure"))
                .when(client).executePost(contains("/rest/api/3/issuetype"), anyString());

        JSONObject result = new JSONObject(client.copyProjectStructure("SRC", "TGT"));
        assertEquals("next-gen-create-scoped", result.getString("method"));
        JSONArray issueTypes = result.getJSONArray("issueTypes");
        assertEquals("exists", issueTypes.getJSONObject(0).getString("status"));
        assertEquals("created", issueTypes.getJSONObject(1).getString("status"));
        assertEquals("failed", issueTypes.getJSONObject(2).getString("status"));
    }

    @Test
    public void testCopyProjectStructureNextGenNoSourceTypes() throws IOException {
        doReturn(projectDetails("100", "SRC", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/SRC"));
        doReturn(projectDetails("200", "TGT", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/TGT"));

        assertThrows(IOException.class, () -> client.copyProjectStructure("SRC", "TGT"));
    }

    @Test
    public void testExecutePostErrorHandling() throws IOException {
        postResponse = "{\"errorMessages\":[\"bad request\"]}";
        IOException exception = assertThrows(IOException.class, () -> client.executePost(BASE + "/x", "{}"));
        assertEquals("bad request", exception.getMessage());

        postResponse = "{\"errors\":{\"f1\":\"e1\",\"f2\":\"e2\"}}";
        IOException joined = assertThrows(IOException.class, () -> client.executePost(BASE + "/x", "{}"));
        assertTrue(joined.getMessage().contains("e1"));
        assertTrue(joined.getMessage().contains("e2"));

        postResponse = "not json but contains \"errors\": yes";
        assertEquals("not json but contains \"errors\": yes", client.executePost(BASE + "/x", "{}"));

        postResponse = "{\"ok\":true}";
        assertEquals("{\"ok\":true}", client.executePost(BASE + "/x", "{}"));
    }

    @Test
    public void testExecuteDeleteErrorHandling() throws IOException {
        deleteResponse = "";
        assertEquals("", client.executeDelete(BASE + "/x"));

        deleteResponse = "{\"errorMessages\":[\"cannot delete\"]}";
        assertThrows(IOException.class, () -> client.executeDelete(BASE + "/x"));

        deleteResponse = "{\"errors\":{\"f\":\"e\"}}";
        assertThrows(IOException.class, () -> client.executeDelete(BASE + "/x"));

        deleteResponse = "plain \"errors\": text";
        assertEquals("plain \"errors\": text", client.executeDelete(BASE + "/x"));
    }

    @Test
    public void testCloneProjectNextGen() throws IOException {
        doReturn(projectDetails("100", "MYTUBE", "next-gen", new JSONArray()
                        .put(new JSONObject().put("id", "1").put("name", "Task"))
                        .put(new JSONObject().put("id", "2").put("name", "Custom")))
                .put("projectTypeKey", "software")
                .put("lead", new JSONObject().put("accountId", "acc-1")).toString())
                .when(client).executeGet(contains("project/MYTUBE"));
        doReturn(projectDetails("101", "TP2", "next-gen",
                new JSONArray().put(new JSONObject().put("id", "1").put("name", "Task"))).toString())
                .when(client).executeGet(contains("project/TP2"));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        doReturn("{\"id\":\"101\",\"key\":\"TP2\"}")
                .when(client).executePost(contains("/rest/api/3/project"), bodyCaptor.capture());

        JSONObject result = new JSONObject(client.cloneProject("MYTUBE", "TP2", null));
        assertEquals("TP2", result.getString("projectKey"));
        assertEquals("next-gen", result.getString("style"));
        assertTrue(result.getJSONArray("missingIssueTypes").toList().contains("Custom"));

        JSONObject body = new JSONObject(bodyCaptor.getValue());
        assertEquals("acc-1", body.getString("leadAccountId"));
        assertTrue(body.has("projectTemplateKey"));
    }

    @Test
    public void testCloneProjectClassic() throws IOException {
        doReturn(projectDetails("100", "SRC", "classic", new JSONArray()
                        .put(new JSONObject().put("id", "1").put("name", "Task")))
                .put("projectTypeKey", "software").toString())
                .when(client).executeGet(contains("project/SRC"));
        doReturn(projectDetails("101", "NEW", "classic",
                new JSONArray().put(new JSONObject().put("id", "1").put("name", "Task"))).toString())
                .when(client).executeGet(contains("project/NEW"));
        doReturn("{\"id\":\"101\",\"key\":\"NEW\"}")
                .when(client).executePost(contains("/rest/api/3/project"), anyString());

        JSONObject result = new JSONObject(client.cloneProject("SRC", "NEW", "Brand New"));
        assertEquals("Brand New", result.getString("projectName"));
        assertEquals("classic", result.getString("style"));
        assertTrue(result.getString("message").contains("all issue types match"));
    }

    @Test
    public void testGetProjectBoardConfig() throws IOException {
        doReturn("{\"values\":[{\"id\":1,\"name\":\"Board\",\"type\":\"scrum\"}]}")
                .when(client).executeGet(contains("/rest/agile/1.0/board?projectKeyOrId=TP"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"Open\",\"scope\":{\"type\":\"GLOBAL\"}}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/TP/statuses"));
        doReturn("[{\"id\":\"it\",\"statuses\":[{\"id\":\"s1\",\"name\":\"Open\",\"statusCategory\":{\"key\":\"indeterminate\"}}," +
                        "{\"id\":\"s2\",\"name\":\"Done\",\"statusCategory\":\"DONE\"}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/TP/statuses"));
        doReturn("{\"columnConfig\":{\"columns\":[{\"name\":\"Col1\",\"statuses\":[{\"id\":\"s1\"}]}]}}")
                .when(client).executeGet(contains("/configuration"));

        JSONObject result = new JSONObject(client.getProjectBoardConfig("TP"));
        assertEquals("1", result.getString("boardId"));
        assertEquals(1, result.getInt("totalStatuses"));
        assertEquals("IN_PROGRESS", result.getJSONArray("statuses").getJSONObject(0).getString("statusCategory"));
        assertEquals("Col1", result.getJSONArray("columns").getJSONObject(0).getString("name"));
    }

    @Test
    public void testGetProjectBoardConfigNoBoard() throws IOException {
        doReturn("{\"values\":[]}")
                .when(client).executeGet(contains("/rest/agile/1.0/board?projectKeyOrId=TP"));
        assertThrows(IOException.class, () -> client.getProjectBoardConfig("TP"));
    }

    @Test
    public void testGetProjectBoardConfigNoColumnConfig() throws IOException {
        doReturn("{\"values\":[{\"id\":1,\"name\":\"Board\",\"type\":\"simple\"}]}")
                .when(client).executeGet(contains("/rest/agile/1.0/board?projectKeyOrId=TP"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"Open\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/TP/statuses"));
        doThrow(new IOException("no v3")).when(client).executeGet(contains("/rest/api/3/project/TP/statuses"));
        doThrow(new IOException("no config")).when(client).executeGet(contains("/configuration"));

        JSONObject result = new JSONObject(client.getProjectBoardConfig("TP"));
        assertEquals(0, result.getJSONArray("columns").length());
        assertEquals(1, result.getInt("totalStatuses"));
        assertEquals("TODO", result.getJSONArray("statuses").getJSONObject(0).getString("statusCategory"));
    }

    private void stubWorkflowApis(String projectKey, String projectId) throws IOException {
        doReturn(projectDetails(projectId, projectKey, "next-gen",
                new JSONArray().put(new JSONObject().put("id", "it1"))).toString())
                .when(client).executeGet(contains("/rest/api/latest/project/" + projectKey + "\""));
        doReturn(projectDetails(projectId, projectKey, "next-gen",
                new JSONArray().put(new JSONObject().put("id", "it1"))).toString())
                .when(client).executeGet(endsWith("/rest/api/latest/project/" + projectKey));
        doReturn("[{\"id\":\"it1\",\"statuses\":[" +
                        "{\"id\":\"s1\",\"name\":\"To Do\",\"statusCategory\":{\"key\":\"new\"}}," +
                        "{\"id\":\"s2\",\"name\":\"Done\",\"statusCategory\":{\"key\":\"done\"}}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/" + projectKey + "/statuses"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"To Do\"},{\"id\":\"s2\",\"name\":\"Done\"}]}]",
                "[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"To Do\"},{\"id\":\"s2\",\"name\":\"Done\"}," +
                        "{\"id\":\"s3\",\"name\":\"In Progress\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/" + projectKey + "/statuses"));
        doReturn("[{\"id\":\"s3\"}]").when(client).executePost(contains("/rest/api/3/statuses"), anyString());
        doReturn("").when(client).executePost(contains("/rest/api/3/workflows/update"), anyString());
        doReturn("{\"workflows\":[{\"id\":\"wf1\",\"version\":{\"id\":\"v1\",\"versionNumber\":2}," +
                        "\"statuses\":[{\"statusReference\":\"s1\"},{\"statusReference\":\"s2\"}]}]}")
                .when(client).executePost(contains("/rest/api/3/workflows"), anyString());
    }

    @Test
    public void testSetupProjectWorkflow() throws IOException {
        stubWorkflowApis("TP", "100");

        JSONObject result = new JSONObject(client.setupProjectWorkflow("TP",
                "[{\"name\":\"To Do\",\"category\":\"TODO\"},{\"name\":\"In Progress\",\"category\":\"IN_PROGRESS\"}]"));
        assertEquals("success", result.getString("result"));
        assertEquals(2, result.getInt("statusesSynced"));
        assertEquals(1, result.getInt("statusesCreated"));
        assertEquals(1, result.getInt("removedStatuses"));
    }

    @Test
    public void testSetupProjectWorkflowDoubleEncoded() throws IOException {
        stubWorkflowApis("TP", "100");

        String doubleEncoded = "[\"{\\\"name\\\":\\\"To Do\\\",\\\"category\\\":\\\"TODO\\\"}\"]";
        JSONObject result = new JSONObject(client.setupProjectWorkflow("TP", doubleEncoded));
        assertEquals("success", result.getString("result"));
        assertEquals(0, result.getInt("statusesCreated"));
    }

    @Test
    public void testSyncProjectWorkflowCreatesStatuses() throws IOException {
        doReturn(projectDetails("200", "TGT", "next-gen",
                new JSONArray().put(new JSONObject().put("id", "it1"))).toString())
                .when(client).executeGet(endsWith("/rest/api/latest/project/TGT"));
        // source statuses: To Do + Done (one with string category)
        doReturn("[{\"id\":\"it\",\"statuses\":[" +
                        "{\"id\":\"s1\",\"name\":\"To Do\",\"statusCategory\":{\"key\":\"new\"}}," +
                        "{\"id\":\"s2\",\"name\":\"Done\",\"statusCategory\":\"DONE\"}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/SRC/statuses"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"To Do\"},{\"id\":\"s2\",\"name\":\"Done\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/SRC/statuses"));
        // target statuses: only To Do; second read picks up the created Done status
        doReturn("[{\"id\":\"it\",\"statuses\":[{\"id\":\"t1\",\"name\":\"To Do\",\"statusCategory\":{\"key\":\"new\"}}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/TGT/statuses"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"t1\",\"name\":\"To Do\"}]}]",
                "[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"t1\",\"name\":\"To Do\"},{\"id\":\"t2\",\"name\":\"Done\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/TGT/statuses"));
        doReturn("[{\"id\":\"t2\"}]").when(client).executePost(contains("/rest/api/3/statuses"), anyString());
        doReturn("").when(client).executePost(contains("/rest/api/3/workflows/update"), anyString());
        doReturn("{\"workflows\":[{\"id\":\"wf\",\"version\":{\"id\":\"v\",\"versionNumber\":1}," +
                        "\"statuses\":[{\"statusReference\":\"t1\"}]}]}")
                .when(client).executePost(contains("/rest/api/3/workflows"), anyString());

        JSONObject result = new JSONObject(client.syncProjectWorkflow("SRC", "TGT"));
        assertEquals("success", result.getString("result"));
        assertEquals(2, result.getInt("statusesSynced"));
        assertEquals(1, result.getJSONArray("statusesCreated").length());
    }

    // ─── attachments ─────────────────────────────────────────────────────────

    @Test(expected = IOException.class)
    public void testAttachFileToTicketMissingFile() throws IOException {
        client.attachFileToTicket("TP-1", "missing.png", "image/png", "/no/such/file-12345.png");
    }

    @Test
    public void testAttachFileToTicketSuccess() throws IOException {
        File tempFile = File.createTempFile("attach", ".txt");
        tempFile.deleteOnExit();
        doNothing().when(client).attachFileToTicket(anyString(), anyString(), nullable(String.class), any(File.class));

        JSONObject result = client.attachFileToTicket("TP-1", "doc.txt", "text/plain", tempFile.getAbsolutePath());
        assertEquals(JiraClient.SUCCESS, result.getString("status"));
        assertEquals("TP-1", result.getString("ticket"));
        assertEquals("doc.txt", result.getString("fileName"));
    }

    @Test
    public void testAttachFileToTicketExistingAttachmentSkips() throws IOException {
        File tempFile = File.createTempFile("attach", ".png");
        tempFile.deleteOnExit();
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{\"summary\":\"s\"," +
                "\"attachment\":[{\"filename\":\"a.png\",\"content\":\"http://x/1\"}]}}");

        client.attachFileToTicket("TP-1", "A.PNG", null, tempFile);
        // returned before any HTTP upload: only GET requests happened
        verify(client, never()).post(any(GenericRequest.class));
    }

    @Test
    public void testIsValidImageUrl() throws IOException {
        assertTrue(client.isValidImageUrl(BASE + "/secure/attachment/1/pic.png"));
        assertTrue(client.isValidImageUrl(BASE + "/pic.jpg"));
        assertFalse(client.isValidImageUrl("http://other.example.com/pic.png"));
    }

    // ─── second pass: remaining branches ─────────────────────────────────────

    @Test
    public void testSearchAndPerformLegacyPerformerBreak() throws Exception {
        routeGet("/search?", "{\"maxResults\":50,\"total\":100,\"issues\":[{\"key\":\"TP-1\",\"fields\":{}}]}");

        List<String> processed = new ArrayList<>();
        client.searchAndPerform((JiraClient.Performer<Ticket>) ticket -> {
            processed.add(ticket.getKey());
            return true;
        }, "project = TP", new String[]{"summary"});
        assertEquals(List.of("TP-1"), processed);
        verify(client, atLeastOnce()).execute(any(GenericRequest.class));
    }

    @Test
    public void testSearchByPageWithExpiration() throws IOException {
        client.setCacheExpirationForJQLInHours("project = TP", 1);
        routeGet("search/jql", "{\"issues\":[],\"isLast\":true}");
        SearchResult result = client.searchByPage("project = TP", null, List.of("summary"));
        assertNotNull(result);
    }

    @Test
    public void testPostCommentMarkdownTextType() throws IOException {
        TestableJiraClient markdownClient = spy(new TestableJiraClient() {
            @Override
            public TextType getTextType() {
                return TextType.MARKDOWN;
            }
        });
        stubTransport(markdownClient);
        markdownClient.postComment("TP-1", "plain comment");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(markdownClient, atLeastOnce()).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("plain comment"));
    }

    @Test
    public void testPostCommentIfNotExistsMarkdownTextType() throws IOException {
        TestableJiraClient markdownClient = spy(new TestableJiraClient() {
            @Override
            public TextType getTextType() {
                return TextType.MARKDOWN;
            }
        });
        stubTransport(markdownClient);
        routeGet("issue/TP-1/comment", "{\"comments\":[]}");
        markdownClient.postCommentIfNotExists("TP-1", "markdown comment");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(markdownClient, atLeastOnce()).post(captor.capture());
        assertTrue(captor.getValue().getBody().contains("markdown comment"));
    }

    @Test
    public void testCreateTicketInProjectMcpSuccess() throws IOException {
        postResponse = "{\"key\":\"TP-8\"}";
        String result = client.createTicketInProjectMcp("TP", "Task", "S", "D");
        assertTrue(result.contains("TP-8"));
    }

    @Test
    public void testCreateTicketInProjectNonReadableFieldError() throws IOException {
        postResponse = "{\"errors\":{\"components\":\"Component is invalid\"}}";
        IOException exception = assertThrows(IOException.class,
                () -> client.createTicketInProject("TP", "Bug", "S", "D", null));
        assertTrue(exception.getMessage().contains("components: Component is invalid"));
    }

    @Test
    public void testCreateTicketCustomFieldWhenFieldLoadingFails() throws IOException {
        routeGet("api/latest/field", THROW);
        routeGet("createmeta", THROW);
        postResponse = "{\"key\":\"TP-9\"}";
        String result = client.createTicketInProject("TP", "Story", "S", "D",
                fields -> fields.set("Story Points", 5));
        assertTrue(result.contains("TP-9"));
        // field could not be resolved -> original name is kept
        assertTrue(lastPostRequest().getBody().contains("Story Points"));
    }

    @Test
    public void testTransformResponseFieldMappingFails() throws IOException {
        client.setTransformCustomFieldsToNames(true);
        routeGet("api/latest/field", THROW);
        routeGet("createmeta", THROW);
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{}}");
        assertThrows(RuntimeException.class, () -> client.performTicket("TP-1", null));
    }

    @Test
    public void testTransformResponseEmptyMapping() throws IOException {
        client.setTransformCustomFieldsToNames(true);
        routeGet("api/latest/field", "[]");
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{\"customfield_1\":\"v\"}}");
        Ticket result = client.performTicket("TP-1", null);
        assertEquals("v", result.getFields().getJSONObject().getString("customfield_1"));
    }

    @Test
    public void testTransformResponseArrayBody() throws Exception {
        client.setTransformCustomFieldsToNames(true);
        routeGet("api/latest/field", "[{\"id\":\"customfield_10001\",\"name\":\"Unique\"}]");
        routeGet("/search?", "[{\"customfield_10001\":\"v\"}]",
                "{\"maxResults\":50,\"total\":0,\"issues\":[]}");
        SearchResult result = client.search("project = TP", 0, List.of("summary"));
        assertNotNull(result);
    }

    @Test
    public void testTransformResponseInvalidJsonBody() throws Exception {
        client.setTransformCustomFieldsToNames(true);
        routeGet("api/latest/field", "[{\"id\":\"customfield_10001\",\"name\":\"Unique\"}]");
        routeGet("/search?", "{broken", "{\"maxResults\":50,\"total\":0,\"issues\":[]}");
        SearchResult result = client.search("project = TP", 0, List.of("summary"));
        assertNotNull(result);
    }

    @Test
    public void testResolveFieldsSkipsMalformedEntries() throws IOException {
        routeGet("api/latest/field",
                "[{\"name\":\"No Id Here\"},{\"id\":\"customfield_10016\",\"name\":\"Story Points\"}]");
        postResponse = "{\"key\":\"TP-2\"}";
        client.createTicketInProject("TP", "Story", "S", "D", fields -> fields.set("Story Points", 5));
        JSONObject fields = new JSONObject(lastPostRequest().getBody()).getJSONObject("fields");
        assertEquals(5, fields.getInt("customfield_10016"));
    }

    @Test
    public void testResolveFieldViaSingleFieldFallback() throws IOException {
        // id does not start with customfield_ -> not in bulk mapping, but single-field lookup finds it
        routeGet("api/latest/field", "[{\"id\":\"cf_x\",\"name\":\"Weird\"}]");
        postResponse = "{\"key\":\"TP-3\"}";
        client.createTicketInProject("TP", "Story", "S", "D", fields -> fields.set("Weird", "w"));
        JSONObject fields = new JSONObject(lastPostRequest().getBody()).getJSONObject("fields");
        assertEquals("w", fields.getString("cf_x"));
    }

    @Test
    public void testResolveFieldNamesSystemFieldWithCustomSameName() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_9\",\"name\":\"Summary\"}]");
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{}}");
        client.performTicket("TP-1", new String[]{"summary"});
        String url = findExecuteRequest("issue/TP-1").url();
        assertTrue(url.contains("summary"));
        assertTrue(url.contains("customfield_9"));
    }

    @Test
    public void testResolveFieldNamesDuplicateResolutionSkipped() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_5\",\"name\":\"Dep\"}]");
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{}}");
        client.performTicket("TP-1", new String[]{"Dep", "DEP"});
        String url = findExecuteRequest("issue/TP-1").url();
        assertTrue(url.contains("customfield_5"));
        // the second identical resolution must be skipped, leaving a single occurrence
        assertEquals(url.indexOf("customfield_5"), url.lastIndexOf("customfield_5"));
    }

    @Test
    public void testResolveFieldNamesKeepsOriginalOnError() throws IOException {
        routeGet("api/latest/field", THROW);
        routeGet("createmeta", THROW);
        routeGet("issue/TP-1", "{\"key\":\"TP-1\",\"fields\":{}}");
        client.performTicket("TP-1", new String[]{"Some Custom"});
        assertTrue(findExecuteRequest("issue/TP-1").url().contains("Some"));
    }

    @Test
    public void testExtractProjectKeyFromJqlSkipsKeywordTicketPattern() throws Exception {
        routeGet("serverInfo", "{\"deploymentType\":\"Cloud\"}");
        routeGet("search/jql", "{\"issues\":[],\"isLast\":true}");
        // AND-1 is filtered out as a JQL keyword; TP-2 is used
        client.searchAndPerform("key = AND-1 or key = TP-2", new String[]{"summary"});
        assertTrue(lastExecuteRequest().url().contains("search/jql"));
    }

    @Test
    public void testCoerceFieldValueArrayTrailingContent() {
        String value = "[1,2][3]";
        assertSame(value, JiraClient.coerceFieldValue(value));
    }

    @Test
    public void testUpdateFieldInvalidJsonResultBody() throws IOException {
        putResponse = "{broken";
        assertEquals("{broken", client.updateField("TP-1", "summary", "x"));
    }

    @Test
    public void testUpdateFieldCustomFieldNotInDefinitions() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_1\",\"name\":\"Other\"}]");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", "raw");
        assertTrue(lastPutRequest().getBody().contains("raw"));
    }

    @Test
    public void testUpdateFieldCustomFieldEmptyFieldsResponse() throws IOException {
        routeGet("api/latest/field", "");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", "raw");
        assertTrue(lastPutRequest().getBody().contains("raw"));
    }

    @Test
    public void testUpdateFieldCustomFieldInvalidMetaResponse() throws IOException {
        routeGet("api/latest/field", "{broken");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", "raw");
        assertTrue(lastPutRequest().getBody().contains("raw"));
    }

    @Test
    public void testFindFieldDefinitionCreateMetaVariants() throws IOException {
        // different projects -> separate getFields cache entries -> sequential responses used
        routeGet("api/latest/field",
                "{}",
                "{\"projects\":[{}]}",
                "{\"projects\":[{\"issuetypes\":[{}]}]}",
                "{\"projects\":[{\"issuetypes\":[{\"fields\":{\"other\":{\"id\":\"customfield_9\"}}}]}]}");
        putResponse = "";
        client.updateField("TP-1", "customfield_10100", "v1");
        client.updateField("AB-1", "customfield_10100", "v2");
        client.updateField("CD-1", "customfield_10100", "v3");
        client.updateField("EF-1", "customfield_10100", "v4");
        verify(client, times(4)).put(any(GenericRequest.class));
    }

    @Test
    public void testUpdateFieldByNameResolvedViaInactiveField() throws IOException {
        routeGet("api/latest/field", "[{\"id\":\"customfield_1\",\"name\":\"Dep\",\"active\":false}]");
        putResponse = "";
        String result = client.updateField("TP-1", "Dep", "value");
        assertEquals("Field 'Dep' updated successfully on ticket TP-1", result);
    }

    @Test
    public void testMoveToStatusWithResolutionNotFound() throws IOException {
        routeGet("transitions", "{\"transitions\":[{\"id\":\"11\",\"name\":\"Done\",\"to\":{\"name\":\"Done\"}}]}");
        assertNull(client.moveToStatus("TP-1", "Missing", "Fixed"));
    }

    @Test
    public void testCloudJiraDetectionWithoutDeploymentType() throws IOException {
        routeGet("serverInfo", "{\"version\":\"9.4.0\"}");
        routeGet("createmeta", "[{\"name\":\"Bug\"}]");
        List<IssueType> types = client.getIssueTypes("TP");
        assertEquals(1, types.size());
    }

    @Test
    public void testExecuteGetAndPutProtected() throws IOException {
        assertEquals("{}", client.executeGet(BASE + "/rest/api/latest/thing"));
        client.executePut(BASE + "/rest/api/latest/thing", "{\"a\":1}");
        verify(client, atLeastOnce()).put(any(GenericRequest.class));
    }

    @Test
    public void testCopyProjectStructureTargetDetailsFail() throws IOException {
        doReturn(projectDetails("100", "SRC", "next-gen",
                new JSONArray().put(new JSONObject().put("name", "A"))).toString())
                .when(client).executeGet(contains("project/SRC"));
        doThrow(new IOException("target not found")).when(client).executeGet(contains("project/TGT"));

        assertThrows(IOException.class, () -> client.copyProjectStructure("SRC", "TGT"));
    }

    @Test
    public void testCopyProjectStructureAlreadyInUseError() throws IOException {
        doReturn(projectDetails("100", "SRC", "next-gen",
                new JSONArray().put(new JSONObject().put("name", "A"))).toString())
                .when(client).executeGet(contains("project/SRC"));
        doReturn(projectDetails("200", "TGT", "next-gen", new JSONArray()).toString())
                .when(client).executeGet(contains("project/TGT"));
        doThrow(new IOException("Name already in use"))
                .when(client).executePost(contains("/rest/api/3/issuetype"), anyString());

        JSONObject result = new JSONObject(client.copyProjectStructure("SRC", "TGT"));
        assertEquals("exists", result.getJSONArray("issueTypes").getJSONObject(0).getString("status"));
    }

    @Test
    public void testGetProjectBoardConfigDoneCategoryObject() throws IOException {
        doReturn("{\"values\":[{\"id\":1,\"name\":\"Board\",\"type\":\"scrum\"}]}")
                .when(client).executeGet(contains("/rest/agile/1.0/board?projectKeyOrId=TP"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"Done\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/TP/statuses"));
        doReturn("[{\"id\":\"it\",\"statuses\":[{\"id\":\"s1\",\"name\":\"Done\",\"statusCategory\":{\"key\":\"done\"}}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/TP/statuses"));
        doThrow(new IOException("no config")).when(client).executeGet(contains("/configuration"));

        JSONObject result = new JSONObject(client.getProjectBoardConfig("TP"));
        assertEquals("DONE", result.getJSONArray("statuses").getJSONObject(0).getString("statusCategory"));
    }

    @Test
    public void testSetupProjectWorkflowCreateStatusFails() throws IOException {
        stubWorkflowApis("TP", "100");
        doThrow(new IOException("cannot create")).when(client).executePost(contains("/rest/api/3/statuses"), anyString());

        JSONObject result = new JSONObject(client.setupProjectWorkflow("TP",
                "[{\"name\":\"To Do\",\"category\":\"TODO\"},{\"name\":\"In Progress\",\"category\":\"IN_PROGRESS\"}]"));
        assertEquals("success", result.getString("result"));
        assertEquals(0, result.getInt("statusesCreated"));
    }

    @Test
    public void testSetupProjectWorkflowNoWorkflowFound() throws IOException {
        stubWorkflowApis("TP", "100");
        doReturn("{\"workflows\":[]}").when(client).executePost(contains("/rest/api/3/workflows"), anyString());

        assertThrows(IOException.class, () -> client.setupProjectWorkflow("TP",
                "[{\"name\":\"To Do\",\"category\":\"TODO\"}]"));
    }

    @Test
    public void testSyncProjectWorkflowCreateStatusFails() throws IOException {
        doReturn(projectDetails("200", "TGT", "next-gen",
                new JSONArray().put(new JSONObject().put("id", "it1"))).toString())
                .when(client).executeGet(endsWith("/rest/api/latest/project/TGT"));
        doReturn("[{\"id\":\"it\",\"statuses\":[" +
                        "{\"id\":\"s1\",\"name\":\"To Do\",\"statusCategory\":{\"key\":\"new\"}}," +
                        "{\"id\":\"s2\",\"name\":\"Done\",\"statusCategory\":{\"key\":\"done\"}}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/SRC/statuses"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"s1\",\"name\":\"To Do\"},{\"id\":\"s2\",\"name\":\"Done\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/SRC/statuses"));
        doReturn("[{\"id\":\"it\",\"statuses\":[{\"id\":\"t1\",\"name\":\"To Do\",\"statusCategory\":{\"key\":\"new\"}}]}]")
                .when(client).executeGet(contains("/rest/api/3/project/TGT/statuses"));
        doReturn("[{\"name\":\"Bug\",\"statuses\":[{\"id\":\"t1\",\"name\":\"To Do\"}]}]")
                .when(client).executeGet(contains("/rest/api/latest/project/TGT/statuses"));
        doThrow(new IOException("cannot create")).when(client).executePost(contains("/rest/api/3/statuses"), anyString());
        doReturn("").when(client).executePost(contains("/rest/api/3/workflows/update"), anyString());
        doReturn("{\"workflows\":[{\"id\":\"wf\",\"version\":{\"id\":\"v\",\"versionNumber\":1}," +
                        "\"statuses\":[{\"statusReference\":\"t1\"}]}]}")
                .when(client).executePost(contains("/rest/api/3/workflows"), anyString());

        JSONObject result = new JSONObject(client.syncProjectWorkflow("SRC", "TGT"));
        assertEquals("success", result.getString("result"));
        // "Done" could not be created -> skipped from the synced set
        assertEquals(1, result.getInt("statusesSynced"));
        assertEquals(0, result.getJSONArray("statusesCreated").length());
    }
}
