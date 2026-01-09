package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * REST client for X-ray Cloud API communication.
 * Handles authentication and API requests to X-ray Cloud.
 * 
 * <p>
 * X-ray Cloud uses a proprietary authentication mechanism:
 * - POST client_id and client_secret to /authenticate endpoint
 * - Receive bearer token as plain string response
 * - Use bearer token for subsequent API requests
 * </p>
 */
public class XrayRestClient extends AbstractRestClient {

    private static final Logger logger = LogManager.getLogger(XrayRestClient.class);

    private final String clientId;
    private final String clientSecret;
    
    // Bearer token management
    private volatile String accessToken;
    private volatile long tokenExpiryTime;
    private static final long TOKEN_REFRESH_BUFFER_MS = 60000; // Refresh 1 minute before expiry
    
    // Pagination limit override (default 100, can be set for testing)
    private volatile int paginationLimit = 100;

    /**
     * Creates a new XrayRestClient instance.
     * 
     * @param basePath X-ray API base path (e.g., "https://xray.cloud.getxray.app/api/v2")
     * @param clientId X-ray client ID
     * @param clientSecret X-ray client secret
     * @throws IOException if initialization fails
     */
    public XrayRestClient(String basePath, String clientId, String clientSecret) throws IOException {
        // X-ray doesn't use standard authorization header for base auth
        // We'll use bearer token after authentication
        super(basePath, "");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        
        // Read cache configuration from properties (default: false for both GET and POST)
        PropertyReader propertyReader = new PropertyReader();
        boolean cacheGetRequestsEnabled = propertyReader.isXrayCacheGetRequestsEnabled();
        boolean cachePostRequestsEnabled = propertyReader.isXrayCachePostRequestsEnabled();
        
        setCacheGetRequestsEnabled(cacheGetRequestsEnabled);
        setCachePostRequestsEnabled(cachePostRequestsEnabled);
        
        if (cacheGetRequestsEnabled) {
            logger.info("X-ray GET request caching is enabled (via XRAY_CACHE_GET_REQUESTS_ENABLED property)");
        } else {
            logger.debug("X-ray GET request caching is disabled (default)");
        }
        
        if (cachePostRequestsEnabled) {
            logger.info("X-ray POST/GraphQL request caching is enabled (via XRAY_CACHE_POST_REQUESTS_ENABLED property)");
        } else {
            logger.debug("X-ray POST/GraphQL request caching is disabled (default)");
        }
    }

    @Override
    public String path(String path) {
        // X-ray API paths: if path already includes /api/v2, use it as-is
        // Otherwise, construct from base domain
        if (path.startsWith("http")) {
            // Already a full URL
            return path;
        }
        
        // Extract base domain from basePath (remove /api/v2 if present)
        String baseDomain = basePath;
        if (baseDomain.contains("/api/v2")) {
            baseDomain = baseDomain.replace("/api/v2", "");
        }
        // Normalize trailing slashes - ensure baseDomain doesn't end with / (we'll add it)
        baseDomain = baseDomain.replaceAll("/+$", "");
        
        // If path starts with /api/v2, append to base domain
        if (path.startsWith("/api/v2") || path.startsWith("api/v2")) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return baseDomain + path;
        }
        
        // Otherwise, append to basePath (normalize to avoid double slashes)
        String normalizedBasePath = basePath.replaceAll("/+$", "");
        if (path.startsWith("/")) {
            return normalizedBasePath + path;
        }
        return normalizedBasePath + "/" + path;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        // Add bearer token authorization
        // getAccessToken() can throw IOException, but we need to handle it gracefully
        // since sign() method signature doesn't allow throwing exceptions
        try {
            String token = getAccessToken();
            if (token != null && !token.isEmpty()) {
                // Trim token to remove any whitespace (X-ray returns token as plain string)
                token = token.trim();
                logger.debug("Adding Bearer token to request (token length: {}, first 20 chars: {}...)", 
                        token.length(), token.length() > 20 ? token.substring(0, 20) : token);
                builder.header("Authorization", "Bearer " + token);
            } else {
                logger.warn("No access token available for signing request");
            }
        } catch (RuntimeException e) {
            // getAccessToken() throws RuntimeException if token acquisition fails
            logger.error("Failed to get access token for signing request", e);
        }
        builder.header("Content-Type", "application/json");
        return builder;
    }

    @Override
    protected String getCacheFolderName() {
        return "cacheXrayRestClient";
    }

    /**
     * Obtains or refreshes the X-ray bearer access token.
     * 
     * @return Access token for X-ray API calls
     * @throws IOException if token acquisition fails
     */
    private synchronized String getAccessToken() {
        // Check if we have a valid token
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime - TOKEN_REFRESH_BUFFER_MS) {
            return accessToken;
        }

        try {
            // Request new token
            // X-ray Cloud authenticate endpoint is at /api/v2/authenticate
            // If basePath is "https://eu.xray.cloud.getxray.app/" or "https://eu.xray.cloud.getxray.app/api/v2",
            // authenticate should be at "https://eu.xray.cloud.getxray.app/api/v2/authenticate"
            String authenticateUrl = basePath;
            
            // Normalize basePath - remove trailing slashes
            authenticateUrl = authenticateUrl.replaceAll("/+$", "");
            
            // Ensure we have /api/v2/authenticate
            if (authenticateUrl.endsWith("/api/v2")) {
                // Already has /api/v2, just add /authenticate
                authenticateUrl += "/authenticate";
            } else if (!authenticateUrl.contains("/api/v2")) {
                // Add /api/v2/authenticate
                if (!authenticateUrl.endsWith("/")) {
                    authenticateUrl += "/";
                }
                authenticateUrl += "api/v2/authenticate";
            } else {
                // Has /api/v2 somewhere, replace with /api/v2/authenticate
                authenticateUrl = authenticateUrl.replaceAll("/api/v2.*$", "") + "/api/v2/authenticate";
            }

            logger.debug("Authenticating with X-ray at: {}", authenticateUrl);

            // Prepare request body as JSON
            JSONObject json = new JSONObject();
            json.put("client_id", clientId);
            json.put("client_secret", clientSecret);
            String requestBody = json.toString();

            // Create POST request directly (bypass GenericRequest for authentication)
            RequestBody body = RequestBody.create(requestBody, JSON);
            Request request = new Request.Builder()
                    .url(authenticateUrl)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    logger.error("X-ray authentication failed. HTTP {}: {}", response.code(), responseBody);
                    throw new IOException("Failed to obtain X-ray access token. HTTP " + response.code() + ": " + 
                            (responseBody.isEmpty() ? "No error body" : responseBody));
                }

                // X-ray returns the token as a plain string; expiry is assumed to be 1 hour (3600 seconds)
                // Remove any surrounding quotes and whitespace
                accessToken = responseBody.trim();
                // Remove surrounding quotes if present (JSON string format)
                if (accessToken.startsWith("\"") && accessToken.endsWith("\"")) {
                    accessToken = accessToken.substring(1, accessToken.length() - 1);
                }
                long expiresIn = 3600; // Default to 1 hour
                tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000);

                logger.debug("Successfully obtained X-ray access token (length: {}), expires in {} seconds", 
                        accessToken.length(), expiresIn);
                return accessToken;
            }

        } catch (Exception e) {
            logger.error("Error obtaining X-ray access token", e);
            throw new RuntimeException("Failed to obtain X-ray access token: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a GraphQL query against X-ray API.
     * 
     * @param query GraphQL query string
     * @return Response body as string
     * @throws IOException if request fails
     */
    public String executeGraphQL(String query) throws IOException {
        return executeGraphQL(query, false);
    }

    /**
     * Executes a GraphQL query against X-ray API.
     * 
     * @param query GraphQL query string
     * @param ignoreCache If true, bypass cache for this request
     * @return Response body as string
     * @throws IOException if request fails
     */
    public String executeGraphQL(String query, boolean ignoreCache) throws IOException {
        // Ensure we have a valid token
        getAccessToken();
        
        // GraphQL endpoint is at /api/v2/graphql
        // Extract base domain from basePath (remove /api/v2 if present)
        String baseDomain = basePath;
        if (baseDomain.contains("/api/v2")) {
            baseDomain = baseDomain.replace("/api/v2", "");
        }
        // Normalize trailing slashes
        baseDomain = baseDomain.replaceAll("/+$", "");
        
        // Build GraphQL URL - ensure we use the correct base domain
        String graphqlUrl = baseDomain + "/api/v2/graphql";
        logger.debug("Executing GraphQL query at: {} (basePath was: {})", graphqlUrl, basePath);
        
        // Build GraphQL request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("query", query);
        
        logger.debug("GraphQL query: {}", query);
        
        GenericRequest request = new GenericRequest(this, graphqlUrl);
        request.setBody(requestBody.toString());
        request.setIgnoreCache(ignoreCache);
        
        // Retry logic for 503 errors
        int maxRetries = 3;
        int retryCount = 0;
        IOException lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                return post(request);
            } catch (IOException e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                
                // Retry on 503 errors
                if ((errorMsg.contains("503") || errorMsg.contains("backup")) && retryCount < maxRetries - 1) {
                    retryCount++;
                    long waitTime = 1000L * retryCount;
                    logger.warn("X-ray GraphQL API returned 503, retrying in {}ms (attempt {}/{})", 
                            waitTime, retryCount, maxRetries);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted during retry", ie);
                    }
                    continue;
                }
                throw e;
            }
        }
        
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("GraphQL request failed after " + maxRetries + " attempts");
    }

    /**
     * Makes an authenticated request to X-ray API.
     * 
     * @param endpoint X-ray API endpoint (relative to basePath, e.g., "test/TP-123/steps")
     * @param method HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param body Request body (can be null for GET/DELETE)
     * @return Response body as string
     * @throws IOException if request fails
     */
    public String xrayRequest(String endpoint, String method, String body) throws IOException {
        // Ensure we have a valid token
        getAccessToken();
        
        // Build the full path
        String fullPath = path(endpoint);
        
        GenericRequest request = new GenericRequest(this, fullPath);
        
        if (body != null) {
            request.setBody(body);
        }

        // Retry logic for 503 errors (service unavailable)
        int maxRetries = 3;
        int retryCount = 0;
        IOException lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                switch (method.toUpperCase()) {
                    case "GET":
                        return execute(request);
                    case "POST":
                        return post(request);
                    case "PUT":
                        return put(request);
                    case "PATCH":
                        return patch(request);
                    case "DELETE":
                        return delete(request);
                    default:
                        throw new IllegalArgumentException("Unsupported HTTP method: " + method);
                }
            } catch (IOException e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                
                // Retry on 503 errors (service unavailable) or "backup" errors
                if ((errorMsg.contains("503") || errorMsg.contains("backup")) && retryCount < maxRetries - 1) {
                    retryCount++;
                    long waitTime = 1000L * retryCount; // 1s, 2s, 3s
                    logger.warn("X-ray API returned 503 (service unavailable), retrying in {}ms (attempt {}/{})", 
                            waitTime, retryCount, maxRetries);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted during retry", ie);
                    }
                    continue;
                }
                
                // For other errors or max retries reached, throw the exception
                logger.error("X-ray API request failed: {} {} (attempt {}/{})", method, endpoint, retryCount + 1, maxRetries, e);
                throw e;
            }
        }
        
        // Should never reach here, but just in case
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("X-ray API request failed after " + maxRetries + " attempts");
    }

    /**
     * Sets the pagination limit for GraphQL queries.
     * This allows overriding the default limit of 100 for testing purposes.
     * 
     * @param limit Maximum number of results per page (1-100)
     */
    public void setPaginationLimit(int limit) {
        this.paginationLimit = Math.min(100, Math.max(1, limit));
        logger.debug("Pagination limit set to: {}", this.paginationLimit);
    }
    
    /**
     * Gets the current pagination limit.
     * 
     * @return Current pagination limit (1-100)
     */
    public int getPaginationLimit() {
        return paginationLimit;
    }
    
    /**
     * Gets all test details with steps and preconditions using X-ray GraphQL API by JQL query.
     * This is more efficient than calling getTestDetailsGraphQL for each ticket individually.
     * Uses the default pagination limit if not specified.
     * 
     * @param jqlQuery JQL query string (e.g., "project = TP AND issuetype = Test")
     * @return JSONArray of test details including steps and preconditions, or empty array if none found
     * @throws IOException if API call fails
     */
    public JSONArray getTestsByJQLGraphQL(String jqlQuery) throws IOException {
        return getTestsByJQLGraphQL(jqlQuery, paginationLimit, null);
    }
    
    /**
     * Gets all test details with steps and preconditions using X-ray GraphQL API by JQL query.
     * This is more efficient than calling getTestDetailsGraphQL for each ticket individually.
     * 
     * @param jqlQuery JQL query string (e.g., "project = TP AND issuetype = Test")
     * @param limit Maximum number of results to return (1-100)
     * @return JSONArray of test details including steps and preconditions, or empty array if none found
     * @throws IOException if API call fails
     */
    public JSONArray getTestsByJQLGraphQL(String jqlQuery, int limit) throws IOException {
        return getTestsByJQLGraphQL(jqlQuery, limit, null);
    }
    
    /**
     * Gets test details with steps and preconditions using X-ray GraphQL API by JQL query with pagination support.
     * 
     * @param jqlQuery JQL query string (e.g., "project = TP AND issuetype = Test")
     * @param limit Maximum number of results per page (1-100)
     * @param after Cursor for pagination (null for first page)
     * @return JSONObject with results array and pageInfo, or null if error
     * @throws IOException if API call fails
     */
    private JSONObject getTestsByJQLGraphQLWithPagination(String jqlQuery, int limit) throws IOException {
        // GraphQL query to get all tests matching JQL with steps and preconditions
        // Escape quotes in JQL query for GraphQL
        // Note: Xray GraphQL API doesn't support 'after' parameter for getTests
        String escapedJQL = jqlQuery.replace("\"", "\\\"");
        
        String query = String.format(
            "query { " +
            "  getTests(jql: \"%s\", limit: %d) { " +
            "    results { " +
            "      issueId " +
            "      projectId " +
            "      jira(fields: [\"key\", \"summary\", \"description\"]) " +
            "      testType { name } " +
            "      folder { path } " +
            "      steps { " +
            "        id " +
            "        action " +
            "        data " +
            "        result " +
            "        attachments { " +
            "          id " +
            "          filename " +
            "          downloadLink " +
            "        } " +
            "        customFields { " +
            "          id " +
            "          name " +
            "          value " +
            "        } " +
            "      } " +
            "      scenarioType " +
            "      gherkin " +
            "      unstructured " +
            "      dataset { " +
            "        parameters { " +
            "          name " +
            "          type " +
            "          listValues " +
            "        } " +
            "        rows { " +
            "          order " +
            "          Values " +
            "        } " +
            "      } " +
            "      preconditions(limit: 10) { " +
            "        total " +
            "        results { " +
            "          issueId " +
            "          definition " +
            "          jira(fields: [\"key\", \"summary\"]) " +
            "        } " +
            "      } " +
            "    } " +
            "  } " +
            "}",
            escapedJQL, limit
        );

        try {
            String response = executeGraphQL(query);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = errors.length() > 0 ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL query returned errors for JQL {}: {}", jqlQuery, errorMessage);
                return null;
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject data = responseJson.getJSONObject("data");
                if (data.has("getTests")) {
                    return data.getJSONObject("getTests");
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL query for JQL {}", jqlQuery, e);
            throw new IOException("Failed to get tests via GraphQL: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets all test details with steps and preconditions using X-ray GraphQL API by JQL query.
     * Automatically handles pagination to fetch all results.
     * 
     * Note: Xray GraphQL API doesn't support cursor-based pagination (after parameter) for getTests.
     * This method uses a workaround: it makes multiple requests with modified JQL queries
     * to exclude already fetched keys, effectively implementing pagination.
     * 
     * @param jqlQuery JQL query string (e.g., "project = TP AND issuetype = Test")
     * @param limit Maximum number of results per page (1-100, default 100)
     * @param after Not used (Xray doesn't support after parameter) - kept for API compatibility
     * @return JSONArray of test details including steps and preconditions, or empty array if none found
     * @throws IOException if API call fails
     */
    public JSONArray getTestsByJQLGraphQL(String jqlQuery, int limit, String after) throws IOException {
        JSONArray allResults = new JSONArray();
        int pageLimit = Math.min(100, Math.max(1, limit)); // Ensure limit is between 1 and 100
        String lastKey = null; // Track last fetched key for cursor-based pagination
        int maxIterations = 1000; // Safety limit to prevent infinite loops
        int iteration = 0;
        
        while (iteration < maxIterations) {
            iteration++;
            
            // Build JQL query with cursor-based pagination using key > lastKey
            String currentJQL = jqlQuery;
            if (lastKey != null) {
                // Escape any quotes in the key value
                String escapedKey = lastKey.replace("\"", "\\\"");
                currentJQL = jqlQuery + " AND key > \"" + escapedKey + "\"";
            }
            // Add ORDER BY key ASC to ensure consistent ordering and enable cursor pagination
            currentJQL += " ORDER BY key ASC";
            
            JSONObject pageData = getTestsByJQLGraphQLWithPagination(currentJQL, pageLimit);
            if (pageData == null) {
                break;
            }
            
            // Extract results from this page
            JSONArray pageResults = null;
            if (pageData.has("results")) {
                pageResults = pageData.getJSONArray("results");
                
                // Xray GraphQL API returns results in DESC order despite ORDER BY key ASC
                // Reverse the array to get ASC order for proper pagination
                if (pageResults.length() > 1) {
                    JSONObject firstResult = pageResults.getJSONObject(0);
                    JSONObject lastResult = pageResults.getJSONObject(pageResults.length() - 1);
                    String firstKeyBeforeReverse = null;
                    String lastKeyBeforeReverse = null;
                    if (firstResult.has("jira") && firstResult.getJSONObject("jira").has("key")) {
                        firstKeyBeforeReverse = firstResult.getJSONObject("jira").getString("key");
                    }
                    if (lastResult.has("jira") && lastResult.getJSONObject("jira").has("key")) {
                        lastKeyBeforeReverse = lastResult.getJSONObject("jira").getString("key");
                    }
                    
                    // If results are in DESC order (first > last), reverse the array
                    if (firstKeyBeforeReverse != null && lastKeyBeforeReverse != null && firstKeyBeforeReverse.compareTo(lastKeyBeforeReverse) > 0) {
                        logger.debug("Reversing results array (Xray returns DESC order despite ORDER BY ASC). Before reverse - First: {}, Last: {}", firstKeyBeforeReverse, lastKeyBeforeReverse);
                        JSONArray reversedResults = new JSONArray();
                        for (int i = pageResults.length() - 1; i >= 0; i--) {
                            reversedResults.put(pageResults.getJSONObject(i));
                        }
                        pageResults = reversedResults;
                    }
                }
                
                // Add all results to the collection
                for (int i = 0; i < pageResults.length(); i++) {
                    allResults.put(pageResults.getJSONObject(i));
                }
                
                int fetchedCount = pageResults.length();
                
                // Extract first and last keys for debugging and pagination
                String firstKey = null;
                String extractedLastKey = null;
                if (fetchedCount > 0) {
                    // Get first key (after potential reversal)
                    JSONObject firstResult = pageResults.getJSONObject(0);
                    if (firstResult.has("jira")) {
                        JSONObject jira = firstResult.getJSONObject("jira");
                        if (jira.has("key")) {
                            firstKey = jira.getString("key");
                        }
                    }
                    
                    // Extract last key from this batch for next iteration (after potential reversal)
                    JSONObject lastResult = pageResults.getJSONObject(fetchedCount - 1);
                    if (lastResult.has("jira")) {
                        JSONObject jira = lastResult.getJSONObject("jira");
                        if (jira.has("key")) {
                            extractedLastKey = jira.getString("key");
                            lastKey = extractedLastKey;
                        }
                    }
                }
                
                logger.debug("Fetched {} tests from Xray (total so far: {}). First key: {}, Last key: {}, Next query will use: key > \"{}\"", 
                        fetchedCount, allResults.length(), firstKey, extractedLastKey, lastKey);
                
                // If we got fewer results than requested, this is the last page
                if (fetchedCount < pageLimit) {
                    break;
                }
            } else {
                // No results field, we're done
                break;
            }
        }
        
        if (iteration >= maxIterations) {
            logger.warn("Reached max iterations ({}) for pagination, stopping to prevent infinite loop", maxIterations);
        }
        
        logger.debug("GraphQL returned total {} tests for JQL query: {}", allResults.length(), jqlQuery);
        return allResults;
    }

    /**
     * Gets test details and steps using X-ray GraphQL API.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONObject with test details including steps and preconditions, or null if not found
     * @throws IOException if API call fails
     */
    public JSONObject getTestDetailsGraphQL(String testKey) throws IOException {
        // GraphQL query to get test details with steps and preconditions
        // Note: getTests works for both Test and Precondition issue types
        String query = String.format(
            "query { " +
            "  getTests(jql: \"key=%s\", limit: 1) { " +
            "    results { " +
            "      issueId " +
            "      projectId " +
            "      jira(fields: [\"key\", \"summary\", \"description\"]) " +
            "      testType { name } " +
            "      folder { path } " +
            "      steps { " +
            "        id " +
            "        action " +
            "        data " +
            "        result " +
            "        attachments { " +
            "          id " +
            "          filename " +
            "          downloadLink " +
            "        } " +
            "        customFields { " +
            "          id " +
            "          name " +
            "          value " +
            "        } " +
            "      } " +
            "      scenarioType " +
            "      gherkin " +
            "      unstructured " +
            "      dataset { " +
            "        parameters { " +
            "          name " +
            "          type " +
            "          listValues " +
            "        } " +
            "        rows { " +
            "          order " +
            "          Values " +
            "        } " +
            "      } " +
            "      preconditions(limit: 10) { " +
            "        total " +
            "        results { " +
            "          issueId " +
            "          definition " +
            "          jira(fields: [\"key\", \"summary\"]) " +
            "        } " +
            "      } " +
            "    } " +
            "  } " +
            "}",
            testKey
        );

        try {
            String response = executeGraphQL(query);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = errors.length() > 0 ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL query returned errors for test {}: {}", testKey, errorMessage);
                return null;
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject data = responseJson.getJSONObject("data");
                if (data.has("getTests")) {
                    JSONObject getTests = data.getJSONObject("getTests");
                    if (getTests.has("results")) {
                        JSONArray results = getTests.getJSONArray("results");
                        if (results.length() > 0) {
                            JSONObject result = results.getJSONObject(0);
                            // Log if this is a Precondition issue and has steps
                            if (result.has("jira")) {
                                JSONObject jira = result.getJSONObject("jira");
                                String key = jira.optString("key", "");
                                if (result.has("steps")) {
                                    JSONArray steps = result.getJSONArray("steps");
                                    logger.debug("GraphQL returned {} steps for {}", steps.length(), key);
                                    if (steps.length() == 0) {
                                        logger.debug("GraphQL response for {} has steps field but it's empty. Full result keys: {}", 
                                                key, result.keySet());
                                    }
                                } else {
                                    logger.debug("GraphQL returned no steps field for {}. Available fields: {}", 
                                            key, result.keySet());
                                }
                            }
                            return result;
                        } else {
                            logger.debug("GraphQL getTests returned empty results array for {}", testKey);
                        }
                    } else {
                        logger.debug("GraphQL getTests has no results field for {}", testKey);
                    }
                } else {
                    logger.debug("GraphQL data has no getTests field for {}", testKey);
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL query for test {}", testKey, e);
            throw new IOException("Failed to get test details via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Gets test steps using X-ray GraphQL API.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONArray of test steps, or empty array if none found
     * @throws IOException if API call fails
     */
    public JSONArray getTestStepsGraphQL(String testKey) throws IOException {
        JSONObject testDetails = getTestDetailsGraphQL(testKey);
        if (testDetails != null && testDetails.has("steps")) {
            return testDetails.getJSONArray("steps");
        }
        return new JSONArray();
    }

    /**
     * Gets preconditions using X-ray GraphQL API.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONArray of precondition objects with jira fields, or empty array if none found
     * @throws IOException if API call fails
     */
    public JSONArray getPreconditionsGraphQL(String testKey) throws IOException {
        JSONObject testDetails = getTestDetailsGraphQL(testKey);
        if (testDetails != null && testDetails.has("preconditions")) {
            JSONObject preconditions = testDetails.getJSONObject("preconditions");
            if (preconditions.has("results")) {
                return preconditions.getJSONArray("results");
            }
        }
        return new JSONArray();
    }
    
    /**
     * Gets Precondition details including definition using X-ray GraphQL API.
     * This method is specifically for Precondition issue types which have a definition field.
     * 
     * @param preconditionKey Jira ticket key (e.g., "TP-910")
     * @return JSONObject with precondition details including definition, or null if not found
     * @throws IOException if API call fails
     */
    public JSONObject getPreconditionDetailsGraphQL(String preconditionKey) throws IOException {
        // GraphQL query for Precondition issues with definition
        // Use getTests with inline fragment to get definition for Precondition type
        String query = String.format(
            "query { " +
            "  getTests(jql: \"key=%s AND issueType = Precondition\", limit: 1) { " +
            "    results { " +
            "      issueId " +
            "      projectId " +
            "      jira(fields: [\"key\", \"summary\", \"description\"]) " +
            "      testType { name } " +
            "      ... on Precondition { " +
            "        definition " +
            "      } " +
            "    } " +
            "  } " +
            "}",
            preconditionKey
        );

        try {
            String response = executeGraphQL(query);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = errors.length() > 0 ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL query returned errors for precondition {}: {}", preconditionKey, errorMessage);
                return null;
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject data = responseJson.getJSONObject("data");
                if (data.has("getTests")) {
                    JSONObject getTests = data.getJSONObject("getTests");
                    if (getTests.has("results")) {
                        JSONArray results = getTests.getJSONArray("results");
                        if (results.length() > 0) {
                            return results.getJSONObject(0);
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL query for precondition {}", preconditionKey, e);
            throw new IOException("Failed to get precondition details via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Adds a test step to a test issue using X-ray GraphQL API.
     * Can accept either issue ID or ticket key (e.g., "12345" or "TP-123").
     * 
     * @param issueIdOrKey Jira issue ID (e.g., "12345") or ticket key (e.g., "TP-123")
     * @param action Step action (e.g., "Enter username")
     * @param data Step data (e.g., "test_user")
     * @param result Step expected result (e.g., "Username accepted")
     * @return JSONObject with created step details (id, action, data, result), or null if failed
     * @throws IOException if API call fails
     */
    public JSONObject addTestStepGraphQL(String issueIdOrKey, String action, String data, String result) throws IOException {
        // GraphQL mutation to add a test step
        // X-ray GraphQL API accepts: addTestStep(issueId: String!, step: CreateStepInput!)
        // Try using the identifier directly - it may accept both ID and key
        // CreateStepInput has fields: action, data, result
        
        // Escape special characters in strings for JSON
        String escapedIdentifier = issueIdOrKey != null ? issueIdOrKey.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") : "";
        String escapedAction = action != null ? action.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") : "";
        String escapedData = data != null ? data.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") : "";
        String escapedResult = result != null ? result.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") : "";
        
        String mutation = String.format(
            "mutation { " +
            "  addTestStep( " +
            "    issueId: \"%s\", " +
            "    step: { " +
            "      action: \"%s\", " +
            "      data: \"%s\", " +
            "      result: \"%s\" " +
            "    } " +
            "  ) { " +
            "    id " +
            "    action " +
            "    data " +
            "    result " +
            "  } " +
            "}",
            escapedIdentifier, escapedAction, escapedData, escapedResult
        );

        try {
            String response = executeGraphQL(mutation);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = errors.length() > 0 ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL mutation returned errors for test {}: {}", issueIdOrKey, errorMessage);
                throw new IOException("GraphQL mutation failed: " + errorMessage);
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject dataObj = responseJson.getJSONObject("data");
                if (dataObj.has("addTestStep")) {
                    return dataObj.getJSONObject("addTestStep");
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL mutation to add test step for test {}", issueIdOrKey, e);
            throw new IOException("Failed to add test step via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Adds multiple test steps to a test issue using X-ray GraphQL API.
     * Can accept either issue ID or ticket key (e.g., "12345" or "TP-123").
     * 
     * @param issueIdOrKey Jira issue ID (e.g., "12345") or ticket key (e.g., "TP-123")
     * @param steps JSONArray of step objects, each with "action", "data", and "result" fields
     * @return JSONArray of created step objects, or empty array if failed
     * @throws IOException if API call fails
     */
    public JSONArray addTestStepsGraphQL(String issueIdOrKey, JSONArray steps) throws IOException {
        JSONArray createdSteps = new JSONArray();
        
        if (steps == null || steps.isEmpty()) {
            logger.debug("No steps to add for test {}", issueIdOrKey);
            return createdSteps;
        }

        int successfulCount = 0;
        IOException firstError = null;
        
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String action = step.optString("action", "");
            String data = step.optString("data", "");
            String result = step.optString("result", "");
            
            try {
                JSONObject createdStep = addTestStepGraphQL(issueIdOrKey, action, data, result);
                if (createdStep != null) {
                    createdSteps.put(createdStep);
                    successfulCount++;
                    logger.debug("Successfully added step {} to test {}", i + 1, issueIdOrKey);
                } else {
                    logger.warn("Failed to add step {} to test {} (null result)", i + 1, issueIdOrKey);
                }
            } catch (IOException e) {
                if (firstError == null) {
                    firstError = e;
                }
                logger.error("Error adding step {} to test {}: {}", i + 1, issueIdOrKey, e.getMessage());
                // Continue with next step to try adding remaining steps
            }
        }

        logger.info("Successfully added {} out of {} steps to test {}", successfulCount, steps.length(), issueIdOrKey);
        
        // If all steps failed, throw exception to allow fallback to issue ID
        if (createdSteps.length() == 0 && firstError != null) {
            throw new IOException("Failed to add any steps to test " + issueIdOrKey + ": " + firstError.getMessage(), firstError);
        }
        
        return createdSteps;
    }

    /**
     * Adds a single precondition to a test issue using X-ray GraphQL API.
     * Note: GraphQL API uses "addPreconditionsToTest" (plural) and accepts array of preconditionIssueIds.
     * 
     * @param testIssueId Jira issue ID of the test (e.g., "12345")
     * @param preconditionIssueId Jira issue ID of the precondition (e.g., "12346")
     * @return JSONObject with result, or null if failed
     * @throws IOException if API call fails
     */
    public JSONObject addPreconditionToTestGraphQL(String testIssueId, String preconditionIssueId) throws IOException {
        if (testIssueId == null || testIssueId.isEmpty() || preconditionIssueId == null || preconditionIssueId.isEmpty()) {
            logger.debug("Invalid parameters for adding precondition to test");
            return null;
        }

        // GraphQL mutation to add preconditions to a test
        // Correct mutation name is "addPreconditionsToTest" (plural) - API suggests this
        // It accepts an array of preconditionIssueIds
        // Note: preconditionIssueIds must be issue IDs of Precondition type issues, not Test issues
        String mutation = String.format(
            "mutation { " +
            "  addPreconditionsToTest( " +
            "    issueId: \"%s\", " +
            "    preconditionIssueIds: [\"%s\"] " +
            "  ) { " +
            "    __typename " +
            "  } " +
            "}",
            testIssueId, preconditionIssueId
        );
        
        logger.debug("Adding precondition {} to test {} via GraphQL", preconditionIssueId, testIssueId);

        try {
            String response = executeGraphQL(mutation);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = !errors.isEmpty() ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL mutation returned errors for test {}: {}", testIssueId, errorMessage);
                throw new IOException("GraphQL mutation failed: " + errorMessage);
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject dataObj = responseJson.getJSONObject("data");
                if (dataObj.has("addPreconditionsToTest")) {
                    JSONObject result = dataObj.getJSONObject("addPreconditionsToTest");
                    logger.debug("Successfully added precondition {} to test {}", preconditionIssueId, testIssueId);
                    return result;
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL mutation to add precondition to test {}", testIssueId, e);
            throw new IOException("Failed to add precondition via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Adds multiple preconditions to a test issue using X-ray GraphQL API.
     * Adds them one by one since the API only supports adding one at a time.
     * 
     * @param testIssueId Jira issue ID of the test (e.g., "12345")
     * @param preconditionIssueIds JSONArray of precondition issue IDs (e.g., ["12346", "12347"])
     * @return JSONArray of results, or empty array if failed
     * @throws IOException if API call fails
     */
    public JSONArray addPreconditionsToTestGraphQL(String testIssueId, JSONArray preconditionIssueIds) throws IOException {
        JSONArray results = new JSONArray();
        
        if (preconditionIssueIds == null || preconditionIssueIds.length() == 0) {
            logger.debug("No preconditions to add for test {}", testIssueId);
            return results;
        }

        // Add each precondition individually (GraphQL API uses singular mutation)
        for (int i = 0; i < preconditionIssueIds.length(); i++) {
            String preconditionId = preconditionIssueIds.getString(i);
            try {
                JSONObject result = addPreconditionToTestGraphQL(testIssueId, preconditionId);
                if (result != null) {
                    results.put(result);
                    logger.debug("Successfully added precondition {} to test {}", preconditionId, testIssueId);
                } else {
                    logger.warn("Failed to add precondition {} to test {} (null result)", preconditionId, testIssueId);
                }
            } catch (IOException e) {
                logger.error("Error adding precondition {} to test {}: {}", preconditionId, testIssueId, e.getMessage());
                // Continue with next precondition instead of failing completely
            }
        }

        logger.info("Successfully added {} out of {} preconditions to test {}", 
                results.length(), preconditionIssueIds.length(), testIssueId);
        return results;
    }

    /**
     * Updates a test issue to be a Cucumber test and sets its gherkin content using X-ray GraphQL API.
     * 
     * @param issueId Xray issue ID (e.g., "12345")
     * @param gherkin Gherkin scenario content
     * @return JSONObject with result, or null if failed
     * @throws IOException if API call fails
     */
    public JSONObject updateCucumberTestGraphQL(String issueId, String gherkin) throws IOException {
        if (issueId == null || issueId.isEmpty()) {
            logger.debug("Invalid issue ID for updating Cucumber test");
            return null;
        }
        if (gherkin == null || gherkin.isEmpty()) {
            logger.debug("Gherkin content is empty, skipping");
            return null;
        }

        // For Xray Cloud, updating a Cucumber test requires two steps:
        // 1. Update the test type to "Cucumber"
        // 2. Update the gherkin definition
        
        // Step 1: Update Test Type
        String updateTypeMutation = String.format(
            "mutation { " +
            "  updateTestType( " +
            "    issueId: \"%s\", " +
            "    testType: { name: \"Cucumber\" } " +
            "  ) { " +
            "    issueId " +
            "  } " +
            "}",
            issueId
        );
        
        logger.debug("Updating test {} type to Cucumber via GraphQL", issueId);
        try {
            executeGraphQL(updateTypeMutation);
        } catch (IOException e) {
            logger.error("Failed to update test type to Cucumber for {}: {}", issueId, e.getMessage());
            throw e;
        }

        // Step 2: Update Gherkin Definition
        String escapedGherkin = gherkin.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        String updateGherkinMutation = String.format(
            "mutation { " +
            "  updateGherkinTestDefinition( " +
            "    issueId: \"%s\", " +
            "    gherkin: \"%s\" " +
            "  ) { " +
            "    issueId " +
            "  } " +
            "}",
            issueId, escapedGherkin
        );
        
        logger.debug("Updating gherkin definition for test {} via GraphQL", issueId);

        try {
            String response = executeGraphQL(updateGherkinMutation);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = !errors.isEmpty() ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL mutation returned errors for test {}: {}", issueId, errorMessage);
                throw new IOException("GraphQL mutation failed: " + errorMessage);
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject dataObj = responseJson.getJSONObject("data");
                if (dataObj.has("updateGherkinTestDefinition")) {
                    JSONObject result = dataObj.getJSONObject("updateGherkinTestDefinition");
                    logger.debug("Successfully updated gherkin definition for test {}", issueId);
                    return result;
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL mutation to update gherkin for test {}", issueId, e);
            throw new IOException("Failed to update gherkin definition via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Updates dataset for a Cucumber test using X-ray Internal API.
     * Dataset contains parameters and data rows for data-driven testing.
     * Note: This uses the internal API which requires testIssueId (Xray's internal MongoDB ID).
     * 
     * @param jiraIssueId Jira issue ID (e.g., "16201")
     * @param testIssueId Xray's internal test ID (MongoDB _id from getTestDetailsGraphQL)
     * @param dataset JSONObject containing parameters and rows
     * @return JSONObject with result, or null if failed
     * @throws IOException if API call fails
     */
    public JSONObject updateDatasetInternalAPI(String jiraIssueId, String testIssueId, JSONObject dataset) throws IOException {
        if (jiraIssueId == null || jiraIssueId.isEmpty()) {
            logger.debug("Invalid Jira issue ID for updating dataset");
            return null;
        }
        if (testIssueId == null || testIssueId.isEmpty()) {
            logger.debug("Invalid test issue ID for updating dataset");
            return null;
        }
        if (dataset == null || dataset.isEmpty()) {
            logger.debug("Dataset is empty, skipping");
            return null;
        }

        logger.debug("Updating dataset for test {} (testIssueId: {}) via Internal API", jiraIssueId, testIssueId);

        try {
            // Build the internal API request payload
            JSONObject payload = new JSONObject();
            
            // Build dataset object
            JSONObject datasetObj = new JSONObject();
            JSONArray parameters = new JSONArray();
            
            if (dataset.has("parameters")) {
                JSONArray inputParams = dataset.getJSONArray("parameters");
                for (int i = 0; i < inputParams.length(); i++) {
                    JSONObject param = inputParams.getJSONObject(i);
                    JSONObject paramObj = new JSONObject();
                    paramObj.put("name", param.optString("name", ""));
                    paramObj.put("type", param.optString("type", "text"));
                    paramObj.put("combinations", param.optBoolean("combinations", false));
                    paramObj.put("listValues", param.optJSONArray("listValues") != null ? param.getJSONArray("listValues") : new JSONArray());
                    parameters.put(paramObj);
                }
            }
            
            datasetObj.put("parameters", parameters);
            datasetObj.put("testIssueId", testIssueId);
            payload.put("dataset", datasetObj);
            
            // Build datasetRows array
            JSONArray datasetRows = new JSONArray();
            int iterationsCount = 0;
            
            if (dataset.has("rows")) {
                JSONArray inputRows = dataset.getJSONArray("rows");
                iterationsCount = inputRows.length();
                
                for (int i = 0; i < inputRows.length(); i++) {
                    JSONObject row = inputRows.getJSONObject(i);
                    JSONObject rowObj = new JSONObject();
                    rowObj.put("order", row.optInt("order", i));
                    rowObj.put("combinatorialParameterId", JSONObject.NULL);
                    
                    // Build values object - map parameter index to value
                    JSONObject values = new JSONObject();
                    if (row.has("Values")) {
                        JSONArray rowValues = row.getJSONArray("Values");
                        for (int j = 0; j < rowValues.length() && j < parameters.length(); j++) {
                            // Use parameter index as key
                            values.put(String.valueOf(j), rowValues.getString(j));
                        }
                    }
                    rowObj.put("values", values);
                    datasetRows.put(rowObj);
                }
            }
            
            payload.put("datasetRows", datasetRows);
            payload.put("iterationsCount", iterationsCount);
            
            // Make PUT request to internal API
            // Note: This requires proper authentication/session cookies from Jira
            String url = String.format("%sapi/internal/paramDataset?testIssueId=%s", basePath, testIssueId);
            
            logger.debug("Calling internal API: {}", url);
            logger.debug("Payload: {}", payload.toString(2));
            
            try {
                GenericRequest request = new GenericRequest(this, url);
                request.setBody(payload.toString());
                
                String response = put(request);
                if (response == null || response.trim().isEmpty()) {
                    logger.warn("Internal API returned empty response for dataset update");
                    return null;
                }
                
                JSONObject result = new JSONObject(response);
                logger.info("Successfully updated dataset for test {} via Internal API", jiraIssueId);
                return result;
            } catch (Exception e) {
                logger.warn("Internal API call failed (this is expected if not authenticated with Jira session): {}", e.getMessage());
                logger.debug("Internal API requires Jira session cookies or X-acpt token from Atlassian Connect context");
                throw new IOException("Failed to update dataset via Internal API: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Error preparing dataset update for test {}", jiraIssueId, e);
            throw new IOException("Failed to prepare dataset update: " + e.getMessage(), e);
        }
    }

    /**
     * Updates dataset for a Cucumber test using X-ray GraphQL API (deprecated - GraphQL doesn't support dataset updates).
     * This method is kept for compatibility but will try Internal API instead.
     * 
     * @param issueId Xray issue ID (e.g., "12345")
     * @param dataset JSONObject containing parameters and rows
     * @return JSONObject with result, or null if failed
     * @throws IOException if API call fails
     */
    @Deprecated
    public JSONObject updateDatasetGraphQL(String issueId, JSONObject dataset) throws IOException {
        if (issueId == null || issueId.isEmpty()) {
            logger.debug("Invalid issue ID for updating dataset");
            return null;
        }
        if (dataset == null || dataset.isEmpty()) {
            logger.debug("Dataset is empty, skipping");
            return null;
        }

        logger.debug("Updating dataset for test {} via GraphQL", issueId);

        try {
            // Build parameters array
            StringBuilder parametersBuilder = new StringBuilder("[");
            if (dataset.has("parameters")) {
                JSONArray parameters = dataset.getJSONArray("parameters");
                for (int i = 0; i < parameters.length(); i++) {
                    if (i > 0) parametersBuilder.append(", ");
                    JSONObject param = parameters.getJSONObject(i);
                    parametersBuilder.append("{ ");
                    parametersBuilder.append("name: \"").append(param.optString("name", "")).append("\", ");
                    parametersBuilder.append("type: \"").append(param.optString("type", "text")).append("\"");
                    
                    // Add listValues if present
                    if (param.has("listValues")) {
                        JSONArray listValues = param.getJSONArray("listValues");
                        if (listValues.length() > 0) {
                            parametersBuilder.append(", listValues: [");
                            for (int j = 0; j < listValues.length(); j++) {
                                if (j > 0) parametersBuilder.append(", ");
                                parametersBuilder.append("\"").append(listValues.getString(j).replace("\"", "\\\"")).append("\"");
                            }
                            parametersBuilder.append("]");
                        }
                    }
                    parametersBuilder.append(" }");
                }
            }
            parametersBuilder.append("]");

            // Build rows array
            StringBuilder rowsBuilder = new StringBuilder("[");
            if (dataset.has("rows")) {
                JSONArray rows = dataset.getJSONArray("rows");
                for (int i = 0; i < rows.length(); i++) {
                    if (i > 0) rowsBuilder.append(", ");
                    JSONObject row = rows.getJSONObject(i);
                    rowsBuilder.append("{ ");
                    rowsBuilder.append("order: ").append(row.optInt("order", i));
                    
                    // Add Values array
                    if (row.has("Values")) {
                        JSONArray values = row.getJSONArray("Values");
                        rowsBuilder.append(", Values: [");
                        for (int j = 0; j < values.length(); j++) {
                            if (j > 0) rowsBuilder.append(", ");
                            String value = values.getString(j);
                            // Escape special characters for GraphQL
                            value = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                            rowsBuilder.append("\"").append(value).append("\"");
                        }
                        rowsBuilder.append("]");
                    }
                    rowsBuilder.append(" }");
                }
            }
            rowsBuilder.append("]");

            // Create the mutation
        String mutation = String.format(
            "mutation { " +
            "  updateTest( " +
            "    issueId: \"%s\", " +
            "    test: { " +
                "      dataset: { " +
                "        parameters: %s, " +
                "        rows: %s " +
                "      } " +
            "    } " +
            "  ) { " +
            "    issueId " +
            "  } " +
            "}",
                issueId, parametersBuilder.toString(), rowsBuilder.toString()
            );

            // Note: This GraphQL mutation doesn't work - Xray doesn't support updateTest with dataset
            // Keeping this code for reference, but it will fail
            logger.warn("Attempting GraphQL dataset update (known to fail - use Internal API instead)");
            logger.debug("Executing GraphQL mutation to update dataset for test {}", issueId);
            String response = executeGraphQL(mutation);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = !errors.isEmpty() ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL mutation returned errors for test {}: {}", issueId, errorMessage);
                throw new IOException("GraphQL mutation failed: " + errorMessage);
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject dataObj = responseJson.getJSONObject("data");
                if (dataObj.has("updateTest")) {
                    JSONObject result = dataObj.getJSONObject("updateTest");
                    logger.debug("Successfully updated dataset for test {}", issueId);
                    return result;
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL mutation to update dataset for test {}", issueId, e);
            throw new IOException("Failed to update dataset via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Sets definition for a Precondition issue using X-ray GraphQL API.
     * Definition is a string that describes the precondition steps/requirements.
     * 
     * @param preconditionIssueId Xray issue ID of the precondition (e.g., "12345")
     * @param definition Definition string describing the precondition
     * @return JSONObject with result, or null if failed
     * @throws IOException if API call fails
     */
    public JSONObject setPreconditionDefinitionGraphQL(String preconditionIssueId, String definition) throws IOException {
        if (preconditionIssueId == null || preconditionIssueId.isEmpty()) {
            logger.debug("Invalid precondition issue ID for setting definition");
            return null;
        }
        if (definition == null || definition.isEmpty()) {
            logger.debug("Definition is empty, skipping");
            return null;
        }

        // GraphQL mutation to update precondition definition
        // Use updatePrecondition mutation
        // Escape special characters in definition for GraphQL (consistent with addTestStepGraphQL)
        String escapedDefinition = definition.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        String mutation = String.format(
            "mutation { " +
            "  updatePrecondition( " +
            "    issueId: \"%s\", " +
            "    precondition: { " +
            "      definition: \"%s\" " +
            "    } " +
            "  ) { " +
            "    issueId " +
            "  } " +
            "}",
            preconditionIssueId, escapedDefinition
        );
        
        logger.debug("Setting definition for precondition {} via GraphQL", preconditionIssueId);

        try {
            String response = executeGraphQL(mutation);
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            
            // Check for GraphQL errors
            if (responseJson.has("errors")) {
                JSONArray errors = responseJson.getJSONArray("errors");
                String errorMessage = !errors.isEmpty() ? errors.getJSONObject(0).optString("message", "Unknown GraphQL error") : "GraphQL error";
                logger.warn("GraphQL mutation returned errors for precondition {}: {}", preconditionIssueId, errorMessage);
                throw new IOException("GraphQL mutation failed: " + errorMessage);
            }

            // Extract data
            if (responseJson.has("data")) {
                JSONObject dataObj = responseJson.getJSONObject("data");
                if (dataObj.has("updatePrecondition")) {
                    JSONObject result = dataObj.getJSONObject("updatePrecondition");
                    logger.debug("Successfully set definition for precondition {}", preconditionIssueId);
                    return result;
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error executing GraphQL mutation to set definition for precondition {}", preconditionIssueId, e);
            throw new IOException("Failed to set definition via GraphQL: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts X-acpt JWT token from Jira issue page HTML.
     * The token is embedded in the page's JavaScript as part of SSR data (window.SSR_DATA or inline JSON).
     * The token appears in format: "contextJwt": "eyJ0eXAiOiJKV1Q..."
     * 
     * @param jiraClient JiraClient instance to fetch the HTML
     * @param issueKey Jira issue key (e.g., "TP-1436")
     * @return JWT token for X-acpt header, or null if not found
     * @throws IOException if fetching HTML fails
     */
    public String getXacptTokenFromJiraPage(com.github.istin.dmtools.atlassian.jira.JiraClient<?> jiraClient, String issueKey) throws IOException {
        // Fetch Jira issue page HTML (only need to fetch once - token is in SSR data)
        String url = jiraClient.getBasePath() + "browse/" + issueKey;
        logger.debug("Fetching Jira page to extract X-acpt token from SSR data: {}", url);
        
        GenericRequest request = new GenericRequest(jiraClient, url);
        String html = jiraClient.execute(request);
        
        if (html == null || html.isEmpty()) {
            logger.error("Empty HTML response from Jira page for issue {}", issueKey);
            return null;
        }
        
        logger.debug("Received HTML response ({} KB) for issue {}", html.length() / 1024, issueKey);
        
        // Search for contextJwt in JavaScript/JSON blocks
        // In HTML, it appears as: \\"contextJwt\\": \\"eyJ0eXAiOiJKV1Q...
        // The quotes are escaped with backslashes in JavaScript strings
        // Pattern handles both escaped (\\") and unescaped (") variants
        String contextJwtPattern = "\\\\*\"contextJwt\\\\*\"\\s*:\\s*\\\\*\"([^\\\\\"]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(contextJwtPattern);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        
        if (matcher.find()) {
            String contextJwt = matcher.group(1);
            
            // Unescape any escaped characters in the JWT token
            contextJwt = contextJwt.replace("\\/", "/");
            
            logger.info("✅ Successfully extracted X-acpt token from Jira SSR data for issue {}", issueKey);
            logger.debug("Token length: {}, expires: {}", contextJwt.length(), extractTokenExpiry(contextJwt));
            
            return contextJwt;
        }
        
        logger.warn("❌ Could not find contextJwt in Jira page HTML for issue {}", issueKey);
        logger.debug("Issue may not be a Test type, or Xray is not enabled for this project");
        return null;
    }
    
    /**
     * Extracts expiry time from JWT token for logging.
     */
    private String extractTokenExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return "unknown";
            
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JSONObject payloadJson = new JSONObject(payload);
            
            if (payloadJson.has("exp")) {
                long exp = payloadJson.getLong("exp");
                return new java.util.Date(exp * 1000).toString();
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Transforms dataset from GraphQL format to Internal API format.
     * GraphQL format: parameters array with name/type, rows with Values array
     * Internal API format: parameters with _id/isNew/combinations, rows with values object
     * 
     * @param graphQLDataset Dataset from GraphQL API
     * @param existingDataset Existing dataset from GET request (may be null for new dataset)
     * @return Transformed dataset ready for Internal API PUT
     */
    private JSONObject transformDatasetForInternalAPI(JSONObject graphQLDataset, JSONObject existingDataset) {
        JSONObject result = new JSONObject();
        JSONObject dataset = new JSONObject();
        JSONArray datasetRows = new JSONArray();
        
        // Transform parameters
        JSONArray graphQLParams = graphQLDataset.optJSONArray("parameters");
        JSONArray transformedParams = new JSONArray();
        java.util.Map<String, String> paramNameToId = new java.util.HashMap<>();
        
        if (graphQLParams != null) {
            for (int i = 0; i < graphQLParams.length(); i++) {
                JSONObject graphQLParam = graphQLParams.getJSONObject(i);
                JSONObject transformedParam = new JSONObject();
                
                String paramName = graphQLParam.getString("name");
                String paramType = graphQLParam.optString("type", "text");
                
                // Check if parameter exists in existingDataset
                String existingId = findExistingParameterId(paramName, existingDataset);
                
                if (existingId != null) {
                    // Use existing parameter _id
                    transformedParam.put("_id", existingId);
                } else {
                    // Generate new UUID for new parameter
                    transformedParam.put("_id", java.util.UUID.randomUUID().toString());
                    transformedParam.put("isNew", true);
                }
                
                transformedParam.put("name", paramName);
                transformedParam.put("type", paramType);
                transformedParam.put("combinations", graphQLParam.optBoolean("combinations", false));
                transformedParam.put("listValues", graphQLParam.optJSONArray("listValues") != null ? 
                    graphQLParam.getJSONArray("listValues") : new JSONArray());
                
                transformedParams.put(transformedParam);
                paramNameToId.put(paramName, transformedParam.getString("_id"));
            }
        }
        
        dataset.put("parameters", transformedParams);
        dataset.put("callTestIssueId", "");
        
        // Transform rows
        JSONArray graphQLRows = graphQLDataset.optJSONArray("rows");
        if (graphQLRows != null) {
            for (int i = 0; i < graphQLRows.length(); i++) {
                JSONObject graphQLRow = graphQLRows.getJSONObject(i);
                JSONObject transformedRow = new JSONObject();
                
                int order = graphQLRow.optInt("order", i);
                JSONArray valuesArray = graphQLRow.optJSONArray("Values");
                
                // Transform Values array to values object
                JSONObject valuesObject = new JSONObject();
                if (valuesArray != null && transformedParams.length() > 0) {
                    // Map array values to parameter IDs
                    for (int j = 0; j < Math.min(valuesArray.length(), transformedParams.length()); j++) {
                        JSONObject param = transformedParams.getJSONObject(j);
                        String paramId = param.getString("_id");
                        String value = valuesArray.optString(j, "");
                        valuesObject.put(paramId, value);
                    }
                }
                
                transformedRow.put("values", valuesObject);
                transformedRow.put("order", order);
                transformedRow.put("combinatorialParameterId", graphQLRow.opt("combinatorialParameterId"));
                
                datasetRows.put(transformedRow);
            }
        }
        
        // If no rows, create empty row
        if (datasetRows.length() == 0) {
            JSONObject emptyRow = new JSONObject();
            emptyRow.put("values", new JSONObject());
            emptyRow.put("order", 0);
            emptyRow.put("combinatorialParameterId", (Object) null);
            datasetRows.put(emptyRow);
        }
        
        result.put("dataset", dataset);
        result.put("datasetRows", datasetRows);
        result.put("iterationsCount", Math.max(datasetRows.length(), 1));
        
        return result;
    }
    
    /**
     * Finds existing parameter _id by name in existing dataset.
     */
    private String findExistingParameterId(String paramName, JSONObject existingDataset) {
        if (existingDataset == null || !existingDataset.has("parameters")) {
            return null;
        }
        
        JSONArray existingParams = existingDataset.getJSONArray("parameters");
        for (int i = 0; i < existingParams.length(); i++) {
            JSONObject param = existingParams.getJSONObject(i);
            if (paramName.equals(param.optString("name"))) {
                return param.optString("_id");
            }
        }
        return null;
    }
    
    /**
     * Gets testVersionId for a Jira issue using Xray Internal API.
     * This is the Xray-internal MongoDB ObjectId needed for dataset operations.
     * 
     * @param jiraIssueId Jira issue ID (numeric, e.g., "16201")
     * @param xacptToken X-acpt JWT token for authentication
     * @return testVersionId (Xray MongoDB ObjectId) or null if not found
     * @throws IOException if request fails
     */
    public String getTestVersionId(String jiraIssueId, String xacptToken) throws IOException {
        if (jiraIssueId == null || jiraIssueId.isEmpty()) {
            logger.warn("Invalid jiraIssueId for getting testVersionId");
            return null;
        }
        if (xacptToken == null || xacptToken.isEmpty()) {
            logger.warn("X-acpt token is required for getting testVersionId");
            return null;
        }
        
        logger.debug("Getting testVersionId for Jira issue ID: {}", jiraIssueId);
        
        // Construct URL for Internal API
        String baseDomain = basePath;
        if (baseDomain.contains("/api/v2")) {
            baseDomain = baseDomain.replace("/api/v2", "");
        }
        baseDomain = baseDomain.replaceAll("/+$", "");
        String url = baseDomain + "/api/internal/tests/versions";
        
        // Prepare request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("issueIds", new JSONArray().put(jiraIssueId));
        requestBody.put("includeArchived", true);
        requestBody.put("includeTestType", true);
        
        GenericRequest request = new GenericRequest(this, url);
        request.setBody(requestBody.toString())
               .header("Content-Type", "application/json")
               .header("Accept", "application/json, text/plain, */*")
               .header("Origin", baseDomain)
               .header("X-addon-key", "com.xpandit.plugins.xray")
               .header("X-acpt", xacptToken);
        
        logger.debug("Calling Internal API to get testVersionId: {}", url);
        
        try {
            String response = post(request);
            if (response == null || response.trim().isEmpty()) {
                logger.warn("Empty response when getting testVersionId for issue {}", jiraIssueId);
                return null;
            }
            
            JSONObject responseJson = new JSONObject(response);
            if (!responseJson.has(jiraIssueId)) {
                logger.warn("Response does not contain issue ID {} - issue may not be a Test type", jiraIssueId);
                return null;
            }
            
            JSONArray versions = responseJson.getJSONArray(jiraIssueId);
            if (versions.length() == 0) {
                logger.warn("No test versions found for issue {}", jiraIssueId);
                return null;
            }
            
            // Get the default version (or first if no default)
            JSONObject defaultVersion = null;
            for (int i = 0; i < versions.length(); i++) {
                JSONObject version = versions.getJSONObject(i);
                if (version.optBoolean("isDefault", false)) {
                    defaultVersion = version;
                    break;
                }
            }
            
            if (defaultVersion == null) {
                defaultVersion = versions.getJSONObject(0);
                logger.debug("No default version found, using first version");
            }
            
            String testVersionId = defaultVersion.getString("testVersionId");
            logger.info("✅ Got testVersionId: {} for issue {}", testVersionId, jiraIssueId);
            
            if (defaultVersion.has("testType")) {
                JSONObject testType = defaultVersion.getJSONObject("testType");
                logger.debug("  Test type: {}", testType.optString("value"));
            }
            
            return testVersionId;
            
        } catch (IOException e) {
            logger.error("Failed to get testVersionId for issue {}: {}", jiraIssueId, e.getMessage());
            throw new IOException("Failed to get testVersionId: " + e.getMessage(), e);
        }
    }

    /**
     * Updates dataset for a Cucumber test using X-ray Internal API.
     * This uses the undocumented internal REST API that requires X-acpt JWT token.
     * The token is extracted from the Jira issue page HTML.
     * 
     * @param jiraClient JiraClient instance to fetch the X-acpt token from HTML
     * @param testIssueKey Jira issue key (e.g., "TP-1436")
     * @param testIssueId Jira issue ID (numeric)
     * @param dataset Dataset JSON object with parameters and rows
     * @return Response JSON or null if failed
     * @throws IOException if request fails
     */
    public JSONObject updateDatasetInternalAPI(com.github.istin.dmtools.atlassian.jira.JiraClient<?> jiraClient, 
                                               String testIssueKey,
                                               String jiraIssueId, 
                                               JSONObject dataset) throws IOException {
        if (jiraIssueId == null || jiraIssueId.isEmpty()) {
            logger.debug("Invalid jiraIssueId for updating dataset via Internal API");
            return null;
        }
        if (dataset == null || dataset.isEmpty()) {
            logger.debug("Dataset is empty, skipping update via Internal API");
            return null;
        }

        logger.debug("Updating dataset for test {} (Jira ID: {}) via Internal API", testIssueKey, jiraIssueId);

        // Extract X-acpt token from Jira page HTML
        String xacptToken = getXacptTokenFromJiraPage(jiraClient, testIssueKey);
        if (xacptToken == null || xacptToken.isEmpty()) {
            throw new IOException("Failed to extract X-acpt token from Jira page. " +
                    "Ensure the issue is a Test type with Xray panel loaded.");
        }

        // Get testVersionId (Xray internal MongoDB ObjectId)
        String testVersionId = getTestVersionId(jiraIssueId, xacptToken);
        if (testVersionId == null || testVersionId.isEmpty()) {
            throw new IOException("Failed to get testVersionId for issue " + testIssueKey + 
                    ". Issue may not be a Test type or Xray is not configured.");
        }

        logger.debug("Using testVersionId: {} for test {}", testVersionId, testIssueKey);

        // Construct the base domain
        String baseDomain = basePath;
        if (baseDomain.contains("/api/v2")) {
            baseDomain = baseDomain.replace("/api/v2", "");
        }
        baseDomain = baseDomain.replaceAll("/+$", "");

        // First, GET existing dataset structure to preserve _id fields
        // Use Jira issue ID for GET (testVersionId is only for PUT)
        String getUrl = baseDomain + "/api/internal/paramDataset?testIssueId=" + jiraIssueId;
        logger.debug("Getting existing dataset structure from: {} (using Jira issue ID)", getUrl);

        JSONObject existingDataset = null;
        try {
            GenericRequest getRequest = new GenericRequest(this, getUrl);
            getRequest.header("Accept", "application/json")
                      .header("Origin", baseDomain)
                      .header("X-addon-key", "com.xpandit.plugins.xray")
                      .header("X-acpt", xacptToken);

            String getResponse = execute(getRequest);
            if (getResponse != null && !getResponse.isEmpty()) {
                try {
                    existingDataset = new JSONObject(getResponse);
                    logger.debug("✅ Retrieved existing dataset with _id fields");
                } catch (org.json.JSONException jsonEx) {
                    logger.warn("GET response is not valid JSON (may be empty dataset): {}", getResponse);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not retrieve existing dataset (will create new): {}", e.getMessage());
        }

        // Transform dataset from GraphQL format to Internal API format
        JSONObject transformedDataset = transformDatasetForInternalAPI(dataset, existingDataset);
        
        // Prepare the request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("dataset", transformedDataset.getJSONObject("dataset"));
        requestBody.put("datasetRows", transformedDataset.getJSONArray("datasetRows"));
        requestBody.put("iterationsCount", transformedDataset.getInt("iterationsCount"));

        // Construct PUT URL with BOTH jiraIssueId AND testVersionId
        String putUrl = baseDomain + "/api/internal/paramDataset?testIssueId=" + jiraIssueId + "&testVersionId=" + testVersionId;

        GenericRequest putRequest = new GenericRequest(this, putUrl);
        putRequest.setBody(requestBody.toString())
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json, text/plain, */*")
                  .header("Origin", baseDomain)
                  .header("X-addon-key", "com.xpandit.plugins.xray")
                  .header("X-acpt", xacptToken);
        
        logger.debug("Calling Internal API PUT with testVersionId: {}", putUrl);

        try {
            String response = put(putRequest);
            if (response == null || response.trim().isEmpty()) {
                logger.info("Dataset updated successfully (empty response)");
                return new JSONObject();
            }
            logger.info("Dataset updated successfully for test {}", testIssueKey);
            return new JSONObject(response);
        } catch (IOException e) {
            logger.error("Internal API call failed for test {}: {}", testIssueKey, e.getMessage());
            throw new IOException("Failed to update dataset via Internal API: " + e.getMessage(), e);
        }
    }
}
