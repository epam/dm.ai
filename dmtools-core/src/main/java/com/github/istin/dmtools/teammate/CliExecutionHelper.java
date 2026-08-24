// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CliCommandFailedException;
import com.github.istin.dmtools.common.utils.CliExecutionStoppedException;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.common.utils.IOUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.ConfluencePageDownloader;
import com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Helper class for CLI command execution within Teammate jobs.
 * Handles context preparation, command execution, and output processing.
 */
public class CliExecutionHelper {
    
    private static final Logger logger = LogManager.getLogger(CliExecutionHelper.class);
    
    private static final String INPUT_FOLDER_PREFIX = "input";
    private static final String REQUEST_FILE_NAME = "request.md";
    private static final String OUTPUT_FOLDER = "outputs";
    private static final String OUTPUT_FOLDER_LEGACY = "output";  // Backward compatibility
    private static final String COMMENTS_FILE_NAME = "comments.md";
    private static final String RESPONSE_FILE_NAME = "response.md";

    public enum OutputFolderPreference {
        LEGACY_OUTPUT_FIRST,
        OUTPUTS_FIRST
    }

    private static final String[] VIDEO_EXTENSIONS = {
        ".mp4", ".mov", ".avi", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".ogv", ".mpg", ".mpeg"
    };

    /**
     * Returns true if the given filename has a video file extension.
     */
    public static boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /**
     * Creates input context folder and files for CLI command execution.
     * All attachments (including videos) are downloaded so the CLI tool has full access.
     * <p>
     * When {@code ticket} is {@code null}, a standalone input folder ({@code input/standalone/})
     * is created without ticket-specific enrichment or attachments.
     *
     * @param ticket The ticket to create context for; may be null for standalone CLI mode
     * @param inputParams The input parameters to save as request.md
     * @param trackerClient The tracker client for downloading attachments
     * @return Path to the created input folder
     * @throws IOException if folder/file creation fails
     */
    public Path createInputContext(ITicket ticket, String inputParams, TrackerClient<?> trackerClient) throws IOException {
        return createInputContext(ticket, inputParams, trackerClient, null);
    }

    /**
     * Creates input context folder and files for CLI command execution under an optional base directory.
     * All attachments (including videos) are downloaded so the CLI tool has full access.
     * <p>
     * When {@code ticket} is {@code null}, a standalone input folder ({@code input/standalone/})
     * is created without ticket-specific enrichment or attachments.
     *
     * @param ticket The ticket to create context for; may be null for standalone CLI mode
     * @param inputParams The input parameters to save as request.md
     * @param trackerClient The tracker client for downloading attachments
     * @param baseDirectory Base directory for the {@code input/} folder; if null, uses the current working directory
     * @return Path to the created input folder
     * @throws IOException if folder/file creation fails
     */
    public Path createInputContext(ITicket ticket, String inputParams, TrackerClient<?> trackerClient, Path baseDirectory) throws IOException {
        return createInputContext(ticket, inputParams, trackerClient, baseDirectory, null);
    }

    /**
     * Creates input context folder and files for CLI command execution under an optional base directory,
     * with an optional override for the attachment list to download.
     * <p>
     * When {@code ticket} is {@code null}, a standalone input folder ({@code input/standalone/})
     * is created without ticket-specific enrichment or attachments.
     *
     * @param ticket The ticket to create context for; may be null for standalone CLI mode
     * @param inputParams The input parameters to save as request.md
     * @param trackerClient The tracker client for downloading attachments
     * @param baseDirectory Base directory for the {@code input/} folder; if null, uses the current working directory
     * @param attachmentsOverride Attachments to download instead of {@code ticket.getAttachments()}; may be null
     * @return Path to the created input folder
     * @throws IOException if folder/file creation fails
     */
    public Path createInputContext(ITicket ticket, String inputParams, TrackerClient<?> trackerClient,
                                    Path baseDirectory, List<? extends IAttachment> attachmentsOverride) throws IOException {
        return createInputContext(ticket, inputParams, trackerClient, baseDirectory, attachmentsOverride, false);
    }

    Path createInputContext(ITicket ticket, String inputParams, TrackerClient<?> trackerClient,
                            Path baseDirectory, List<? extends IAttachment> attachmentsOverride,
                            boolean relationsAlreadyEnriched) throws IOException {
        boolean isStandalone = ticket == null;
        String ticketKey = isStandalone ? "standalone" : ticket.getTicketKey();
        if (ticketKey == null || ticketKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket key cannot be null or empty");
        }

        // Create input folder structure: [baseDirectory/]input/[TICKET-KEY]/ or input/standalone/
        Path inputFolderPath = baseDirectory != null
                ? baseDirectory.resolve(INPUT_FOLDER_PREFIX).resolve(ticketKey)
                : Paths.get(INPUT_FOLDER_PREFIX, ticketKey);
        Files.createDirectories(inputFolderPath);
        logger.info("Created input folder: {}", inputFolderPath.toAbsolutePath());

        // Write inputParams to request.md file
        if (inputParams != null && !inputParams.trim().isEmpty()) {
            Path requestFilePath = inputFolderPath.resolve(REQUEST_FILE_NAME);
            Files.write(requestFilePath, inputParams.getBytes(StandardCharsets.UTF_8));
            logger.info("Created request file: {} ({} bytes)", requestFilePath.toAbsolutePath(), inputParams.length());
        }

        if (isStandalone) {
            logger.info("Standalone CLI mode - skipping ticket enrichment and attachments");
            return inputFolderPath;
        }

        if (!relationsAlreadyEnriched) {
            enrichTicketWithRelations(ticket, trackerClient);
        }

        // Download ticket attachments to the input folder
        List<? extends IAttachment> attachments = attachmentsOverride != null ? attachmentsOverride : ticket.getAttachments();
        logger.info("📎 Ticket {} has {} attachments", ticketKey, attachments != null ? attachments.size() : 0);

        if (attachments != null && !attachments.isEmpty() && trackerClient != null) {
            logger.info("⬇️ Downloading {} attachments for ticket {}", attachments.size(), ticketKey);
            for (IAttachment att : attachments) {
                if (att != null) {
                    logger.info("  - {} (URL: {})", att.getName(), att.getUrl());
                }
            }
            downloadAttachments(attachments, inputFolderPath, trackerClient);
        } else {
            if (attachments == null || attachments.isEmpty()) {
                logger.info("ℹ️ No attachments found for ticket {}", ticketKey);
            }
            if (trackerClient == null) {
                logger.warn("⚠️ TrackerClient is null, cannot download attachments");
            }
        }

        return inputFolderPath;
    }

    void enrichTicketWithRelations(ITicket ticket, TrackerClient<?> trackerClient) {
        if (!(trackerClient instanceof AzureDevOpsClient) || !(ticket instanceof WorkItem)) {
            return;
        }
        try {
            AzureDevOpsClient adoClient = (AzureDevOpsClient) trackerClient;
            WorkItem workItem = (WorkItem) ticket;
            adoClient.enrichWorkItemWithRelations(workItem);
            logger.info("🔄 Enriched ADO work item {} with relations for attachment detection",
                    ticket.getTicketKey());
        } catch (Exception e) {
            logger.warn("⚠️ Could not enrich work item with relations: {}", e.getMessage());
        }
    }

    /**
     * Writes ticket comments to comments.md in the given input folder.
     * Uses the same toText() format as ChunkPreparation when sending comments to AI.
     *
     * @param inputFolderPath Path to the input folder (created by createInputContext)
     * @param comments List of comments to write
     * @throws IOException if file writing fails
     */
    public void writeCommentsFile(Path inputFolderPath, List<? extends IComment> comments) throws IOException {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        StringBuilder content = new StringBuilder();
        for (IComment comment : comments) {
            if (comment instanceof ToText) {
                String text = ((ToText) comment).toText();
                if (text != null && !text.isBlank()) {
                    if (!content.isEmpty()) {
                        content.append("\n\n---\n\n");
                    }
                    content.append(text);
                }
            }
        }
        if (!content.isEmpty()) {
            Files.write(inputFolderPath.resolve(COMMENTS_FILE_NAME),
                    content.toString().getBytes(StandardCharsets.UTF_8));
            logger.info("Created comments.md with {} comments ({} bytes)", comments.size(), content.length());
        }
    }

    /**
     * Fetches Confluence pages referenced in {@code textContent} and writes both the page text
     * and all page attachments to {@code inputFolderPath/confluence/} so CLI agents can read them
     * without web access.
     * <p>
     * This overload preserves the original behavior: depth 0 (only pages explicitly linked in the
     * ticket text) and attachments enabled.
     *
     * @param textContent     Any text that may contain Confluence URLs (e.g. full ticket text)
     * @param inputFolderPath The base input folder (e.g. {@code input/PROJ-123})
     * @param confluence      Confluence client; if {@code null} the method does nothing
     */
    public void writeConfluencePagesFile(String textContent, Path inputFolderPath, Confluence confluence) {
        writeConfluencePagesFile(textContent, inputFolderPath, confluence, 0, true, false);
    }

    /**
     * Backward-compatible overload that does not fetch inline comments.
     */
    public void writeConfluencePagesFile(String textContent, Path inputFolderPath, Confluence confluence,
                                         int confluenceDepth, boolean confluenceAttachments) {
        writeConfluencePagesFile(textContent, inputFolderPath, confluence, confluenceDepth, confluenceAttachments, false);
    }

    /**
     * Fetches Confluence pages referenced in {@code textContent} and writes both the page text
     * and all page attachments to {@code inputFolderPath/confluence/} so CLI agents can read them
     * without web access.
     * <p>
     * Recursively follows internal Confluence links and {@code children} macros up to
     * {@code confluenceDepth} levels. Attachments are downloaded for every fetched page unless
     * {@code confluenceAttachments} is {@code false}.
     * <p>
     * Delegates to {@link ConfluencePageDownloader} so that Teammate and the
     * {@code confluence_download_pages} MCP tool use the exact same download logic.
     *
     * @param textContent              Any text that may contain Confluence URLs (e.g. full ticket text)
     * @param inputFolderPath          The base input folder (e.g. {@code input/PROJ-123})
     * @param confluence               Confluence client; if {@code null} the method does nothing
     * @param confluenceDepth          How many levels of linked/child pages to follow (0 = only ticket links)
     * @param confluenceAttachments    Whether to download attachments for each fetched page
     * @param includeConfluenceComments Whether to fetch inline comments for each fetched page
     */
    public void writeConfluencePagesFile(String textContent, Path inputFolderPath, Confluence confluence,
                                         int confluenceDepth, boolean confluenceAttachments,
                                         boolean includeConfluenceComments) {
        if (confluence == null || textContent == null || textContent.isBlank()) return;
        try {
            Set<String> seedUrls = confluence.parseUris(textContent);
            if (seedUrls == null || seedUrls.isEmpty()) {
                logger.info("No Confluence URLs detected in ticket text");
                return;
            }
            logger.info("Found {} Confluence URL(s), writing to input/confluence/ with depth={} attachments={} comments={}",
                    seedUrls.size(), confluenceDepth, confluenceAttachments, includeConfluenceComments);

            Path confluenceFolder = inputFolderPath.resolve("confluence");
            new ConfluencePageDownloader(confluence).downloadPages(
                    new ArrayList<>(seedUrls), confluenceFolder.toFile(), confluenceDepth, confluenceAttachments, includeConfluenceComments);
        } catch (Exception e) {
            logger.warn("writeConfluencePagesFile failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Downloads ticket attachments to the specified folder.
     *
     * @param attachments List of attachments to download
     * @param targetFolder Target folder to save attachments
     * @param trackerClient Tracker client for downloading files
     * @throws IOException if attachment download fails
     */
    public void downloadTicketAttachments(List<? extends IAttachment> attachments, Path targetFolder, TrackerClient<?> trackerClient) throws IOException {
        downloadAttachments(attachments, targetFolder, trackerClient);
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
        int successCount = 0;
        int failCount = 0;
        
        for (IAttachment attachment : attachments) {
            if (attachment == null) {
                logger.warn("⚠️ Skipping null attachment");
                failCount++;
                continue;
            }
            
            try {
                String fileName = attachment.getName();
                if (fileName == null || fileName.trim().isEmpty()) {
                    logger.warn("⚠️ Skipping attachment with empty filename");
                    failCount++;
                    continue;
                }
                
                // Ensure safe filename (remove path separators)
                fileName = fileName.replaceAll("[/\\\\]", "_");
                
                Path attachmentPath = targetFolder.resolve(fileName);
                
                // Download attachment using TrackerClient
                String attachmentUrl = attachment.getUrl();
                if (attachmentUrl != null && !attachmentUrl.trim().isEmpty()) {
                    logger.info("⬇️ Downloading: {} from {}", fileName, attachmentUrl);
                    File downloadedFile = trackerClient.convertUrlToFile(attachmentUrl);
                    if (downloadedFile != null && downloadedFile.exists()) {
                        Files.copy(downloadedFile.toPath(), attachmentPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        long size = Files.size(attachmentPath);
                        logger.info("✅ Downloaded attachment: {} ({} bytes) to {}", fileName, size, attachmentPath.toAbsolutePath());
                        successCount++;
                    } else {
                        logger.warn("❌ Failed to download attachment: {} (convertUrlToFile returned null or non-existent file)", fileName);
                        failCount++;
                    }
                } else {
                    logger.warn("❌ Attachment {} has no URL", fileName);
                    failCount++;
                }
            } catch (Exception e) {
                logger.error("❌ Failed to download attachment {}: {}", attachment.getName(), e.getMessage(), e);
                failCount++;
                // Continue with other attachments instead of failing completely
            }
        }
        
        logger.info("📊 Attachment download summary: {} succeeded, {} failed out of {} total", 
            successCount, failCount, attachments.size());
    }
    
    /**
     * Executes CLI commands and collects their responses.
     * 
     * @param cliCommands Array of CLI commands to execute
     * @param workingDirectory Working directory for command execution (optional)
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile) {
        return executeCliCommands(cliCommands, workingDirectory, envVariablesFile, null, false, null, null, null, null);
    }

    /**
     * Executes CLI commands and collects their responses.
     * Each output line is also published to {@code liveOutput} (if non-null) so that a
     * concurrent timer thread can read accumulated output between command lines.
     *
     * @param cliCommands      Array of CLI commands to execute
     * @param workingDirectory Working directory for command execution (optional)
     * @param envVariablesFile Path to environment file (null → resolve dmtools.env relative to workingDirectory)
     * @param liveOutput       Optional AtomicReference updated with accumulated output after each line
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput) {
        return executeCliCommands(cliCommands, workingDirectory, envVariablesFile, liveOutput, false, null, null, null, null);
    }

    /**
     * Executes CLI commands and collects their responses.
     * Each output line is also published to {@code liveOutput} (if non-null) so that a
     * concurrent timer thread can read accumulated output between command lines.
     *
     * @param cliCommands      Array of CLI commands to execute
     * @param workingDirectory Working directory for command execution (optional)
     * @param envVariablesFile Path to environment file (null → resolve dmtools.env relative to workingDirectory)
     * @param liveOutput       Optional AtomicReference updated with accumulated output after each line
     * @param allowAnyCommand  When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                         Use only for trusted job configs.
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput, boolean allowAnyCommand) {
        return executeCliCommands(cliCommands, workingDirectory, envVariablesFile, liveOutput, allowAnyCommand, null, null, null, null);
    }

    /**
     * Executes CLI commands and collects their responses.
     * Each output line is also published to {@code liveOutput} (if non-null) so that a
     * concurrent timer thread can read accumulated output between command lines.
     *
     * @param cliCommands            Array of CLI commands to execute
     * @param workingDirectory       Working directory for command execution (optional)
     * @param envVariablesFile       Path to environment file (null → resolve dmtools.env relative to workingDirectory)
     * @param liveOutput             Optional AtomicReference updated with accumulated output after each line
     * @param allowAnyCommand        When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                               Use only for trusted job configs.
     * @param excludedEnvVariables   Exact env variable names to exclude from the subprocess
     * @param excludedEnvRegexes     Regex patterns; matching env variable names are excluded
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput, boolean allowAnyCommand,
                                            String[] excludedEnvVariables, String[] excludedEnvRegexes) {
        return executeCliCommands(cliCommands, workingDirectory, envVariablesFile, liveOutput, allowAnyCommand,
                excludedEnvVariables, excludedEnvRegexes, null, null);
    }

    /**
     * Executes CLI commands and collects their responses.
     * Each output line is also published to {@code liveOutput} (if non-null) so that a
     * concurrent timer thread can read accumulated output between command lines.
     *
     * @param cliCommands            Array of CLI commands to execute
     * @param workingDirectory       Working directory for command execution (optional)
     * @param envVariablesFile       Path to environment file (null → resolve dmtools.env relative to workingDirectory)
     * @param liveOutput             Optional AtomicReference updated with accumulated output after each line
     * @param allowAnyCommand        When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                               Use only for trusted job configs.
     * @param excludedEnvVariables   Exact env variable names to exclude from the subprocess
     * @param excludedEnvRegexes     Regex patterns; matching env variable names are excluded
     * @param errorHandler           Optional consumer invoked when a CLI command fails
     * @param lineStopPredicate      Optional predicate invoked for each output line; returning {@code true} stops execution
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput, boolean allowAnyCommand,
                                            String[] excludedEnvVariables, String[] excludedEnvRegexes,
                                            Consumer<Exception> errorHandler, Predicate<String> lineStopPredicate) {
        return executeCliCommandsInternal(cliCommands, workingDirectory, envVariablesFile, liveOutput, allowAnyCommand,
                excludedEnvVariables, excludedEnvRegexes, errorHandler, lineStopPredicate).getCommandResponses();
    }

    /**
     * Internal outcome of a CLI command batch, tracking both the accumulated text
     * responses and a structured signal for the last fatal (non-zero exit code)
     * command failure encountered, if any. Used to populate {@link CliExecutionResult}
     * without changing the public {@code executeCliCommands} return type.
     */
    private static class CliCommandsOutcome {
        private final StringBuilder commandResponses;
        private final boolean hasFatalError;
        private final Integer lastExitCode;
        private final String lastErrorMessage;

        CliCommandsOutcome(StringBuilder commandResponses, boolean hasFatalError,
                           Integer lastExitCode, String lastErrorMessage) {
            this.commandResponses = commandResponses;
            this.hasFatalError = hasFatalError;
            this.lastExitCode = lastExitCode;
            this.lastErrorMessage = lastErrorMessage;
        }

        StringBuilder getCommandResponses() {
            return commandResponses;
        }

        boolean hasFatalError() {
            return hasFatalError;
        }

        Integer getLastExitCode() {
            return lastExitCode;
        }

        String getLastErrorMessage() {
            return lastErrorMessage;
        }
    }

    /**
     * Immutable snapshot of the last fatal CLI command failure encountered so far,
     * published live via an {@link AtomicReference} (analogous to {@code liveOutput})
     * so a concurrent {@code timerJSAction} can react to a fatal error before the CLI
     * command batch as a whole finishes — see {@code Teammate.runJobImpl}.
     */
    public static class LiveCliErrorState {
        public static final LiveCliErrorState NONE = new LiveCliErrorState(false, null, null);

        private final boolean hasFatalError;
        private final Integer exitCode;
        private final String errorMessage;

        public LiveCliErrorState(boolean hasFatalError, Integer exitCode, String errorMessage) {
            this.hasFatalError = hasFatalError;
            this.exitCode = exitCode;
            this.errorMessage = errorMessage;
        }

        public boolean hasFatalError() {
            return hasFatalError;
        }

        public Integer getExitCode() {
            return exitCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Executes CLI commands and collects their responses, additionally tracking a
     * structured signal for the last fatal (non-zero exit code) command failure.
     *
     * <p>Unlike the public {@code executeCliCommands} overloads, callers here get
     * access to the real subprocess exit code rather than only the free-text error
     * message embedded in {@code cliResponses} — see {@link CliCommandFailedException}.
     */
    private CliCommandsOutcome executeCliCommandsInternal(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput, boolean allowAnyCommand,
                                            String[] excludedEnvVariables, String[] excludedEnvRegexes,
                                            Consumer<Exception> errorHandler, Predicate<String> lineStopPredicate) {
        return executeCliCommandsInternal(cliCommands, workingDirectory, envVariablesFile, liveOutput, allowAnyCommand,
                excludedEnvVariables, excludedEnvRegexes, errorHandler, lineStopPredicate, null);
    }

    /**
     * Same as the overload above, additionally publishing a live snapshot of the last fatal
     * CLI failure to {@code liveErrorState} (if non-null) as soon as it is detected — mirroring
     * how {@code liveOutput} is updated after every line — so a concurrent {@code timerJSAction}
     * can observe a fatal error before the whole command batch finishes.
     */
    private CliCommandsOutcome executeCliCommandsInternal(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput, boolean allowAnyCommand,
                                            String[] excludedEnvVariables, String[] excludedEnvRegexes,
                                            Consumer<Exception> errorHandler, Predicate<String> lineStopPredicate,
                                            AtomicReference<LiveCliErrorState> liveErrorState) {
        StringBuilder cliResponses = new StringBuilder();
        boolean hasFatalError = false;
        Integer lastExitCode = null;
        String lastErrorMessage = null;

        if (cliCommands == null || cliCommands.length == 0) {
            logger.info("No CLI commands to execute");
            return new CliCommandsOutcome(cliResponses, false, null, null);
        }

        // Load environment variables from dmtools.env for CLI tools like cursor-agent
        if (envVariablesFile == null) {
            envVariablesFile = workingDirectory.resolve("dmtools.env").toString();
        }
        Map<String, String> envVars = CommandLineUtils.loadEnvironmentFromFile(envVariablesFile);
        if (!envVars.isEmpty()) {
            logger.info("Loaded {} environment variables from dmtools.env", envVars.size());
            // Log if CURSOR_API_KEY is available (without revealing the key)
            if (envVars.containsKey("CURSOR_API_KEY")) {
                logger.info("CURSOR_API_KEY found in environment (length: {})", envVars.get("CURSOR_API_KEY").length());
            }
        }

        // Merge per-job envVariables overrides (from JSON config) into the subprocess env.
        // These take priority over dmtools.env so that e.g. COPILOT_MODEL set in a JSON
        // agent config actually reaches run-agent.sh.
        Map<String, String> jobOverrides = PropertyReader.getOverrides();
        if (!jobOverrides.isEmpty()) {
            java.util.HashMap<String, String> merged = new java.util.HashMap<>(envVars);
            int skipped = 0;
            for (Map.Entry<String, String> entry : jobOverrides.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.isBlank() || value == null) {
                    logger.warn("Skipping invalid envVariables entry: key={}, keyBlank={}, valueNull={}",
                            key, key != null && key.isBlank(), value == null);
                    skipped++;
                    continue;
                }
                merged.put(key, value);
            }
            int mergedCount = jobOverrides.size() - skipped;
            logger.info("Merging {} per-job envVariables override(s) into subprocess environment ({} skipped due to null/blank key or null value)",
                    mergedCount, skipped);
            envVars = merged;
        }

        // Apply explicit exclusions before spawning subprocess
        envVars = filterEnvVariables(envVars, excludedEnvVariables, excludedEnvRegexes);

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
                // Build a per-line consumer that also updates liveOutput so timer JS can read partial output
                final StringBuilder commandOutput = new StringBuilder();
                java.util.function.Consumer<String> lineConsumer = liveOutput == null ? null : line -> {
                    commandOutput.append(line).append(System.lineSeparator());
                    liveOutput.set(cliResponses + commandOutput.toString());
                };
                String response;
                boolean hasEnvironmentExclusions = hasEnvironmentExclusions(
                        excludedEnvVariables, excludedEnvRegexes);
                if (hasEnvironmentExclusions) {
                    response = CommandLineUtils.runCommand(
                            command.trim(), workingDir, envVars, lineConsumer, !allowAnyCommand,
                            lineStopPredicate, -1, excludedEnvVariables, excludedEnvRegexes);
                } else if (lineStopPredicate == null) {
                    response = allowAnyCommand
                            ? CommandLineUtils.runCommand(command.trim(), workingDir, envVars, lineConsumer, false, null, -1)
                            : CommandLineUtils.runCommand(command.trim(), workingDir, envVars, lineConsumer, true, null, -1);
                } else {
                    response = allowAnyCommand
                            ? CommandLineUtils.runCommand(command.trim(), workingDir, envVars, lineConsumer, false, lineStopPredicate, -1)
                            : CommandLineUtils.runCommand(command.trim(), workingDir, envVars, lineConsumer, true, lineStopPredicate, -1);
                }

                if (response != null && !response.trim().isEmpty()) {
                    cliResponses.append("CLI Command: ").append(command).append("\n");
                    cliResponses.append("Response:\n").append(response).append("\n\n");
                    if (liveOutput != null) {
                        liveOutput.set(cliResponses.toString());
                    }
                    logger.info("CLI command completed successfully");
                } else {
                    logger.warn("CLI command returned empty response");
                }
            } catch (CliExecutionStoppedException e) {
                logger.warn("CLI execution stopped by line callback for command '{}': {}", command, e.getMessage());
                cliResponses.append("CLI Command: ").append(command).append("\n");
                cliResponses.append("Stopped: ").append(e.getMessage()).append("\n\n");
                if (liveOutput != null) {
                    liveOutput.set(cliResponses.toString());
                }
                throw e;
            } catch (Exception e) {
                String errorMsg = "Failed to execute CLI command '" + command + "': " + e.getMessage();

                // Check if this is a cursor-agent related error and provide helpful message
                if (command.contains("cursor-agent")) {
                    errorMsg += "\n\nNote: Cursor AI CLI may not be available on this platform.";
                    errorMsg += "\nCursor CLI is currently supported on macOS and Windows.";
                    errorMsg += "\nFor Linux environments, consider using alternative AI tools or running on supported platforms.";
                }

                logger.error(errorMsg, e);
                if (errorHandler != null) {
                    try {
                        errorHandler.accept(e);
                    } catch (Exception handlerException) {
                        logger.warn("errorHandler threw exception, ignoring: {}", handlerException.getMessage());
                    }
                }
                cliResponses.append("CLI Command: ").append(command).append("\n");
                cliResponses.append("Error: ").append(errorMsg).append("\n\n");
                if (liveOutput != null) {
                    liveOutput.set(cliResponses.toString());
                }

                // Track a structured fatal-error signal so downstream consumers
                // (e.g. timerJSAction / postJSAction retry logic) can distinguish a
                // real, non-retryable CLI failure from a transient interruption
                // without string-scanning the free-text response above.
                hasFatalError = true;
                lastErrorMessage = errorMsg;
                lastExitCode = (e instanceof CliCommandFailedException)
                        ? ((CliCommandFailedException) e).getExitCode()
                        : null;
                if (liveErrorState != null) {
                    liveErrorState.set(new LiveCliErrorState(true, lastExitCode, lastErrorMessage));
                }
            }
        }

        return new CliCommandsOutcome(cliResponses, hasFatalError, lastExitCode, lastErrorMessage);
    }

    private boolean hasEnvironmentExclusions(String[] excludedEnvVariables, String[] excludedEnvRegexes) {
        return hasNonBlankValue(excludedEnvVariables) || hasNonBlankValue(excludedEnvRegexes);
    }

    private boolean hasNonBlankValue(String[] values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Executes CLI commands in the specified working directory and processes output response.
     *
     * @param cliCommands      Array of CLI commands to execute
     * @param workingDirectory Working directory for command execution (optional)
     * @param envVariablesFile Path to environment file (null → auto-resolve)
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory, String envVariablesFile) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, null, 0, null, false, null, null, null, null);
    }

    /**
     * Executes CLI commands in the specified working directory and processes output response,
     * allowing arbitrary shell syntax when {@code allowAnyCommand} is {@code true}.
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile, boolean allowAnyCommand) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, null, 0, null, allowAnyCommand, null, null, null, null);
    }

    /**
     * Executes CLI commands with an optional background timer that fires a JS action periodically.
     *
     * <p>If {@code timerAction} is non-null and {@code timerIntervalSeconds > 0}, a single-daemon-thread
     * {@link ScheduledExecutorService} fires {@code timerAction.run()} every {@code timerIntervalSeconds}
     * seconds starting after the first interval. The action receives the accumulated CLI output so far
     * via the {@code Runnable} closure (see {@code Teammate.runJobImpl} for how this is wired).
     * Timer exceptions are caught and logged; they never interrupt CLI execution.
     * The executor is shut down (with a 5-second grace period) once all CLI commands finish.
     *
     * @param cliCommands          Array of CLI commands to execute
     * @param workingDirectory     Working directory for command execution (optional)
     * @param envVariablesFile     Path to environment file (null → auto-resolve)
     * @param timerAction          Optional runnable executed on the timer thread; receives live output via closure
     * @param timerIntervalSeconds Interval between timer firings in seconds; timer is disabled if &lt;= 0
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, timerAction, timerIntervalSeconds, null, false, null, null, null, null);
    }

    /**
     * Executes CLI commands with an optional background timer and shared live output reference.
     *
     * @param cliCommands          Array of CLI commands to execute
     * @param workingDirectory     Working directory for command execution (optional)
     * @param envVariablesFile     Path to environment file (null → auto-resolve)
     * @param timerAction          Optional runnable executed on the timer thread
     * @param timerIntervalSeconds Interval between timer firings in seconds; timer is disabled if &lt;= 0
     * @param liveCliOutput        Optional shared AtomicReference updated with accumulated CLI output;
     *                             if null, an internal reference is created when timerAction is non-null
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds,
                                                           AtomicReference<String> liveCliOutput) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, timerAction,
                timerIntervalSeconds, liveCliOutput, false, null, null, null, null);
    }

    /**
     * Executes CLI commands with an optional background timer, shared live output reference,
     * and optional shell-injection validation bypass.
     *
     * @param cliCommands          Array of CLI commands to execute
     * @param workingDirectory     Working directory for command execution (optional)
     * @param envVariablesFile     Path to environment file (null → auto-resolve)
     * @param timerAction          Optional runnable executed on the timer thread
     * @param timerIntervalSeconds Interval between timer firings in seconds; timer is disabled if &lt; 0
     * @param liveCliOutput        Optional shared AtomicReference updated with accumulated CLI output;
     *                             if null, an internal reference is created when timerAction is non-null
     * @param allowAnyCommand      When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                             Use only for trusted job configs.
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds,
                                                           AtomicReference<String> liveCliOutput,
                                                           boolean allowAnyCommand) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, timerAction,
                timerIntervalSeconds, liveCliOutput, allowAnyCommand, null, null, null, null);
    }

    /**
     * Executes CLI commands with an optional background timer, shared live output reference,
     * optional shell-injection validation bypass, and optional env variable exclusion.
     *
     * @param cliCommands            Array of CLI commands to execute
     * @param workingDirectory       Working directory for command execution (optional)
     * @param envVariablesFile       Path to environment file (null → auto-resolve)
     * @param timerAction            Optional runnable executed on the timer thread
     * @param timerIntervalSeconds   Interval between timer firings in seconds; timer is disabled if &lt;= 0
     * @param liveCliOutput          Optional shared AtomicReference updated with accumulated CLI output;
     *                               if null, an internal reference is created when timerAction is non-null
     * @param allowAnyCommand        When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                               Use only for trusted job configs.
     * @param excludedEnvVariables   Exact env variable names to exclude from the subprocess
     * @param excludedEnvRegexes     Regex patterns; matching env variable names are excluded
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds,
                                                           AtomicReference<String> liveCliOutput,
                                                           boolean allowAnyCommand,
                                                           String[] excludedEnvVariables,
                                                           String[] excludedEnvRegexes) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, timerAction,
                timerIntervalSeconds, liveCliOutput, allowAnyCommand, excludedEnvVariables, excludedEnvRegexes, null, null);
    }

    /**
     * Executes CLI commands with an optional background timer, shared live output reference,
     * optional shell-injection validation bypass, optional env variable exclusion,
     * optional error handler, and optional per-line stop predicate.
     *
     * @param cliCommands            Array of CLI commands to execute
     * @param workingDirectory       Working directory for command execution (optional)
     * @param envVariablesFile       Path to environment file (null → auto-resolve)
     * @param timerAction            Optional runnable executed on the timer thread
     * @param timerIntervalSeconds   Interval between timer firings in seconds; timer is disabled if &lt;= 0
     * @param liveCliOutput          Optional shared AtomicReference updated with accumulated CLI output;
     *                               if null, an internal reference is created when timerAction is non-null
     * @param allowAnyCommand        When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                               Use only for trusted job configs.
     * @param excludedEnvVariables   Exact env variable names to exclude from the subprocess
     * @param excludedEnvRegexes     Regex patterns; matching env variable names are excluded
     * @param errorHandler           Optional consumer invoked when a CLI command fails
     * @param lineStopPredicate      Optional predicate invoked for each output line; returning {@code true} stops execution
     * @return CliExecutionResult containing command responses and output response
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds,
                                                           AtomicReference<String> liveCliOutput,
                                                           boolean allowAnyCommand,
                                                           String[] excludedEnvVariables,
                                                           String[] excludedEnvRegexes,
                                                           Consumer<Exception> errorHandler,
                                                           Predicate<String> lineStopPredicate) {
        return executeCliCommandsWithResult(
                cliCommands, workingDirectory, envVariablesFile, timerAction, timerIntervalSeconds,
                liveCliOutput, allowAnyCommand, excludedEnvVariables, excludedEnvRegexes,
                errorHandler, lineStopPredicate, OutputFolderPreference.LEGACY_OUTPUT_FIRST);
    }

    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds,
                                                           AtomicReference<String> liveCliOutput,
                                                           boolean allowAnyCommand,
                                                           String[] excludedEnvVariables,
                                                           String[] excludedEnvRegexes,
                                                           Consumer<Exception> errorHandler,
                                                           Predicate<String> lineStopPredicate,
                                                           OutputFolderPreference outputFolderPreference) {
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, timerAction, timerIntervalSeconds,
                liveCliOutput, allowAnyCommand, excludedEnvVariables, excludedEnvRegexes, errorHandler, lineStopPredicate,
                outputFolderPreference, null);
    }

    /**
     * Same as the overload above, additionally publishing a live snapshot of the last fatal CLI
     * failure to {@code liveErrorState} (if non-null) as soon as it is detected — mirroring
     * {@code liveCliOutput} — so a concurrent {@code timerJSAction} can observe and react to a
     * fatal, non-retryable error (e.g. an invalid model ID rejected by the AI provider) before
     * the whole CLI command batch finishes, rather than only being able to string-scan the raw
     * output for error text.
     *
     * @param liveErrorState Optional shared AtomicReference updated with a {@link LiveCliErrorState}
     *                       snapshot as soon as a fatal (non-zero exit code) command failure is detected
     */
    public CliExecutionResult executeCliCommandsWithResult(String[] cliCommands, Path workingDirectory,
                                                           String envVariablesFile,
                                                           Runnable timerAction, int timerIntervalSeconds,
                                                           AtomicReference<String> liveCliOutput,
                                                           boolean allowAnyCommand,
                                                           String[] excludedEnvVariables,
                                                           String[] excludedEnvRegexes,
                                                           Consumer<Exception> errorHandler,
                                                           Predicate<String> lineStopPredicate,
                                                           OutputFolderPreference outputFolderPreference,
                                                           AtomicReference<LiveCliErrorState> liveErrorState) {
        AtomicReference<String> liveOutput = liveCliOutput != null ? liveCliOutput
                : (timerAction != null ? new AtomicReference<>("") : null);

        AtomicReference<CliExecutionResult> resultRef = new AtomicReference<>();
        runWithTimer(timerAction, timerIntervalSeconds, () -> {
            CliCommandsOutcome outcome = executeCliCommandsInternal(cliCommands, workingDirectory, envVariablesFile, liveOutput,
                    allowAnyCommand, excludedEnvVariables, excludedEnvRegexes, errorHandler, lineStopPredicate, liveErrorState);
            String outputResponse = processOutputResponse(workingDirectory, outputFolderPreference);
            resultRef.set(new CliExecutionResult(outcome.getCommandResponses(), outputResponse,
                    outcome.hasFatalError(), outcome.getLastExitCode(), outcome.getLastErrorMessage()));
        });
        return resultRef.get();
    }

    /**
     * Runs {@code work} with an optional periodic background timer, using the same
     * scheduling/shutdown/final-tick semantics as {@link #executeCliCommandsWithResult}.
     *
     * <p>Useful for wrapping any long-running phase with the same auto-commit/session-save
     * safety net used for the main CLI command phase — in particular a {@code postJSAction}
     * that itself loops over CLI commands (e.g. a feedback-loop quality-gate retry calling
     * {@code cli_execute_command} repeatedly from JavaScript). Without this, such a phase
     * would run with zero periodic auto-save/auto-commit protection, since the timer
     * previously only wrapped the main {@code cliCommands} array execution.
     *
     * <p>Each timer firing invokes {@code timerAction} on a dedicated daemon scheduler thread.
     * Since {@code timerAction} is expected to spin up its own independent JS execution
     * context (see {@code Teammate}'s {@code js(...)} helper), this is safe to run
     * concurrently with {@code work} even when {@code work} itself is executing JavaScript,
     * as GraalJS contexts created independently do not share thread-affinity locks.
     *
     * @param timerAction          Optional runnable executed on the timer thread; timer is disabled if null or timerIntervalSeconds &lt;= 0
     * @param timerIntervalSeconds Interval between timer firings in seconds
     * @param work                 The work to run on the calling thread while the timer fires in the background
     */
    public static void runWithTimer(Runnable timerAction, int timerIntervalSeconds, Runnable work) {
        ScheduledExecutorService scheduler = null;
        if (timerAction != null && timerIntervalSeconds > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "teammate-timer-js");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    timerAction.run();
                } catch (Exception e) {
                    logger.warn("timerJSAction threw exception (ignored, execution continues): {}", e.getMessage());
                }
            }, timerIntervalSeconds, timerIntervalSeconds, TimeUnit.SECONDS);
            logger.info("timerJSAction scheduler started (interval: {}s)", timerIntervalSeconds);
        }

        try {
            work.run();
        } finally {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                logger.info("timerJSAction scheduler stopped");
                // Fire one final tick so the complete output (commit/push, session save) is captured
                try {
                    logger.info("timerJSAction final tick: saving complete session output");
                    timerAction.run();
                } catch (Exception e) {
                    logger.warn("timerJSAction final tick failed (non-fatal): {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Processes a Teammate output response, preferring the legacy output/response.md location
     * and falling back to outputs/response.md.
     *
     * @return response file content if it exists, null otherwise
     */
    public String processOutputResponse() {
        return processOutputResponse(null, OutputFolderPreference.LEGACY_OUTPUT_FIRST);
    }

    /**
     * Processes a Teammate output response relative to the specified working directory,
     * preferring output/response.md and falling back to outputs/response.md.
     *
     * @param workingDirectory working directory containing output folders (null for current directory)
     * @return response file content if it exists, null otherwise
     */
    public String processOutputResponse(Path workingDirectory) {
        return processOutputResponse(workingDirectory, OutputFolderPreference.LEGACY_OUTPUT_FIRST);
    }

    public String processOutputResponse(Path workingDirectory, OutputFolderPreference preference) {
        Path outputsResponse = resolveOutputFile(workingDirectory, OUTPUT_FOLDER);
        Path legacyResponse = resolveOutputFile(workingDirectory, OUTPUT_FOLDER_LEGACY);
        Path primary = preference == OutputFolderPreference.OUTPUTS_FIRST ? outputsResponse : legacyResponse;
        Path fallback = preference == OutputFolderPreference.OUTPUTS_FIRST ? legacyResponse : outputsResponse;

        Path outputFilePath = primary;
        if (!Files.exists(primary)) {
            logger.info("No output response file found at: {}", primary.toAbsolutePath());
            if (!Files.exists(fallback)) {
                logger.info("No fallback output response file found at: {}", fallback.toAbsolutePath());
                return null;
            }
            logger.info("Found output response file at fallback location: {}", fallback.toAbsolutePath());
            outputFilePath = fallback;
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

    private Path resolveOutputFile(Path workingDirectory, String folder) {
        return workingDirectory != null
                ? workingDirectory.resolve(folder).resolve(RESPONSE_FILE_NAME)
                : Paths.get(folder, RESPONSE_FILE_NAME);
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
            IOUtils.deleteRecursively(inputFolderPath);
            logger.info("Cleaned up input folder: {}", inputFolderPath.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to cleanup input folder {}: {}", 
                       inputFolderPath.toAbsolutePath(), e.getMessage());
        }
    }
    
    /**
     * Filters environment variables by exact name and/or regex patterns.
     * Returns a new map with matching keys removed. If both filters are null/empty,
     * returns the original map reference unchanged.
     */
    public static Map<String, String> filterEnvVariables(Map<String, String> envVars,
                                                         String[] excludedEnvVariables,
                                                         String[] excludedEnvRegexes) {
        if (envVars == null || envVars.isEmpty()) {
            return envVars;
        }

        Set<String> exactExclusions = new HashSet<>();
        if (excludedEnvVariables != null) {
            for (String name : excludedEnvVariables) {
                if (name != null && !name.isBlank()) {
                    exactExclusions.add(name);
                }
            }
        }

        List<Pattern> regexPatterns = new ArrayList<>();
        if (excludedEnvRegexes != null) {
            for (String regex : excludedEnvRegexes) {
                if (regex != null && !regex.isBlank()) {
                    regexPatterns.add(Pattern.compile(regex));
                }
            }
        }

        if (exactExclusions.isEmpty() && regexPatterns.isEmpty()) {
            return envVars;
        }

        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            String key = entry.getKey();
            if (exactExclusions.contains(key)) {
                logger.debug("Excluding env variable {} (exact match)", key);
                continue;
            }
            boolean matchesRegex = false;
            for (Pattern pattern : regexPatterns) {
                if (pattern.matcher(key).matches()) {
                    logger.debug("Excluding env variable {} (regex match: {})", key, pattern.pattern());
                    matchesRegex = true;
                    break;
                }
            }
            if (!matchesRegex) {
                filtered.put(key, entry.getValue());
            }
        }
        logger.info("Filtered environment variables: {} excluded, {} retained", envVars.size() - filtered.size(), filtered.size());
        return filtered;
    }

    /**
     * Appends processed prompt to each CLI command via temporary file.
     * Creates a temporary file with prompt content and passes file path as parameter.
     * This approach is cross-platform compatible (Windows cmd.exe, POSIX shells, PowerShell).
     *
     * CLI scripts can read the prompt from file:
     * - POSIX: PROMPT=$(cat "$1")
     * - Windows cmd: set /p PROMPT=<"%~1"
     * - Windows PowerShell: $PROMPT = Get-Content $args[0]
     *
     * Or check if argument is a file and fallback to direct string (backward compatibility):
     * - if [ -f "$1" ]; then PROMPT=$(cat "$1"); else PROMPT="$1"; fi
     *
     * @param commands Original CLI commands array
     * @param prompt Processed prompt content to append
     * @return New array with prompt file path appended to each command
     */
    public static String[] appendPromptToCommands(String[] commands, String prompt) {
        if (commands == null || commands.length == 0) {
            return commands;
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            return commands;
        }

        try {
            // Create temporary file with prompt content
            // Use system temp directory to avoid conflicts with input/ folder
            File promptFile = File.createTempFile("dmtools_cli_prompt_", ".txt");
            promptFile.deleteOnExit();  // Auto-cleanup on JVM exit

            // Write prompt to file with UTF-8 encoding
            Files.write(promptFile.toPath(), prompt.getBytes(StandardCharsets.UTF_8));

            logger.info("Created temporary prompt file: {} ({} bytes)",
                       promptFile.getAbsolutePath(), prompt.length());

            // Append prompt file path as parameter to each command
            String[] modifiedCommands = new String[commands.length];
            for (int i = 0; i < commands.length; i++) {
                String command = commands[i];
                if (command != null && !command.trim().isEmpty()) {
                    // Pass file path as quoted parameter (works on all platforms)
                    modifiedCommands[i] = command + " \"" + promptFile.getAbsolutePath() + "\"";
                } else {
                    modifiedCommands[i] = command;
                }
            }

            return modifiedCommands;

        } catch (IOException e) {
            logger.error("Failed to create temporary prompt file: {}", e.getMessage());
            logger.warn("Falling back to original commands without prompt appended");
            // Fallback to original commands without prompt (safer than trying to escape)
            return commands;
        }
    }

    /**
     * Result container for CLI execution that includes both command responses and output response,
     * plus a structured signal for the last fatal (non-zero exit code) command failure encountered,
     * if any. This lets callers (e.g. {@code timerJSAction}/{@code postJSAction} retry logic)
     * distinguish a real, non-retryable CLI failure from a transient interruption without having
     * to string-scan the free-text {@code commandResponses}.
     */
    public static class CliExecutionResult {
        private final StringBuilder commandResponses;
        private final String outputResponse;
        private final boolean hasFatalError;
        private final Integer lastExitCode;
        private final String lastErrorMessage;

        public CliExecutionResult(StringBuilder commandResponses, String outputResponse) {
            this(commandResponses, outputResponse, false, null, null);
        }

        public CliExecutionResult(StringBuilder commandResponses, String outputResponse,
                                   boolean hasFatalError, Integer lastExitCode, String lastErrorMessage) {
            this.commandResponses = commandResponses;
            this.outputResponse = outputResponse;
            this.hasFatalError = hasFatalError;
            this.lastExitCode = lastExitCode;
            this.lastErrorMessage = lastErrorMessage;
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

        /**
         * @return {@code true} if the last CLI command in this batch failed with a non-zero
         * exit code (a fatal, non-retryable error), as opposed to producing no output due to
         * a transient interruption.
         */
        public boolean hasFatalError() {
            return hasFatalError;
        }

        /**
         * @return the exit code of the failing CLI command, or {@code null} if no command
         * failed with a known exit code (e.g. a non-{@link com.github.istin.dmtools.common.utils.CliCommandFailedException}
         * exception was thrown, or no command failed at all).
         */
        public Integer getLastExitCode() {
            return lastExitCode;
        }

        /**
         * @return a concise error message describing the last fatal CLI command failure,
         * or {@code null} if no command failed.
         */
        public String getLastErrorMessage() {
            return lastErrorMessage;
        }
    }
}
