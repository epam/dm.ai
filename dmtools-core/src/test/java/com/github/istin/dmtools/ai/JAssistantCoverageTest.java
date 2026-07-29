// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.ba.UserStoryGenerator;
import com.github.istin.dmtools.ba.UserStoryGeneratorParams;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.dev.UnitTestsGeneratorParams;
import com.github.istin.dmtools.prompt.PromptManager;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Additional coverage tests for {@link JAssistant} focusing on branches
 * not exercised by {@link JAssistantTest}: user story creation/update
 * flows, story estimation and similar-ticket validation.
 */
public class JAssistantCoverageTest {

    private JAssistant jAssistant;
    private TrackerClient<ITicket> trackerClientMock;
    private List<SourceCode> sourceCodesMock;
    private AI aiClientMock;
    private PromptManager promptManagerMock;

    @Before
    public void setUp() {
        trackerClientMock = mock(TrackerClient.class);
        sourceCodesMock = new ArrayList<>();
        aiClientMock = mock(AI.class);
        promptManagerMock = mock(PromptManager.class);
        jAssistant = new JAssistant(trackerClientMock, sourceCodesMock, aiClientMock, promptManagerMock);
    }

    @Test
    public void testGenerateUserStoriesAsTrackerComment() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.getTicketKey()).thenReturn("KEY-1");
        when(ticketMock.getKey()).thenReturn("KEY-1");
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(ticketContextMock.getTicket()).thenReturn(ticketMock);
        when(trackerClientMock.getBasePath()).thenReturn("basePath");
        when(trackerClientMock.getComments(eq("KEY-1"), eq(ticketMock))).thenReturn(new ArrayList<>());
        when(promptManagerMock.generateUserStoriesAsHTML(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("HTML stories");

        UserStoryGenerator.Result result = jAssistant.generateUserStories(ticketContextMock, new ArrayList<>(),
                "PROJ", "Story", null, null,
                UserStoryGeneratorParams.OUTPUT_TYPE_TRACKER_COMMENT, "priorities", null);

        assertNotNull(result);
        assertEquals("KEY-1", result.getKey());
        assertEquals("HTML stories", result.getUserStoriesAsHTML());
        verify(trackerClientMock).postComment("KEY-1",
                JAssistant.USER_STORIES_COMMENT_PREFIX + "HTML stories");
    }

    @Test
    public void testGenerateUserStoriesSkipsWhenCommentAlreadyExists() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.getTicketKey()).thenReturn("KEY-1");
        when(ticketMock.getKey()).thenReturn("KEY-1");
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(ticketContextMock.getTicket()).thenReturn(ticketMock);
        IComment commentMock = mock(IComment.class);
        when(commentMock.getBody()).thenReturn(JAssistant.USER_STORIES_COMMENT_PREFIX + "old stories");
        doReturn(Collections.singletonList(commentMock)).when(trackerClientMock).getComments("KEY-1", ticketMock);

        UserStoryGenerator.Result result = jAssistant.generateUserStories(ticketContextMock, new ArrayList<>(),
                "PROJ", "Story", null, null,
                UserStoryGeneratorParams.OUTPUT_TYPE_TRACKER_COMMENT, "priorities", null);

        assertNull(result);
        verify(trackerClientMock, never()).postComment(anyString(), anyString());
        verifyNoInteractions(aiClientMock);
    }

    @Test
    public void testGenerateUserStoriesCreatesNewStories() throws Exception {
        JiraClient<Ticket> jiraClientMock = mock(JiraClient.class);
        JAssistant assistant = new JAssistant(jiraClientMock, new ArrayList<>(), aiClientMock, promptManagerMock);

        Ticket mainTicket = new Ticket("{\"key\":\"DMC-1\"}");
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(ticketContextMock.getTicket()).thenReturn(mainTicket);
        when(jiraClientMock.getBasePath()).thenReturn("basePath");
        when(promptManagerMock.generateUserStoriesAsJSONArray(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn(
                "[{\"summary\":\"S1\",\"description\":\"D1\",\"acceptanceCriteria\":\"AC1\",\"priority\":\"High\"}]");
        when(jiraClientMock.getFieldCustomCode(anyString(), anyString())).thenReturn("customfield_100");
        TrackerClient.TrackerTicketFields fieldsMock = mock(TrackerClient.TrackerTicketFields.class);
        when(jiraClientMock.createTicketInProject(anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    TrackerClient.FieldsInitializer initializer = invocation.getArgument(4);
                    initializer.init(fieldsMock);
                    return "{\"key\":\"DMC-100\"}";
                });

        UserStoryGenerator.Result result = assistant.generateUserStories(ticketContextMock, new ArrayList<>(),
                "DMC", "Story", "Acceptance Criteria", "relates",
                UserStoryGeneratorParams.OUTPUT_TYPE_TRACKER_CREATION, "priorities", "Epic Link");

        assertNotNull(result);
        assertEquals("DMC-1", result.getKey());
        assertEquals(1, result.getNewStories().length());
        assertNull(result.getUpdatedStories());
        verify(jiraClientMock).linkIssueWithRelationship("DMC-1", "DMC-100", "relates");
        verify(fieldsMock).set(eq("priority"), any());
        verify(fieldsMock).set(eq("labels"), any());
        verify(fieldsMock).set("customfield_100", "DMC-1");
    }

    @Test
    public void testGenerateUserStoriesUpdatesExistingStories() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.getTicketKey()).thenReturn("KEY-1");
        when(ticketMock.getKey()).thenReturn("KEY-1");
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(ticketContextMock.getTicket()).thenReturn(ticketMock);
        when(trackerClientMock.getBasePath()).thenReturn("basePath");
        when(promptManagerMock.generateUserStoriesAsJSONArray(any())).thenReturn("AI Request");
        when(promptManagerMock.updateUserStoriesAsJSONArray(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn("[]",
                "[{\"key\":\"DMC-5\",\"summary\":\"S2\",\"description\":\"D2\",\"acceptanceCriteria\":\"AC2\"}]");
        TrackerClient.TrackerTicketFields fieldsMock = mock(TrackerClient.TrackerTicketFields.class);
        when(trackerClientMock.updateTicket(eq("DMC-5"), any())).thenAnswer(invocation -> {
            TrackerClient.FieldsInitializer initializer = invocation.getArgument(1);
            initializer.init(fieldsMock);
            return null;
        });

        UserStoryGenerator.Result result = jAssistant.generateUserStories(ticketContextMock, new ArrayList<>(),
                "PROJ", "Story", null, null,
                UserStoryGeneratorParams.OUTPUT_TYPE_TRACKER_CREATION, "priorities", null);

        assertNotNull(result);
        assertEquals(0, result.getNewStories().length());
        assertEquals(1, result.getUpdatedStories().length());
        verify(fieldsMock).set("description", "D2\n\nAC2");
        verify(fieldsMock).set("summary", "S2");
    }

    @Test
    public void testGenerateUserStoriesUpdatesExistingStoriesMarkdown() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.getTicketKey()).thenReturn("KEY-1");
        when(ticketMock.getKey()).thenReturn("KEY-1");
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(ticketContextMock.getTicket()).thenReturn(ticketMock);
        when(trackerClientMock.getBasePath()).thenReturn("basePath");
        when(trackerClientMock.getTextType()).thenReturn(TrackerClient.TextType.MARKDOWN);
        when(promptManagerMock.generateUserStoriesAsJSONArray(any())).thenReturn("AI Request");
        when(promptManagerMock.updateUserStoriesAsJSONArray(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn("[]",
                "[{\"key\":\"DMC-5\",\"summary\":\"S2\",\"description\":\"D2\",\"acceptanceCriteria\":\"AC2\"}]");
        TrackerClient.TrackerTicketFields fieldsMock = mock(TrackerClient.TrackerTicketFields.class);
        when(trackerClientMock.updateTicket(eq("DMC-5"), any())).thenAnswer(invocation -> {
            TrackerClient.FieldsInitializer initializer = invocation.getArgument(1);
            initializer.init(fieldsMock);
            return null;
        });

        UserStoryGenerator.Result result = jAssistant.generateUserStories(ticketContextMock, new ArrayList<>(),
                "PROJ", "Story", "Acceptance Criteria", null,
                UserStoryGeneratorParams.OUTPUT_TYPE_TRACKER_CREATION, "priorities", null);

        assertNotNull(result);
        assertEquals(1, result.getUpdatedStories().length());
        verify(fieldsMock).set(eq("Acceptance Criteria"), any());
        verify(fieldsMock).set(eq("description"), any());
        verify(fieldsMock).set("summary", "S2");
    }

    @Test
    public void testEstimateStory() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.toText()).thenReturn("");
        when(trackerClientMock.performTicket(eq("KEY-1"), any())).thenReturn(ticketMock);
        when(promptManagerMock.checkSimilarTickets(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn("[]");
        when(promptManagerMock.estimateStory(any())).thenReturn("Final AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("The estimation is 3.5 points");

        Double result = jAssistant.estimateStory("role", "KEY-1", new ArrayList<>(), false);

        assertEquals(Double.valueOf(3.5), result);
    }

    @Test
    public void testEstimateStoryReturnsNullWhenNoNumberFound() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.toText()).thenReturn("");
        when(trackerClientMock.performTicket(eq("KEY-1"), any())).thenReturn(ticketMock);
        when(promptManagerMock.checkSimilarTickets(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn("[]");
        when(promptManagerMock.estimateStory(any())).thenReturn("Final AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("cannot estimate this story");

        Double result = jAssistant.estimateStory("role", "KEY-1", new ArrayList<>(), false);

        assertNull(result);
    }

    @Test
    public void testCheckSimilarTicketsWithDetailsValidation() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        ITicket similarTicketMock = mock(ITicket.class);
        when(similarTicketMock.getTicketTitle()).thenReturn("Similar Ticket");
        when(trackerClientMock.performTicket(eq("SIM-1"), any())).thenReturn(similarTicketMock);
        when(promptManagerMock.checkSimilarTickets(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn("[\"SIM-1\"]");
        when(promptManagerMock.validateSimilarStory(any())).thenReturn("Validate Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("true");

        List<ITicket> result = jAssistant.checkSimilarTickets("role", new ArrayList<>(), true,
                new TicketContext(trackerClientMock, ticketMock));

        assertEquals(1, result.size());
        assertSame(similarTicketMock, result.get(0));
    }

    @Test
    public void testCheckSimilarTicketsWithoutDetailsValidation() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        ITicket similarTicketMock = mock(ITicket.class);
        when(similarTicketMock.getTicketTitle()).thenReturn("Similar Ticket");
        when(trackerClientMock.performTicket(eq("SIM-1"), any())).thenReturn(similarTicketMock);
        when(promptManagerMock.checkSimilarTickets(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn("[\"SIM-1\"]");

        List<ITicket> result = jAssistant.checkSimilarTickets("role", new ArrayList<>(), false,
                new TicketContext(trackerClientMock, ticketMock));

        assertEquals(1, result.size());
        assertSame(similarTicketMock, result.get(0));
        verify(promptManagerMock, never()).validateSimilarStory(any());
    }

    @Test
    public void testGenerateUnitTest() throws Exception {
        when(promptManagerMock.requestTestGeneration(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn(
                "intro @jai_generated_code\npublic class FooTest {}\n@jai_generated_code outro");

        String result = jAssistant.generateUnitTest("file content", "Foo", "com.example",
                "template", new UnitTestsGeneratorParams());

        assertTrue(result.contains("public class FooTest {}"));
    }

    @Test
    public void testConversationObserverGetterSetterAndConstructor() {
        ConversationObserver observer = new ConversationObserver();
        JAssistant assistant = new JAssistant(trackerClientMock, sourceCodesMock, aiClientMock,
                promptManagerMock, observer);
        assertSame(observer, assistant.getConversationObserver());

        ConversationObserver anotherObserver = new ConversationObserver();
        assistant.setConversationObserver(anotherObserver);
        assertSame(anotherObserver, assistant.getConversationObserver());
    }

    @Test
    public void testIdentifyRequirementsSkipsWhenLabelExists() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.getTicketLabels()).thenReturn(new JSONArray().put("prefix_requirements"));

        jAssistant.identifyIsContentRelatedToRequirementsAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock, never()).addLabelIfNotExists(any(), anyString());
        verifyNoInteractions(aiClientMock);
        verifyNoInteractions(promptManagerMock);
    }

    @Test
    public void testIdentifyRequirementsMarksNotRelated() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.toText()).thenReturn("");
        when(promptManagerMock.isContentRelatedToRequirements(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("false");

        jAssistant.identifyIsContentRelatedToRequirementsAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock).addLabelIfNotExists(ticketMock, "prefix_not_requirements");
    }

    @Test
    public void testIdentifyTimelineMarksNotRelated() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.toText()).thenReturn("");
        when(promptManagerMock.isContentRelatedToTimeline(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("false");

        jAssistant.identifyIsContentRelatedToTimelineAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock).addLabelIfNotExists(ticketMock, "prefix_not_timeline");
    }

    @Test
    public void testIdentifyTeamSetupMarksNotRelated() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(ticketMock.toText()).thenReturn("");
        when(promptManagerMock.isContentRelatedToTeamSetup(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("false");

        jAssistant.identifyIsContentRelatedToTeamSetupAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock).addLabelIfNotExists(ticketMock, "prefix_not_team_setup");
    }
}
