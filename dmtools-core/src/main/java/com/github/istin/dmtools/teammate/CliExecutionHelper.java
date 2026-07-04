// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.common.utils.IOUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.ConfluencePageDownloader;
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
import java.util.regex.Pattern;

/**
 * Helper class for CLI command execution within Teammate jobs.
 * Handles context preparation, command execution, and output processing.
 */
public class CliExecutionHelper {
    
    private static final Logger logger = LogManager.getLogger(CliExecutionHelper.class);
    
    private static final String INPUT_FOLDER_PREFIX = "input";
    private static final String REQUEST_FILE_NAME = "request.md";
    private static final String OUTPUT_FOLDER = "output";  // Changed from "outputs" to "output"
    private static final String OUTPUT_FOLDER_LEGACY = "outputs";  // Backward compatibility
    private static final String COMMENTS_FILE_NAME = "comments.md";
    private static final String RESPONSE_FILE_NAME = "response.md";

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
        
        // Enrich work item with relations if it's an ADO work item
        // This is needed because ADO API doesn't include relations when using fields parameter
        if (trackerClient != null) {
            try {
                // Check if this is an AzureDevOpsClient using instanceof
                if (trackerClient instanceof com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient) {
                    com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient adoClient = 
                        (com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient) trackerClient;
                    com.github.istin.dmtools.microsoft.ado.model.WorkItem workItem = 
                        (com.github.istin.dmtools.microsoft.ado.model.WorkItem) ticket;
                    adoClient.enrichWorkItemWithRelations(workItem);
                    logger.info("🔄 Enriched ADO work item {} with relations for attachment detection", ticketKey);
                }
            } catch (Exception e) {
                logger.warn("⚠️ Could not enrich work item with relations: {}", e.getMessage());
            }
        }

        // Download all ticket attachments to the input folder (including videos)
        List<? extends IAttachment> attachments = ticket.getAttachments();
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
        writeConfluencePagesFile(textContent, inputFolderPath, confluence, 0, true);
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
     * @param textContent           Any text that may contain Confluence URLs (e.g. full ticket text)
     * @param inputFolderPath       The base input folder (e.g. {@code input/PROJ-123})
     * @param confluence            Confluence client; if {@code null} the method does nothing
     * @param confluenceDepth       How many levels of linked/child pages to follow (0 = only ticket links)
     * @param confluenceAttachments Whether to download attachments for each fetched page
     */
    public void writeConfluencePagesFile(String textContent, Path inputFolderPath, Confluence confluence,
                                         int confluenceDepth, boolean confluenceAttachments) {
        if (confluence == null || textContent == null || textContent.isBlank()) return;
        try {
            Set<String> seedUrls = confluence.parseUris(textContent);
            if (seedUrls == null || seedUrls.isEmpty()) {
                logger.info("No Confluence URLs detected in ticket text");
                return;
            }
            logger.info("Found {} Confluence URL(s), writing to input/confluence/ with depth={} attachments={}",
                    seedUrls.size(), confluenceDepth, confluenceAttachments);

            Path confluenceFolder = inputFolderPath.resolve("confluence");
            new ConfluencePageDownloader(confluence).downloadPages(
                    new ArrayList<>(seedUrls), confluenceFolder.toFile(), confluenceDepth, confluenceAttachments);
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
        return executeCliCommands(cliCommands, workingDirectory, envVariablesFile, null);
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
        return executeCliCommands(cliCommands, workingDirectory, envVariablesFile, liveOutput, false, null, null);
    }

    /**
     * Executes CLI commands and collects their responses.
     * Each output line is also published to {@code liveOutput} (if non-null) so that a
     * concurrent timer thread can read accumulated output between command lines.
     *
     * @param cliCommands          Array of CLI commands to execute
     * @param workingDirectory     Working directory for command execution (optional)
     * @param envVariablesFile     Path to environment file (null → resolve dmtools.env relative to workingDirectory)
     * @param liveOutput           Optional AtomicReference updated with accumulated output after each line
     * @param allowAnyCommand      When {@code true}, skips shell-injection validation so arbitrary shell syntax is allowed.
     *                             Use only for trusted job configs.
     * @param excludedEnvVariables Exact env variable names to exclude from the subprocess
     * @param excludedEnvRegexes   Regex patterns; matching env variable names are excluded
     * @return StringBuilder containing all command responses
     */
    public StringBuilder executeCliCommands(String[] cliCommands, Path workingDirectory, String envVariablesFile,
                                            AtomicReference<String> liveOutput, boolean allowAnyCommand,
                                            String[] excludedEnvVariables, String[] excludedEnvRegexes) {
        StringBuilder cliResponses = new StringBuilder();

        if (cliCommands == null || cliCommands.length == 0) {
            logger.info("No CLI commands to execute");
            return cliResponses;
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
                String response = CommandLineUtils.runCommand(command.trim(), workingDir, envVars, lineConsumer);
                
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
                if (liveOutput != null) {
                    liveOutput.set(cliResponses.toString());
                }
            }
        }
        
        return cliResponses;
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
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, null, 0);
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
        return executeCliCommandsWithResult(cliCommands, workingDirectory, envVariablesFile, timerAction, timerIntervalSeconds, null);
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
                timerIntervalSeconds, liveCliOutput, false, null, null);
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
        AtomicReference<String> liveOutput = liveCliOutput != null ? liveCliOutput
                : (timerAction != null ? new AtomicReference<>("") : null);

        AtomicReference<CliExecutionResult> resultRef = new AtomicReference<>();
        runWithTimer(timerAction, timerIntervalSeconds, () -> {
            StringBuilder cliResponses = executeCliCommands(cliCommands, workingDirectory, envVariablesFile, liveOutput,
                    allowAnyCommand, excludedEnvVariables, excludedEnvRegexes);
            String outputResponse = processOutputResponse(workingDirectory);
            resultRef.set(new CliExecutionResult(cliResponses, outputResponse));
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
     * Processes output response from CLI commands by checking for output/response.md file.
     * For backward compatibility, also checks outputs/response.md if output/response.md is not found.
     *
     * @return Content of output/response.md file if it exists, null otherwise
     */
    public String processOutputResponse() {
        return processOutputResponse(null);
    }

    /**
     * Processes output response from CLI commands by checking for output/response.md file
     * relative to the specified working directory.
     * For backward compatibility, also checks outputs/response.md if output/response.md is not found.
     *
     * @param workingDirectory Working directory to look for output/response.md file (null for current directory)
     * @return Content of output/response.md file if it exists, null otherwise
     */
    public String processOutputResponse(Path workingDirectory) {
        // Try new location: output/response.md
        Path outputFilePath;
        if (workingDirectory != null) {
            outputFilePath = workingDirectory.resolve(OUTPUT_FOLDER).resolve(RESPONSE_FILE_NAME);
        } else {
            outputFilePath = Paths.get(OUTPUT_FOLDER, RESPONSE_FILE_NAME);
        }

        if (!Files.exists(outputFilePath)) {
            logger.info("No output response file found at: {}", outputFilePath.toAbsolutePath());

            // Backward compatibility: Try legacy location: outputs/response.md
            Path legacyOutputFilePath;
            if (workingDirectory != null) {
                legacyOutputFilePath = workingDirectory.resolve(OUTPUT_FOLDER_LEGACY).resolve(RESPONSE_FILE_NAME);
            } else {
                legacyOutputFilePath = Paths.get(OUTPUT_FOLDER_LEGACY, RESPONSE_FILE_NAME);
            }

            if (Files.exists(legacyOutputFilePath)) {
                logger.info("Found output response file at legacy location: {}", legacyOutputFilePath.toAbsolutePath());
                outputFilePath = legacyOutputFilePath;
            } else {
                logger.info("No output response file found at legacy location: {}", legacyOutputFilePath.toAbsolutePath());
                return null;
            }
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
