package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Teammate ADO attachment download functionality.
 * Tests that work item 755 from RustemAgziamov/ai-native-sdlc-blueprint can be fetched
 * and its attachments downloaded to input folder.
 *
 * Configuration via PropertyReader (environment variables or config.properties):
 * - ADO_ORGANIZATION: RustemAgziamov
 * - ADO_PROJECT: ai-native-sdlc-blueprint
 * - ADO_PAT_TOKEN: Your personal access token
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TeammateAdoAttachmentIntegrationTest {

    private static final Logger logger = LogManager.getLogger(TeammateAdoAttachmentIntegrationTest.class);

    private static BasicAzureDevOpsClient adoClient;
    private static final String TEST_WORK_ITEM_ID = "755";
    private static final String INPUT_FOLDER_PREFIX = "input";

    @BeforeAll
    static void setUp() throws IOException {
        logger.info("Setting up TeammateAdoAttachmentIntegrationTest");

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

    @AfterAll
    static void tearDown() {
        // Clean up input folder after tests
        try {
            Path inputFolder = Paths.get(INPUT_FOLDER_PREFIX, TEST_WORK_ITEM_ID);
            if (Files.exists(inputFolder)) {
                FileUtils.deleteDirectory(inputFolder.toFile());
                logger.info("Cleaned up input folder: {}", inputFolder.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up input folder: {}", e.getMessage());
        }
    }

    /**
     * Test 1: Verify that work item 755 can be fetched and has description
     */
    @Test
    @Order(1)
    void testWorkItem755HasDescription() throws IOException {
        logger.info("Testing work item 755 retrieval and description");

        // Fetch work item
        WorkItem workItem = adoClient.performTicket(TEST_WORK_ITEM_ID, adoClient.getExtendedQueryFields());
        assertNotNull(workItem, "Work item 755 should be found");

        // Verify basic fields
        assertEquals(TEST_WORK_ITEM_ID, workItem.getTicketKey(), "Work item ID should match");

        // Verify description
        String description = workItem.getTicketDescription();
        assertNotNull(description, "Work item should have a description");
        assertFalse(description.trim().isEmpty(), "Description should not be empty");

        logger.info("✓ Work item 755 found with description ({} characters)", description.length());
        logger.info("Work item title: {}", workItem.getTicketTitle());
        logger.info("Work item type: {}", workItem.getIssueType());
    }

    /**
     * Test 2: Verify that attachments are downloaded to input folder
     */
    @Test
    @Order(2)
    void testAttachmentsDownloadedToInputFolder() throws IOException {
        logger.info("Testing attachment download to input folder for work item 755");

        // Fetch work item with full context
        WorkItem workItem = adoClient.performTicket(TEST_WORK_ITEM_ID, adoClient.getExtendedQueryFields());
        assertNotNull(workItem, "Work item 755 should be found");

        List<? extends IComment> comments = adoClient.getComments(TEST_WORK_ITEM_ID, workItem);
        // Create input context using CliExecutionHelper
        // This will enrich the work item with relations (formal attachments)
        CliExecutionHelper cliHelper = new CliExecutionHelper();
        String inputParams = "Test context for work item " + TEST_WORK_ITEM_ID;

        Path inputFolder = cliHelper.createInputContext(workItem, inputParams, adoClient);

        // Get attachments AFTER enrichment to get both formal attachments and embedded images
        List<? extends IAttachment> attachments = workItem.getAttachments();
        logger.info("Work item has {} attachments (after enrichment)", attachments != null ? attachments.size() : 0);

        if (attachments == null || attachments.isEmpty()) {
            logger.warn("Work item 755 has no attachments - skipping download test");
            return; // Skip test if no attachments
        }

        // Verify input folder was created
        assertTrue(Files.exists(inputFolder), "Input folder should exist");
        assertTrue(Files.isDirectory(inputFolder), "Input folder should be a directory");

        // Verify request.md file was created
        Path requestFile = inputFolder.resolve("request.md");
        assertTrue(Files.exists(requestFile), "request.md should exist");
        String requestContent = Files.readString(requestFile);
        assertEquals(inputParams, requestContent, "request.md should contain input params");

        // Verify attachments were downloaded
        for (IAttachment attachment : attachments) {
            if (attachment == null) {
                logger.warn("Skipping null attachment");
                continue;
            }

            String fileName = attachment.getName();
            assertNotNull(fileName, "Attachment should have a name");

            // Clean filename (same logic as in CliExecutionHelper)
            String cleanFileName = fileName.replaceAll("[/\\\\]", "_");
            Path attachmentPath = inputFolder.resolve(cleanFileName);

            assertTrue(Files.exists(attachmentPath),
                String.format("Attachment '%s' should be downloaded to input folder", cleanFileName));

            long fileSize = Files.size(attachmentPath);
            assertTrue(fileSize > 0, String.format("Attachment '%s' should not be empty", cleanFileName));

            logger.info("✓ Verified attachment: {} ({} bytes)", cleanFileName, fileSize);
        }

        logger.info("✓ All {} attachments successfully downloaded to: {}",
            attachments.size(), inputFolder.toAbsolutePath());
    }

    /**
     * Test 3: End-to-end test simulating Teammate behavior
     */
    @Test
    @Order(3)
    void testTeammateWorkflowWithAttachments() throws IOException {
        logger.info("Testing full Teammate workflow with work item 755");

        // 1. Fetch work item (simulating Teammate.runJobImpl)
        WorkItem workItem = adoClient.performTicket(TEST_WORK_ITEM_ID, adoClient.getExtendedQueryFields());
        assertNotNull(workItem, "Work item should be found");

        // 2. Verify we can access description
        String description = workItem.getTicketDescription();
        assertNotNull(description, "Description should be available");

        // 3. Create input context with attachments
        CliExecutionHelper cliHelper = new CliExecutionHelper();
        String ticketContext = String.format(
            "Work Item: %s\nTitle: %s\nDescription: %s",
            workItem.getTicketKey(),
            workItem.getTicketTitle(),
            description
        );

        Path inputFolder = cliHelper.createInputContext(workItem, ticketContext, adoClient);

        // 4. Verify the complete setup
        assertTrue(Files.exists(inputFolder), "Input folder should exist");

        Path requestFile = inputFolder.resolve("request.md");
        assertTrue(Files.exists(requestFile), "request.md should exist");

        String requestContent = Files.readString(requestFile);
        assertTrue(requestContent.contains(workItem.getTicketKey()),
            "request.md should contain work item ID");
        assertTrue(requestContent.contains(workItem.getTicketTitle()),
            "request.md should contain work item title");

        // 5. Count attachments in folder
        File[] files = inputFolder.toFile().listFiles((dir, name) -> !name.equals("request.md"));
        int attachmentCount = files != null ? files.length : 0;

        List<? extends IAttachment> expectedAttachments = workItem.getAttachments();
        int expectedCount = expectedAttachments != null ? expectedAttachments.size() : 0;

        assertEquals(expectedCount, attachmentCount,
            "Number of downloaded attachments should match work item attachments");

        logger.info("✓ Teammate workflow completed successfully");
        logger.info("  - Input folder: {}", inputFolder.toAbsolutePath());
        logger.info("  - request.md created with {} characters", requestContent.length());
        logger.info("  - {} attachments downloaded", attachmentCount);
    }

    /**
     * Test 4: Verify direct attachment download using convertUrlToFile
     */
    @Test
    @Order(4)
    void testDirectAttachmentDownload() throws IOException {
        logger.info("Testing direct attachment download via convertUrlToFile");

        WorkItem workItem = adoClient.performTicket(TEST_WORK_ITEM_ID, adoClient.getExtendedQueryFields());
        List<? extends IAttachment> attachments = workItem.getAttachments();

        if (attachments == null || attachments.isEmpty()) {
            logger.warn("Work item 755 has no attachments - skipping direct download test");
            return;
        }

        // Test downloading first attachment directly
        IAttachment firstAttachment = attachments.get(0);
        assertNotNull(firstAttachment, "First attachment should not be null");

        String attachmentUrl = firstAttachment.getUrl();
        assertNotNull(attachmentUrl, "Attachment should have URL");

        logger.info("Downloading attachment: {} from URL: {}", firstAttachment.getName(), attachmentUrl);

        // Download using ADO client's convertUrlToFile method
        File downloadedFile = adoClient.convertUrlToFile(attachmentUrl);

        assertNotNull(downloadedFile, "Downloaded file should not be null");
        assertTrue(downloadedFile.exists(), "Downloaded file should exist");
        assertTrue(downloadedFile.length() > 0, "Downloaded file should not be empty");

        logger.info("✓ Direct download successful: {} ({} bytes)",
            downloadedFile.getName(), downloadedFile.length());
    }
}

