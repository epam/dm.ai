// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for XrayClient X-ray behavior: GraphQL delegators, ticket creation
 * with steps/gherkin/dataset/preconditions, and search enrichment.
 * All Jira HTTP traffic is intercepted via mockConstruction(GenericRequest.class)
 * and all X-ray API traffic via mockConstruction(XrayRestClient.class),
 * so no real network calls are made.
 */
public class XrayClientEnrichmentTest {

    private static final String CREATED_TICKET_RESPONSE = "{\"key\":\"TP-100\",\"id\":\"10001\"}";

    private static final String TICKET_JSON = "{\"id\":\"10002\",\"key\":\"TP-1\",\"fields\":{" +
            "\"summary\":\"Pre summary\",\"description\":\"Pre desc\"," +
            "\"issuetype\":{\"name\":\"Precondition\"}}}";

    private static final String SEARCH_JSON = "{\"issues\":[{" +
            "\"id\":\"10001\",\"key\":\"TP-1\",\"fields\":{" +
            "\"issuetype\":{\"name\":\"Test\"},\"summary\":\"Test ticket\"}}],\"isLast\":true}";

    private static final String XRAY_TESTS_JSON = "[{" +
            "\"jira\":{\"key\":\"TP-1\"}," +
            "\"steps\":[{\"action\":\"Open page\",\"data\":\"url\",\"result\":\"Page opened\"}]," +
            "\"testType\":{\"name\":\"Manual\"}," +
            "\"gherkin\":\"Given a precondition\"," +
            "\"dataset\":{\"parameters\":[\"p1\"],\"rows\":[[\"v1\"]]}," +
            "\"preconditions\":{\"results\":[{\"jira\":{\"key\":\"PC-1\"}}]}" +
            "}]";

    @Before
    public void setUp() {
        PropertyReader.clearOverrides();
    }

    @After
    public void tearDown() {
        PropertyReader.clearOverrides();
    }

    private XrayClient newClient() throws IOException {
        XrayClient client = new XrayClient(
                "https://test-jira.atlassian.net",
                "dGVzdDp0ZXN0", // base64 test:test
                "Basic",
                -1,
                "https://xray.cloud.getxray.app/api/v2",
                "test_client_id",
                "test_client_secret",
                false, // isLoggingEnabled
                false, // isClearCache
                false, // isWaitBeforePerform
                100L,  // sleepTimeRequest
                null,  // extraFieldsProject
                null   // extraFields
        );
        // Deterministic behavior regardless of the environment's JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES
        client.setTransformCustomFieldsToNames(false);
        return client;
    }

    /**
     * Response provider for mocked Jira HTTP calls; may throw IOException to simulate failures.
     */
    @FunctionalInterface
    private interface JiraResponder {
        String respond(String url) throws IOException;
    }

    /**
     * Intercepts all Jira HTTP calls. execute() responses are dispatched by request URL,
     * post() always returns the given response.
     */
    private MockedConstruction<GenericRequest> mockJiraHttp(JiraResponder executeResponder,
                                                            String postResponse) {
        return mockConstruction(GenericRequest.class, (mock, context) -> {
            String url = (String) context.arguments().get(1);
            when(mock.url()).thenReturn(url);
            when(mock.param(anyString(), anyString())).thenReturn(mock);
            when(mock.param(anyString(), anyInt())).thenReturn(mock);
            when(mock.fields(any())).thenReturn(mock);
            when(mock.header(anyString(), anyString())).thenReturn(mock);
            when(mock.setBody(anyString())).thenReturn(mock);
            when(mock.execute()).thenAnswer(inv -> executeResponder.respond(url));
            when(mock.post()).thenAnswer(inv -> postResponse);
        });
    }

    private JiraResponder defaultJiraResponder(String searchResponse) {
        return url -> {
            if (url.contains("search/jql")) {
                return searchResponse;
            }
            if (url.endsWith("/field")) {
                return "[{\"id\":\"customfield_10001\",\"name\":\"Story Points\"}]";
            }
            if (url.contains("issue/")) {
                return TICKET_JSON;
            }
            // serverInfo, project list, etc. - "[]" is not a valid JSONObject, which makes
            // isCloudJira() fall back to the atlassian.net URL pattern (Cloud)
            return "[]";
        };
    }

    private XrayRestClient xrayMock(MockedConstruction<XrayRestClient> mocked) {
        return mocked.constructed().get(0);
    }

    private void enableEnrichment() {
        PropertyReader.setOverrides(Map.of(
                PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT, "true",
                PropertyReader.XRAY_PARALLEL_FETCH_ENABLED, "false"));
    }

    // ------------------------------------------------------------------
    // GraphQL delegators
    // ------------------------------------------------------------------

    @Test
    public void testGraphQLDelegators_delegateToRestClient() throws IOException {
        try (MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);

            Map<String, Object> connectionResult = Map.of("status", "ok");
            when(rest.testConnection()).thenReturn(connectionResult);
            JSONObject details = new JSONObject().put("issueId", "x1");
            when(rest.getTestDetailsGraphQL("TP-1")).thenReturn(details);
            JSONArray steps = new JSONArray().put(new JSONObject().put("action", "a"));
            when(rest.getTestStepsGraphQL("TP-1")).thenReturn(steps);
            JSONArray preconditions = new JSONArray().put(new JSONObject().put("key", "PC-1"));
            when(rest.getPreconditionsGraphQL("TP-1")).thenReturn(preconditions);
            when(rest.getPreconditionDetailsGraphQL("PC-1")).thenReturn(details);
            when(rest.addTestStepGraphQL("10001", "a", "d", "r")).thenReturn(details);
            when(rest.addTestStepsGraphQL(eq("10001"), any(JSONArray.class))).thenReturn(steps);
            when(rest.addPreconditionToTestGraphQL("10001", "10002")).thenReturn(details);
            when(rest.addPreconditionsToTestGraphQL(eq("10001"), any(JSONArray.class))).thenReturn(preconditions);

            assertSame(connectionResult, client.testConnection());
            assertSame(details, client.getTestDetailsGraphQL("TP-1"));
            assertSame(steps, client.getTestStepsGraphQL("TP-1"));
            assertSame(preconditions, client.getPreconditionsGraphQL("TP-1"));
            assertSame(details, client.getPreconditionDetailsGraphQL("PC-1"));
            assertSame(details, client.addTestStepGraphQL("10001", "a", "d", "r"));
            assertSame(steps, client.addTestStepsGraphQL("10001", steps));
            assertSame(details, client.addPreconditionToTestGraphQL("10001", "10002"));
            assertSame(preconditions, client.addPreconditionsToTestGraphQL("10001", new JSONArray().put("10002")));

            assertSame(rest, client.getXrayRestClient());
        }
    }

    // ------------------------------------------------------------------
    // getTextFieldsOnly
    // ------------------------------------------------------------------

    @Test
    public void testGetTextFieldsOnly_withoutCustomFields() throws IOException {
        XrayClient client = newClient();
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketTitle()).thenReturn("Title");
        when(ticket.getTicketDescription()).thenReturn("Description");

        assertEquals("Title\nDescription", client.getTextFieldsOnly(ticket));
    }

    @Test
    public void testGetTextFieldsOnly_withConfiguredCustomField() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE)) {
            // extraFieldsProject + extraFields trigger getFieldCustomCode resolution in the constructor;
            // the mocked /field endpoint maps "Story Points" to customfield_10001
            XrayClient client = new XrayClient(
                    "https://test-jira.atlassian.net",
                    "dGVzdDp0ZXN0",
                    "Basic",
                    -1,
                    "https://xray.cloud.getxray.app/api/v2",
                    "test_client_id",
                    "test_client_secret",
                    false, false, false, 100L,
                    "TP",
                    new String[]{"Story Points"});
            client.setTransformCustomFieldsToNames(false);

            ITicket ticket = mock(ITicket.class);
            when(ticket.getTicketTitle()).thenReturn("Title");
            when(ticket.getTicketDescription()).thenReturn("Description");
            when(ticket.getFields()).thenReturn(new Fields(new JSONObject().put("customfield_10001", "5")));

            assertEquals("Title\nDescription\n5", client.getTextFieldsOnly(ticket));
        }
    }

    // ------------------------------------------------------------------
    // searchAndPerform (List) + enrichment
    // ------------------------------------------------------------------

    @Test
    public void testSearchAndPerform_list_enrichesTicketsSequentially() throws Exception {
        enableEnrichment();
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByJQLGraphQL(anyString())).thenReturn(new JSONArray(XRAY_TESTS_JSON));

            List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});

            assertEquals(1, tickets.size());
            Ticket ticket = tickets.get(0);
            JSONObject fields = ticket.getFields().getJSONObject();
            assertEquals(1, fields.getJSONArray("xrayTestSteps").length());
            assertEquals("Manual", fields.getJSONObject("xrayTestType").getString("name"));
            assertEquals("Given a precondition", fields.getString("xrayGherkin"));
            assertEquals(1, fields.getJSONObject("xrayDataset").getJSONArray("parameters").length());
            JSONArray preconditions = fields.getJSONArray("xrayPreconditions");
            assertEquals(1, preconditions.length());
            // Precondition was enriched with Jira summary/description from performTicket
            assertEquals("Pre summary", preconditions.getJSONObject(0).getString("summary"));
            assertEquals("Pre desc", preconditions.getJSONObject(0).getString("description"));
        }
    }

    @Test
    public void testSearchAndPerform_list_enrichesTicketsInParallel() throws Exception {
        PropertyReader.setOverrides(Map.of(
                PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT, "true",
                PropertyReader.XRAY_PARALLEL_FETCH_ENABLED, "true",
                PropertyReader.XRAY_PARALLEL_BATCH_SIZE, "10",
                PropertyReader.XRAY_PARALLEL_THREADS, "2",
                PropertyReader.XRAY_PARALLEL_DELAY_MS, "0"));
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByKeysGraphQLParallel(anyList(), anyString(), anyInt(), anyInt(), anyLong()))
                    .thenReturn(new JSONArray(XRAY_TESTS_JSON));

            List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});

            assertEquals(1, tickets.size());
            assertTrue(tickets.get(0).getFields().getJSONObject().has("xrayTestSteps"));
            // Note: the List overload enriches twice (once via the Performer override and once
            // directly), so the parallel fetch may be invoked more than once
            verify(rest, atLeastOnce()).getTestsByKeysGraphQLParallel(anyList(), anyString(), anyInt(), anyInt(), anyLong());
            verify(rest, never()).getTestsByJQLGraphQL(anyString());
        }
    }

    @Test
    public void testSearchAndPerform_list_emptyResultReturnsImmediately() throws Exception {
        enableEnrichment();
        String emptySearch = "{\"issues\":[],\"isLast\":true}";
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(emptySearch), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);

            List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});

            assertNotNull(tickets);
            assertTrue(tickets.isEmpty());
            verify(rest, never()).getTestsByJQLGraphQL(anyString());
        }
    }

    @Test
    public void testSearchAndPerform_list_xrayFailureReturnsUnenrichedTickets() throws Exception {
        enableEnrichment();
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByJQLGraphQL(anyString())).thenThrow(new IOException("X-ray unavailable"));

            List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});

            assertEquals(1, tickets.size());
            assertFalse(tickets.get(0).getFields().getJSONObject().has("xrayTestSteps"));
        }
    }

    @Test
    public void testSearchAndPerform_list_ensureIssueTypeFieldVariants() throws Exception {
        enableEnrichment();
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByJQLGraphQL(anyString())).thenReturn(new JSONArray());

            // null fields -> ensureIssueTypeField adds ['issuetype']
            assertEquals(1, client.searchAndPerform("project = TP", null).size());
            // issuetype already present -> fields returned as-is
            assertEquals(1, client.searchAndPerform("project = TP", new String[]{"issuetype"}).size());
        }
    }

    // ------------------------------------------------------------------
    // searchAndPerform (Performer)
    // ------------------------------------------------------------------

    @Test
    public void testSearchAndPerform_performer_enrichesWithSentinelAndHonorsBreak() throws Exception {
        PropertyReader.setOverrides(Map.of(
                PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT, "false",
                PropertyReader.XRAY_PARALLEL_FETCH_ENABLED, "false"));
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByJQLGraphQL(anyString())).thenReturn(new JSONArray(XRAY_TESTS_JSON));

            List<Ticket> performed = new ArrayList<>();
            client.searchAndPerform(ticket -> {
                performed.add(ticket);
                return true; // request stop after first ticket
            }, "project = TP", new String[]{"summary", XrayClient.FIELD_XRAY_ENRICHMENT});

            assertEquals(1, performed.size());
            assertTrue(performed.get(0).getFields().getJSONObject().has("xrayTestSteps"));
            verify(rest).getTestsByJQLGraphQL(anyString());
        }
    }

    @Test
    public void testSearchAndPerform_performer_skipsEnrichmentWhenNotRequested() throws Exception {
        PropertyReader.setOverrides(Map.of(
                PropertyReader.XRAY_ENRICHMENT_ENABLED_BY_DEFAULT, "false",
                PropertyReader.XRAY_PARALLEL_FETCH_ENABLED, "false"));
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);

            List<Ticket> performed = new ArrayList<>();
            client.searchAndPerform(ticket -> {
                performed.add(ticket);
                return false;
            }, "project = TP", new String[]{"summary"});

            assertEquals(1, performed.size());
            assertFalse(performed.get(0).getFields().getJSONObject().has("xrayTestSteps"));
            verify(rest, never()).getTestsByJQLGraphQL(anyString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchAndPerform_performer_recursionGuardCallsParentDirectly() throws Exception {
        enableEnrichment();
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);

            Field field = XrayClient.class.getDeclaredField("isEnriching");
            field.setAccessible(true);
            ThreadLocal<Boolean> isEnriching = (ThreadLocal<Boolean>) field.get(client);
            isEnriching.set(true);
            try {
                List<Ticket> performed = new ArrayList<>();
                client.searchAndPerform(ticket -> {
                    performed.add(ticket);
                    return false;
                }, "project = TP", new String[]{"summary"});
                assertEquals(1, performed.size());
                // Enrichment must be skipped while the recursion guard is set
                verify(rest, never()).getTestsByJQLGraphQL(anyString());
            } finally {
                isEnriching.set(false);
            }
        }
    }

    // ------------------------------------------------------------------
    // createTicketInProject
    // ------------------------------------------------------------------

    @Test
    public void testCreateTicketInProject_steps_setViaGraphQLWithXrayIssueId() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addTestStepsGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "s1")));

            JSONArray steps = new JSONArray()
                    .put(new JSONObject().put("action", "a1").put("data", "d1").put("result", "r1"))
                    .put(new JSONObject().put("step", "s2").put("expectedResult", "e2"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("steps", steps));

            assertEquals("TP-100", new JSONObject(response).getString("key"));

            ArgumentCaptor<JSONArray> stepsCaptor = ArgumentCaptor.forClass(JSONArray.class);
            verify(rest).addTestStepsGraphQL(eq("xray-123"), stepsCaptor.capture());
            JSONArray graphqlSteps = stepsCaptor.getValue();
            assertEquals(2, graphqlSteps.length());
            assertEquals("a1", graphqlSteps.getJSONObject(0).getString("action"));
            assertEquals("d1", graphqlSteps.getJSONObject(0).getString("data"));
            assertEquals("r1", graphqlSteps.getJSONObject(0).getString("result"));
            // 'step'/'expectedResult' field names are mapped to action/result
            assertEquals("s2", graphqlSteps.getJSONObject(1).getString("action"));
            assertEquals("e2", graphqlSteps.getJSONObject(1).getString("result"));

            // The Jira create body must not contain the X-ray-specific 'steps' field
            GenericRequest createRequest = gr.constructed().stream()
                    .filter(m -> m.url().endsWith("/issue"))
                    .findFirst().orElseThrow(AssertionError::new);
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(createRequest).setBody(bodyCaptor.capture());
            assertFalse(bodyCaptor.getValue().contains("\"steps\""));
        }
    }

    @Test
    public void testCreateTicketInProject_gherkinAndDataset_setViaGraphQL() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.updateCucumberTestGraphQL(anyString(), anyString())).thenReturn(new JSONObject());
            when(rest.updateDatasetInternalAPI(any(), anyString(), anyString(), any(JSONObject.class)))
                    .thenReturn(new JSONObject());

            JSONObject dataset = new JSONObject().put("parameters", new JSONArray().put("p1"));
            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("gherkin", "Given something");
                fields.set("dataset", dataset);
            });

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest).updateCucumberTestGraphQL("xray-123", "Given something");
            verify(rest).updateDatasetInternalAPI(any(), eq("TP-100"), eq("xray-123"), any(JSONObject.class));
            // Gherkin has precedence: steps API must not be called
            verify(rest, never()).addTestStepsGraphQL(anyString(), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_datasetFailure_doesNotFailCreation() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(
                url -> {
                    if (url.contains("issue/")) {
                        return "{\"errorMessages\":[\"boom\"]}";
                    }
                    return defaultJiraResponder(SEARCH_JSON).respond(url);
                }, CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.updateCucumberTestGraphQL(anyString(), anyString())).thenReturn(new JSONObject());

            // performTicket returns null for the error response, so setDataset throws IOException
            // which must be swallowed - the ticket was already created
            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("gherkin", "Given something");
                fields.set("dataset", new JSONObject().put("parameters", new JSONArray().put("p1")));
            });

            assertEquals("TP-100", new JSONObject(response).getString("key"));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditionIssueType_convertsStepsToDefinition() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.setPreconditionDefinitionGraphQL(anyString(), anyString()))
                    .thenReturn(new JSONObject().put("definition", "Step 1: a1 -> d1 -> r1"));

            JSONArray steps = new JSONArray()
                    .put(new JSONObject().put("action", "a1").put("data", "d1").put("result", "r1"))
                    .put(new JSONObject().put("action", "a2"));

            String response = client.createTicketInProject("TP", "Precondition", "Summary", "Description",
                    fields -> fields.set("steps", steps));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            ArgumentCaptor<String> definitionCaptor = ArgumentCaptor.forClass(String.class);
            verify(rest).setPreconditionDefinitionGraphQL(eq("xray-123"), definitionCaptor.capture());
            assertEquals("Step 1: a1 -> d1 -> r1\nStep 2: a2", definitionCaptor.getValue());
            // For Precondition issues, steps must not be set via the test steps API
            verify(rest, never()).addTestStepsGraphQL(anyString(), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_graphQLEmpty_fallsBackToRest() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class))).thenReturn(new JSONArray());
            when(rest.xrayRequest(anyString(), anyString(), anyString())).thenReturn("{}");

            JSONArray preconditions = new JSONArray()
                    .put("PC-1")
                    .put(new JSONObject().put("key", "PC-2"))
                    .put(new JSONObject().put("id", "PC-3"))
                    .put(42); // unsupported item type - must be skipped

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", preconditions));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest).addPreconditionsToTestGraphQL(eq("xray-123"), any(JSONArray.class));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(rest).xrayRequest(eq("api/v2/tests/TP-100/preconditions"), eq("PUT"), bodyCaptor.capture());
            assertEquals("[\"PC-1\",\"PC-2\",\"PC-3\"]", bodyCaptor.getValue());
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_graphQLError_fallsBackToRest() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class)))
                    .thenThrow(new IOException("preconditions with the following ids were not found"));
            when(rest.xrayRequest(anyString(), anyString(), anyString())).thenReturn("{}");

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put("PC-1")));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest).xrayRequest(eq("api/v2/tests/TP-100/preconditions"), eq("PUT"), eq("[\"PC-1\"]"));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_graphQLSuccess_noRestFallback() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "link-1")));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put("PC-1")));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest, never()).xrayRequest(anyString(), anyString(), anyString());
        }
    }

    @Test
    public void testCreateTicketInProject_steps_keyNotValid_fallsBackToJiraIssueId() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            // Ticket never syncs to X-ray: waitForXraySync exhausts attempts and returns null
            when(rest.getTestDetailsGraphQL("TP-100")).thenThrow(new IOException("not synced"));
            // First call with the ticket key fails as invalid, fallback with Jira issue ID succeeds
            when(rest.addTestStepsGraphQL(anyString(), any(JSONArray.class)))
                    .thenThrow(new IOException("Issue key is not valid"))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "s1")));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("steps", new JSONArray().put(new JSONObject().put("action", "a1"))));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            // performTicket returned id 10002 (from TICKET_JSON) for the fallback call
            verify(rest).addTestStepsGraphQL(eq("10002"), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_stringEncodedXrayFields_areParsed() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addTestStepsGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "s1")));
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "link-1")));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("steps", "[{\"action\":\"a1\"}]");
                fields.set("preconditions", "[\"PC-1\"]");
            });

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest).addTestStepsGraphQL(eq("xray-123"), any(JSONArray.class));
            verify(rest).addPreconditionsToTestGraphQL(eq("xray-123"), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_invalidStepsString_isSkipped() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("steps", "not a json array"));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest, never()).addTestStepsGraphQL(anyString(), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_nullFieldsInitializer_createsPlainTicket() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", null);

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest, never()).addTestStepsGraphQL(anyString(), any(JSONArray.class));
        }
    }

    // ------------------------------------------------------------------
    // createPrecondition MCP tool
    // ------------------------------------------------------------------

    @Test
    public void testCreatePrecondition_withJsonSteps() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.setPreconditionDefinitionGraphQL(anyString(), anyString()))
                    .thenReturn(new JSONObject().put("definition", "Step 1: a1"));

            String key = client.createPrecondition("TP", "Precondition summary", null,
                    "[{\"action\":\"a1\",\"data\":\"d1\",\"result\":\"r1\"}]");

            assertEquals("TP-100", key);
            verify(rest).setPreconditionDefinitionGraphQL(eq("xray-123"), eq("Step 1: a1 -> d1 -> r1"));
        }
    }

    @Test
    public void testCreatePrecondition_withPolyglotListSteps() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.setPreconditionDefinitionGraphQL(anyString(), anyString()))
                    .thenReturn(new JSONObject().put("definition", "Step 1: b1"));

            // GraalJS PolyglotList string representation "(1)[{...}]" - array part must be extracted
            String key = client.createPrecondition("TP", "Precondition summary", "Description",
                    "(1)[{\"action\":\"b1\"}]");

            assertEquals("TP-100", key);
            verify(rest).setPreconditionDefinitionGraphQL(eq("xray-123"), eq("Step 1: b1"));
        }
    }

    @Test
    public void testCreatePrecondition_withInvalidSteps_stillCreates() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));

            String key = client.createPrecondition("TP", "Precondition summary", "Description", "{invalid json");

            assertEquals("TP-100", key);
            verify(rest, never()).setPreconditionDefinitionGraphQL(anyString(), anyString());
        }
    }

    @Test
    public void testCreatePrecondition_withoutSteps() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));

            String key = client.createPrecondition("TP", "Precondition summary", "Description", null);

            assertEquals("TP-100", key);
            verify(rest, never()).setPreconditionDefinitionGraphQL(anyString(), anyString());
        }
    }

    // ------------------------------------------------------------------
    // Fallback and error-handling branches
    // ------------------------------------------------------------------

    @Test
    public void testCreateTicketInProject_syncedWithoutIssueId_usesTicketKeyDirectly() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            // Ticket is synced to X-ray but has no issueId - waitForXraySync returns null immediately
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject());
            when(rest.addTestStepsGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "s1")));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("steps", new JSONArray().put(new JSONObject().put("action", "a1"))));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            // Ticket key accepted by GraphQL directly - no Jira issue ID fallback needed
            verify(rest).addTestStepsGraphQL(eq("TP-100"), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_steps_keyFailsWithOtherError_rethrowsAndIsSwallowed() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject());
            // Error is not a "key not valid" error - must be rethrown immediately (and swallowed by createTicketInProject)
            when(rest.addTestStepsGraphQL(anyString(), any(JSONArray.class)))
                    .thenThrow(new IOException("connection timeout"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("steps", new JSONArray().put(new JSONObject().put("action", "a1"))));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest, times(1)).addTestStepsGraphQL(anyString(), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_gherkinAndDataset_fallbackToJiraIssueId() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject()); // synced, no issueId
            when(rest.updateCucumberTestGraphQL(anyString(), anyString())).thenReturn(new JSONObject());
            when(rest.updateDatasetInternalAPI(any(), anyString(), anyString(), any(JSONObject.class)))
                    .thenReturn(new JSONObject());

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("gherkin", "Given something");
                fields.set("dataset", new JSONObject().put("parameters", new JSONArray().put("p1")));
            });

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            // No Xray issue ID available - Jira issue ID from performTicket (10002) is used
            verify(rest).updateCucumberTestGraphQL(eq("10002"), eq("Given something"));
            verify(rest).updateDatasetInternalAPI(any(), eq("TP-100"), eq("10002"), any(JSONObject.class));
        }
    }

    @Test
    public void testCreateTicketInProject_gherkinFails_stillCreatesTicket() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.updateCucumberTestGraphQL(anyString(), anyString()))
                    .thenThrow(new IOException("GraphQL failed"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("gherkin", "Given something"));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
        }
    }

    @Test
    public void testCreateTicketInProject_datasetUpdateFails_loggedNotThrown() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.updateCucumberTestGraphQL(anyString(), anyString())).thenReturn(new JSONObject());
            // Internal API failure is a known limitation - must be logged, not thrown
            when(rest.updateDatasetInternalAPI(any(), anyString(), anyString(), any(JSONObject.class)))
                    .thenThrow(new IOException("X-acpt token extraction failed"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("gherkin", "Given something");
                fields.set("dataset", new JSONObject().put("parameters", new JSONArray().put("p1")));
            });

            assertEquals("TP-100", new JSONObject(response).getString("key"));
        }
    }

    @Test
    public void testCreateTicketInProject_emptyXrayValues_areSkippedEarly() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.updateCucumberTestGraphQL(anyString(), anyString())).thenReturn(new JSONObject());

            // Empty dataset -> setDataset returns early
            String response1 = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("gherkin", "Given something");
                fields.set("dataset", new JSONObject());
            });
            assertEquals("TP-100", new JSONObject(response1).getString("key"));
            verify(rest, never()).updateDatasetInternalAPI(any(), anyString(), anyString(), any(JSONObject.class));

            // Empty steps on a Test -> setTestSteps returns early; empty preconditions -> setPreconditions returns early
            String response2 = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("steps", new JSONArray());
                fields.set("preconditions", new JSONArray());
            });
            assertEquals("TP-100", new JSONObject(response2).getString("key"));

            // Empty steps on a Precondition -> setPreconditionDefinition returns early
            String response3 = client.createTicketInProject("TP", "Precondition", "Summary", "Description",
                    fields -> fields.set("steps", new JSONArray()));
            assertEquals("TP-100", new JSONObject(response3).getString("key"));

            verify(rest, never()).addTestStepsGraphQL(anyString(), any(JSONArray.class));
            verify(rest, never()).setPreconditionDefinitionGraphQL(anyString(), anyString());
            verify(rest, never()).addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditionDefinition_fallbackToJiraIssueId() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject()); // synced, no issueId
            // Result without 'definition' key exercises the alternate logging branch
            when(rest.setPreconditionDefinitionGraphQL(anyString(), anyString())).thenReturn(new JSONObject());

            String response = client.createTicketInProject("TP", "Precondition", "Summary", "Description",
                    fields -> fields.set("steps", new JSONArray().put(new JSONObject().put("action", "a1"))));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest).setPreconditionDefinitionGraphQL(eq("10002"), eq("Step 1: a1"));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_noValidKeys_skipsApiCalls() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));

            // Only unsupported item types - no valid precondition keys can be extracted
            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put(42).put(43)));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest, never()).addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class));
            verify(rest, never()).xrayRequest(anyString(), anyString(), anyString());
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_noXrayIssueId_fetchesTestIssueId() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject()); // synced, no issueId
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "link-1")));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put("PC-1")));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            // Test issue ID fetched from Jira (performTicket -> 10002) since no Xray issue ID is available
            verify(rest).addPreconditionsToTestGraphQL(eq("10002"), any(JSONArray.class));
            verify(rest, never()).xrayRequest(anyString(), anyString(), anyString());
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_preconditionTicketFetchFails_usesRestFallback() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(
                url -> {
                    if (url.contains("issue/")) {
                        throw new IOException("Jira unavailable");
                    }
                    return defaultJiraResponder(SEARCH_JSON).respond(url);
                }, CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.xrayRequest(anyString(), anyString(), anyString())).thenReturn("{}");

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put("PC-1")));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            // Precondition issue IDs could not be resolved -> GraphQL skipped, REST API used with keys
            verify(rest, never()).addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class));
            verify(rest).xrayRequest(eq("api/v2/tests/TP-100/preconditions"), eq("PUT"), eq("[\"PC-1\"]"));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_otherGraphQLError_warnsAndFallsBack() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class)))
                    .thenThrow(new IOException("connection timeout"));
            when(rest.xrayRequest(anyString(), anyString(), anyString())).thenReturn("{}");

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put("PC-1")));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            verify(rest).xrayRequest(eq("api/v2/tests/TP-100/preconditions"), eq("PUT"), eq("[\"PC-1\"]"));
        }
    }

    @Test
    public void testCreateTicketInProject_preconditions_restFallbackFails_stillCreatesTicket() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addPreconditionsToTestGraphQL(anyString(), any(JSONArray.class))).thenReturn(new JSONArray());
            when(rest.xrayRequest(anyString(), anyString(), anyString()))
                    .thenThrow(new IOException("REST API failed"));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description",
                    fields -> fields.set("preconditions", new JSONArray().put("PC-1")));

            assertEquals("TP-100", new JSONObject(response).getString("key"));
        }
    }

    @Test
    public void testCreateTicketInProject_nonXrayFields_copiedToJiraCreateBody() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));
            when(rest.addTestStepsGraphQL(anyString(), any(JSONArray.class)))
                    .thenReturn(new JSONArray().put(new JSONObject().put("id", "s1")));

            String response = client.createTicketInProject("TP", "Test", "Summary", "Description", fields -> {
                fields.set("steps", new JSONArray().put(new JSONObject().put("action", "a1")));
                fields.set("customfield_10001", "custom value");
            });

            assertEquals("TP-100", new JSONObject(response).getString("key"));
            GenericRequest createRequest = gr.constructed().stream()
                    .filter(m -> m.url().endsWith("/issue"))
                    .findFirst().orElseThrow(AssertionError::new);
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(createRequest).setBody(bodyCaptor.capture());
            assertTrue(bodyCaptor.getValue().contains("customfield_10001"));
            assertFalse(bodyCaptor.getValue().contains("\"steps\""));
        }
    }

    @Test
    public void testCreatePrecondition_gsonParsesNonList_stepsIgnored() throws IOException {
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(SEARCH_JSON), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestDetailsGraphQL("TP-100")).thenReturn(new JSONObject().put("issueId", "xray-123"));

            // Not a JSON array for org.json, but valid JSON for Gson - a plain string, not a List
            String key = client.createPrecondition("TP", "Precondition summary", "Description", "\"hello\"");

            assertEquals("TP-100", key);
            verify(rest, never()).setPreconditionDefinitionGraphQL(anyString(), anyString());
        }
    }

    @Test
    public void testSearchAndPerform_enrichment_ticketWithoutIssueType_treatedAsPotentialTest() throws Exception {
        enableEnrichment();
        String searchJson = "{\"issues\":[" +
                "{\"id\":\"10001\",\"key\":\"TP-1\",\"fields\":{\"summary\":\"No issue type\"}}," +
                "{\"id\":\"10002\",\"key\":\"TP-2\",\"fields\":{\"issuetype\":{\"name\":\"Test\"},\"summary\":\"Test\"}}" +
                "],\"isLast\":true}";
        String xrayJson = "[" +
                "{\"jira\":{\"key\":\"TP-1\"},\"steps\":[]}," +
                "{\"jira\":{\"key\":\"TP-2\"}}" +
                "]";
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(defaultJiraResponder(searchJson), CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByJQLGraphQL(anyString())).thenReturn(new JSONArray(xrayJson));

            List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});

            assertEquals(2, tickets.size());
            // Ticket without an issuetype field is treated as a potential test and looked up in X-ray data;
            // empty steps array and missing steps key must be handled gracefully
            verify(rest, atLeastOnce()).getTestsByJQLGraphQL(anyString());
        }
    }

    @Test
    public void testSearchAndPerform_enrichment_preconditionJiraFetchFails_enrichmentContinues() throws Exception {
        enableEnrichment();
        try (MockedConstruction<GenericRequest> gr = mockJiraHttp(
                url -> {
                    if (url.contains("issue/")) {
                        throw new IOException("Jira unavailable");
                    }
                    return defaultJiraResponder(SEARCH_JSON).respond(url);
                }, CREATED_TICKET_RESPONSE);
             MockedConstruction<XrayRestClient> mocked = mockConstruction(XrayRestClient.class)) {
            XrayClient client = newClient();
            XrayRestClient rest = xrayMock(mocked);
            when(rest.getTestsByJQLGraphQL(anyString())).thenReturn(new JSONArray(XRAY_TESTS_JSON));

            List<Ticket> tickets = client.searchAndPerform("project = TP", new String[]{"summary"});

            assertEquals(1, tickets.size());
            JSONObject fields = tickets.get(0).getFields().getJSONObject();
            assertTrue(fields.has("xrayTestSteps"));
            // Preconditions are attached but without Jira summary/description enrichment
            JSONArray preconditions = fields.getJSONArray("xrayPreconditions");
            assertFalse(preconditions.getJSONObject(0).has("summary"));
        }
    }

    @Test
    public void testStripXrayEnrichmentSentinel_nullOrEmptyFields_returnedAsIs() throws IOException {
        XrayClient client = newClient();
        assertNull(client.stripXrayEnrichmentSentinel(null));
        assertArrayEquals(new String[0], client.stripXrayEnrichmentSentinel(new String[0]));
    }

    // ------------------------------------------------------------------
    // getInstance
    // ------------------------------------------------------------------

    @Test
    public void testGetInstance_consistentWithEnvironmentConfiguration() throws IOException {
        TrackerClient<? extends ITicket> instance = XrayClient.getInstance();
        // Environment-dependent: returns null when Jira/X-ray is not configured,
        // otherwise a configured XrayClient. Either branch must work.
        assertTrue(instance == null || instance instanceof XrayClient);
    }
}
