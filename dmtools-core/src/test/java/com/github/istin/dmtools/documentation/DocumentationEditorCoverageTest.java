// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.ContentUtils;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.common.model.TicketLink;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.documentation.area.ITicketDocumentationHistoryTracker;
import com.github.istin.dmtools.documentation.area.KeyAreaMapper;
import com.github.istin.dmtools.documentation.area.KeyAreaMapperViaConfluence;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DocumentationEditorCoverageTest {

    private JAssistant jAssistant;
    private TrackerClient<ITicket> tracker;
    private BasicConfluence confluence;
    private DocumentationEditor documentationEditor;

    @Before
    public void setUp() {
        jAssistant = mock(JAssistant.class);
        tracker = mock(TrackerClient.class);
        confluence = mock(BasicConfluence.class);
        documentationEditor = new DocumentationEditor(jAssistant, tracker, confluence, "areaPrefix");
    }

    private static Object newStoryMock(String key, String link) {
        Key story = mock(Key.class, withSettings().extraInterfaces(ToText.class, TicketLink.class));
        when(story.getKey()).thenReturn(key);
        when(((TicketLink) story).getTicketLink()).thenReturn(link);
        return story;
    }

    private static Content newContentMock(String id, String title, String parentId, String storageValue) {
        Content content = mock(Content.class);
        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn(storageValue);
        when(content.getStorage()).thenReturn(storage);
        when(content.getId()).thenReturn(id);
        when(content.getTitle()).thenReturn(title);
        when(content.getParentId()).thenReturn(parentId);
        return content;
    }

    // ---------------- buildDraftFeatureAreasByStories ----------------

    @Test
    public void testBuildDraftFeatureAreasByStoriesDeduplicatesAreas() throws Exception {
        ITicket ticket1 = mock(ITicket.class);
        ITicket ticket2 = mock(ITicket.class);
        when(jAssistant.whatIsFeatureAreaOfStory(ticket1)).thenReturn("Area1");
        when(jAssistant.whatIsFeatureAreaOfStory(ticket2)).thenReturn("Area1");

        JSONArray result = documentationEditor.buildDraftFeatureAreasByStories(Arrays.asList(ticket1, ticket2));

        assertEquals(1, result.length());
        assertEquals("Area1", result.getString(0));
    }

    @Test(expected = RuntimeException.class)
    public void testBuildDraftFeatureAreasByStoriesPropagatesException() throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(jAssistant.whatIsFeatureAreaOfStory(ticket)).thenThrow(new RuntimeException("AI failure"));

        documentationEditor.buildDraftFeatureAreasByStories(Arrays.asList(ticket));
    }

    // ---------------- buildDraftFeatureAreasByDataInput ----------------

    @Test
    public void testBuildDraftFeatureAreasByDataInputSkipsCaseInsensitiveDuplicates() throws Exception {
        ToText text = mock(ToText.class);
        JSONArray recognized = new JSONArray().put("area1").put("Area2");
        when(jAssistant.whatIsFeatureAreasOfDataInput(text)).thenReturn(recognized);

        JSONArray existing = new JSONArray().put("Area1");
        JSONArray result = documentationEditor.buildDraftFeatureAreasByDataInput(Arrays.asList(text), existing);

        assertSame(existing, result);
        assertEquals(2, result.length());
        assertEquals("Area1", result.getString(0));
        assertEquals("Area2", result.getString(1));
    }

    // ---------------- markTicketsByArea ----------------

    @Test
    public void testMarkTicketsByAreaWithNewAreaAllowedAndPlainArea() throws Exception {
        Object story = newStoryMock("KEY-1", "http://link/KEY-1");
        List tickets = new ArrayList();
        tickets.add(story);

        when(jAssistant.whatIsFeatureAreaOfStory(any(ToText.class))).thenReturn("Area1");

        // "Area1" is not a JSON array, so it is appended to topAreas only if missing.
        JSONArray topAreas = new JSONArray().put(new JSONArray().put("Area1"));
        documentationEditor.markTicketsByArea(tickets, topAreas, true);

        // area already present in topAreas -> not appended again
        assertEquals(1, topAreas.length());
        verify(jAssistant, times(1)).whatIsFeatureAreaOfStory(any(ToText.class));
        verify((TicketLink) story, atLeastOnce()).getTicketLink();
    }

    @Test
    public void testMarkTicketsByAreaAppendsUnknownPlainArea() throws Exception {
        Object story = newStoryMock("KEY-2", "http://link/KEY-2");
        List tickets = new ArrayList();
        tickets.add(story);

        when(jAssistant.chooseFeatureAreaForStory(any(ToText.class), anyString())).thenReturn("BrandNewArea");

        JSONArray topAreas = new JSONArray().put(new JSONArray().put("Area1"));
        documentationEditor.markTicketsByArea(tickets, topAreas, false);

        assertEquals(2, topAreas.length());
        assertEquals("BrandNewArea", topAreas.getJSONArray(1).getString(0));
    }

    @Test
    public void testMarkTicketsByAreaReplacesTopAreasWhenAreaIsJsonArray() throws Exception {
        Object story = newStoryMock("KEY-3", "http://link/KEY-3");
        List tickets = new ArrayList();
        tickets.add(story);

        // Valid JSON array returned by AI replaces topAreas; sub-areas are iterated.
        when(jAssistant.whatIsFeatureAreaOfStory(any(ToText.class)))
                .thenReturn("[[\"AreaX\", [\"SubA\", \"SubB\"]]]");

        JSONArray topAreas = new JSONArray().put(new JSONArray().put("Area1"));
        documentationEditor.markTicketsByArea(tickets, topAreas, true);

        // no exception: area was parsed as JSON and sub-areas were processed
        verify(jAssistant, times(1)).whatIsFeatureAreaOfStory(any(ToText.class));
    }

    @Test(expected = RuntimeException.class)
    public void testMarkTicketsByAreaPropagatesExceptionFromFinally() throws Exception {
        Object story = newStoryMock("KEY-4", "http://link/KEY-4");
        List tickets = new ArrayList();
        tickets.add(story);

        when(jAssistant.whatIsFeatureAreaOfStory(any(ToText.class))).thenThrow(new RuntimeException("boom"));

        documentationEditor.markTicketsByArea(tickets, new JSONArray(), true);
    }

    // ---------------- buildConfluenceStructure ----------------

    @Test
    public void testBuildConfluenceStructureChoosesAreaWhenMissing() throws Exception {
        Object story = newStoryMock("KEY-5", "http://link/KEY-5");
        List tickets = new ArrayList();
        tickets.add(story);

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(any(Key.class))).thenReturn(null);
        when(jAssistant.chooseFeatureAreaForStory(any(ToText.class), anyString())).thenReturn("Area1");

        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootContent")).thenReturn(root);

        // Area1 has no sub-areas and has stories -> a page is created
        JSONObject featureAreas = new JSONObject().put("Area1", new JSONObject());
        documentationEditor.buildConfluenceStructure(featureAreas, tickets, "rootContent", confluence, ticketAreaMapper);

        verify(ticketAreaMapper).setAreaForTicket(any(Key.class), eq("Area1"));
        verify(confluence).findOrCreate("areaPrefix Area1", "rootId", "");
        verify((TicketLink) story, atLeastOnce()).getTicketLink();
    }

    @Test
    public void testBuildConfluenceStructureWithSubAreas() throws Exception {
        Object story = newStoryMock("KEY-6", "http://link/KEY-6");
        List tickets = new ArrayList();
        tickets.add(story);

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(any(Key.class))).thenReturn("Sub1");

        Content root = newContentMock("rootId", "root", null, "");
        Content mainArea = newContentMock("mainId", "areaPrefix Area1", "rootId", "");
        when(confluence.findContent("rootContent")).thenReturn(root);
        when(confluence.findOrCreate("areaPrefix Area1", "rootId", "")).thenReturn(mainArea);

        JSONObject subAreas = new JSONObject().put("Sub1", new JSONObject());
        JSONObject featureAreas = new JSONObject().put("Area1", subAreas);
        documentationEditor.buildConfluenceStructure(featureAreas, tickets, "rootContent", confluence, ticketAreaMapper);

        verify(confluence).findOrCreate("areaPrefix Area1", "rootId", "");
        verify(confluence).findOrCreate("areaPrefix Sub1", "mainId", "");
        verify((TicketLink) story, atLeastOnce()).getTicketLink();
    }

    @Test
    public void testBuildConfluenceStructureSkipsAreasWithoutStories() throws Exception {
        List tickets = new ArrayList();

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootContent")).thenReturn(root);

        JSONObject featureAreas = new JSONObject().put("EmptyArea", new JSONObject());
        documentationEditor.buildConfluenceStructure(featureAreas, tickets, "rootContent", confluence, ticketAreaMapper);

        verify(confluence, never()).findOrCreate(anyString(), anyString(), anyString());
    }

    // ---------------- buildPagesForTickets ----------------

    @Test
    public void testBuildPagesForTicketsSkipTechnicalDetails() throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn("KEY-7");
        when(ticket.toText()).thenReturn("plain text without keys");
        when(ticket.getTicketLink()).thenReturn("http://link/KEY-7");

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(ticket)).thenReturn("Area1");

        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootPage")).thenReturn(root);

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        when(historyTracker.isTicketWasAddedToPage(ticket, "prefix Area1")).thenReturn(false);

        Content page = newContentMock("pageId", "prefix Area1", "rootId", "old source");
        when(confluence.findOrCreate("prefix Area1", "rootId", "")).thenReturn(page);
        when(confluence.getDefaultSpace()).thenReturn("SPACE");
        when(jAssistant.buildNiceLookingDocumentationForStory(any(ToText.class), eq("old source"))).thenReturn("new source");

        documentationEditor.buildPagesForTickets(Arrays.asList(ticket), "prefix", "rootPage", confluence, ticketAreaMapper, historyTracker, true);

        verify(jAssistant).buildNiceLookingDocumentationForStory(any(TicketContext.class), eq("old source"));
        verify(confluence).updatePage(eq("pageId"), eq("prefix Area1"), eq("rootId"), eq("new source"), eq("SPACE"), contains("KEY-7"));
        verify(historyTracker).addTicketToPageHistory(ticket, "prefix Area1");
    }

    @Test
    public void testBuildPagesForTicketsWithTechnicalDetails() throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn("KEY-8");
        when(ticket.toText()).thenReturn("plain text without keys");
        when(ticket.getTicketLink()).thenReturn("http://link/KEY-8");

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(ticket)).thenReturn("Area1");

        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootPage")).thenReturn(root);

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        when(historyTracker.isTicketWasAddedToPage(ticket, "prefix Area1")).thenReturn(false);

        Content page = newContentMock("pageId", "prefix Area1", "rootId", "old source");
        when(confluence.findOrCreate("prefix Area1", "rootId", "")).thenReturn(page);
        when(jAssistant.buildNiceLookingDocumentationForStoryWithTechnicalDetails(any(ToText.class), eq("old source"))).thenReturn("new source");

        documentationEditor.buildPagesForTickets(Arrays.asList(ticket), "prefix", "rootPage", confluence, ticketAreaMapper, historyTracker, false);

        verify(jAssistant).buildNiceLookingDocumentationForStoryWithTechnicalDetails(any(ToText.class), eq("old source"));
    }

    @Test
    public void testBuildPagesForTicketsSkipsTicketsWithoutArea() throws Exception {
        ITicket ticket = mock(ITicket.class);
        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(ticket)).thenReturn("");

        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootPage")).thenReturn(root);

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        documentationEditor.buildPagesForTickets(Arrays.asList(ticket), "prefix", "rootPage", confluence, ticketAreaMapper, historyTracker, true);

        verify(confluence, never()).findOrCreate(anyString(), anyString(), anyString());
        verify(jAssistant, never()).buildNiceLookingDocumentationForStory(any(ToText.class), anyString());
    }

    // ---------------- buildPagesForOtherAnyContent ----------------

    @Test
    public void testBuildPagesForOtherAnyContent() throws Exception {
        Object input = newStoryMock("KEY-9", "http://link/KEY-9");
        List inputs = new ArrayList();
        inputs.add(input);

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(any(Key.class))).thenReturn("Area1");

        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootPage")).thenReturn(root);

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        when(historyTracker.isTicketWasAddedToPage(any(TicketLink.class), eq("prefix Area1"))).thenReturn(false);

        Content page = newContentMock("pageId", "prefix Area1", "rootId", "old source");
        when(confluence.findOrCreate("prefix Area1", "rootId", "")).thenReturn(page);
        when(jAssistant.buildNiceLookingDocumentationForStory(any(ToText.class), eq("old source"))).thenReturn("new source");

        documentationEditor.buildPagesForOtherAnyContent(inputs, "prefix", "rootPage", confluence, ticketAreaMapper, historyTracker, true);

        verify(jAssistant).buildNiceLookingDocumentationForStory(any(ToText.class), eq("old source"));
        verify(historyTracker).addTicketToPageHistory(any(TicketLink.class), eq("prefix Area1"));
    }

    // ---------------- buildDetailedPageWithRequirementsForInputData ----------------

    @Test
    public void testBuildDetailedPageWithRequirementsForInputData() throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn("KEY-10");
        when(ticket.getTicketLink()).thenReturn("http://link/KEY-10");

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);
        when(ticketAreaMapper.getAreaForTicket(ticket)).thenReturn("Area1");

        Content root = newContentMock("rootId", "root", null, "");
        when(confluence.findContent("rootPage")).thenReturn(root);

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        when(historyTracker.isTicketWasAddedToPage(ticket, "prefix Area1")).thenReturn(false);

        Content page = newContentMock("pageId", "prefix Area1", "rootId", "old source");
        when(confluence.findOrCreate("prefix Area1", "rootId", "")).thenReturn(page);
        when(jAssistant.buildDetailedPageWithRequirementsForInputData(any(ToText.class), eq("old source"))).thenReturn("detailed source");

        documentationEditor.buildDetailedPageWithRequirementsForInputData(Arrays.asList(ticket), "prefix", "rootPage", confluence, ticketAreaMapper, historyTracker, false);

        verify(jAssistant).buildDetailedPageWithRequirementsForInputData(any(ToText.class), eq("old source"));
        verify(confluence).updatePage(eq("pageId"), eq("prefix Area1"), eq("rootId"), eq("detailed source"), any(), contains("KEY-10"));
    }

    // ---------------- extendDocumentationPageWithTicket ----------------

    @Test
    public void testExtendDocumentationPageWithTicketSkipsAlreadyTrackedTicket() throws Exception {
        ITicket ticket = mock(ITicket.class);
        Content root = newContentMock("rootId", "root", null, "");

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        when(historyTracker.isTicketWasAddedToPage(ticket, "pageName")).thenReturn(true);

        documentationEditor.extendDocumentationPageWithTicket(confluence, historyTracker, ticket, "pageName", root, source -> "unused");

        verify(confluence, never()).findOrCreate(anyString(), anyString(), anyString());
        verify(confluence, never()).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(historyTracker, never()).addTicketToPageHistory(any(TicketLink.class), anyString());
    }

    @Test
    public void testExtendDocumentationPageWithTicketPrintsConversationObserver() throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getKey()).thenReturn("KEY-11");
        when(ticket.getTicketLink()).thenReturn("http://link/KEY-11");
        Content root = newContentMock("rootId", "root", null, "");

        ITicketDocumentationHistoryTracker historyTracker = mock(ITicketDocumentationHistoryTracker.class);
        when(historyTracker.isTicketWasAddedToPage(ticket, "pageName")).thenReturn(false);

        Content page = newContentMock("pageId", "pageName", "rootId", "old source");
        when(confluence.findOrCreate("pageName", "rootId", "")).thenReturn(page);
        when(confluence.getDefaultSpace()).thenReturn("SPACE");

        ConversationObserver conversationObserver = mock(ConversationObserver.class);
        when(jAssistant.getConversationObserver()).thenReturn(conversationObserver);

        documentationEditor.extendDocumentationPageWithTicket(confluence, historyTracker, ticket, "pageName", root, source -> "ai answer");

        verify(conversationObserver).printAndClear();
        verify(confluence).updatePage(eq("pageId"), eq("pageName"), eq("rootId"), eq("ai answer"), eq("SPACE"), contains("KEY-11"));
        verify(historyTracker).addTicketToPageHistory(ticket, "pageName");
    }

    // ---------------- buildExistingAreasStructureForConfluence ----------------

    @Test
    public void testBuildExistingAreasStructureForConfluenceWithChildrenAndMappingPage() throws Exception {
        Content mappingPage = newContentMock("mapId", "areaPrefix" + KeyAreaMapperViaConfluence.TICKET_TO_AREA_MAPPING, null, "");
        Content areaPage = newContentMock("areaId", "areaPrefix Area1", null, "");
        Content child1 = newContentMock("childId1", "areaPrefix Sub1", "areaId", "");
        Content child2 = newContentMock("childId2", "areaPrefix Sub2", "areaId", "");

        when(confluence.getChildrenOfContentByName("rootPage")).thenReturn(Arrays.asList(mappingPage, areaPage));
        when(confluence.getChildrenOfContentById("areaId")).thenReturn(Arrays.asList(child1, child2));

        JSONObject result = documentationEditor.buildExistingAreasStructureForConfluence("areaPrefix", "rootPage");

        assertEquals(1, result.length());
        assertTrue(result.has("Area1"));
        JSONObject children = result.getJSONObject("Area1");
        assertTrue(children.has("Sub1"));
        assertTrue(children.has("Sub2"));
        verify(confluence, never()).getChildrenOfContentById("mapId");
    }

    @Test
    public void testBuildExistingAreasStructureForConfluenceWithNullPrefix() throws Exception {
        Content areaPage = newContentMock("areaId", "Area1", null, "");
        when(confluence.getChildrenOfContentByName("rootPage")).thenReturn(Arrays.asList(areaPage));
        when(confluence.getChildrenOfContentById("areaId")).thenReturn(new ArrayList<>());

        JSONObject result = documentationEditor.buildExistingAreasStructureForConfluence(null, "rootPage");

        assertTrue(result.has("Area1"));
        assertEquals(0, result.getJSONObject("Area1").length());
    }

    // ---------------- attachImagesForPage ----------------

    @Test
    public void testAttachImagesForPageUpdatesPageWhenBodyChanged() throws Exception {
        Content content = newContentMock("pageId", "pageTitle", "parentId", "original body");
        when(confluence.findContent("pageTitle")).thenReturn(content);
        when(confluence.getDefaultSpace()).thenReturn("SPACE");

        ContentUtils.UrlToImageFile urlToImageFile = mock(ContentUtils.UrlToImageFile.class);

        try (MockedStatic<ContentUtils> contentUtils = mockStatic(ContentUtils.class)) {
            contentUtils.when(() -> ContentUtils.convertLinksToImages(eq(confluence), eq(content), any(ContentUtils.UrlToImageFile[].class)))
                    .thenReturn("converted body");

            documentationEditor.attachImagesForPage("pageTitle", urlToImageFile);
        }

        verify(confluence).updatePage("pageId", "pageTitle", "parentId", "converted body", "SPACE", "images attached to the page");
    }

    @Test
    public void testAttachImagesForPageSkipsUpdateWhenBodyUnchanged() throws Exception {
        Content content = newContentMock("pageId", "pageTitle", "parentId", "same body");
        when(confluence.findContent("pageTitle")).thenReturn(content);

        ContentUtils.UrlToImageFile urlToImageFile = mock(ContentUtils.UrlToImageFile.class);

        try (MockedStatic<ContentUtils> contentUtils = mockStatic(ContentUtils.class)) {
            contentUtils.when(() -> ContentUtils.convertLinksToImages(eq(confluence), eq(content), any(ContentUtils.UrlToImageFile[].class)))
                    .thenReturn("same body");

            documentationEditor.attachImagesForPage("pageTitle", urlToImageFile);
        }

        verify(confluence, never()).updatePage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
