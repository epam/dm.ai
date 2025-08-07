package com.github.istin.dmtools.ba;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class BusinessAnalyticDORGeneration extends AbstractJob<BusinessAnalyticDORGenerationParams, List<ResultItem>> {
    private static final Logger logger = LogManager.getLogger(BusinessAnalyticDORGeneration.class);
    @Override
    public List<ResultItem> runJob(BusinessAnalyticDORGenerationParams params) throws Exception {
        return runJob(params.getOutputConfluencePage(), params.getInputJQL(), params.getInitiator());
    }

    public static List<ResultItem> runJob(String outputConfluencePage, String inputJQL, String initiator) throws Exception {
        TrackerClient<? extends ITicket> trackerClient = BasicJiraClient.getInstance();
        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI ai = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();
        JAssistant jAssistant = new JAssistant(trackerClient, null, ai, promptManager);
        List<ResultItem> resultItems = new ArrayList<>();
        trackerClient.searchAndPerform(new JiraClient.Performer() {
            @Override
            public boolean perform(ITicket ticket) throws Exception {
                TicketContext ticketContext = new TicketContext(trackerClient, ticket);
                ticketContext.prepareContext();

                BasicConfluence confluence = BasicConfluence.getInstance();
                Content content = confluence.findContent(outputConfluencePage);
                String output = jAssistant.buildDORGenerationForStory(ticketContext, content.getStorage().getValue());
                confluence.updatePage(content.getId(), content.getTitle(), content.getParentId(), output, confluence.getDefaultSpace(), ticket.getKey() + " " + ticket.getTicketLink());
                resultItems.add(new ResultItem(ticket.getKey(), output));
                return false;
            }
        }, inputJQL, trackerClient.getExtendedQueryFields());
        return resultItems;
    }

    @Override
    public AI getAi() {
        return null;
    }
}
