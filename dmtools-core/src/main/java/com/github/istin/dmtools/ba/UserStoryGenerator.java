package com.github.istin.dmtools.ba;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import lombok.*;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UserStoryGenerator extends AbstractJob<UserStoryGeneratorParams, List<UserStoryGenerator.Result>> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Result {
        private String key;
        private JSONArray updatedStories;
        private JSONArray newStories;
        private String userStoriesAsHTML;
    }

    @Override
    public List<Result> runJob(UserStoryGeneratorParams params) throws Exception {
        return runJob(params.getInputJQL(), params.getExistingUserStoriesJql(), params.getOutputType(), params.getPriorities(), params.getInitiator(), params.getProjectCode(), params.getIssueType(), params.getAcceptanceCriteriaField(), params.getRelationship(), params.getParentField());
    }

    public static List<Result> runJob(String inputJQL, String existingUserStoriesJQL, String outputType, String priorities, String initiator, String projectCode, String issueType, String acceptanceCriteriaField, String relationship, String parentField) throws Exception {
        TrackerClient<? extends ITicket> trackerClient = BasicJiraClient.getInstance();

        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI ai = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();

        JAssistant jAssistant = new JAssistant(trackerClient, null, ai, promptManager);

        List<Result> results = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            List<? extends ITicket> existingUserStories = trackerClient.searchAndPerform(existingUserStoriesJQL, trackerClient.getExtendedQueryFields());
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.setOnTicketDetailsRequest(new TicketContext.OnTicketDetailsRequest() {
                @Override
                public ITicket getTicketDetails(String key) throws IOException {
                    for (ITicket ticket : existingUserStories) {
                        if (ticket.getKey().equalsIgnoreCase(key)) {
                            return ticket;
                        }
                    }
                    return null;
                }
            });
            ticketContext.prepareContext();
            Result result = jAssistant.generateUserStories(
                    ticketContext,
                    existingUserStories,
                    projectCode,
                    issueType,
                    acceptanceCriteriaField,
                    relationship,
                    outputType,
                    priorities, parentField
            );
            if (result.updatedStories != null) {
                String comment = trackerClient.tag(initiator) + ", user stories are generated and linked.\n\n";
                if (!result.updatedStories.isEmpty()) {
                    comment += "Following user stories are updated:";
                }
                for (int i = 0; i < result.updatedStories.length(); i++) {
                    comment += "\n" + result.updatedStories.getJSONObject(i).getString("key");
                }
                trackerClient.postCommentIfNotExists(ticket.getTicketKey(), comment);
            } else {
                trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(initiator) + ", user stories are generated and linked.");
            }
            results.add(result);
            return false;
        }, inputJQL, trackerClient.getExtendedQueryFields());
        return results;
    }

    @Override
    public AI getAi() {
        return null;
    }
}
