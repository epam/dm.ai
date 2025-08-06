package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.ai.agent.SourceImpactAssessmentAgent;
import com.github.istin.dmtools.ai.agent.TeamAssistantAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import com.github.istin.dmtools.common.utils.MarkdownToJiraConverter;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.di.*;
import com.github.istin.dmtools.expert.ExpertParams;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.JobTrackerParams;
import com.github.istin.dmtools.job.Params;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.search.CodebaseSearchOrchestrator;
import com.github.istin.dmtools.search.ConfluenceSearchOrchestrator;
import com.github.istin.dmtools.search.TrackerSearchOrchestrator;
import dagger.Component;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Teammate extends AbstractJob<Teammate.TeammateParams, List<ResultItem>> {

    public static class TeammateParams extends JobTrackerParams<RequestDecompositionAgent.Result> {

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
    SourceCodeFactory sourceCodeFactory;

    @Inject
    SourceImpactAssessmentAgent sourceImpactAssessmentAgent;

    @Inject
    RequestDecompositionAgent requestDecompositionAgent;

    TeamAssistantAgent teamAssistantAgent;

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

    private static TeammateComponent teammateComponent;

    /**
     * Server-managed Dagger component that uses pre-resolved integrations
     * Includes ServerManagedIntegrationsModule for integrations and AIAgentsModule for agents
     */
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class, SourceCodeModule.class, AIAgentsModule.class})
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
        // Use existing Dagger component for standalone mode
        if (teammateComponent == null) {
            teammateComponent = DaggerTeammateComponent.create();
        }
        teammateComponent.inject(this);
        
        // Initialize TeamAssistantAgent with injected dependencies
        teamAssistantAgent = new TeamAssistantAgent(ai, promptTemplateReader);
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        // Create dynamic component with pre-resolved integrations
        try {
            ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(resolvedIntegrations);
            ServerManagedExpertComponent component = DaggerTeammate_ServerManagedExpertComponent.builder()
                    .serverManagedIntegrationsModule(module)
                    .build();
            component.inject(this);
            
            // Initialize TeamAssistantAgent with server-managed dependencies
            teamAssistantAgent = new TeamAssistantAgent(ai, promptTemplateReader);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Expert in server-managed mode", e);
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
        inputParams.setQuestions(new String[]{});
        inputParams.setTasks(new String[]{});

        contextOrchestrator.processUrisInContent(inputParams.getKnownInfo(), uriProcessingSources, 2);
        inputParams.setKnownInfo(contextOrchestrator.summarize().toString());
        contextOrchestrator.clear();

        List<ResultItem> results = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext(true);
            List<? extends IAttachment> attachments = ticket.getAttachments();
            contextOrchestrator.processFullContent(ticket.getKey(), ticketContext.toText(), (UriToObject) trackerClient, uriProcessingSources, expertParams.getTicketContextDepth());
            String textFieldsOnly = trackerClient.getTextFieldsOnly(ticket);
            contextOrchestrator.processUrisInContent(textFieldsOnly, uriProcessingSources, 1);
            contextOrchestrator.processUrisInContent(attachments, uriProcessingSources, 1);
            List<ChunkPreparation.Chunk> chunksContext = contextOrchestrator.summarize();

            TeamAssistantAgent.Params teamAssistantParams = new TeamAssistantAgent.Params(inputParams, null, chunksContext, expertParams.getChunkProcessingTimeoutInMinutes() * 60 * 1000);
            String response = teamAssistantAgent.run(teamAssistantParams);
            if (expertParams.isAttachResponseAsFile()) {
                attachResponse(teamAssistantAgent, "_final_answer.txt", response, ticket.getKey(), "text/plain");
            }
            if (outputType == Params.OutputType.field) {
                String fieldCustomCode = ((JiraClient) trackerClient).getFieldCustomCode(ticket.getTicketKey().split("-")[0], fieldName);
                String currentFieldValue = ticket.getFields().getString(fieldCustomCode);
                if (expertParams.getOperationType() == Params.OperationType.Append) {
                    trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCustomCode, currentFieldValue + "\n\n" + StringUtils.convertToMarkdown(response)));
                } else if (expertParams.getOperationType() == Params.OperationType.Replace) {
                    trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCustomCode, StringUtils.convertToMarkdown(response)));
                }
                if (initiator != null && initiator.isEmpty()) {
                    trackerClient.postComment(ticket.getTicketKey(), trackerClient.tag(initiator) + ", \n\n AI response in '" + fieldName + "' on your request.");
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
        StringBuilder result = new StringBuilder();
        for (String input : inputArray) {
            if (input != null && input.startsWith("https://")) {
                String value = confluence.contentByUrl(input).getStorage().getValue();
                if (StringUtils.isConfluenceYamlFormat(value)) {
                    input = StringUtils.extractYamlContentFromConfluence(value);
                } else {
                    input = HtmlCleaner.cleanOnlyStylesAndSizes(value);
                    input = MarkdownToJiraConverter.convertToJiraMarkdown(input);
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