// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for XrayRestClient covering URL building, request signing,
 * token management, GraphQL query/mutation handling, pagination,
 * parallel fetching and dataset transformation logic.
 * All HTTP interactions are mocked - no network calls are made.
 */
@RunWith(MockitoJUnitRunner.class)
public class XrayRestClientCoverageTest {

    private static final String BASE_PATH = "https://eu.xray.cloud.getxray.app/api/v2";

    private XrayRestClient client;

    @Before
    public void setUp() throws Exception {
        client = createSpyClient();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private XrayRestClient createSpyClient() throws Exception {
        return createSpyClient(BASE_PATH);
    }

    private XrayRestClient createSpyClient(String basePath) throws Exception {
        XrayRestClient spyClient = spy(new XrayRestClient(basePath, "test_client_id", "test_client_secret"));
        setToken(spyClient, "test-token", System.currentTimeMillis() + 3600_000L);
        return spyClient;
    }

    private void setToken(XrayRestClient c, String token, long expiryTime) throws Exception {
        Field tokenField = XrayRestClient.class.getDeclaredField("accessToken");
        tokenField.setAccessible(true);
        tokenField.set(c, token);
        Field expiryField = XrayRestClient.class.getDeclaredField("tokenExpiryTime");
        expiryField.setAccessible(true);
        expiryField.setLong(c, expiryTime);
    }

    private Object invokePrivate(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = XrayRestClient.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Response buildHttpResponse(int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://eu.xray.cloud.getxray.app/api/v2/authenticate").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("message")
                .body(ResponseBody.create(body, MediaType.get("text/plain")))
                .build();
    }

    private void installMockHttpClient(XrayRestClient c, Response response) throws Exception {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(call);
        Field clientField = AbstractRestClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockHttpClient);
    }

    private String graphQlDataResponse(JSONObject data) {
        return new JSONObject().put("data", data).toString();
    }

    private JSONObject testResult(String key) {
        JSONObject jira = new JSONObject();
        jira.put("key", key);
        jira.put("summary", "Summary for " + key);
        JSONObject result = new JSONObject();
        result.put("issueId", "id-" + key);
        result.put("jira", jira);
        return result;
    }

    private String getTestsResponse(String... keys) {
        JSONArray results = new JSONArray();
        for (String key : keys) {
            results.put(testResult(key));
        }
        JSONObject getTests = new JSONObject();
        getTests.put("results", results);
        return graphQlDataResponse(new JSONObject().put("getTests", getTests));
    }

    // ------------------------------------------------------------------
    // path()
    // ------------------------------------------------------------------

    @Test
    public void testPath_FullUrlReturnedAsIs() {
        assertEquals("https://other.example.com/api/v2/graphql",
                client.path("https://other.example.com/api/v2/graphql"));
    }

    @Test
    public void testPath_ApiV2PathAppendedToBaseDomain() {
        assertEquals("https://eu.xray.cloud.getxray.app/api/v2/graphql",
                client.path("/api/v2/graphql"));
    }

    @Test
    public void testPath_ApiV2PathWithoutLeadingSlash() {
        assertEquals("https://eu.xray.cloud.getxray.app/api/v2/graphql",
                client.path("api/v2/graphql"));
    }

    @Test
    public void testPath_RelativePathWithLeadingSlash() {
        assertEquals("https://eu.xray.cloud.getxray.app/api/v2/test/TP-1/steps",
                client.path("/test/TP-1/steps"));
    }

    @Test
    public void testPath_RelativePathWithoutLeadingSlash() {
        assertEquals("https://eu.xray.cloud.getxray.app/api/v2/test/TP-1/steps",
                client.path("test/TP-1/steps"));
    }

    @Test
    public void testPath_BasePathWithoutApiV2AndTrailingSlash() throws Exception {
        XrayRestClient c = createSpyClient("https://xray.example.com/");
        assertEquals("https://xray.example.com/api/v2/graphql", c.path("api/v2/graphql"));
        assertEquals("https://xray.example.com/foo/bar", c.path("foo/bar"));
    }

    // ------------------------------------------------------------------
    // sign() / getCacheFolderName()
    // ------------------------------------------------------------------

    @Test
    public void testSign_AddsBearerTokenAndContentType() {
        Request.Builder builder = new Request.Builder().url("https://example.com/api/v2/graphql").get();
        Request signed = client.sign(builder).build();
        assertEquals("Bearer test-token", signed.header("Authorization"));
        assertEquals("application/json", signed.header("Content-Type"));
    }

    @Test
    public void testSign_TokenAcquisitionFailureStillAddsContentType() throws Exception {
        // No valid token and HTTP client that fails -> getAccessToken throws RuntimeException, caught by sign()
        setToken(client, null, 0L);
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("connection refused"));
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(call);
        Field clientField = AbstractRestClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(client, mockHttpClient);

        Request.Builder builder = new Request.Builder().url("https://example.com/api/v2/graphql").get();
        Request signed = client.sign(builder).build();
        assertNull(signed.header("Authorization"));
        assertEquals("application/json", signed.header("Content-Type"));
    }

    @Test
    public void testGetCacheFolderName() throws Exception {
        Method method = XrayRestClient.class.getDeclaredMethod("getCacheFolderName");
        method.setAccessible(true);
        assertEquals("cacheXrayRestClient", method.invoke(client));
    }

    // ------------------------------------------------------------------
    // getAccessToken() / testConnection()
    // ------------------------------------------------------------------

    @Test
    public void testTestConnection_SuccessWithCachedToken() {
        Map<String, Object> result = client.testConnection();
        assertEquals(true, result.get("success"));
        assertEquals("X-ray API connection successful", result.get("message"));
        assertEquals("test-token".length(), result.get("tokenLength"));
    }

    @Test
    public void testGetAccessToken_RefreshSuccess_StripsQuotes() throws Exception {
        XrayRestClient c = createSpyClient();
        setToken(c, null, 0L);
        installMockHttpClient(c, buildHttpResponse(200, "\"fresh-token\""));

        Map<String, Object> result = c.testConnection();
        assertEquals(true, result.get("success"));
        assertEquals("fresh-token".length(), result.get("tokenLength"));

        // token is now cached and used for signing
        Request signed = c.sign(new Request.Builder().url("https://example.com/x").get()).build();
        assertEquals("Bearer fresh-token", signed.header("Authorization"));
    }

    @Test
    public void testGetAccessToken_RefreshSuccess_PlainToken() throws Exception {
        XrayRestClient c = createSpyClient();
        setToken(c, null, 0L);
        installMockHttpClient(c, buildHttpResponse(200, "  plain-token  "));

        Map<String, Object> result = c.testConnection();
        assertEquals(true, result.get("success"));
        assertEquals("plain-token".length(), result.get("tokenLength"));
    }

    @Test
    public void testGetAccessToken_AuthFailure() throws Exception {
        XrayRestClient c = createSpyClient();
        setToken(c, null, 0L);
        installMockHttpClient(c, buildHttpResponse(401, "unauthorized"));

        Map<String, Object> result = c.testConnection();
        assertEquals(false, result.get("success"));
        assertTrue(((String) result.get("message")).contains("X-ray API connection failed"));
        assertEquals("RuntimeException", result.get("error"));
    }

    @Test
    public void testGetAccessToken_AuthFailureEmptyBody() throws Exception {
        XrayRestClient c = createSpyClient();
        setToken(c, null, 0L);
        installMockHttpClient(c, buildHttpResponse(500, ""));

        Map<String, Object> result = c.testConnection();
        assertEquals(false, result.get("success"));
        assertTrue(((String) result.get("message")).contains("No error body"));
    }

    @Test
    public void testGetAccessToken_BasePathWithoutApiV2() throws Exception {
        XrayRestClient c = createSpyClient("https://xray.example.com");
        setToken(c, null, 0L);
        installMockHttpClient(c, buildHttpResponse(200, "tok"));
        assertEquals(true, c.testConnection().get("success"));
    }

    @Test
    public void testGetAccessToken_BasePathWithApiV2InMiddle() throws Exception {
        XrayRestClient c = createSpyClient("https://xray.example.com/api/v2/graphql");
        setToken(c, null, 0L);
        installMockHttpClient(c, buildHttpResponse(200, "tok"));
        assertEquals(true, c.testConnection().get("success"));
    }

    // ------------------------------------------------------------------
    // executeGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testExecuteGraphQL_Success() throws Exception {
        doReturn("response-body").when(client).post(any(GenericRequest.class));
        assertEquals("response-body", client.executeGraphQL("query { getTests { total } }"));
    }

    @Test
    public void testExecuteGraphQL_SingleArgOverload() throws Exception {
        doReturn("response-body").when(client).post(any(GenericRequest.class));
        assertEquals("response-body", client.executeGraphQL("query {}"));
    }

    @Test
    public void testExecuteGraphQL_RetriesOn503ThenSucceeds() throws Exception {
        doThrow(new IOException("HTTP 503 Service Unavailable"))
                .doReturn("ok-after-retry")
                .when(client).post(any(GenericRequest.class));
        assertEquals("ok-after-retry", client.executeGraphQL("query {}", true));
        verify(client, times(2)).post(any(GenericRequest.class));
    }

    @Test
    public void testExecuteGraphQL_NonRetryableErrorThrowsImmediately() throws Exception {
        doThrow(new IOException("HTTP 401 Unauthorized")).when(client).post(any(GenericRequest.class));
        try {
            client.executeGraphQL("query {}");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("401"));
        }
        verify(client, times(1)).post(any(GenericRequest.class));
    }

    @Test
    public void testExecuteGraphQL_InterruptedDuringRetry() throws Exception {
        doThrow(new IOException("HTTP 503 backup")).when(client).post(any(GenericRequest.class));
        Thread.currentThread().interrupt();
        try {
            client.executeGraphQL("query {}");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("interrupted"));
        } finally {
            Thread.interrupted(); // clear interrupt flag
        }
    }

    // ------------------------------------------------------------------
    // xrayRequest()
    // ------------------------------------------------------------------

    @Test
    public void testXrayRequest_Get() throws Exception {
        doReturn("get-response").when(client).execute(any(GenericRequest.class));
        assertEquals("get-response", client.xrayRequest("test/TP-1/steps", "GET", null));
    }

    @Test
    public void testXrayRequest_Post() throws Exception {
        doReturn("post-response").when(client).post(any(GenericRequest.class));
        assertEquals("post-response", client.xrayRequest("test/TP-1/steps", "post", "{\"a\":1}"));
    }

    @Test
    public void testXrayRequest_Put() throws Exception {
        doReturn("put-response").when(client).put(any(GenericRequest.class));
        assertEquals("put-response", client.xrayRequest("test/TP-1/steps", "PUT", "{}"));
    }

    @Test
    public void testXrayRequest_Patch() throws Exception {
        doReturn("patch-response").when(client).patch(any(GenericRequest.class));
        assertEquals("patch-response", client.xrayRequest("test/TP-1/steps", "PATCH", "{}"));
    }

    @Test
    public void testXrayRequest_Delete() throws Exception {
        doReturn("delete-response").when(client).delete(any(GenericRequest.class));
        assertEquals("delete-response", client.xrayRequest("test/TP-1/steps/1", "DELETE", null));
    }

    @Test
    public void testXrayRequest_UnsupportedMethod() throws Exception {
        try {
            client.xrayRequest("test/TP-1/steps", "OPTIONS", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported HTTP method"));
        }
    }

    @Test
    public void testXrayRequest_NonRetryableError() throws Exception {
        doThrow(new IOException("HTTP 404 Not Found")).when(client).execute(any(GenericRequest.class));
        try {
            client.xrayRequest("test/TP-1", "GET", null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("404"));
        }
        verify(client, times(1)).execute(any(GenericRequest.class));
    }

    @Test
    public void testXrayRequest_RetriesOn503ThenSucceeds() throws Exception {
        doThrow(new IOException("HTTP 503 backup"))
                .doReturn("ok-after-retry")
                .when(client).execute(any(GenericRequest.class));
        assertEquals("ok-after-retry", client.xrayRequest("test/TP-1", "GET", null));
        verify(client, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testXrayRequest_InterruptedDuringRetry() throws Exception {
        doThrow(new IOException("HTTP 503")).when(client).execute(any(GenericRequest.class));
        Thread.currentThread().interrupt();
        try {
            client.xrayRequest("test/TP-1", "GET", null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("interrupted"));
        } finally {
            Thread.interrupted(); // clear interrupt flag
        }
    }

    // ------------------------------------------------------------------
    // Pagination limit
    // ------------------------------------------------------------------

    @Test
    public void testPaginationLimit_DefaultAndClamping() {
        assertEquals(100, client.getPaginationLimit());
        client.setPaginationLimit(50);
        assertEquals(50, client.getPaginationLimit());
        client.setPaginationLimit(0);
        assertEquals(1, client.getPaginationLimit());
        client.setPaginationLimit(500);
        assertEquals(100, client.getPaginationLimit());
    }

    // ------------------------------------------------------------------
    // getTestsByJQLGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testGetTestsByJQLGraphQL_SinglePage() throws Exception {
        doReturn(getTestsResponse("TEST-1")).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByJQLGraphQL("project = TEST");
        assertEquals(1, result.length());
        assertEquals("TEST-1", result.getJSONObject(0).getJSONObject("jira").getString("key"));
    }

    @Test
    public void testGetTestsByJQLGraphQL_TwoArgOverload() throws Exception {
        doReturn(getTestsResponse("TEST-1")).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByJQLGraphQL("project = TEST", 10);
        assertEquals(1, result.length());
    }

    @Test
    public void testGetTestsByJQLGraphQL_PaginationWithDescOrderReversal() throws Exception {
        // First page: full page (2 items) in DESC order -> triggers reversal and another iteration
        // Second page: partial page (1 item) -> stops pagination
        doReturn(getTestsResponse("TEST-2", "TEST-1"))
                .doReturn(getTestsResponse("TEST-3"))
                .when(client).executeGraphQL(anyString());

        JSONArray result = client.getTestsByJQLGraphQL("project = TEST", 2, null);
        assertEquals(3, result.length());
        assertEquals("TEST-1", result.getJSONObject(0).getJSONObject("jira").getString("key"));
        assertEquals("TEST-2", result.getJSONObject(1).getJSONObject("jira").getString("key"));
        assertEquals("TEST-3", result.getJSONObject(2).getJSONObject("jira").getString("key"));
    }

    @Test
    public void testGetTestsByJQLGraphQL_EmptyResponse() throws Exception {
        doReturn("   ").when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByJQLGraphQL("project = TEST", 10, null);
        assertEquals(0, result.length());
    }

    @Test
    public void testGetTestsByJQLGraphQL_GraphQLErrors() throws Exception {
        JSONObject error = new JSONObject().put("message", "bad jql");
        String response = new JSONObject().put("errors", new JSONArray().put(error)).toString();
        doReturn(response).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByJQLGraphQL("project = TEST", 10, null);
        assertEquals(0, result.length());
    }

    @Test
    public void testGetTestsByJQLGraphQL_DataWithoutGetTests() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject())).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByJQLGraphQL("project = TEST", 10, null);
        assertEquals(0, result.length());
    }

    @Test
    public void testGetTestsByJQLGraphQL_NoResultsField() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", new JSONObject())))
                .when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByJQLGraphQL("project = TEST", 10, null);
        assertEquals(0, result.length());
    }

    @Test
    public void testGetTestsByJQLGraphQL_ExceptionWrapped() throws Exception {
        doThrow(new RuntimeException("boom")).when(client).executeGraphQL(anyString());
        try {
            client.getTestsByJQLGraphQL("project = TEST", 10, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to get tests via GraphQL"));
        }
    }

    // ------------------------------------------------------------------
    // getTestsByKeysGraphQLParallel()
    // ------------------------------------------------------------------

    @Test
    public void testGetTestsByKeysGraphQLParallel_NullAndEmptyKeys() throws Exception {
        assertEquals(0, client.getTestsByKeysGraphQLParallel(null, null, 50, 2, 0L).length());
        assertEquals(0, client.getTestsByKeysGraphQLParallel(new ArrayList<>(), null, 50, 2, 0L).length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_FiltersExtraKeys() throws Exception {
        doReturn(getTestsResponse("TEST-1", "TEST-2", "TEST-3")).when(client).executeGraphQL(anyString());

        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Arrays.asList("TEST-2", "TEST-1"), "project = TEST AND issueType = Test", 2, 1, 0L);

        assertEquals(2, result.length());
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < result.length(); i++) {
            keys.add(result.getJSONObject(i).getJSONObject("jira").getString("key"));
        }
        assertTrue(keys.contains("TEST-1"));
        assertTrue(keys.contains("TEST-2"));
        assertFalse(keys.contains("TEST-3"));
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_NoBaseFilter() throws Exception {
        doReturn(getTestsResponse("TEST-1")).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Collections.singletonList("TEST-1"), null, 2, 1, 0L);
        assertEquals(1, result.length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_MissingKeysLogged() throws Exception {
        // Response only contains one of two requested keys -> missing keys branch
        doReturn(getTestsResponse("TEST-1")).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Arrays.asList("TEST-1", "TEST-2"), "", 2, 1, 0L);
        assertEquals(1, result.length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_DuplicateKeysInInput() throws Exception {
        doReturn(getTestsResponse("TEST-1")).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Arrays.asList("TEST-1", "TEST-1"), null, 2, 1, 0L);
        // Duplicate requested keys are deduplicated via the requestedKeys set
        assertEquals(1, result.length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_RateLimitRetryThenSuccess() throws Exception {
        String rateLimitError = "HTTP 429 {\"error\":{\"text\":\"Too many requests\","
                + "\"nextValidRequestDate\":\"2020-01-01T00:00:00.000Z\"}}";
        doThrow(new IOException(rateLimitError))
                .doReturn(getTestsResponse("TEST-1"))
                .when(client).executeGraphQL(anyString());

        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Collections.singletonList("TEST-1"), null, 1, 1, 0L);
        assertEquals(1, result.length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_RateLimitWithoutDate_Backoff() throws Exception {
        doThrow(new IOException("HTTP 429 rate limit exceeded"))
                .doReturn(getTestsResponse("TEST-1"))
                .when(client).executeGraphQL(anyString());

        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Collections.singletonList("TEST-1"), null, 1, 1, 0L);
        assertEquals(1, result.length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_NonRateLimitErrorReturnsEmpty() throws Exception {
        doThrow(new IOException("connection reset")).when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Collections.singletonList("TEST-1"), null, 1, 1, 0L);
        assertEquals(0, result.length());
    }

    @Test
    public void testGetTestsByKeysGraphQLParallel_NullPageData() throws Exception {
        doReturn("").when(client).executeGraphQL(anyString());
        JSONArray result = client.getTestsByKeysGraphQLParallel(
                Collections.singletonList("TEST-1"), null, 1, 1, 0L);
        assertEquals(0, result.length());
    }

    // ------------------------------------------------------------------
    // extractBaseFilter() / extractNextValidRequestDate() / enforceRateLimit()
    // ------------------------------------------------------------------

    @Test
    public void testExtractBaseFilter_RemovesKeyInClause() throws Exception {
        String result = (String) invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class},
                "project = DIGIX AND issueType = Test AND key in (DIGIX-123, DIGIX-456)");
        assertEquals("project = DIGIX AND issueType = Test", result);
    }

    @Test
    public void testExtractBaseFilter_RemovesKeyEqualsQuoted() throws Exception {
        String result = (String) invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class},
                "project = DIGIX AND key = \"DIGIX-123\"");
        assertEquals("project = DIGIX", result);
    }

    @Test
    public void testExtractBaseFilter_KeyFilterAtStart() throws Exception {
        String result = (String) invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class},
                "key in (DIGIX-123) AND project = DIGIX");
        assertEquals("project = DIGIX", result);
    }

    @Test
    public void testExtractBaseFilter_RemovesOrderBy() throws Exception {
        String result = (String) invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class},
                "project = DIGIX ORDER BY key ASC");
        assertEquals("project = DIGIX", result);
    }

    @Test
    public void testExtractBaseFilter_NullEmptyAndOnlyKeyFilter() throws Exception {
        assertEquals("", invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class}, (Object) null));
        assertEquals("", invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class}, "   "));
        // A lone key filter without AND is not removed by the regex patterns (current behavior)
        assertEquals("key = \"X-1\"", invokePrivate(client, "extractBaseFilter", new Class<?>[]{String.class}, "key = \"X-1\""));
    }

    @Test
    public void testExtractNextValidRequestDate_JsonNotAtStart() throws Exception {
        Long result = (Long) invokePrivate(client, "extractNextValidRequestDate", new Class<?>[]{String.class},
                "prefix text {\"error\":{\"nextValidRequestDate\":\"2026-01-27T17:48:54.791Z\"}} suffix");
        assertNotNull(result);
    }

    @Test
    public void testExtractNextValidRequestDate_MalformedJson() throws Exception {
        Long result = (Long) invokePrivate(client, "extractNextValidRequestDate", new Class<?>[]{String.class},
                "nextValidRequestDate {not valid json");
        assertNull(result);
    }

    @Test
    public void testEnforceRateLimit_SleepsWhenTooFast() throws Exception {
        Semaphore semaphore = new Semaphore(1);
        AtomicLong lastRequestTime = new AtomicLong(System.currentTimeMillis());
        long start = System.currentTimeMillis();
        invokePrivate(client, "enforceRateLimit",
                new Class<?>[]{Semaphore.class, AtomicLong.class, long.class},
                semaphore, lastRequestTime, 50L);
        assertTrue("Should have slept ~50ms", System.currentTimeMillis() - start >= 40);
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    public void testEnforceRateLimit_NoSleepWhenEnoughTimePassed() throws Exception {
        Semaphore semaphore = new Semaphore(1);
        AtomicLong lastRequestTime = new AtomicLong(System.currentTimeMillis() - 10_000);
        long start = System.currentTimeMillis();
        invokePrivate(client, "enforceRateLimit",
                new Class<?>[]{Semaphore.class, AtomicLong.class, long.class},
                semaphore, lastRequestTime, 50L);
        assertTrue("Should not have slept", System.currentTimeMillis() - start < 1000);
    }

    // ------------------------------------------------------------------
    // getTestDetailsGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testGetTestDetailsGraphQL_ValidResponseWithSteps() throws Exception {
        JSONObject test = testResult("TP-1");
        test.put("steps", new JSONArray().put(new JSONObject().put("id", "s1").put("action", "a")));
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(test));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());

        JSONObject result = client.getTestDetailsGraphQL("TP-1");
        assertNotNull(result);
        assertEquals("TP-1", result.getJSONObject("jira").getString("key"));
        assertEquals(1, result.getJSONArray("steps").length());
    }

    @Test
    public void testGetTestDetailsGraphQL_EmptyStepsArray() throws Exception {
        JSONObject test = testResult("TP-1");
        test.put("steps", new JSONArray());
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(test));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());

        JSONObject result = client.getTestDetailsGraphQL("TP-1");
        assertNotNull(result);
        assertEquals(0, result.getJSONArray("steps").length());
    }

    @Test
    public void testGetTestDetailsGraphQL_NoStepsField() throws Exception {
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(testResult("TP-1")));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());
        assertNotNull(client.getTestDetailsGraphQL("TP-1"));
    }

    @Test
    public void testGetTestDetailsGraphQL_EmptyResponse() throws Exception {
        doReturn("").when(client).executeGraphQL(anyString());
        assertNull(client.getTestDetailsGraphQL("TP-1"));
    }

    @Test
    public void testGetTestDetailsGraphQL_GraphQLErrors() throws Exception {
        String response = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "err"))).toString();
        doReturn(response).when(client).executeGraphQL(anyString());
        assertNull(client.getTestDetailsGraphQL("TP-1"));
    }

    @Test
    public void testGetTestDetailsGraphQL_NoGetTestsField() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject())).when(client).executeGraphQL(anyString());
        assertNull(client.getTestDetailsGraphQL("TP-1"));
    }

    @Test
    public void testGetTestDetailsGraphQL_NoResultsField() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", new JSONObject())))
                .when(client).executeGraphQL(anyString());
        assertNull(client.getTestDetailsGraphQL("TP-1"));
    }

    @Test
    public void testGetTestDetailsGraphQL_EmptyResults() throws Exception {
        JSONObject getTests = new JSONObject().put("results", new JSONArray());
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());
        assertNull(client.getTestDetailsGraphQL("TP-1"));
    }

    @Test
    public void testGetTestDetailsGraphQL_ExceptionWrapped() throws Exception {
        doThrow(new RuntimeException("boom")).when(client).executeGraphQL(anyString());
        try {
            client.getTestDetailsGraphQL("TP-1");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to get test details via GraphQL"));
        }
    }

    // ------------------------------------------------------------------
    // getTestStepsGraphQL() / getPreconditionsGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testGetTestStepsGraphQL_ReturnsSteps() throws Exception {
        JSONObject test = testResult("TP-1");
        test.put("steps", new JSONArray().put(new JSONObject().put("id", "s1")));
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(test));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());
        assertEquals(1, client.getTestStepsGraphQL("TP-1").length());
    }

    @Test
    public void testGetTestStepsGraphQL_NoTestDetails() throws Exception {
        doReturn("").when(client).executeGraphQL(anyString());
        assertEquals(0, client.getTestStepsGraphQL("TP-1").length());
    }

    @Test
    public void testGetPreconditionsGraphQL_ReturnsResults() throws Exception {
        JSONObject preconditions = new JSONObject()
                .put("results", new JSONArray().put(new JSONObject().put("issueId", "pc-1")));
        JSONObject test = testResult("TP-1");
        test.put("preconditions", preconditions);
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(test));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());
        assertEquals(1, client.getPreconditionsGraphQL("TP-1").length());
    }

    @Test
    public void testGetPreconditionsGraphQL_NoPreconditions() throws Exception {
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(testResult("TP-1")));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());
        assertEquals(0, client.getPreconditionsGraphQL("TP-1").length());
    }

    // ------------------------------------------------------------------
    // getPreconditionDetailsGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testGetPreconditionDetailsGraphQL_Valid() throws Exception {
        JSONObject precondition = testResult("TP-10");
        precondition.put("definition", "some definition");
        JSONObject getTests = new JSONObject().put("results", new JSONArray().put(precondition));
        doReturn(graphQlDataResponse(new JSONObject().put("getTests", getTests)))
                .when(client).executeGraphQL(anyString());

        JSONObject result = client.getPreconditionDetailsGraphQL("TP-10");
        assertNotNull(result);
        assertEquals("some definition", result.getString("definition"));
    }

    @Test
    public void testGetPreconditionDetailsGraphQL_EmptyResponseAndErrorsAndMissing() throws Exception {
        doReturn("").when(client).executeGraphQL(anyString());
        assertNull(client.getPreconditionDetailsGraphQL("TP-10"));

        String errorResponse = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "err"))).toString();
        doReturn(errorResponse).when(client).executeGraphQL(anyString());
        assertNull(client.getPreconditionDetailsGraphQL("TP-10"));

        doReturn(graphQlDataResponse(new JSONObject().put("getTests",
                        new JSONObject().put("results", new JSONArray()))))
                .when(client).executeGraphQL(anyString());
        assertNull(client.getPreconditionDetailsGraphQL("TP-10"));
    }

    @Test
    public void testGetPreconditionDetailsGraphQL_ExceptionWrapped() throws Exception {
        doThrow(new RuntimeException("boom")).when(client).executeGraphQL(anyString());
        try {
            client.getPreconditionDetailsGraphQL("TP-10");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to get precondition details via GraphQL"));
        }
    }

    // ------------------------------------------------------------------
    // addTestStepGraphQL() / addTestStepsGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testAddTestStepGraphQL_Valid() throws Exception {
        JSONObject step = new JSONObject().put("id", "step-1").put("action", "do it");
        doReturn(graphQlDataResponse(new JSONObject().put("addTestStep", step)))
                .when(client).executeGraphQL(anyString());

        JSONObject result = client.addTestStepGraphQL("TP-1", "action \"quoted\"", "data\nline", "result");
        assertNotNull(result);
        assertEquals("step-1", result.getString("id"));
    }

    @Test
    public void testAddTestStepGraphQL_NullInputsAndEmptyResponse() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject().put("addTestStep", new JSONObject())))
                .when(client).executeGraphQL(anyString());
        assertNotNull(client.addTestStepGraphQL(null, null, null, null));

        doReturn("").when(client).executeGraphQL(anyString());
        assertNull(client.addTestStepGraphQL("TP-1", "a", "b", "c"));
    }

    @Test
    public void testAddTestStepGraphQL_GraphQLErrorThrows() throws Exception {
        String response = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "mutation failed"))).toString();
        doReturn(response).when(client).executeGraphQL(anyString());
        try {
            client.addTestStepGraphQL("TP-1", "a", "b", "c");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("mutation failed"));
        }
    }

    @Test
    public void testAddTestStepGraphQL_NoDataField() throws Exception {
        doReturn(new JSONObject().toString()).when(client).executeGraphQL(anyString());
        assertNull(client.addTestStepGraphQL("TP-1", "a", "b", "c"));
    }

    @Test
    public void testAddTestStepsGraphQL_EmptyInput() throws Exception {
        assertEquals(0, client.addTestStepsGraphQL("TP-1", null).length());
        assertEquals(0, client.addTestStepsGraphQL("TP-1", new JSONArray()).length());
    }

    @Test
    public void testAddTestStepsGraphQL_PartialSuccess() throws Exception {
        JSONObject step = new JSONObject().put("id", "step-1");
        doReturn(graphQlDataResponse(new JSONObject().put("addTestStep", step)))
                .doThrow(new IOException("second step failed"))
                .when(client).executeGraphQL(anyString());

        JSONArray steps = new JSONArray()
                .put(new JSONObject().put("action", "a1"))
                .put(new JSONObject().put("action", "a2"));
        JSONArray result = client.addTestStepsGraphQL("TP-1", steps);
        assertEquals(1, result.length());
    }

    @Test
    public void testAddTestStepsGraphQL_AllFailThrows() throws Exception {
        doThrow(new IOException("always fails")).when(client).executeGraphQL(anyString());
        JSONArray steps = new JSONArray().put(new JSONObject().put("action", "a1"));
        try {
            client.addTestStepsGraphQL("TP-1", steps);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to add any steps"));
        }
    }

    // ------------------------------------------------------------------
    // addPreconditionToTestGraphQL() / addPreconditionsToTestGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testAddPreconditionToTestGraphQL_InvalidParams() throws Exception {
        assertNull(client.addPreconditionToTestGraphQL(null, "pc-1"));
        assertNull(client.addPreconditionToTestGraphQL("", "pc-1"));
        assertNull(client.addPreconditionToTestGraphQL("t-1", null));
        assertNull(client.addPreconditionToTestGraphQL("t-1", ""));
    }

    @Test
    public void testAddPreconditionToTestGraphQL_Valid() throws Exception {
        JSONObject mutationResult = new JSONObject().put("__typename", "Test");
        doReturn(graphQlDataResponse(new JSONObject().put("addPreconditionsToTest", mutationResult)))
                .when(client).executeGraphQL(anyString());
        assertNotNull(client.addPreconditionToTestGraphQL("t-1", "pc-1"));
    }

    @Test
    public void testAddPreconditionToTestGraphQL_EmptyResponseErrorAndNoData() throws Exception {
        doReturn("").when(client).executeGraphQL(anyString());
        assertNull(client.addPreconditionToTestGraphQL("t-1", "pc-1"));

        String errorResponse = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "pc failed"))).toString();
        doReturn(errorResponse).when(client).executeGraphQL(anyString());
        try {
            client.addPreconditionToTestGraphQL("t-1", "pc-1");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("pc failed"));
        }

        doReturn(new JSONObject().toString()).when(client).executeGraphQL(anyString());
        assertNull(client.addPreconditionToTestGraphQL("t-1", "pc-1"));
    }

    @Test
    public void testAddPreconditionsToTestGraphQL_EmptyInput() throws Exception {
        assertEquals(0, client.addPreconditionsToTestGraphQL("t-1", null).length());
        assertEquals(0, client.addPreconditionsToTestGraphQL("t-1", new JSONArray()).length());
    }

    @Test
    public void testAddPreconditionsToTestGraphQL_PartialSuccess() throws Exception {
        JSONObject mutationResult = new JSONObject().put("__typename", "Test");
        doReturn(graphQlDataResponse(new JSONObject().put("addPreconditionsToTest", mutationResult)))
                .when(client).executeGraphQL(contains("pc-ok"));
        doThrow(new IOException("pc failed")).when(client).executeGraphQL(contains("pc-bad"));

        JSONArray ids = new JSONArray().put("pc-ok").put("pc-bad");
        JSONArray result = client.addPreconditionsToTestGraphQL("t-1", ids);
        assertEquals(1, result.length());
    }

    // ------------------------------------------------------------------
    // updateCucumberTestGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testUpdateCucumberTestGraphQL_InvalidParams() throws Exception {
        assertNull(client.updateCucumberTestGraphQL(null, "gherkin"));
        assertNull(client.updateCucumberTestGraphQL("", "gherkin"));
        assertNull(client.updateCucumberTestGraphQL("12345", null));
        assertNull(client.updateCucumberTestGraphQL("12345", ""));
    }

    @Test
    public void testUpdateCucumberTestGraphQL_Success() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject().put("updateTestType", new JSONObject())))
                .when(client).executeGraphQL(contains("updateTestType"));
        doReturn(graphQlDataResponse(new JSONObject()
                        .put("updateGherkinTestDefinition", new JSONObject().put("issueId", "12345"))))
                .when(client).executeGraphQL(contains("updateGherkinTestDefinition"));

        JSONObject result = client.updateCucumberTestGraphQL("12345", "Feature: f\n  Scenario: s");
        assertNotNull(result);
        assertEquals("12345", result.getString("issueId"));
    }

    @Test
    public void testUpdateCucumberTestGraphQL_UpdateTypeFails() throws Exception {
        doThrow(new IOException("type update failed")).when(client).executeGraphQL(contains("updateTestType"));
        try {
            client.updateCucumberTestGraphQL("12345", "Feature: f");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("type update failed"));
        }
    }

    @Test
    public void testUpdateCucumberTestGraphQL_GherkinMutationErrorAndEmpty() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject().put("updateTestType", new JSONObject())))
                .when(client).executeGraphQL(contains("updateTestType"));

        String errorResponse = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "gherkin failed"))).toString();
        doReturn(errorResponse).when(client).executeGraphQL(contains("updateGherkinTestDefinition"));
        try {
            client.updateCucumberTestGraphQL("12345", "Feature: f");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("gherkin failed"));
        }

        doReturn("").when(client).executeGraphQL(contains("updateGherkinTestDefinition"));
        assertNull(client.updateCucumberTestGraphQL("12345", "Feature: f"));

        doReturn(new JSONObject().toString()).when(client).executeGraphQL(contains("updateGherkinTestDefinition"));
        assertNull(client.updateCucumberTestGraphQL("12345", "Feature: f"));
    }

    // ------------------------------------------------------------------
    // updateDatasetInternalAPI(jiraIssueId, testIssueId, dataset)
    // ------------------------------------------------------------------

    private JSONObject sampleDataset() {
        JSONArray listValues = new JSONArray().put("v1").put("v2");
        JSONObject param = new JSONObject()
                .put("name", "param1")
                .put("type", "list")
                .put("combinations", true)
                .put("listValues", listValues);
        JSONObject row = new JSONObject()
                .put("order", 0)
                .put("Values", new JSONArray().put("value1"));
        return new JSONObject()
                .put("parameters", new JSONArray().put(param))
                .put("rows", new JSONArray().put(row));
    }

    @Test
    public void testUpdateDatasetInternalAPI_InvalidParams() throws Exception {
        assertNull(client.updateDatasetInternalAPI(null, "tid", sampleDataset()));
        assertNull(client.updateDatasetInternalAPI("", "tid", sampleDataset()));
        assertNull(client.updateDatasetInternalAPI("jid", null, sampleDataset()));
        assertNull(client.updateDatasetInternalAPI("jid", "", sampleDataset()));
        assertNull(client.updateDatasetInternalAPI("jid", "tid", null));
        assertNull(client.updateDatasetInternalAPI("jid", "tid", new JSONObject()));
    }

    @Test
    public void testUpdateDatasetInternalAPI_Success() throws Exception {
        doReturn("{\"ok\":true}").when(client).put(any(GenericRequest.class));
        JSONObject result = client.updateDatasetInternalAPI("16201", "mongo-id", sampleDataset());
        assertNotNull(result);
        assertTrue(result.getBoolean("ok"));
    }

    @Test
    public void testUpdateDatasetInternalAPI_EmptyResponse() throws Exception {
        doReturn("").when(client).put(any(GenericRequest.class));
        assertNull(client.updateDatasetInternalAPI("16201", "mongo-id", sampleDataset()));
    }

    @Test
    public void testUpdateDatasetInternalAPI_PutFails() throws Exception {
        doThrow(new IOException("put failed")).when(client).put(any(GenericRequest.class));
        try {
            client.updateDatasetInternalAPI("16201", "mongo-id", sampleDataset());
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to update dataset via Internal API"));
        }
    }

    @Test
    public void testUpdateDatasetInternalAPI_NoRowsNoParams() throws Exception {
        doReturn("{\"ok\":true}").when(client).put(any(GenericRequest.class));
        JSONObject dataset = new JSONObject().put("something", "x");
        assertNotNull(client.updateDatasetInternalAPI("16201", "mongo-id", dataset));
    }

    // ------------------------------------------------------------------
    // updateDatasetGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testUpdateDatasetGraphQL_InvalidParams() throws Exception {
        assertNull(client.updateDatasetGraphQL(null, sampleDataset()));
        assertNull(client.updateDatasetGraphQL("", sampleDataset()));
        assertNull(client.updateDatasetGraphQL("12345", null));
        assertNull(client.updateDatasetGraphQL("12345", new JSONObject()));
    }

    @Test
    public void testUpdateDatasetGraphQL_Success() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject()
                        .put("updateTest", new JSONObject().put("issueId", "12345"))))
                .when(client).executeGraphQL(anyString());
        JSONObject result = client.updateDatasetGraphQL("12345", sampleDataset());
        assertNotNull(result);
        assertEquals("12345", result.getString("issueId"));
    }

    @Test
    public void testUpdateDatasetGraphQL_ErrorEmptyAndNoData() throws Exception {
        String errorResponse = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "dataset failed"))).toString();
        doReturn(errorResponse).when(client).executeGraphQL(anyString());
        try {
            client.updateDatasetGraphQL("12345", sampleDataset());
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("dataset failed"));
        }

        doReturn("").when(client).executeGraphQL(anyString());
        assertNull(client.updateDatasetGraphQL("12345", sampleDataset()));

        doReturn(new JSONObject().toString()).when(client).executeGraphQL(anyString());
        assertNull(client.updateDatasetGraphQL("12345", sampleDataset()));
    }

    @Test
    public void testUpdateDatasetGraphQL_ParamWithoutListValues() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject().put("updateTest", new JSONObject())))
                .when(client).executeGraphQL(anyString());
        JSONObject param = new JSONObject().put("name", "p").put("type", "text");
        JSONObject row = new JSONObject().put("order", 0)
                .put("Values", new JSONArray().put("v\"1\n"));
        JSONObject dataset = new JSONObject()
                .put("parameters", new JSONArray().put(param))
                .put("rows", new JSONArray().put(row));
        assertNotNull(client.updateDatasetGraphQL("12345", dataset));
    }

    // ------------------------------------------------------------------
    // setPreconditionDefinitionGraphQL()
    // ------------------------------------------------------------------

    @Test
    public void testSetPreconditionDefinitionGraphQL_InvalidParams() throws Exception {
        assertNull(client.setPreconditionDefinitionGraphQL(null, "def"));
        assertNull(client.setPreconditionDefinitionGraphQL("", "def"));
        assertNull(client.setPreconditionDefinitionGraphQL("12345", null));
        assertNull(client.setPreconditionDefinitionGraphQL("12345", ""));
    }

    @Test
    public void testSetPreconditionDefinitionGraphQL_Success() throws Exception {
        doReturn(graphQlDataResponse(new JSONObject()
                        .put("updatePrecondition", new JSONObject().put("issueId", "12345"))))
                .when(client).executeGraphQL(anyString());
        JSONObject result = client.setPreconditionDefinitionGraphQL("12345", "def \"quoted\"\nline2");
        assertNotNull(result);
        assertEquals("12345", result.getString("issueId"));
    }

    @Test
    public void testSetPreconditionDefinitionGraphQL_ErrorEmptyAndNoData() throws Exception {
        String errorResponse = new JSONObject()
                .put("errors", new JSONArray().put(new JSONObject().put("message", "def failed"))).toString();
        doReturn(errorResponse).when(client).executeGraphQL(anyString());
        try {
            client.setPreconditionDefinitionGraphQL("12345", "def");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("def failed"));
        }

        doReturn("").when(client).executeGraphQL(anyString());
        assertNull(client.setPreconditionDefinitionGraphQL("12345", "def"));

        doReturn(new JSONObject().toString()).when(client).executeGraphQL(anyString());
        assertNull(client.setPreconditionDefinitionGraphQL("12345", "def"));
    }

    // ------------------------------------------------------------------
    // getXacptTokenFromJiraPage() / extractTokenExpiry()
    // ------------------------------------------------------------------

    private String buildJwt(long expSeconds) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"exp\":" + expSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return "eyJ0eXAiOiJKV1QifQ." + payload + ".signature";
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testGetXacptTokenFromJiraPage_TokenFound() throws Exception {
        String jwt = buildJwt(1893456000L);
        String html = "<script>window.SSR_DATA = \"{\\\"contextJwt\\\": \\\"" + jwt + "\\\"}\";</script>";

        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn(html);

        String token = client.getXacptTokenFromJiraPage(jiraClient, "TP-1436");
        assertEquals(jwt, token);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testGetXacptTokenFromJiraPage_TokenNotFound() throws Exception {
        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn("<html>no token here</html>");

        assertNull(client.getXacptTokenFromJiraPage(jiraClient, "TP-1436"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testGetXacptTokenFromJiraPage_EmptyHtml() throws Exception {
        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn("");

        assertNull(client.getXacptTokenFromJiraPage(jiraClient, "TP-1436"));
    }

    @Test
    public void testExtractTokenExpiry_ValidAndInvalidJwt() throws Exception {
        String valid = buildJwt(1893456000L);
        String expiry = (String) invokePrivate(client, "extractTokenExpiry", new Class<?>[]{String.class}, valid);
        assertNotNull(expiry);
        assertNotEquals("unknown", expiry);

        String noExp = "a." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8)) + ".b";
        assertEquals("unknown", invokePrivate(client, "extractTokenExpiry", new Class<?>[]{String.class}, noExp));

        assertEquals("unknown", invokePrivate(client, "extractTokenExpiry", new Class<?>[]{String.class}, "not-a-jwt"));
        assertEquals("unknown", invokePrivate(client, "extractTokenExpiry", new Class<?>[]{String.class}, "a.!!!invalid!!!.b"));
    }

    // ------------------------------------------------------------------
    // getTestVersionId()
    // ------------------------------------------------------------------

    @Test
    public void testGetTestVersionId_InvalidParams() throws Exception {
        assertNull(client.getTestVersionId(null, "token"));
        assertNull(client.getTestVersionId("", "token"));
        assertNull(client.getTestVersionId("16201", null));
        assertNull(client.getTestVersionId("16201", ""));
    }

    @Test
    public void testGetTestVersionId_DefaultVersion() throws Exception {
        JSONObject version = new JSONObject()
                .put("isDefault", true)
                .put("testVersionId", "version-123")
                .put("testType", new JSONObject().put("value", "Cucumber"));
        String response = new JSONObject().put("16201", new JSONArray().put(version)).toString();
        doReturn(response).when(client).post(any(GenericRequest.class));

        assertEquals("version-123", client.getTestVersionId("16201", "xacpt-token"));
    }

    @Test
    public void testGetTestVersionId_NoDefaultUsesFirst() throws Exception {
        JSONObject version = new JSONObject().put("testVersionId", "version-first");
        String response = new JSONObject().put("16201", new JSONArray().put(version)).toString();
        doReturn(response).when(client).post(any(GenericRequest.class));

        assertEquals("version-first", client.getTestVersionId("16201", "xacpt-token"));
    }

    @Test
    public void testGetTestVersionId_EmptyResponseMissingIdAndEmptyVersions() throws Exception {
        doReturn("").when(client).post(any(GenericRequest.class));
        assertNull(client.getTestVersionId("16201", "xacpt-token"));

        doReturn(new JSONObject().put("99999", new JSONArray()).toString())
                .when(client).post(any(GenericRequest.class));
        assertNull(client.getTestVersionId("16201", "xacpt-token"));

        doReturn(new JSONObject().put("16201", new JSONArray()).toString())
                .when(client).post(any(GenericRequest.class));
        assertNull(client.getTestVersionId("16201", "xacpt-token"));
    }

    @Test
    public void testGetTestVersionId_PostFails() throws Exception {
        doThrow(new IOException("post failed")).when(client).post(any(GenericRequest.class));
        try {
            client.getTestVersionId("16201", "xacpt-token");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to get testVersionId"));
        }
    }

    // ------------------------------------------------------------------
    // transformDatasetForInternalAPI() / findExistingParameterId()
    // ------------------------------------------------------------------

    @Test
    public void testTransformDatasetForInternalAPI_NewAndExistingParams() throws Exception {
        JSONObject existingParam = new JSONObject().put("_id", "existing-id").put("name", "param1");
        JSONObject existingDataset = new JSONObject()
                .put("parameters", new JSONArray().put(existingParam));

        JSONObject result = (JSONObject) invokePrivate(client, "transformDatasetForInternalAPI",
                new Class<?>[]{JSONObject.class, JSONObject.class}, sampleDataset(), existingDataset);

        JSONArray params = result.getJSONObject("dataset").getJSONArray("parameters");
        assertEquals(1, params.length());
        assertEquals("existing-id", params.getJSONObject(0).getString("_id"));
        assertFalse(params.getJSONObject(0).has("isNew"));

        JSONArray rows = result.getJSONArray("datasetRows");
        assertEquals(1, rows.length());
        assertEquals("value1", rows.getJSONObject(0).getJSONObject("values").getString("existing-id"));
        assertEquals(1, result.getInt("iterationsCount"));
    }

    @Test
    public void testTransformDatasetForInternalAPI_NoExistingDataset() throws Exception {
        JSONObject result = (JSONObject) invokePrivate(client, "transformDatasetForInternalAPI",
                new Class<?>[]{JSONObject.class, JSONObject.class}, sampleDataset(), null);

        JSONObject param = result.getJSONObject("dataset").getJSONArray("parameters").getJSONObject(0);
        assertTrue(param.getBoolean("isNew"));
        assertNotNull(param.getString("_id"));
    }

    @Test
    public void testTransformDatasetForInternalAPI_NoRowsCreatesEmptyRow() throws Exception {
        JSONObject dataset = new JSONObject()
                .put("parameters", new JSONArray().put(new JSONObject().put("name", "p1")));
        JSONObject result = (JSONObject) invokePrivate(client, "transformDatasetForInternalAPI",
                new Class<?>[]{JSONObject.class, JSONObject.class}, dataset, new JSONObject());

        JSONArray rows = result.getJSONArray("datasetRows");
        assertEquals(1, rows.length());
        assertEquals(0, rows.getJSONObject(0).getInt("order"));
    }

    @Test
    public void testFindExistingParameterId() throws Exception {
        JSONObject existing = new JSONObject().put("parameters", new JSONArray()
                .put(new JSONObject().put("_id", "id-1").put("name", "p1")));

        assertEquals("id-1", invokePrivate(client, "findExistingParameterId",
                new Class<?>[]{String.class, JSONObject.class}, "p1", existing));
        assertNull(invokePrivate(client, "findExistingParameterId",
                new Class<?>[]{String.class, JSONObject.class}, "unknown", existing));
        assertNull(invokePrivate(client, "findExistingParameterId",
                new Class<?>[]{String.class, JSONObject.class}, "p1", null));
        assertNull(invokePrivate(client, "findExistingParameterId",
                new Class<?>[]{String.class, JSONObject.class}, "p1", new JSONObject()));
    }

    // ------------------------------------------------------------------
    // updateDatasetInternalAPI(jiraClient, testIssueKey, jiraIssueId, dataset)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("rawtypes")
    public void testUpdateDatasetInternalAPIWithJiraClient_InvalidParams() throws Exception {
        JiraClient jiraClient = mock(JiraClient.class);
        assertNull(client.updateDatasetInternalAPI(jiraClient, "TP-1", null, sampleDataset()));
        assertNull(client.updateDatasetInternalAPI(jiraClient, "TP-1", "", sampleDataset()));
        assertNull(client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", null));
        assertNull(client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", new JSONObject()));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testUpdateDatasetInternalAPIWithJiraClient_TokenExtractionFails() throws Exception {
        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn("<html>no token</html>");

        try {
            client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", sampleDataset());
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to extract X-acpt token"));
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testUpdateDatasetInternalAPIWithJiraClient_TestVersionIdFails() throws Exception {
        String jwt = buildJwt(1893456000L);
        String html = "<script>\"{\\\"contextJwt\\\": \\\"" + jwt + "\\\"}\"</script>";

        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn(html);
        doReturn(new JSONObject().toString()).when(client).post(any(GenericRequest.class));

        try {
            client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", sampleDataset());
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to get testVersionId"));
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testUpdateDatasetInternalAPIWithJiraClient_FullFlow() throws Exception {
        String jwt = buildJwt(1893456000L);
        String html = "<script>\"{\\\"contextJwt\\\": \\\"" + jwt + "\\\"}\"</script>";

        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn(html);

        // getTestVersionId
        JSONObject version = new JSONObject().put("isDefault", true).put("testVersionId", "ver-1");
        doReturn(new JSONObject().put("16201", new JSONArray().put(version)).toString())
                .when(client).post(any(GenericRequest.class));

        // GET existing dataset
        JSONObject existingParam = new JSONObject().put("_id", "existing-id").put("name", "param1");
        doReturn(new JSONObject().put("parameters", new JSONArray().put(existingParam)).toString())
                .when(client).execute(any(GenericRequest.class));

        // PUT dataset
        doReturn("{\"updated\":true}").when(client).put(any(GenericRequest.class));

        JSONObject result = client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", sampleDataset());
        assertNotNull(result);
        assertTrue(result.getBoolean("updated"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testUpdateDatasetInternalAPIWithJiraClient_GetFailsAndEmptyPut() throws Exception {
        String jwt = buildJwt(1893456000L);
        String html = "<script>\"{\\\"contextJwt\\\": \\\"" + jwt + "\\\"}\"</script>";

        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn(html);

        JSONObject version = new JSONObject().put("testVersionId", "ver-1");
        doReturn(new JSONObject().put("16201", new JSONArray().put(version)).toString())
                .when(client).post(any(GenericRequest.class));
        doThrow(new IOException("get failed")).when(client).execute(any(GenericRequest.class));
        doReturn("").when(client).put(any(GenericRequest.class));

        JSONObject result = client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", sampleDataset());
        assertNotNull(result);
        assertEquals(0, result.length());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testUpdateDatasetInternalAPIWithJiraClient_PutFails() throws Exception {
        String jwt = buildJwt(1893456000L);
        String html = "<script>\"{\\\"contextJwt\\\": \\\"" + jwt + "\\\"}\"</script>";

        JiraClient jiraClient = mock(JiraClient.class);
        when(jiraClient.getBasePath()).thenReturn("https://jira.example.com/");
        when(jiraClient.execute(any(GenericRequest.class))).thenReturn(html);

        JSONObject version = new JSONObject().put("testVersionId", "ver-1");
        doReturn(new JSONObject().put("16201", new JSONArray().put(version)).toString())
                .when(client).post(any(GenericRequest.class));
        doReturn("not json").when(client).execute(any(GenericRequest.class));
        doThrow(new IOException("put failed")).when(client).put(any(GenericRequest.class));

        try {
            client.updateDatasetInternalAPI(jiraClient, "TP-1", "16201", sampleDataset());
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to update dataset via Internal API"));
        }
    }
}
