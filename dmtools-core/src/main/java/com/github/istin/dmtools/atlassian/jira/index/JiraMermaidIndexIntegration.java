// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.index;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.index.mermaid.MermaidIndexIntegration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Jira implementation of MermaidIndexIntegration.
 * Retrieves content from Jira tickets using JQL queries.
 * <p>
 * Uses JQL query from includePatterns[0] to search for tickets.
 * Processes tickets one-by-one using Performer callback to optimize memory usage.
 * Supports custom fields, optional comments inclusion, and attachment downloads.
 */
public class JiraMermaidIndexIntegration implements MermaidIndexIntegration {
    
    private static final Logger logger = LogManager.getLogger(JiraMermaidIndexIntegration.class);
    
    private final TrackerClient<? extends ITicket> trackerClient;
    private final String[] customFields;
    private final boolean includeComments;
    
    /**
     * Creates a new JiraMermaidIndexIntegration instance.
     * 
     * @param trackerClient TrackerClient instance (BasicJiraClient or XrayClient)
     * @param customFields Array of custom field names to include in content
     * @param includeComments Whether to include comments in the content
     */
    public JiraMermaidIndexIntegration(TrackerClient<? extends ITicket> trackerClient, 
                                     String[] customFields, 
                                     boolean includeComments) {
        if (trackerClient == null) {
            throw new IllegalArgumentException("TrackerClient is required");
        }
        this.trackerClient = trackerClient;
        this.customFields = customFields;
        this.includeComments = includeComments;
        logger.info("JiraMermaidIndexIntegration initialized with TrackerClient: {} (class: {})", 
                trackerClient.getClass().getSimpleName(), trackerClient.getClass().getName());
    }
    
    @Override
    public void getContentForIndex(List<String> includePatterns, List<String> excludePatterns, ContentProcessor processor) {
        try {
            if (includePatterns == null || includePatterns.isEmpty()) {
                logger.warn("No include patterns provided, no content will be retrieved");
                return;
            }
            
            // Use first include pattern as JQL query
            String jql = includePatterns.get(0);
            logger.info("Processing JQL query: {}", jql);
            logger.debug("Custom fields: {}", customFields != null ? java.util.Arrays.toString(customFields) : "null");
            
            // Use searchAndPerform with Performer callback to process tickets one-by-one
            // This avoids loading all tickets into memory at once
            // Determine which fields to use:
            // - If customFields not specified: use trackerClient default fields + "description" (needed for content)
            // - If customFields specified: use customFields + "key" + "description" + "updated" (to ensure key, description, and updated are always returned)
            // Note: "updated" is needed to track content changes for diagram regeneration
            String[] fieldsToUse;
            if (customFields != null && customFields.length > 0) {
                // Add "key", "description", and "updated" to customFields to ensure they're always returned
                // Note: "key" is at top level in Jira API, but including it in fields ensures proper response
                // "description" is needed for ticket content
                // "updated" is needed to track content changes
                List<String> fieldsList = new ArrayList<>(java.util.Arrays.asList(customFields));
                if (!fieldsList.contains("key")) {
                    fieldsList.add("key");
                }
                if (!fieldsList.contains(Fields.DESCRIPTION)) {
                    fieldsList.add(Fields.DESCRIPTION);
                }
                if (!fieldsList.contains(Fields.UPDATED)) {
                    fieldsList.add(Fields.UPDATED);
                }
                fieldsToUse = fieldsList.toArray(new String[0]);
                logger.debug("Using custom fields + key + description + updated: {}", java.util.Arrays.toString(fieldsToUse));
            } else {
                // Use default fields from trackerClient and add "description" if not present (needed for content)
                // Note: "updated" should already be in default fields, but we ensure it's there
                List<String> fieldsList = new ArrayList<>(java.util.Arrays.asList(trackerClient.getDefaultQueryFields()));
                if (!fieldsList.contains(Fields.DESCRIPTION)) {
                    fieldsList.add(Fields.DESCRIPTION);
                }
                if (!fieldsList.contains(Fields.UPDATED)) {
                    fieldsList.add(Fields.UPDATED);
                }
                fieldsToUse = fieldsList.toArray(new String[0]);
                logger.debug("Using trackerClient default fields + description + updated: {}", java.util.Arrays.toString(fieldsToUse));
            }
            
            @SuppressWarnings("unchecked")
            TrackerClient<ITicket> client = (TrackerClient<ITicket>) trackerClient;
            client.searchAndPerform(new JiraClient.Performer<ITicket>() {
                @Override
                public boolean perform(ITicket ticket) throws Exception {
                    try {
                        processTicket(ticket, excludePatterns, processor);
                    } catch (Exception e) {
                        // Try to get ticket key for logging, but don't fail if it's null
                        String ticketKeyForLogging = "unknown";
                        try {
                            if (ticket != null) {
                                String key = ticket.getTicketKey();
                                if (key != null && !key.isEmpty()) {
                                    ticketKeyForLogging = key;
                                } else if (ticket instanceof com.github.istin.dmtools.common.model.Key) {
                                    key = ((com.github.istin.dmtools.common.model.Key) ticket).getKey();
                                    if (key != null && !key.isEmpty()) {
                                        ticketKeyForLogging = key;
                                    }
                                }
                            }
                        } catch (Exception keyException) {
                            // Ignore key retrieval errors in error handler
                        }
                        logger.error("Error processing ticket {}: {}", ticketKeyForLogging, e.getMessage(), e);
                    }
                    // Return false to continue processing all tickets
                    return false;
                }
            }, jql, fieldsToUse);
            
        } catch (Exception e) {
            logger.error("Error retrieving content from Jira", e);
            throw new RuntimeException("Failed to retrieve content from Jira", e);
        }
    }
    
    /**
     * Processes a single ticket.
     * Extracts content, optionally includes comments, downloads attachments, and calls processor.
     * 
     * @param ticket The ticket to process
     * @param excludePatterns Patterns to exclude (not used for Jira, kept for interface compatibility)
     * @param processor The content processor callback
     */
    private void processTicket(ITicket ticket, List<String> excludePatterns, ContentProcessor processor) {
        if (ticket == null) {
            logger.warn("Ticket is null, skipping");
            return;
        }
        
        // Try multiple ways to get ticket key
        String ticketKey = null;
        try {
            ticketKey = ticket.getTicketKey();
            if (ticketKey != null && !ticketKey.isEmpty()) {
                logger.debug("Got ticket key via getTicketKey(): {}", ticketKey);
            }
        } catch (Exception e) {
            logger.debug("Failed to get ticket key via getTicketKey(): {}", e.getMessage());
        }
        
        // Fallback: try getKey() if ticket implements Key interface
        if ((ticketKey == null || ticketKey.isEmpty()) && ticket instanceof com.github.istin.dmtools.common.model.Key) {
            try {
                ticketKey = ((com.github.istin.dmtools.common.model.Key) ticket).getKey();
                if (ticketKey != null && !ticketKey.isEmpty()) {
                    logger.debug("Got ticket key via getKey(): {}", ticketKey);
                }
            } catch (Exception e) {
                logger.debug("Failed to get ticket key via getKey(): {}", e.getMessage());
            }
        }
        
        // Additional fallback: try to get key from JSON if ticket is a JSONModel
        if ((ticketKey == null || ticketKey.isEmpty()) && ticket instanceof com.github.istin.dmtools.common.model.JSONModel) {
            try {
                com.github.istin.dmtools.common.model.JSONModel jsonModel = (com.github.istin.dmtools.common.model.JSONModel) ticket;
                org.json.JSONObject json = jsonModel.getJSONObject();
                if (json != null) {
                    ticketKey = json.optString("key", null);
                    if (ticketKey != null && !ticketKey.isEmpty()) {
                        logger.debug("Got ticket key from JSON: {}", ticketKey);
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to get ticket key from JSON: {}", e.getMessage());
            }
        }
        
        if (ticketKey == null || ticketKey.isEmpty()) {
            logger.warn("Ticket has no key, skipping. Ticket class: {}, toString preview: {}", 
                ticket.getClass().getName(),
                ticket.toString().length() > 200 ? ticket.toString().substring(0, 200) + "..." : ticket.toString());
            return;
        }
        
        logger.debug("Processing ticket: {}", ticketKey);
        
        // Extract project key from ticket key (e.g., "PROJ-123" -> "PROJ")
        String projectKey = extractProjectKey(ticketKey);
        
        // Get ticket title
        String ticketTitle;
        try {
            ticketTitle = ticket.getTicketTitle();
        } catch (IOException e) {
            logger.warn("Failed to get ticket title for {}, using key: {}", ticketKey, e.getMessage());
            ticketTitle = ticketKey;
        }
        
        // Build content string
        StringBuilder contentBuilder = new StringBuilder();
        
        // Add ticket description
        String description = ticket.getTicketDescription();
        if (description != null && !description.trim().isEmpty()) {
            contentBuilder.append(description);
            logger.debug("Added description for ticket {} (length: {})", ticketKey, description.length());
        } else {
            logger.debug("No description found for ticket {}", ticketKey);
        }
        
        // Add custom field values if specified
        if (customFields != null && customFields.length > 0) {
            for (String fieldName : customFields) {
                try {
                    String fieldValue = ticket.getFieldValueAsString(fieldName);
                    if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                        contentBuilder.append("\n\n--- ").append(fieldName).append(" ---\n");
                        contentBuilder.append(fieldValue);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to get custom field {} for ticket {}: {}", fieldName, ticketKey, e.getMessage());
                }
            }
        }
        
        // Add X-ray test steps if available (for XrayClient enriched tickets)
        try {
            Fields fields = ticket.getFields();
            if (fields != null) {
                org.json.JSONObject fieldsJson = fields.getJSONObject();
                if (fieldsJson != null && fieldsJson.has("xrayTestSteps")) {
                    org.json.JSONArray testSteps = fieldsJson.optJSONArray("xrayTestSteps");
                    if (testSteps != null && testSteps.length() > 0) {
                        contentBuilder.append("\n\n--- Test Steps ---\n");
                        for (int i = 0; i < testSteps.length(); i++) {
                            org.json.JSONObject step = testSteps.optJSONObject(i);
                            if (step != null) {
                                contentBuilder.append("\nStep ").append(i + 1).append(":\n");
                                String action = step.optString("action", "");
                                String data = step.optString("data", "");
                                String result = step.optString("result", step.optString("expectedResult", ""));
                                
                                if (!action.isEmpty()) {
                                    contentBuilder.append("  Action: ").append(action).append("\n");
                                }
                                if (!data.isEmpty()) {
                                    contentBuilder.append("  Data: ").append(data).append("\n");
                                }
                                if (!result.isEmpty()) {
                                    contentBuilder.append("  Expected Result: ").append(result).append("\n");
                                }
                            }
                        }
                        logger.info("✅ Added {} test steps to content for ticket {}", testSteps.length(), ticketKey);
                    }
                }
                
                // Add X-ray preconditions if available
                if (fieldsJson != null && fieldsJson.has("xrayPreconditions")) {
                    org.json.JSONArray preconditions = fieldsJson.optJSONArray("xrayPreconditions");
                    if (preconditions != null && preconditions.length() > 0) {
                        contentBuilder.append("\n\n--- Preconditions ---\n");
                        for (int i = 0; i < preconditions.length(); i++) {
                            org.json.JSONObject precondition = preconditions.optJSONObject(i);
                            if (precondition != null) {
                                String precKey = "";
                                String precSummary = "";
                                String precDescription = "";
                                
                                if (precondition.has("jira")) {
                                    org.json.JSONObject jira = precondition.getJSONObject("jira");
                                    precKey = jira.optString("key", "");
                                }
                                precSummary = precondition.optString("summary", "");
                                precDescription = precondition.optString("description", "");
                                
                                contentBuilder.append("\nPrecondition ").append(i + 1);
                                if (!precKey.isEmpty()) {
                                    contentBuilder.append(" (").append(precKey).append(")");
                                }
                                contentBuilder.append(":\n");
                                
                                if (!precSummary.isEmpty()) {
                                    contentBuilder.append("  Summary: ").append(precSummary).append("\n");
                                }
                                if (!precDescription.isEmpty()) {
                                    contentBuilder.append("  Description: ").append(precDescription).append("\n");
                                }
                                
                                // Add definition if available
                                if (precondition.has("definition")) {
                                    String precDefinition = precondition.optString("definition", "");
                                    if (!precDefinition.isEmpty()) {
                                        contentBuilder.append("  Definition: ").append(precDefinition).append("\n");
                                    }
                                }
                            }
                        }
                        logger.info("✅ Added {} preconditions to content for ticket {}", preconditions.length(), ticketKey);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract X-ray test steps/preconditions for ticket {}: {}", ticketKey, e.getMessage());
        }
        
        // Add comments if requested
        if (includeComments) {
            try {
                List<? extends IComment> comments = trackerClient.getComments(ticketKey, ticket);
                if (comments != null && !comments.isEmpty()) {
                    contentBuilder.append("\n\n--- Comments ---\n");
                    for (IComment comment : comments) {
                        if (comment == null) {
                            continue;
                        }
                        String authorName = "Unknown";
                        String authorEmail = "";
                        String createdDate = "";
                        try {
                            if (comment.getAuthor() != null) {
                                authorName = comment.getAuthor().getFullName();
                                authorEmail = comment.getAuthor().getEmailAddress();
                            }
                            if (comment.getCreated() != null) {
                                createdDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(comment.getCreated());
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to get comment author/date: {}", e.getMessage());
                        }
                        
                        String commentBody = comment.getBody();
                        if (commentBody != null && !commentBody.trim().isEmpty()) {
                            contentBuilder.append("\n--- Comment by ").append(authorName);
                            if (authorEmail != null && !authorEmail.isEmpty()) {
                                contentBuilder.append(" (").append(authorEmail).append(")");
                            }
                            if (!createdDate.isEmpty()) {
                                contentBuilder.append(" on ").append(createdDate);
                            }
                            contentBuilder.append(" ---\n");
                            contentBuilder.append(commentBody).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get comments for ticket {}: {}", ticketKey, e.getMessage());
            }
        }
        
        String content = contentBuilder.toString();
        logger.debug("Total content length for ticket {}: {}", ticketKey, content.length());
        
        // Get metadata
        List<String> metadata = new ArrayList<>();
        metadata.add("projectKey:" + projectKey);
        metadata.add("ticketKey:" + ticketKey);
        try {
            String issueType = ticket.getIssueType();
            if (issueType != null) {
                metadata.add("issueType:" + issueType);
            }
        } catch (IOException e) {
            logger.debug("Failed to get issue type: {}", e.getMessage());
        }
        
        // Get and download attachments
        List<File> attachmentFiles = new ArrayList<>();
        Path tempDirPath = null;
        try {
            List<? extends com.github.istin.dmtools.common.model.IAttachment> attachments = ticket.getAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                // Create temp directory for attachments
                tempDirPath = Files.createTempDirectory("jira-attachments-" + ticketKey);
                
                // Cast trackerClient to JiraClient to access convertUrlToFile method
                if (trackerClient instanceof JiraClient) {
                    JiraClient<?> jiraClient = (JiraClient<?>) trackerClient;
                    
                    for (com.github.istin.dmtools.common.model.IAttachment attachment : attachments) {
                        if (attachment == null) {
                            continue;
                        }
                        String attachmentName = attachment.getName();
                        String attachmentUrl = attachment.getUrl();
                        
                        if (attachmentUrl != null && !attachmentUrl.isEmpty()) {
                            try {
                                // Download attachment using JiraClient
                                File downloadedFile = jiraClient.convertUrlToFile(attachmentUrl);
                                if (downloadedFile != null && downloadedFile.exists()) {
                                    attachmentFiles.add(downloadedFile);
                                    logger.debug("Downloaded attachment {} for ticket {}", attachmentName, ticketKey);
                                } else {
                                    logger.warn("Downloaded file is null or doesn't exist for attachment {} in ticket {}", attachmentName, ticketKey);
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to download attachment {} for ticket {}: {}", attachmentName, ticketKey, e.getMessage());
                            }
                        } else {
                            logger.warn("Attachment {} has no URL for ticket {}", attachmentName, ticketKey);
                        }
                    }
                } else {
                    logger.warn("TrackerClient is not a JiraClient instance, cannot download attachments");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get attachments for ticket {}: {}", ticketKey, e.getMessage());
        }
        
        // Get last modified date
        Date lastModified;
        Long updatedMillis = ticket.getUpdatedAsMillis();
        if (updatedMillis != null && updatedMillis > 0) {
            lastModified = new Date(updatedMillis);
        } else {
            Date created = ticket.getCreated();
            lastModified = created != null ? created : new Date();
        }
        
        // Build pathOrId as projectKey/ticketKey
        String pathOrId = projectKey + "/" + ticketKey;
        
        // Process content with attachments
        try {
            processor.process(pathOrId, ticketTitle, content, metadata, attachmentFiles, lastModified);
        } finally {
            // Clean up temp directory after processing is complete
            if (tempDirPath != null) {
                cleanupTempDirectory(tempDirPath);
            }
        }
    }
    
    /**
     * Extracts project key from ticket key.
     * Example: "PROJ-123" -> "PROJ"
     * 
     * @param ticketKey The ticket key (e.g., "PROJ-123")
     * @return The project key
     */
    private String extractProjectKey(String ticketKey) {
        if (ticketKey == null || ticketKey.isEmpty()) {
            return "UNKNOWN";
        }
        int dashIndex = ticketKey.indexOf('-');
        if (dashIndex > 0) {
            return ticketKey.substring(0, dashIndex);
        }
        return "UNKNOWN";
    }
    
    /**
     * Cleans up a temporary directory and its contents.
     * Walks the directory tree in reverse order to delete files before directories.
     *
     * @param tempDirPath the path to the temporary directory
     */
    private void cleanupTempDirectory(Path tempDirPath) {
        try {
            Files.walk(tempDirPath)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        logger.debug("Failed to delete temp file: {}", path, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to clean up temp directory: {}", tempDirPath, e);
        }
    }
}

