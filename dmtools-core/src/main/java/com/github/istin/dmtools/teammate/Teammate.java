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

public class Teammate extends AbstractJob<Teammate.TeammateParams, List<ResultItem>> {

    private static final Logger logger = LogManager.getLogger(Teammate.class);

    @Getter
    @Setter
    public static class TeammateParams extends JobTrackerParams<RequestDecompositionAgent.Result> {

        public static final String SYSTEM_REQUEST_COMMENT_ALIAS = "systemRequestCommentAlias";

        @SerializedName("hooksAsContext")
        private String[] hooksAsContext;

        @SerializedName("cliCommands")
        private String[] cliCommands;

        @SerializedName("skipAIProcessing")
        private boolean skipAIProcessing = false;

        @SerializedName("indexes")
        private IndexConfig[] indexes;

        @SerializedName(SYSTEM_REQUEST_COMMENT_ALIAS)
        private String systemRequestCommentAlias;

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
        // Validate TrackerClient availability if inputJql is provided
        String inputJQL = expertParams.getInputJql();
        if (inputJQL != null && !inputJQL.trim().isEmpty() && trackerClient == null) {
            throw new IllegalStateException(
                "TrackerClient is not configured, but inputJql is provided. " +
                "Please configure Jira (JIRA_BASE_PATH + JIRA_EMAIL + JIRA_API_TOKEN) or " +
                "ADO (ADO_ORGANIZATION + ADO_PROJECT + ADO_PAT_TOKEN) or " +
                "Rally (RALLY_PATH + RALLY_TOKEN). " +
                "Alternatively, remove inputJql parameter if tracker integration is not needed."
            );
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

        RequestDecompositionAgent.Result inputParams = expertParams.getAgentParams();
        String[] aiRoleArray = instructionProcessor.extractIfNeeded(inputParams.getAiRole());
        inputParams.setAiRole(aiRoleArray.length > 0 ? aiRoleArray[0] : "");
        inputParams.setInstructions(instructionProcessor.extractIfNeeded(inputParams.getInstructions()));
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
            ticketContext.prepareContext(true, false);
            // Get attachments and convert to text
            List<? extends IAttachment> attachments = ticket.getAttachments();
            // Process content with ContextOrchestrator
            //contextOrchestrator.processFullContent(ticket.getKey(), ticketText, (UriToObject) trackerClient, uriProcessingSources, expertParams.getTicketContextDepth());
            
            String textFieldsOnly = trackerClient.getTextFieldsOnly(ticket);

            //inputParams.setKnownInfo(inputParams.getKnownInfo());

            inputParams.setRequest(textFieldsOnly);
            ChunkPreparation contextChunkPreparation = new ChunkPreparation();
            int requestTokens = new Claude35TokenCounter().countTokens(inputParams.toString());
            int systemTokenLimits = contextChunkPreparation.getTokenLimit();
            int tokenLimit = (systemTokenLimits - requestTokens)/2;
            System.out.println("GENERATION TOKEN LIMIT: " + tokenLimit);
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
                            System.err.println("Failed to call hook: " + hook + ", error: " + e.getMessage());
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
            
            if (cliCommands != null && cliCommands.length > 0) {
                try {
                    // Create input context for CLI commands
                    inputContextPath = cliHelper.createInputContext(ticket, inputParams.toString(), trackerClient);
                    
                    // Execute CLI commands from project root directory (where cursor-agent can find workspace config)
                    Path projectRoot = Paths.get(System.getProperty("user.dir"));
                    cliResult = cliHelper.executeCliCommandsWithResult(cliCommands, projectRoot, null);
                    
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
                        cliHelper.cleanupInputContext(inputContextPath);
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
            if (expertParams.isSkipAIProcessing()) {
                // Skip AI processing and use CLI output response if available
                if (cliResult != null && cliResult.hasOutputResponse()) {
                    response = cliResult.getOutputResponse();
                    logger.info("Using CLI output response as final response for ticket {}", ticket.getKey());
                } else if (cliResult != null) {
                    response = cliResult.getCommandResponses().toString();
                    logger.info("Using CLI execution results as final response for ticket {}", ticket.getKey());
                } else {
                    response = "No CLI commands executed or results available.";
                    logger.info("No CLI results available for ticket {}", ticket.getKey());
                }
            } else {
                // Standard AI processing workflow with index chunks
                if (!indexChunks.isEmpty()) {
                    chunksContext.addAll(indexChunks);
                }
                GenericRequestAgent.Params genericRequesAgentParams = new GenericRequestAgent.Params(inputParams, null, chunksContext, expertParams.getChunkProcessingTimeoutInMinutes() * 60 * 1000);
                response = genericRequestAgent.run(genericRequesAgentParams);
            }
            js(expertParams.getPostJSAction())
                .mcp(trackerClient, ai, confluence, null) // sourceCode not available in Teammate context
                .withJobContext(expertParams, ticket, response)
                .with(TrackerParams.INITIATOR, initiator)
                .with("systemRequest", systemRequestCommentAlias)
                .execute();
            if (expertParams.isAttachResponseAsFile()) {
                attachResponse(genericRequestAgent, "_final_answer.txt", response, ticket.getKey(), "text/plain");
            }
            
            // Handle output based on outputType, skip publishing if outputType is 'none'
            if (outputType != Params.OutputType.none) {
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
            } else {
                logger.info("Output type is 'none', skipping publishing results for ticket {}", ticket.getKey());
            }
            results.add(new ResultItem(ticket.getTicketKey(), response));
            return false;
        }, inputJQL, trackerClient.getExtendedQueryFields());
        return results;
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
        FileUtils.deleteQuietly(tempFileResult);
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

}