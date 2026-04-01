// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.graphql.GraphQLClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

public class ConfluenceGraphQLClient extends GraphQLClient {
    private static final Logger logger = LogManager.getLogger(ConfluenceGraphQLClient.class);

    private String siteId;
    private static final String CLOUD_ID_QUERY = """
        query GetCloudId($hostNames: [String!]!) {
          tenantContexts(hostNames: $hostNames) {
            cloudId
          }
        }
    """;

    public ConfluenceGraphQLClient(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    /**
     * Extracts hostname from the base path
     */
    private String getHostname() throws IOException {
        try {
            URI uri = new URI(getBasePath());
            return uri.getHost();
        } catch (Exception e) {
            throw new IOException("Failed to extract hostname from base path: " + getBasePath(), e);
        }
    }

    /**
     * Fetches the cloud ID and constructs the site ID
     */
    private String getSiteId() throws IOException {
        if (siteId != null) {
            return siteId;
        }

        // Create variables for the query
        JSONObject variables = new JSONObject()
                .put("hostNames", new JSONArray().put(getHostname()));

        // Execute the query to get cloud ID
        String response = executeGraphQL("GetCloudId", CLOUD_ID_QUERY, variables);
        JSONObject data = parseGraphQLResponse(response);

        try {
            String cloudId = data.getJSONArray("tenantContexts")
                    .getJSONObject(0)
                    .getString("cloudId");

            // Construct the site ID using the cloud ID
            siteId = String.format("ari:cloud:confluence::site/%s", cloudId);

            logger.debug("Retrieved site ID: {}", siteId);
            return siteId;
        } catch (Exception e) {
            logger.error("Failed to parse cloud ID from response: " + response, e);
            throw new IOException("Unable to determine site ID", e);
        }
    }

    /**
     * Creates search filters with dynamic site ID
     */
    private JSONObject createSearchFilters(String[] contentTypes) throws IOException {
        return new JSONObject()
                .put("entities", new JSONArray(contentTypes))
                .put("locations", new JSONArray().put(getSiteId()))
                .put("confluenceFilters", new JSONObject());
    }

    /**
     * Creates experiment context for search
     */
    private JSONObject createExperimentContext() {
        return new JSONObject()
                .put("experimentId", "default_default___QR-cloudberry")
                .put("shadowExperimentId", "____")
                .put("experimentLayers", new JSONArray()
                        .put(createExperimentLayer("search_platform_layer_l1", "default", ""))
                        .put(createExperimentLayer("search_platform_layer_l2", "default", ""))
                        .put(createExperimentLayer("search_platform_layer_l2_blending", null, null))
                        .put(createExperimentLayer("search_platform_layer_l3", "", null))
                        .put(createExperimentLayer("search_platform_layer_query_expansion", "QR-cloudberry", null)));
    }

    private JSONObject createExperimentLayer(String name, String layerId, String shadowId) {
        JSONObject layer = new JSONObject()
                .put("name", name)
                .put("layerId", layerId == null ? JSONObject.NULL : layerId)
                .put("shadowId", shadowId == null ? JSONObject.NULL : shadowId);
        return layer;
    }

    /**
     * Performs an advanced search in Confluence
     */
    public JSONArray search(String searchTerm, int limit) throws IOException {
        return search(searchTerm, limit, new String[]{
                "ati:cloud:confluence:page",
                "ati:cloud:confluence:attachment",
                "ati:cloud:confluence:blogpost"
        });
    }

    /**
     * Performs an advanced search with specific content types
     */
    public JSONArray search(String searchTerm, int limit, String[] contentTypes) throws IOException {
        String query = ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY;

        JSONObject variables = new JSONObject()
                .put("experience", "confluence.advancedSearch")
                .put("analytics", new JSONObject()
                        .put("searchSessionId", UUID.randomUUID().toString()))
                .put("query", searchTerm)
                .put("first", limit)
                .put("after", JSONObject.NULL)
                .put("filters", createSearchFilters(contentTypes))
                .put("experimentContext", createExperimentContext())
                .put("isLivePagesEnabled", false);

        String response = executeGraphQL("AdvancedAGGSearchQuery", query, variables);
        return parseSearchResults(response);
    }

    /**
     * Parses search results into a JSONArray
     */
    private JSONArray parseSearchResults(String response) throws IOException {
        JSONObject data = parseGraphQLResponse(response);
        return data.getJSONObject("search")
                .getJSONObject("search")
                .getJSONArray("edges");
    }

    /**
     * Override the site ID if needed (mainly for testing)
     */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }
}