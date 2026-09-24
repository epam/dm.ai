// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for the Confluence v2 REST API path (CONFLUENCE_API_VERSION=v2), which is
 * required when authenticating with Atlassian granular/scoped API tokens — the
 * legacy v1 content endpoints return 401 scope-mismatch under such tokens.
 */
public class ConfluenceApiV2Test {

    private Confluence confluence;

    @Before
    public void setUp() throws IOException {
        confluence = Mockito.spy(new Confluence("http://example.com", "auth"));
    }

    @After
    public void tearDown() {
        PropertyReader.clearOverrides();
    }

    // ------------------------------------------------------------------
    // apiVersion flag + path building
    // ------------------------------------------------------------------

    @Test
    public void testApiVersionDefaultsToV1() {
        assertEquals("v1", confluence.getApiVersion());
        assertFalse(confluence.isApiV2());
    }

    @Test
    public void testApiVersionV2EnablesV2() {
        confluence.setApiVersion("v2");
        assertTrue(confluence.isApiV2());
    }

    @Test
    public void testApiVersionCaseInsensitive() {
        confluence.setApiVersion("V2");
        assertTrue(confluence.isApiV2());
    }

    @Test
    public void testPathV2() {
        assertEquals("http://example.com/wiki/api/v2/pages/123", confluence.pathV2("pages/123"));
    }

    @Test
    public void testPathStillV1ByDefault() {
        assertEquals("http://example.com/rest/api/content/123", confluence.path("content/123"));
    }

    // ------------------------------------------------------------------
    // contentById
    // ------------------------------------------------------------------

    private String buildPageJson(String storageValue) {
        return new JSONObject()
            .put("id", "123")
            .put("title", "Test Page")
            .put("body", new JSONObject()
                .put("storage", new JSONObject()
                    .put("value", storageValue)
                    .put("representation", "storage")))
            .toString();
    }

    @Test
    public void testContentByIdV2_usesV2PagesEndpoint() throws IOException {
        confluence.setApiVersion("v2");
        doReturn(buildPageJson("<p>Hi</p>")).when(confluence).execute(any(GenericRequest.class));

        Content result = confluence.contentById("123", null);

        assertNotNull(result);
        assertEquals("<p>Hi</p>", result.getStorage().getValue());

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(confluence, times(1)).execute(captor.capture());
        assertEquals("http://example.com/wiki/api/v2/pages/123?body-format=storage",
                captor.getValue().url());
    }

    @Test
    public void testContentByIdV1_keepsLegacyEndpoint() throws IOException {
        doReturn(buildPageJson("<p>Hi</p>")).when(confluence).execute(any(GenericRequest.class));

        confluence.contentById("123", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(confluence, times(1)).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/rest/api/content/123"));
        assertTrue(captor.getValue().url().contains("expand=body.storage"));
    }

    // ------------------------------------------------------------------
    // getChildrenOfContentById
    // ------------------------------------------------------------------

    @Test
    public void testGetChildrenV2_usesParentIdQuery() throws IOException {
        confluence.setApiVersion("v2");
        String response = new JSONObject()
            .put("results", new org.json.JSONArray()
                .put(new JSONObject(buildPageJson("<p>child</p>"))))
            .toString();
        doReturn(response).when(confluence).execute(any(GenericRequest.class));

        List<Content> children = confluence.getChildrenOfContentById("999", null);

        assertEquals(1, children.size());

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(confluence, times(1)).execute(captor.capture());
        String url = captor.getValue().url();
        assertTrue(url.startsWith("http://example.com/wiki/api/v2/pages?"));
        assertTrue(url.contains("parent-id=999"));
    }

    @Test
    public void testGetChildrenV1_keepsLegacyChildPageEndpoint() throws IOException {
        String response = new JSONObject()
            .put("results", new org.json.JSONArray()
                .put(new JSONObject(buildPageJson("<p>child</p>"))))
            .toString();
        doReturn(response).when(confluence).execute(any(GenericRequest.class));

        confluence.getChildrenOfContentById("999", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(confluence, times(1)).execute(captor.capture());
        assertTrue(captor.getValue().url().contains("/rest/api/content/999/child/page"));
    }

    // ------------------------------------------------------------------
    // testConnection health-check fallback (user/current 401s under
    // granular/scoped tokens; space listing works)
    // ------------------------------------------------------------------

    @Test
    public void testConnectionFallsBackToSpaceListingWhenProfile401() throws IOException {
        doThrow(new IOException("401 scope does not match")).when(confluence).profile();
        doReturn(new JSONObject().put("results", new org.json.JSONArray()
                .put(new JSONObject().put("key", "PROJ"))).toString())
            .when(confluence).execute(any(GenericRequest.class));

        Map<String, Object> result = confluence.testConnection();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("scoped-token", result.get("user"));
        assertEquals(1, result.get("spacesVisible"));
    }

    @Test
    public void testConnectionSuccessViaProfile() throws IOException {
        doReturn(new JSONObject().put("displayName", "Jane").put("email", "j@x.com").toString())
            .when(confluence).profile();

        Map<String, Object> result = confluence.testConnection();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("Jane", result.get("user"));
    }

    @Test
    public void testConnectionFailsWhenBothProfileAndSpacesFail() throws IOException {
        doThrow(new IOException("401 scope does not match")).when(confluence).profile();
        doThrow(new IOException("connection refused")).when(confluence).execute(any(GenericRequest.class));

        Map<String, Object> result = confluence.testConnection();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(String.valueOf(result.get("message")).contains("connection refused"));
    }

    // ------------------------------------------------------------------
    // PropertyReader flag
    // ------------------------------------------------------------------

    @Test
    public void testConfluenceApiVersionDefaultsToV1() {
        PropertyReader.setOverrides(Map.of());
        try {
            // With no override set and no env, default is v1
            PropertyReader.clearOverrides();
            assertEquals("v1", new PropertyReader().getConfluenceApiVersion());
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    public void testConfluenceApiVersionReadsV2() {
        PropertyReader.setOverrides(Map.of(PropertyReader.CONFLUENCE_API_VERSION, "v2"));
        try {
            assertEquals("v2", new PropertyReader().getConfluenceApiVersion());
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    public void testConfluenceApiVersionCaseNormalized() {
        PropertyReader.setOverrides(Map.of(PropertyReader.CONFLUENCE_API_VERSION, " V2 "));
        try {
            assertEquals("v2", new PropertyReader().getConfluenceApiVersion());
        } finally {
            PropertyReader.clearOverrides();
        }
    }
}
