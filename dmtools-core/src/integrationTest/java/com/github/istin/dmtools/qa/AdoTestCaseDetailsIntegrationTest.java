package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to inspect details of ADO test case work item 792.
 * 
 * Configuration via PropertyReader (environment variables or config.properties):
 * - ADO_ORGANIZATION: RustemAgziamov
 * - ADO_PROJECT: ai-native-sdlc-blueprint
 * - ADO_PAT_TOKEN: Your personal access token
 */
public class AdoTestCaseDetailsIntegrationTest {

    private static final Logger logger = LogManager.getLogger(AdoTestCaseDetailsIntegrationTest.class);

    private static BasicAzureDevOpsClient adoClient;
    private static final String TEST_CASE_ID = "792";

    @BeforeAll
    static void setUp() throws IOException {
        logger.info("Setting up AdoTestCaseDetailsIntegrationTest");

        // Initialize ADO client
        adoClient = BasicAzureDevOpsClient.getInstance();

        if (adoClient == null) {
            throw new IllegalStateException(
                "ADO configuration not found. Please set ADO_ORGANIZATION, ADO_PROJECT, and ADO_PAT_TOKEN " +
                "in your environment variables or dmtools.env file."
            );
        }

        adoClient.setLogEnabled(true);
        adoClient.setCacheGetRequestsEnabled(false);

        logger.info("ADO client initialized for organization: {}, project: {}",
            BasicAzureDevOpsClient.ORGANIZATION,
            BasicAzureDevOpsClient.PROJECT);
    }

    @Test
    void testInspectTestCaseDetails() throws IOException {
        logger.info("=== Inspecting Test Case Work Item {} ===", TEST_CASE_ID);

        // Fetch work item with extended fields plus TCM fields for test cases
        String[] testCaseFields = new String[]{
            "System.Id",
            "System.Title",
            "System.Description",
            "System.State",
            "System.WorkItemType",
            "System.AssignedTo",
            "System.CreatedBy",
            "System.CreatedDate",
            "System.ChangedDate",
            "System.Tags",
            "Microsoft.VSTS.Common.Priority",
            // TCM (Test Case Management) fields
            "Microsoft.VSTS.TCM.Steps",
            "Microsoft.VSTS.TCM.Parameters",
            "Microsoft.VSTS.TCM.LocalDataSource",
            "Microsoft.VSTS.TCM.ReproSteps"
        };
        
        WorkItem workItem = adoClient.performTicket(TEST_CASE_ID, testCaseFields);
        assertNotNull(workItem, "Test case work item " + TEST_CASE_ID + " should be found");

        // Print basic information
        logger.info("\n=== BASIC INFORMATION ===");
        logger.info("ID: {}", workItem.getTicketKey());
        try {
            logger.info("Title: {}", workItem.getTicketTitle());
            logger.info("Work Item Type: {}", workItem.getIssueType());
            logger.info("State: {}", workItem.getStatus());
        } catch (IOException e) {
            logger.warn("Failed to get some basic fields: {}", e.getMessage());
        }
        logger.info("Project: {}", workItem.getProject());

        // Print all fields
        logger.info("\n=== ALL FIELDS ===");
        JSONObject fields = workItem.getFieldsObject();
        if (fields != null) {
            fields.keySet().forEach(key -> {
                Object value = fields.opt(key);
                logger.info("  {}: {}", key, value);
            });
        } else {
            logger.warn("No fields object found");
        }

        // Print description
        logger.info("\n=== DESCRIPTION ===");
        String description = workItem.getTicketDescription();
        if (description != null && !description.isEmpty()) {
            logger.info("Description length: {} characters", description.length());
            logger.info("Description content:\n{}", description);
        } else {
            logger.warn("No description found");
        }

        // Print test steps (TCM field)
        logger.info("\n=== TEST STEPS (Microsoft.VSTS.TCM.Steps) ===");
        String testSteps = workItem.getFieldValueAsString("Microsoft.VSTS.TCM.Steps");
        if (testSteps != null && !testSteps.isEmpty()) {
            logger.info("Test Steps (raw XML):");
            logger.info("{}", testSteps);
            
            // Try to parse XML and display in a more readable format
            try {
                logger.info("\n--- Parsed Test Steps (readable format) ---");
                parseAndDisplayTestSteps(testSteps);
            } catch (Exception e) {
                logger.warn("Failed to parse test steps XML: {}", e.getMessage());
                logger.info("Showing raw XML format above");
            }
        } else {
            logger.info("No test steps found (Microsoft.VSTS.TCM.Steps is empty)");
        }

        // Print test parameters
        logger.info("\n=== TEST PARAMETERS (Microsoft.VSTS.TCM.Parameters) ===");
        String testParameters = workItem.getFieldValueAsString("Microsoft.VSTS.TCM.Parameters");
        if (testParameters != null && !testParameters.isEmpty()) {
            logger.info("Test Parameters:");
            logger.info("{}", testParameters);
        } else {
            logger.info("No test parameters found");
        }

        // Print local data source
        logger.info("\n=== LOCAL DATA SOURCE (Microsoft.VSTS.TCM.LocalDataSource) ===");
        String localDataSource = workItem.getFieldValueAsString("Microsoft.VSTS.TCM.LocalDataSource");
        if (localDataSource != null && !localDataSource.isEmpty()) {
            logger.info("Local Data Source:");
            logger.info("{}", localDataSource);
        } else {
            logger.info("No local data source found");
        }

        // Print repro steps (for bugs)
        logger.info("\n=== REPRO STEPS (Microsoft.VSTS.TCM.ReproSteps) ===");
        String reproSteps = workItem.getFieldValueAsString("Microsoft.VSTS.TCM.ReproSteps");
        if (reproSteps != null && !reproSteps.isEmpty()) {
            logger.info("Repro Steps:");
            logger.info("{}", reproSteps);
        } else {
            logger.info("No repro steps found");
        }

        // Print priority
        logger.info("\n=== PRIORITY ===");
        try {
            String priority = workItem.getPriority();
            logger.info("Priority: {}", priority != null ? priority : "Not set");
        } catch (IOException e) {
            logger.warn("Failed to get priority: {}", e.getMessage());
        }

        // Print tags
        logger.info("\n=== TAGS ===");
        String tags = workItem.getFieldValueAsString("System.Tags");
        logger.info("Tags: {}", tags != null ? tags : "No tags");

        // Print assigned to
        logger.info("\n=== ASSIGNED TO ===");
        String assignedTo = workItem.getFieldValueAsString("System.AssignedTo");
        logger.info("Assigned To: {}", assignedTo != null ? assignedTo : "Unassigned");

        // Print attachments
        logger.info("\n=== ATTACHMENTS ===");
        List<? extends IAttachment> attachments = workItem.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            logger.info("Found {} attachment(s):", attachments.size());
            for (int i = 0; i < attachments.size(); i++) {
                IAttachment attachment = attachments.get(i);
                logger.info("  [{}] Name: {}", i + 1, attachment.getName());
                logger.info("       URL: {}", attachment.getUrl());
                logger.info("       Content Type: {}", attachment.getContentType());
            }
        } else {
            logger.info("No attachments found");
        }

        // Print comments
        logger.info("\n=== COMMENTS ===");
        List<? extends IComment> comments = adoClient.getComments(TEST_CASE_ID, workItem);
        if (comments != null && !comments.isEmpty()) {
            logger.info("Found {} comment(s):", comments.size());
            for (int i = 0; i < comments.size(); i++) {
                IComment comment = comments.get(i);
                String authorName = "Unknown";
                if (comment.getAuthor() != null) {
                    authorName = comment.getAuthor().getFullName();
                    if (authorName == null || authorName.isEmpty()) {
                        authorName = comment.getAuthor().getEmailAddress();
                    }
                }
                logger.info("  [{}] Author: {}", i + 1, authorName);
                logger.info("       Created: {}", comment.getCreated());
                logger.info("       Body length: {} characters", 
                    comment.getBody() != null ? comment.getBody().length() : 0);
                if (comment.getBody() != null && comment.getBody().length() < 500) {
                    logger.info("       Body: {}", comment.getBody());
                }
            }
        } else {
            logger.info("No comments found");
        }

        // Print relations/links
        logger.info("\n=== RELATIONS/LINKS ===");
        try {
            // Try to enrich with relations if method exists
            if (adoClient instanceof com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient) {
                ((com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient) adoClient).enrichWorkItemWithRelations(workItem);
            }
            org.json.JSONArray relations = workItem.getJSONArray("relations");
            if (relations != null && relations.length() > 0) {
                logger.info("Found {} relation(s):", relations.length());
                for (int i = 0; i < relations.length(); i++) {
                    org.json.JSONObject relation = relations.getJSONObject(i);
                    logger.info("  [{}] Type: {}", i + 1, relation.optString("rel"));
                    logger.info("       URL: {}", relation.optString("url"));
                }
            } else {
                logger.info("No relations found (may need $expand=relations in API call)");
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch relations: {}", e.getMessage());
        }

        // Print full JSON (for debugging)
        logger.info("\n=== FULL JSON (for debugging) ===");
        try {
            // WorkItem extends JSONModel, which wraps a JSONObject
            // We can get the underlying JSON by converting to string and parsing, or use reflection
            // Simplest: just use toString() which should give us the JSON representation
            String jsonString = workItem.toString();
            org.json.JSONObject jsonObject = new org.json.JSONObject(jsonString);
            logger.info("{}", jsonObject.toString(2));
        } catch (Exception e) {
            logger.warn("Failed to format JSON: {}", e.getMessage());
            logger.info("{}", workItem.toString());
        }

        logger.info("\n=== Test Case Inspection Complete ===");
    }

    /**
     * Parse XML test steps and display in a readable format.
     * ADO test steps are stored in XML format like:
     * <steps id="0" last="3">
     *   <step id="2" type="ActionStep">
     *     <parameterizedString isformatted="true">Step description</parameterizedString>
     *   </step>
     * </steps>
     */
    private void parseAndDisplayTestSteps(String xmlSteps) {
        if (xmlSteps == null || xmlSteps.trim().isEmpty()) {
            return;
        }

        try {
            // Simple XML parsing using string operations (no external dependencies)
            // Look for <step> tags
            int stepIndex = 1;
            int startPos = 0;
            
            while (true) {
                int stepStart = xmlSteps.indexOf("<step", startPos);
                if (stepStart == -1) {
                    break;
                }
                
                int stepEnd = xmlSteps.indexOf("</step>", stepStart);
                if (stepEnd == -1) {
                    break;
                }
                
                String stepXml = xmlSteps.substring(stepStart, stepEnd + 7);
                
                // Extract step type
                String stepType = "Unknown";
                int typeStart = stepXml.indexOf("type=\"");
                if (typeStart != -1) {
                    int typeEnd = stepXml.indexOf("\"", typeStart + 6);
                    if (typeEnd != -1) {
                        stepType = stepXml.substring(typeStart + 6, typeEnd);
                    }
                }
                
                // Extract step description from parameterizedString
                String stepDescription = "";
                int descStart = stepXml.indexOf("<parameterizedString");
                if (descStart != -1) {
                    int descContentStart = stepXml.indexOf(">", descStart) + 1;
                    int descContentEnd = stepXml.indexOf("</parameterizedString>", descContentStart);
                    if (descContentEnd != -1) {
                        stepDescription = stepXml.substring(descContentStart, descContentEnd).trim();
                        // Decode HTML entities if any
                        stepDescription = stepDescription
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'");
                    }
                }
                
                logger.info("  Step {} [{}]: {}", stepIndex, stepType, stepDescription);
                
                stepIndex++;
                startPos = stepEnd + 7;
            }
            
            if (stepIndex == 1) {
                logger.info("  No steps found in XML (may be empty or different format)");
            }
        } catch (Exception e) {
            logger.warn("Error parsing test steps XML: {}", e.getMessage());
        }
    }
}

