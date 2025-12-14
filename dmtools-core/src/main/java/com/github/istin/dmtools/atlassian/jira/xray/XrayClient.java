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
    private static final String[] STEPS_FIELD_NAMES = {"steps", "testSteps", "xraySteps", "test_steps"};
    private static final String[] PRECONDITIONS_FIELD_NAMES = {"preconditions", "xrayPreconditions", "testPreconditions", "test_preconditions"};

    private static volatile XrayClient instance;
    private final String[] defaultJiraFields;
    private final String[] extendedJiraFields;
    private final String[] customCodesOfConfigFields;

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
        int maxAttempts = 10; // Maximum 10 attempts
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
        // Extract steps and preconditions from FieldsInitializer before creating ticket
        JSONArray steps = null;
        JSONArray preconditions = null;

        // Create a wrapper FieldsInitializer that intercepts field sets to extract steps/preconditions
        FieldsInitializer wrappedFieldsInitializer = null;
        if (fieldsInitializer != null) {
            // Create a temporary Fields object to extract steps/preconditions
            Fields tempFields = new Fields();
            fieldsInitializer.init(tempFields);
            
            steps = extractSteps(tempFields);
            preconditions = extractPreconditions(tempFields);
            
            // Create a new FieldsInitializer that excludes steps/preconditions
            final Fields fieldsWithoutXray = tempFields;
            // Use Sets for O(1) lookup of steps and preconditions field names
            final Set<String> stepsFieldNamesSet = new HashSet<>(Arrays.asList(STEPS_FIELD_NAMES));
            final Set<String> preconditionsFieldNamesSet = new HashSet<>(Arrays.asList(PRECONDITIONS_FIELD_NAMES));
            wrappedFieldsInitializer = new FieldsInitializer() {
                @Override
                public void init(TrackerTicketFields fields) {
                    // Copy all fields except steps/preconditions
                    JSONObject fieldsJson = fieldsWithoutXray.getJSONObject();
                    for (String key : fieldsJson.keySet()) {
                        // Skip X-ray specific fields using O(1) lookup
                        boolean isStepsField = stepsFieldNamesSet.contains(key);
                        boolean isPreconditionsField = preconditionsFieldNamesSet.contains(key);
                        if (!isStepsField && !isPreconditionsField) {
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

        // Set steps/definition and preconditions via X-ray API
        // For Precondition issues, set definition instead of steps
        if (steps != null) {
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
    public List<? extends ITicket> getTestCases(ITicket ticket) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public TextType getTextType() {
        return TextType.MARKDOWN;
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
    @Override
    public List<Ticket> searchAndPerform(String searchQueryJQL, String[] fields) throws Exception {
        // First, call parent method to get tickets from Jira
        List<Ticket> tickets = super.searchAndPerform(searchQueryJQL, fields);
        
        if (tickets == null || tickets.isEmpty()) {
            logger.debug("No tickets found for query: {}", searchQueryJQL);
            return tickets;
        }
        
        logger.debug("Found {} tickets, enriching with X-ray test steps and preconditions", tickets.size());
        
        // Filter Test and Precondition issues
        List<Ticket> testTickets = new ArrayList<>();
        Map<String, Ticket> ticketMap = new HashMap<>();
        for (Ticket ticket : tickets) {
            try {
                String issueType = ticket.getIssueType();
                if (issueType != null && (issueType.equalsIgnoreCase("Test") || issueType.equalsIgnoreCase("Precondition"))) {
                    String ticketKey = ticket.getKey();
                    if (ticketKey != null && !ticketKey.isEmpty()) {
                        testTickets.add(ticket);
                        ticketMap.put(ticketKey, ticket);
                    }
                }
            } catch (NullPointerException e) {
                // Skip tickets without issue type (may happen if fields are not fully loaded)
                String ticketKey = ticket != null ? ticket.getKey() : "unknown";
                logger.debug("Skipping ticket {} - issue type is not available", ticketKey);
            }
        }
        
        if (testTickets.isEmpty()) {
            logger.debug("No Test or Precondition issues found in results");
            return tickets;
        }
        
        // Use the same JQL query to get all X-ray data via GraphQL with pagination
        // This will fetch all tests matching the JQL query, not just the first 100
        logger.debug("Fetching X-ray data for {} test tickets using JQL query: {} (will fetch all matching tests via pagination)", 
                testTickets.size(), searchQueryJQL);
        
        try {
            // Fetch all tests with pagination (uses paginationLimit from xrayRestClient, but will fetch all pages)
            JSONArray xrayTests = xrayRestClient.getTestsByJQLGraphQL(searchQueryJQL);
            
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
            
            logger.debug("Retrieved {} tests from X-ray GraphQL API", xrayDataMap.size());
            
            // Batch fetch precondition summary/description from Jira
            Map<String, Ticket> preconditionTicketsMap = new HashMap<>();
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
            
            // Enrich tickets with X-ray data
            for (Ticket ticket : testTickets) {
                try {
                    String ticketKey = ticket.getKey();
                    JSONObject xrayData = xrayDataMap.get(ticketKey);
                    
                    if (xrayData == null) {
                        logger.debug("No X-ray data found for ticket {}", ticketKey);
                        continue;
                    }
                    
                    Fields fieldsObj = ticket.getFields();
                    if (fieldsObj == null) {
                        continue;
                    }
                    
                    // Add test steps
                    if (xrayData.has("steps")) {
                        JSONArray steps = xrayData.getJSONArray("steps");
                        if (steps != null && steps.length() > 0) {
                            fieldsObj.getJSONObject().put("xrayTestSteps", steps);
                            logger.debug("Added {} test steps to ticket {}", steps.length(), ticketKey);
                        }
                    }
                    
                    // Add preconditions with definition from Xray and summary/description from Jira
                    if (xrayData.has("preconditions")) {
                        JSONObject preconditionsObj = xrayData.getJSONObject("preconditions");
                        if (preconditionsObj.has("results")) {
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
                            logger.debug("Added {} preconditions to ticket {} (with definition from Xray and summary/description from Jira)", 
                                    preconditions.length(), ticketKey);
                        }
                    }
                    
                } catch (Exception e) {
                    logger.warn("Error enriching ticket {} with X-ray data: {}", ticket.getKey(), e.getMessage());
                    // Continue processing other tickets even if one fails
                }
            }
            
        } catch (IOException e) {
            logger.warn("Failed to get X-ray data for JQL query {}: {}", searchQueryJQL, e.getMessage());
            // Continue - tickets will be returned without X-ray enrichment
        }
        
        logger.debug("Finished enriching {} tickets with X-ray data", tickets.size());
        return tickets;
    }

    /**
     * Gets test details and steps using X-ray GraphQL API.
     * Delegates to XrayRestClient.
     * 
     * @param testKey Jira ticket key (e.g., "TP-909")
     * @return JSONObject with test details including steps and preconditions, or null if not found
     * @throws IOException if API call fails
     */
    public JSONObject getTestDetailsGraphQL(String testKey) throws IOException {
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
    public JSONArray getTestStepsGraphQL(String testKey) throws IOException {
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
    public JSONArray getPreconditionsGraphQL(String testKey) throws IOException {
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
    public JSONObject getPreconditionDetailsGraphQL(String preconditionKey) throws IOException {
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
    public JSONObject addTestStepGraphQL(String issueId, String action, String data, String result) throws IOException {
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
    public JSONArray addTestStepsGraphQL(String issueId, JSONArray steps) throws IOException {
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
    public JSONObject addPreconditionToTestGraphQL(String testIssueId, String preconditionIssueId) throws IOException {
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
    public JSONArray addPreconditionsToTestGraphQL(String testIssueId, JSONArray preconditionIssueIds) throws IOException {
        return xrayRestClient.addPreconditionsToTestGraphQL(testIssueId, preconditionIssueIds);
    }

}
