package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IBody;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.di.CommitsTriageComponent;
import com.github.istin.dmtools.di.DaggerCommitsTriageComponent;
import com.github.istin.dmtools.di.SourceCodeFactory;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.prompt.Prompt;
import com.github.istin.dmtools.prompt.PromptContext;
import kotlin.Pair;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class CommitsTriage extends AbstractJob<CommitsTriageParams, List<ResultItem>> {

    private static final Logger logger = LogManager.getLogger(CommitsTriage.class);

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Inject
    @Getter
    AI ai;

    @Inject
    IPromptTemplateReader promptTemplateReader;

    @Inject
    SourceCodeFactory sourceCodeFactory;

    private CommitsTriageComponent commitsTriageComponent;

    public CommitsTriage() {
        // Lazy initialization - will be initialized in initializeStandalone()
        // This prevents Dagger initialization during static class loading (e.g., in JobRunner.JOBS)
    }

    @Override
    protected void initializeStandalone() {
        logger.info("Initializing CommitsTriage in STANDALONE mode");
        
        if (commitsTriageComponent == null) {
            logger.info("Creating new DaggerCommitsTriageComponent for standalone mode");
            commitsTriageComponent = DaggerCommitsTriageComponent.create();
        }
        
        logger.info("Injecting dependencies using CommitsTriageComponent");
        commitsTriageComponent.inject(this);
        
        logger.info("CommitsTriage standalone initialization completed - AI type: {}", 
                   (ai != null ? ai.getClass().getSimpleName() : "null"));
    }

    @Override
    public List<ResultItem> runJob(CommitsTriageParams params) throws Exception {
        SourceCode sourceCode = sourceCodeFactory.createSourceCodes(params.getSourceType());
        List<ResultItem> results = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext();

            PromptContext context = new PromptContext(params);
            context.set("ticket", ticketContext);
            String triageResults = triage(sourceCode, params, context);
            trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(params.getInitiator()) + ", below results of triage. \n" + triageResults);
            results.add(new ResultItem(ticket.getKey(), triageResults));
            return false;
        }, params.getInputJQL(), trackerClient.getExtendedQueryFields());
        return results;
    }

    public String triage(SourceCode sourceCode, CommitsTriageParams params, PromptContext context) throws Exception {
        List<ICommit> branchCommits = sourceCode.getCommitsFromBranch(params.getWorkspace(),
                params.getRepo(), params.getBranch(), params.getStartDate(), params.getEndDate());
        List<Pair<ICommit,String>> potentialCommits = new ArrayList<>();
        StringBuilder finalResponse = new StringBuilder();
        for (ICommit commit : branchCommits) {

            IBody commitDiff = sourceCode.getCommitDiff(params.getWorkspace(),
                    params.getRepo(), commit.getHash());

            String body = commitDiff.getBody();
            if (body.isEmpty()) {
                continue;
            }
            context.set("diff", body);
            String devCommitsTriagePrompt = new Prompt(promptTemplateReader, "developer_commits_triage", context).prepare();
            String response = ai.chat(params.getModel(), devCommitsTriagePrompt, context.getFiles());
            try {
                if (AIResponseParser.parseBooleanResponse(response)) {
                    potentialCommits.add(new Pair<>(commit, response));
                }
            } catch (IllegalArgumentException e) {
                // just skip it
            }
        }
        for (Pair<ICommit,String> pair :  potentialCommits) {
            ICommit commit = pair.component1();
            String aiResponse = pair.component2();
            finalResponse.append(DateUtils.formatToJiraDate(commit.getCommiterTimestamp()));
            finalResponse.append("\n");
            finalResponse.append(commit.getMessage());
            finalResponse.append("\n");
            finalResponse.append(commit.getHash());
            finalResponse.append("\n");
            finalResponse.append(aiResponse);
            finalResponse.append("\n");
        }
        return finalResponse.toString();
    }
}
