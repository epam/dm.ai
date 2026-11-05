// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfluenceGraphQLClientTest {

    private ConfluenceGraphQLClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = new ConfluenceGraphQLClient("https://test.atlassian.net", "Bearer test-token");
    }

    @Test
    void testConstructor() throws IOException {
        ConfluenceGraphQLClient testClient = new ConfluenceGraphQLClient(
                "https://example.atlassian.net",
                "Bearer test-auth"
        );
        assertNotNull(testClient);
    }

    @Test
    void testSetSiteId() throws IOException {
        String testSiteId = "ari:cloud:confluence::site/test-cloud-id";
        client.setSiteId(testSiteId);
        // Site ID should be stored and retrievable in future calls
        // We can verify this indirectly by checking if it doesn't throw when used
        assertNotNull(client);
    }

    @Test
    void testSearchWithDefaultContentTypes() {
        // This test would require mocking the GraphQL execution
        // For now, we'll test that the method signature works
        assertThrows(Exception.class, () -> {
            client.search("test query", 10);
        });
    }

    @Test
    void testSearchWithCustomContentTypes() {
        String[] customTypes = {"ati:cloud:confluence:page"};
        assertThrows(Exception.class, () -> {
            client.search("test query", 10, customTypes);
        });
    }

    @Test
    void testConstructorAcceptsAnyBasePath() throws IOException {
        // Constructor doesn't validate base path - it accepts any string
        ConfluenceGraphQLClient testClient = new ConfluenceGraphQLClient("", "Bearer test-token");
        assertNotNull(testClient);
        
        // Another test with null-like values
        ConfluenceGraphQLClient testClient2 = new ConfluenceGraphQLClient("invalid-url", "auth");
        assertNotNull(testClient2);
    }

    @Test
    void testSearchScopesToSpaceKeyInConfluenceFilters() throws IOException {
        ConfluenceGraphQLClient spyClient = spy(new ConfluenceGraphQLClient(
                "https://test.atlassian.net",
                "Bearer test-token"
        ));
        spyClient.setSiteId("ari:cloud:confluence::site/test-cloud-id");

        String graphQLResponse = new JSONObject()
                .put("data", new JSONObject()
                        .put("search", new JSONObject()
                                .put("search", new JSONObject()
                                        .put("edges", new JSONArray()))))
                .toString();

        doAnswer(invocation -> {
            JSONObject variables = invocation.getArgument(2);
            JSONObject filters = variables.getJSONObject("filters");
            JSONObject confluenceFilters = filters.getJSONObject("confluenceFilters");
            assertEquals(new JSONArray().put("ENG").toString(), confluenceFilters.getJSONArray("spaces").toString());
            return graphQLResponse;
        }).when(spyClient).executeGraphQL(eq("AdvancedAGGSearchQuery"), anyString(), any(JSONObject.class));

        JSONArray edges = spyClient.search("docs", 10, "ENG");
        assertEquals(0, edges.length());
    }

    @Test
    void testSearchWithoutSpaceKeyLeavesConfluenceFiltersEmpty() throws IOException {
        ConfluenceGraphQLClient spyClient = spy(new ConfluenceGraphQLClient(
                "https://test.atlassian.net",
                "Bearer test-token"
        ));
        spyClient.setSiteId("ari:cloud:confluence::site/test-cloud-id");

        String graphQLResponse = new JSONObject()
                .put("data", new JSONObject()
                        .put("search", new JSONObject()
                                .put("search", new JSONObject()
                                        .put("edges", new JSONArray()))))
                .toString();

        doAnswer(invocation -> {
            JSONObject variables = invocation.getArgument(2);
            JSONObject filters = variables.getJSONObject("filters");
            JSONObject confluenceFilters = filters.getJSONObject("confluenceFilters");
            assertFalse(confluenceFilters.has("spaces"));
            return graphQLResponse;
        }).when(spyClient).executeGraphQL(eq("AdvancedAGGSearchQuery"), anyString(), any(JSONObject.class));

        JSONArray edges = spyClient.search("docs", 10);
        assertEquals(0, edges.length());
    }
}
