// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.ai.agent.SourceImpactAssessmentAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.di.*;
import com.github.istin.dmtools.expert.ExpertParams;
import com.github.istin.dmtools.index.mermaid.tool.MermaidIndexTools;
import com.github.istin.dmtools.job.*;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.search.CodebaseSearchOrchestrator;
import com.github.istin.dmtools.search.ConfluenceSearchOrchestrator;
import com.github.istin.dmtools.search.TrackerSearchOrchestrator;
import com.github.istin.dmtools.common.utils.IOUtils;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dagger.Component;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class Teammate extends AbstractJob<Teammate.TeammateParams, List<ResultItem>> {

    private static final Logger logger = LogManager.getLogger(Teammate.class);

    @Getter
    @Setter
    public static class TeammateParams extends JobTrackerParams<RequestDecompositionAgent.Result> implements InputContextConfig {

        public static final String SYSTEM_REQUEST_COMMENT_ALIAS = "systemRequestCommentAlias";

        @SerializedName("hooksAsContext")
        private String[] hooksAsContext;

        @SerializedName("cliCommands")
        private String[] cliCommands;

        @SerializedName("cliPrompt")
        private String cliPrompt;

        @SerializedName("cliPrompts")
        private String[] cliPrompts;

        @SerializedName("cliPromptsByTracker")
        private Map<String, String[]> cliPromptsByTracker;

        @SerializedName("skipAIProcessing")
        private boolean skipAIProcessing = false;

        @SerializedName("requireCliOutputFile")
        private boolean requireCliOutputFile = true;  // Default: true (strict mode by default for safety)

        @SerializedName("cleanupInputFolder")
        private boolean cleanupInputFolder = true;  // Default: true (cleanup input/ folder after execution)

        @SerializedName("indexes")
        private IndexConfig[] indexes;

        @SerializedName(SYSTEM_REQUEST_COMMENT_ALIAS)
        private String systemRequestCommentAlias;

        @SerializedName("preCliJSAction")
        private String preCliJSAction;

        @SerializedName("skipVideoAttachments")
        private boolean skipVideoAttachments = false;

        @SerializedName("skipAllAttachments")
        private boolean skipAllAttachments = false;

        @SerializedName("additionalInstructions")
        private String[] additionalInstructions;

        @SerializedName("writeAgentParamsToFiles")
        private boolean writeAgentParamsToFiles = true;

        @SerializedName("ignoreClonedByRelationship")
        private boolean ignoreClonedByRelationship = true;

        @SerializedName("autoConvertionToMarkdown")
        private boolean autoConvertionToMarkdown = true;

        @SerializedName("timerJSAction")
        private String timerJSAction;

        @SerializedName("timerIntervalSeconds")
        private int timerIntervalSeconds = 60;

        @SerializedName("confluenceDepth")
        private int confluenceDepth = 1;

        @SerializedName("confluenceAttachments")
        private boolean confluenceAttachments = true;

        @SerializedName("includeParentConfluence")
        private boolean includeParentConfluence = true;

        @SerializedName("excludedEnvVariables")
        private String[] excludedEnvVariables;

        @SerializedName("excludedEnvRegexes")
        private String[] excludedEnvRegexes;

        // ----- InputContextConfig implementation (preserves existing Teammate behavior) -----

        @Override
        public boolean isSmart() {
            return true;
        }

        @Override
        public String[] getSources() {
            return new String[] { "confluence" };
        }

        @Override
        public int getDepth() {
            return 0;
        }

        @Override
        public boolean isIncludeComments() {
            return true;
        }

        @Override
        public boolean isIncludeAttachments() {
            return true;
        }

        @Override
        public boolean isIncludeLinkedTickets() {
            return false;
        }

        @Override
        public boolean isSkipVideoAttachments() {
            return skipVideoAttachments;
        }

        @Override
        public boolean isSkipAllAttachments() {
            return skipAllAttachments;
        }

        @Override
        public boolean isIgnoreClonedByRelationship() {
            return ignoreClonedByRelationship;
        }

        @Override
        public boolean isWriteAgentParamsToFiles() {
            return writeAgentParamsToFiles;
        }

    }

    /**
     * Configuration for index tool execution.
     */
    @Getter
    @Setter
    public static class IndexConfig {
        @SerializedName("integration")
        private String integration;

        @SerializedName("storagePath")
        private String storagePath;
    }

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Inject
    Confluence confluence;

    @Inject
    @Getter
    AI ai;

    @Inject
    IPromptTemplateReader promptTemplateReader;

    @Inject
    List<SourceCode> sourceCodes;

    @Inject
    SourceImpactAssessmentAgent sourceImpactAssessmentAgent;

    @Inject
    RequestDecompositionAgent requestDecompositionAgent;

    @Inject
    GenericRequestAgent genericRequestAgent;

    @Inject
    ApplicationConfiguration configuration;

    List<CodebaseSearchOrchestrator> listOfCodebaseSearchOrchestrator = new ArrayList<>();

    @Inject
    ConfluenceSearchOrchestrator confluenceSearchOrchestrator; // Temporarily disabled

    @Inject
    TrackerSearchOrchestrator trackerSearchOrchestrator;

    @Inject
    ContextOrchestrator contextOrchestrator;

    @Inject
    UriToObjectFactory uriToObjectFactory;

    @Inject
    MermaidIndexTools mermaidIndexTools;

    // JavaScript bridge is now inherited from AbstractJob
    
    InstructionProcessor instructionProcessor;
    AgentParamsFileWriter agentParamsFileWriter;

    private static TeammateComponent teammateComponent;

    /**
     * Server-managed Dagger component that uses pre-resolved integrations
     * Includes ServerManagedIntegrationsModule for integrations and AIAgentsModule for agents
     */
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class, AIAgentsModule.class, MermaidIndexModule.class})
    public interface ServerManagedExpertComponent {
        void inject(Teammate expert);
    }

    /**
     * Creates a new Expert instance with the default configuration
     */
    public Teammate() {
        this(null);
    }

    /**
     * Creates a new Expert instance with the provided configuration
     *
     * @param configuration The application configuration to use
     */
    public Teammate(ApplicationConfiguration configuration) {
        // Don't initialize here - will be done in initializeForMode based on execution mode
    }

    @Override
    protected void initializeStandalone() {
        logger.info("Initializing Teammate in STANDALONE mode using TeammateComponent with BasicGeminiAI");
        
        // Use existing Dagger component for standalone mode
        if (teammateComponent == null) {
            logger.info("Creating new DaggerTeammateComponent for standalone mode");
            teammateComponent = DaggerTeammateComponent.create();
        }
        
        logger.info("Injecting dependencies using TeammateComponent");
        teammateComponent.inject(this);
        
        // Initialize instruction processor after dependencies are injected
        this.instructionProcessor = new InstructionProcessor(confluence);
        this.agentParamsFileWriter = new AgentParamsFileWriter(this.instructionProcessor);
        
        logger.info("Teammate standalone initialization completed - AI type: {}", 
                   (ai != null ? ai.getClass().getSimpleName() : "null"));
        
        // TeamAssistantAgent is now automatically injected by Dagger
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        logger.info("Initializing Teammate in SERVER_MANAGED mode using ServerManagedIntegrationsModule");
        logger.info("Resolved integrations: {}", 
                   (resolvedIntegrations != null ? resolvedIntegrations.length() + " integrations" : "null"));
        
        // Create dynamic component with pre-resolved integrations
        try {
            logger.info("Creating ServerManagedIntegrationsModule with resolved credentials");
            ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(resolvedIntegrations);
            
            logger.info("Building ServerManagedExpertComponent for Teammate");
            ServerManagedExpertComponent component = DaggerTeammate_ServerManagedExpertComponent.builder()
                    .serverManagedIntegrationsModule(module)
                    .build();
            
            logger.info("Injecting dependencies using ServerManagedExpertComponent");
            component.inject(this);
            
            // Initialize instruction processor after dependencies are injected
            this.instructionProcessor = new InstructionProcessor(confluence);
            this.agentParamsFileWriter = new AgentParamsFileWriter(this.instructionProcessor);
            
            logger.info("Teammate server-managed initialization completed - AI type: {}", 
                       (ai != null ? ai.getClass().getSimpleName() : "null"));
            
            // TeamAssistantAgent is now automatically injected by Dagger with server-managed dependencies
        } catch (Exception e) {
            logger.error("Failed to initialize Teammate in server-managed mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Teammate in server-managed mode", e);
        }
    }

    @Override
    protected List<ResultItem> runJobImpl(TeammateParams expertParams) throws Exception {
        // Validate TrackerClient availability
        String inputJQL = expertParams.getInputJql();
        boolean hasJqlQuery = inputJQL != null && !inputJQL.trim().isEmpty();

        if (trackerClient == null) {
            if (hasJqlQuery) {
                // TrackerClient is required when inputJql is provided
                throw new IllegalStateException(
                    "TrackerClient is not configured, but inputJql is provided. " +
                    "Please configure Jira (JIRA_BASE_PATH + JIRA_EMAIL + JIRA_API_TOKEN) or " +
                    "ADO (ADO_ORGANIZATION + ADO_PROJECT + ADO_PAT_TOKEN) or " +
                    "Rally (RALLY_PATH + RALLY_TOKEN). " +
                    "Alternatively, remove inputJql parameter if tracker integration is not needed."
                );
            } else {
                // No tracker and no JQL query - return empty results
                // (non-tracker execution path - useful for direct AI processing without tickets)
                logger.info("No TrackerClient configured and no inputJql provided - skipping ticket processing");
                return new ArrayList<>();
            }
        }

        ExpertParams.OutputType outputType = expertParams.getOutputType();
        String initiator = expertParams.getInitiator();
        String fieldName = expertParams.getFieldName();
        String systemRequestCommentAlias = expertParams.getSystemRequestCommentAlias();

        // Use injected UriToObjectFactory to create URI processing sources
        List<? extends UriToObject> uriProcessingSources;
        try {
            uriProcessingSources = uriToObjectFactory.createUriProcessingSources();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create URI processing sources", e);
        }

        RequestDecompositionAgent.Result rawInputParams = expertParams.getAgentParams();
        final RequestDecompositionAgent.Result inputParams = rawInputParams != null
                ? rawInputParams
                : new RequestDecompositionAgent.Result("", "", new String[0], new String[0], new String[0], "", "", "");

        // Snapshot original params BEFORE InstructionProcessor resolves URLs/paths to content.
        // Used later by AgentParamsFileWriter when writeAgentParamsToFiles=true.
        final RequestDecompositionAgent.Result originalParams;
        if (expertParams.isWriteAgentParamsToFiles()) {
            Gson snapshotGson = new Gson();
            originalParams = snapshotGson.fromJson(snapshotGson.toJson(inputParams), RequestDecompositionAgent.Result.class);
        } else {
            originalParams = null;
        }

        instructionProcessor.setAutoConvertionToMarkdown(expertParams.isAutoConvertionToMarkdown());
        String[] aiRoleArray = instructionProcessor.extractIfNeeded(inputParams.getAiRole());
        inputParams.setAiRole(aiRoleArray.length > 0 ? aiRoleArray[0] : "");
        String[] resolvedInstructions = instructionProcessor.extractIfNeeded(inputParams.getInstructions());
        String[] resolvedAdditional = instructionProcessor.extractIfNeeded(expertParams.getAdditionalInstructions());
        if (resolvedAdditional != null && resolvedAdditional.length > 0) {
            String[] combined = new String[resolvedInstructions.length + resolvedAdditional.length];
            System.arraycopy(resolvedInstructions, 0, combined, 0, resolvedInstructions.length);
            System.arraycopy(resolvedAdditional, 0, combined, resolvedInstructions.length, resolvedAdditional.length);
            inputParams.setInstructions(combined);
        } else {
            inputParams.setInstructions(resolvedInstructions);
        }
        String[] formattingRulesArray = instructionProcessor.extractIfNeeded(inputParams.getFormattingRules());
        inputParams.setFormattingRules(formattingRulesArray.length > 0 ? formattingRulesArray[0] : "");
        String[] fewShotsArray = instructionProcessor.extractIfNeeded(inputParams.getFewShots());
        inputParams.setFewShots(fewShotsArray.length > 0 ? fewShotsArray[0] : "");
        inputParams.setQuestions(instructionProcessor.extractIfNeeded(inputParams.getQuestions()));
        inputParams.setTasks(instructionProcessor.extractIfNeeded(inputParams.getTasks()));

        contextOrchestrator.processUrisInContent(inputParams.getKnownInfo(), uriProcessingSources, 2);
        String processedKnownInfo = contextOrchestrator.summarize().toString();
        inputParams.setKnownInfo(processedKnownInfo);
        contextOrchestrator.clear();

        List<ResultItem> results = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            long overallStart = System.currentTimeMillis();
            logger.info("Processing ticket: {}", ticket.getKey());

            // Post "processing started" comment so CI run is traceable from the ticket immediately
            String ciRunUrl = expertParams.getCiRunUrl();
            if (ciRunUrl != null && !ciRunUrl.isEmpty()) {
                if (shouldPostComments(expertParams)) {
                    try {
                        logger.info("Tracing CI run URL to ticket {}: {}", ticket.getTicketKey(), ciRunUrl);
                        trackerClient.postComment(ticket.getTicketKey(), agentNamePrefix(expertParams) + "Processing started. CI Run: " + ciRunUrl);
                    } catch (Exception e) {
                        logger.warn("Failed to post CI run trace comment for ticket {} — continuing. Error: {}",
                                ticket.getTicketKey(), e.getMessage());
                    }
                } else {
                    logger.debug("CI run URL provided ({}) but comments disabled - not posting to ticket {}", ciRunUrl, ticket.getTicketKey());
                }
            }

            // Execute pre-action before AI processing
            Object preActionResult = js(expertParams.getPreJSAction())
                .mcp(trackerClient, ai, confluence, null) // sourceCode not available in Teammate context
                .withJobContext(expertParams, ticket, null) // response is null in pre-action
                .with(TrackerParams.INITIATOR, initiator)
                .execute();

            // Check return value to determine if processing should continue
            if (preActionResult != null && preActionResult.equals(false)) {
                logger.info("Pre-action returned false, skipping AI processing for ticket: {}", ticket.getKey());
                results.add(new ResultItem(ticket.getTicketKey(), "Skipped by pre-action"));
                return false; // Skip this ticket
            }
            
            // Create and prepare ticket context
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext(true, false, expertParams.isIgnoreClonedByRelationship());
            // Get attachments and convert to text
            List<? extends IAttachment> attachments = ticket.getAttachments();
            if (expertParams.isSkipAllAttachments()) {
                logger.info("⏭️ Skipping all attachments (skipAllAttachments=true)");
                attachments = null;
            } else if (expertParams.isSkipVideoAttachments() && attachments != null) {
                List<IAttachment> filtered = new ArrayList<>();
                for (IAttachment a : attachments) {
                    if (a != null && CliExecutionHelper.isVideoFile(a.getName())) {
                        logger.info("⏭️ Skipping video attachment (skipVideoAttachments=true): {}", a.getName());
                    } else {
                        filtered.add(a);
                    }
                }
                attachments = filtered;
            }
            // Process content with ContextOrchestrator
            //contextOrchestrator.processFullContent(ticket.getKey(), ticketText, (UriToObject) trackerClient, uriProcessingSources, expertParams.getTicketContextDepth());
            
            String textFieldsOnly = trackerClient.getTextFieldsOnly(ticket);

            //inputParams.setKnownInfo(inputParams.getKnownInfo());

            inputParams.setRequest(textFieldsOnly);
            ChunkPreparation contextChunkPreparation = new ChunkPreparation();
            int requestTokens = new Claude35TokenCounter().countTokens(inputParams.toString());
            int systemTokenLimits = contextChunkPreparation.getTokenLimit();
            int tokenLimit = (systemTokenLimits - requestTokens)/2;
            logger.info("GENERATION TOKEN LIMIT: " + tokenLimit);
            contextOrchestrator.setTokenLimit(tokenLimit);
            contextOrchestrator.processUrisInContent(textFieldsOnly, uriProcessingSources, 1);
            contextOrchestrator.processUrisInContent(attachments, uriProcessingSources, 1);
            List<ChunkPreparation.Chunk> chunksContext = contextOrchestrator.summarize();
            contextOrchestrator.clear();
            chunksContext.addAll(contextChunkPreparation.prepareChunks(ticketContext.getComments(), tokenLimit));
            chunksContext.addAll(contextChunkPreparation.prepareChunks(ticketContext.getExtraTickets(), tokenLimit));

            // Process hooks as context first
            String[] hooksAsContext = expertParams.getHooksAsContext();
            StringBuilder globalHooksResponses = new StringBuilder();
            if (hooksAsContext != null && sourceCodes != null) {
                for (String hook : hooksAsContext) {
                    for (SourceCode sourceCode : sourceCodes) {
                        try {
                            String response = sourceCode.callHookAndWaitResponse(hook, inputParams.toString());
                            if (response != null) {
                                globalHooksResponses.append("Tools Information (").append(hook).append("):\n");
                                globalHooksResponses.append(response).append("\n\n");
                            }
                        } catch (Exception e) {
                            // Log but don't fail the workflow
                            logger.error("Failed to call hook: " + hook + ", error: " + e.getMessage());
                        }
                    }
                }
            }

            // Append hooks responses to knownInfo
            if (!globalHooksResponses.isEmpty()) {
                inputParams.setKnownInfo(inputParams.getKnownInfo() + "\n\nAdditional Context:\n" + globalHooksResponses);
            }

            // Process CLI commands if configured
            String[] cliCommands = expertParams.getCliCommands();
            CliExecutionHelper cliHelper = new CliExecutionHelper();
            CliExecutionHelper.CliExecutionResult cliResult = null;
            Path inputContextPath = null;

            // Build timer JS action runnable if configured. Declared here (outer scope) so the
            // SAME timer also wraps postJSAction below — a postJSAction can itself run a long
            // CLI-driven loop (e.g. a feedback-loop quality-gate retry calling cli_execute_command
            // repeatedly from JavaScript), which previously had zero periodic auto-save/auto-commit
            // protection since the timer only covered the main cliCommands execution.
            String timerJSAction = expertParams.getTimerJSAction();
            int timerIntervalSeconds = expertParams.getTimerIntervalSeconds();
            AtomicReference<String> liveCliOutput = new AtomicReference<>("");
            Runnable timerRunnable = null;
            if (timerJSAction != null && !timerJSAction.trim().isEmpty() && timerIntervalSeconds > 0) {
                timerRunnable = () -> {
                    try {
                        js(timerJSAction)
                            .mcp(trackerClient, ai, confluence, null)
                            .withJobContext(expertParams, ticket, null)
                            .with(TrackerParams.INITIATOR, initiator)
                            .with("systemRequest", systemRequestCommentAlias)
                            .with("currentCliOutput", liveCliOutput.get())
                            .execute();
                    } catch (Exception e) {
                        logger.warn("timerJSAction execution failed (continues): {}", e.getMessage());
                    }
                };
                logger.info("timerJSAction configured: {} (interval: {}s)", timerJSAction, timerIntervalSeconds);
            }

            if (cliCommands != null && cliCommands.length > 0) {
                try {
                    // Build final CLI commands with aggregated prompt (shared logic with CliAgent)
                    CliCommandBuilder cliCommandBuilder = new CliCommandBuilder(instructionProcessor, configuration);
                    String[] finalCliCommands = cliCommandBuilder.buildCommands(
                            cliCommands,
                            expertParams.getCliPrompt(),
                            expertParams.getCliPrompts(),
                            expertParams.getCliPromptsByTracker());

                    // Create input context for CLI commands via shared builder.
                    // Pass null as the base directory so the input folder is created relative to the
                    // process working directory, preserving the original Teammate behavior.
                    TicketInputContextBuilder contextBuilder = new TicketInputContextBuilder(instructionProcessor);
                    TicketInputContextBuilder.Result contextResult = contextBuilder.build(
                            expertParams, ticket, null,
                            trackerClient, confluence, null, originalParams);
                    inputContextPath = contextResult.getPath();

                    // Run preCliJSAction to allow extending input folder with extra content before CLI execution
                    String preCliJSAction = expertParams.getPreCliJSAction();
                    if (preCliJSAction != null && !preCliJSAction.trim().isEmpty()) {
                        try {
                            js(preCliJSAction)
                                .mcp(trackerClient, ai, confluence, null)
                                .withJobContext(expertParams, ticket, null)
                                .with(TrackerParams.INITIATOR, initiator)
                                .with("inputFolderPath", inputContextPath.toAbsolutePath().toString())
                                .execute();
                            logger.info("preCliJSAction executed for ticket: {}", ticket.getKey());
                        } catch (Exception e) {
                            logger.warn("preCliJSAction failed for ticket {}, continuing with CLI execution: {}",
                                ticket.getKey(), e.getMessage());
                        }
                    }

                    // Execute CLI commands from project root directory (where cursor-agent can find workspace config)
                    Path projectRoot = Paths.get(System.getProperty("user.dir"));

                    cliResult = cliHelper.executeCliCommandsWithResult(
                            finalCliCommands,
                            projectRoot,
                            null,
                            timerRunnable,
                            timerIntervalSeconds,
                            liveCliOutput,
                            false,
                            expertParams.getExcludedEnvVariables(),
                            expertParams.getExcludedEnvRegexes());
                    
                    // Append CLI responses to knownInfo if not empty
                    StringBuilder cliResponses = cliResult.getCommandResponses();
                    if (!cliResponses.isEmpty()) {
                        String cliContent = cliResponses.toString();
                        // Include output response if available
                        if (cliResult.hasOutputResponse()) {
                            cliContent += cliResult.getOutputResponse() + "\n\n";
                        }
                        inputParams.setKnownInfo(inputParams.getKnownInfo() + "\n\nCLI Execution Results:\n" + cliContent);
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to execute CLI commands for ticket {}: {}", ticket.getKey(), e.getMessage(), e);
                    // Create error result for consistent handling below
                    StringBuilder errorResponse = new StringBuilder("CLI Execution Error: ").append(e.getMessage()).append("\n");
                    cliResult = new CliExecutionHelper.CliExecutionResult(errorResponse, null);
                } finally {
                    // Clean up input context
                    if (inputContextPath != null) {
                        if (expertParams.isCleanupInputFolder()) {
                            cliHelper.cleanupInputContext(inputContextPath);
                            logger.info("Cleaned up input folder (cleanupInputFolder=true)");
                        } else {
                            logger.info("Keeping input folder for inspection (cleanupInputFolder=false): {}",
                                       inputContextPath.toAbsolutePath());
                        }
                    }
                }
            }

            // Process indexes if configured
            IndexConfig[] indexes = expertParams.getIndexes();
            List<ChunkPreparation.Chunk> indexChunks = new ArrayList<>();
            if (indexes != null) {
                for (IndexConfig indexConfig : indexes) {
                    try {
                        List<ToText> indexData = executeIndexTool(indexConfig);
                        if (indexData != null && !indexData.isEmpty()) {
                            String indexName = indexConfig.getIntegration() != null ? indexConfig.getIntegration() : "index";
                            if (expertParams.isSkipAIProcessing()) {
                                // Add to knownInfo as text and save as file
                                String indexText = ToText.Utils.toText(indexData);
                                inputParams.setKnownInfo(inputParams.getKnownInfo() + "\n\nIndex Data (" + indexName + "):\n" + indexText);
                                attachResponse(this, "_index_" + indexName + ".txt", indexText, ticket.getKey(), "text/plain");
                                logger.info("Saved index data from {} as attachment for ticket {}", indexName, ticket.getKey());
                            } else {
                                // Prepare chunks for AI processing with reduced token limit
                                // Account for story tokens (same pattern as TestCasesGenerator)
                                logger.info("Index chunking for {}: story tokens={}, system limit={}, chunk limit={}",
                                    indexName, systemTokenLimits, systemTokenLimits, tokenLimit);
                                
                                List<ChunkPreparation.Chunk> chunks = contextChunkPreparation.prepareChunks(indexData, tokenLimit);
                                indexChunks.addAll(chunks);
                                logger.info("Prepared {} chunks from index {} for ticket {}", chunks.size(), indexName, ticket.getKey());
                            }
                        }
                    } catch (Exception e) {
                        String indexName = indexConfig.getIntegration() != null ? indexConfig.getIntegration() : "index";
                        logger.error("Failed to execute index {} for ticket {}: {}", indexName, ticket.getKey(), e.getMessage(), e);
                    }
                }
            }

            String response;
            boolean skipFieldUpdate = false;  // NEW: Flag to skip field update

            if (expertParams.isSkipAIProcessing()) {
                // Skip AI processing and use CLI output response if available
                if (cliResult != null && cliResult.hasOutputResponse()) {
                    // response.md file exists and has content
                    response = cliResult.getOutputResponse();
                    logger.info("Using CLI output response as final response for ticket {}", ticket.getKey());
                } else {
                    // response.md is missing or empty
                    if (expertParams.isRequireCliOutputFile()) {
                        // Strict mode: Don't use fallback, mark for skipping field update
                        logger.warn("CLI output file (response.md) is missing or empty for ticket {}, " +
                                   "but requireCliOutputFile=true. Will skip field update and post error comment.",
                                   ticket.getKey());

                        // Prepare error message for comment
                        if (cliResult != null) {
                            response = "CLI command executed but did not produce output file:\n" +
                                      cliResult.getCommandResponses().toString();
                        } else {
                            response = "No CLI commands executed or results available.";
                        }

                        skipFieldUpdate = true;  // Mark to skip field update
                    } else {
                        // Permissive mode: Use fallback (backwards compatible)
                        if (cliResult != null) {
                            response = cliResult.getCommandResponses().toString();
                            logger.info("Using CLI execution results as final response for ticket {}", ticket.getKey());
                        } else {
                            response = "No CLI commands executed or results available.";
                            logger.info("No CLI results available for ticket {}", ticket.getKey());
                        }
                    }
                }
            } else {
                // Standard AI processing workflow with index chunks
                if (!indexChunks.isEmpty()) {
                    chunksContext.addAll(indexChunks);
                }
                GenericRequestAgent.Params genericRequesAgentParams = new GenericRequestAgent.Params(inputParams, null, chunksContext, expertParams.getChunkProcessingTimeoutInMinutes() * 60 * 1000);
                response = genericRequestAgent.run(genericRequesAgentParams);
            }
            AtomicReference<Exception> postJsError = new AtomicReference<>();
            CliExecutionHelper.runWithTimer(timerRunnable, timerIntervalSeconds, () -> {
                try {
                    js(expertParams.getPostJSAction())
                        .mcp(trackerClient, ai, confluence, null) // sourceCode not available in Teammate context
                        .withJobContext(expertParams, ticket, response)
                        .with(TrackerParams.INITIATOR, initiator)
                        .with("systemRequest", systemRequestCommentAlias)
                        .execute();
                } catch (Exception e) {
                    postJsError.set(e);
                }
            });
            if (postJsError.get() != null) {
                throw postJsError.get();
            }
            if (expertParams.isAttachResponseAsFile()) {
                attachResponse(genericRequestAgent, "_final_answer.txt", response, ticket.getKey(), "text/plain");
            }
            
            // Handle output based on outputType, skip publishing if outputType is 'none'
            if (outputType != Params.OutputType.none) {
                // NEW: Check if output should be skipped (when requireCliOutputFile=true and no output file)
                if (skipFieldUpdate) {
                    logger.warn("Skipping output processing for ticket {} due to missing CLI output file (requireCliOutputFile=true)",
                               ticket.getKey());

                    // Post error comment (when outputType is not 'none')
                    String errorComment;
                    if (initiator != null && !initiator.isEmpty()) {
                        errorComment = trackerClient.tag(initiator) +
                            ", \n\n⚠️ CLI command execution issue:\n\n" + response;
                    } else {
                        errorComment = "⚠️ CLI command execution issue:\n\n" + response;
                    }
                    trackerClient.postComment(ticket.getTicketKey(), errorComment);
                    logger.info("Posted error comment to ticket {} instead of processing output (outputType={})",
                               ticket.getKey(), outputType);

                    // Skip further processing (no field update, no comment, no ticket creation)
                } else {
                    // Normal output processing flow (unchanged)
                    if (outputType == Params.OutputType.field) {
                        // Use tracker-agnostic field resolution
                        final String fieldCode = trackerClient.resolveFieldName(ticket.getTicketKey(), fieldName);
                        String currentFieldValue = ticket.getFieldValueAsString(fieldCode);

                        if (expertParams.getOperationType() == Params.OperationType.Append) {
                            String newValue;
                            if (currentFieldValue == null || currentFieldValue.trim().isEmpty()) {
                                newValue = response;
                            } else {
                                newValue = currentFieldValue + "\n\n" + response;
                            }
                            trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCode, newValue));
                        } else if (expertParams.getOperationType() == Params.OperationType.Replace) {
                            trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCode, response));
                        }

                        if (initiator != null && !initiator.isEmpty()) {
                            String comment = trackerClient.tag(initiator) + ", \n\n AI response in '" + fieldName + "' on your request.";
                            if (systemRequestCommentAlias != null && !systemRequestCommentAlias.isEmpty()) {
                                comment = trackerClient.tag(initiator) + ", there is AI response in '"+ fieldName + "' on your request: \n"+
                                        "System Request: " + systemRequestCommentAlias;
                            }
                            trackerClient.postComment(ticket.getTicketKey(), comment);
                        }
                    } else {
                        String comment = trackerClient.tag(initiator) + ", \n\nAI Response is: \n" + response;
                        if (systemRequestCommentAlias != null && !systemRequestCommentAlias.isEmpty()) {
                            comment = trackerClient.tag(initiator) + ", there is response on your request: \n" + "System Request: " + systemRequestCommentAlias + "\n\nAI Response is: \n" + response;
                        }
                        trackerClient.postCommentIfNotExists(ticket.getTicketKey(), comment);
                    }
                }
            } else {
                logger.info("Output type is 'none', skipping publishing results for ticket {}", ticket.getKey());
            }

            // Post completion comment so the initiator is notified when the Teammate run finishes
            postCompletionComment(trackerClient, ticket.getTicketKey(), ciRunUrl, initiator, expertParams);

            results.add(new ResultItem(ticket.getTicketKey(), response));
            return false;
        }, inputJQL, trackerClient.getExtendedQueryFields());
        return results;
    }


    /**
     * Resolves the effective CLI prompts by merging base {@code cliPrompts} with tracker-specific
     * prompts from {@code cliPromptsByTracker} when a matching tracker type is configured.
     *
     * @param baseCliPrompts      the base CLI prompts array (may be null)
     * @param cliPromptsByTracker map of tracker type → tracker-specific prompts (may be null)
     * @param trackerType         the current tracker type from configuration (may be null)
     * @return merged array of CLI prompts, or base prompts if no tracker-specific match found
     */
    static String[] resolveCliPrompts(String[] baseCliPrompts, Map<String, String[]> cliPromptsByTracker, String trackerType) {
        return CliCommandBuilder.resolveCliPrompts(baseCliPrompts, cliPromptsByTracker, trackerType);
    }

    public void attachResponse(Object orchestratorClass, String file, String result, String ticketKey, String contentType) throws IOException {
        String fileNameResult = orchestratorClass.getClass().getSimpleName() + file;
        String[] fields = {Fields.ATTACHMENT, Fields.SUMMARY};
        ITicket t = trackerClient.performTicket(ticketKey, fields);
        List<? extends IAttachment> attachments = t.getAttachments();
        fileNameResult = IAttachment.Utils.generateUniqueFileName(fileNameResult, attachments);

        File tempFileResult = File.createTempFile(fileNameResult, null);

        // Write JSON to file using Commons IO
        FileUtils.writeStringToFile(tempFileResult, result, "UTF-8");

        // Attach file to ticket
        trackerClient.attachFileToTicket(
                ticketKey,
                fileNameResult,
                contentType,
                tempFileResult
        );
        // Clean up temp file
        IOUtils.deleteFileIfExists(tempFileResult);
    }

    /**
     * Executes an index tool based on the configuration and returns the results as List<ToText>.
     *
     * @param config The index configuration specifying which tool to use and its parameters
     * @return List of ToText objects from the index tool, or empty list if tool is unknown
     * @throws IOException if an error occurs reading from the index
     */
    private List<ToText> executeIndexTool(IndexConfig config) throws IOException {
        if (config == null) {
            return Collections.emptyList();
        }
        return mermaidIndexTools.read(config.getIntegration(), config.getStoragePath());
    }

    /**
     * Builds the completion comment text that tags the initiator and references the CI run URL.
     * If tagging the initiator fails, the comment is built without the tag so the notification
     * still goes out.
     */
    static String buildCompletionComment(String initiator, String ciRunUrl, TrackerClient trackerClient) {
        if (initiator != null && !initiator.isEmpty()) {
            try {
                return trackerClient.tag(initiator) + ", \n\n✅ Teammate run completed. CI Run: " + ciRunUrl;
            } catch (Exception e) {
                logger.warn("Failed to tag initiator {} in completion comment — posting without tag. Error: {}",
                        initiator, e.getMessage());
            }
        }
        return "✅ Teammate run completed. CI Run: " + ciRunUrl;
    }

    /**
     * Posts a completion comment to the ticket when a CI run URL is configured.
     * Tags the initiator if available. Errors are logged and swallowed so the job continues.
     */
    void postCompletionComment(TrackerClient trackerClient, String ticketKey, String ciRunUrl, String initiator, TeammateParams params) {
        if (ciRunUrl == null || ciRunUrl.isEmpty()) {
            return;
        }
        if (!shouldPostComments(params)) {
            logger.debug("CI run URL provided ({}) but comments disabled - not posting completion comment to ticket {}",
                    ciRunUrl, ticketKey);
            return;
        }
        try {
            String completionComment = buildCompletionComment(initiator, ciRunUrl, trackerClient);
            trackerClient.postComment(ticketKey, agentNamePrefix(params) + completionComment);
            logger.info("Posted completion comment for ticket {} with CI run trace", ticketKey);
        } catch (Exception e) {
            logger.warn("Failed to post completion comment for ticket {} — continuing. Error: {}",
                    ticketKey, e.getMessage());
        }
    }

}
