package com.github.istin.dmtools.expert;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.ConfluencePagesContext;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.ai.agent.SourceImpactAssessmentAgent;
import com.github.istin.dmtools.ai.agent.TeamAssistantAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
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
import com.github.istin.dmtools.di.DaggerExpertComponent;
import com.github.istin.dmtools.di.ExpertComponent;
import com.github.istin.dmtools.di.SourceCodeFactory;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.Params;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.search.AbstractSearchOrchestrator;
import com.github.istin.dmtools.search.CodebaseSearchOrchestrator;
// import com.github.istin.dmtools.search.ConfluenceSearchOrchestrator; // Temporarily disabled
import com.github.istin.dmtools.search.TrackerSearchOrchestrator;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Expert extends AbstractJob<ExpertParams> {

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

    @Inject
    TeamAssistantAgent teamAssistantAgent;

    @Inject
    ApplicationConfiguration configuration;

    List<CodebaseSearchOrchestrator> listOfCodebaseSearchOrchestrator = new ArrayList<>();

    // @Inject
    // ConfluenceSearchOrchestrator confluenceSearchOrchestrator; // Temporarily disabled

    @Inject
    TrackerSearchOrchestrator trackerSearchOrchestrator;

    @Inject
    ContextOrchestrator contextOrchestrator;

    private static ExpertComponent expertComponent;

    /**
     * Creates a new Expert instance with the default configuration
     */
    public Expert() {
        this(null);
    }

    /**
     * Creates a new Expert instance with the provided configuration
     * @param configuration The application configuration to use
     */
    public Expert(ApplicationConfiguration configuration) {
        if (expertComponent == null) {
            expertComponent = DaggerExpertComponent.create();
        }
        expertComponent.inject(this);
    }

    @Override
    protected void runJobImpl(ExpertParams expertParams) throws Exception {
        String projectContext = expertParams.getProjectContext();
        String request = expertParams.getRequest();
        String systemRequest = expertParams.getSystemRequest();
        String systemRequestCommentAlias = expertParams.getSystemRequestCommentAlias();
        String[] confluencePages = expertParams.getConfluencePages();
        ExpertParams.OutputType outputType = expertParams.getOutputType();
        String initiator = expertParams.getInitiator();
        String inputJQL = expertParams.getInputJql();
        String fieldName = expertParams.getFieldName();

        List<? extends UriToObject> uriProcessingSources = new UriToObjectFactory().createUriProcessingSources(expertParams.getSourceCodeConfig());

        boolean transformConfluencePagesToMarkdown = expertParams.isTransformConfluencePagesToMarkdown();
        if (systemRequest != null && systemRequest.startsWith("https://")) {
            String value = confluence.contentByUrl(systemRequest).getStorage().getValue();
            if (StringUtils.isConfluenceYamlFormat(value)) {
                systemRequest = StringUtils.extractYamlContentFromConfluence(value);
            } else {
                systemRequest = HtmlCleaner.cleanOnlyStylesAndSizes(value);
                if (transformConfluencePagesToMarkdown) {
                    systemRequest = MarkdownToJiraConverter.convertToJiraMarkdown(systemRequest);
                }
            }
        }

        contextOrchestrator.processUrisInContent(systemRequest, uriProcessingSources, 1);

        if (projectContext != null && projectContext.startsWith("https://")) {
            projectContext = HtmlCleaner.cleanOnlyStylesAndSizes(confluence.contentByUrl(projectContext).getStorage().getValue());
            if (transformConfluencePagesToMarkdown) {
                projectContext = MarkdownToJiraConverter.convertToJiraMarkdown(projectContext);
            }
        }

        if (confluencePages != null) {
            new ConfluencePagesContext(contextOrchestrator, confluencePages, confluence, transformConfluencePagesToMarkdown);
        }

        String finalProjectContext = projectContext;
        String finalSystemRequest = systemRequest;
        trackerClient.searchAndPerform(ticket -> {
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext(true);
            List<? extends IAttachment> attachments = ticket.getAttachments();
            contextOrchestrator.processFullContent(ticket.getKey(), ticketContext.toText(), (UriToObject) trackerClient, uriProcessingSources, expertParams.getTicketContextDepth());
            String textFieldsOnly = trackerClient.getTextFieldsOnly(ticket);
            contextOrchestrator.processUrisInContent(textFieldsOnly, uriProcessingSources, 1);
            contextOrchestrator.processUrisInContent(attachments, uriProcessingSources, 1);
            List<ChunkPreparation.Chunk> chunksContext = contextOrchestrator.summarize();
            RequestDecompositionAgent.Params requestDecompositionParams = new RequestDecompositionAgent.Params(finalSystemRequest + "\n" + request, finalProjectContext + "\n" + ticketContext.toText(), null, expertParams.getRequestDecompositionChunkProcessing() ? chunksContext : null);
            RequestDecompositionAgent.Result structuredRequest = requestDecompositionAgent.run(requestDecompositionParams);

            if (expertParams.isCodeAsSource() || expertParams.isConfluenceAsSource() || expertParams.isTrackerAsSource()) {
                if (expertParams.isCodeAsSource()) {
                    List<ChunkPreparation.Chunk> fileExtendedChunks = extendContextWithCode(ticket.getTicketKey(), expertParams, structuredRequest);
                    chunksContext.addAll(fileExtendedChunks);
                }
                if (expertParams.isConfluenceAsSource()) {
                    List<ChunkPreparation.Chunk> confluenceExtendedChunks = extendContextWithConfluence(ticket.getTicketKey(), expertParams, structuredRequest);
                    chunksContext.addAll(confluenceExtendedChunks);
                }
                if (expertParams.isTrackerAsSource()) {
                    List<ChunkPreparation.Chunk> trackerExtendedChunks = extendContextWithTracker(ticket.getTicketKey(), expertParams, structuredRequest);
                    chunksContext.addAll(trackerExtendedChunks);
                }
            }

            TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(structuredRequest, null, chunksContext, expertParams.getChunkProcessingTimeoutInMinutes() * 60 * 1000);
            String response = teamAssistantAgent.run(params);
            attachResponse(teamAssistantAgent, "_final_answer.txt", response, ticket.getKey(), "text/plain");
            if (outputType == Params.OutputType.field) {
                String fieldCustomCode = ((JiraClient) BasicJiraClient.getInstance()).getFieldCustomCode(ticket.getTicketKey().split("-")[0], fieldName);
                String currentFieldValue = ticket.getFields().getString(fieldCustomCode);
                if (expertParams.getOperationType() == Params.OperationType.Append) {
                    trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCustomCode, currentFieldValue + "\n\n" + StringUtils.convertToMarkdown(response)));
                } else if (expertParams.getOperationType() == Params.OperationType.Replace) {
                    trackerClient.updateTicket(ticket.getTicketKey(), fields -> fields.set(fieldCustomCode, StringUtils.convertToMarkdown(response)));
                }
                trackerClient.postComment(ticket.getTicketKey(), trackerClient.tag(initiator) + ", there is AI response in '"+ fieldName + "' on your request: \n"+
                        "System Request: " + systemRequestCommentAlias + "\n" + request);
            } else {
                trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(initiator) + ", there is response on your request: \n" + "System Request: " + systemRequestCommentAlias + "\n"+ request + "\n\nAI Response is: \n" + response);
            }
            return false;
        }, inputJQL, trackerClient.getExtendedQueryFields());
    }

    private String getKeywordsBlacklist(String keywordsBlacklist) throws Exception {
        if (keywordsBlacklist != null && keywordsBlacklist.startsWith("https://")) {
            return confluence.contentByUrl(keywordsBlacklist).getStorage().getValue();
        }
        if (keywordsBlacklist == null) {
            return "";
        }
        return keywordsBlacklist;
    }

    protected void saveAndAttachStats(String ticketKey, List<ChunkPreparation.Chunk> chunks, AbstractSearchOrchestrator orchestratorClass) {

        try {
            StringBuilder builder = new StringBuilder();
            for (ChunkPreparation.Chunk chunk : chunks) {
                builder.append(chunk.getText());
                List<File> files = chunk.getFiles();
                if (files != null && !files.isEmpty()) {
                    builder.append("\n").append("Files: ");
                    for (File file : files) {
                        builder.append("\n").append(file.getName());
                    }
                }
            }
            attachResponse(orchestratorClass, "_stats.json", orchestratorClass.getSearchStats().toJson().toString(2), ticketKey, "application/json");
            attachResponse(orchestratorClass, "_result.txt", builder.toString(), ticketKey, "text/plain");
        } catch (Exception e) {
            System.err.println("Failed to save and attach stats: " + e.getMessage());
            e.printStackTrace();
        }
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


    private List<ChunkPreparation.Chunk> extendContextWithCode(String ticketKey, ExpertParams expertParams, RequestDecompositionAgent.Result structuredRequest) throws Exception {
        SourceCodeConfig[] sourceCodeConfig = expertParams.getSourceCodeConfig();
        List<SourceCode> sourceCodeList = new SourceCodeFactory().createSourceCodes(sourceCodeConfig);
        List<ChunkPreparation.Chunk> chunks = new ArrayList<>();
        for (SourceCode sourceCode : sourceCodeList) {
            CodebaseSearchOrchestrator searchOrchestrator = new CodebaseSearchOrchestrator(sourceCode);
            listOfCodebaseSearchOrchestrator.add(searchOrchestrator);
            String keywordsBlacklist = getKeywordsBlacklist(expertParams.getKeywordsBlacklist());
            int filesLimit = expertParams.getFilesLimit();
            List<ChunkPreparation.Chunk> newChunks = searchOrchestrator.run(structuredRequest.toString(), keywordsBlacklist, filesLimit, expertParams.getFilesIterations());
            saveAndAttachStats(ticketKey, chunks, searchOrchestrator);
            chunks.addAll(newChunks);
        }

        return chunks;
    }

    private List<ChunkPreparation.Chunk> extendContextWithConfluence(String ticketKey, ExpertParams expertParams, RequestDecompositionAgent.Result structuredRequest) throws Exception {
        // Temporarily disabled due to ClassCastException
        /*
        String keywordsBlacklist = getKeywordsBlacklist(expertParams.getKeywordsBlacklist());
        int confluenceLimit = expertParams.getConfluenceLimit();
        int confluenceIterations = expertParams.getConfluenceIterations();
        List<ChunkPreparation.Chunk> chunks = confluenceSearchOrchestrator.run(structuredRequest.toString(), keywordsBlacklist, confluenceLimit, confluenceIterations);
        saveAndAttachStats(ticketKey, chunks, confluenceSearchOrchestrator);
        return chunks;
        */
        return new ArrayList<>(); // Return empty list temporarily
    }

    private List<ChunkPreparation.Chunk> extendContextWithTracker(String ticketKey, ExpertParams expertParams, RequestDecompositionAgent.Result structuredRequest) throws Exception {
        String keywordsBlacklist = getKeywordsBlacklist(expertParams.getKeywordsBlacklist());
        int trackerLimit = expertParams.getTrackerLimit();
        int trackerIterations = expertParams.getTrackerIterations();
        List<ChunkPreparation.Chunk> response = trackerSearchOrchestrator.run(structuredRequest.toString(), keywordsBlacklist, trackerLimit, trackerIterations);
        saveAndAttachStats(ticketKey, response, trackerSearchOrchestrator);
        return response;
    }



}