package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import com.github.istin.dmtools.report.freemarker.GenericCell;
import com.github.istin.dmtools.report.freemarker.GenericReport;
import com.github.istin.dmtools.report.freemarker.GenericRow;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class CodeGenerator extends AbstractJob<CodeGeneratorParams, List<ResultItem>> {

    @Override
    public List<ResultItem> runJob(CodeGeneratorParams params) throws Exception {
        return runJob(params.getConfluenceRootPage(), params.getEachPagePrefix(), params.getInputJQL(), params.getInitiator(), params.getRole(), params.getSources());
    }

    @Override
    public AI getAi() {
        return null;
    }

    public static List<ResultItem> runJob(String confluenceRootPage, String eachPagePrefix, String inputJQL, String initiator, String role, JSONArray sources) throws Exception {
        BasicConfluence confluence = BasicConfluence.getInstance();

        TrackerClient<? extends ITicket> trackerClient = BasicJiraClient.getInstance();
        List<SourceCode> basicSourceCodes = SourceCode.Impl.getConfiguredSourceCodes(sources);
        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI ai = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();

        JAssistant jAssistant = new JAssistant(trackerClient, basicSourceCodes, ai, promptManager);

        List<ResultItem> resultItems = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext();
            String response = generateCode(confluenceRootPage, eachPagePrefix, role, ticketContext, jAssistant, conversationObserver, confluence, basicSourceCodes);
            trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(initiator) + ", code is prepared.");
            resultItems.add(new ResultItem(ticket.getKey(), response));
            return false;
        }, inputJQL, trackerClient.getExtendedQueryFields());
        return resultItems;
    }

    public static String generateCode(String confluenceRootPage, String eachPagePrefix, String role, TicketContext ticketContext, JAssistant jAssistant, ConversationObserver conversationObserver, BasicConfluence confluence, List<SourceCode> basicSourceCode) throws Exception {
        String response = jAssistant.generateCode(role, ticketContext);
        List<ConversationObserver.Message> messages = conversationObserver.getMessages();
        if (confluenceRootPage == null || eachPagePrefix == null || confluenceRootPage.isEmpty() || eachPagePrefix.isEmpty()) {
            messages.clear();
            return "";
        }

        if (!messages.isEmpty()) {
            GenericReport genericReport = new GenericReport();
            genericReport.setIsNotWiki(false);
            genericReport.setName(eachPagePrefix + " " + ticketContext.getTicket().getKey());
            for (ConversationObserver.Message message : messages) {
                GenericRow row = new GenericRow(false);
                row.getCells().add(new GenericCell("<b>" + message.getAuthor() + "</b>"));
                row.getCells().add(new GenericCell(message.getText()));
                genericReport.getRows().add(row);
            }
            conversationObserver.printAndClear();
            confluence.publishPageToDefaultSpace(confluenceRootPage, eachPagePrefix, genericReport);
        }
        return response;
    }
}
