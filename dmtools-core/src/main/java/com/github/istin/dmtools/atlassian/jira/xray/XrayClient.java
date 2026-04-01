// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * X-ray client that extends JiraClient to provide X-ray-specific functionality.
 * Supports test steps and preconditions via X-ray REST API.
 * 
 * This client uses dual configuration:
 * - Jira configuration (JIRA_BASE_PATH, JIRA_API_TOKEN) for Jira API calls
 * - X-ray configuration (XRAY_CLIENT_ID, XRAY_CLIENT_SECRET, XRAY_BASE_PATH) for X-ray API calls
 * 
 * <p>
 * <b>Note on X-ray Cloud authentication:</b>
 * X-ray Cloud does <em>not</em> use standard OAuth2 client credentials flow.
 * Instead, it uses a proprietary authentication mechanism where you POST the client credentials
 * (client ID and client secret) to the <code>/authenticate</code> endpoint and receive a bearer token in response.
 * This bearer token is then used to authorize subsequent API requests.
 * </p>
 */
public class XrayClient extends JiraClient<Ticket> {

    private static final Logger logger = LogManager.getLogger(XrayClient.class);

    // X-ray configuration (static for property-based, instance for server-managed)
    private static final String XRAY_BASE_PATH;
    private static final String XRAY_CLIENT_ID;
    private static final String XRAY_CLIENT_SECRET;


    // Jira configuration (inherited from BasicJiraClient pattern)
    private static final String JIRA_BASE_PATH;
    private static final String JIRA_TOKEN;
    private static final String JIRA_AUTH_TYPE;

    private static final boolean IS_JIRA_LOGGING_ENABLED;
    private static final boolean IS_JIRA_CLEAR_CACHE;
    private static final boolean IS_JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES;
    private static final boolean IS_JIRA_WAIT_BEFORE_PERFORM;
    private static final Long SLEEP_TIME_REQUEST;
    private static final String[] JIRA_EXTRA_FIELDS;
    private static final String JIRA_EXTRA_FIELDS_PROJECT;
    private static final int JIRA_SEARCH_MAX_RESULTS;

    /**
     * -- GETTER --
     *  Gets the underlying XrayRestClient instance.
     *  Useful for setting pagination limits in tests.
     *
     * @return XrayRestClient instance
     */
    // X-ray REST client for API communication
    @Getter
    private final XrayRestClient xrayRestClient;

    // Field names for steps and preconditions extraction
    private static final String[] STEPS_FIELD_NAMES = {"steps", "testSteps", "xraySteps", "xrayTestSteps", "test_steps"};
    private static final String[] PRECONDITIONS_FIELD_NAMES = {"preconditions", "xrayPreconditions", "testPreconditions", "test_preconditions"};
    private static final String[] GHERKIN_FIELD_NAMES = {"gherkin", "cucumber", "cucumber_scenario", "xrayGherkin", "gherkin_scenario"};
    private static final String[] DATASET_FIELD_NAMES = {"dataset", "xrayDataset", "testDataset", "test_dataset", "data_driven"};

    private static volatile XrayClient instance;
    private final String[] defaultJiraFields;
    private final String[] extendedJiraFields;
    private final String[] customCodesOfConfigFields;
    
    /**
     * Sentinel field value that opts into X-ray enrichment during searchAndPerform.
     * Add this to a TrackerRule's getRequiredExtraFields() to enable X-ray test step/precondition
     * enrichment explicitly. When XRAY_ENRICHMENT_ENABLED_BY_DEFAULT is true (default),
     * enrichment also runs without the sentinel to preserve existing behavior.
     */
    public static final String FIELD_XRAY_ENRICHMENT = "xray_enrichment";

    // Flag to prevent recursion when enriching tickets
    private final ThreadLocal<Boolean> isEnriching = ThreadLocal.withInitial(() -> false);

    static {
        PropertyReader propertyReader = new PropertyReader();
        
        // Read Jira configuration
        JIRA_BASE_PATH = propertyReader.getJiraBasePath();
        String jiraLoginPassToken = propertyReader.getJiraLoginPassToken();
        if (jiraLoginPassToken == null || jiraLoginPassToken.isEmpty()) {
            String email = propertyReader.getJiraEmail();
            String token = propertyReader.getJiraApiToken();
            if (email != null && token != null) {
                String credentials = email.trim() + ":" + token.trim();
                JIRA_TOKEN = Base64.getEncoder().encodeToString(credentials.getBytes());
            } else {
                JIRA_TOKEN = jiraLoginPassToken;
            }
        } else {
            JIRA_TOKEN = jiraLoginPassToken;
        }
        JIRA_AUTH_TYPE = propertyReader.getJiraAuthType();
        IS_JIRA_LOGGING_ENABLED = propertyReader.isJiraLoggingEnabled();
        IS_JIRA_CLEAR_CACHE = propertyReader.isJiraClearCache();
        IS_JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES = propertyReader.isJiraTransformCustomFieldsToNames();
        IS_JIRA_WAIT_BEFORE_PERFORM = propertyReader.isJiraWaitBeforePerform();
        SLEEP_TIME_REQUEST = propertyReader.getSleepTimeRequest();
        JIRA_EXTRA_FIELDS = propertyReader.getJiraExtraFields();
        JIRA_EXTRA_FIELDS_PROJECT = propertyReader.getJiraExtraFieldsProject();
        JIRA_SEARCH_MAX_RESULTS = propertyReader.getJiraMaxSearchResults();

        // Read X-ray configuration
        XRAY_BASE_PATH = propertyReader.getXrayBasePath();
        XRAY_CLIENT_ID = propertyReader.getXrayClientId();
        XRAY_CLIENT_SECRET = propertyReader.getXrayClientSecret();
    }

    public static TrackerClient<? extends ITicket> getInstance() throws IOException {
        // Double-checked locking for thread-safe singleton
        if (instance == null) {
            synchronized (XrayClient.class) {
        if (instance == null) {
            if (JIRA_BASE_PATH == null || JIRA_BASE_PATH.isEmpty()) {
                logger.warn("JIRA_BASE_PATH is not configured, cannot create XrayClient");
                return null;
            }
            if (XRAY_BASE_PATH == null || XRAY_BASE_PATH.isEmpty()) {
                logger.warn("XRAY_BASE_PATH is not configured, cannot create XrayClient");
                return null;
            }
            if (XRAY_CLIENT_ID == null || XRAY_CLIENT_ID.isEmpty() || 
                XRAY_CLIENT_SECRET == null || XRAY_CLIENT_SECRET.isEmpty()) {
                logger.warn("XRAY_CLIENT_ID or XRAY_CLIENT_SECRET is not configured, cannot create XrayClient");
                return null;
            }
            instance = new XrayClient();
                }
            }
        }
        return instance;
    }

    /**
     * Default constructor for property-based configuration (singleton pattern)
     */
    public XrayClient() throws IOException {
        this(JIRA_BASE_PATH, JIRA_TOKEN, JIRA_AUTH_TYPE, JIRA_SEARCH_MAX_RESULTS,
                XRAY_BASE_PATH, XRAY_CLIENT_ID, XRAY_CLIENT_SECRET,
                IS_JIRA_LOGGING_ENABLED, IS_JIRA_CLEAR_CACHE, IS_JIRA_WAIT_BEFORE_PERFORM, SLEEP_TIME_REQUEST,
                JIRA_EXTRA_FIELDS_PROJECT, JIRA_EXTRA_FIELDS);
    }

    /**
     * Constructor for server-managed mode with explicit configuration
     */
    protected XrayClient(String jiraBasePath, String jiraToken, String jiraAuthType, int maxSearchResults,
                        String xrayBasePath, String xrayClientId, String xrayClientSecret,
                        boolean isLoggingEnabled, boolean isClearCache, boolean isWaitBeforePerform, Long sleepTimeRequest,
                        String extraFieldsProject, String[] extraFields) throws IOException {
        // Initialize with Jira configuration (for Jira API calls)
        super(jiraBasePath, jiraToken, LogManager.getLogger(XrayClient.class), maxSearchResults);
        
        if (jiraAuthType != null) {
            setAuthType(jiraAuthType);
        }
        setLogEnabled(isLoggingEnabled);
        setWaitBeforePerform(isWaitBeforePerform);
        if (sleepTimeRequest != null) {
            setSleepTimeRequest(sleepTimeRequest);
        }
        setClearCache(isClearCache);
        setTransformCustomFieldsToNames(IS_JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES);
        setProjectContext(extraFieldsProject);

        // Initialize X-ray REST client (for X-ray API calls)
        this.xrayRestClient = new XrayRestClient(xrayBasePath, xrayClientId, xrayClientSecret);

        // Initialize field arrays like BasicJiraClient
        List<String> defaultFields = new ArrayList<>(Arrays.asList(BasicJiraClient.DEFAULT_QUERY_FIELDS));

        if (extraFieldsProject != null && extraFields != null) {
            customCodesOfConfigFields = new String[extraFields.length];
            for (int i = 0; i < extraFields.length; i++) {
                String extraField = extraFields[i];
                String fieldCustomCode = getFieldCustomCode(extraFieldsProject, extraField);
                customCodesOfConfigFields[i] = fieldCustomCode;
                defaultFields.add(fieldCustomCode);
            }
        } else {
            customCodesOfConfigFields = null;
        }

        defaultJiraFields = defaultFields.toArray(new String[0]);

        List<String> extendedFields = new ArrayList<>();
        extendedFields.addAll(Arrays.asList(BasicJiraClient.EXTENDED_QUERY_FIELDS));
        extendedFields.addAll(defaultFields);

        extendedJiraFields = extendedFields.toArray(new String[0]);
    }

    /**
     * Makes an authenticated request to X-ray API using XrayRestClient.
     * 
     * @param endpoint X-ray API endpoint (relative to basePath, e.g., "test/TP-123/steps")
     * @param method HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param body Request body (can be null for GET/DELETE)
     * @return Response body as string
     * @throws IOException if request fails
     */
    private String xrayApiRequest(String endpoint, String method, String body) throws IOException {
        return xrayRestClient.xrayRequest(endpoint, method, body);
    }

    /**
     * Extracts test steps from Fields object.
     * Checks for predefined field names: steps, testSteps, xraySteps, test_steps
     * 
     * @param fields Fields object from FieldsInitializer
     * @return List of step objects, or null if not found
     */
    private JSONArray extractSteps(Fields fields) {
        for (String fieldName : STEPS_FIELD_NAMES) {
            try {
                Object stepsValue = fields.getJSONObject().opt(fieldName);
                if (stepsValue != null) {
                    if (stepsValue instanceof JSONArray) {
                        return (JSONArray) stepsValue;
                    } else if (stepsValue instanceof String) {
                        // Try to parse as JSON array
                        try {
                            return new JSONArray((String) stepsValue);
                        } catch (Exception e) {
                            logger.warn("Failed to parse steps field as JSON array: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // Field doesn't exist or is not accessible, continue to next field name
            }
        }
        return null;
    }

    /**
     * Extracts preconditions from Fields object.
     * Checks for predefined field names: preconditions, xrayPreconditions, testPreconditions, test_preconditions
     * 
     * @param fields Fields object from FieldsInitializer
     * @return List of precondition objects, or null if not found
     */
    private JSONArray extractPreconditions(Fields fields) {
        for (String fieldName : PRECONDITIONS_FIELD_NAMES) {
            try {
                Object preconditionsValue = fields.getJSONObject().opt(fieldName);
                if (preconditionsValue != null) {
                    if (preconditionsValue instanceof JSONArray) {
                        return (JSONArray) preconditionsValue;
                    } else if (preconditionsValue instanceof String) {
                        // Try to parse as JSON array
                        try {
                            return new JSONArray((String) preconditionsValue);
                        } catch (Exception e) {
                            logger.warn("Failed to parse preconditions field as JSON array: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // Field doesn't exist or is not accessible, continue to next field name
            }
        }
        return null;
    }

    /**
     * Extracts gherkin scenario from Fields object.
     * Checks for predefined field names: gherkin, cucumber, cucumber_scenario, xrayGherkin, gherkin_scenario
     *
     * @param fields Fields object from FieldsInitializer
     * @return Gherkin scenario string, or null if not found
     */
    private String extractGherkin(Fields fields) {
        for (String fieldName : GHERKIN_FIELD_NAMES) {
            try {
                Object gherkinValue = fields.getJSONObject().opt(fieldName);
                if (gherkinValue != null) {
                    return gherkinValue.toString();
                }
            } catch (Exception e) {
                // Field doesn't exist or is not accessible, continue to next field name
            }
        }
        return null;
    }

    /**
     * Helper method to extract dataset from Fields object.
     * Checks multiple possible field names for dataset.
     * 
     * @param fields Fields object potentially containing dataset
     * @return JSONObject with dataset data (parameters and rows), or null if not found
     */
    private JSONObject extractDataset(Fields fields) {
        for (String fieldName : DATASET_FIELD_NAMES) {
            try {
                Object datasetValue = fields.getJSONObject().opt(fieldName);
                if (datasetValue != null) {
                    if (datasetValue instanceof JSONObject) {
                        return (JSONObject) datasetValue;
                    } else if (datasetValue instanceof String) {
                        // Try to parse as JSON
                        return new JSONObject((String) datasetValue);
                    }
                }
            } catch (Exception e) {
                // Field doesn't exist or is not accessible, continue to next field name
            }
        }
        return null;
    }

    /**
     * Sets test steps on a Jira ticket via X-ray GraphQL API.
     * Migrated from REST API to GraphQL for better reliability.
     * 
     * @param testKey Jira ticket key (e.g., "PROJ-123")
     * @param steps Array of step objects with action, data, expectedResult fields
     * @throws IOException if API call fails
     */
    /**
     * Waits for X-ray to sync a newly created ticket.
     * Checks if the ticket is available in Xray by querying it via GraphQL.
     * 
     * @param ticketKey Jira ticket key (e.g., "TP-123")
     * @return The Xray issue ID if available, or null if not synced yet
     */
    private String waitForXraySync(String ticketKey) {
        int maxAttempts = 3; // Maximum 3 attempts
        long waitTimeMs = 2000; // Wait 2 seconds between attempts
        
        logger.debug("Waiting for X-ray to sync ticket {}", ticketKey);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Check if ticket is available in Xray by querying it
                JSONObject testDetails = xrayRestClient.getTestDetailsGraphQL(ticketKey);
                if (testDetails != null) {
                    // Extract issueId from Xray response - this is the ID Xray uses
                    String xrayIssueId = testDetails.optString("issueId", null);
                    if (xrayIssueId != null && !xrayIssueId.isEmpty()) {
                        logger.debug("Ticket {} is now available in X-ray (attempt {}), Xray issue ID: {}", 
                                ticketKey, attempt, xrayIssueId);
                        return xrayIssueId; // Return Xray issue ID
                    } else {
                        logger.debug("Ticket {} is available in X-ray but issueId not found (attempt {})", 
                                ticketKey, attempt);
                        return null; // Ticket is synced but no issueId, proceed anyway
                    }
                }
            } catch (IOException e) {
                // Ticket not yet synced, continue waiting
                logger.debug("Ticket {} not yet available in X-ray (attempt {}/{}): {}", 
                        ticketKey, attempt, maxAttempts, e.getMessage());
            }
            
            // Wait before next attempt (except on last attempt)
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(waitTimeMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Thread interrupted while waiting for X-ray sync");
                    return null;
                }
            }
        }
        
        logger.warn("Ticket {} may not be fully synced in X-ray after {} attempts, proceeding anyway", 
                ticketKey, maxAttempts);
        return null;
    }

    private void setTestSteps(String testKey, JSONArray steps, String xrayIssueId) throws IOException {
        if (steps == null || steps.isEmpty()) {
            logger.debug("No steps to set for test {}", testKey);
            return;
        }

        logger.debug("Setting {} steps for test {} using GraphQL", steps.length(), testKey);
        
        // Convert steps array to format expected by GraphQL
        JSONArray graphqlSteps = new JSONArray();
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            // Handle different field names: action, data, result, expectedResult
            String action = step.optString("action", step.optString("step", ""));
            String data = step.optString("data", "");
            String result = step.optString("result", step.optString("expectedResult", ""));
            
            graphqlSteps.put(new JSONObject()
                    .put("action", action)
                    .put("data", data)
                    .put("result", result));
        }
        
        // Use Xray issue ID if available (preferred), otherwise try ticket key, then fallback to Jira issue ID
        String issueIdToUse = null;
        
        if (xrayIssueId != null && !xrayIssueId.isEmpty()) {
            issueIdToUse = xrayIssueId;
            logger.debug("Using Xray issue ID {} for test {}", issueIdToUse, testKey);
        } else {
            // Try using ticket key first
            IOException keyException = null;
            try {
                addTestStepsGraphQL(testKey, graphqlSteps);
                logger.info("Successfully set {} steps for test {} using GraphQL (with ticket key)", steps.length(), testKey);
                return;
            } catch (IOException e) {
                keyException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                // If key is not valid, fallback to issue ID
                if (errorMsg.contains("not valid") || errorMsg.contains("Invalid") || errorMsg.contains("Cannot")) {
                    logger.debug("Ticket key not accepted by GraphQL API, falling back to Jira issue ID");
                } else {
                    // For other errors, rethrow immediately
                    throw e;
                }
            }
            
            // Fallback: Get issue ID from Jira
            logger.debug("Getting issue ID from Jira for test {} to use with GraphQL API", testKey);
            Ticket ticket = performTicket(testKey, new String[]{"id"});
            if (ticket == null || ticket.getId() == null || ticket.getId().isEmpty()) {
                throw new IOException("Cannot get issue ID for test " + testKey + (keyException != null ? ": " + keyException.getMessage() : ""));
            }
            issueIdToUse = ticket.getId();
            logger.debug("Using Jira issue ID {} for test {}", issueIdToUse, testKey);
        }
        
        logger.debug("Setting {} steps for test {} (issue ID: {}) using GraphQL", steps.length(), testKey, issueIdToUse);
        
        addTestStepsGraphQL(issueIdToUse, graphqlSteps);
        logger.info("Successfully set {} steps for test {} using GraphQL (with issue ID)", steps.length(), testKey);
    }

    /**
     * Sets gherkin (Cucumber) scenario on a Jira ticket via X-ray GraphQL API.
     *
     * @param testKey Jira ticket key (e.g., "PROJ-123")
     * @param gherkin Gherkin scenario text
     * @param xrayIssueId Xray issue ID if available (from waitForXraySync), or null
     * @throws IOException if API call fails
     */
    private void setCucumberTest(String testKey, String gherkin, String xrayIssueId) throws IOException {
        if (gherkin == null || gherkin.isEmpty()) {
            logger.debug("No gherkin content to set for test {}", testKey);
            return;
        }

        logger.debug("Setting gherkin for test {} using GraphQL", testKey);

        // Use Xray issue ID if available (preferred), otherwise try ticket key, then fallback to Jira issue ID
        String issueIdToUse = null;

        if (xrayIssueId != null && !xrayIssueId.isEmpty()) {
            issueIdToUse = xrayIssueId;
            logger.debug("Using Xray issue ID {} for test {}", issueIdToUse, testKey);
        } else {
            // Fallback: Get issue ID from Jira
            logger.debug("Getting issue ID from Jira for test {} to use with GraphQL API", testKey);
            Ticket ticket = performTicket(testKey, new String[]{"id"});
            if (ticket == null || ticket.getId() == null || ticket.getId().isEmpty()) {
                throw new IOException("Cannot get issue ID for test " + testKey);
            }
            issueIdToUse = ticket.getId();
            logger.debug("Using Jira issue ID {} for test {}", issueIdToUse, testKey);
        }

        logger.debug("Setting gherkin for test {} (issue ID: {}) using GraphQL", testKey, issueIdToUse);

        xrayRestClient.updateCucumberTestGraphQL(issueIdToUse, gherkin);
        logger.info("Successfully set gherkin and updated test {} to Cucumber type using GraphQL", testKey);
    }

    /**
     * Sets dataset for a Cucumber test using X-ray GraphQL API.
     * Dataset contains parameters and data rows for data-driven testing.
     * 
     * @param testKey Jira ticket key (e.g., "TP-123")
     * @param dataset JSONObject containing parameters and rows
     * @param xrayIssueId Xray issue ID if available (from waitForXraySync), or null
     * @throws IOException if API call fails
     */
    private void setDataset(String testKey, JSONObject dataset, String xrayIssueId) throws IOException {
        if (dataset == null || dataset.isEmpty()) {
            logger.debug("No dataset to set for test {}", testKey);
            return;
        }

        logger.debug("Setting dataset for test {} using Internal API", testKey);

        // Use Xray issue ID if available (preferred), otherwise try ticket key, then fallback to Jira issue ID
        String issueIdToUse = null;
        String jiraIssueId = null;

        // Get Jira issue ID first
        Ticket ticket = performTicket(testKey, new String[]{"id"});
        if (ticket == null || ticket.getId() == null || ticket.getId().isEmpty()) {
            throw new IOException("Cannot get issue ID for test " + testKey);
        }
        jiraIssueId = ticket.getId();

        if (xrayIssueId != null && !xrayIssueId.isEmpty()) {
            issueIdToUse = xrayIssueId;
            logger.debug("Using Xray issue ID {} (testIssueId) for test {}", issueIdToUse, testKey);
        } else {
            // Fallback: Use Jira issue ID (they might be the same in some cases)
            issueIdToUse = jiraIssueId;
            logger.debug("Using Jira issue ID {} as testIssueId for test {}", issueIdToUse, testKey);
        }

        logger.debug("Setting dataset for test {} (Jira ID: {}, testIssueId: {}) using Internal API", 
                testKey, jiraIssueId, issueIdToUse);

        try {
            // Pass 'this' (JiraClient instance) for HTML fetching
            xrayRestClient.updateDatasetInternalAPI(this, testKey, issueIdToUse, dataset);
            logger.info("Successfully set dataset for test {} using Internal API", testKey);
        } catch (IOException e) {
            logger.warn("Internal API failed for dataset update: {}. " +
                       "This is expected if X-acpt token extraction fails. " +
                       "Dataset may need to be set manually through Xray UI.", e.getMessage());
            // Don't throw - this is a known limitation
        }
    }

    /**
     * Sets definition for a Precondition issue using X-ray GraphQL API.
     * Converts steps array to a definition string format.
     * 
     * @param preconditionKey Jira ticket key (e.g., "TP-123")
     * @param steps Array of step objects with action, data, expectedResult fields
     * @param xrayIssueId Xray issue ID if available (from waitForXraySync), or null
     * @throws IOException if API call fails
     */
    private void setPreconditionDefinition(String preconditionKey, JSONArray steps, String xrayIssueId) throws IOException {
        if (steps == null || steps.isEmpty()) {
            logger.debug("No steps to convert to definition for precondition {}", preconditionKey);
            return;
        }

        logger.debug("Setting definition for precondition {} using GraphQL", preconditionKey);
        
        // Convert steps array to definition string
        // Format: "Step 1: action -> data -> result\nStep 2: ..."
        StringBuilder definitionBuilder = new StringBuilder();
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String action = step.optString("action", step.optString("step", ""));
            String data = step.optString("data", "");
            String result = step.optString("result", step.optString("expectedResult", ""));
            
            if (i > 0) {
                definitionBuilder.append("\n");
            }
            definitionBuilder.append("Step ").append(i + 1).append(": ");
            if (!action.isEmpty()) {
                definitionBuilder.append(action);
            }
            if (!data.isEmpty()) {
                if (!action.isEmpty()) {
                    definitionBuilder.append(" -> ");
                }
                definitionBuilder.append(data);
            }
            if (!result.isEmpty()) {
                if (!action.isEmpty() || !data.isEmpty()) {
                    definitionBuilder.append(" -> ");
                }
                definitionBuilder.append(result);
            }
        }
        
        String definition = definitionBuilder.toString();
        
        // Use Xray issue ID if available (preferred), otherwise try to get it
        String issueIdToUse = null;
        
        if (xrayIssueId != null && !xrayIssueId.isEmpty()) {
            issueIdToUse = xrayIssueId;
            logger.debug("Using Xray issue ID {} for precondition {}", issueIdToUse, preconditionKey);
        } else {
            // Get issue ID from Jira
            logger.debug("Getting issue ID from Jira for precondition {} to use with GraphQL API", preconditionKey);
            Ticket ticket = performTicket(preconditionKey, new String[]{"id"});
            if (ticket == null || ticket.getId() == null || ticket.getId().isEmpty()) {
                throw new IOException("Cannot get issue ID for precondition " + preconditionKey);
            }
            issueIdToUse = ticket.getId();
            logger.debug("Using Jira issue ID {} for precondition {}", issueIdToUse, preconditionKey);
        }
        
        logger.debug("Setting definition for precondition {} (issue ID: {}) using GraphQL", 
                preconditionKey, issueIdToUse);
        logger.debug("Definition content (first 200 chars): {}", 
                definition.length() > 200 ? definition.substring(0, 200) + "..." : definition);
        
        JSONObject result = xrayRestClient.setPreconditionDefinitionGraphQL(issueIdToUse, definition);
        if (result != null && result.has("definition")) {
            String returnedDefinition = result.optString("definition", "");
            logger.info("Successfully set definition for precondition {} using GraphQL. Returned definition length: {}", 
                    preconditionKey, returnedDefinition.length());
        } else {
            logger.info("Successfully set definition for precondition {} using GraphQL (with issue ID)", preconditionKey);
        }
    }

    /**
     * Sets preconditions on a Jira ticket via X-ray API.
     * Note: GraphQL API requires Precondition type issues, but REST API may accept Test issues.
     * We try GraphQL first, and fall back to REST API if GraphQL fails with "not found" error.
     * 
     * @param testKey Jira ticket key (e.g., "PROJ-123")
     * @param preconditions Array of precondition ticket keys or objects
     * @param xrayIssueId Xray issue ID if available (from waitForXraySync), or null
     * @throws IOException if API call fails
     */
    private void setPreconditions(String testKey, JSONArray preconditions, String xrayIssueId) throws IOException {
        if (preconditions == null || preconditions.length() == 0) {
            logger.debug("No preconditions to set for test {}", testKey);
            return;
        }

        // Format preconditions - extract ticket keys
        JSONArray preconditionKeys = new JSONArray();
        for (int i = 0; i < preconditions.length(); i++) {
            Object item = preconditions.get(i);
            if (item instanceof String) {
                preconditionKeys.put(item);
            } else if (item instanceof JSONObject) {
                // Extract key from object if it's a JSON object
                JSONObject obj = (JSONObject) item;
                String key = obj.optString("key", obj.optString("id", null));
                if (key != null) {
                    preconditionKeys.put(key);
                }
            }
        }

        if (preconditionKeys.length() == 0) {
            logger.warn("No valid precondition keys found for test {}", testKey);
            return;
        }

        // Try GraphQL API first (requires Precondition type issues)
        try {
            // Use Xray issue ID if available (preferred), otherwise get from Jira
            String issueId = xrayIssueId;
            if (issueId == null || issueId.isEmpty()) {
                // Get the issue ID from Jira (GraphQL requires issue ID, not key)
                Ticket ticket = performTicket(testKey, new String[]{"id"});
                if (ticket == null || ticket.getId() == null || ticket.getId().isEmpty()) {
                    throw new IOException("Cannot get issue ID for test " + testKey);
                }
                issueId = ticket.getId();
                logger.debug("Using Jira issue ID {} for test {}", issueId, testKey);
            } else {
                logger.debug("Using Xray issue ID {} for test {}", issueId, testKey);
            }
            
            // Convert precondition keys to issue IDs
            JSONArray preconditionIssueIds = new JSONArray();
            for (int i = 0; i < preconditionKeys.length(); i++) {
                String preconditionKey = preconditionKeys.getString(i);
                try {
                    Ticket preconditionTicket = performTicket(preconditionKey, new String[]{"id"});
                    if (preconditionTicket != null && preconditionTicket.getId() != null) {
                        preconditionIssueIds.put(preconditionTicket.getId());
                    }
                } catch (IOException e) {
                    logger.warn("Error getting issue ID for precondition ticket {}: {}", preconditionKey, e.getMessage());
                }
            }

            if (preconditionIssueIds.length() > 0) {
                logger.debug("Attempting to set {} preconditions for test {} using GraphQL", 
                        preconditionIssueIds.length(), testKey);
                JSONArray results = addPreconditionsToTestGraphQL(issueId, preconditionIssueIds);
                // Check if GraphQL succeeded (added at least one precondition)
                if (results != null && results.length() > 0) {
                    logger.info("Successfully set {} preconditions for test {} using GraphQL", 
                            results.length(), testKey);
                    return;
                } else {
                    // GraphQL returned empty results - preconditions may not be Precondition type issues
                    logger.debug("GraphQL returned no results (preconditions may not be Precondition type), falling back to REST API");
                }
            }
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            // If GraphQL fails with "not found" error, it means preconditions are not Precondition type issues
            // Fall back to REST API which may accept Test issues as preconditions
            if (errorMsg.contains("not found") || errorMsg.contains("preconditions with the following ids")) {
                logger.debug("GraphQL failed (preconditions may not be Precondition type), falling back to REST API: {}", errorMsg);
            } else {
                // For other errors, log and fall back to REST API
                logger.warn("GraphQL failed, falling back to REST API: {}", errorMsg);
            }
        }

        // Fall back to REST API (may work with Test issues as preconditions)
        logger.debug("Using REST API to set {} preconditions for test {}", preconditionKeys.length(), testKey);
        // X-ray API v2 expects: PUT /api/v2/tests/{testKey}/preconditions
        // Note: "tests" (plural) not "test", and body is array directly
        String endpoint = "api/v2/tests/" + testKey + "/preconditions";
        
        xrayApiRequest(endpoint, "PUT", preconditionKeys.toString());
        logger.info("Successfully set {} preconditions for test {} using REST API", 
                preconditionKeys.length(), testKey);
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        // Extract steps, gherkin, dataset, and preconditions from FieldsInitializer before creating ticket
        JSONArray steps = null;
        String gherkin = null;
        JSONObject dataset = null;
        JSONArray preconditions = null;

        // Create a wrapper FieldsInitializer that intercepts field sets to extract steps/gherkin/dataset/preconditions
        FieldsInitializer wrappedFieldsInitializer = null;
        if (fieldsInitializer != null) {
            // Create a temporary Fields object to extract steps/gherkin/dataset/preconditions
            Fields tempFields = new Fields();
            fieldsInitializer.init(tempFields);
            
            gherkin = extractGherkin(tempFields);
            dataset = extractDataset(tempFields);
            steps = extractSteps(tempFields);
            preconditions = extractPreconditions(tempFields);
            
            // Create a new FieldsInitializer that excludes steps/gherkin/dataset/preconditions
            final Fields fieldsWithoutXray = tempFields;
            // Use Sets for O(1) lookup of steps, gherkin, dataset and preconditions field names
            final Set<String> stepsFieldNamesSet = new HashSet<>(Arrays.asList(STEPS_FIELD_NAMES));
            final Set<String> preconditionsFieldNamesSet = new HashSet<>(Arrays.asList(PRECONDITIONS_FIELD_NAMES));
            final Set<String> gherkinFieldNamesSet = new HashSet<>(Arrays.asList(GHERKIN_FIELD_NAMES));
            final Set<String> datasetFieldNamesSet = new HashSet<>(Arrays.asList(DATASET_FIELD_NAMES));
            wrappedFieldsInitializer = new FieldsInitializer() {
                @Override
                public void init(TrackerTicketFields fields) {
                    // Copy all fields except steps/gherkin/dataset/preconditions
                    JSONObject fieldsJson = fieldsWithoutXray.getJSONObject();
                    for (String key : fieldsJson.keySet()) {
                        // Skip X-ray specific fields using O(1) lookup
                        boolean isStepsField = stepsFieldNamesSet.contains(key);
                        boolean isPreconditionsField = preconditionsFieldNamesSet.contains(key);
                        boolean isGherkinField = gherkinFieldNamesSet.contains(key);
                        boolean isDatasetField = datasetFieldNamesSet.contains(key);
                        if (!isStepsField && !isPreconditionsField && !isGherkinField && !isDatasetField) {
                            fields.set(key, fieldsJson.get(key));
                        }
                    }
                }
            };
        }

        // Create Jira ticket first using parent implementation
        String ticketResponse = super.createTicketInProject(project, issueType, summary, description, wrappedFieldsInitializer);
        
        // Extract ticket key from response
        JSONObject responseJson = new JSONObject(ticketResponse);
        String ticketKey = responseJson.getString("key");

        // Wait for X-ray to sync the newly created ticket and get Xray issue ID
        // X-ray needs time to recognize the ticket before we can set steps/preconditions
        String xrayIssueId = waitForXraySync(ticketKey);

        // Set gherkin (Cucumber) or steps/definition and preconditions via X-ray API
        // Gherkin has precedence over steps
        if (gherkin != null) {
            try {
                setCucumberTest(ticketKey, gherkin, xrayIssueId);
                
                // Set dataset if present (for data-driven Cucumber tests)
                if (dataset != null) {
                    try {
                        setDataset(ticketKey, dataset, xrayIssueId);
                    } catch (IOException e) {
                        logger.error("Failed to set X-ray dataset for ticket {}: {}",
                                ticketKey, e.getMessage());
                        // Don't fail the entire operation if dataset setting fails
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to set X-ray gherkin (Cucumber) for ticket {}: {}",
                        ticketKey, e.getMessage());
                // Don't fail the entire operation if X-ray API calls fail
            }
        } else if (steps != null) {
            try {
                if ("Precondition".equalsIgnoreCase(issueType)) {
                    // For Precondition issues, convert steps to definition string
                    setPreconditionDefinition(ticketKey, steps, xrayIssueId);
                } else {
                    // For Test issues, set steps normally
                    setTestSteps(ticketKey, steps, xrayIssueId);
                }
            } catch (IOException e) {
                logger.error("Failed to set X-ray {} for ticket {}: {}",
                        "Precondition".equalsIgnoreCase(issueType) ? "definition" : "steps",
                        ticketKey, e.getMessage());
                // Don't fail the entire operation if X-ray API calls fail
                // The ticket was already created in Jira
            }
        }
        
        if (preconditions != null) {
            try {
                setPreconditions(ticketKey, preconditions, xrayIssueId);
            } catch (IOException e) {
                logger.error("Failed to set X-ray preconditions for ticket {} with {} preconditions: {}",
                        ticketKey, preconditions.length(), e.getMessage());
                // Don't fail the entire operation if X-ray API calls fail
                // The ticket was already created in Jira
            }
        }

        return ticketResponse;
    }

    /**
     * Creates a Precondition issue in Xray with optional steps (converted to definition).
     * This method is exposed as an MCP tool for JavaScript preprocessing.
     * 
     * @param project Project key (e.g., "TP")
     * @param summary Precondition summary
     * @param description Precondition description
     * @param steps Optional JSON array of steps (will be converted to definition format)
     * @return Created ticket key (e.g., "TP-1301")
     * @throws IOException if creation fails
     */
    @MCPTool(
            name = "jira_xray_create_precondition",
            description = "Create a Precondition issue in Xray with optional steps (converted to definition). Returns the created ticket key.",
            integration = "jira_xray",
            category = "xray_management"
    )
    public String createPrecondition(
            @MCPParam(name = "project", description = "Project key (e.g., 'TP')", required = true, example = "TP") String project,
            @MCPParam(name = "summary", description = "Precondition summary", required = true, example = "System is ready for testing") String summary,
            @MCPParam(name = "description", description = "Precondition description", required = false, example = "All system components are initialized") String description,
            @MCPParam(name = "steps", description = "Optional JSON array of steps in format [{\"action\": \"...\", \"data\": \"...\", \"result\": \"...\"}]. Will be converted to definition format.", required = false) String steps
    ) throws IOException {
        if (description == null) {
            description = "";
        }
        
        // Create FieldsInitializer with steps if provided
        TrackerClient.FieldsInitializer fieldsInitializer = null;
        if (steps != null && !steps.trim().isEmpty()) {
            try {
                // Handle steps that might be a PolyglotList string representation
                String stepsStr = steps;
                if (stepsStr.startsWith("(") && stepsStr.contains(")[")) {
                    // Extract array part from PolyglotList string like "(1)[{...}]"
                    // indexOf(")[") returns position of ')', we need position of '[' which is +1
                    int arrayStart = stepsStr.indexOf(")[") + 1;
                    stepsStr = stepsStr.substring(arrayStart);
                }
                JSONArray stepsArray = new JSONArray(stepsStr);
                final JSONArray finalSteps = stepsArray;
                fieldsInitializer = new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        fields.set("steps", finalSteps);
                    }
                };
            } catch (Exception e) {
                logger.warn("Failed to parse steps JSON for precondition: {}", e.getMessage());
                // Try using Gson as fallback
                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    Object parsed = gson.fromJson(steps, Object.class);
                    if (parsed instanceof java.util.List) {
                        JSONArray stepsArray = new JSONArray();
                        for (Object item : (java.util.List<?>) parsed) {
                            String itemJson = gson.toJson(item);
                            stepsArray.put(new JSONObject(itemJson));
                        }
                        final JSONArray finalSteps = stepsArray;
                        fieldsInitializer = new TrackerClient.FieldsInitializer() {
                            @Override
                            public void init(TrackerClient.TrackerTicketFields fields) {
                                fields.set("steps", finalSteps);
                            }
                        };
                    }
                } catch (Exception e2) {
                    logger.warn("Failed to parse steps with Gson fallback: {}", e2.getMessage());
                }
            }
        }
        
        String response = createTicketInProject(project, "Precondition", summary, description, fieldsInitializer);
        JSONObject responseJson = new JSONObject(response);
        return responseJson.getString("key");
    }

    @Override
    public String getTextFieldsOnly(ITicket ticket) {
        StringBuilder ticketDescription = null;
        try {
            ticketDescription = new StringBuilder(ticket.getTicketTitle());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ticketDescription.append("\n").append(ticket.getTicketDescription());
        if (customCodesOfConfigFields != null) {
            for (String customField : customCodesOfConfigFields) {
                if (customField != null) {
                    String value = ticket.getFields().getString(customField);
                    if (value != null) {
                        ticketDescription.append("\n").append(value);
                    }
                }
            }
        }
        return ticketDescription.toString();
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        // Empty implementation like BasicJiraClient
    }

    @Override
    public String[] getDefaultQueryFields() {
        return defaultJiraFields;
    }

    @Override
    public String[] getExtendedQueryFields() {
        return extendedJiraFields;
    }

    @Override
    public TextType getTextType() {
        return TextType.MARKDOWN;
    }

    /**
     * Enriches test tickets with X-ray test steps and preconditions.
     * This is a shared method used by both searchAndPerform overloads to avoid code duplication.
     * 
     * @param tickets List of tickets to enrich (will be modified in place)
     * @param searchQueryJQL JQL query used to fetch X-ray data
     */
    private void enrichTicketsWithXrayData(List<Ticket> tickets, String searchQueryJQL) {
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        
        logger.debug("Found {} tickets, enriching Test/Precondition issues with X-ray data", tickets.size());
        
        // Filter Test and Precondition issues
        List<Ticket> testTickets = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (ticket == null) {
                continue;
            }
            String ticketKey = ticket.getKey() != null ? ticket.getKey() : "unknown";
            try {
                String issueType = ticket.getIssueType();
                if (issueType != null && (issueType.equalsIgnoreCase("Test") || issueType.equalsIgnoreCase("Precondition"))) {
                    if (ticketKey != null && !ticketKey.isEmpty()) {
                        testTickets.add(ticket);
                        logger.debug("Found Test/Precondition ticket: {}", ticketKey);
                    }
                } else if (issueType == null) {
                    // If issuetype field is not available, treat all tickets as potential tests
                    // This happens when issuetype is not requested in fields array
                    if (ticketKey != null && !ticketKey.isEmpty()) {
                        testTickets.add(ticket);
                        logger.debug("Found ticket without issuetype field, treating as potential test: {}", ticketKey);
                    }
                }
            } catch (Exception e) {
                // If issuetype field is not available (not requested in fields), treat as potential test
                logger.debug("Issue type check failed for ticket {}, treating as potential test: {}", ticketKey, e.getMessage());
                if (ticketKey != null && !ticketKey.isEmpty()) {
                    testTickets.add(ticket);
                }
            }
        }
        
        if (testTickets.isEmpty()) {
            logger.debug("No Test or Precondition issues found in results, skipping X-ray enrichment");
            return;
        }
        
        // Enrich test tickets with X-ray data
        logger.info("Enriching {} Test/Precondition tickets with X-ray test steps and preconditions", testTickets.size());

        // Track performance metrics
        long startTime = System.currentTimeMillis();
        boolean usedParallelFetch = false;
        int totalBatches = 0;
        int parallelThreads = 0;

        try {
            // Fetch all tests - use parallel fetch if enabled
            JSONArray xrayTests;
            PropertyReader propertyReader = new PropertyReader();
            if (propertyReader.isXrayParallelFetchEnabled()) {
                usedParallelFetch = true;
                // Extract keys from existing testTickets - no separate Jira query needed!
                List<String> testKeys = new ArrayList<>();
                for (Ticket ticket : testTickets) {
                    String key = ticket.getKey();
                    if (key != null && !key.isEmpty()) {
                        testKeys.add(key);
                    }
                }

                logger.debug("Extracted {} test keys from tickets for parallel fetch", testKeys.size());

                // Read configuration with defaults
                int batchSize = propertyReader.getXrayParallelBatchSize();
                int parallelism = propertyReader.getXrayParallelThreads();
                long delayMs = propertyReader.getXrayParallelDelayMs();

                // Calculate metrics
                totalBatches = (int) Math.ceil((double) testKeys.size() / batchSize);
                parallelThreads = parallelism;

                logger.info("Using parallel fetch: batchSize={}, threads={}, delayMs={}", batchSize, parallelism, delayMs);
                xrayTests = xrayRestClient.getTestsByKeysGraphQLParallel(testKeys, searchQueryJQL, batchSize, parallelism, delayMs);
            } else {
                // Fallback to sequential fetch using the same JQL query
                xrayTests = xrayRestClient.getTestsByJQLGraphQL(searchQueryJQL);
            }
            logger.debug("Retrieved {} tests from X-ray GraphQL API", xrayTests.length());
            
            // Create a map of X-ray test data by ticket key for fast lookup
            Map<String, JSONObject> xrayDataMap = new HashMap<>();
            Set<String> preconditionKeys = new HashSet<>();
            
            for (int i = 0; i < xrayTests.length(); i++) {
                JSONObject xrayTest = xrayTests.getJSONObject(i);
                if (xrayTest.has("jira")) {
                    JSONObject jira = xrayTest.getJSONObject("jira");
                    String key = jira.optString("key", null);
                    if (key != null && !key.isEmpty()) {
                        xrayDataMap.put(key, xrayTest);
                        
                        // Collect all precondition keys for batch fetching from Jira
                        if (xrayTest.has("preconditions")) {
                            JSONObject preconditionsObj = xrayTest.getJSONObject("preconditions");
                            if (preconditionsObj.has("results")) {
                                JSONArray preconditions = preconditionsObj.getJSONArray("results");
                                for (int j = 0; j < preconditions.length(); j++) {
                                    JSONObject precondition = preconditions.getJSONObject(j);
                                    if (precondition.has("jira")) {
                                        JSONObject precJira = precondition.getJSONObject("jira");
                                        String precKey = precJira.optString("key", null);
                                        if (precKey != null && !precKey.isEmpty()) {
                                            preconditionKeys.add(precKey);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            logger.debug("X-ray data map contains {} entries", xrayDataMap.size());
            
            // Batch fetch precondition summary/description from Jira
            Map<String, Ticket> preconditionTicketsMap = new HashMap<>();
            if (!preconditionKeys.isEmpty()) {
                logger.debug("Fetching {} preconditions from Jira", preconditionKeys.size());
                for (String preconditionKey : preconditionKeys) {
                    try {
                        Ticket preconditionTicket = performTicket(preconditionKey, new String[]{"summary", "description"});
                        if (preconditionTicket != null) {
                            preconditionTicketsMap.put(preconditionKey, preconditionTicket);
                        }
                    } catch (IOException e) {
                        logger.debug("Failed to get Jira data for precondition {}: {}", preconditionKey, e.getMessage());
                    }
                }
            }
            
            // Enrich tickets with X-ray data
            for (Ticket ticket : testTickets) {
                try {
                    String ticketKey = ticket.getKey();
                    JSONObject xrayData = xrayDataMap.get(ticketKey);
                    
                    if (xrayData == null) {
                        logger.debug("No X-ray data found for ticket {} (may not be a test with steps)", ticketKey);
                        continue;
                    }
                    
                    Fields fieldsObj = ticket.getFields();
                    if (fieldsObj == null) {
                        logger.warn("Ticket {} has no fields object", ticketKey);
                        continue;
                    }
                    
                    // Add test steps
                    if (xrayData.has("steps")) {
                        JSONArray steps = xrayData.optJSONArray("steps");
                        if (steps != null && steps.length() > 0) {
                            fieldsObj.getJSONObject().put("xrayTestSteps", steps);
                            logger.info("✅ Added {} test steps to ticket {}", steps.length(), ticketKey);
                        } else {
                            logger.debug("Ticket {} has empty steps array", ticketKey);
                        }
                    } else {
                        logger.debug("Ticket {} has no steps in X-ray data", ticketKey);
                    }

                    // Add test type and gherkin for Cucumber tests
                    if (xrayData.has("testType")) {
                        JSONObject testType = xrayData.optJSONObject("testType");
                        if (testType != null) {
                            fieldsObj.getJSONObject().put("xrayTestType", testType);
                            logger.debug("Added test type {} to ticket {}", testType.optString("name", "unknown"), ticketKey);
                        }
                    }
                    if (xrayData.has("gherkin")) {
                        String gherkin = xrayData.optString("gherkin", null);
                        if (gherkin != null && !gherkin.isEmpty()) {
                            fieldsObj.getJSONObject().put("xrayGherkin", gherkin);
                            logger.info("✅ Added gherkin content to ticket {}", ticketKey);
                        }
                    }
                    
                    // Add dataset for tests with data-driven scenarios
                    if (xrayData.has("dataset")) {
                        JSONObject dataset = xrayData.optJSONObject("dataset");
                        if (dataset != null && !dataset.isEmpty()) {
                            fieldsObj.getJSONObject().put("xrayDataset", dataset);
                            int paramsCount = dataset.has("parameters") ? dataset.getJSONArray("parameters").length() : 0;
                            int rowsCount = dataset.has("rows") ? dataset.getJSONArray("rows").length() : 0;
                            logger.info("✅ Added dataset to ticket {} ({} parameters, {} rows)", ticketKey, paramsCount, rowsCount);
                        }
                    }
                    
                    // Add preconditions with definition from Xray and summary/description from Jira
                    if (xrayData.has("preconditions")) {
                        JSONObject preconditionsObj = xrayData.optJSONObject("preconditions");
                        if (preconditionsObj != null && preconditionsObj.has("results")) {
                            JSONArray preconditions = preconditionsObj.getJSONArray("results");
                            
                            // Enrich each precondition with Jira data
                            for (int i = 0; i < preconditions.length(); i++) {
                                JSONObject precondition = preconditions.getJSONObject(i);
                                
                                String preconditionKey = null;
                                if (precondition.has("jira")) {
                                    JSONObject jira = precondition.getJSONObject("jira");
                                    preconditionKey = jira.optString("key", null);
                                }
                                
                                if (preconditionKey != null && !preconditionKey.isEmpty()) {
                                    Ticket preconditionTicket = preconditionTicketsMap.get(preconditionKey);
                                    if (preconditionTicket != null) {
                                        Fields preconditionFields = preconditionTicket.getFields();
                                        if (preconditionFields != null) {
                                            if (preconditionFields.getSummary() != null) {
                                                precondition.put("summary", preconditionFields.getSummary());
                                            }
                                            if (preconditionFields.getDescription() != null) {
                                                precondition.put("description", preconditionFields.getDescription());
                                            }
                                        }
                                    }
                                }
                            }
                            
                            fieldsObj.getJSONObject().put("xrayPreconditions", preconditions);
                            logger.info("✅ Added {} preconditions to ticket {}", preconditions.length(), ticketKey);
                        }
                    }
                    
                } catch (Exception e) {
                    logger.warn("Error enriching ticket {} with X-ray data: {}", ticket.getKey(), e.getMessage(), e);
                    // Continue processing other tickets even if one fails
                }
            }

            // Calculate final metrics
            long endTime = System.currentTimeMillis();
            long elapsedMs = endTime - startTime;
            double elapsedSec = elapsedMs / 1000.0;

            // Count X-ray test steps and preconditions
            int totalSteps = 0;
            int totalPreconditions = 0;
            int cucumberTests = 0;

            for (Ticket ticket : testTickets) {
                try {
                    Fields fields = ticket.getFields();
                    if (fields != null && fields.getJSONObject() != null) {
                        JSONObject fieldsJson = fields.getJSONObject();

                        // Count test steps
                        if (fieldsJson.has("xrayTestSteps")) {
                            Object stepsObj = fieldsJson.get("xrayTestSteps");
                            if (stepsObj instanceof JSONArray) {
                                JSONArray steps = (JSONArray) stepsObj;
                                totalSteps += steps.length();
                            }
                        }

                        // Count preconditions
                        if (fieldsJson.has("xrayPreconditions")) {
                            Object precondsObj = fieldsJson.get("xrayPreconditions");
                            if (precondsObj instanceof JSONArray) {
                                JSONArray preconds = (JSONArray) precondsObj;
                                totalPreconditions += preconds.length();
                            }
                        }

                        // Count Cucumber tests (with gherkin)
                        if (fieldsJson.has("xrayGherkin")) {
                            Object gherkinObj = fieldsJson.get("xrayGherkin");
                            if (gherkinObj != null && !gherkinObj.toString().trim().isEmpty()) {
                                cucumberTests++;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error counting metrics for ticket {}: {}", ticket.getKey(), e.getMessage());
                }
            }

            // Print detailed summary
            logger.info("================================================================================");
            logger.info("X-RAY DATA ENRICHMENT SUMMARY");
            logger.info("================================================================================");
            logger.info("Total Test/Precondition Tickets:  {}", testTickets.size());
            logger.info("X-ray Tests Retrieved:             {}", xrayDataMap.size());
            logger.info("X-ray Cucumber Tests (Gherkin):    {}", cucumberTests);
            logger.info("X-ray Test Steps:                  {}", totalSteps);
            logger.info("X-ray Preconditions:               {}", totalPreconditions);
            logger.info("--------------------------------------------------------------------------------");
            logger.info("Fetch Method:                      {}", usedParallelFetch ? "PARALLEL" : "SEQUENTIAL");

            if (usedParallelFetch) {
                logger.info("Parallel Configuration:");
                logger.info("  - Batches:                       {}", totalBatches);
                logger.info("  - Threads:                       {}", parallelThreads);
                logger.info("  - Requests per thread:           ~{}", Math.ceil((double) totalBatches / parallelThreads));
            }

            logger.info("--------------------------------------------------------------------------------");
            logger.info(String.format("Time Elapsed:                      %.2f seconds (%d ms)", elapsedSec, elapsedMs));

            // Estimate sequential time for comparison
            if (usedParallelFetch && totalBatches > 0) {
                // Assume ~1.5 seconds per request for sequential
                double estimatedSequentialSec = totalBatches * 1.5;
                double speedup = (estimatedSequentialSec / elapsedSec);
                double improvement = ((estimatedSequentialSec - elapsedSec) / estimatedSequentialSec) * 100;

                logger.info(String.format("Estimated Sequential Time:         %.2f seconds", estimatedSequentialSec));
                logger.info(String.format("Performance Improvement:           %.1f%% faster (%.1fx speedup)", improvement, speedup));
            }

            logger.info("================================================================================");

        } catch (IOException e) {
            logger.warn("Failed to get X-ray data for JQL query {}: {}", searchQueryJQL, e.getMessage(), e);
            // Continue - tickets will be processed without X-ray enrichment
        }
    }

    /**
     * Overrides searchAndPerform to enrich test tickets with X-ray test steps and preconditions.
     * First calls the parent method to get tickets from Jira, then for each Test issue,
     * retrieves test steps and preconditions from X-ray GraphQL API and adds them to the ticket.
     * 
     * @param searchQueryJQL JQL search query
     * @param fields Array of field names to retrieve
     * @return List of tickets with X-ray test steps and preconditions added for Test issues
     * @throws Exception if search or X-ray API calls fail
     */
    @MCPTool(
            name = "jira_xray_search_tickets",
            description = "Search for Jira tickets using JQL query and enrich Test/Precondition issues with X-ray test steps and preconditions. Returns list of tickets with X-ray data.",
            integration = "jira_xray",
            category = "search"
    )
    @Override
    public List<Ticket> searchAndPerform(
            @MCPParam(name = "searchQueryJQL", description = "JQL search query (e.g., 'project = TP AND issueType = Test')", required = true, example = "project = TP AND issueType = Test") String searchQueryJQL,
            @MCPParam(name = "fields", description = "Array of field names to retrieve (e.g., ['summary', 'description', 'status'])", required = false, example = "summary,description,status") String[] fields
    ) throws Exception {
        // Ensure issuetype field is always included for X-ray enrichment
        fields = ensureIssueTypeField(fields);

        // First, call parent method to get tickets from Jira
        List<Ticket> tickets = super.searchAndPerform(searchQueryJQL, fields);
        
        if (tickets == null || tickets.isEmpty()) {
            logger.debug("No tickets found for query: {}", searchQueryJQL);
            return tickets;
        }
        
        // Enrich tickets with X-ray data
        enrichTicketsWithXrayData(tickets, searchQueryJQL);
        
        logger.debug("Finished enriching {} tickets with X-ray data", tickets.size());
        return tickets;
    }

    /**
     * Ensures that the issuetype field is included in the fields array for X-ray enrichment.
     * X-ray enrichment requires knowing the issue type to determine if it's a Test or Precondition.
     *
     * @param fields Array of field names to check and modify
     * @return Modified array with issuetype field included
     */
    private String[] ensureIssueTypeField(String[] fields) {
        logger.debug("ensureIssueTypeField called with fields: {}", fields != null ? String.join(",", fields) : "null");

        if (fields == null || fields.length == 0) {
            logger.debug("Fields array is null or empty, returning ['issuetype']");
            return new String[]{"issuetype"};
        }

        // Check if issuetype is already present (case-insensitive)
        for (String field : fields) {
            if ("issuetype".equalsIgnoreCase(field)) {
                logger.debug("Field 'issuetype' already present in fields array");
                return fields; // Already present, return as-is
            }
        }

        // Add issuetype to the fields array
        String[] newFields = new String[fields.length + 1];
        System.arraycopy(fields, 0, newFields, 0, fields.length);
        newFields[fields.length] = "issuetype";

        logger.info("Added 'issuetype' field to search fields for X-ray enrichment. Original fields: {}, New fields: {}",
                    String.join(",", fields), String.join(",", newFields));

        return newFields;
    }

    /**
     * Overrides searchAndPerform with Performer callback to enrich test tickets with X-ray test steps and preconditions.
     * For each ticket that is a Test issue, retrieves test steps and preconditions from X-ray GraphQL API
     * and adds them to the ticket before passing it to the Performer callback.
     * 
     * @param performer Performer callback to process each ticket
     * @param searchQueryJQL JQL search query
     * @param fields Array of field names to retrieve
     * @throws Exception if search or X-ray API calls fail
     */
    @Override
    public void searchAndPerform(Performer<Ticket> performer, String searchQueryJQL, String[] fields) throws Exception {
        // Check if we're already enriching to prevent recursion
        if (isEnriching.get()) {
            logger.debug("Already enriching tickets, calling parent searchAndPerform directly to avoid recursion");
            super.searchAndPerform(performer, searchQueryJQL, fields);
            return;
        }

        boolean needsEnrichment = shouldEnrichSearchResults(fields);

        // Strip sentinel from fields before passing to Jira API
        if (needsEnrichment && fields != null) {
            fields = stripXrayEnrichmentSentinel(fields);
        }

        if (!needsEnrichment) {
            // No enrichment needed — stream directly without collecting into a List
            logger.debug("XrayClient: Skipping X-ray enrichment (not requested), using streaming search for JQL: {}", searchQueryJQL);
            super.searchAndPerform(performer, searchQueryJQL, fields);
            return;
        }

        logger.debug("XrayClient.searchAndPerform with Performer: enriching tickets with X-ray data for JQL: {}", searchQueryJQL);
        isEnriching.set(true);

        try {
            // First, get all tickets using parent's searchAndPerform with Performer (not the List version to avoid recursion)
            // We use a collector Performer to gather tickets first, then enrich them
            List<Ticket> tickets = new ArrayList<>();
            super.searchAndPerform(ticket -> {
                tickets.add(ticket);
                return false; // Continue collecting all tickets
            }, searchQueryJQL, fields);
            
            if (tickets == null || tickets.isEmpty()) {
                logger.debug("No tickets found for query: {}", searchQueryJQL);
                return;
            }
            
            // Enrich tickets with X-ray data
            enrichTicketsWithXrayData(tickets, searchQueryJQL);
        
            // Now process all tickets with the Performer callback
            logger.debug("Processing {} tickets with Performer callback", tickets.size());
            for (Ticket ticket : tickets) {
                boolean shouldBreak = performer.perform(ticket);
                if (shouldBreak) {
                    logger.debug("Performer requested to stop processing");
                    break;
                }
            }
            
            logger.debug("Finished processing tickets with Performer callback");
        } finally {
            isEnriching.set(false);
        }
    }

    boolean shouldEnrichSearchResults(String[] fields) {
        return isXrayEnrichmentRequested(fields) || isXrayEnrichmentEnabledByDefault();
    }

    boolean isXrayEnrichmentEnabledByDefault() {
        return new PropertyReader().isXrayEnrichmentEnabledByDefault();
    }

    String[] stripXrayEnrichmentSentinel(String[] fields) {
        if (fields == null || fields.length == 0) {
            return fields;
        }
        return java.util.Arrays.stream(fields)
                .filter(f -> !FIELD_XRAY_ENRICHMENT.equalsIgnoreCase(f))
                .toArray(String[]::new);
    }

    private boolean isXrayEnrichmentRequested(String[] fields) {
        return fields != null && java.util.Arrays.stream(fields)
                .anyMatch(f -> FIELD_XRAY_ENRICHMENT.equalsIgnoreCase(f));
    }

    /**
     * Gets test details and steps using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONObject with test details including steps and preconditions, or null if not found
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_get_test_details",
            description = "Get test details including steps and preconditions using X-ray GraphQL API. Returns JSONObject with test details.",
            integration = "jira_xray",
            category = "test_retrieval"
    )
    public JSONObject getTestDetailsGraphQL(
            @MCPParam(name = "testKey", description = "Jira ticket key (e.g., 'TP-909')", required = true, example = "TP-909") String testKey
    ) throws IOException {
        return xrayRestClient.getTestDetailsGraphQL(testKey);
    }

    /**
     * Gets test steps using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONArray of test steps, or empty array if none found
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_get_test_steps",
            description = "Get test steps for a test issue using X-ray GraphQL API. Returns JSONArray of test steps.",
            integration = "jira_xray",
            category = "test_retrieval"
    )
    public JSONArray getTestStepsGraphQL(
            @MCPParam(name = "testKey", description = "Jira ticket key (e.g., 'TP-909')", required = true, example = "TP-909") String testKey
    ) throws IOException {
        return xrayRestClient.getTestStepsGraphQL(testKey);
    }

    /**
     * Gets preconditions using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONArray of precondition objects with jira fields, or empty array if none found
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_get_preconditions",
            description = "Get preconditions for a test issue using X-ray GraphQL API. Returns JSONArray of precondition objects.",
            integration = "jira_xray",
            category = "test_retrieval"
    )
    public JSONArray getPreconditionsGraphQL(
            @MCPParam(name = "testKey", description = "Jira ticket key (e.g., 'TP-909')", required = true, example = "TP-909") String testKey
    ) throws IOException {
        return xrayRestClient.getPreconditionsGraphQL(testKey);
    }
    
    /**
     * Gets Precondition details including definition using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param preconditionKey Jira ticket key (e.g., "TP-910")
     * @return JSONObject with precondition details including definition, or null if not found
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_get_precondition_details",
            description = "Get Precondition details including definition using X-ray GraphQL API. Returns JSONObject with precondition details.",
            integration = "jira_xray",
            category = "test_retrieval"
    )
    public JSONObject getPreconditionDetailsGraphQL(
            @MCPParam(name = "preconditionKey", description = "Jira ticket key (e.g., 'TP-910')", required = true, example = "TP-910") String preconditionKey
    ) throws IOException {
        return xrayRestClient.getPreconditionDetailsGraphQL(preconditionKey);
    }

    /**
     * Adds a test step to a test issue using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param issueId Jira issue ID (e.g., "12345")
     * @param action Step action (e.g., "Enter username")
     * @param data Step data (e.g., "test_user")
     * @param result Step expected result (e.g., "Username accepted")
     * @return JSONObject with created step details (id, action, data, result), or null if failed
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_add_test_step",
            description = "Add a single test step to a test issue using X-ray GraphQL API. Returns JSONObject with created step details.",
            integration = "jira_xray",
            category = "test_management"
    )
    public JSONObject addTestStepGraphQL(
            @MCPParam(name = "issueId", description = "Jira issue ID (e.g., '12345')", required = true, example = "12345") String issueId,
            @MCPParam(name = "action", description = "Step action (e.g., 'Enter username')", required = true, example = "Enter username") String action,
            @MCPParam(name = "data", description = "Step data (e.g., 'test_user')", required = false, example = "test_user") String data,
            @MCPParam(name = "result", description = "Step expected result (e.g., 'Username accepted')", required = false, example = "Username accepted") String result
    ) throws IOException {
        return xrayRestClient.addTestStepGraphQL(issueId, action, data, result);
    }

    /**
     * Adds multiple test steps to a test issue using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param issueId Jira issue ID (e.g., "12345")
     * @param steps JSONArray of step objects, each with "action", "data", and "result" fields
     * @return JSONArray of created step objects, or empty array if failed
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_add_test_steps",
            description = "Add multiple test steps to a test issue using X-ray GraphQL API. Returns JSONArray of created step objects.",
            integration = "jira_xray",
            category = "test_management"
    )
    public JSONArray addTestStepsGraphQL(
            @MCPParam(name = "issueId", description = "Jira issue ID (e.g., '12345')", required = true, example = "12345") String issueId,
            @MCPParam(name = "steps", description = "JSON array string of step objects, each with 'action', 'data', and 'result' fields (e.g., '[{\"action\":\"Enter username\",\"data\":\"test_user\",\"result\":\"Username accepted\"}]')", required = true, example = "[{\"action\":\"Enter username\",\"data\":\"test_user\",\"result\":\"Username accepted\"}]") JSONArray steps
    ) throws IOException {
        return xrayRestClient.addTestStepsGraphQL(issueId, steps);
    }

    /**
     * Adds a single precondition to a test issue using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param testIssueId Jira issue ID of the test (e.g., "12345")
     * @param preconditionIssueId Jira issue ID of the precondition (e.g., "12346")
     * @return JSONObject with result, or null if failed
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_add_precondition_to_test",
            description = "Add a single precondition to a test issue using X-ray GraphQL API. Returns JSONObject with result.",
            integration = "jira_xray",
            category = "test_management"
    )
    public JSONObject addPreconditionToTestGraphQL(
            @MCPParam(name = "testIssueId", description = "Jira issue ID of the test (e.g., '12345')", required = true, example = "12345") String testIssueId,
            @MCPParam(name = "preconditionIssueId", description = "Jira issue ID of the precondition (e.g., '12346')", required = true, example = "12346") String preconditionIssueId
    ) throws IOException {
        return xrayRestClient.addPreconditionToTestGraphQL(testIssueId, preconditionIssueId);
    }

    /**
     * Adds multiple preconditions to a test issue using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param testIssueId Jira issue ID of the test (e.g., "12345")
     * @param preconditionIssueIds JSONArray of precondition issue IDs (e.g., ["12346", "12347"])
     * @return JSONArray of results, or empty array if failed
     * @throws IOException if API call fails
     */
    @MCPTool(
            name = "jira_xray_add_preconditions_to_test",
            description = "Add multiple preconditions to a test issue using X-ray GraphQL API. Returns JSONArray of results.",
            integration = "jira_xray",
            category = "test_management"
    )
    public JSONArray addPreconditionsToTestGraphQL(
            @MCPParam(name = "testIssueId", description = "Jira issue ID of the test (e.g., '12345')", required = true, example = "12345") String testIssueId,
            @MCPParam(name = "preconditionIssueIds", description = "JSON array string of precondition issue IDs (e.g., '[\"12346\", \"12347\"]')", required = true, example = "[\"12346\", \"12347\"]") JSONArray preconditionIssueIds
    ) throws IOException {
        return xrayRestClient.addPreconditionsToTestGraphQL(testIssueId, preconditionIssueIds);
    }

}
