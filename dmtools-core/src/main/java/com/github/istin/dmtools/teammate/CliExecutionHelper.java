package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Helper class for CLI command execution within Teammate jobs.
 * Handles context preparation, command execution, and output processing.
 */
public class CliExecutionHelper {
    
    private static final Logger logger = LogManager.getLogger(CliExecutionHelper.class);
    
    private static final String INPUT_FOLDER_PREFIX = "input";
    private static final String REQUEST_FILE_NAME = "request.md";
    private static final String OUTPUT_FOLDER = "outputs";
    private static final String RESPONSE_FILE_NAME = "response.md";
    
    /**
     * Creates input context folder and files for CLI command execution.
     * 
     * @param ticket The ticket to create context for
     * @param inputParams The input parameters to save as request.md
     * @param trackerClient The tracker client for downloading attachments
     * @return Path to the created input folder
     * @throws IOException if folder/file creation fails
     */
    public Path createInputContext(ITicket ticket, String inputParams, TrackerClient<?> trackerClient) throws IOException {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        
        String ticketKey = ticket.getTicketKey();
        if (ticketKey == null || ticketKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket key cannot be null or empty");
        }
        
        // Create input folder structure: input/[TICKET-KEY]/
        Path inputFolderPath = Paths.get(INPUT_FOLDER_PREFIX, ticketKey);
        Files.createDirectories(inputFolderPath);
        logger.info("Created input folder: {}", inputFolderPath.toAbsolutePath());
        
        // Write inputParams to request.md file
        if (inputParams != null && !inputParams.trim().isEmpty()) {
            Path requestFilePath = inputFolderPath.resolve(REQUEST_FILE_NAME);
            Files.write(requestFilePath, inputParams.getBytes(StandardCharsets.UTF_8));
            logger.info("Created request file: {} ({} bytes)", requestFilePath.toAbsolutePath(), inputParams.length());
        }
        
        // Download all ticket attachments to the input folder
        List<? extends IAttachment> attachments = ticket.getAttachments();
        if (attachments != null && !attachments.isEmpty() && trackerClient != null) {
            logger.info("Downloading {} attachments for ticket {}", attachments.size(), ticketKey);
            downloadAttachments(attachments, inputFolderPath, trackerClient);
        } else {
            logger.info("No attachments found for ticket {}", ticketKey);
        }
        
        return inputFolderPath;
    }
    
    /**
     * Downloads ticket attachments to the specified folder.
     * 
     * @param attachments List of attachments to download
     * @param targetFolder Target folder to save attachments
     * @param trackerClient Tracker client for downloading files
     * @throws IOException if attachment download fails
     */
    private void downloadAttachments(List<? extends IAttachment> attachments, Path targetFolder, TrackerClient<?> trackerClient) throws IOException {
        for (IAttachment attachment : attachments) {
            try {
                String fileName = attachment.getName();
                if (fileName == null || fileName.trim().isEmpty()) {
                    logger.warn("Skipping attachment with empty filename");
                    continue;
                }
                
                // Ensure safe filename (remove path separators)
                fileName = fileName.replaceAll("[/\\\\]", "_");
                
                Path attachmentPath = targetFolder.resolve(fileName);
                
                // Download attachment using TrackerClient
                String attachmentUrl = attachment.getUrl();
                if (attachmentUrl != null && !attachmentUrl.trim().isEmpty()) {
                    File downloadedFile = trackerClient.convertUrlToFile(attachmentUrl);
                    if (downloadedFile != null && downloadedFile.exists()) {
                        Files.copy(downloadedFile.toPath(), attachmentPath);
                        logger.info("Downloaded attachment: {} ({} bytes)", fileName, Files.size(attachmentPath));
                    } else {
                        logger.warn("Failed to download attachment: {}", fileName);
                    }
                } else {
                    logger.warn("Attachment {} has no URL", fileName);
                }
            } catch (Exception e) {
                logger.error("Failed to download attachment {}: {}", attachment.getName(), e.getMessage());
                // Continue with other attachments instead of failing completely
            }
        }
    }
    
    /**
     * Executes CLI commands and collects their responses.
     * 
     * @param cliCommands Array of CLI commands to execute
     * @param workingDirectory Working directory for command execution (optional)
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile) {
        StringBuilder cliResponses = new StringBuilder();
        
        if (cliCommands == null || cliCommands.length == 0) {
            logger.info("No CLI commands to execute");
            return cliResponses;
        }
        
        // Load environment variables from dmtools.env for CLI tools like cursor-agent
        Map<String, String> envVars = CommandLineUtils.loadEnvironmentFromFile(envVariablesFile);
        if (!envVars.isEmpty()) {
            logger.info("Loaded {} environment variables from dmtools.env", envVars.size());
            // Log if CURSOR_API_KEY is available (without revealing the key)
            if (envVars.containsKey("CURSOR_API_KEY")) {
                logger.info("CURSOR_API_KEY found in environment (length: {})", envVars.get("CURSOR_API_KEY").length());
            }
        }
        
        // Convert Path to File for ProcessBuilder - safer than changing system properties
        File workingDir = null;
        if (workingDirectory != null && Files.exists(workingDirectory) && Files.isDirectory(workingDirectory)) {
            workingDir = workingDirectory.toFile();
            logger.info("Set working directory to: {}", workingDirectory.toAbsolutePath());
        }
        
        for (String command : cliCommands) {
            if (command == null || command.trim().isEmpty()) {
                logger.warn("Skipping empty CLI command");
                continue;
            }
            
            try {
                logger.info("Executing CLI command: {}", command);
                // Use the new method that accepts working directory and environment variables
                String response = CommandLineUtils.runCommand(command.trim(), workingDir, envVars);
                
                if (response != null && !response.trim().isEmpty()) {
                    cliResponses.append("CLI Command: ").append(command).append("\n");
                    cliResponses.append("Response:\n").append(response).append("\n\n");
                    logger.info("CLI command completed successfully");
                } else {
                    logger.warn("CLI command returned empty response");
                }
            } catch (Exception e) {
                String errorMsg = "Failed to execute CLI command '" + command + "': " + e.getMessage();
                
                // Check if this is a cursor-agent related error and provide helpful message
                if (command.contains("cursor-agent")) {
                    errorMsg += "\n\nNote: Cursor AI CLI may not be available on this platform.";
                    errorMsg += "\nCursor CLI is currently supported on macOS and Windows.";
                    errorMsg += "\nFor Linux environments, consider using alternative AI tools or running on supported platforms.";
                }
                
                logger.error(errorMsg, e);
                cliResponses.append("CLI Command: ").append(command).append("\n");
                cliResponses.append("Error: ").append(errorMsg).append("\n\n");
            }
        }
        
        return cliResponses;
    }
    
    /**
     * Executes CLI commands in the specified working directory and processes output response.
     * 
     * @param cliCommands Array of CLI commands to execute
     * @param workingDirectory Working directory for command execution (optional)
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory, String envVariablesFile) {
        StringBuilder cliResponses = executeCliCommands(cliCommands, workingDirectory, envVariablesFile);
        
        // Check for output response file in the working directory where commands were executed
        String outputResponse = processOutputResponse(workingDirectory);
        
        return new CliExecutionResult(cliResponses, outputResponse);
    }
    
    /**
     * Processes output response from CLI commands by checking for outputs/response.md file.
     * 
     * @return Content of outputs/response.md file if it exists, null otherwise
     */
    public String processOutputResponse() {
        return processOutputResponse(null);
    }
    
    /**
     * Processes output response from CLI commands by checking for outputs/response.md file
     * relative to the specified working directory.
     * 
     * @param workingDirectory Working directory to look for outputs/response.md file (null for current directory)
     * @return Content of outputs/response.md file if it exists, null otherwise
     */
    public String processOutputResponse(Path workingDirectory) {
        Path outputFilePath;
        if (workingDirectory != null) {
            outputFilePath = workingDirectory.resolve(OUTPUT_FOLDER).resolve(RESPONSE_FILE_NAME);
        } else {
            outputFilePath = Paths.get(OUTPUT_FOLDER, RESPONSE_FILE_NAME);
        }
        
        if (!Files.exists(outputFilePath)) {
            logger.info("No output response file found at: {}", outputFilePath.toAbsolutePath());
            return null;
        }
        
        try {
            String content = Files.readString(outputFilePath, StandardCharsets.UTF_8);
            if (content != null && !content.trim().isEmpty()) {
                logger.info("Read output response file: {} ({} bytes)", 
                           outputFilePath.toAbsolutePath(), content.length());
                return content;
            } else {
                logger.warn("Output response file is empty: {}", outputFilePath.toAbsolutePath());
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to read output response file {}: {}", 
                        outputFilePath.toAbsolutePath(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Cleans up temporary input folders and files.
     * 
     * @param inputFolderPath Path to the input folder to clean up
     */
    public void cleanupInputContext(Path inputFolderPath) {
        if (inputFolderPath == null || !Files.exists(inputFolderPath)) {
            return;
        }
        
        try {
            FileUtils.deleteDirectory(inputFolderPath.toFile());
            logger.info("Cleaned up input folder: {}", inputFolderPath.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to cleanup input folder {}: {}", 
                       inputFolderPath.toAbsolutePath(), e.getMessage());
        }
    }
    
    /**
     * Result container for CLI execution that includes both command responses and output response.
     */
    public static class CliExecutionResult {
        private final StringBuilder commandResponses;
        private final String outputResponse;
        
        public CliExecutionResult(StringBuilder commandResponses, String outputResponse) {
            this.commandResponses = commandResponses;
            this.outputResponse = outputResponse;
        }
        
        public StringBuilder getCommandResponses() {
            return commandResponses;
        }
        
        public String getOutputResponse() {
            return outputResponse;
        }
        
        public boolean hasOutputResponse() {
            return outputResponse != null && !outputResponse.trim().isEmpty();
        }
    }
}
