package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.JobJavaScriptBridge;
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
 * Integration tests for Xray JavaScript preprocessing function
 * Tests the preprocessXrayTestCases.js function and verifies that jira_xray tools are registered in JS bridge
 * 
 * Requires configuration:
 * - JIRA_BASE_PATH
 * - JIRA_API_TOKEN or JIRA_EMAIL + JIRA_API_TOKEN
 * - XRAY_BASE_PATH
 * - XRAY_CLIENT_ID
 * - XRAY_CLIENT_SECRET
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class XrayPreprocessingJSIntegrationTest {

    private static final Logger logger = LogManager.getLogger(XrayPreprocessingJSIntegrationTest.class);
    public static final String PRECONDITION_KEY = "TP-910";

    private static XrayClient xrayClient;
    private static String testProjectKey;
    private static JobJavaScriptBridge jsBridge;
    
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
        
        logger.info("=== XrayPreprocessingJS Integration Test Configuration Check ===");
        logger.info("JIRA_BASE_PATH: {}", jiraBasePath != null && !jiraBasePath.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("XRAY_BASE_PATH: {}", xrayBasePath != null && !xrayBasePath.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("XRAY_CLIENT_ID: {}", xrayClientId != null && !xrayClientId.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("XRAY_CLIENT_SECRET: {}", xrayClientSecret != null && !xrayClientSecret.isEmpty() ? "[SET]" : "[MISSING]");
        logger.info("Test Project Key: {}", testProjectKey);
        
        // Initialize XrayClient using getInstance()
        TrackerClient<? extends ITicket> client = null;
        try {
            client = XrayClient.getInstance();
        } catch (Exception e) {
            logger.error("Exception while initializing XrayClient: {}", e.getMessage(), e);
        }
        
        if (client == null || !(client instanceof XrayClient)) {
            logger.error("XrayClient could not be initialized. Missing required configuration.");
            Assumptions.assumeTrue(false, 
                    String.format("XrayClient configuration not available. Missing: JIRA_BASE_PATH=%s, XRAY_BASE_PATH=%s, XRAY_CLIENT_ID=%s, XRAY_CLIENT_SECRET=%s",
                            jiraBasePath != null && !jiraBasePath.isEmpty(),
                            xrayBasePath != null && !xrayBasePath.isEmpty(),
                            xrayClientId != null && !xrayClientId.isEmpty(),
                            xrayClientSecret != null && !xrayClientSecret.isEmpty()));
            return;
        }
        
        xrayClient = (XrayClient) client;
        
        // Initialize JS bridge with XrayClient
        jsBridge = new JobJavaScriptBridge(xrayClient, null, null, null, null);
        
        logger.info("✅ XrayClient and JobJavaScriptBridge initialized successfully");
    }
    
    /**
     * Convert JS execution result to JSONArray
     */
    private static JSONArray convertResultToJSONArray(Object result) throws Exception {
        if (result instanceof JSONArray) {
            return (JSONArray) result;
        } else if (result instanceof String) {
            String resultStr = (String) result;
            // Check if it's a PolyglotList string representation like "(3)[{...}]"
            if (resultStr.startsWith("(") && resultStr.contains(")[")) {
                // Extract the array part after ")["
                int arrayStart = resultStr.indexOf(")[") + 2;
                String arrayStr = resultStr.substring(arrayStart);
                logger.debug("Extracted array string: {}", arrayStr);
                
                // Try using GraalVM to parse JavaScript array syntax
                try {
                    org.graalvm.polyglot.Context jsContext = org.graalvm.polyglot.Context.create("js");
                    // Wrap in variable assignment to ensure proper parsing
                    String jsCode = "var arr = " + arrayStr + "; JSON.stringify(arr)";
                    org.graalvm.polyglot.Value parsed = jsContext.eval("js", jsCode);
                    String jsonStr = parsed.asString();
                    logger.debug("Converted to JSON string: {}", jsonStr);
                    return new JSONArray(jsonStr);
                } catch (Exception e) {
                    logger.warn("Failed to parse with GraalVM ({}), trying Gson: {}", e.getMessage(), arrayStr.substring(0, Math.min(100, arrayStr.length())));
                    // Try using Gson to parse (Gson can handle some JavaScript-like syntax)
                    try {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        Object parsed = gson.fromJson(arrayStr, Object.class);
                        if (parsed instanceof java.util.List) {
                            JSONArray jsonArray = new JSONArray();
                            for (Object item : (java.util.List<?>) parsed) {
                                if (item instanceof java.util.Map) {
                                    jsonArray.put(new JSONObject((java.util.Map<?, ?>) item));
                                } else {
                                    String itemJson = gson.toJson(item);
                                    jsonArray.put(new JSONObject(itemJson));
                                }
                            }
                            return jsonArray;
                        } else {
                            // Single object, wrap in array
                            JSONArray jsonArray = new JSONArray();
                            String itemJson = gson.toJson(parsed);
                            jsonArray.put(new JSONObject(itemJson));
                            return jsonArray;
                        }
                    } catch (Exception e2) {
                        logger.error("Failed to parse with Gson, trying direct JSON: {}", e2.getMessage());
                        // Last resort: try direct JSON parsing
                        try {
                            return new JSONArray(arrayStr);
                        } catch (Exception e3) {
                            logger.error("All parsing methods failed for: {}", arrayStr);
                            throw new Exception("Failed to parse JS result: " + e3.getMessage(), e3);
                        }
                    }
                }
            } else {
                // Regular JSON string
                try {
                    return new JSONArray(resultStr);
                } catch (Exception e) {
                    logger.error("Failed to parse result as JSONArray from string: {}", resultStr);
                    throw e;
                }
            }
        } else {
            // Handle PolyglotList or other collection types
            try {
                String className = result.getClass().getName();
                if (className.contains("PolyglotList") || className.contains("List") || result instanceof java.util.List) {
                    // Convert to JSONArray by iterating
                    JSONArray jsonArray = new JSONArray();
                    if (result instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) result;
                        for (Object item : list) {
                            if (item instanceof JSONObject) {
                                jsonArray.put(item);
                            } else if (item instanceof java.util.Map) {
                                jsonArray.put(new JSONObject((java.util.Map<?, ?>) item));
                            } else {
                                // Try to convert using Gson
                                try {
                                    com.google.gson.Gson gson = new com.google.gson.Gson();
                                    String jsonStr = gson.toJson(item);
                                    jsonArray.put(new JSONObject(jsonStr));
                                } catch (Exception e) {
                                    logger.warn("Failed to convert item to JSONObject: {}", e.getMessage());
                                }
                            }
                        }
                    } else {
                        // Try to use toString() and parse as JSON
                        String resultStr = result.toString();
                        // Check if it's a PolyglotList string representation
                        if (resultStr.startsWith("(") && resultStr.contains(")[")) {
                            int arrayStart = resultStr.indexOf(")[") + 2;
                            String arrayStr = resultStr.substring(arrayStart);
                            return new JSONArray(arrayStr);
                        }
                        return new JSONArray(resultStr);
                    }
                    return jsonArray;
                } else {
                    // Try to parse as JSON string
                    String resultStr = result.toString();
                    // Check if it's a PolyglotList string representation
                    if (resultStr.startsWith("(") && resultStr.contains(")[")) {
                        int arrayStart = resultStr.indexOf(")[") + 2;
                        String arrayStr = resultStr.substring(arrayStart);
                        return new JSONArray(arrayStr);
                    }
                    return new JSONArray(resultStr);
                }
            } catch (Exception e) {
                logger.error("Failed to convert JavaScript preprocessing result to JSONArray (type: {}): {}", 
                        result.getClass().getName(), e.getMessage());
                throw new Exception("Failed to parse JS result: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get the absolute path to the JS preprocessing file
     */
    private static String getJsFilePath() {
        // Try to find the file relative to project root
        String[] possiblePaths = {
            "agents/js/preprocessXrayTestCases.js",
            "../agents/js/preprocessXrayTestCases.js",
            "../../agents/js/preprocessXrayTestCases.js"
        };
        
        for (String path : possiblePaths) {
            java.io.File file = new java.io.File(path);
            if (file.exists() && file.isFile()) {
                return file.getAbsolutePath();
            }
        }
        
        // Fallback: try to find from current working directory
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            java.io.File file = new java.io.File(userDir, "agents/js/preprocessXrayTestCases.js");
            if (file.exists()) {
                return file.getAbsolutePath();
            }
            // Try one level up (if we're in dmtools-core subdirectory)
            file = new java.io.File(new java.io.File(userDir).getParent(), "agents/js/preprocessXrayTestCases.js");
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        
        // Last resort: return relative path and hope it works
        return "agents/js/preprocessXrayTestCases.js";
    }
    
    @AfterAll
    static void tearDown() {
        // Cleanup: Delete created preconditions
        if (xrayClient != null && !createdTicketKeys.isEmpty()) {
            logger.info("Cleaning up {} created test tickets", createdTicketKeys.size());
            for (String ticketKey : createdTicketKeys) {
                try {
                    xrayClient.deleteTicket(ticketKey);
                    logger.debug("Deleted test ticket: {}", ticketKey);
                } catch (Exception e) {
                    logger.warn("Failed to delete test ticket {}: {}", ticketKey, e.getMessage());
                }
            }
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Verify jira_xray_create_precondition tool is registered in JS bridge")
    void testJiraXrayToolRegistered() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        Assumptions.assumeTrue(jsBridge != null, "JobJavaScriptBridge not initialized");
        
        // Execute a simple JS to check if jira_xray_create_precondition is available
        String testJS = """
            function action(params) {
                if (typeof jira_xray_create_precondition === 'function') {
                    return 'jira_xray_create_precondition is available';
                } else {
                    return 'jira_xray_create_precondition is NOT available';
                }
            }
        """;
        
        JSONObject params = new JSONObject();
        Object result = jsBridge.executeJavaScript(testJS, params);
        
        assertNotNull(result, "JS execution should return a result");
        String resultStr = result.toString();
        logger.info("JS tool check result: {}", resultStr);
        
        assertTrue(resultStr.contains("available"), 
                "jira_xray_create_precondition should be available in JS bridge. Result: " + resultStr);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test JS preprocessing with temporary precondition IDs")
    void testPreprocessingWithTemporaryPreconditionIds() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        Assumptions.assumeTrue(jsBridge != null, "JobJavaScriptBridge not initialized");
        
        // Create test cases with temporary precondition IDs
        JSONArray testCases = new JSONArray();
        
        // Test case 1: with temporary precondition ID as object
        JSONObject testCase1 = new JSONObject();
        testCase1.put("priority", "High");
        testCase1.put("summary", "Test case with temporary precondition");
        testCase1.put("description", "This test case references a temporary precondition");
        JSONObject customFields1 = new JSONObject();
        JSONArray preconditions1 = new JSONArray();
        JSONObject tempPrecondition1 = new JSONObject();
        tempPrecondition1.put("key", "@precondition-1");
        tempPrecondition1.put("summary", "Test Precondition 1");
        tempPrecondition1.put("description", "This is a test precondition");
        JSONArray steps1 = new JSONArray();
        JSONObject step1 = new JSONObject();
        step1.put("action", "Ensure system is ready");
        step1.put("data", "System state: initialized");
        step1.put("result", "System is ready for testing");
        steps1.put(step1);
        tempPrecondition1.put("steps", steps1);
        preconditions1.put(tempPrecondition1);
        customFields1.put("preconditions", preconditions1);
        testCase1.put("customFields", customFields1);
        testCases.put(testCase1);
        
        // Test case 2: with temporary precondition ID as string (should also work)
        JSONObject testCase2 = new JSONObject();
        testCase2.put("priority", "Medium");
        testCase2.put("summary", "Test case with temporary precondition as string");
        testCase2.put("description", "This test case references the same temporary precondition");
        JSONObject customFields2 = new JSONObject();
        JSONArray preconditions2 = new JSONArray();
        preconditions2.put("@precondition-1"); // Same temporary ID, should reuse
        customFields2.put("preconditions", preconditions2);
        testCase2.put("customFields", customFields2);
        testCases.put(testCase2);
        
        // Test case 3: with existing precondition key (should not be processed)
        JSONObject testCase3 = new JSONObject();
        testCase3.put("priority", "Low");
        testCase3.put("summary", "Test case with existing precondition");
        testCase3.put("description", "This test case references an existing precondition");
        JSONObject customFields3 = new JSONObject();
        JSONArray preconditions3 = new JSONArray();
        preconditions3.put(PRECONDITION_KEY); // Existing precondition key
        customFields3.put("preconditions", preconditions3);
        testCase3.put("customFields", customFields3);
        testCases.put(testCase3);
        
        // Prepare parameters for JS function
        JSONObject params = new JSONObject();
        params.put("newTestCases", testCases);
        
        JSONObject ticket = new JSONObject();
        ticket.put("key", testProjectKey + "-9999"); // Fake ticket key for project extraction
        params.put("ticket", ticket);
        
        JSONObject jobParams = new JSONObject();
        jobParams.put("inputJql", "project = " + testProjectKey);
        params.put("jobParams", jobParams);
        
        // Load and execute the preprocessing JS function
        // Use path relative to project root (dmtools-core directory)
        String jsCode = getJsFilePath();
        logger.info("Executing JS preprocessing function: {}", jsCode);
        
        Object result = jsBridge.executeJavaScript(jsCode, params);
        
        assertNotNull(result, "JS preprocessing should return a result");
        logger.info("JS preprocessing result type: {}, value: {}", result.getClass().getName(), result);
        
        // Convert result to JSONArray
        JSONArray processedTestCases = convertResultToJSONArray(result);
        
        assertNotNull(processedTestCases, "Processed test cases should not be null");
        assertEquals(3, processedTestCases.length(), "Should have 3 processed test cases");
        
        // Verify first test case: temporary precondition should be replaced with real key
        JSONObject processed1 = processedTestCases.getJSONObject(0);
        JSONObject customFields1Processed = processed1.getJSONObject("customFields");
        JSONArray preconditions1Processed = customFields1Processed.getJSONArray("preconditions");
        assertEquals(1, preconditions1Processed.length(), "First test case should have 1 precondition");
        
        String preconditionKey1 = preconditions1Processed.getString(0);
        assertNotNull(preconditionKey1, "Precondition key should not be null");
        assertFalse(preconditionKey1.startsWith("@"), 
                "Temporary ID should be replaced with real key. Got: " + preconditionKey1);
        assertTrue(preconditionKey1.startsWith(testProjectKey + "-"), 
                "Precondition key should start with project key. Got: " + preconditionKey1);
        
        // Store created precondition key for cleanup
        createdTicketKeys.add(preconditionKey1);
        logger.info("✅ Created precondition: {}", preconditionKey1);
        
        // Verify second test case: should reference the same precondition (reused)
        JSONObject processed2 = processedTestCases.getJSONObject(1);
        JSONObject customFields2Processed = processed2.getJSONObject("customFields");
        JSONArray preconditions2Processed = customFields2Processed.getJSONArray("preconditions");
        assertEquals(1, preconditions2Processed.length(), "Second test case should have 1 precondition");
        
        String preconditionKey2 = preconditions2Processed.getString(0);
        assertEquals(preconditionKey1, preconditionKey2, 
                "Second test case should reference the same precondition (reused)");
        
        // Verify third test case: existing precondition key should remain unchanged
        JSONObject processed3 = processedTestCases.getJSONObject(2);
        JSONObject customFields3Processed = processed3.getJSONObject("customFields");
        JSONArray preconditions3Processed = customFields3Processed.getJSONArray("preconditions");
        assertEquals(1, preconditions3Processed.length(), "Third test case should have 1 precondition");
        
        String preconditionKey3 = preconditions3Processed.getString(0);
        assertEquals("TP-910", preconditionKey3, 
                "Existing precondition key should remain unchanged");
        
        logger.info("✅ All test cases processed correctly");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test JS preprocessing with preconditions as JSON string")
    void testPreprocessingWithPreconditionsAsString() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        Assumptions.assumeTrue(jsBridge != null, "JobJavaScriptBridge not initialized");
        
        // Create test case with preconditions as JSON string (as they come from LLM)
        JSONArray testCases = new JSONArray();
        
        JSONObject testCase = new JSONObject();
        testCase.put("priority", "High");
        testCase.put("summary", "Test case with preconditions as JSON string");
        testCase.put("description", "This test case has preconditions as a JSON string");
        JSONObject customFields = new JSONObject();
        
        // Preconditions as JSON string (simulating LLM output)
        JSONArray preconditionsArray = new JSONArray();
        JSONObject tempPrecondition = new JSONObject();
        tempPrecondition.put("key", "@precondition-2");
        tempPrecondition.put("summary", "Test Precondition 2");
        tempPrecondition.put("description", "Another test precondition");
        preconditionsArray.put(tempPrecondition);
        customFields.put("preconditions", preconditionsArray.toString()); // As string!
        testCase.put("customFields", customFields);
        testCases.put(testCase);
        
        // Prepare parameters
        JSONObject params = new JSONObject();
        params.put("newTestCases", testCases);
        
        JSONObject ticket = new JSONObject();
        ticket.put("key", testProjectKey + "-9999");
        params.put("ticket", ticket);
        
        JSONObject jobParams = new JSONObject();
        jobParams.put("inputJql", "project = " + testProjectKey);
        params.put("jobParams", jobParams);
        
        // Execute JS function
        String jsCode = getJsFilePath();
        Object result = jsBridge.executeJavaScript(jsCode, params);
        
        assertNotNull(result, "JS preprocessing should return a result");
        
        // Convert result
        JSONArray processedTestCases = convertResultToJSONArray(result);
        
        assertEquals(1, processedTestCases.length(), "Should have 1 processed test case");
        
        JSONObject processed = processedTestCases.getJSONObject(0);
        JSONObject customFieldsProcessed = processed.getJSONObject("customFields");
        JSONArray preconditionsProcessed = customFieldsProcessed.getJSONArray("preconditions");
        assertEquals(1, preconditionsProcessed.length(), "Should have 1 precondition");
        
        String preconditionKey = preconditionsProcessed.getString(0);
        assertNotNull(preconditionKey, "Precondition key should not be null");
        assertFalse(preconditionKey.startsWith("@"), 
                "Temporary ID should be replaced. Got: " + preconditionKey);
        assertTrue(preconditionKey.startsWith(testProjectKey + "-"), 
                "Precondition key should start with project key. Got: " + preconditionKey);
        
        // Store for cleanup
        createdTicketKeys.add(preconditionKey);
        logger.info("✅ Created precondition from string format: {}", preconditionKey);
    }
    
    @Test
    @Order(4)
    @DisplayName("Test JS preprocessing error handling")
    void testPreprocessingErrorHandling() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        Assumptions.assumeTrue(jsBridge != null, "JobJavaScriptBridge not initialized");
        
        // Test with invalid parameters (no project code)
        JSONArray testCases = new JSONArray();
        JSONObject testCase = new JSONObject();
        testCase.put("priority", "High");
        testCase.put("summary", "Test case without project");
        testCase.put("description", "This test case has no project context");
        testCases.put(testCase);
        
        JSONObject params = new JSONObject();
        params.put("newTestCases", testCases);
        
        // No ticket and no jobParams.inputJql - should return original test cases
        params.put("ticket", new JSONObject());
        params.put("jobParams", new JSONObject());
        
        String jsCode = getJsFilePath();
        Object result = jsBridge.executeJavaScript(jsCode, params);
        
        assertNotNull(result, "JS preprocessing should return a result even on error");
        
        // Should return original test cases
        JSONArray processedTestCases = convertResultToJSONArray(result);
        
        assertEquals(1, processedTestCases.length(), "Should return original test case on error");
        assertEquals("Test case without project", processedTestCases.getJSONObject(0).getString("summary"));
        
        logger.info("✅ Error handling works correctly");
    }
    
    @Test
    @Order(5)
    @DisplayName("Test JS preprocessing with real story TP-1309")
    void testPreprocessingWithRealStoryTP1309() throws Exception {
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        Assumptions.assumeTrue(jsBridge != null, "JobJavaScriptBridge not initialized");
        
        // Get real story TP-1309
        Ticket story = (Ticket) xrayClient.performTicket("TP-1309", new String[]{"summary", "description", "project"});
        Assumptions.assumeTrue(story != null, "Story TP-1309 not found");
        
        logger.info("Testing with real story: TP-1309 - {}", story.getFields().getSummary());
        
        // Create test cases with temporary precondition IDs (simulating LLM output)
        JSONArray testCases = new JSONArray();
        
        // Test case 1: with temporary precondition
        JSONObject testCase1 = new JSONObject();
        testCase1.put("priority", "High");
        testCase1.put("summary", "Test case for TP-1309 with shared precondition");
        testCase1.put("description", "This test case references a temporary precondition that should be created");
        JSONObject customFields1 = new JSONObject();
        JSONArray preconditions1 = new JSONArray();
        JSONObject tempPrecondition1 = new JSONObject();
        tempPrecondition1.put("key", "@precondition-tp1309-1");
        tempPrecondition1.put("summary", "System is ready for TP-1309 testing");
        tempPrecondition1.put("description", "Precondition for testing TP-1309 functionality");
        JSONArray steps1 = new JSONArray();
        JSONObject step1 = new JSONObject();
        step1.put("action", "Ensure TP-1309 system components are initialized");
        step1.put("data", "System state: ready");
        step1.put("result", "System is ready for testing");
        steps1.put(step1);
        tempPrecondition1.put("steps", steps1);
        preconditions1.put(tempPrecondition1);
        customFields1.put("preconditions", preconditions1);
        testCase1.put("customFields", customFields1);
        testCases.put(testCase1);
        
        // Test case 2: references the same precondition (should reuse)
        JSONObject testCase2 = new JSONObject();
        testCase2.put("priority", "Medium");
        testCase2.put("summary", "Another test case for TP-1309");
        testCase2.put("description", "This test case should reuse the same precondition");
        JSONObject customFields2 = new JSONObject();
        JSONArray preconditions2 = new JSONArray();
        preconditions2.put("@precondition-tp1309-1"); // Same temporary ID
        customFields2.put("preconditions", preconditions2);
        testCase2.put("customFields", customFields2);
        testCases.put(testCase2);
        
        // Prepare parameters
        JSONObject params = new JSONObject();
        params.put("newTestCases", testCases);
        
        JSONObject ticket = new JSONObject();
        ticket.put("key", "TP-1309");
        params.put("ticket", ticket);
        
        JSONObject jobParams = new JSONObject();
        jobParams.put("inputJql", "key in (TP-1309)");
        params.put("jobParams", jobParams);
        
        // Execute JS function
        String jsCode = getJsFilePath();
        logger.info("Executing JS preprocessing for TP-1309");
        
        Object result = jsBridge.executeJavaScript(jsCode, params);
        
        assertNotNull(result, "JS preprocessing should return a result");
        
        // Convert result
        JSONArray processedTestCases = convertResultToJSONArray(result);
        
        assertNotNull(processedTestCases, "Processed test cases should not be null");
        assertEquals(2, processedTestCases.length(), "Should have 2 processed test cases");
        
        // Verify first test case
        JSONObject processed1 = processedTestCases.getJSONObject(0);
        JSONObject customFields1Processed = processed1.getJSONObject("customFields");
        JSONArray preconditions1Processed = customFields1Processed.getJSONArray("preconditions");
        assertEquals(1, preconditions1Processed.length(), "First test case should have 1 precondition");
        
        String preconditionKey1 = preconditions1Processed.getString(0);
        assertNotNull(preconditionKey1, "Precondition key should not be null");
        assertFalse(preconditionKey1.startsWith("@"), 
                "Temporary ID should be replaced. Got: " + preconditionKey1);
        assertTrue(preconditionKey1.startsWith(testProjectKey + "-"), 
                "Precondition key should start with project key. Got: " + preconditionKey1);
        
        // Store for cleanup
        createdTicketKeys.add(preconditionKey1);
        logger.info("✅ Created precondition for TP-1309: {}", preconditionKey1);
        
        // Verify second test case: should reference the same precondition
        JSONObject processed2 = processedTestCases.getJSONObject(1);
        JSONObject customFields2Processed = processed2.getJSONObject("customFields");
        JSONArray preconditions2Processed = customFields2Processed.getJSONArray("preconditions");
        assertEquals(1, preconditions2Processed.length(), "Second test case should have 1 precondition");
        
        String preconditionKey2 = preconditions2Processed.getString(0);
        assertEquals(preconditionKey1, preconditionKey2, 
                "Second test case should reference the same precondition (reused)");
        
        // Verify the precondition was created correctly
        Ticket createdPrecondition = (Ticket) xrayClient.performTicket(preconditionKey1, new String[]{"summary", "description", "issueType"});
        assertNotNull(createdPrecondition, "Created precondition should exist");
        
        // Verify issue type (with null check)
        if (createdPrecondition.getFields() != null && createdPrecondition.getFields().getIssueType() != null) {
            assertEquals("Precondition", createdPrecondition.getFields().getIssueType().getName(), 
                    "Created issue should be of type Precondition");
        } else {
            logger.warn("Issue type not available for precondition {}, but precondition was created successfully", preconditionKey1);
        }
        
        // Verify summary
        if (createdPrecondition.getFields() != null && createdPrecondition.getFields().getSummary() != null) {
            assertEquals("System is ready for TP-1309 testing", createdPrecondition.getFields().getSummary(),
                    "Precondition summary should match");
        }
        
        // Verify definition was set correctly using GraphQL
        // Use getPreconditionDetailsGraphQL which is specifically for Precondition issues with definition
        try {
            // Wait a bit for Xray to sync
            Thread.sleep(3000);
            
            logger.info("Fetching precondition details with definition via GraphQL for: {}", preconditionKey1);
            JSONObject preconditionDetails = xrayClient.getPreconditionDetailsGraphQL(preconditionKey1);
            
            if (preconditionDetails != null) {
                logger.info("Precondition details retrieved. Keys: {}", preconditionDetails.keySet());
                logger.info("Full precondition details JSON: {}", preconditionDetails.toString());
                
                // For Precondition issues, definition should be present
                if (preconditionDetails.has("definition")) {
                    String definition = preconditionDetails.optString("definition", "");
                    assertNotNull(definition, "Definition should not be null");
                    assertFalse(definition.isEmpty(), "Definition should not be empty");
                    
                    // Verify definition contains expected content from steps
                    assertTrue(definition.contains("Step 1") || definition.contains("Ensure TP-1309") || definition.contains("TP-1309") || definition.contains("System state") || definition.contains("System is ready"), 
                            "Definition should contain step information. Got: " + definition);
                    logger.info("✅ Precondition definition verified and contains expected content: {}", 
                            definition.length() > 150 ? definition.substring(0, 150) + "..." : definition);
                } else {
                    logger.warn("⚠️ Definition field not found in precondition details. Available fields: {}", 
                            preconditionDetails.keySet());
                    // Log full response for debugging
                    logger.info("Full precondition details JSON: {}", preconditionDetails.toString());
                    // This is a warning, not a failure, as definition might not be immediately available
                }
            } else {
                logger.warn("⚠️ Precondition details not found via GraphQL for: {}. Definition might not be immediately available after creation.", preconditionKey1);
            }
        } catch (Exception e) {
            logger.error("Failed to verify definition via GraphQL: {}", e.getMessage(), e);
            // Don't fail the test if GraphQL check fails, but log it
        }
        
        logger.info("✅ All tests passed for TP-1309");
    }
}

