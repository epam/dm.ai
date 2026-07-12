// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.testrail;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.testrail.model.TestCase;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.*;

/**
 * Additional unit tests for {@link TestRailClient} aimed at covering the HTTP-backed
 * API surface (projects, suites, cases, labels, case types, create/update/delete flows,
 * pagination and path normalization). The HTTP layer is stubbed by overriding
 * {@code execute(GenericRequest)} / {@code post(GenericRequest)} - no real network
 * calls are made.
 */
public class TestRailClientCoverageTest {

    private static final String BASE_PATH = "https://example.testrail.com";
    private static final String USERNAME = "test@example.com";
    private static final String API_KEY = "test_api_key";

    private static class RecordedRequest {
        final String method;
        final String url;
        final String body;

        RecordedRequest(String method, String url, String body) {
            this.method = method;
            this.url = url;
            this.body = body;
        }
    }

    private static class StubTestRailClient extends TestRailClient {
        private final Queue<String> queuedGetResponses = new ArrayDeque<>();
        private final Queue<String> queuedPostResponses = new ArrayDeque<>();
        private final List<RecordedRequest> requests = new ArrayList<>();

        StubTestRailClient(String basePath, String username, String apiKey) throws IOException {
            super(basePath, username, apiKey);
        }

        void queueGet(String response) {
            queuedGetResponses.add(response);
        }

        void queuePost(String response) {
            queuedPostResponses.add(response);
        }

        List<RecordedRequest> getRequests() {
            return requests;
        }

        List<String> getUrls() {
            List<String> urls = new ArrayList<>();
            for (RecordedRequest request : requests) {
                urls.add(request.url);
            }
            return urls;
        }

        RecordedRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }

        @Override
        public String execute(GenericRequest genericRequest) throws IOException {
            requests.add(new RecordedRequest("GET", genericRequest.url(), genericRequest.getBody()));
            if (queuedGetResponses.isEmpty()) {
                throw new IOException("No queued GET response for " + genericRequest.url());
            }
            return queuedGetResponses.remove();
        }

        @Override
        public String post(GenericRequest genericRequest) throws IOException {
            requests.add(new RecordedRequest("POST", genericRequest.url(), genericRequest.getBody()));
            if (queuedPostResponses.isEmpty()) {
                throw new IOException("No queued POST response for " + genericRequest.url());
            }
            return queuedPostResponses.remove();
        }
    }

    @Before
    public void setUp() {
        PropertyReader.clearOverrides();
    }

    @After
    public void tearDown() {
        PropertyReader.clearOverrides();
    }

    // ========== helpers ==========

    private StubTestRailClient newStubClient() throws IOException {
        return new StubTestRailClient(BASE_PATH, USERNAME, API_KEY);
    }

    private static JSONObject project(int id, String name) {
        return new JSONObject().put("id", id).put("name", name);
    }

    private static String projectsPage(Object nextLink, JSONObject... projects) {
        return pagedPage("projects", nextLink, projects);
    }

    private static String suitesPage(Object nextLink, JSONObject... suites) {
        return pagedPage("suites", nextLink, suites);
    }

    private static String casesPage(Object nextLink, JSONObject... cases) {
        return pagedPage("cases", nextLink, cases);
    }

    private static String labelsPage(Object nextLink, JSONObject... labels) {
        return pagedPage("labels", nextLink, labels);
    }

    private static String pagedPage(String key, Object nextLink, JSONObject... items) {
        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        for (JSONObject item : items) {
            array.put(item);
        }
        response.put("offset", 0);
        response.put("limit", 250);
        response.put("size", array.length());
        response.put(key, array);
        response.put("_links", new JSONObject()
                .put("next", nextLink == null ? JSONObject.NULL : nextLink)
                .put("prev", JSONObject.NULL));
        return response.toString();
    }

    private static String sectionsPage(JSONObject... sections) {
        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        for (JSONObject section : sections) {
            array.put(section);
        }
        response.put("sections", array);
        return response.toString();
    }

    private static String apiUrl(String apiPath) {
        return BASE_PATH + "/index.php?/api/v2" + apiPath;
    }

    // ========== sign / path / constructors ==========

    @Test
    public void testSignAddsAuthAndContentTypeHeaders() throws IOException {
        StubTestRailClient client = newStubClient();

        Request request = client.sign(new Request.Builder().url("https://example.testrail.com/index.php")).build();

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((USERNAME + ":" + API_KEY).getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuth, request.header("Authorization"));
        assertEquals("application/json", request.header("Content-Type"));
    }

    @Test
    public void testPathStripsTrailingSlashFromBasePath() throws IOException {
        StubTestRailClient client = new StubTestRailClient(BASE_PATH + "/", USERNAME, API_KEY);

        assertEquals(BASE_PATH + "/index.php?/api/v2/get_case/1", client.path("/get_case/1"));
        assertEquals(BASE_PATH + "/", client.getBasePath());
    }

    @Test
    public void testDefaultConstructorUsesStaticConfiguration() throws Exception {
        // The no-arg constructor reads the static TESTRAIL_* configuration; in the test
        // environment these are typically unset, so only exercise it when configured.
        if (TestRailClient.BASE_PATH == null || TestRailClient.BASE_PATH.isEmpty()) {
            return;
        }
        assertNotNull(new TestRailClient());
    }

    // ========== testConnection ==========

    @Test
    public void testConnectionSuccess() throws IOException {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject()
                .put("projects", new JSONArray().put(project(1, "P1")).put(project(2, "P2")))
                .toString());

        var result = client.testConnection();

        assertEquals(true, result.get("success"));
        assertEquals(2, result.get("projects"));
    }

    @Test
    public void testConnectionUnexpectedFormat() throws IOException {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject().put("unexpected", true).toString());

        var result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("Unexpected response format from TestRail API", result.get("message"));
    }

    @Test
    public void testConnectionEmptyResponse() throws IOException {
        StubTestRailClient client = newStubClient();
        client.queueGet("");

        var result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("Empty response from TestRail API", result.get("message"));
    }

    @Test
    public void testConnectionFailure() throws IOException {
        StubTestRailClient client = newStubClient();
        // no queued response -> stub throws IOException

        var result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertTrue(String.valueOf(result.get("message")).startsWith("TestRail API connection failed"));
        assertEquals("IOException", result.get("error"));
    }

    // ========== pagination & next-link normalization ==========

    @Test
    public void testGetProjectsFollowsAbsoluteNextLink() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage("https://example.testrail.com/index.php?/api/v2/get_projects&limit=250&offset=250",
                project(1, "A")));
        client.queueGet(projectsPage(null, project(2, "B")));

        JSONObject response = new JSONObject(client.getProjects());

        assertEquals(2, response.getJSONArray("projects").length());
        assertEquals(List.of(
                apiUrl("/get_projects&limit=250&offset=0"),
                apiUrl("/get_projects&limit=250&offset=250")
        ), client.getUrls());
    }

    @Test
    public void testGetProjectsFollowsIndexPhpPrefixedNextLink() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage("/index.php?/api/v2/get_projects&limit=250&offset=250", project(1, "A")));
        client.queueGet(projectsPage(null, project(2, "B")));

        new JSONObject(client.getProjects());

        assertEquals(List.of(
                apiUrl("/get_projects&limit=250&offset=0"),
                apiUrl("/get_projects&limit=250&offset=250")
        ), client.getUrls());
    }

    @Test
    public void testGetProjectsAddsLeadingSlashToBareNextLink() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage("get_projects&limit=250&offset=250", project(1, "A")));
        client.queueGet(projectsPage(null, project(2, "B")));

        new JSONObject(client.getProjects());

        assertEquals(List.of(
                apiUrl("/get_projects&limit=250&offset=0"),
                apiUrl("/get_projects&limit=250&offset=250")
        ), client.getUrls());
    }

    @Test
    public void testGetProjectsStopsOnBlankNextLink() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage("   ", project(1, "A")));

        JSONObject response = new JSONObject(client.getProjects());

        assertEquals(1, response.getJSONArray("projects").length());
        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testGetProjectsStopsOnLiteralNullNextLink() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage("null", project(1, "A")));

        new JSONObject(client.getProjects());

        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testGetProjectsBreaksOnEmptyPage() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage("/api/v2/get_projects&limit=250&offset=250"));

        JSONObject response = new JSONObject(client.getProjects());

        assertEquals(0, response.getJSONArray("projects").length());
        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testPaginationWithoutLinksContinuesWhilePageIsFull() throws Exception {
        StubTestRailClient client = newStubClient();
        // First page: no _links, but item count equals the limit -> next offset page is requested.
        JSONObject fullPage = new JSONObject();
        JSONArray cases = new JSONArray();
        for (int i = 0; i < 250; i++) {
            cases.put(new JSONObject().put("id", i).put("title", "TC" + i));
        }
        fullPage.put("offset", 0);
        fullPage.put("cases", cases);
        client.queueGet(fullPage.toString());
        // Second page: no _links, fewer than limit items -> stops.
        JSONObject lastPage = new JSONObject();
        lastPage.put("offset", 250);
        lastPage.put("cases", new JSONArray().put(new JSONObject().put("id", 300).put("title", "TC300")));
        client.queueGet(lastPage.toString());

        List<TestCase> result = client.getAllCasesByProjectId(7);

        assertEquals(251, result.size());
        assertEquals(List.of(
                apiUrl("/get_cases/7&limit=250&offset=0"),
                apiUrl("/get_cases/7&limit=250&offset=250")
        ), client.getUrls());
    }

    // ========== suites ==========

    @Test
    public void testGetSuitesByProjectIdPaginationAndEmptyBreak() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(suitesPage("/api/v2/get_suites/5&limit=250&offset=250",
                new JSONObject().put("id", 1).put("name", "Master")));
        client.queueGet(suitesPage(null));

        JSONArray suites = new JSONArray(client.getSuitesByProjectId(5));

        assertEquals(1, suites.length());
        assertEquals("Master", suites.getJSONObject(0).getString("name"));
        assertEquals(List.of(
                apiUrl("/get_suites/5&limit=250&offset=0"),
                apiUrl("/get_suites/5&limit=250&offset=250")
        ), client.getUrls());
    }

    @Test
    public void testGetSuitesResolvesProjectAndCachesId() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(suitesPage(null, new JSONObject().put("id", 1).put("name", "Master")));
        client.queueGet(suitesPage(null, new JSONObject().put("id", 2).put("name", "Second")));

        client.getSuites("Project A");
        client.getSuites("Project A"); // second call must reuse the cached project ID

        assertEquals(List.of(
                apiUrl("/get_projects&limit=250&offset=0"),
                apiUrl("/get_suites/5&limit=250&offset=0"),
                apiUrl("/get_suites/5&limit=250&offset=0")
        ), client.getUrls());
    }

    @Test
    public void testGetSuitesThrowsWhenProjectNotFound() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));

        IOException exception = assertThrows(IOException.class, () -> client.getSuites("Missing"));
        assertTrue(exception.getMessage().contains("Project not found: Missing"));
    }

    // ========== cases retrieval ==========

    @Test
    public void testGetCaseWithCAndWithoutCPrefix() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject().put("id", 123).put("title", "Case A").toString());
        client.queueGet(new JSONObject().put("id", 456).put("title", "Case B").toString());

        TestCase withPrefix = client.getCase("C123", null);
        TestCase withoutPrefix = client.performTicket("456", null);

        assertEquals("C123", withPrefix.getKey());
        assertEquals("C456", withoutPrefix.getKey());
        assertEquals(List.of(
                apiUrl("/get_case/123"),
                apiUrl("/get_case/456")
        ), client.getUrls());
    }

    @Test
    public void testGetCaseMarkdownFormatConvertsHtmlFields() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject()
                .put("id", 10)
                .put("title", "Case")
                .put("custom_preconds", "<p>Precondition text</p>")
                .toString());

        TestCase testCase = client.getCase("10", "markdown");

        String converted = testCase.getJSONObject().getString("custom_preconds");
        assertFalse(converted.contains("<p>"));
        assertTrue(converted.contains("Precondition text"));
    }

    @Test
    public void testGetCaseMarkdownFormatConvertsStepsSeparatedSubfields() throws Exception {
        StubTestRailClient client = newStubClient();
        JSONArray steps = new JSONArray()
                .put(new JSONObject()
                        .put("content", "<p>Step one</p>")
                        .put("expected", "<p>Expected one</p>")
                        .put("additional_info", "<p>Info one</p>"))
                .put(new JSONObject().put("content", "plain text, no html"));
        client.queueGet(new JSONObject()
                .put("id", 11)
                .put("title", "Case")
                .put("custom_steps_separated", steps)
                .toString());

        TestCase testCase = client.getCase("11", "markdown");

        JSONArray convertedSteps = testCase.getJSONObject().getJSONArray("custom_steps_separated");
        assertFalse(convertedSteps.getJSONObject(0).getString("content").contains("<p>"));
        assertFalse(convertedSteps.getJSONObject(0).getString("expected").contains("<p>"));
        assertFalse(convertedSteps.getJSONObject(0).getString("additional_info").contains("<p>"));
        assertEquals("plain text, no html", convertedSteps.getJSONObject(1).getString("content"));
    }

    @Test
    public void testSearchCasesBuildsFiltersIntoPath() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(9, "Project X")));
        client.queueGet(casesPage(null, new JSONObject().put("id", 1).put("title", "TC")));

        List<TestCase> result = client.searchCases("Project X", "3", "10", null);

        assertEquals(1, result.size());
        assertEquals(apiUrl("/get_cases/9&suite_id=3&section_id=10&limit=250&offset=0"),
                client.lastRequest().url);
    }

    @Test
    public void testGetCasesByLabelResolvesLabelAndFilters() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(9, "Project X")));
        client.queueGet(labelsPage(null,
                new JSONObject().put("id", 12).put("title", "ai_generated")));
        client.queueGet(casesPage(null, new JSONObject().put("id", 5).put("title", "Labeled")));

        List<TestCase> result = client.getCasesByLabel("Project X", "ai_generated");

        assertEquals(1, result.size());
        assertEquals(apiUrl("/get_cases/9&label_ids=12&limit=250&offset=0"), client.lastRequest().url);
    }

    @Test
    public void testGetCasesByLabelByProjectId() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(labelsPage(null,
                new JSONObject().put("id", 7).put("title", "Login")));
        client.queueGet(casesPage(null, new JSONObject().put("id", 6).put("title", "Labeled")));

        List<TestCase> result = client.getCasesByLabelByProjectId(9, "Login");

        assertEquals(1, result.size());
        assertEquals(apiUrl("/get_cases/9&label_ids=7&limit=250&offset=0"), client.lastRequest().url);
    }

    @Test
    public void testGetCasesByRefsByProjectIdEncodesRefs() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(casesPage(null, new JSONObject().put("id", 77).put("title", "Linked")));

        List<TestCase> result = client.getCasesByRefsByProjectId("PROJ-1,PROJ 2", 4);

        assertEquals(1, result.size());
        assertEquals(apiUrl("/get_cases/4&refs=PROJ-1%2CPROJ+2&limit=250&offset=0"),
                client.lastRequest().url);
    }

    @Test
    public void testGetCasesByRefsPaginates() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(3, "Proj")));
        client.queueGet(casesPage("/api/v2/get_cases/3&refs=PROJ-1&limit=250&offset=250",
                new JSONObject().put("id", 1).put("title", "A")));
        client.queueGet(casesPage(null, new JSONObject().put("id", 2).put("title", "B")));

        List<TestCase> result = client.getCasesByRefs("PROJ-1", "Proj");

        assertEquals(2, result.size());
        assertEquals(List.of(
                apiUrl("/get_projects&limit=250&offset=0"),
                apiUrl("/get_cases/3&refs=PROJ-1&limit=250&offset=0"),
                apiUrl("/get_cases/3&refs=PROJ-1&limit=250&offset=250")
        ), client.getUrls());
    }

    // ========== searchAndPerform ==========

    @Test
    public void testSearchAndPerformCollectsAllCases() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(casesPage(null,
                new JSONObject().put("id", 1).put("title", "A"),
                new JSONObject().put("id", 2).put("title", "B")));

        List<TestCase> result = client.searchAndPerform("project_id=5", null);

        assertEquals(2, result.size());
        assertEquals(apiUrl("/get_cases&project_id=5&limit=250&offset=0"), client.lastRequest().url);
    }

    @Test
    public void testSearchAndPerformStopsWhenPerformerReturnsFalse() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(casesPage("/api/v2/get_cases&project_id=5&limit=250&offset=250",
                new JSONObject().put("id", 1).put("title", "A"),
                new JSONObject().put("id", 2).put("title", "B")));

        List<TestCase> performed = new ArrayList<>();
        client.searchAndPerform(ticket -> {
            performed.add(ticket);
            return false; // stop after the first ticket
        }, "project_id=5", null);

        assertEquals(1, performed.size());
        assertEquals(1, client.getRequests().size()); // no second page requested
    }

    // ========== create / update / delete ==========

    @Test
    public void testCreateCaseAppliesPriorityAndRefs() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(sectionsPage(new JSONObject().put("id", 42).put("name", "Main")));
        client.queuePost(new JSONObject().put("id", 100).toString());

        String response = client.createCase("Project A", "My title", "Some description", "3", "PROJ-9");

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals("My title", body.getString("title"));
        assertEquals("Some description", body.getString("custom_preconds"));
        assertEquals(3, body.getInt("priority_id"));
        assertEquals("PROJ-9", body.getString("refs"));
        assertEquals(apiUrl("/add_case/42"), client.lastRequest().url);
        assertEquals("100", new JSONObject(response).get("id").toString());
    }

    @Test
    public void testCreateCaseDefaultsPriorityOnInvalidValue() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 101).toString());

        client.createCase("Project A", "Title", null, "not-a-number", null);

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals(2, body.getInt("priority_id"));
        assertFalse(body.has("refs"));
        assertFalse(body.has("custom_preconds"));
    }

    @Test
    public void testCreateTicketInProjectWithNullDescriptionAndInitializer() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 102).toString());

        client.createTicketInProject("Project A", "Test Case", "Summary only", null, null);

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals("Summary only", body.getString("title"));
        assertFalse(body.has("custom_preconds"));
    }

    @Test
    public void testCreateCaseDetailedBuildsFullBody() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 200).toString());

        String response = client.createCaseDetailedByProjectId(5, "Detailed title",
                "Preconditions",
                "| A | B |\n|---|---|\n| 1 | 2 |",
                "Expected result",
                "4", "6", "PROJ-1", "7,8");

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals("Detailed title", body.getString("title"));
        assertEquals("Preconditions", body.getString("custom_preconds"));
        assertTrue(body.getString("custom_steps").contains("|||:A|:B"));
        assertEquals("Expected result", body.getString("custom_expected"));
        assertEquals(4, body.getInt("priority_id"));
        assertEquals(6, body.getInt("type_id"));
        assertEquals("PROJ-1", body.getString("refs"));
        assertEquals(List.of(7, 8), body.getJSONArray("labels").toList());
        assertEquals("200", new JSONObject(response).get("id").toString());
    }

    @Test
    public void testCreateCaseDetailedSkipsInvalidTypeAndLabelIds() throws Exception {
        StubTestRailClient client = newStubClient();
        client.setLogEnabled(true); // exercise the log() path for invalid values
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 201).toString());

        client.createCaseDetailedByProjectId(5, "Title", null, null, null,
                "bad-priority", "bad-type", null, "7,bad, ");

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals(2, body.getInt("priority_id")); // default on parse failure
        assertFalse(body.has("type_id"));            // invalid type is skipped
        assertFalse(body.has("custom_preconds"));
        assertEquals(List.of(7), body.getJSONArray("labels").toList()); // only valid label kept
    }

    @Test
    public void testCreateCaseStepsBuildsStepsTemplateBody() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 300).toString());

        String stepsJson = new JSONArray()
                .put(new JSONObject()
                        .put("content", "Open page\n\n| A | B |\n|---|---|\n| 1 | 2 |")
                        .put("expected", "Page opens")
                        .put("additional_info", "info")
                        .put("refs", "PROJ-1"))
                .toString();

        client.createCaseStepsByProjectId(5, "Steps title", "Precond text",
                stepsJson, null, null, null, null);

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals("Steps title", body.getString("title"));
        assertEquals(2, body.getInt("template_id"));
        assertTrue(body.getString("custom_preconds").contains("<p>Precond text</p>"));
        assertEquals(2, body.getInt("priority_id")); // default

        JSONObject step = body.getJSONArray("custom_steps_separated").getJSONObject(0);
        assertTrue(step.getString("content").contains("<table>"));
        assertEquals("<p>Page opens</p>", step.getString("expected"));
        assertEquals("info", step.getString("additional_info"));
        assertEquals("PROJ-1", step.getString("refs"));
        assertEquals(1, step.getInt("markdown_editor_id"));
    }

    @Test
    public void testCreateCaseStepsRejectsInvalidStepsJson() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));

        IOException exception = assertThrows(IOException.class, () ->
                client.createCaseStepsByProjectId(5, "Title", null, "not json", null, null, null, null));

        assertTrue(exception.getMessage().contains("Invalid steps_json format"));
    }

    @Test
    public void testCreateCaseStepsViaProjectName() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 301).toString());

        client.createCaseSteps("Project A", "Title", null,
                "[{\"content\":\"c\",\"expected\":\"e\"}]", "1", "2", "PROJ-5", "9");

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals(1, body.getInt("priority_id"));
        assertEquals(2, body.getInt("type_id"));
        assertEquals("PROJ-5", body.getString("refs"));
        assertEquals(List.of(9), body.getJSONArray("labels").toList());
    }

    @Test
    public void testCreateCaseWithNullPriorityUsesDefault() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 103).toString());

        client.createCase("Project A", "Title", null, null, null);

        assertEquals(2, new JSONObject(client.lastRequest().body).getInt("priority_id"));
    }

    @Test
    public void testCreateCaseDetailedViaProjectNameWithDefaults() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 202).toString());

        client.createCaseDetailed("Project A", "Title", null, null, null,
                null, null, null, null);

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals(2, body.getInt("priority_id")); // default when priority_id omitted
        assertFalse(body.has("type_id"));
        assertFalse(body.has("refs"));
        assertFalse(body.has("labels"));
    }

    @Test
    public void testCreateCaseStepsSkipsInvalidPriorityTypeAndLabels() throws Exception {
        StubTestRailClient client = newStubClient();
        client.setLogEnabled(true);
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));
        client.queuePost(new JSONObject().put("id", 302).toString());

        client.createCaseStepsByProjectId(5, "Title", null,
                "[{\"content\":\"c\",\"expected\":\"e\"}]", "bad", "bad", null, "bad");

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals(2, body.getInt("priority_id")); // default on parse failure
        assertFalse(body.has("type_id"));            // invalid type skipped
        assertFalse(body.has("labels"));             // no valid label IDs
    }

    @Test
    public void testGetAllCasesViaProjectName() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(5, "Project A")));
        client.queueGet(casesPage(null, new JSONObject().put("id", 1).put("title", "TC")));

        List<TestCase> result = client.getAllCases("Project A", null);

        assertEquals(1, result.size());
        assertEquals("C1", result.get(0).getKey());
    }

    @Test
    public void testGetCasesBreaksOnEmptyCasesPage() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(casesPage("/api/v2/get_cases/7&limit=250&offset=250"));

        List<TestCase> result = client.getAllCasesByProjectId(7);

        assertEquals(0, result.size());
        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testGetLabelsBreaksOnEmptyLabelsPage() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(labelsPage("/api/v2/get_labels/8&limit=250&offset=250"));

        JSONArray labels = new JSONArray(client.getLabelsById(8));

        assertEquals(0, labels.length());
        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testSearchAndPerformBreaksOnEmptyCasesPage() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(casesPage("/api/v2/get_cases&project_id=5&limit=250&offset=250"));

        List<TestCase> result = client.searchAndPerform("project_id=5", null);

        assertEquals(0, result.size());
        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testGetProjectsStopsWhenLinksIsNotAnObject() throws Exception {
        StubTestRailClient client = newStubClient();
        JSONObject response = new JSONObject();
        response.put("projects", new JSONArray().put(project(1, "A")));
        response.put("_links", "not-an-object");
        client.queueGet(response.toString());

        JSONObject result = new JSONObject(client.getProjects());

        assertEquals(1, result.getJSONArray("projects").length());
        assertEquals(1, client.getRequests().size());
    }

    @Test
    public void testUpdateCaseSetsFieldsAndSkipsInvalidPriority() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queuePost(new JSONObject().put("id", 123).toString());

        client.updateCase("C123", "New title", "oops", "PROJ-7");

        RecordedRequest request = client.lastRequest();
        assertEquals(apiUrl("/update_case/123"), request.url);
        JSONObject body = new JSONObject(request.body);
        assertEquals("New title", body.getString("title"));
        assertFalse(body.has("priority_id")); // invalid priority ignored
        assertEquals("PROJ-7", body.getString("refs"));
    }

    @Test
    public void testUpdateCaseWithValidPriority() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queuePost(new JSONObject().put("id", 123).toString());

        client.updateCase("123", null, "4", null);

        JSONObject body = new JSONObject(client.lastRequest().body);
        assertEquals(4, body.getInt("priority_id"));
        assertFalse(body.has("title"));
        assertFalse(body.has("refs"));
    }

    @Test
    public void testUpdateCaseWithNullFieldsSendsEmptyBody() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queuePost(new JSONObject().put("id", 123).toString());

        client.updateCase("123", null, null, null);

        assertEquals("{}", client.lastRequest().body);
    }

    @Test
    public void testUpdateDescriptionMapsToCustomPreconds() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queuePost(new JSONObject().put("id", 123).toString());

        client.updateDescription("C123", "New description");

        assertEquals("New description",
                new JSONObject(client.lastRequest().body).getString("custom_preconds"));
    }

    @Test
    public void testDeleteCasePostsToDeleteEndpoint() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queuePost("{}");

        String response = client.deleteCase("123");

        assertEquals("{}", response);
        assertEquals(apiUrl("/delete_case/123"), client.lastRequest().url);
        assertEquals("{}", client.lastRequest().body);
    }

    @Test
    public void testLinkToRequirementAppendsToExistingRefs() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject().put("id", 123).put("refs", "PROJ-1").toString());
        client.queuePost(new JSONObject().put("id", 123).toString());

        client.linkToRequirement("C123", "PROJ-2");

        JSONObject body = new JSONObject(client.lastRequest().body);
        String refs = body.getString("refs");
        assertTrue(refs.contains("PROJ-1"));
        assertTrue(refs.contains("PROJ-2"));
    }

    @Test
    public void testLinkToRequirementWithoutExistingRefs() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject().put("id", 123).toString());
        client.queuePost(new JSONObject().put("id", 123).toString());

        client.linkIssueWithRelationship("123", " PROJ-9 ", "relates");

        assertEquals("PROJ-9", new JSONObject(client.lastRequest().body).getString("refs"));
    }

    // ========== labels & case types ==========

    @Test
    public void testGetLabelsByIdPaginates() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(labelsPage("/api/v2/get_labels/8&limit=250&offset=250",
                new JSONObject().put("id", 1).put("title", "one")));
        client.queueGet(labelsPage(null,
                new JSONObject().put("id", 2).put("title", "two")));

        JSONArray labels = new JSONArray(client.getLabelsById(8));

        assertEquals(2, labels.length());
        assertEquals(List.of(
                apiUrl("/get_labels/8&limit=250&offset=0"),
                apiUrl("/get_labels/8&limit=250&offset=250")
        ), client.getUrls());
    }

    @Test
    public void testGetLabelsResolvesProject() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(8, "Project L")));
        client.queueGet(labelsPage(null, new JSONObject().put("id", 1).put("title", "one")));

        JSONArray labels = new JSONArray(client.getLabels("Project L"));

        assertEquals(1, labels.length());
    }

    @Test
    public void testGetLabelExecutesGetRequest() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONObject().put("id", 7).put("title", "Login").toString());

        String response = client.getLabel("7");

        assertEquals(7, new JSONObject(response).getInt("id"));
        assertEquals(apiUrl("/get_label/7"), client.lastRequest().url);
    }

    @Test
    public void testUpdateLabelPostsProjectIdAndTitle() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(8, "Project L")));
        client.queuePost(new JSONObject().put("id", 7).toString());

        client.updateLabel("7", "Project L", "Release 2.0");

        RecordedRequest request = client.lastRequest();
        assertEquals(apiUrl("/update_label/7"), request.url);
        JSONObject body = new JSONObject(request.body);
        assertEquals(8, body.getInt("project_id"));
        assertEquals("Release 2.0", body.getString("title"));
    }

    @Test
    public void testGetCaseTypesAndResolveTypeIdByName() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONArray()
                .put(new JSONObject().put("id", 6).put("name", "Functional"))
                .put(new JSONObject().put("id", 1).put("name", "Automated"))
                .toString());
        client.queueGet(new JSONArray()
                .put(new JSONObject().put("id", 6).put("name", "Functional"))
                .toString());

        String types = client.getCaseTypes();
        assertEquals(2, new JSONArray(types).length());
        assertEquals(apiUrl("/get_case_types"), client.getRequests().get(0).url);

        assertEquals("6", client.resolveTypeIdByName(" functional "));
    }

    @Test
    public void testResolveTypeIdByNameThrowsWhenMissing() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(new JSONArray()
                .put(new JSONObject().put("id", 6).put("name", "Functional"))
                .toString());

        IOException exception = assertThrows(IOException.class,
                () -> client.resolveTypeIdByName("Nonexistent"));
        assertTrue(exception.getMessage().contains("Case type not found: Nonexistent"));
    }

    @Test
    public void testResolveLabelIdsByNamesResolvesProjectFirst() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(8, "Project L")));
        client.queueGet(labelsPage(null,
                new JSONObject().put("id", 12).put("title", "ai_generated"),
                new JSONObject().put("id", 7).put("title", "Login")));

        String ids = client.resolveLabelIdsByNames("Project L", new String[]{" Login ", "ai_generated"});

        assertEquals("7,12", ids);
    }

    // ========== default section resolution ==========

    @Test
    public void testGetDefaultSectionIdCreatesSectionWhenNoneExist() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(sectionsPage()); // empty sections
        client.queuePost(new JSONObject().put("id", 99).put("name", "Test Cases").toString());

        int sectionId = client.getDefaultSectionId(5);

        assertEquals(99, sectionId);
        RecordedRequest createRequest = client.lastRequest();
        assertEquals("POST", createRequest.method);
        assertEquals(apiUrl("/add_section/5"), createRequest.url);
        assertEquals("Test Cases", new JSONObject(createRequest.body).getString("name"));
    }

    @Test
    public void testGetDefaultSectionIdUsesCache() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(sectionsPage(new JSONObject().put("id", 42)));

        assertEquals(42, client.getDefaultSectionId(5));
        assertEquals(42, client.getDefaultSectionId(5));

        assertEquals(1, client.getRequests().size()); // second call served from cache
    }

    // ========== TrackerClient behavior ==========

    @Test
    public void testCreateTicketParsesBody() throws IOException {
        StubTestRailClient client = newStubClient();

        TestCase testCase = client.createTicket(new JSONObject().put("id", 55).put("title", "T").toString());

        assertEquals("C55", testCase.getKey());
    }

    @Test
    public void testGetTestCasesWrapsFailuresInIOException() throws Exception {
        StubTestRailClient client = newStubClient();
        client.queueGet(projectsPage(null, project(1, "Other")));

        ITicket ticket = new TestCase(BASE_PATH, new JSONObject().put("id", 1));

        assertThrows(IOException.class, () -> client.getTestCases(ticket, "Test Case"));
    }

    @Test
    public void testGetTextFieldsOnlyHandlesNullDescription() throws IOException {
        StubTestRailClient client = newStubClient();
        TestCase testCase = new TestCase(BASE_PATH, new JSONObject().put("id", 1).put("title", "Only title"));

        String text = client.getTextFieldsOnly(testCase);

        assertTrue(text.contains("Only title"));
    }

    // ========== markdown table conversion edge cases ==========

    @Test
    public void testConvertMarkdownTablesToTestRailFormatSkipsSeparatorOnlyTable() {
        String markdown = "|---|---|\n|---|";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(markdown);

        assertEquals("", result);
    }

    @Test
    public void testConvertMarkdownTablesToTestRailFormatSkipsEmptyCells() {
        String markdown = "| A |  | B |\n|---|---|---|\n| 1 |  | 2 |";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(markdown);

        assertTrue(result.contains("|||:A|:B"));
        assertTrue(result.contains("||1|2"));
    }

    @Test
    public void testConvertMarkdownTablesToHtmlTableOnly() {
        String markdown = "| H1 | H2 |\n|----|----|\n| v1 | v2 |";

        String result = TestRailClient.convertMarkdownTablesToHtml(markdown);

        assertEquals("<table><thead><tr><th>H1</th><th>H2</th></tr></thead>"
                + "<tbody><tr><td>v1</td><td>v2</td></tr></tbody></table>", result);
    }

    @Test
    public void testConvertMarkdownTablesToHtmlSkipsBlankLinesAndSeparatorOnlyTable() {
        String markdown = "\n\n|---|---|\nText after";

        String result = TestRailClient.convertMarkdownTablesToHtml(markdown);

        assertEquals("<p>Text after</p>", result);
    }
}
