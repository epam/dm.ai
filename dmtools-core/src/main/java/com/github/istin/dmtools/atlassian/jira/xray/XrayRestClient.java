package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
        setCacheGetRequestsEnabled(false); // Disable caching for X-ray API calls
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
        Set<String> fetchedKeys = new HashSet<>();
        int maxIterations = 1000; // Safety limit to prevent infinite loops
        int iteration = 0;
        
        while (iteration < maxIterations) {
            iteration++;
            
            // Build JQL query excluding already fetched keys
            String currentJQL = jqlQuery;
            if (!fetchedKeys.isEmpty()) {
                // Add exclusion for already fetched keys (keys need to be quoted in JQL)
                StringBuilder excludeKeysBuilder = new StringBuilder();
                boolean first = true;
                for (String key : fetchedKeys) {
                    if (!first) {
                        excludeKeysBuilder.append(", ");
                    }
                    excludeKeysBuilder.append("\"").append(key).append("\"");
                    first = false;
                }
                currentJQL = jqlQuery + " AND key NOT IN (" + excludeKeysBuilder.toString() + ")";
            }
            
            JSONObject pageData = getTestsByJQLGraphQLWithPagination(currentJQL, pageLimit);
            if (pageData == null) {
                break;
            }
            
            // Extract results from this page
            JSONArray pageResults = null;
            if (pageData.has("results")) {
                pageResults = pageData.getJSONArray("results");
                int newResultsCount = 0;
                for (int i = 0; i < pageResults.length(); i++) {
                    JSONObject result = pageResults.getJSONObject(i);
                    if (result.has("jira")) {
                        JSONObject jira = result.getJSONObject("jira");
                        if (jira.has("key")) {
                            String key = jira.getString("key");
                            if (!fetchedKeys.contains(key)) {
                                allResults.put(result);
                                fetchedKeys.add(key);
                                newResultsCount++;
                            }
                        }
                    }
                }
                logger.debug("Fetched {} new tests from Xray (total so far: {})", newResultsCount, allResults.length());
                
                // If we got fewer results than requested, or no new results, this is the last page
                if (pageResults.length() < pageLimit || newResultsCount == 0) {
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

        int failedCount = 0;
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
                    logger.debug("Successfully added step {} to test {}", i + 1, issueIdOrKey);
                } else {
                    failedCount++;
                    logger.warn("Failed to add step {} to test {} (null result)", i + 1, issueIdOrKey);
                }
            } catch (IOException e) {
                failedCount++;
                if (firstError == null) {
                    firstError = e;
                }
                logger.error("Error adding step {} to test {}: {}", i + 1, issueIdOrKey, e.getMessage());
                // Continue with next step to try adding remaining steps
            }
        }

        logger.info("Successfully added {} out of {} steps to test {}", createdSteps.length(), steps.length(), issueIdOrKey);
        
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
        // Use updateTest mutation (works for both Test and Precondition issues)
        // Escape special characters in definition for GraphQL (consistent with addTestStepGraphQL)
        String escapedDefinition = definition.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        String mutation = String.format(
            "mutation { " +
            "  updateTest( " +
            "    issueId: \"%s\", " +
            "    test: { " +
            "      definition: \"%s\" " +
            "    } " +
            "  ) { " +
            "    issueId " +
            "    definition " +
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
                if (dataObj.has("updateTest")) {
                    JSONObject result = dataObj.getJSONObject("updateTest");
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
}

