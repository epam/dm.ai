// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.FigmaClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Builds a ticket-based CLI input folder ({@code input/{TICKET-KEY}/}) that can be consumed
 * by CLI agents and by {@code preCliJSAction} scripts.
 * <p>
 * The builder is intentionally generic: it is driven by {@link InputContextConfig} and can be
 * reused by both {@link Teammate} and {@link com.github.istin.dmtools.cliagent.CliAgent}.
 */
public class TicketInputContextBuilder {

    private static final Logger logger = LogManager.getLogger(TicketInputContextBuilder.class);

    private static final String SOURCE_CONFLUENCE = "confluence";
    private static final String SOURCE_FIGMA = "figma";

    private final InstructionProcessor instructionProcessor;
    private final AgentParamsFileWriter agentParamsFileWriter;

    public TicketInputContextBuilder(InstructionProcessor instructionProcessor) {
        this.instructionProcessor = instructionProcessor;
        this.agentParamsFileWriter = instructionProcessor != null
                ? new AgentParamsFileWriter(instructionProcessor)
                : null;
    }

    /**
     * Resolves a ticket from {@link com.github.istin.dmtools.cliagent.InputParams} and builds the input folder.
     */
    public Result build(com.github.istin.dmtools.cliagent.InputParams inputParams,
                        Path workingDirectory,
                        TrackerClient<?> trackerClient,
                        Confluence confluence,
                        FigmaClient figmaClient) throws Exception {
        ITicket ticket = resolveTicket(inputParams, trackerClient);
        return build(inputParams, ticket, workingDirectory, trackerClient, confluence, figmaClient, null);
    }

    /**
     * Builds the input folder for an already-resolved ticket.
     */
    public Result build(InputContextConfig config,
                        ITicket ticket,
                        Path workingDirectory,
                        TrackerClient<?> trackerClient,
                        Confluence confluence,
                        FigmaClient figmaClient,
                        RequestDecompositionAgent.Result agentParams) throws Exception {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("InputContextConfig cannot be null");
        }

        String ticketKey = ticket.getTicketKey();
        logger.info("Building input context for ticket: {}", ticketKey);

        // Prepare ticket context (comments + linked tickets)
        TicketContext ticketContext = new TicketContext(trackerClient, ticket);
        ticketContext.prepareContext(
                config.isIncludeComments(),
                config.isIncludeLinkedTickets(),
                config.isIgnoreClonedByRelationship());

        // Determine request.md content
        String textFieldsOnly = trackerClient != null
                ? trackerClient.getTextFieldsOnly(ticket)
                : "";
        String requestContent = buildRequestContent(config, ticketContext, textFieldsOnly, agentParams);

        // Determine which attachments to download
        List<? extends IAttachment> attachments = filterAttachments(ticket.getAttachments(), config);

        // Create folder, write request.md, download attachments
        CliExecutionHelper cliHelper = new CliExecutionHelper();
        Path inputFolderPath = cliHelper.createInputContext(
                ticket, requestContent, trackerClient, workingDirectory, attachments);

        // Write comments.md
        cliHelper.writeCommentsFile(inputFolderPath, ticketContext.getComments());

        // Write agent-param files for Teammate
        if (agentParams != null && config.isWriteAgentParamsToFiles() && agentParamsFileWriter != null) {
            agentParamsFileWriter.writeToInputFolder(inputFolderPath, agentParams);
        }

        // Smart source resolution
        if (config.isSmart() && !textFieldsOnly.isBlank()) {
            if (isSourceEnabled(config, SOURCE_CONFLUENCE)) {
                cliHelper.writeConfluencePagesFile(textFieldsOnly, inputFolderPath, confluence);
            }
            if (isSourceEnabled(config, SOURCE_FIGMA)) {
                writeFigmaFiles(textFieldsOnly, inputFolderPath, figmaClient);
            }
        }

        logger.info("Input context ready at: {}", inputFolderPath.toAbsolutePath());
        return new Result(inputFolderPath, ticket);
    }

    private ITicket resolveTicket(com.github.istin.dmtools.cliagent.InputParams inputParams,
                                  TrackerClient<?> trackerClient) throws Exception {
        if (inputParams == null) {
            throw new IllegalArgumentException("InputParams cannot be null");
        }
        if (trackerClient == null) {
            throw new IllegalStateException("TrackerClient is required when input is configured");
        }

        if (inputParams.getTicket() != null && !inputParams.getTicket().trim().isEmpty()) {
            logger.info("Resolving input ticket by key: {}", inputParams.getTicket());
            return trackerClient.performTicket(inputParams.getTicket(), trackerClient.getExtendedQueryFields());
        }

        if (inputParams.getJql() != null && !inputParams.getJql().trim().isEmpty()) {
            logger.info("Resolving input ticket by JQL: {}", inputParams.getJql());
            List<? extends ITicket> tickets = trackerClient.searchAndPerform(inputParams.getJql(), trackerClient.getExtendedQueryFields());
            if (tickets == null || tickets.isEmpty()) {
                throw new IllegalStateException("JQL returned no tickets: " + inputParams.getJql());
            }
            ITicket first = tickets.get(0);
            logger.info("Using first ticket from JQL result: {}", first.getTicketKey());
            return first;
        }

        throw new IllegalArgumentException("Input must specify either 'ticket' or 'jql'");
    }

    private String buildRequestContent(InputContextConfig config,
                                        TicketContext ticketContext,
                                        String textFieldsOnly,
                                        RequestDecompositionAgent.Result agentParams) throws IOException {
        if (agentParams != null && !config.isWriteAgentParamsToFiles()) {
            return agentParams.toString();
        }
        if (agentParams != null && config.isWriteAgentParamsToFiles()) {
            return textFieldsOnly != null ? textFieldsOnly : "";
        }
        // CliAgent path: full ticket context as request.md
        return ticketContext.toText();
    }

    private List<? extends IAttachment> filterAttachments(List<? extends IAttachment> attachments, InputContextConfig config) {
        if (attachments == null || attachments.isEmpty()) {
            return attachments;
        }
        if (config.isSkipAllAttachments()) {
            logger.info("⏭️ Skipping all attachments (skipAllAttachments=true)");
            return Collections.emptyList();
        }
        if (!config.isSkipVideoAttachments()) {
            return attachments;
        }
        List<IAttachment> filtered = new ArrayList<>();
        for (IAttachment attachment : attachments) {
            if (attachment != null && CliExecutionHelper.isVideoFile(attachment.getName())) {
                logger.info("⏭️ Skipping video attachment (skipVideoAttachments=true): {}", attachment.getName());
            } else {
                filtered.add(attachment);
            }
        }
        return filtered;
    }

    private boolean isSourceEnabled(InputContextConfig config, String source) {
        if (config.getSources() == null || config.getSources().length == 0) {
            return true;
        }
        for (String configured : config.getSources()) {
            if (configured != null && configured.equalsIgnoreCase(source)) {
                return true;
            }
        }
        return false;
    }

    private void writeFigmaFiles(String textContent, Path inputFolderPath, FigmaClient figmaClient) {
        if (figmaClient == null || textContent == null || textContent.isBlank()) {
            return;
        }
        try {
            Set<String> urls = figmaClient.parseUris(textContent);
            if (urls == null || urls.isEmpty()) {
                logger.info("No Figma URLs detected in ticket text");
                return;
            }
            logger.info("Found {} Figma URL(s), writing to input/figma/...", urls.size());

            Path figmaFolder = inputFolderPath.resolve("figma");
            Files.createDirectories(figmaFolder);

            int written = 0;
            for (String url : urls) {
                try {
                    Object obj = figmaClient.uriToObject(url);
                    if (obj instanceof File) {
                        File file = (File) obj;
                        String name = deriveSafeName(url, file.getName(), written);
                        Path target = figmaFolder.resolve(name);
                        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        logger.info("Wrote Figma file → {} ({} bytes)", target, Files.size(target));
                        written++;
                    } else {
                        logger.warn("Figma URL {} did not resolve to a file, skipping", url);
                    }
                } catch (Exception e) {
                    logger.warn("Could not fetch Figma URL {} (skipping): {}", url, e.getMessage());
                }
            }
            logger.info("Wrote {}/{} Figma files to input/figma/", written, urls.size());
        } catch (Exception e) {
            logger.warn("writeFigmaFiles failed (non-fatal): {}", e.getMessage());
        }
    }

    private String deriveSafeName(String url, String fallbackName, int index) {
        String name = fallbackName != null && !fallbackName.isBlank() ? fallbackName : "figma_" + index;
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (!name.toLowerCase().endsWith(".png") && !name.toLowerCase().endsWith(".jpg")
                && !name.toLowerCase().endsWith(".jpeg") && !name.toLowerCase().endsWith(".svg")) {
            name = name + ".png";
        }
        return name;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Result {
        private final Path path;
        private final ITicket ticket;
    }
}
