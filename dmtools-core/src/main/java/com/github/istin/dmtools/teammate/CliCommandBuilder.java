// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds final CLI commands by aggregating {@code cliPrompt} and {@code cliPrompts}
 * through {@link InstructionProcessor} and appending the combined prompt to each command.
 * <p>
 * Extracted from {@link Teammate} so that {@code CliAgent} and other jobs can reuse the
 * same prompt-aggregation logic without duplicating it.
 */
public class CliCommandBuilder {

    private static final Logger logger = LogManager.getLogger(CliCommandBuilder.class);

    private final InstructionProcessor instructionProcessor;
    private final ApplicationConfiguration configuration;

    public CliCommandBuilder(InstructionProcessor instructionProcessor, ApplicationConfiguration configuration) {
        this.instructionProcessor = instructionProcessor;
        this.configuration = configuration;
    }

    /**
     * Resolves the effective CLI prompts by merging base {@code cliPrompts} with tracker-specific
     * prompts from {@code cliPromptsByTracker} when a matching tracker type is configured.
     */
    public static String[] resolveCliPrompts(String[] baseCliPrompts,
                                              Map<String, String[]> cliPromptsByTracker,
                                              String trackerType) {
        String effectiveTracker = trackerType;
        if (effectiveTracker == null || effectiveTracker.isBlank()) {
            effectiveTracker = "ado";
        }
        if (cliPromptsByTracker == null || !cliPromptsByTracker.containsKey(effectiveTracker)) {
            return baseCliPrompts;
        }

        String[] trackerPrompts = cliPromptsByTracker.get(effectiveTracker);
        if (trackerPrompts == null || trackerPrompts.length == 0) {
            return baseCliPrompts;
        }

        List<String> merged = new ArrayList<>();
        if (baseCliPrompts != null) {
            for (String prompt : baseCliPrompts) {
                merged.add(prompt);
            }
        }
        for (String prompt : trackerPrompts) {
            merged.add(prompt);
        }
        return merged.toArray(new String[0]);
    }

    /**
     * Builds the final CLI commands for execution.
     *
     * @param cliCommands       base CLI commands from the job config
     * @param cliPrompt         single base CLI prompt (may be null)
     * @param cliPrompts        array of CLI prompts (may be null)
     * @param cliPromptsByTracker tracker-specific prompts (may be null)
     * @return commands with aggregated prompt appended, or original commands if no prompt provided
     */
    public String[] buildCommands(String[] cliCommands, String cliPrompt, String[] cliPrompts,
                                   Map<String, String[]> cliPromptsByTracker) throws IOException {
        if (cliCommands == null || cliCommands.length == 0) {
            return cliCommands;
        }

        String trackerType = configuration != null ? configuration.getDefaultTracker() : null;
        String[] mergedCliPrompts = resolveCliPrompts(cliPrompts, cliPromptsByTracker, trackerType);
        if (mergedCliPrompts != cliPrompts) {
            logger.info("Merged tracker-specific cliPrompts ({} total prompts)", mergedCliPrompts.length);
        }

        String processedPrompt = instructionProcessor.buildCombinedPrompt(cliPrompt, mergedCliPrompts);
        if (processedPrompt == null || processedPrompt.trim().isEmpty()) {
            logger.info("No CLI prompt provided, running commands as-is");
            return cliCommands;
        }

        logger.info("Combined CLI prompt ready ({} chars)", processedPrompt.length());
        String[] finalCommands = CliExecutionHelper.appendPromptToCommands(cliCommands, processedPrompt);
        logger.info("Appended prompt to {} CLI commands", finalCommands.length);
        return finalCommands;
    }
}
