// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.JavaScriptExecutor;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.job.TrackerParams;
import com.github.istin.dmtools.teammate.AgentParamsFileWriter;
import com.github.istin.dmtools.teammate.CliCommandBuilder;
import com.github.istin.dmtools.teammate.CliExecutionHelper;
import com.github.istin.dmtools.teammate.InstructionProcessor;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight CLI-agent orchestrator.
 * <p>
 * {@code CliAgent} takes the CLI-execution parts of {@code Teammate} and removes the
 * tracker-ticket plumbing. It is designed for running cursor-agent / claude / copilot-style
 * CLI tools with aggregated prompts and optional setup/cache/reset hooks, without needing
 * an {@code inputJql} or a ticket system.
 * <p>
 * Execution lifecycle:
 * <pre>
 *   setup → preJSAction → preCliJSAction → cliCommands → postJSAction → cache → reset
 * </pre>
 */
public class CliAgent extends AbstractJob<CliAgentParams, List<ResultItem>> {

    private static final Logger logger = LogManager.getLogger(CliAgent.class);

    @Inject
    @Getter
    AI ai;

    @Inject
    Confluence confluence;

    @Inject
    ApplicationConfiguration configuration;

    private InstructionProcessor instructionProcessor;
    private AgentParamsFileWriter agentParamsFileWriter;

    /**
     * Server-managed Dagger component. Reuses the same integrations module as Teammate.
     */
    @Singleton
    @dagger.Component(modules = {ServerManagedIntegrationsModule.class})
    public interface ServerManagedCliAgentComponent {
        void inject(CliAgent agent);
    }

    @Override
    protected void initializeStandalone() {
        logger.info("Initializing CliAgent in STANDALONE mode");
        this.instructionProcessor = new InstructionProcessor(confluence);
        this.agentParamsFileWriter = new AgentParamsFileWriter(instructionProcessor);
        logger.info("CliAgent standalone initialization completed");
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        logger.info("Initializing CliAgent in SERVER_MANAGED mode");
        ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(resolvedIntegrations);
        ServerManagedCliAgentComponent component = DaggerCliAgent_ServerManagedCliAgentComponent.builder()
                .serverManagedIntegrationsModule(module)
                .build();
        component.inject(this);
        this.instructionProcessor = new InstructionProcessor(confluence);
        this.agentParamsFileWriter = new AgentParamsFileWriter(instructionProcessor);
        logger.info("CliAgent server-managed initialization completed");
    }

    @Override
    protected List<ResultItem> runJobImpl(CliAgentParams params) throws Exception {
        if (params.getCliCommands() == null || params.getCliCommands().length == 0) {
            logger.info("No cliCommands provided - nothing to do");
            return Collections.emptyList();
        }

        String contextId = getContextId(params);
        Path workingDirectory = resolveWorkingDirectory(params);
        Path inputContextPath = null;
        CliExecutionHelper cliHelper = new CliExecutionHelper();
        CliExecutionHelper.CliExecutionResult cliResult = null;

        try {
            // 1. Setup hook
            executeScriptHook(params.getSetup(), "setup", params, workingDirectory, null);

            // 2. Pre-JS action
            executeJsAction(params.getPreJSAction(), "preJSAction", params, null, null);

            // 3. Build input context and pre-CLI JS action
            inputContextPath = createInputContext(params, workingDirectory);

            // 4. Pre-CLI JS action
            executeJsAction(params.getPreCliJSAction(), "preCliJSAction", params, null,
                    inputContextPath != null ? inputContextPath.toAbsolutePath().toString() : null);

            // 5. Build and execute CLI commands with aggregated prompt
            CliCommandBuilder commandBuilder = new CliCommandBuilder(instructionProcessor, configuration);
            String[] finalCommands = commandBuilder.buildCommands(
                    params.getCliCommands(),
                    params.getCliPrompt(),
                    params.getCliPrompts(),
                    params.getCliPromptsByTracker());

            AtomicReference<String> liveOutput = new AtomicReference<>("");
            cliResult = cliHelper.executeCliCommandsWithResult(
                    finalCommands,
                    workingDirectory,
                    null,
                    null,
                    0,
                    liveOutput,
                    true); // allow arbitrary shell syntax

            // 6. Post-JS action
            String response = extractResponse(cliResult, params);
            executeJsAction(params.getPostJSAction(), "postJSAction", params, response,
                    inputContextPath != null ? inputContextPath.toAbsolutePath().toString() : null);

            // 7. Cache hook
            executeScriptHook(params.getCache(), "cache", params, workingDirectory, response);

            return Collections.singletonList(new ResultItem(contextId, response));

        } catch (Exception e) {
            logger.error("CliAgent execution failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            // 8. Reset hook (always run, even on failure)
            try {
                executeScriptHook(params.getReset(), "reset", params, workingDirectory,
                        cliResult != null ? extractResponse(cliResult, params) : null);
            } catch (Exception resetException) {
                logger.warn("Reset hook failed: {}", resetException.getMessage(), resetException);
            }

            // Clean up input context
            if (inputContextPath != null && params.isCleanupInputFolder()) {
                try {
                    cliHelper.cleanupInputContext(inputContextPath);
                    logger.info("Cleaned up input folder: {}", inputContextPath.toAbsolutePath());
                } catch (Exception cleanupException) {
                    logger.warn("Failed to clean up input folder: {}", cleanupException.getMessage());
                }
            }
        }
    }

    private String getContextId(CliAgentParams params) {
        if (params.getMetadata() != null && params.getMetadata().getContextId() != null) {
            return params.getMetadata().getContextId();
        }
        return "cli-agent";
    }

    private Path resolveWorkingDirectory(CliAgentParams params) {
        if (params.getWorkingDirectory() != null && !params.getWorkingDirectory().trim().isEmpty()) {
            Path path = Paths.get(params.getWorkingDirectory());
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path;
            }
            logger.warn("Configured workingDirectory does not exist or is not a directory: {}", params.getWorkingDirectory());
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    private Path createInputContext(CliAgentParams params, Path workingDirectory) throws IOException {
        Path inputContextPath = Paths.get("input", getContextId(params));
        Files.createDirectories(inputContextPath);
        return inputContextPath;
    }

    private void executeJsAction(String action, String actionName, CliAgentParams params, String response,
                                  String inputFolderPath) {
        if (action == null || action.trim().isEmpty()) {
            return;
        }
        try {
            logger.info("Executing {}: {}", actionName, action);
            JavaScriptExecutor executor = js(action)
                    .mcp(null, ai, confluence, null)
                    .withJobContext(params, null, response);
            if (inputFolderPath != null) {
                executor.with("inputFolderPath", inputFolderPath);
            }
            if (params.getCustomParams() != null && !params.getCustomParams().isEmpty()) {
                executor.with("customParams", params.getCustomParams());
            }
            executor.execute();
        } catch (Exception e) {
            logger.warn("{} failed, continuing: {}", actionName, e.getMessage(), e);
        }
    }

    private void executeScriptHook(String script, String hookName, CliAgentParams params, Path workingDirectory,
                                    String response) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        logger.info("Executing {} hook: {}", hookName, script);
        try {
            if (script.endsWith(".js")) {
                JavaScriptExecutor executor = js(script)
                        .mcp(null, ai, confluence, null)
                        .withJobContext(params, null, response)
                        .with("workingDirectory", workingDirectory.toAbsolutePath().toString());
                if (params.getCustomParams() != null && !params.getCustomParams().isEmpty()) {
                    executor.with("customParams", params.getCustomParams());
                }
                executor.execute();
            } else {
                CommandLineUtils.runCommand(script, workingDirectory.toFile(), PropertyReader.getOverrides(), null, false);
            }
        } catch (Exception e) {
            logger.warn("{} hook failed, continuing: {}", hookName, e.getMessage(), e);
        }
    }

    private String extractResponse(CliExecutionHelper.CliExecutionResult cliResult, CliAgentParams params) {
        if (cliResult == null) {
            return "No CLI result available.";
        }
        if (params.isRequireCliOutputFile() && !cliResult.hasOutputResponse()) {
            return "CLI command executed but did not produce output file:\n" + cliResult.getCommandResponses();
        }
        if (cliResult.hasOutputResponse()) {
            return cliResult.getOutputResponse();
        }
        return cliResult.getCommandResponses().toString();
    }
}
