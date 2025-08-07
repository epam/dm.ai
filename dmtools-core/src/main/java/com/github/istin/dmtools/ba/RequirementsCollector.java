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
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.documentation.DocumentationEditor;
import com.github.istin.dmtools.documentation.area.TicketDocumentationHistoryTrackerViaConfluence;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class RequirementsCollector extends AbstractJob<RequirementsCollectorParams, List<ResultItem>> {
    private static final Logger logger = LogManager.getLogger(RequirementsCollector.class);
    @Override
    public List<ResultItem> runJob(RequirementsCollectorParams params) throws Exception {
        return runJob(params.getRoleSpecific(), params.getProjectSpecific(), params.getInputJQL(), params.getExcludeJQL(), params.getLabelNameToMarkAsReviewed(), params.getEachPagePrefix());
    }

    public static List<ResultItem> runJob(String roleSpecific, String projectSpecific, String storiesJql, String excludeJQL, String labelNameToMarkAsReviewed, String eachPagePrefix) throws Exception {
        TrackerClient<? extends ITicket> trackerClient = BasicJiraClient.getInstance();
        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI ai = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();
        JAssistant jAssistant = new JAssistant(trackerClient, null, ai, promptManager);
        List<ResultItem> results = new ArrayList<>();
        trackerClient.searchAndPerform(new JiraClient.Performer() {
            @Override
            public boolean perform(ITicket ticket) throws Exception {
                TicketContext ticketContext = new TicketContext(trackerClient, ticket);
                ticketContext.prepareContext();
                String jqlToSearch = jAssistant.buildJQLForContent(trackerClient, roleSpecific, projectSpecific, ticketContext);
                logger.info(jqlToSearch);
                String researchPageName = makeSearchAndCollectRequirementsToPage(trackerClient, jAssistant, jqlToSearch, excludeJQL, ticketContext, roleSpecific, projectSpecific, eachPagePrefix);
                BasicConfluence confluence = BasicConfluence.getInstance();
                String viewUrl = confluence.findContent(researchPageName).getViewUrl(confluence.getBasePath());
                trackerClient.postCommentIfNotExists(ticket.getKey(), "Research in Existing Jira Tickets Was Done " + viewUrl);
                trackerClient.addLabelIfNotExists(ticket, labelNameToMarkAsReviewed);
                results.add(new ResultItem(ticket.getKey(), viewUrl));
                return false;
            }
        }, storiesJql, trackerClient.getExtendedQueryFields());
        return results;
    }

    protected static String makeSearchAndCollectRequirementsToPage(TrackerClient<? extends ITicket> trackerClient, JAssistant jAssistant, String jqlToSearch, String excludeJQL, TicketContext featureContext, String roleSpecific, String projectSpecific, String eachPagePrefix) throws Exception {
        List<Key> keys = new ArrayList<>();
        keys.add(featureContext.getTicket());
        keys.addAll(featureContext.getExtraTickets());
        String jqlKeysNotIn = JiraClient.buildNotInJQLByKeys(keys);
        jqlToSearch = "(" + jqlToSearch + ") and " + jqlKeysNotIn ;
        if (excludeJQL != null && !excludeJQL.isEmpty()) {
            jqlToSearch += " and " + excludeJQL;
        }

        trackerClient.postCommentIfNotExists(featureContext.getTicket().getTicketKey(), "Research query: " + trackerClient.buildUrlToSearch(jqlToSearch));

        List<ITicket> relatedTickets = new ArrayList<>();
        final int[] i = {0};

        List<ITicket> ticketsToCheck = new ArrayList<>();

        trackerClient.searchAndPerform(new JiraClient.Performer() {


            @Override
            public boolean perform(ITicket ticket) throws Exception {
                logger.info("Progress : {}", i[0]);
                ticketsToCheck.add(ticket);
                if (ticketsToCheck.size() == 50) {
                    List<ITicket> tickets = jAssistant.checkSimilarTickets(roleSpecific, ticketsToCheck, false, featureContext);
                    relatedTickets.addAll(tickets);
                    ticketsToCheck.clear();
                }
                i[0]++;
                return false;
            }
        }, jqlToSearch, trackerClient.getExtendedQueryFields());

        if (!ticketsToCheck.isEmpty()) {
            List<ITicket> tickets = jAssistant.checkSimilarTickets(roleSpecific, ticketsToCheck, false, featureContext);
            relatedTickets.addAll(tickets);
        }

        BasicConfluence confluence = BasicConfluence.getInstance();
        DocumentationEditor documentationEditor = new DocumentationEditor(jAssistant, trackerClient, confluence, eachPagePrefix);
        Content rootContent = confluence.findContent(eachPagePrefix);
        TicketDocumentationHistoryTrackerViaConfluence ticketDocumentationHistoryTrackerViaConfluence = new TicketDocumentationHistoryTrackerViaConfluence(confluence);
        String researchPageName = eachPagePrefix + " " + featureContext.getTicket().getKey();

        List<ITicket> finalList = new ArrayList<>();
        for (ITicket relatedTicket :relatedTickets) {
            boolean isRelated = jAssistant.baIsTicketRelatedToContent(trackerClient, roleSpecific, projectSpecific, featureContext, relatedTicket);
            if (isRelated) {
                finalList.add(relatedTicket);
            }
        }

        String searchQuery = JiraClient.buildJQLByKeys(finalList);
        trackerClient.postCommentIfNotExists(featureContext.getTicket().getTicketKey(), "Filtered tickets: " + trackerClient.buildUrlToSearch(searchQuery));

        if (!finalList.isEmpty()) {
            trackerClient.searchAndPerform(new JiraClient.Performer() {
                @Override
                public boolean perform(ITicket content) throws Exception {
                    documentationEditor.extendDocumentationPageWithTicket(confluence, ticketDocumentationHistoryTrackerViaConfluence, content, researchPageName, rootContent, source ->
                            jAssistant.buildPageWithRequirementsForInputData(featureContext, roleSpecific, projectSpecific, source, content));
                    return false;
                }
            }, searchQuery, trackerClient.getExtendedQueryFields());
        }
        return researchPageName;
    }

    @Override
    public AI getAi() {
        return null;
    }
}
