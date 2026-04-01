// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.ContentUtils;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.common.model.TicketLink;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.documentation.area.ITicketDocumentationHistoryTracker;
import com.github.istin.dmtools.documentation.area.KeyAreaMapper;
import com.github.istin.dmtools.documentation.area.KeyAreaMapperViaConfluence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class DocumentationEditor {
    private static final Logger logger = LogManager.getLogger(DocumentationEditor.class);
    private final JAssistant jAssistant;
    private final TrackerClient<? extends ITicket> tracker;
    private final String areaPrefix;
    private final BasicConfluence confluence;

    public DocumentationEditor(JAssistant jAssistant, TrackerClient<? extends ITicket> tracker, BasicConfluence confluence, String areaPrefix) {
        this.jAssistant = jAssistant;
        this.tracker = tracker;
        this.confluence = confluence;
        this.areaPrefix = areaPrefix;
    }

    public JSONArray buildDraftFeatureAreasByStories(List<? extends ITicket> ticketsAsInput) throws Exception {
        int i = 0;

        JSONArray areas = new JSONArray();
        for (ITicket story : ticketsAsInput) {
            try {
                String area = jAssistant.whatIsFeatureAreaOfStory(story);
                if (!areas.toString().contains(area)) {
                    areas.put(area);
                }
            } finally {
                i++;
                logger.info("reviewed {} from {}", i, ticketsAsInput.size());
            }

        }
        return areas;
    }

    public JSONArray buildDraftFeatureAreasByDataInput(List<? extends ToText> textsAsInput, JSONArray existingAreas) throws Exception {
        JSONArray areas = existingAreas == null ? new JSONArray() : existingAreas;
        for (ToText inputText : textsAsInput) {
            JSONArray recognizedAreas = jAssistant.whatIsFeatureAreasOfDataInput(inputText);
            for (int i = 0; i < recognizedAreas.length(); i++) {
                String recognizedArea = recognizedAreas.getString(i);
                boolean alreadyExists = false;
                for (int j = 0; j < areas.length(); j++) {
                    String existingArea = areas.getString(j);
                    if (existingArea.equalsIgnoreCase(recognizedArea)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (!alreadyExists) {
                    areas.put(recognizedArea);
                }
            }
        }
        return areas;
    }

    public <T  extends Key & ToText & TicketLink> void markTicketsByArea(List<T> ticketsAsInput, JSONArray topAreas, boolean isAllowedToIntroduceNewArea) throws Exception {
        int i = 0;
        Map<String, List<T>> storiesPerArea = new HashMap<>();

        Set<String> areas = new HashSet<>();
        for (T story : ticketsAsInput) {
            try {
                String area = null;
//                String areaFromComments = TrackerClient.Utils.checkCommentStartedWith(tracker, story.getKey(), story, areaPrefix +":");
//                if (areaFromComments != null) {
//                    area = areaFromComments.split(areaPrefix + ":")[1].replaceAll("</p>", "").trim();
//                } else {
                if (isAllowedToIntroduceNewArea) {
                    area = jAssistant.whatIsFeatureAreaOfStory(story);
                } else {
                    area = jAssistant.chooseFeatureAreaForStory(story, topAreas.toString());
                }
//                    tracker.postCommentIfNotExists(story.getKey(), areaPrefix + ":" + area);
//                    tracker.addLabelIfNotExists(story, areaPrefix);
//                    tracker.addLabelIfNotExists(story, area);
//                }
                areas.add(area);
                try {
                    topAreas = new JSONArray(area);
                } catch (Exception ignored) {
                    if (!topAreas.toString().toLowerCase().contains(area.toLowerCase())) {
                        topAreas.put(new JSONArray().put(area));
                    }
                }

                logger.info(area);

                List<T> storiesFromArea = storiesPerArea.getOrDefault(area, new ArrayList<>());
                storiesFromArea.add(story);
                storiesPerArea.put(area, storiesFromArea);
            } finally {
                i++;
                logger.info("reviewed {} from {}", i, ticketsAsInput.size());
            }

        }

        logger.info(areas);
        for (int j = 0; j < topAreas.length(); j++) {
            JSONArray areasAndSubAreas = topAreas.getJSONArray(j);
            String area = areasAndSubAreas.getString(0);
            if (areasAndSubAreas.length() > 1) {
                JSONArray subAreas = areasAndSubAreas.getJSONArray(1);
                for (int z = 0; z < subAreas.length(); z++) {
                    area = subAreas.getString(z);
                    printTicketsPerArea(storiesPerArea, area);
                }
            } else {
                printTicketsPerArea(storiesPerArea, area);
            }
        }
    }

    private static <T  extends Key & ToText & TicketLink> void printTicketsPerArea(Map<String, List<T>> storiesPerArea, String area) throws IOException {
        List<T> list = storiesPerArea.get(area);
        if (list != null) {
            logger.info(area);
            for (T ticket : list) {
                logger.info("      {}", ticket.getTicketLink());
            }
        }
    }

    public JSONObject createFeatureAreasTree(JSONArray areas) throws Exception {
        return jAssistant.createFeatureAreasTree(areas.toString());
    }

    public JSONArray cleanFeatureAreas(JSONArray areas) throws Exception {
        return jAssistant.cleanFeatureAreas(areas.toString());
    }

    public <T  extends Key & ToText & TicketLink> void buildConfluenceStructure(JSONObject featureAreas, List<T> ticketsAsInput, String rootContent, BasicConfluence confluence, KeyAreaMapper ticketAreaMapper) throws Exception {
        Content root = confluence.findContent(rootContent);
        Map<String, List<T>> storiesPerArea = new HashMap<>();
        int i = 0;
        for (T story : ticketsAsInput) {
            try {
                String area = ticketAreaMapper.getAreaForTicket(story);
                if (area == null || area.isEmpty()) {
                    area = jAssistant.chooseFeatureAreaForStory(story, featureAreas.toString());
                    ticketAreaMapper.setAreaForTicket(story, area);
                }
                List<T> storiesFromArea = storiesPerArea.getOrDefault(area, new ArrayList<>());
                storiesFromArea.add(story);
                storiesPerArea.put(area, storiesFromArea);

            } finally {
                i++;
                logger.info("reviewed {} from {}", i, ticketsAsInput.size());
            }

        }

        for (String area : featureAreas.keySet()) {
            logger.info("Area: {}", area);
            JSONObject subAreas = featureAreas.getJSONObject(area);
            Set<String> subAreasKeys = subAreas.keySet();
            if (subAreasKeys.isEmpty()) {
                List<T> list = storiesPerArea.get(area);
                if (list != null && !list.isEmpty()) {
                    confluence.findOrCreate(areaPrefix + " " + area, root.getId(), "");
                    printTicketsPerArea(storiesPerArea, area);
                }
            } else {
                for (String subArea : subAreasKeys) {
                    List<T> rallyIssues = storiesPerArea.get(subArea);
                    if (rallyIssues != null && !rallyIssues.isEmpty()) {
                        Content mainArea = confluence.findOrCreate(areaPrefix + " " + area, root.getId(), "");
                        confluence.findOrCreate(areaPrefix + " " + subArea, mainArea.getId(), "");
                        printTicketsPerArea(storiesPerArea, subArea);
                        logger.info("  SubArea: {}", subArea);
                    }
                }
            }
        }

    }

    public <T extends ITicket> void buildPagesForTickets(List<T> tickets, String prefix, String rootPage, BasicConfluence confluence, KeyAreaMapper ticketAreaMapper, ITicketDocumentationHistoryTracker ticketDocumentationHistoryTracker, boolean isSkipTechnicalDetails) throws Exception {
        Content rootContent = confluence.findContent(rootPage);
        int i = 0;
        for (T ticket : tickets) {
            String areaForTicket = ticketAreaMapper.getAreaForTicket(ticket);
            if (areaForTicket != null && !areaForTicket.isEmpty()) {
                String pageName = prefix + " " + areaForTicket;
                TicketContext ticketContext = new TicketContext(tracker, ticket);
                ticketContext.prepareContext();
                extendDocumentationPageWithTicket(confluence, ticketDocumentationHistoryTracker, ticket, pageName, rootContent, source -> isSkipTechnicalDetails ?
                        jAssistant.buildNiceLookingDocumentationForStory(ticketContext, source)
                        :
                        jAssistant.buildNiceLookingDocumentationForStoryWithTechnicalDetails(ticketContext, source));
            }
            logger.info(" ========= Progress {} from {}", i, tickets.size());
            i++;
        }
    }

    public <T extends Key & TicketLink & ToText> void buildPagesForOtherAnyContent(List<T> inputs, String prefix, String rootPage, BasicConfluence confluence, KeyAreaMapper ticketAreaMapper, ITicketDocumentationHistoryTracker ticketDocumentationHistoryTracker, boolean isSkipTechnicalDetails) throws Exception {
        Content rootContent = confluence.findContent(rootPage);
        int i = 0;
        for (T input : inputs) {
            String areaForTicket = ticketAreaMapper.getAreaForTicket(input);
            if (areaForTicket != null && !areaForTicket.isEmpty()) {
                String pageName = prefix + " " + areaForTicket;
                extendDocumentationPageWithTicket(confluence, ticketDocumentationHistoryTracker, input, pageName, rootContent, source -> isSkipTechnicalDetails ?
                        jAssistant.buildNiceLookingDocumentationForStory(input, source)
                        :
                        jAssistant.buildNiceLookingDocumentationForStoryWithTechnicalDetails(input, source));
            }
            logger.info(" ========= Progress {} from {}", i, inputs.size());
            i++;
        }
    }

    public void buildDetailedPageWithRequirementsForInputData(List<? extends ITicket> tickets, String prefix, String rootPage, BasicConfluence confluence, KeyAreaMapper ticketAreaMapper, ITicketDocumentationHistoryTracker ticketDocumentationHistoryTracker, boolean isSkipTechnicalDetails) throws Exception {
        Content rootContent = confluence.findContent(rootPage);
        int i = 0;
        for (ITicket ticket : tickets) {
            String areaForTicket = ticketAreaMapper.getAreaForTicket(ticket);
            if (areaForTicket != null && !areaForTicket.isEmpty()) {
                String pageName = prefix + " " + areaForTicket;
                extendDocumentationPageWithTicket(confluence, ticketDocumentationHistoryTracker, ticket, pageName, rootContent, source ->
                        jAssistant.buildDetailedPageWithRequirementsForInputData(ticket, source));
            }
            logger.info(" ========= Progress {} from {}", i, tickets.size());
            i++;
        }
    }

    public interface DelegateToAI {
        String askAI(String source) throws Exception;
    }

    public <T extends Key & TicketLink> void extendDocumentationPageWithTicket(BasicConfluence confluence, ITicketDocumentationHistoryTracker ticketDocumentationHistoryTracker, T keyTicketLink, String pageName, Content rootContent, DelegateToAI delegateToAI) throws Exception {
        boolean ticketWasAddedToPage = ticketDocumentationHistoryTracker.isTicketWasAddedToPage(keyTicketLink, pageName);
        if (!ticketWasAddedToPage) {
            Content content = confluence.findOrCreate(pageName, rootContent.getId(), "");
            String newPageSource = delegateToAI.askAI(content.getStorage().getValue());
            ConversationObserver conversationObserver = jAssistant.getConversationObserver();
            if (conversationObserver != null) {
                conversationObserver.printAndClear();
            }
            String parentId = content.getParentId();
            confluence.updatePage(content.getId(), content.getTitle(), parentId, newPageSource, confluence.getDefaultSpace(), keyTicketLink.getKey() + " " + keyTicketLink.getTicketLink());
            ticketDocumentationHistoryTracker.addTicketToPageHistory(keyTicketLink, pageName);
        }
    }

    public JSONObject buildExistingAreasStructureForConfluence(String prefix, String rootPage) throws IOException {
        List<Content> results = confluence.getChildrenOfContentByName(rootPage);
        JSONObject jsonObject = new JSONObject();
        for (Content content : results) {
            logger.info(content.getTitle());
            if (content.getTitle().contains(KeyAreaMapperViaConfluence.TICKET_TO_AREA_MAPPING)) {
                continue;
            }

            JSONObject children = new JSONObject();
            List<Content> childrenOfContent = confluence.getChildrenOfContentById(content.getId());
            for (Content child : childrenOfContent) {
                String title = child.getTitle();
                logger.info(title);
                children.put(adoptTitle(prefix, title), new JSONObject());
            }
            jsonObject.put(adoptTitle(prefix, content.getTitle()), children);
        }
        return jsonObject;
    }

    private static @NotNull String adoptTitle(String prefix, String title) {
        if (prefix == null || prefix.isEmpty()) {
            return title;
        }
        return title.replaceAll(prefix + " ", "");
    }

    public void attachImagesForPage(String pageTitle, ContentUtils.UrlToImageFile... urlToImageFiles) throws Exception {
        Content content = confluence.findContent(pageTitle);
        String value = content.getStorage().getValue();
        String convertedBody = ContentUtils.convertLinksToImages(confluence, content, urlToImageFiles);
        if (!value.equals(convertedBody)) {
            confluence.updatePage(content.getId(), content.getTitle(), content.getParentId(), convertedBody, confluence.getDefaultSpace(), "images attached to the page");
        }
    }
}
