package com.github.istin.dmtools.diagram;

import com.github.istin.dmtools.ai.*;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import lombok.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;



public class DiagramsCreator extends AbstractJob<DiagramsCreatorParams, List<DiagramsCreator.Result>> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Result {
        private String key;
        private List<Diagram> diagrams;
    }

    @Override
    public List<Result> runJob(DiagramsCreatorParams params) throws Exception {
        return runJob(params.getRoleSpecific(), params.getProjectSpecific(), params.getStoriesJql(), params.getLabelNameToMarkAsReviewed());
    }

    public static List<Result> runJob(String roleSpecific, String projectSpecific, String storiesJql, String labelNameToMarkAsReviewed) throws Exception {
        TrackerClient<? extends ITicket> trackerClient = BasicJiraClient.getInstance();
        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI dial = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();
        JAssistant jAssistant = new JAssistant(trackerClient, null, dial, promptManager);
        DiagramsDrawer diagramsDrawer = new DiagramsDrawer();
        List<Result> resultItems = new ArrayList<>();
        trackerClient.searchAndPerform(new JiraClient.Performer() {
            @Override
            public boolean perform(ITicket ticket) throws Exception {
                TicketContext ticketContext = new TicketContext(trackerClient, ticket);
                ticketContext.prepareContext();

                List<Diagram> diagrams = jAssistant.createDiagrams(ticketContext, roleSpecific, projectSpecific);
                for (Diagram diagram : diagrams) {
                    File screenshot = diagramsDrawer.draw(ticket.getTicketTitle() + "_" + diagram.getType(), diagram);
                    trackerClient.attachFileToTicket(ticket.getTicketKey(), screenshot.getName(), null, screenshot);
                    trackerClient.postCommentIfNotExists(ticket.getKey(), diagram.getType() + " \n " + diagram.getCode());

                }
                trackerClient.addLabelIfNotExists(ticket, labelNameToMarkAsReviewed);
                resultItems.add(new Result(ticket.getKey(), diagrams));
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
