package com.github.istin.dmtools.documentation;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.utils.ChangelogAssessment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.documentation.area.KeyAreaMapperViaConfluence;
import com.github.istin.dmtools.documentation.area.TicketDocumentationHistoryTrackerViaConfluence;
import com.github.istin.dmtools.figma.BasicFigmaClient;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import com.github.istin.dmtools.report.model.KeyTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DocumentationGenerator extends AbstractJob<DocumentationGeneratorParams, JSONObject> {
    private static final Logger logger = LogManager.getLogger(DocumentationGenerator.class);
    public static String NAME = "DocumentationEditor";

    @Override
    public JSONObject runJob(DocumentationGeneratorParams params) throws Exception {
        return runJob(params.getConfluenceRootPage(), params.getEachPagePrefix(), params.getJQL(), params.getListOfStatusesToSort(), params.isReadFeatureAreasFromConfluenceRootPage());
    }

    public static JSONObject runJob(String confluenceRootPage, String eachPagePrefix, String jql, String[] listOfStatusesToSort, boolean isReadFeatureAreasFromConfluenceRootPage) throws Exception {
        ConversationObserver conversationObserver = new ConversationObserver();
        BasicDialAI ai = new BasicDialAI(conversationObserver);
        PromptManager promptManager = new PromptManager();
        TrackerClient tracker = BasicJiraClient.getInstance();
        BasicConfluence confluence = BasicConfluence.getInstance();
        FigmaClient figma = BasicFigmaClient.getInstance();

        List<? extends ITicket> tickets = retrieveTickets(tracker, jql, listOfStatusesToSort);

        JAssistant jAssistant = new JAssistant(tracker, null, ai, promptManager);

        KeyAreaMapperViaConfluence ticketAreaMapper = new KeyAreaMapperViaConfluence(eachPagePrefix, confluenceRootPage, confluence);
        DocumentationEditor documentationEditor = new DocumentationEditor(jAssistant, tracker,  confluence, eachPagePrefix);

        JSONObject optimizedFeatureAreas;
        if (!isReadFeatureAreasFromConfluenceRootPage) {
            JSONArray areas = documentationEditor.buildDraftFeatureAreasByStories(tickets);
            areas = documentationEditor.cleanFeatureAreas(areas);
            System.out.println(areas);
            optimizedFeatureAreas = documentationEditor.createFeatureAreasTree(areas);
        } else {
            optimizedFeatureAreas = documentationEditor.buildExistingAreasStructureForConfluence(eachPagePrefix, confluenceRootPage);
        }

        documentationEditor.buildConfluenceStructure(optimizedFeatureAreas, tickets, confluenceRootPage, confluence, ticketAreaMapper);
        System.out.println(optimizedFeatureAreas);

        TicketDocumentationHistoryTrackerViaConfluence ticketDocumentationHistoryTrackerViaConfluence = new TicketDocumentationHistoryTrackerViaConfluence(confluence);
        documentationEditor.buildPagesForTickets(tickets, eachPagePrefix, confluenceRootPage, confluence, ticketAreaMapper, ticketDocumentationHistoryTrackerViaConfluence, false);

        JSONObject jsonObject = documentationEditor.buildExistingAreasStructureForConfluence("", confluenceRootPage);
        Set<String> keys = jsonObject.keySet();
        for (String key : keys) {
            logger.info(key);
            JSONObject children = jsonObject.getJSONObject(key);
            Set<String> childrenKeys = children.keySet();
            for (String childKey : childrenKeys) {
                logger.info("==={}", childKey);
                documentationEditor.attachImagesForPage(childKey, tracker, figma);
            }
        }
        return optimizedFeatureAreas;
    }

    public static @NotNull List<? extends ITicket> retrieveTickets(TrackerClient tracker, String jql, String[] listOfStatusesToSort) throws Exception {
        List<? extends ITicket> list = tracker.searchAndPerform(jql, tracker.getExtendedQueryFields());
        return list.stream().sorted(Comparator.comparingLong(ticket -> {
                    try {
                        List<KeyTime> datesWhenTicketWasInStatus = ChangelogAssessment.findDatesWhenTicketWasInStatus(tracker, ticket.getKey(), ticket, listOfStatusesToSort);
                        if (datesWhenTicketWasInStatus == null || datesWhenTicketWasInStatus.isEmpty()) {
                            return ticket.getUpdatedAsMillis();
                        }
                        Calendar when = datesWhenTicketWasInStatus.get(datesWhenTicketWasInStatus.size() - 1).getWhen();
                        return when.getTimeInMillis();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .collect(Collectors.toList());
    }

    public static @NotNull List<Content> retrievePages(Confluence confluence, String... urls) throws Exception {
        List<Content> contents = confluence.contentsByUrls(urls);
        return contents.stream().sorted(Comparator.comparingLong(content -> content.getLastModifiedDate().getTime()))
                .collect(Collectors.toList());
    }

    @Override
    public AI getAi() {
        return null;
    }
}
