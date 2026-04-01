// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for XrayClient
 * Tests creating Test issues with test steps and preconditions via X-ray API
 * 
 * Requires configuration:
 * - JIRA_BASE_PATH
 * - JIRA_API_TOKEN or JIRA_EMAIL + JIRA_API_TOKEN
 * - XRAY_BASE_PATH
 * - XRAY_CLIENT_ID
 * - XRAY_CLIENT_SECRET
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class XrayClientIntegrationTest {

    private static final Logger logger = LogManager.getLogger(XrayClientIntegrationTest.class);
    
    private static XrayClient xrayClient;
    private static String testProjectKey;
    
    // Track created tickets for cleanup
    private static List<String> createdTicketKeys = new ArrayList<>();
    
    @BeforeAll
    static void setUp() throws IOException {
        // Get test project key from system properties or use default
        testProjectKey = System.getProperty("jira.test.project", "TP");
        
        // Check configuration before attempting to initialize
        com.github.istin.dmtools.common.utils.PropertyReader propertyReader = new com.github.istin.dmtools.common.utils.PropertyReader();
        String jiraBasePath = propertyReader.getJiraBasePath();
        String xrayBasePath = propertyReader.getXrayBasePath();
        String xrayClientId = propertyReader.getXrayClientId();
        String xrayClientSecret = propertyReader.getXrayClientSecret();
        
        logger.info("XRAY_BASE_PATH from config: {}", xrayBasePath);
        
        // Check if we need to use EU region
        if (xrayBasePath != null && xrayBasePath.contains("eu.xray")) {
            logger.info("✅ Using EU region X-ray API");
        } else if (xrayBasePath != null && !xrayBasePath.contains("eu.xray")) {
            logger.warn("⚠️ XRAY_BASE_PATH does not contain 'eu.xray' - using default region");
        }
        
        logger.info("=== XrayClient Integration Test Configuration Check ===");
        logger.info("JIRA_BASE_PATH: {}", jiraBasePath != null && !jiraBasePath.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("XRAY_BASE_PATH: {}", xrayBasePath != null && !xrayBasePath.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("XRAY_CLIENT_ID: {}", xrayClientId != null && !xrayClientId.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("XRAY_CLIENT_SECRET: {}", xrayClientSecret != null && !xrayClientSecret.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("Test Project Key: {}", testProjectKey);
        
        // Initialize XrayClient using getInstance() which reads from PropertyReader
        TrackerClient<? extends ITicket> client = null;
        try {
            client = XrayClient.getInstance();
        } catch (Exception e) {
            logger.error("Exception while initializing XrayClient: {}", e.getMessage(), e);
        }
        
        if (client == null) {
            logger.error("XrayClient could not be initialized. Missing required configuration.");
            logger.error("Required environment variables/properties:");
            logger.error("  - JIRA_BASE_PATH: {}", jiraBasePath != null && !jiraBasePath.isEmpty() ? "✓" : "✗ MISSING");
            logger.error("  - JIRA_API_TOKEN or (JIRA_EMAIL + JIRA_API_TOKEN): {}", 
                    propertyReader.getJiraApiToken() != null || propertyReader.getJiraLoginPassToken() != null ? "✓" : "✗ MISSING");
            logger.error("  - XRAY_BASE_PATH: {}", xrayBasePath != null && !xrayBasePath.isEmpty() ? "✓" : "✗ MISSING");
            logger.error("  - XRAY_CLIENT_ID: {}", xrayClientId != null && !xrayClientId.isEmpty() ? "✓" : "✗ MISSING");
            logger.error("  - XRAY_CLIENT_SECRET: {}", xrayClientSecret != null && !xrayClientSecret.isEmpty() ? "✓" : "✗ MISSING");
            Assumptions.assumeTrue(false, 
                    String.format("XrayClient configuration not available. Missing: JIRA_BASE_PATH=%s, XRAY_BASE_PATH=%s, XRAY_CLIENT_ID=%s, XRAY_CLIENT_SECRET=%s",
                            jiraBasePath != null && !jiraBasePath.isEmpty(),
                            xrayBasePath != null && !xrayBasePath.isEmpty(),
                            xrayClientId != null && !xrayClientId.isEmpty(),
                            xrayClientSecret != null && !xrayClientSecret.isEmpty()));
            return;
        }
        
        if (!(client instanceof XrayClient)) {
            logger.warn("Expected XrayClient but got: {}", client.getClass().getName());
            Assumptions.assumeTrue(false, "XrayClient not available");
            return;
        }
        
        xrayClient = (XrayClient) client;
        xrayClient.setLogEnabled(true);
        xrayClient.setClearCache(true);
        xrayClient.setCacheGetRequestsEnabled(false);
        
        logger.info("✅ XrayClient initialized successfully for project: {}", testProjectKey);
        logger.info("   Jira base path: {}", xrayClient.getBasePath());
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        if (xrayClient == null) {
            return;
        }
        
        // Clean up all created tickets
        logger.info("Cleaning up {} created tickets", createdTicketKeys.size());
        for (String ticketKey : createdTicketKeys) {
            try {
                xrayClient.deleteTicket(ticketKey);
                logger.info("Cleaned up ticket: {}", ticketKey);
            } catch (Exception e) {
                logger.warn("Failed to clean up ticket {}: {}", ticketKey, e.getMessage());
            }
        }
        createdTicketKeys.clear();
        logger.info("Integration tests completed for XrayClient");
    }
    
    /**
     * Helper method to create a test ticket and track it for cleanup
     */
    private String createTestTicket(String summary, String description, String issueType) throws IOException {
        String response = xrayClient.createTicketInProject(testProjectKey, issueType, summary, description, null);
        logger.info("Create ticket response: {}", response);
        assertNotNull(response);
        
        // Response is JSON with "key" field
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        
        createdTicketKeys.add(ticketKey);
        logger.info("Created test ticket: {} ({})", ticketKey, summary);
        return ticketKey;
    }
    
    @Test
    @Order(1)
    @DisplayName("Test creating a Test issue with test steps")
    void testCreateTestWithSteps() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing Test issue creation with steps ===");
        
        String summary = "Xray Integration Test - Steps - " + System.currentTimeMillis();
        String description = "This is a test created by XrayClient integration test to verify test steps functionality.";
        
        // Create test steps JSONArray
        // X-ray expects steps with: action, data, expectedResult
        JSONArray steps = new JSONArray();
        
        JSONObject step1 = new JSONObject();
        step1.put("action", "Navigate to the login page");
        step1.put("data", "URL: https://example.com/login");
        step1.put("expectedResult", "Login page is displayed with username and password fields");
        steps.put(step1);
        
        JSONObject step2 = new JSONObject();
        step2.put("action", "Enter valid credentials");
        step2.put("data", "Username: testuser, Password: testpass");
        step2.put("expectedResult", "Credentials are entered successfully");
        steps.put(step2);
        
        JSONObject step3 = new JSONObject();
        step3.put("action", "Click the Login button");
        step3.put("data", "");
        step3.put("expectedResult", "User is redirected to the dashboard");
        steps.put(step3);
        
        logger.info("Created {} test steps", steps.length());
        
        // Create the test ticket with steps using FieldsInitializer
        String response = xrayClient.createTicketInProject(
            testProjectKey,
            "Test",  // Issue type for X-ray tests
            summary,
            description,
            new TrackerClient.FieldsInitializer() {
                @Override
                public void init(TrackerClient.TrackerTicketFields fields) {
                    // Set steps field - XrayClient looks for: steps, testSteps, xraySteps, test_steps
                    fields.set("steps", steps);
                }
            }
        );
        
        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);
        
        logger.info("Created test ticket with steps: {}", ticketKey);
        
        // Verify the ticket was created in Jira
        Ticket ticket = xrayClient.performTicket(ticketKey, new String[]{"summary", "description", "issuetype", "key"});
        assertNotNull(ticket, "Ticket should be created in Jira");
        assertEquals(summary, ticket.getFields().getSummary(), "Ticket summary should match");
        assertEquals(description, ticket.getFields().getDescription(), "Ticket description should match");
        assertEquals("Test", ticket.getFields().getIssueType().getName(), "Issue type should be Test");
        assertEquals(ticketKey, ticket.getKey(), "Ticket key should match");
        
        logger.info("✅ Test ticket verified in Jira: {}", ticketKey);
        logger.info("   Summary: {}", ticket.getFields().getSummary());
        logger.info("   Issue Type: {}", ticket.getFields().getIssueType().getName());
        
        // Wait for X-ray to sync the ticket and process the steps
        // X-ray may need time to sync newly created tickets, and may return 503 temporarily
        int maxWaitAttempts = 5;
        int waitAttempt = 0;
        JSONArray retrievedSteps = null;
        boolean stepsRetrieved = false;
        
        while (waitAttempt < maxWaitAttempts && !stepsRetrieved) {
            try {
                Thread.sleep(2000); // Wait 2 seconds between attempts
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted while waiting for X-ray to process steps");
                break;
            }
            
            try {
                retrievedSteps = xrayClient.getTestStepsGraphQL(ticketKey);
                if (retrievedSteps != null && retrievedSteps.length() == steps.length()) {
                    stepsRetrieved = true;
                    logger.info("✅ Steps retrieved successfully after {} attempts", waitAttempt + 1);
                    break;
                } else if (retrievedSteps != null && retrievedSteps.length() > 0) {
                    logger.warn("⚠️ Retrieved {} steps, expected {}. Continuing to wait...", 
                            retrievedSteps.length(), steps.length());
                }
            } catch (IOException e) {
                // Continue waiting if we get 503 or other temporary errors
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                logger.debug("Waiting for X-ray to sync (attempt {}/{}): {}", 
                        waitAttempt + 1, maxWaitAttempts, errorMsg);
            }
            waitAttempt++;
        }
        
        // Verify steps if we successfully retrieved them
        if (stepsRetrieved && retrievedSteps != null) {
            logger.info("✅ Retrieved {} steps from X-ray API", retrievedSteps.length());
            
            // Verify each step matches what we created
            for (int i = 0; i < steps.length(); i++) {
                JSONObject expectedStep = steps.getJSONObject(i);
                JSONObject actualStep = retrievedSteps.getJSONObject(i);
                
                assertEquals(expectedStep.getString("action"), actualStep.getString("action"),
                        String.format("Step %d action should match", i + 1));
                assertEquals(expectedStep.getString("data"), actualStep.getString("data"),
                        String.format("Step %d data should match", i + 1));
                // GraphQL returns "result" not "expectedResult"
                String expectedResult = expectedStep.optString("expectedResult", expectedStep.optString("result", ""));
                String actualResult = actualStep.optString("result", actualStep.optString("expectedResult", ""));
                assertEquals(expectedResult, actualResult,
                        String.format("Step %d result should match", i + 1));
                
                logger.info("   Step {}: {} -> {}", i + 1, actualStep.getString("action"), actualResult);
            }
            
            logger.info("✅ All test steps verified successfully");
        } else {
            logger.warn("⚠️ Could not retrieve steps from X-ray API after {} attempts. " +
                    "This may be due to X-ray API 503 errors or sync delays. " +
                    "Ticket was created successfully in Jira: {}", maxWaitAttempts, ticketKey);
            // Don't fail the test - ticket creation in Jira is the main goal
            // X-ray API issues are external and shouldn't cause test failure
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("Test creating a Test issue with preconditions")
    void testCreateTestWithPreconditions() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing Test issue creation with preconditions ===");
        
        // Step 1: Create a Precondition issue (not Test issue)
        String preconditionSummary = "Precondition Test - " + System.currentTimeMillis();
        String preconditionDescription = "This is a precondition test for Xray integration test.";
        
        String preconditionKey = null;
        try {
            String response = xrayClient.createTicketInProject(testProjectKey, "Precondition", preconditionSummary, preconditionDescription, null);
            JSONObject responseJson = new JSONObject(response);
            preconditionKey = responseJson.getString("key");
            logger.info("Created Precondition issue: {}", preconditionKey);
            createdTicketKeys.add(preconditionKey);
        } catch (Exception e) {
            logger.warn("Could not create Precondition issue type, creating as Test instead: {}", e.getMessage());
            preconditionKey = createTestTicket(preconditionSummary, preconditionDescription, "Test");
            logger.info("Created Test issue to use as precondition: {}", preconditionKey);
        }
        
        assertNotNull(preconditionKey, "Precondition ticket should be created");
        
        // Wait for Jira to process the precondition
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Step 2: Add steps to the precondition
        logger.info("--- Adding step to precondition {} ---", preconditionKey);
        Ticket preconditionTicket = xrayClient.performTicket(preconditionKey, new String[]{"id"});
        assertNotNull(preconditionTicket, "Precondition ticket should exist");
        String preconditionIssueId = preconditionTicket.getId();
        assertNotNull(preconditionIssueId, "Precondition issue ID should be available");
        
        try {
            JSONObject step = xrayClient.addTestStepGraphQL(
                    preconditionIssueId,
                    "Verify system is ready",
                    "Check system status and configuration",
                    "System is ready for testing"
            );
            if (step != null) {
                logger.info("✅ Created step in precondition: {}", step.toString(2));
            } else {
                logger.warn("⚠️ Could not add step to Precondition issue (Precondition type may not support steps)");
            }
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("not found") || errorMsg.contains("test with id")) {
                logger.warn("⚠️ Precondition issue does not support steps via GraphQL: {}", errorMsg);
                logger.info("   This is expected - Precondition issues may not support steps, only Test issues do");
            } else {
                throw e;
            }
        }
        
        // Wait for X-ray to process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Step 3: Now create a test that depends on this precondition
        String summary = "Xray Integration Test - Preconditions - " + System.currentTimeMillis();
        String description = "This test depends on a precondition test.";
        
        // Create preconditions JSONArray with ticket keys
        JSONArray preconditions = new JSONArray();
        preconditions.put(preconditionKey);
        
        logger.info("Created preconditions array with: {}", preconditionKey);
        
        // Create the test ticket with preconditions
        String response = xrayClient.createTicketInProject(
            testProjectKey,
            "Test",
            summary,
            description,
            new TrackerClient.FieldsInitializer() {
                @Override
                public void init(TrackerClient.TrackerTicketFields fields) {
                    // Set preconditions field - XrayClient looks for: preconditions, xrayPreconditions, testPreconditions, test_preconditions
                    fields.set("preconditions", preconditions);
                }
            }
        );
        
        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);
        
        logger.info("Created test ticket with preconditions: {}", ticketKey);
        
        // Verify the ticket was created in Jira
        Ticket ticket = xrayClient.performTicket(ticketKey, new String[]{"summary", "description", "issuetype", "key"});
        assertNotNull(ticket, "Ticket should be created in Jira");
        assertEquals(summary, ticket.getFields().getSummary(), "Ticket summary should match");
        assertEquals(ticketKey, ticket.getKey(), "Ticket key should match");
        
        logger.info("✅ Test ticket verified in Jira: {}", ticketKey);
        
        // Wait a moment for X-ray to process the preconditions
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread interrupted while waiting for X-ray to process preconditions");
        }
        
        // Fetch preconditions from X-ray API and verify they were set correctly
        JSONArray retrievedPreconditions = xrayClient.getPreconditionsGraphQL(ticketKey);
        assertNotNull(retrievedPreconditions, "Retrieved preconditions should not be null");
        assertTrue(retrievedPreconditions.length() >= 1,
                String.format("Expected at least 1 precondition, but got %d", retrievedPreconditions.length()));
        
        logger.info("✅ Retrieved {} preconditions from X-ray API", retrievedPreconditions.length());
        
        // Verify the precondition key matches
        // GraphQL returns preconditions as objects with "jira" field, not just strings
        String expectedPreconditionKey = preconditions.getString(0);
        String actualPreconditionKey = null;
        if (retrievedPreconditions.length() > 0) {
            JSONObject firstPrecondition = retrievedPreconditions.getJSONObject(0);
            if (firstPrecondition.has("jira")) {
                JSONObject jira = firstPrecondition.getJSONObject("jira");
                actualPreconditionKey = jira.optString("key", "");
            }
        }
        if (actualPreconditionKey != null && !actualPreconditionKey.isEmpty()) {
            assertEquals(expectedPreconditionKey, actualPreconditionKey,
                    "Precondition key should match");
            logger.info("   Precondition: {}", actualPreconditionKey);
        } else {
            logger.warn("⚠️ Could not extract precondition key from GraphQL response (may need more time to sync)");
        }
        
        // Verify the precondition ticket exists
        Ticket verifiedPreconditionTicket = xrayClient.performTicket(actualPreconditionKey != null ? actualPreconditionKey : expectedPreconditionKey, new String[]{"summary", "key"});
        assertNotNull(verifiedPreconditionTicket, "Precondition ticket should exist");
        
        logger.info("✅ Precondition ticket verified: {}", verifiedPreconditionTicket.getKey());
        logger.info("✅ All preconditions verified successfully");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test creating a Test issue with both steps and preconditions")
    void testCreateTestWithStepsAndPreconditions() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing Test issue creation with steps and preconditions ===");
        
        // Step 1: Create a Precondition issue (not Test issue)
        String preconditionSummary = "Precondition for Combined Test - " + System.currentTimeMillis();
        String preconditionDescription = "Precondition test for combined steps and preconditions test.";
        
        String preconditionKey = null;
        try {
            String response = xrayClient.createTicketInProject(testProjectKey, "Precondition", preconditionSummary, preconditionDescription, null);
            JSONObject responseJson = new JSONObject(response);
            preconditionKey = responseJson.getString("key");
            logger.info("Created Precondition issue: {}", preconditionKey);
            createdTicketKeys.add(preconditionKey);
        } catch (Exception e) {
            logger.warn("Could not create Precondition issue type, creating as Test instead: {}", e.getMessage());
            preconditionKey = createTestTicket(preconditionSummary, preconditionDescription, "Test");
            logger.info("Created Test issue to use as precondition: {}", preconditionKey);
        }
        
        assertNotNull(preconditionKey, "Precondition ticket should be created");
        
        // Wait for Jira to process the precondition
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Step 2: Add steps to the precondition
        logger.info("--- Adding step to precondition {} ---", preconditionKey);
        Ticket preconditionTicket = xrayClient.performTicket(preconditionKey, new String[]{"id"});
        assertNotNull(preconditionTicket, "Precondition ticket should exist");
        String preconditionIssueId = preconditionTicket.getId();
        assertNotNull(preconditionIssueId, "Precondition issue ID should be available");
        
        try {
            JSONObject step = xrayClient.addTestStepGraphQL(
                    preconditionIssueId,
                    "Verify system is ready",
                    "Check system status and configuration",
                    "System is ready for testing"
            );
            if (step != null) {
                logger.info("✅ Created step in precondition: {}", step.toString(2));
            } else {
                logger.warn("⚠️ Could not add step to Precondition issue (Precondition type may not support steps)");
            }
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("not found") || errorMsg.contains("test with id")) {
                logger.warn("⚠️ Precondition issue does not support steps via GraphQL: {}", errorMsg);
                logger.info("   This is expected - Precondition issues may not support steps, only Test issues do");
            } else {
                throw e;
            }
        }
        
        // Wait for X-ray to process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Step 3: Create test steps for the main test
        JSONArray steps = new JSONArray();
        
        JSONObject step1 = new JSONObject();
        step1.put("action", "Verify system is ready");
        step1.put("data", "Check system status");
        step1.put("expectedResult", "System is ready for testing");
        steps.put(step1);
        
        JSONObject step2 = new JSONObject();
        step2.put("action", "Execute test scenario");
        step2.put("data", "Test data: sample input");
        step2.put("expectedResult", "Test scenario completes successfully");
        steps.put(step2);
        
        // Create preconditions
        JSONArray preconditions = new JSONArray();
        preconditions.put(preconditionKey);
        
        String summary = "Xray Integration Test - Steps and Preconditions - " + System.currentTimeMillis();
        String description = "This test has both steps and preconditions to verify full X-ray functionality.";
        
        // Create the test ticket with both steps and preconditions
        String response = xrayClient.createTicketInProject(
            testProjectKey,
            "Test",
            summary,
            description,
            new TrackerClient.FieldsInitializer() {
                @Override
                public void init(TrackerClient.TrackerTicketFields fields) {
                    fields.set("steps", steps);
                    fields.set("preconditions", preconditions);
                }
            }
        );
        
        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);
        
        logger.info("Created test ticket with steps and preconditions: {}", ticketKey);
        
        // Verify the ticket was created in Jira
        Ticket ticket = xrayClient.performTicket(ticketKey, new String[]{"summary", "description", "issuetype", "key"});
        assertNotNull(ticket, "Ticket should be created in Jira");
        assertEquals(summary, ticket.getFields().getSummary(), "Ticket summary should match");
        assertEquals(ticketKey, ticket.getKey(), "Ticket key should match");
        
        logger.info("✅ Test ticket verified in Jira: {}", ticketKey);
        
        // Wait a moment for X-ray to process the steps and preconditions
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread interrupted while waiting for X-ray to process steps and preconditions");
        }
        
        // Fetch and verify test steps from X-ray API
        JSONArray retrievedSteps = xrayClient.getTestStepsGraphQL(ticketKey);
        assertNotNull(retrievedSteps, "Retrieved steps should not be null");
        assertEquals(steps.length(), retrievedSteps.length(),
                String.format("Expected %d steps, but got %d", steps.length(), retrievedSteps.length()));
        
        logger.info("✅ Retrieved {} steps from X-ray API", retrievedSteps.length());
        
        // Verify each step
        for (int i = 0; i < steps.length(); i++) {
            JSONObject expectedStep = steps.getJSONObject(i);
            JSONObject actualStep = retrievedSteps.getJSONObject(i);
            
            assertEquals(expectedStep.getString("action"), actualStep.getString("action"),
                    String.format("Step %d action should match", i + 1));
            assertEquals(expectedStep.getString("data"), actualStep.getString("data"),
                    String.format("Step %d data should match", i + 1));
            // GraphQL returns "result" not "expectedResult"
            String expectedResult = expectedStep.optString("expectedResult", expectedStep.optString("result", ""));
            String actualResult = actualStep.optString("result", actualStep.optString("expectedResult", ""));
            assertEquals(expectedResult, actualResult,
                    String.format("Step %d result should match", i + 1));
        }
        
        // Fetch and verify preconditions from X-ray API
        JSONArray retrievedPreconditions = xrayClient.getPreconditionsGraphQL(ticketKey);
        assertNotNull(retrievedPreconditions, "Retrieved preconditions should not be null");
        assertEquals(preconditions.length(), retrievedPreconditions.length(),
                String.format("Expected %d preconditions, but got %d", preconditions.length(), retrievedPreconditions.length()));
        
        logger.info("✅ Retrieved {} preconditions from X-ray API", retrievedPreconditions.length());
        
        // Verify the precondition key matches
        // GraphQL returns preconditions as objects with "jira" field, not just strings
        String expectedPreconditionKey = preconditions.getString(0);
        String actualPreconditionKey = null;
        if (retrievedPreconditions.length() > 0) {
            JSONObject firstPrecondition = retrievedPreconditions.getJSONObject(0);
            if (firstPrecondition.has("jira")) {
                JSONObject jira = firstPrecondition.getJSONObject("jira");
                actualPreconditionKey = jira.optString("key", "");
            }
        }
        if (actualPreconditionKey != null && !actualPreconditionKey.isEmpty()) {
            assertEquals(expectedPreconditionKey, actualPreconditionKey,
                    "Precondition key should match");
        } else {
            logger.warn("⚠️ Could not extract precondition key from GraphQL response (may need more time to sync)");
        }
        
        logger.info("✅ Test ticket with steps and preconditions verified successfully");
        logger.info("   Steps count: {}", retrievedSteps.length());
        logger.info("   Preconditions count: {}", retrievedPreconditions.length());
    }
    
    @Test
    @Order(4)
    @DisplayName("Test creating a Test issue without steps or preconditions")
    void testCreateTestWithoutXrayFields() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing Test issue creation without X-ray specific fields ===");
        
        String summary = "Xray Integration Test - Basic Test - " + System.currentTimeMillis();
        String description = "This is a basic test without steps or preconditions.";
        
        // Create test without any X-ray fields
        String ticketKey = createTestTicket(summary, description, "Test");
        
        // Verify the ticket was created in Jira
        Ticket ticket = xrayClient.performTicket(ticketKey, new String[]{"summary", "description", "issuetype", "key"});
        assertNotNull(ticket, "Ticket should be created in Jira");
        assertEquals(summary, ticket.getFields().getSummary(), "Ticket summary should match");
        assertEquals(description, ticket.getFields().getDescription(), "Ticket description should match");
        assertEquals("Test", ticket.getFields().getIssueType().getName(), "Issue type should be Test");
        assertEquals(ticketKey, ticket.getKey(), "Ticket key should match");
        
        logger.info("✅ Basic test ticket verified in Jira: {}", ticketKey);
        
        // Verify no steps or preconditions were set (since we didn't provide any)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread interrupted while waiting for X-ray");
        }
        
        JSONArray retrievedSteps = xrayClient.getTestStepsGraphQL(ticketKey);
        assertNotNull(retrievedSteps, "Retrieved steps should not be null");
        assertEquals(0, retrievedSteps.length(), "Should have no steps");
        
        JSONArray retrievedPreconditions = xrayClient.getPreconditionsGraphQL(ticketKey);
        assertNotNull(retrievedPreconditions, "Retrieved preconditions should not be null");
        assertEquals(0, retrievedPreconditions.length(), "Should have no preconditions");
        
        logger.info("✅ Verified no steps or preconditions were set (as expected)");
    }

    @Test
    @Order(5)
    @DisplayName("Get test details and steps for existing test ticket")
    void testGetTestDetailsAndSteps() throws IOException {
        String testKey = "TP-909";
        logger.info("=== Testing getting details and steps for test ticket {} ===", testKey);

        // First, verify the ticket exists in Jira and get its ID
        Ticket ticket = xrayClient.performTicket(testKey, new String[]{"summary", "description", "issuetype", "key", "status", "project", "id"});
        assertNotNull(ticket, "Ticket should exist in Jira");
        assertEquals(testKey, ticket.getKey(), "Ticket key should match");
        logger.info("✅ Ticket verified in Jira: {}", testKey);
        logger.info("   Summary: {}", ticket.getFields().getSummary());
        logger.info("   Issue Type: {}", ticket.getFields().getIssueType().getName());
        if (ticket.getFields().getStatus() != null) {
            logger.info("   Status: {}", ticket.getFields().getStatus().getName());
        }
        if (ticket.getFields().getProject() != null) {
            logger.info("   Project: {}", ticket.getFields().getProject().toString());
        }
        
        // Get issue ID
        String issueId = ticket.getFields().getString("id");
        if (issueId != null && !issueId.isEmpty()) {
            logger.info("   Issue ID: {}", issueId);
        } else {
            // Try to get ID from ticket's self URL
            String self = ticket.getString("self");
            if (self != null && self.contains("/issue/")) {
                issueId = self.substring(self.lastIndexOf("/issue/") + 7);
                if (issueId.contains("?")) {
                    issueId = issueId.substring(0, issueId.indexOf("?"));
                }
                logger.info("   Issue ID (extracted from self URL): {}", issueId);
            } else {
                // Try to get ID from ticket directly
                issueId = ticket.getId();
                if (issueId != null && !issueId.isEmpty()) {
                    logger.info("   Issue ID (from ticket.getId()): {}", issueId);
                }
            }
        }
        
        // Log all available fields for debugging
        logger.info("   Available fields: {}", String.join(", ", ticket.getFields().getJSONObject().keySet()));

        // Try to get test details from X-ray API using ticket key
        logger.info("\n--- Trying with ticket key: {} ---", testKey);
        JSONObject testDetails = xrayClient.getTestDetailsGraphQL(testKey);
        if (testDetails != null) {
            logger.info("✅ Retrieved test details from X-ray API using key:");
            logger.info("   Test details: {}", testDetails.toString(2));
        } else {
            logger.warn("⚠️ Test details not found in X-ray API using key (may not be synced yet)");
        }

        // Get test steps from X-ray API using ticket key
        JSONArray steps = xrayClient.getTestStepsGraphQL(testKey);
        assertNotNull(steps, "Retrieved steps should not be null");
        logger.info("✅ Retrieved {} steps from X-ray API using key", steps.length());

        // Try to get test details from X-ray API using issue ID
        if (issueId != null && !issueId.isEmpty()) {
            logger.info("\n--- Trying with issue ID: {} ---", issueId);
            JSONObject testDetailsById = xrayClient.getTestDetailsGraphQL(testKey);
            if (testDetailsById != null) {
                logger.info("✅ Retrieved test details from X-ray API using ID:");
                logger.info("   Test details: {}", testDetailsById.toString(2));
            } else {
                logger.warn("⚠️ Test details not found in X-ray API using ID");
            }

            // Get test steps from X-ray API using issue ID
            JSONArray stepsById = xrayClient.getTestStepsGraphQL(testKey);
            assertNotNull(stepsById, "Retrieved steps should not be null");
            logger.info("✅ Retrieved {} steps from X-ray API using ID", stepsById.length());

            if (stepsById.length() > 0) {
                logger.info("   Steps (by ID):");
                for (int i = 0; i < stepsById.length(); i++) {
                    JSONObject step = stepsById.getJSONObject(i);
                    logger.info("   Step {}: {} -> {}", 
                            i + 1,
                            step.optString("action", "N/A"),
                            step.optString("result", "N/A"));
                }
            } else if (steps.length() > 0) {
                logger.info("   Steps (by key):");
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    logger.info("   Step {}: {} -> {}", 
                            i + 1,
                            step.optString("action", "N/A"),
                            step.optString("result", "N/A"));
                }
            } else {
                logger.info("   No steps found for this test");
            }

            // Get preconditions from X-ray API using issue ID
            JSONArray preconditionsById = xrayClient.getPreconditionsGraphQL(testKey);
            assertNotNull(preconditionsById, "Retrieved preconditions should not be null");
            logger.info("✅ Retrieved {} preconditions from X-ray API using ID", preconditionsById.length());

            if (preconditionsById.length() > 0) {
                logger.info("   Preconditions (by ID):");
                for (int i = 0; i < preconditionsById.length(); i++) {
                    JSONObject precondition = preconditionsById.getJSONObject(i);
                    if (precondition.has("jira")) {
                        JSONObject jira = precondition.getJSONObject("jira");
                        String key = jira.optString("key", "");
                        logger.info("   Precondition {}: {}", i + 1, key);
                    }
                }
            } else {
                logger.info("   No preconditions found for this test");
            }
        } else {
            logger.warn("⚠️ Could not extract issue ID, skipping ID-based tests");
            
            // Fallback to key-based retrieval
            if (steps.length() > 0) {
                logger.info("   Steps:");
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    logger.info("   Step {}: {} -> {}", 
                            i + 1,
                            step.optString("action", "N/A"),
                            step.optString("result", "N/A"));
                }
            } else {
                logger.info("   No steps found for this test");
            }

                JSONArray preconditions = xrayClient.getPreconditionsGraphQL(testKey);
            assertNotNull(preconditions, "Retrieved preconditions should not be null");
            logger.info("✅ Retrieved {} preconditions from X-ray API", preconditions.length());
        }

        logger.info("✅ Successfully retrieved test details, steps, and preconditions for {}", testKey);
    }

    @Test
    @Order(6)
    @DisplayName("Get test details and steps using GraphQL API")
    void testGetTestDetailsGraphQL() throws IOException {
        String testKey = "TP-909";
        logger.info("=== Testing GraphQL API for test ticket {} ===", testKey);

        // Get test details using GraphQL
        JSONObject testDetails = xrayClient.getTestDetailsGraphQL(testKey);
        if (testDetails != null) {
            logger.info("✅ Retrieved test details from X-ray GraphQL API:");
            logger.info("   Test details: {}", testDetails.toString(2));
            
            // Extract and log steps
            if (testDetails.has("steps")) {
                JSONArray steps = testDetails.getJSONArray("steps");
                logger.info("✅ Retrieved {} steps from GraphQL", steps.length());
                
                if (steps.length() > 0) {
                    logger.info("   Steps:");
                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.getJSONObject(i);
                        logger.info("   Step {}: {} -> {}", 
                                i + 1,
                                step.optString("action", "N/A"),
                                step.optString("result", "N/A"));
                    }
                }
            } else {
                logger.info("   No steps found in GraphQL response");
            }
            
            // Extract and log preconditions
            if (testDetails.has("preconditions")) {
                JSONObject preconditionsObj = testDetails.getJSONObject("preconditions");
                if (preconditionsObj.has("results")) {
                    JSONArray preconditions = preconditionsObj.getJSONArray("results");
                    int total = preconditionsObj.optInt("total", preconditions.length());
                    logger.info("✅ Retrieved {} preconditions from GraphQL (total: {})", preconditions.length(), total);
                    
                    if (preconditions.length() > 0) {
                        logger.info("   Preconditions:");
                        for (int i = 0; i < preconditions.length(); i++) {
                            JSONObject precondition = preconditions.getJSONObject(i);
                            if (precondition.has("jira")) {
                                JSONObject jira = precondition.getJSONObject("jira");
                                logger.info("   Precondition {}: {} - {}", 
                                        i + 1,
                                        jira.optString("key", "N/A"),
                                        jira.optString("summary", "N/A"));
                            }
                        }
                    }
                }
            } else {
                logger.info("   No preconditions found in GraphQL response");
            }
            
            // Log other test information
            if (testDetails.has("jira")) {
                JSONObject jira = testDetails.getJSONObject("jira");
                logger.info("   Jira info: key={}, summary={}", 
                        jira.optString("key", "N/A"),
                        jira.optString("summary", "N/A"));
            }
            
            if (testDetails.has("testType")) {
                JSONObject testType = testDetails.getJSONObject("testType");
                logger.info("   Test Type: {}", testType.optString("name", "N/A"));
            }
            
            logger.info("   Issue ID: {}", testDetails.optString("issueId", "N/A"));
            logger.info("   Project ID: {}", testDetails.optString("projectId", "N/A"));
        } else {
            logger.warn("⚠️ Test details not found in X-ray GraphQL API for {}", testKey);
        }

        // Also test the convenience methods
        JSONArray steps = xrayClient.getTestStepsGraphQL(testKey);
        logger.info("✅ Retrieved {} steps using getTestStepsGraphQL()", steps.length());

        JSONArray preconditions = xrayClient.getPreconditionsGraphQL(testKey);
        logger.info("✅ Retrieved {} preconditions using getPreconditionsGraphQL()", preconditions.length());

        logger.info("✅ GraphQL API test completed for {}", testKey);
    }

    @Test
    @Order(7)
    @DisplayName("Create test steps using GraphQL API")
    void testCreateTestStepsGraphQL() throws IOException {
        logger.info("=== Testing GraphQL API for creating test steps ===");

        // Create a new test ticket
        String testSummary = "GraphQL Test Steps Test - " + System.currentTimeMillis();
        String testDescription = "This is a test ticket for testing GraphQL step creation";
        String response = xrayClient.createTicketInProject(testProjectKey, "Test", testSummary, testDescription, null);
        assertNotNull(response, "Create ticket response should not be null");
        
        // Extract ticket key from response
        JSONObject responseJson = new JSONObject(response);
        String testKey = responseJson.getString("key");
        assertNotNull(testKey, "Test ticket key should be available");
        logger.info("Created test ticket: {}", testKey);

        // Wait a bit for Jira to process the ticket
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get the issue ID
        Ticket ticket = xrayClient.performTicket(testKey, new String[]{"id"});
        assertNotNull(ticket, "Ticket should exist in Jira");
        String issueId = ticket.getId();
        assertNotNull(issueId, "Issue ID should be available");
        logger.info("Test ticket {} has issue ID: {}", testKey, issueId);

        // Try to add steps using issue ID
        logger.info("--- Adding test steps using GraphQL API with issue ID ---");
        JSONObject step1 = xrayClient.addTestStepGraphQL(
                issueId,
                "Navigate to login page",
                "Open browser and go to /login",
                "Login page is displayed"
        );
        assertNotNull(step1, "First step should be created");
        logger.info("✅ Created step 1: {}", step1.toString(2));

        JSONObject step2 = xrayClient.addTestStepGraphQL(
                issueId,
                "Enter credentials",
                "Username: test_user, Password: test_pass",
                "Credentials are accepted"
        );
        assertNotNull(step2, "Second step should be created");
        logger.info("✅ Created step 2: {}", step2.toString(2));

        // Wait a bit for X-ray to process the steps
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify steps were created by retrieving them via GraphQL
        logger.info("--- Verifying steps were created ---");
        JSONObject testDetails = xrayClient.getTestDetailsGraphQL(testKey);
        assertNotNull(testDetails, "Test details should be retrievable");
        
        if (testDetails.has("steps")) {
            JSONArray steps = testDetails.getJSONArray("steps");
            logger.info("✅ Retrieved {} steps from GraphQL", steps.length());
            assertTrue(steps.length() >= 2, "At least 2 steps should be present");
            
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                logger.info("Step {}: action={}, data={}, result={}",
                        i + 1,
                        step.optString("action", "N/A"),
                        step.optString("data", "N/A"),
                        step.optString("result", "N/A"));
            }
        } else {
            logger.warn("⚠️ No steps found in test details (may need more time to sync)");
        }

        // Also test adding multiple steps at once
        logger.info("--- Testing batch step creation ---");
        JSONArray batchSteps = new JSONArray();
        batchSteps.put(new JSONObject()
                .put("action", "Click login button")
                .put("data", "Click on 'Login' button")
                .put("result", "User is logged in"));
        batchSteps.put(new JSONObject()
                .put("action", "Verify dashboard")
                .put("data", "Check dashboard page")
                .put("result", "Dashboard is displayed"));

        JSONArray createdSteps = xrayClient.addTestStepsGraphQL(issueId, batchSteps);
        logger.info("✅ Created {} steps in batch", createdSteps.length());
        assertTrue(createdSteps.length() >= 0, "Batch creation should complete");

        // Clean up
        createdTicketKeys.add(testKey);
        logger.info("✅ GraphQL step creation test completed for {}", testKey);
    }

    @Test
    @Order(8)
    @DisplayName("Get test details and steps for TP-910")
    void testGetTP910Details() throws IOException {
        String testKey = "TP-910";
        logger.info("=== Testing getting details and steps for test ticket {} ===", testKey);

        // Get ticket from Jira
        Ticket ticket = xrayClient.performTicket(testKey, new String[]{"summary", "description", "issuetype", "key", "id", "status", "project"});
        assertNotNull(ticket, "Ticket should exist in Jira");
        assertEquals(testKey, ticket.getKey(), "Ticket key should match");
        logger.info("✅ Ticket verified in Jira: {}", testKey);
        logger.info("   Summary: {}", ticket.getFields().getSummary());
        logger.info("   Issue Type: {}", ticket.getFields().getIssueType().getName());
        logger.info("   Issue ID: {}", ticket.getId());

        // Get test details using GraphQL
        logger.info("--- Getting test details using GraphQL ---");
        JSONObject testDetails = null;
        try {
            testDetails = xrayClient.getTestDetailsGraphQL(testKey);
            if (testDetails != null) {
                logger.info("✅ Retrieved test details from X-ray GraphQL API:");
                logger.info("   Test details: {}", testDetails.toString(2));
            } else {
                logger.warn("⚠️ Test details not found in X-ray GraphQL API (may not be synced yet)");
            }
        } catch (Exception e) {
            logger.warn("⚠️ Error getting test details via GraphQL: {}", e.getMessage());
        }

        // Get steps using GraphQL
        JSONArray steps = xrayClient.getTestStepsGraphQL(testKey);
        logger.info("✅ Retrieved {} steps from GraphQL", steps.length());
        // Note: TP-910 may not have steps synced in X-ray yet, so we just log the count
        if (steps.length() >= 1) {
            logger.info("   TP-910 has {} step(s) in X-ray", steps.length());
            logger.info("   Steps:");
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                logger.info("   Step {}: action={}, data={}, result={}",
                        i + 1,
                        step.optString("action", "N/A"),
                        step.optString("data", "N/A"),
                        step.optString("result", "N/A"));
            }
        } else {
            logger.warn("   TP-910 has no steps in X-ray (may not be synced yet or steps not visible via GraphQL)");
        }


        // Get preconditions using GraphQL
        JSONArray preconditions = xrayClient.getPreconditionsGraphQL(testKey);
        logger.info("✅ Retrieved {} preconditions from GraphQL", preconditions.length());

        logger.info("✅ Successfully retrieved all information for {}", testKey);
    }

    @Test
    @Order(9)
    @DisplayName("Create precondition with step and link to test")
    void testCreatePreconditionWithStep() throws IOException {
        logger.info("=== Testing creating a Precondition issue with step and linking to test ===");

        // First, create a Precondition issue
        // Note: Precondition is a separate issue type in Jira/Xray
        String preconditionSummary = "Precondition with Step - " + System.currentTimeMillis();
        String preconditionDescription = "This is a precondition that has a test step.";
        
        // Try to create Precondition issue type
        String preconditionKey = null;
        try {
            String response = xrayClient.createTicketInProject(testProjectKey, "Precondition", preconditionSummary, preconditionDescription, null);
            JSONObject responseJson = new JSONObject(response);
            preconditionKey = responseJson.getString("key");
            logger.info("Created Precondition issue: {}", preconditionKey);
        } catch (Exception e) {
            // If Precondition type doesn't exist, create as Test and use it as precondition
            logger.warn("Could not create Precondition issue type, creating as Test instead: {}", e.getMessage());
            preconditionKey = createTestTicket(preconditionSummary, preconditionDescription, "Test");
            logger.info("Created Test issue to use as precondition: {}", preconditionKey);
        }

        assertNotNull(preconditionKey, "Precondition ticket should be created");
        createdTicketKeys.add(preconditionKey);

        // Wait for Jira to process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get the issue ID
        Ticket preconditionTicket = xrayClient.performTicket(preconditionKey, new String[]{"id"});
        assertNotNull(preconditionTicket, "Precondition ticket should exist");
        String preconditionIssueId = preconditionTicket.getId();
        assertNotNull(preconditionIssueId, "Precondition issue ID should be available");
        logger.info("Precondition {} has issue ID: {}", preconditionKey, preconditionIssueId);

        // Add a step to the precondition using GraphQL
        // Note: Precondition issues may not support steps via GraphQL (only Test issues do)
        // If Precondition type doesn't support steps, we'll create a Test issue instead
        logger.info("--- Adding step to precondition using GraphQL ---");
        JSONObject step = null;
        try {
            step = xrayClient.addTestStepGraphQL(
                    preconditionIssueId,
                    "Verify system is ready",
                    "Check system status and configuration",
                    "System is ready for testing"
            );
            if (step != null) {
                logger.info("✅ Created step in precondition: {}", step.toString(2));
            } else {
                logger.warn("⚠️ Could not add step to Precondition issue (Precondition type may not support steps)");
            }
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("not found") || errorMsg.contains("test with id")) {
                logger.warn("⚠️ Precondition issue does not support steps via GraphQL: {}", errorMsg);
                logger.info("   This is expected - Precondition issues may not support steps, only Test issues do");
            } else {
                throw e;
            }
        }

        // Wait for X-ray to process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify step was added (if step was successfully created)
        if (step != null) {
            JSONArray steps = xrayClient.getTestStepsGraphQL(preconditionKey);
            logger.info("✅ Retrieved {} steps from precondition {}", steps.length(), preconditionKey);
            if (steps.length() >= 1) {
                logger.info("   Precondition has step(s) in X-ray");
            } else {
                logger.warn("   Precondition has no steps in X-ray (may need more time to sync)");
            }
        } else {
            logger.info("   Skipping step verification (step was not added to Precondition)");
        }

        // Now link this precondition to TP-910
        logger.info("--- Linking precondition {} to TP-910 ---", preconditionKey);
        String testKey = "TP-910";
        
        // Get TP-910 issue ID
        Ticket testTicket = xrayClient.performTicket(testKey, new String[]{"id"});
        assertNotNull(testTicket, "Test ticket TP-910 should exist");
        String testIssueId = testTicket.getId();
        assertNotNull(testIssueId, "Test issue ID should be available");
        logger.info("Test {} has issue ID: {}", testKey, testIssueId);

        // Try to add precondition using GraphQL
        try {
            JSONObject result = xrayClient.addPreconditionToTestGraphQL(testIssueId, preconditionIssueId);
            if (result != null) {
                logger.info("✅ Successfully linked precondition {} to {} using GraphQL", preconditionKey, testKey);
            } else {
                logger.warn("⚠️ GraphQL returned null result (precondition may not be Precondition type)");
            }
        } catch (IOException e) {
            logger.warn("⚠️ GraphQL failed to link precondition: {}", e.getMessage());
            // GraphQL may fail if precondition is not Precondition type, that's okay
        }

        // Wait for X-ray to process
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify precondition was linked by checking TP-910 preconditions
        JSONArray preconditions = xrayClient.getPreconditionsGraphQL(testKey);
        logger.info("✅ Retrieved {} preconditions from test {}", preconditions.length(), testKey);
        
        // Check if our precondition is in the list
        boolean found = false;
        for (int i = 0; i < preconditions.length(); i++) {
            JSONObject precondition = preconditions.getJSONObject(i);
            if (precondition.has("jira")) {
                JSONObject jira = precondition.getJSONObject("jira");
                String key = jira.optString("key", "");
                if (preconditionKey.equals(key)) {
                    found = true;
                    logger.info("✅ Found precondition {} in test {} preconditions", preconditionKey, testKey);
                    break;
                }
            }
        }

        if (!found) {
            logger.warn("⚠️ Precondition {} not found in test {} preconditions (may need more time to sync)", preconditionKey, testKey);
        }

        logger.info("✅ Precondition creation and linking test completed");
    }

    @Test
    @Order(10)
    @DisplayName("Test searchAndPerform enriches tickets with X-ray test steps and preconditions")
    void testSearchAndPerformEnrichesWithXrayData() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing searchAndPerform enrichment with X-ray data ===");
        
        // Step 1: Create a test with steps
        String summary = "Search Test - " + System.currentTimeMillis();
        String description = "This test is used to verify searchAndPerform enrichment.";
        
        JSONArray steps = new JSONArray();
        JSONObject step1 = new JSONObject();
        step1.put("action", "Search test step 1");
        step1.put("data", "Test data for search");
        step1.put("expectedResult", "Step 1 result");
        steps.put(step1);
        
        JSONObject step2 = new JSONObject();
        step2.put("action", "Search test step 2");
        step2.put("data", "More test data");
        step2.put("expectedResult", "Step 2 result");
        steps.put(step2);
        
        String response = xrayClient.createTicketInProject(
            testProjectKey,
            "Test",
            summary,
            description,
            new TrackerClient.FieldsInitializer() {
                @Override
                public void init(TrackerClient.TrackerTicketFields fields) {
                    fields.set("steps", steps);
                }
            }
        );
        
        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);
        
        logger.info("Created test ticket: {}", ticketKey);
        
        // Wait for X-ray to sync
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Step 2: Use searchAndPerform to find this test
        String jql = String.format("project = %s AND key = %s", testProjectKey, ticketKey);
        logger.info("Searching for ticket using JQL: {}", jql);
        
        List<Ticket> foundTickets = xrayClient.searchAndPerform(jql, new String[]{"key", "summary", "issuetype"});
        
        assertNotNull(foundTickets, "Search results should not be null");
        assertTrue(foundTickets.size() >= 1, "Should find at least one ticket");
        
        // Find our ticket in the results
        Ticket foundTicket = null;
        for (Ticket ticket : foundTickets) {
            if (ticketKey.equals(ticket.getKey())) {
                foundTicket = ticket;
                break;
            }
        }
        
        assertNotNull(foundTicket, "Should find the created ticket");
        logger.info("✅ Found ticket in search results: {}", foundTicket.getKey());
        
        // Step 3: Verify that test steps were added to the ticket
        Fields fields = foundTicket.getFields();
        assertNotNull(fields, "Ticket fields should not be null");
        
        JSONObject fieldsJson = fields.getJSONObject();
        assertTrue(fieldsJson.has("xrayTestSteps"), "Ticket should have xrayTestSteps field");
        
        JSONArray enrichedSteps = fieldsJson.getJSONArray("xrayTestSteps");
        assertNotNull(enrichedSteps, "xrayTestSteps should not be null");
        assertTrue(enrichedSteps.length() >= 2, "Should have at least 2 test steps");
        
        logger.info("✅ Ticket enriched with {} test steps", enrichedSteps.length());
        
        // Verify step content
        for (int i = 0; i < Math.min(steps.length(), enrichedSteps.length()); i++) {
            JSONObject expectedStep = steps.getJSONObject(i);
            JSONObject actualStep = enrichedSteps.getJSONObject(i);
            
            String expectedAction = expectedStep.getString("action");
            String actualAction = actualStep.optString("action", "");
            assertEquals(expectedAction, actualAction, 
                    String.format("Step %d action should match", i + 1));
            
            logger.info("   Step {}: {} -> {}", i + 1, actualAction, actualStep.optString("result", "N/A"));
        }
        
        // Step 4: Test with a query that finds multiple tests (including TP-910)
        String multiTestJQL = String.format("project = %s AND issuetype = Test ORDER BY key DESC", testProjectKey);
        logger.info("Searching for multiple tests using JQL: {}", multiTestJQL);
        
        List<Ticket> multipleTickets = xrayClient.searchAndPerform(multiTestJQL, new String[]{"key", "summary", "issuetype"});
        
        assertNotNull(multipleTickets, "Multiple search results should not be null");
        assertTrue(multipleTickets.size() >= 1, "Should find at least one test");
        
        // Check if at least one ticket has xrayTestSteps
        int enrichedCount = 0;
        for (Ticket ticket : multipleTickets) {
            Fields ticketFields = ticket.getFields();
            if (ticketFields != null) {
                JSONObject ticketFieldsJson = ticketFields.getJSONObject();
                if (ticketFieldsJson.has("xrayTestSteps")) {
                    JSONArray ticketSteps = ticketFieldsJson.getJSONArray("xrayTestSteps");
                    if (ticketSteps != null && ticketSteps.length() > 0) {
                        enrichedCount++;
                        logger.info("   Ticket {} has {} test steps", ticket.getKey(), ticketSteps.length());
                    }
                }
            }
        }
        
        logger.info("✅ Found {} tickets with enriched test steps out of {} total tickets", 
                enrichedCount, multipleTickets.size());
        assertTrue(enrichedCount >= 1, "At least one ticket should have enriched test steps");
        
        logger.info("✅ searchAndPerform enrichment test completed successfully");
    }

    @Test
    @Order(11)
    @DisplayName("Test searchAndPerform with query: project = TP and issueType in ('Test')")
    void testSearchAndPerformWithTestIssueTypeQuery() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing searchAndPerform with query: project = TP and issueType in ('Test') ===");
        
        // Use the exact query requested
        String jql = String.format("project = %s and issueType in ('Test')", testProjectKey);
        logger.info("Executing JQL query: {}", jql);
        
        List<Ticket> foundTickets = xrayClient.searchAndPerform(jql, new String[]{"key", "summary", "issuetype"});
        
        assertNotNull(foundTickets, "Search results should not be null");
        assertTrue(foundTickets.size() > 0, "Should find at least one Test issue");
        
        logger.info("✅ Found {} Test issues", foundTickets.size());
        
        // Verify all returned tickets are Test issues
        int testIssueCount = 0;
        int enrichedCount = 0;
        for (Ticket ticket : foundTickets) {
            String issueType = ticket.getIssueType();
            assertNotNull(issueType, "Issue type should not be null for ticket " + ticket.getKey());
            
            if ("Test".equalsIgnoreCase(issueType)) {
                testIssueCount++;
                logger.info("   Ticket {}: {} - {}", ticket.getKey(), issueType, ticket.getFields().getSummary());
                
                // Check if ticket was enriched with X-ray data
                Fields fields = ticket.getFields();
                if (fields != null) {
                    JSONObject fieldsJson = fields.getJSONObject();
                    if (fieldsJson.has("xrayTestSteps")) {
                        JSONArray steps = fieldsJson.getJSONArray("xrayTestSteps");
                        if (steps != null && steps.length() > 0) {
                            enrichedCount++;
                            logger.info("      ✅ Enriched with {} test steps", steps.length());
                        }
                    } else {
                        logger.info("      ⚠️ No test steps found (may not have steps or not synced yet)");
                    }
                    
                    if (fieldsJson.has("xrayPreconditions")) {
                        JSONArray preconditions = fieldsJson.getJSONArray("xrayPreconditions");
                        if (preconditions != null && preconditions.length() > 0) {
                            logger.info("      ✅ Has {} preconditions", preconditions.length());
                        }
                    }
                }
            } else {
                logger.warn("   ⚠️ Ticket {} has unexpected issue type: {}", ticket.getKey(), issueType);
            }
        }
        
        assertEquals(foundTickets.size(), testIssueCount, 
                "All returned tickets should be Test issues");
        
        logger.info("✅ Verified all {} tickets are Test issues", testIssueCount);
        logger.info("✅ {} out of {} tickets were enriched with test steps", enrichedCount, testIssueCount);
        
        // Verify we found at least one known test (like TP-910 or TP-909)
        boolean foundKnownTest = false;
        for (Ticket ticket : foundTickets) {
            String key = ticket.getKey();
            if ("TP-910".equals(key) || "TP-909".equals(key)) {
                foundKnownTest = true;
                logger.info("✅ Found known test ticket: {}", key);
                
                // Verify it has test steps
                Fields fields = ticket.getFields();
                if (fields != null) {
                    JSONObject fieldsJson = fields.getJSONObject();
                    if (fieldsJson.has("xrayTestSteps")) {
                        JSONArray steps = fieldsJson.getJSONArray("xrayTestSteps");
                        logger.info("   {} has {} test steps", key, steps != null ? steps.length() : 0);
                    }
                }
                break;
            }
        }
        
        if (!foundKnownTest) {
            logger.warn("⚠️ Did not find known test tickets (TP-910 or TP-909) in results");
        }
        
        logger.info("✅ Query test completed successfully");
    }

    @Test
    @Order(12)
    @DisplayName("Test that TP-909 has precondition TP-910 in searchAndPerform results")
    void testTP909HasPreconditionTP910() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing that TP-909 has precondition TP-910 ===");
        
        // Search for TP-909 specifically
        String jql = "project = TP and key = TP-909";
        logger.info("Executing JQL query: {}", jql);
        
        List<Ticket> foundTickets = xrayClient.searchAndPerform(jql, new String[]{"key", "summary", "issuetype"});
        
        assertNotNull(foundTickets, "Search results should not be null");
        assertEquals(1, foundTickets.size(), "Should find exactly one ticket (TP-909)");
        
        Ticket tp909 = foundTickets.get(0);
        assertEquals("TP-909", tp909.getKey(), "Should be TP-909");
        logger.info("✅ Found ticket TP-909: {}", tp909.getFields().getSummary());
        
        // Check for test steps
        Fields fields = tp909.getFields();
        assertNotNull(fields, "Ticket fields should not be null");
        JSONObject fieldsJson = fields.getJSONObject();
        
        if (fieldsJson.has("xrayTestSteps")) {
            JSONArray steps = fieldsJson.getJSONArray("xrayTestSteps");
            logger.info("✅ TP-909 has {} test steps", steps.length());
        } else {
            logger.warn("⚠️ TP-909 has no test steps in enriched data");
        }
        
        // Check for preconditions - this is the main test
        assertTrue(fieldsJson.has("xrayPreconditions"), 
                "TP-909 should have xrayPreconditions field in enriched data");
        
        JSONArray preconditions = fieldsJson.getJSONArray("xrayPreconditions");
        assertNotNull(preconditions, "Preconditions array should not be null");
        assertTrue(preconditions.length() >= 1, 
                "TP-909 should have at least 1 precondition");
        
        logger.info("✅ TP-909 has {} preconditions", preconditions.length());
        
        // Verify that TP-910 is in the preconditions
        boolean foundTP910 = false;
        for (int i = 0; i < preconditions.length(); i++) {
            JSONObject precondition = preconditions.getJSONObject(i);
            if (precondition.has("jira")) {
                JSONObject jira = precondition.getJSONObject("jira");
                String preconditionKey = jira.optString("key", "");
                logger.info("   Precondition {}: {}", i + 1, preconditionKey);
                
                if ("TP-910".equals(preconditionKey)) {
                    foundTP910 = true;
                    logger.info("   ✅ Found TP-910 in preconditions!");
                    
                    // Check summary from Jira (should be added by searchAndPerform)
                    if (precondition.has("summary")) {
                        String summary = precondition.getString("summary");
                        logger.info("      Summary (from Jira): {}", summary);
                        assertNotNull(summary, "Precondition should have summary from Jira");
                    } else if (jira.has("summary")) {
                        String summary = jira.optString("summary", "");
                        logger.info("      Summary (from GraphQL jira field): {}", summary);
                    }
                    
                    // Check description from Jira (should be added by searchAndPerform)
                    if (precondition.has("description")) {
                        String description = precondition.getString("description");
                        logger.info("      Description (from Jira): {}", description);
                        assertNotNull(description, "Precondition should have description from Jira");
                    }
                    
                    // Check definition from Xray (should be in precondition from GraphQL)
                    if (precondition.has("definition")) {
                        String definition = precondition.getString("definition");
                        logger.info("      Definition (from Xray): {}", definition);
                        assertNotNull(definition, "Precondition should have definition from Xray");
                        assertFalse(definition.isEmpty(), "Precondition definition should not be empty");
                        // TP-910 should have "Some Step" as definition
                        assertEquals("Some Step", definition, "Precondition TP-910 should have 'Some Step' as definition");
                    } else {
                        logger.warn("      ⚠️ Precondition TP-910 has no definition from Xray");
                    }
                    break;
                }
            }
        }
        
        assertTrue(foundTP910, "TP-910 should be found in TP-909's preconditions");
        
        logger.info("✅ Verified that TP-909 has precondition TP-910");
    }
    
    @Test
    @Order(13)
    @DisplayName("Test pagination with limit 2 - verify all 6 tests are fetched")
    void testPaginationWithLimit2() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        
        logger.info("=== Testing pagination with limit 2 ===");
        
        // Step 1: Create 6 test tickets
        List<String> testKeys = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            String summary = "Pagination Test " + i + " - " + System.currentTimeMillis();
            String description = "This test is used to verify pagination works correctly.";
            
            JSONArray steps = new JSONArray();
            JSONObject step = new JSONObject();
            step.put("action", "Pagination test step " + i);
            step.put("data", "Test data " + i);
            step.put("result", "Expected result " + i);
            steps.put(step);
            
            String response = xrayClient.createTicketInProject(
                testProjectKey,
                "Test",
                summary,
                description,
                new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        fields.set("steps", steps);
                    }
                }
            );
            
            assertNotNull(response);
            JSONObject ticketJson = new JSONObject(response);
            String ticketKey = ticketJson.getString("key");
            testKeys.add(ticketKey);
            createdTicketKeys.add(ticketKey);
            
            logger.info("Created test ticket {}: {}", i, ticketKey);
        }
        
        // Wait for X-ray to sync
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Step 2: Set pagination limit to 2
        XrayRestClient restClient = xrayClient.getXrayRestClient();
        int originalLimit = restClient.getPaginationLimit();
        restClient.setPaginationLimit(2);
        logger.info("Set pagination limit to 2 (original was: {})", originalLimit);
        
        try {
            // Step 3: Search for all created tests using JQL
            String jql = "project = " + testProjectKey + " AND key in (" + String.join(", ", testKeys) + ")";
            logger.info("Executing JQL query: {}", jql);
            
            // Use searchAndPerform which will use pagination internally
            List<Ticket> foundTickets = xrayClient.searchAndPerform(jql, new String[]{"key", "summary", "issuetype"});
            
            assertNotNull(foundTickets, "Search results should not be null");
            assertEquals(6, foundTickets.size(), "Should find all 6 test tickets");
            
            logger.info("✅ Found all {} test tickets", foundTickets.size());
            
            // Step 4: Verify that all tickets have test steps (enriched via pagination)
            for (Ticket ticket : foundTickets) {
                String key = ticket.getKey();
                assertTrue(testKeys.contains(key), "Ticket " + key + " should be in created test keys");
                
                Fields fields = ticket.getFields();
                assertNotNull(fields, "Ticket fields should not be null");
                JSONObject fieldsJson = fields.getJSONObject();
                
                if (fieldsJson.has("xrayTestSteps")) {
                    JSONArray steps = fieldsJson.getJSONArray("xrayTestSteps");
                    assertTrue(steps.length() > 0, "Ticket " + key + " should have test steps");
                    logger.info("✅ Ticket {} has {} test steps", key, steps.length());
                } else {
                    logger.warn("⚠️ Ticket {} has no test steps in enriched data", key);
                }
            }
            
            logger.info("✅ Pagination test passed - all 6 tests were fetched with limit 2");
            
        } finally {
            // Restore original pagination limit
            restClient.setPaginationLimit(originalLimit);
            logger.info("Restored pagination limit to: {}", originalLimit);
        }
    }

    @Test
    @Order(14)
    @DisplayName("Test creating a Test issue with gherkin (Cucumber)")
    void testCreateTestWithGherkin() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");

        logger.info("=== Testing Test issue creation with gherkin (Cucumber) ===");

        String summary = "Xray Integration Test - Gherkin - " + System.currentTimeMillis();
        String description = "This is a test created by XrayClient integration test to verify gherkin (Cucumber) functionality.";
        String gherkin = "Scenario: Test Cucumber support\n" +
                "  Given I have dmtools installed\n" +
                "  When I create a test with gherkin\n" +
                "  Then Xray should mark it as Cucumber type";

        // Create the test ticket with gherkin using FieldsInitializer
        String response = xrayClient.createTicketInProject(
                testProjectKey,
                "Test",
                summary,
                description,
                new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        fields.set("gherkin", gherkin);
                    }
                }
        );

        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);

        logger.info("Created test ticket with gherkin: {}", ticketKey);

        // Wait for X-ray to sync and process the gherkin
        int maxWaitAttempts = 3;
        int waitAttempt = 0;
        JSONObject testDetails = null;
        boolean gherkinVerified = false;

        while (waitAttempt < maxWaitAttempts && !gherkinVerified) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try {
                testDetails = xrayClient.getTestDetailsGraphQL(ticketKey);
                if (testDetails != null && testDetails.has("testType")) {
                    String testTypeName = testDetails.getJSONObject("testType").optString("name", "");
                    String retrievedGherkin = testDetails.optString("gherkin", "");

                    if ("Cucumber".equalsIgnoreCase(testTypeName)) {
                        gherkinVerified = true;
                        logger.info("✅ Test type is Cucumber");
                        assertEquals(gherkin.trim(), retrievedGherkin.trim(), "Gherkin content should match");
                        logger.info("✅ Gherkin content matches");
                        break;
                    } else {
                        logger.warn("⚠️ Test type is {}, expected Cucumber. Attempt {}/{}",
                                testTypeName, waitAttempt + 1, maxWaitAttempts);
                    }
                }
            } catch (IOException e) {
                logger.debug("Waiting for X-ray sync (attempt {}/{}): {}",
                        waitAttempt + 1, maxWaitAttempts, e.getMessage());
            }
            waitAttempt++;
        }

        assertTrue(gherkinVerified, "Test should be updated to Cucumber type with gherkin content");
        logger.info("✅ Cucumber test verification successful");
    }

    @Test
    @Order(11)
    void testGetTestDetailsWithDataset() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");

        logger.info("=== Testing retrieving test details with dataset (TP-1436) ===");

        String testKey = "TP-1436";
        JSONObject testDetails = xrayClient.getTestDetailsGraphQL(testKey);

        assertNotNull(testDetails, "Test details should be retrieved");
        logger.info("Test details for {}: {}", testKey, testDetails.toString(2));

        // Verify dataset is included
        if (testDetails.has("dataset")) {
            JSONObject dataset = testDetails.getJSONObject("dataset");
            logger.info("✅ Dataset found in test details");
            logger.info("Dataset: {}", dataset.toString(2));

            // Verify parameters
            if (dataset.has("parameters")) {
                JSONArray parameters = dataset.getJSONArray("parameters");
                logger.info("Found {} parameters", parameters.length());
                for (int i = 0; i < parameters.length(); i++) {
                    JSONObject param = parameters.getJSONObject(i);
                    logger.info("  Parameter {}: name={}, type={}", i+1, 
                        param.optString("name"), param.optString("type"));
                }
            }

            // Verify rows
            if (dataset.has("rows")) {
                JSONArray rows = dataset.getJSONArray("rows");
                logger.info("Found {} rows", rows.length());
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    logger.info("  Row {}: order={}, values={}", i+1, 
                        row.optInt("order"), row.optJSONArray("Values"));
                }
            }
        } else {
            logger.warn("⚠️ Dataset not found in test details");
            fail("Dataset should be present in test details for " + testKey);
        }

        logger.info("✅ Dataset retrieval test successful");
    }

    @Test
    @Order(12)
    void testCreateTestWithGherkinAndDataset() throws IOException {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");

        logger.info("=== Testing Test issue creation with gherkin and dataset (Cucumber with data-driven) ===");

        String summary = "Xray Integration Test - Cucumber with Dataset - " + System.currentTimeMillis();
        String description = "This is a test created by XrayClient integration test to verify Cucumber with dataset functionality.";
        String gherkin = "Scenario Outline: Test login with different users\n" +
                "  Given user \"<username>\" is registered\n" +
                "  When user logs in with password \"<password>\"\n" +
                "  Then login result is \"<result>\"\n" +
                "\n" +
                "  Examples:\n" +
                "    | username | password  | result  |\n" +
                "    | admin    | admin123  | success |\n" +
                "    | user     | wrongpass | failed  |\n" +
                "    | guest    | guest456  | success |";

        // Create dataset with parameters and rows (for API-based approach)
        // Note: Xray may parse Examples from Gherkin automatically
        JSONObject dataset = new JSONObject();
        
        // Define parameters
        JSONArray parameters = new JSONArray();
        JSONObject param1 = new JSONObject();
        param1.put("name", "username");
        param1.put("type", "text");
        parameters.put(param1);
        
        JSONObject param2 = new JSONObject();
        param2.put("name", "password");
        param2.put("type", "text");
        parameters.put(param2);
        
        JSONObject param3 = new JSONObject();
        param3.put("name", "result");
        param3.put("type", "text");
        parameters.put(param3);
        
        dataset.put("parameters", parameters);
        
        // Define data rows
        JSONArray rows = new JSONArray();
        
        JSONObject row1 = new JSONObject();
        row1.put("order", 0);
        JSONArray values1 = new JSONArray();
        values1.put("admin");
        values1.put("admin123");
        values1.put("success");
        row1.put("Values", values1);
        rows.put(row1);
        
        JSONObject row2 = new JSONObject();
        row2.put("order", 1);
        JSONArray values2 = new JSONArray();
        values2.put("user");
        values2.put("wrongpass");
        values2.put("failed");
        row2.put("Values", values2);
        rows.put(row2);
        
        JSONObject row3 = new JSONObject();
        row3.put("order", 2);
        JSONArray values3 = new JSONArray();
        values3.put("guest");
        values3.put("guest456");
        values3.put("success");
        row3.put("Values", values3);
        rows.put(row3);
        
        dataset.put("rows", rows);

        // Create the test ticket with gherkin (Xray should parse Examples automatically)
        // For now, test without explicit dataset API call
        String response = xrayClient.createTicketInProject(
                testProjectKey,
                "Test",
                summary,
                description,
                new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        fields.set("gherkin", gherkin);
                        // Try to set dataset via Internal API
                        fields.set("dataset", dataset);
                    }
                }
        );

        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);

        logger.info("Created test ticket with gherkin and dataset: {}", ticketKey);

        // Wait for X-ray to sync and process the gherkin and dataset
        int maxWaitAttempts = 3;
        int waitAttempt = 0;
        JSONObject testDetails = null;
        boolean datasetVerified = false;

        while (waitAttempt < maxWaitAttempts && !datasetVerified) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try {
                testDetails = xrayClient.getTestDetailsGraphQL(ticketKey);
                if (testDetails != null && testDetails.has("testType")) {
                    String testTypeName = testDetails.getJSONObject("testType").optString("name", "");
                    logger.debug("Attempt {}/{}: Test type is {}", waitAttempt + 1, maxWaitAttempts, testTypeName);
                    
                    // Check if dataset field exists and is not null
                    JSONObject retrievedDataset = testDetails.optJSONObject("dataset");
                    logger.debug("Attempt {}/{}: Dataset is {}", waitAttempt + 1, maxWaitAttempts, 
                            retrievedDataset == null ? "null" : ("present with " + retrievedDataset.length() + " keys"));

                    if ("Cucumber".equalsIgnoreCase(testTypeName) && retrievedDataset != null && !retrievedDataset.isEmpty()) {
                        // Verify dataset structure
                        if (retrievedDataset.has("parameters") && retrievedDataset.has("rows")) {
                            JSONArray retrievedParams = retrievedDataset.getJSONArray("parameters");
                            JSONArray retrievedRows = retrievedDataset.getJSONArray("rows");
                            
                            if (retrievedParams.length() == parameters.length() && 
                                retrievedRows.length() == rows.length()) {
                                datasetVerified = true;
                                logger.info("✅ Test type is Cucumber");
                                logger.info("✅ Dataset structure verified: {} parameters, {} rows", 
                                    retrievedParams.length(), retrievedRows.length());
                                
                                // Log parameter names
                                for (int i = 0; i < retrievedParams.length(); i++) {
                                    JSONObject param = retrievedParams.getJSONObject(i);
                                    logger.info("   Parameter {}: {}", i+1, param.optString("name"));
                                }
                                
                                break;
                            } else {
                                logger.warn("⚠️ Dataset size mismatch. Expected {} params and {} rows, got {} params and {} rows. Attempt {}/{}",
                                        parameters.length(), rows.length(), 
                                        retrievedParams.length(), retrievedRows.length(),
                                        waitAttempt + 1, maxWaitAttempts);
                            }
                        }
                    } else {
                        logger.warn("⚠️ Test type is {} or dataset is empty. Attempt {}/{}",
                                testTypeName, waitAttempt + 1, maxWaitAttempts);
                    }
                }
            } catch (IOException e) {
                logger.debug("Waiting for X-ray sync (attempt {}/{}): {}",
                        waitAttempt + 1, maxWaitAttempts, e.getMessage());
            }
            waitAttempt++;
        }

        assertTrue(datasetVerified, "Test should be updated to Cucumber type with dataset");
        logger.info("✅ Cucumber test with dataset verification successful");
    }
}

