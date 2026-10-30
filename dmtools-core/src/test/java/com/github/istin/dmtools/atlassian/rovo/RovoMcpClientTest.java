// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.rovo;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.cli.McpCliHandler;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RovoMcpClient} and {@link BasicRovoMcpClient}.
 */
class RovoMcpClientTest {

    private static class StubRovoMcpClient extends RovoMcpClient {
        StubRovoMcpClient(String basePath, String authorization, String authType, String defaultCloudId) throws IOException {
            super(basePath, authorization, authType, defaultCloudId);
        }

        @Override
        public String post(GenericRequest request) throws IOException {
            return "{}";
        }
    }

    private StubRovoMcpClient client;

    @BeforeEach
    void setUp() throws IOException {
        PropertyReader.clearOverrides();
        BasicRovoMcpClient.resetInstance();
        client = spy(new StubRovoMcpClient(
                "https://mcp.atlassian.com/v2/mcp",
                "dXNlcjphcGlfdG9rZW4=",
                "Basic",
                "default-cloud-123"
        ));
    }

    @AfterEach
    void tearDown() {
        PropertyReader.clearOverrides();
        BasicRovoMcpClient.resetInstance();
    }

    private static String wrapMcpTextResult(String text) {
        JSONObject contentItem = new JSONObject()
                .put("type", "text")
                .put("text", text);
        JSONObject result = new JSONObject()
                .put("content", new JSONArray().put(contentItem));
        return new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("result", result)
                .toString();
    }

    @Test
    void rovoSearch_sendsStandardModeAndOptionalParams() throws IOException {
        String searchPayload = "{\"results\":[{\"title\":\"Payment Architecture\",\"id\":\"101\"}]}";
        doReturn(wrapMcpTextResult(searchPayload)).when(client).post(any(GenericRequest.class));

        String result = client.rovoSearch("payment service architecture", "explicit-cloud-999", "confluence", 5);

        assertEquals(searchPayload, result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());

        GenericRequest req = captor.getValue();
        assertEquals("https://mcp.atlassian.com/v2/mcp", req.url());

        JSONObject body = new JSONObject(req.getBody());
        assertEquals("2.0", body.getString("jsonrpc"));
        assertEquals("tools/call", body.getString("method"));

        JSONObject params = body.getJSONObject("params");
        assertEquals("search", params.getString("name"));

        JSONObject args = params.getJSONObject("arguments");
        assertEquals("payment service architecture", args.getString("query"));
        assertEquals("STANDARD", args.getString("mode"));
        assertEquals("explicit-cloud-999", args.getString("cloudId"));
        assertEquals("confluence", args.getString("targetApp"));
        assertEquals(5, args.getInt("limit"));
    }

    @Test
    void rovoSearch_fallsBackToDefaultCloudIdAndOmitsNullOptionals() throws IOException {
        doReturn(wrapMcpTextResult("{\"hits\":[]}")).when(client).post(any(GenericRequest.class));

        String result = client.rovoSearch("onboarding guide", null, null, null);

        assertEquals("{\"hits\":[]}", result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());

        JSONObject args = new JSONObject(captor.getValue().getBody())
                .getJSONObject("params")
                .getJSONObject("arguments");
        assertEquals("onboarding guide", args.getString("query"));
        assertEquals("STANDARD", args.getString("mode"));
        assertEquals("default-cloud-123", args.getString("cloudId"));
        assertFalse(args.has("targetApp"));
        assertFalse(args.has("limit"));
    }

    @Test
    void rovoSearch_rejectsBlankQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> client.rovoSearch("   ", null, null, null));
    }

    @Test
    void rovoSummarize_callsGetConfluenceContentWithAiSummaryDetail() throws IOException {
        String summaryPayload = "{\"contentId\":\"987654\",\"summary\":\"Page covers release checklist.\"}";
        doReturn(wrapMcpTextResult(summaryPayload)).when(client).post(any(GenericRequest.class));

        String result = client.rovoSummarize("987654", null);

        assertEquals(summaryPayload, result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());

        JSONObject params = new JSONObject(captor.getValue().getBody()).getJSONObject("params");
        assertEquals("getConfluenceContent", params.getString("name"));

        JSONObject args = params.getJSONObject("arguments");
        assertEquals("987654", args.getString("contentId"));
        assertEquals("ai_summary", args.getString("detail"));
        assertEquals("default-cloud-123", args.getString("cloudId"));
    }

    @Test
    void rovoSummarize_rejectsBlankContentId() {
        assertThrows(IllegalArgumentException.class,
                () -> client.rovoSummarize("", "cloud-1"));
    }

    @Test
    void rovoContext_callsGetGraphContext() throws IOException {
        String graphPayload = "{\"links\":[{\"type\":\"pull_request\",\"id\":\"PR-42\"}]}";
        doReturn(wrapMcpTextResult(graphPayload)).when(client).post(any(GenericRequest.class));

        String result = client.rovoContext("jira_issue", "PROJ-123", "custom-cloud");

        assertEquals(graphPayload, result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client).post(captor.capture());

        JSONObject params = new JSONObject(captor.getValue().getBody()).getJSONObject("params");
        assertEquals("getGraphContext", params.getString("name"));

        JSONObject args = params.getJSONObject("arguments");
        assertEquals("jira_issue", args.getString("objectType"));
        assertEquals("PROJ-123", args.getString("identifier"));
        assertEquals("custom-cloud", args.getString("cloudId"));
    }

    @Test
    void rovoContext_rejectsMissingParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> client.rovoContext(null, "PROJ-123", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.rovoContext("jira_issue", "  ", null));
    }

    @Test
    void rovoAsk_returnsAgenticResultWhenEnabled() throws IOException {
        String agenticAnswer = "{\"answer\":\"Token refresh uses rotating refresh tokens.\",\"citations\":[\"page-1\"]}";
        doReturn(wrapMcpTextResult(agenticAnswer)).when(client).post(any(GenericRequest.class));

        String result = client.rovoAsk("How does token refresh work?", null);

        assertEquals(agenticAnswer, result);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, times(1)).post(captor.capture());

        JSONObject args = new JSONObject(captor.getValue().getBody())
                .getJSONObject("params")
                .getJSONObject("arguments");
        assertEquals("How does token refresh work?", args.getString("query"));
        assertEquals("AGENTIC", args.getString("mode"));
        assertEquals("default-cloud-123", args.getString("cloudId"));
    }

    @Test
    void rovoAsk_fallsBackToStandardSearchOnHttp403() throws IOException {
        RestClient.RestClientException forbidden = new RestClient.RestClientException(
                "403 Forbidden",
                "{\"error\":\"Agentic search is not enabled for this user or site\"}",
                403
        );
        String standardPayload = "{\"results\":[{\"id\":\"p1\",\"title\":\"Auth Guide\"}]}";

        doThrow(forbidden)
                .doReturn(wrapMcpTextResult(standardPayload))
                .when(client).post(any(GenericRequest.class));

        String result = client.rovoAsk("How does token refresh work?", "site-cloud");

        JSONObject parsed = new JSONObject(result);
        assertEquals("STANDARD_FALLBACK", parsed.getString("mode"));
        assertEquals("How does token refresh work?", parsed.getString("question"));
        assertEquals("Auth Guide", parsed.getJSONArray("results").getJSONObject(0).getString("title"));

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(client, times(2)).post(captor.capture());

        JSONObject firstArgs = new JSONObject(captor.getAllValues().get(0).getBody())
                .getJSONObject("params").getJSONObject("arguments");
        assertEquals("AGENTIC", firstArgs.getString("mode"));

        JSONObject secondArgs = new JSONObject(captor.getAllValues().get(1).getBody())
                .getJSONObject("params").getJSONObject("arguments");
        assertEquals("STANDARD", secondArgs.getString("mode"));
        assertEquals("site-cloud", secondArgs.getString("cloudId"));
    }

    @Test
    void rovoAsk_fallsBackToStandardSearchOnMcpIsError403() throws IOException {
        JSONObject mcpErrorResult = new JSONObject()
                .put("isError", true)
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "text")
                        .put("text", "403: Agentic search is not enabled for this user or site")));
        String mcpErrorEnvelope = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("result", mcpErrorResult)
                .toString();

        doReturn(mcpErrorEnvelope)
                .doReturn(wrapMcpTextResult("Plain text fallback search results"))
                .when(client).post(any(GenericRequest.class));

        String result = client.rovoAsk("Where is the runbook?", null);

        assertEquals("Plain text fallback search results", result);
        verify(client, times(2)).post(any(GenericRequest.class));
    }

    @Test
    void rovoAsk_rethrowsNon403Errors() throws IOException {
        RestClient.RestClientException serverError = new RestClient.RestClientException(
                "500 Internal Server Error",
                "{\"error\":\"Upstream failure\"}",
                500
        );
        doThrow(serverError).when(client).post(any(GenericRequest.class));

        assertThrows(RestClient.RestClientException.class,
                () -> client.rovoAsk("Any question", null));
        verify(client, times(1)).post(any(GenericRequest.class));
    }

    @Test
    void sign_supportsBasicAndBearerHeaders() throws IOException {
        StubRovoMcpClient basicClient = new StubRovoMcpClient(
                null, "dXNlcjp0b2tlbg==", "Basic", null);
        Request basicReq = basicClient.sign(new Request.Builder().url("https://mcp.atlassian.com/v2/mcp")).build();
        assertEquals("Basic dXNlcjp0b2tlbg==", basicReq.header("Authorization"));
        assertEquals("application/json", basicReq.header("Content-Type"));
        assertEquals("application/json, text/event-stream", basicReq.header("Accept"));

        StubRovoMcpClient bearerClient = new StubRovoMcpClient(
                null, "service-key-xyz", "Bearer", null);
        Request bearerReq = bearerClient.sign(new Request.Builder().url("https://mcp.atlassian.com/v2/mcp")).build();
        assertEquals("Bearer service-key-xyz", bearerReq.header("Authorization"));

        StubRovoMcpClient prefixedClient = new StubRovoMcpClient(
                null, "Bearer already-prefixed", "Basic", null);
        Request prefixedReq = prefixedClient.sign(new Request.Builder().url("https://mcp.atlassian.com/v2/mcp")).build();
        assertEquals("Bearer already-prefixed", prefixedReq.header("Authorization"));
    }

    @Test
    void unwrapMcpResponse_handlesSseAndJsonRpcErrors() throws IOException {
        String sse = "event: message\ndata: " + wrapMcpTextResult("{\"ok\":true}") + "\n\n";
        assertEquals("{\"ok\":true}", client.unwrapMcpResponse(sse));

        String rpcError = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("error", new JSONObject().put("code", -32602).put("message", "Invalid params"))
                .toString();
        RestClient.RestClientException ex = assertThrows(
                RestClient.RestClientException.class,
                () -> client.unwrapMcpResponse(rpcError)
        );
        assertTrue(ex.getMessage().contains("Invalid params"));
    }

    @Test
    void basicRovoMcpClient_andPropertyReaderResolution() throws IOException {
        Map<String, String> overrides = new HashMap<>();
        overrides.put(PropertyReader.ROVO_EMAIL, "dev@example.com");
        overrides.put(PropertyReader.ROVO_API_TOKEN, "secret-token");
        overrides.put(PropertyReader.ROVO_AUTH_TYPE, "Basic");
        overrides.put(PropertyReader.ROVO_CLOUD_ID, "cloud-abc");
        PropertyReader.setOverrides(overrides);

        PropertyReader reader = new PropertyReader();
        String expectedToken = Base64.getEncoder()
                .encodeToString("dev@example.com:secret-token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedToken, reader.getRovoLoginPassToken());
        assertEquals("https://mcp.atlassian.com/v2/mcp", reader.getRovoMcpUrl());
        assertEquals("Basic", reader.getRovoAuthType());
        assertEquals("cloud-abc", reader.getRovoCloudId());

        assertTrue(BasicRovoMcpClient.isConfigured());
        RovoMcpClient instance = BasicRovoMcpClient.getInstance();
        assertNotNull(instance);
        assertEquals("cloud-abc", instance.getDefaultCloudId());
    }

    @Test
    void mcpCliHandler_detectsRovoIntegrationWhenConfigured() {
        ApplicationConfiguration config = mock(ApplicationConfiguration.class);
        when(config.getValue(PropertyReader.ROVO_LOGIN_PASS_TOKEN)).thenReturn("dXNlcjp0b2tlbg==");

        assertTrue(McpCliHandler.isIntegrationConfigured("rovo", config));
        ApplicationConfiguration empty = mock(ApplicationConfiguration.class);
        assertFalse(McpCliHandler.isIntegrationConfigured("rovo", empty));
    }
}
