package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    // Instance fields for server-managed mode (override static fields)
    private final String instanceXrayBasePath;
    private final String instanceXrayClientId;
    private final String instanceXrayClientSecret;

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

    // X-ray bearer token management
    private volatile String xrayAccessToken;
    private volatile long xrayTokenExpiryTime;
    private static final long TOKEN_REFRESH_BUFFER_MS = 60000; // Refresh 1 minute before expiry

    // HTTP client for X-ray API calls (separate from Jira client)
    private final OkHttpClient xrayHttpClient;

    // Field names for steps and preconditions extraction
    private static final String[] STEPS_FIELD_NAMES = {"steps", "testSteps", "xraySteps", "test_steps"};
    private static final String[] PRECONDITIONS_FIELD_NAMES = {"preconditions", "xrayPreconditions", "testPreconditions", "test_preconditions"};

    private static XrayClient instance;
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
        
        // Store X-ray configuration for instance use
        this.instanceXrayBasePath = xrayBasePath;
        this.instanceXrayClientId = xrayClientId;
        this.instanceXrayClientSecret = xrayClientSecret;
        
        if (jiraAuthType != null) {
            setAuthType(jiraAuthType);
        }
        setLogEnabled(isLoggingEnabled);
        setWaitBeforePerform(isWaitBeforePerform);
        if (sleepTimeRequest != null) {
            setSleepTimeRequest(sleepTimeRequest);
        }
        setClearCache(isClearCache);

        // Initialize X-ray HTTP client (for X-ray API calls)
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(60, TimeUnit.SECONDS);
        builder.writeTimeout(60, TimeUnit.SECONDS);
        builder.readTimeout(60, TimeUnit.SECONDS);
        this.xrayHttpClient = builder.build();

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
     * Gets the X-ray base path (instance field for server-managed, static for property-based)
     */
    private String getXrayBasePath() {
        return instanceXrayBasePath != null ? instanceXrayBasePath : XRAY_BASE_PATH;
    }

    /**
     * Gets the X-ray client ID (instance field for server-managed, static for property-based)
     */
    private String getXrayClientId() {
        return instanceXrayClientId != null ? instanceXrayClientId : XRAY_CLIENT_ID;
    }

    /**
     * Gets the X-ray client secret (instance field for server-managed, static for property-based)
     */
    private String getXrayClientSecret() {
        return instanceXrayClientSecret != null ? instanceXrayClientSecret : XRAY_CLIENT_SECRET;
    }

    /**
     * Obtains or refreshes the X-ray bearer access token using Xray's proprietary authentication mechanism.
     * <p>
     * Sends the client ID and client secret to the Xray /authenticate endpoint to receive a bearer token.
     * 
     * @return Access token for X-ray API calls
     * @throws IOException if token acquisition fails
     */
    private synchronized String getXrayAccessToken() throws IOException {
        // Check if we have a valid token
        if (xrayAccessToken != null && System.currentTimeMillis() < xrayTokenExpiryTime - TOKEN_REFRESH_BUFFER_MS) {
            return xrayAccessToken;
        }

        // Request new token
        String tokenUrl = getXrayBasePath();
        if (tokenUrl == null || tokenUrl.isEmpty()) {
            throw new IOException("XRAY_BASE_PATH is not configured");
        }
        if (!tokenUrl.endsWith("/")) {
            tokenUrl += "/";
        }
        tokenUrl += "authenticate";

        try {
            // Prepare request body as JSON (X-ray API expects JSON, not form-encoded)
            JSONObject json = new JSONObject();
            json.put("client_id", getXrayClientId());
            json.put("client_secret", getXrayClientSecret());
            RequestBody requestBody = RequestBody.create(json.toString(), MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(tokenUrl)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build();

            // Use try-with-resources to ensure response is closed
            try (Response response = xrayHttpClient.newCall(request).execute()) {
                // Store response body first - can only be consumed once
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to obtain X-ray access token. HTTP " + response.code() + ": " + 
                            (responseBody.isEmpty() ? "No error body" : responseBody));
                }

                // X-ray returns the token as a plain string; expiry is assumed to be 1 hour (3600 seconds)
                xrayAccessToken = responseBody.trim();
                long expiresIn = 3600; // Default to 1 hour
                xrayTokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000);

                logger.debug("Successfully obtained X-ray access token, expires in {} seconds", expiresIn);
                return xrayAccessToken;
            }

        } catch (Exception e) {
            logger.error("Error obtaining X-ray access token", e);
            throw new IOException("Failed to obtain X-ray access token: " + e.getMessage(), e);
        }
    }

    /**
     * Makes an authenticated request to X-ray API.
     * 
     * @param endpoint X-ray API endpoint (relative to XRAY_BASE_PATH)
     * @param method HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param body Request body (can be null)
     * @return Response body as string
     * @throws IOException if request fails
     */
    private String xrayApiRequest(String endpoint, String method, String body) throws IOException {
        String accessToken = getXrayAccessToken();
        
        String url = getXrayBasePath();
        if (url == null || url.isEmpty()) {
            throw new IOException("XRAY_BASE_PATH is not configured");
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += endpoint;

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json");

        if (body != null) {
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json"));
            switch (method.toUpperCase()) {
                case "POST":
                    requestBuilder.post(requestBody);
                    break;
                case "PUT":
                    requestBuilder.put(requestBody);
                    break;
                case "PATCH":
                    requestBuilder.patch(requestBody);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
        } else {
            if ("GET".equalsIgnoreCase(method)) {
                requestBuilder.get();
            } else if ("DELETE".equalsIgnoreCase(method)) {
                requestBuilder.delete();
            } else {
                throw new IllegalArgumentException("Method " + method + " requires a body");
            }
        }

        Request request = requestBuilder.build();

        try (Response response = xrayHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("X-ray API request failed. HTTP " + response.code() + ": " + errorBody);
            }

            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : "";
        }
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
     * Sets test steps on a Jira ticket via X-ray API.
     * 
     * @param testKey Jira ticket key (e.g., "PROJ-123")
     * @param steps Array of step objects with action, data, expectedResult fields
     * @throws IOException if API call fails
     */
    private void setTestSteps(String testKey, JSONArray steps) throws IOException {
        if (steps == null || steps.length() == 0) {
            logger.debug("No steps to set for test {}", testKey);
            return;
        }

        // X-ray API expects the steps array directly, not wrapped in an object
        String endpoint = "api/v2/test/" + testKey + "/steps";
        xrayApiRequest(endpoint, "PUT", steps.toString());
        logger.info("Successfully set {} steps for test {}", steps.length(), testKey);
    }

    /**
     * Sets preconditions on a Jira ticket via X-ray API.
     * Preconditions are separate Jira tickets linked to the test.
     * 
     * @param testKey Jira ticket key (e.g., "PROJ-123")
     * @param preconditions Array of precondition ticket keys or objects
     * @throws IOException if API call fails
     */
    private void setPreconditions(String testKey, JSONArray preconditions) throws IOException {
        if (preconditions == null || preconditions.length() == 0) {
            logger.debug("No preconditions to set for test {}", testKey);
            return;
        }

        // Format preconditions for X-ray API
        // X-ray expects an array of precondition issue keys
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

        // X-ray API expects the request body to be the array directly, not wrapped in an object
        String endpoint = "api/v2/test/" + testKey + "/preconditions";
        xrayApiRequest(endpoint, "PUT", preconditionKeys.toString());
        logger.info("Successfully set {} preconditions for test {}", preconditionKeys.length(), testKey);
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

        // Set steps and preconditions via X-ray API
        if (steps != null) {
            try {
                setTestSteps(ticketKey, steps);
            } catch (IOException e) {
                logger.error("Failed to set X-ray steps for ticket {} with {} steps: {}", 
                        ticketKey, steps.length(), e.getMessage());
                // Don't fail the entire operation if X-ray API calls fail
                // The ticket was already created in Jira
            }
        }
        if (preconditions != null) {
            try {
                setPreconditions(ticketKey, preconditions);
            } catch (IOException e) {
                logger.error("Failed to set X-ray preconditions for ticket {} with {} preconditions: {}", 
                        ticketKey, preconditions.length(), e.getMessage());
                // Don't fail the entire operation if X-ray API calls fail
                // The ticket was already created in Jira
            }
        }

        return ticketResponse;
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
}
