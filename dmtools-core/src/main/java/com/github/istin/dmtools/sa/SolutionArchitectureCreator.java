package com.github.istin.dmtools.sa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;

import java.util.ArrayList;
import java.util.List;

public class SolutionArchitectureCreator extends AbstractJob<SolutionArchitectureCreatorParams, List<ResultItem>> {

    @Override
    public List<ResultItem> runJob(SolutionArchitectureCreatorParams params) throws Exception {
        return runJob(params.getRoleSpecific(), params.getProjectSpecific(), params.getStoriesJql(), params.getLabelNameToMarkAsReviewed());
    }

    public static List<ResultItem> runJob(String roleSpecific, String projectSpecific, String storiesJql, String labelNameToMarkAsReviewed) throws Exception {
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
                String ticketDescription = ticket.getTicketDescription();

                String solution = jAssistant.createSolutionForTicket(trackerClient, roleSpecific, projectSpecific, ticketContext);
                trackerClient.updateDescription(ticket.getKey(), ticketDescription + "\n" + solution);
                trackerClient.addLabelIfNotExists(ticket, labelNameToMarkAsReviewed);
                resultItems.add(new ResultItem(ticket.getKey(), solution));
                return false;
            }
        }, storiesJql, trackerClient.getExtendedQueryFields());
        return resultItems;
    }

    @Override
    public AI getAi() {
        return null;
    }
}
