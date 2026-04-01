// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation;

import com.github.istin.dmtools.ai.JAssistant;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class DocumentationEditorTest {

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

    @Test
    public void testBuildDraftFeatureAreasByStories() throws Exception {
        List<ITicket> tickets = new ArrayList<>();
        ITicket ticket = mock(ITicket.class);
        tickets.add(ticket);

        when(jAssistant.whatIsFeatureAreaOfStory(ticket)).thenReturn("Area1");

        JSONArray result = documentationEditor.buildDraftFeatureAreasByStories(tickets);

        assertNotNull(result);
        verify(jAssistant, times(1)).whatIsFeatureAreaOfStory(ticket);
    }

    @Test
    public void testBuildDraftFeatureAreasByDataInput() throws Exception {
        List<ToText> texts = new ArrayList<>();
        ToText text = mock(ToText.class);
        texts.add(text);

        JSONArray recognizedAreas = new JSONArray();
        recognizedAreas.put("Area1");
        when(jAssistant.whatIsFeatureAreasOfDataInput(text)).thenReturn(recognizedAreas);

        JSONArray result = documentationEditor.buildDraftFeatureAreasByDataInput(texts, null);

        assertNotNull(result);
        verify(jAssistant, times(1)).whatIsFeatureAreasOfDataInput(text);
    }


    @Test
    public void testCreateFeatureAreasTree() throws Exception {
        JSONArray areas = new JSONArray();
        areas.put("Area1");

        when(jAssistant.createFeatureAreasTree(areas.toString())).thenReturn(new JSONObject());

        JSONObject result = documentationEditor.createFeatureAreasTree(areas);

        assertNotNull(result);
        verify(jAssistant, times(1)).createFeatureAreasTree(areas.toString());
    }

    @Test
    public void testCleanFeatureAreas() throws Exception {
        JSONArray areas = new JSONArray();
        areas.put("Area1");

        when(jAssistant.cleanFeatureAreas(areas.toString())).thenReturn(new JSONArray());

        JSONArray result = documentationEditor.cleanFeatureAreas(areas);

        assertNotNull(result);
        verify(jAssistant, times(1)).cleanFeatureAreas(areas.toString());
    }

    @Test
    public void testBuildConfluenceStructure() throws Exception {
        JSONObject featureAreas = new JSONObject();
        List tickets = new ArrayList<>();
        Key ticket = mock(Key.class, withSettings().extraInterfaces(ToText.class, TicketLink.class));
        tickets.add(ticket);

        KeyAreaMapper ticketAreaMapper = mock(KeyAreaMapper.class);

        when(confluence.findContent(anyString())).thenReturn(mock(Content.class));
        when(ticketAreaMapper.getAreaForTicket(ticket)).thenReturn("Area1");

        documentationEditor.buildConfluenceStructure(featureAreas, tickets, "rootContent", confluence, ticketAreaMapper);

        verify(confluence, times(1)).findContent(anyString());
    }



    @Test
    public void testBuildExistingAreasStructureForConfluence() throws Exception {
        when(confluence.getChildrenOfContentByName(anyString())).thenReturn(new ArrayList<>());

        JSONObject result = documentationEditor.buildExistingAreasStructureForConfluence("prefix", "rootPage");

        assertNotNull(result);
        verify(confluence, times(1)).getChildrenOfContentByName(anyString());
    }

}