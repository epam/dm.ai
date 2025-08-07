package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.prompt.PromptManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JAssistantTest {

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
    public void testGenerateCode() throws Exception {
        TicketContext ticketContextMock = mock(TicketContext.class);
        ITicket ticketMock = mock(ITicket.class);
        when(ticketContextMock.getTicket()).thenReturn(ticketMock);
        when(ticketMock.getIssueType()).thenReturn("Story");
        when(trackerClientMock.getBasePath()).thenReturn("basePath");
        when(promptManagerMock.requestGenerateCodeForTicket(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString(), anyString())).thenReturn("AI Response");

        jAssistant.generateCode("role", ticketContextMock);

        verify(trackerClientMock).addLabelIfNotExists(any(), eq("ai_generated_code"));
    }

    @Test
    public void testGenerateNiceLookingStoryInGherkinStyleAndPotentialQuestionsToPO() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(trackerClientMock.performTicket(anyString(), any())).thenReturn(ticketMock);
        when(promptManagerMock.requestNiceLookingStoryInGherkinStyleAndPotentialQuestionsToPO(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("AI Response");

        jAssistant.generateNiceLookingStoryInGherkinStyleAndPotentialQuestionsToPO("TICKET-1");

        verify(trackerClientMock).postComment(anyString(), contains("JAI Generated Nice Looking Story In Gherkin Style And Potential Questions To PO: "));
    }

    @Test
    public void testCheckStoryIsTechnicalOrProduct() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(trackerClientMock.performTicket(anyString(), any())).thenReturn(ticketMock);
        when(promptManagerMock.checkTaskTechnicalOrProduct(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Technical");

        String result = jAssistant.checkStoryIsTechnicalOrProduct(ticketMock);

        assertEquals("Technical", result);
    }

    @Test
    public void testChooseFeatureAreaForStory() throws Exception {
        ToText inputTextMock = mock(ToText.class);
        when(promptManagerMock.checkStoryAreas(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Feature Area");

        String result = jAssistant.chooseFeatureAreaForStory(inputTextMock, "areas");

        assertEquals("Feature Area", result);
    }

    @Test
    public void testWhatIsFeatureAreaOfStory() throws Exception {
        ToText ticketMock = mock(ToText.class);
        when(promptManagerMock.whatIsFeatureAreaOfStory(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Feature Area");

        String result = jAssistant.whatIsFeatureAreaOfStory(ticketMock);

        assertEquals("Feature Area", result);
    }

    @Test
    public void testWhatIsFeatureAreasOfDataInput() throws Exception {
        ToText textInputMock = mock(ToText.class);
        when(promptManagerMock.whatIsFeatureAreasOfDataInput(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File)any())).thenReturn(new JSONArray().toString());

        JSONArray result = jAssistant.whatIsFeatureAreasOfDataInput(textInputMock);

        assertNotNull(result);
    }

    @Test
    public void testBuildDetailedPageWithRequirementsForInputData() throws Exception {
        ToText inputDataMock = mock(ToText.class);
        when(promptManagerMock.buildDetailedPageWithRequirementsForInputData(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Detailed Page");

        String result = jAssistant.buildDetailedPageWithRequirementsForInputData(inputDataMock, "existingContent");

        assertEquals("Detailed Page", result);
    }

    @Test
    public void testBuildNiceLookingDocumentationForStory() throws Exception {
        ToText inputDataMock = mock(ToText.class);
        when(promptManagerMock.buildNiceLookingDocumentation(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Nice Looking Documentation");

        String result = jAssistant.buildNiceLookingDocumentationForStory(inputDataMock, "existingContent");

        assertEquals("Nice Looking Documentation", result);
    }

    @Test
    public void testBuildDORGenerationForStory() throws Exception {
        ToText inputDataMock = mock(ToText.class);
        when(promptManagerMock.buildDorBasedOnExistingStories(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("DOR Generation");

        String result = jAssistant.buildDORGenerationForStory(inputDataMock, "existingContent");

        assertEquals("DOR Generation", result);
    }

    @Test
    public void testBuildProjectTimeline() throws Exception {
        ToText inputMock = mock(ToText.class);
        when(promptManagerMock.buildProjectTimelinePage(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Project Timeline");

        String result = jAssistant.buildProjectTimeline(inputMock, "existingContent");

        assertEquals("Project Timeline", result);
    }

    @Test
    public void testBuildTeamSetupAndLicenses() throws Exception {
        ToText inputMock = mock(ToText.class);
        when(promptManagerMock.buildTeamSetupAndLicensesPage(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Team Setup and Licenses");

        String result = jAssistant.buildTeamSetupAndLicenses(inputMock, "existingContent");

        assertEquals("Team Setup and Licenses", result);
    }

    @Test
    public void testBuildNiceLookingDocumentationForStoryWithTechnicalDetails() throws Exception {
        ToText inputMock = mock(ToText.class);
        when(promptManagerMock.buildNiceLookingDocumentationWithTechnicalDetails(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Documentation with Technical Details");

        String result = jAssistant.buildNiceLookingDocumentationForStoryWithTechnicalDetails(inputMock, "existingContent");

        assertEquals("Documentation with Technical Details", result);
    }

    @Test
    public void testCheckSimilarTickets() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(trackerClientMock.performTicket(anyString(), any())).thenReturn(ticketMock);
        when(promptManagerMock.checkSimilarTickets(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File)any())).thenReturn(new JSONArray().toString());

        List<ITicket> result = jAssistant.checkSimilarTickets("role", new ArrayList<>(), true, new TicketContext(trackerClientMock, ticketMock));

        assertTrue(result.isEmpty());
    }

    @Test
    public void testCreateFeatureAreasTree() throws Exception {
        when(promptManagerMock.createFeatureAreasTree(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn(new JSONObject().toString());

        JSONObject result = jAssistant.createFeatureAreasTree("inputAreas");

        assertNotNull(result);
    }

    @Test
    public void testCleanFeatureAreas() throws Exception {
        when(promptManagerMock.cleanFeatureAreas(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn(new JSONArray().toString());

        JSONArray result = jAssistant.cleanFeatureAreas("inputAreas");

        assertNotNull(result);
    }

    @Test
    public void testIdentifyIsContentRelatedToRequirementsAndMarkViaLabel() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(promptManagerMock.isContentRelatedToRequirements(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("true");

        jAssistant.identifyIsContentRelatedToRequirementsAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock).addLabelIfNotExists(ticketMock, "prefix_requirements");
    }

    @Test
    public void testIdentifyIsContentRelatedToTimelineAndMarkViaLabel() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(promptManagerMock.isContentRelatedToTimeline(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("true");

        jAssistant.identifyIsContentRelatedToTimelineAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock).addLabelIfNotExists(ticketMock, "prefix_timeline");
    }

    @Test
    public void testIdentifyIsContentRelatedToTeamSetupAndMarkViaLabel() throws Exception {
        ITicket ticketMock = mock(ITicket.class);
        when(promptManagerMock.isContentRelatedToTeamSetup(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("true");

        jAssistant.identifyIsContentRelatedToTeamSetupAndMarkViaLabel("prefix", ticketMock);

        verify(trackerClientMock).addLabelIfNotExists(ticketMock, "prefix_team_setup");
    }

    @Test
    public void testCombineTextAndImage() throws Exception {
        when(promptManagerMock.combineTextAndImage(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString(), anyString(), (File) any())).thenReturn("Combined Text and Image");

        String result = jAssistant.combineTextAndImage("text", new java.io.File("image.png"));

        assertEquals("Combined Text and Image", result);
    }

    @Test
    public void testCreateSolutionForTicket() throws Exception {
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(promptManagerMock.saCreateSolutionForTicket(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Solution");

        String result = jAssistant.createSolutionForTicket(trackerClientMock, "roleSpecific", "projectSpecific", ticketContextMock);

        assertEquals("Solution", result);
    }

    @Test
    public void testBuildJQLForContent() throws Exception {
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(promptManagerMock.baBuildJqlForRequirementsSearching(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("JQL");

        String result = jAssistant.buildJQLForContent(trackerClientMock, "roleSpecific", "projectSpecific", ticketContextMock);

        assertEquals("JQL", result);
    }

    @Test
    public void testBaIsTicketRelatedToContent() throws Exception {
        TicketContext ticketContextMock = mock(TicketContext.class);
        ITicket contentMock = mock(ITicket.class);
        when(promptManagerMock.baIsTicketRelatedToContent(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString())).thenReturn("true");

        boolean result = jAssistant.baIsTicketRelatedToContent(trackerClientMock, "roleSpecific", "projectSpecific", ticketContextMock, contentMock);

        assertTrue(result);
    }

    @Test
    public void testBuildPageWithRequirementsForInputData() throws Exception {
        TicketContext ticketContextMock = mock(TicketContext.class);
        ITicket contentMock = mock(ITicket.class);
        when(promptManagerMock.baCollectRequirementsForTicket(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Page with Requirements");

        String result = jAssistant.buildPageWithRequirementsForInputData(ticketContextMock, "roleSpecific", "projectSpecific", "existingContent", contentMock);

        assertEquals("Page with Requirements", result);
    }

    @Test
    public void testCreateDiagrams() throws Exception {
        TicketContext ticketContextMock = mock(TicketContext.class);
        when(promptManagerMock.createDiagrams(any())).thenReturn("AI Request");
        when(aiClientMock.chat(any(), anyString(), (File) any())).thenReturn(new JSONArray().toString());

        List<Diagram> result = jAssistant.createDiagrams(ticketContextMock, "roleSpecific", "projectSpecific");

        assertNotNull(result);
    }

    @Test
    public void testMakeDailyScrumReportOfUserWork() throws Exception {
        when(promptManagerMock.makeDailyScrumReportOfUserWork(any())).thenReturn("AI Request");
        when(aiClientMock.chat(anyString())).thenReturn("Daily Scrum Report");

        String result = jAssistant.makeDailyScrumReportOfUserWork("userName", new ArrayList<>());

        assertEquals("Daily Scrum Report", result);
    }

}