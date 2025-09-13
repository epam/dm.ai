package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.ai.agent.SourceImpactAssessmentAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.di.AIAgentsModule;
import com.github.istin.dmtools.di.DaggerTeammateComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.di.TeammateComponent;
import com.github.istin.dmtools.expert.ExpertParams;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.JobJavaScriptBridge;
import com.github.istin.dmtools.job.JobTrackerParams;
import com.github.istin.dmtools.job.Params;
import com.github.istin.dmtools.job.ResultItem;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Teammate extends AbstractJob<Teammate.TeammateParams, List<ResultItem>> {

    private static final Logger logger = LogManager.getLogger(Teammate.class);

    @Getter
    @Setter
    public static class TeammateParams extends JobTrackerParams<RequestDecompositionAgent.Result> {

        @SerializedName("hooksAsContext")
        private String[] hooksAsContext;

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

    // JavaScript bridge is now inherited from AbstractJob

    private static TeammateComponent teammateComponent;

    /**
     * Server-managed Dagger component that uses pre-resolved integrations
     * Includes ServerManagedIntegrationsModule for integrations and AIAgentsModule for agents
     */
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class, AIAgentsModule.class})
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
        ExpertParams.OutputType outputType = expertParams.getOutputType();
        String initiator = expertParams.getInitiator();
        String inputJQL = expertParams.getInputJql();
        String fieldName = expertParams.getFieldName();

        // Use injected UriToObjectFactory to create URI processing sources
        List<? extends UriToObject> uriProcessingSources;
        try {
            uriProcessingSources = uriToObjectFactory.createUriProcessingSources();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create URI processing sources", e);
        }

        RequestDecompositionAgent.Result inputParams = expertParams.getAgentParams();
        inputParams.setAiRole(extractIfNeeded(inputParams.getAiRole()));
        inputParams.setInstructions(new String[] {extractIfNeeded(inputParams.getInstructions())});
        inputParams.setFormattingRules(extractIfNeeded(inputParams.getFormattingRules()));
        inputParams.setFewShots(extractIfNeeded(inputParams.getFewShots()));
        inputParams.setQuestions(new String[] {extractIfNeeded(inputParams.getQuestions())});
        inputParams.setTasks(new String[] {extractIfNeeded(inputParams.getTasks())});

        contextOrchestrator.processUrisInContent(inputParams.getKnownInfo(), uriProcessingSources, 2);
        String processedKnownInfo = contextOrchestrator.summarize().toString();
        inputParams.setKnownInfo(processedKnownInfo);
        contextOrchestrator.clear();

        List<ResultItem> results = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            long overallStart = System.currentTimeMillis();
            logger.info("TIMING: Starting ticket processing for {} at {}", ticket.getKey(), overallStart);
            
            // Step 1: Create TicketContext
            long step1Start = System.currentTimeMillis();
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            long step1Duration = System.currentTimeMillis() - step1Start;
            logger.info("TIMING: TicketContext creation took {}ms for {}", step1Duration, ticket.getKey());
            
            // Step 2: Prepare Context (this includes IssuesIDsParser.extractAllJiraIDs)
            long step2Start = System.currentTimeMillis();
            logger.info("TIMING: Starting prepareContext(true) for {} at {}", ticket.getKey(), step2Start);
            ticketContext.prepareContext(true);
            long step2Duration = System.currentTimeMillis() - step2Start;
            logger.info("TIMING: prepareContext(true) took {}ms for {}", step2Duration, ticket.getKey());
            
            // Step 3: Get attachments
            long step3Start = System.currentTimeMillis();
            List<? extends IAttachment> attachments = ticket.getAttachments();
            long step3Duration = System.currentTimeMillis() - step3Start;
            logger.info("TIMING: getAttachments() took {}ms for {}", step3Duration, ticket.getKey());
            
            // Step 4: Convert to text
            long step4Start = System.currentTimeMillis();
            logger.info("TIMING: Starting ticketContext.toText() for {} at {}", ticket.getKey(), step4Start);
            String ticketText = ticketContext.toText();
            long step4Duration = System.currentTimeMillis() - step4Start;
            logger.info("TIMING: ticketContext.toText() took {}ms for {} (text length: {})", step4Duration, ticket.getKey(), ticketText.length());
            
            // Step 5: Process full content with ContextOrchestrator
            long step5Start = System.currentTimeMillis();
            logger.info("TIMING: Starting processFullContent() for {} at {} with depth {}", ticket.getKey(), step5Start, expertParams.getTicketContextDepth());
            contextOrchestrator.processFullContent(ticket.getKey(), ticketText, (UriToObject) trackerClient, uriProcessingSources, expertParams.getTicketContextDepth());
            long step5Duration = System.currentTimeMillis() - step5Start;
            logger.info("TIMING: processFullContent() took {}ms for {}", step5Duration, ticket.getKey());
            
            // Step 6: Get text fields only
            long step6Start = System.currentTimeMillis();
            String textFieldsOnly = trackerClient.getTextFieldsOnly(ticket);
            long step6Duration = System.currentTimeMillis() - step6Start;
            logger.info("TIMING: getTextFieldsOnly() took {}ms for {}", step6Duration, ticket.getKey());
            
            // Step 7: Process URIs in text fields
            long step7Start = System.currentTimeMillis();
            logger.info("TIMING: Starting processUrisInContent(textFields) for {} at {}", ticket.getKey(), step7Start);
            contextOrchestrator.processUrisInContent(textFieldsOnly, uriProcessingSources, 1);
            long step7Duration = System.currentTimeMillis() - step7Start;
            logger.info("TIMING: processUrisInContent(textFields) took {}ms for {}", step7Duration, ticket.getKey());
            
            // Step 8: Process URIs in attachments
            long step8Start = System.currentTimeMillis();
            logger.info("TIMING: Starting processUrisInContent(attachments) for {} at {}", ticket.getKey(), step8Start);
            contextOrchestrator.processUrisInContent(attachments, uriProcessingSources, 1);
            long step8Duration = System.currentTimeMillis() - step8Start;
            logger.info("TIMING: processUrisInContent(attachments) took {}ms for {}", step8Duration, ticket.getKey());
            
            // Step 9: Summarize context
            long step9Start = System.currentTimeMillis();
            logger.info("TIMING: Starting contextOrchestrator.summarize() for {} at {}", ticket.getKey(), step9Start);
            List<ChunkPreparation.Chunk> chunksContext = contextOrchestrator.summarize();
            long step9Duration = System.currentTimeMillis() - step9Start;
            logger.info("TIMING: contextOrchestrator.summarize() took {}ms for {}", step9Duration, ticket.getKey());
            
            inputParams.setKnownInfo(inputParams.getKnownInfo() + "\n" + chunksContext.toString());
            contextOrchestrator.clear();
            
            long overallDuration = System.currentTimeMillis() - overallStart;
            logger.info("TIMING: Overall ticket processing took {}ms for {}", overallDuration, ticket.getKey());

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

            GenericRequestAgent.Params genericRequesAgentParams = new GenericRequestAgent.Params(inputParams, null, null, expertParams.getChunkProcessingTimeoutInMinutes() * 60 * 1000);
            String response = genericRequestAgent.run(genericRequesAgentParams);
            js(expertParams.getPostJSAction())
                .mcp(trackerClient, ai, confluence, null) // sourceCode not available in Teammate context
                .withJobContext(expertParams, ticket, response)
                .with("initiator", initiator)
                .execute();
            if (expertParams.isAttachResponseAsFile()) {
                attachResponse(genericRequestAgent, "_final_answer.txt", response, ticket.getKey(), "text/plain");
            }
            if (outputType == Params.OutputType.field) {
                if (trackerClient instanceof JiraClient<?>) {
                    String fieldCustomCode = ((JiraClient<?>) trackerClient).getFieldCustomCode(ticket.getTicketKey().split("-")[0], fieldName);
                    String currentFieldValue = ticket.getFields().getString(fieldCustomCode);
                    if (expertParams.getOperationType() == Params.OperationType.Append) {
                        trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCustomCode, currentFieldValue + "\n\n" + response));
                    } else if (expertParams.getOperationType() == Params.OperationType.Replace) {
                        trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCustomCode, response));
                    }
                    if (initiator != null && initiator.isEmpty()) {
                        trackerClient.postComment(ticket.getTicketKey(), trackerClient.tag(initiator) + ", \n\n AI response in '" + fieldName + "' on your request.");
                    }
                } else {
                    throw new UnsupportedOperationException("the operation to set value to field was tested only with jira client");
                }
            } else {
                trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(initiator) + ", \n\nAI Response is: \n" + response);
            }
            results.add(new ResultItem(ticket.getTicketKey(), response));
            return false;
        }, inputJQL, trackerClient.getExtendedQueryFields());
        return results;
    }

    private String extractIfNeeded(String... inputArray) throws IOException {
        if (inputArray == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String input : inputArray) {
            if (!result.isEmpty()) {
                result.append("\n");
            }
            if (input != null && input.startsWith("https://")) {
                String value = confluence.contentByUrl(input).getStorage().getValue();
                if (StringUtils.isConfluenceYamlFormat(value)) {
                    input = StringUtils.extractYamlContentFromConfluence(value);
                } else {
                    input = value;
                }
            }
            result.append(input);
        }
        return result.toString();
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

}