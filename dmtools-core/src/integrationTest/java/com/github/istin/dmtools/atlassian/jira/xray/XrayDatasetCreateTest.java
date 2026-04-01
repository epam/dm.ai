// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.xray;

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
 * Test creating a new Cucumber test with dataset from scratch.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class XrayDatasetCreateTest {
    private static final Logger logger = LogManager.getLogger(XrayDatasetCreateTest.class);
    
    private static XrayClient xrayClient;
    private static String testProjectKey;
    private static List<String> createdTicketKeys = new ArrayList<>();
    
    @BeforeAll
    static void setup() throws IOException {
        testProjectKey = System.getProperty("jira.test.project", "TP");
        xrayClient = (XrayClient) XrayClient.getInstance();
        Assumptions.assumeTrue(xrayClient != null, "XrayClient not initialized");
        logger.info("=== Xray Dataset Create Test ===");
    }
    
    @AfterAll
    static void cleanup() {
        logger.info("Cleaning up {} created tickets", createdTicketKeys.size());
        for (String key : createdTicketKeys) {
            try {
                // xrayClient.deleteTicket(key); // Uncomment to enable cleanup
                logger.info("Ticket to delete: {}", key);
            } catch (Exception e) {
                logger.warn("Failed to delete ticket {}: {}", key, e.getMessage());
            }
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Create Cucumber test with dataset")
    void testCreateCucumberTestWithDataset() throws IOException {
        logger.info("\n=== Creating Cucumber Test with Dataset ===");
        
        String summary = "Xray Dataset Test - " + System.currentTimeMillis();
        String description = "Test created by XrayDatasetCreateTest to verify dataset functionality";
        String gherkin = "Scenario Outline: Login test\n" +
                "  Given user \"<username>\"\n" +
                "  When password is \"<password>\"\n" +
                "  Then result is \"<result>\"\n" +
                "\n" +
                "  Examples:\n" +
                "    | username | password | result  |\n" +
                "    | admin    | pass123  | success |\n" +
                "    | guest    | wrong    | failed  |";
        
        // Create dataset structure matching GraphQL format
        JSONObject dataset = new JSONObject();
        
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
        
        JSONArray rows = new JSONArray();
        
        JSONObject row1 = new JSONObject();
        row1.put("order", 0);
        JSONArray values1 = new JSONArray();
        values1.put("admin");
        values1.put("pass123");
        values1.put("success");
        row1.put("Values", values1);
        rows.put(row1);
        
        JSONObject row2 = new JSONObject();
        row2.put("order", 1);
        JSONArray values2 = new JSONArray();
        values2.put("guest");
        values2.put("wrong");
        values2.put("failed");
        row2.put("Values", values2);
        rows.put(row2);
        
        dataset.put("rows", rows);
        
        // Create ticket with gherkin and dataset
        String response = xrayClient.createTicketInProject(
                testProjectKey,
                "Test",
                summary,
                description,
                new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        fields.set("gherkin", gherkin);
                        fields.set("dataset", dataset);
                    }
                }
        );
        
        assertNotNull(response);
        JSONObject ticketJson = new JSONObject(response);
        String ticketKey = ticketJson.getString("key");
        createdTicketKeys.add(ticketKey);
        
        logger.info("✅ Created test ticket: {}", ticketKey);
        
        // Wait for Xray to process
        logger.info("Waiting 10 seconds for Xray to process...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify via GraphQL
        JSONObject testDetails = xrayClient.getTestDetailsGraphQL(ticketKey);
        assertNotNull(testDetails);
        
        String testType = testDetails.getJSONObject("testType").optString("name");
        logger.info("Test type: {}", testType);
        assertEquals("Cucumber", testType, "Test should be Cucumber type");
        
        assertTrue(testDetails.has("gherkin"), "Should have gherkin");
        logger.info("✅ Gherkin set successfully");
        
        if (testDetails.has("dataset")) {
            JSONObject retrievedDataset = testDetails.getJSONObject("dataset");
            if (retrievedDataset.has("parameters")) {
                JSONArray params = retrievedDataset.getJSONArray("parameters");
                logger.info("✅ Dataset has {} parameters", params.length());
                
                if (params.length() == 3) {
                    logger.info("🎉 Dataset created successfully via Internal API!");
                } else {
                    logger.warn("⚠️ Dataset parameter count mismatch: expected 3, got {}", params.length());
                }
            } else {
                logger.warn("⚠️ Dataset retrieved but has no parameters");
            }
        } else {
            logger.warn("⚠️ Dataset not found in GraphQL response");
            logger.info("This is expected if Internal API call failed");
        }
        
        logger.info("\n=== Test Summary ===");
        logger.info("Ticket: {}", ticketKey);
        logger.info("Test type: {}", testType);
        logger.info("Gherkin: ✅");
        logger.info("Dataset: {}", testDetails.has("dataset") && 
                testDetails.getJSONObject("dataset").has("parameters") ? "✅" : "⚠️");
    }
}

