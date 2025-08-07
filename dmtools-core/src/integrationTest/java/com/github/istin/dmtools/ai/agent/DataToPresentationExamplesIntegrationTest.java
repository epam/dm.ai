package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.common.utils.Resources;
import com.github.istin.dmtools.presentation.JSPresentationMakerBridge;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class DataToPresentationExamplesIntegrationTest {

    private com.github.istin.dmtools.bridge.DMToolsBridge mockBridge;

    @BeforeEach
    void setUp() throws Exception {
        // We need a mock of the DMToolsBridge to intercept Java calls from the script
        mockBridge = Mockito.mock(com.github.istin.dmtools.bridge.DMToolsBridge.class);

        // Mock the behavior of the bridge methods that the scripts will call
        setupMockBridgeBehavior();
    }

    private void setupMockBridgeBehavior() throws Exception {
        // Mock for example 1 and 2
        when(mockBridge.getTrackerClientInstance()).thenReturn(Mockito.mock(TrackerClient.class));

        // Mock for example 1: generateCustomProjectReport
        JSONObject mockReportJson = new JSONObject();
        mockReportJson.put("htmlContent", "<html><body>Mock Report Content</body></html>");
        when(mockBridge.generateCustomProjectReport(any(TrackerClient.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockReportJson.toString());

        // Mock for example 1 and 2: runPresentationOrchestrator
        JSONObject mockOrchestratorResponse = new JSONObject();
        mockOrchestratorResponse.put("title", "Mock Orchestrated Presentation");
        mockOrchestratorResponse.put("slides", "[]");
        when(mockBridge.runPresentationOrchestrator(anyString()))
                .thenReturn(mockOrchestratorResponse.toString());
    }

    private String getExampleScript(String resourcePath) {
        return Resources.readSpecificResource(resourcePath);
    }

    @Test
    void testExample_csvDataAnalysis_isExecutable() throws Exception {
        // Step 1: Get the script content from the resource file
        String scriptContent = getExampleScript("js/examples/csv_data_analysis.js");
        assertTrue(scriptContent.contains("function generatePresentationJs(paramsForJs, javaClient)"), "Script should contain the main function.");

        // Step 2: Set up the script engine
        javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName("graal.js");
        assertNotNull(engine, "GraalJS engine not found.");
        engine.eval(scriptContent);
        javax.script.Invocable invocable = (javax.script.Invocable) engine;

        // Step 3: Set up mock behavior and argument captor
        org.mockito.ArgumentCaptor<String> orchestratorParamsCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.reset(mockBridge);
        setupMockBridgeBehavior(); // re-apply general mocks
        when(mockBridge.runPresentationOrchestrator(orchestratorParamsCaptor.capture())).thenReturn(
                new JSONObject().put("title", "CSV Analysis Presentation").toString()
        );

        // Step 4: Execute the script. paramsForJs is empty as the script is self-contained.
        Object result = invocable.invokeFunction("generatePresentationJs", "{}", mockBridge);

        // Step 5: Assert the script output
        assertNotNull(result, "Script result should not be null.");
        JSONObject resultJson = new JSONObject(result.toString());
        assertEquals("CSV Analysis Presentation", resultJson.getString("title"));

        // Step 6: Assert that the script correctly processed the CSV and called the orchestrator with the right data
        String capturedOrchestratorParams = orchestratorParamsCaptor.getValue();
        JSONObject capturedJson = new JSONObject(capturedOrchestratorParams);

        assertEquals("Analysis of User Feedback Survey", capturedJson.getString("topic"));

        JSONObject requestData = capturedJson.getJSONArray("requestDataList").getJSONObject(0);
        assertEquals("Create two slides with insights: one summarizing the positive moments from user feedback, and another showing the most popular recommendations for improvement.", requestData.getString("userRequest"));

        String additionalData = requestData.getString("additionalData");
        assertTrue(additionalData.contains("Positive Feedback Highlights"), "Aggregated data should contain positive feedback section.");
        assertTrue(additionalData.contains("Key Improvement Suggestions"), "Aggregated data should contain recommendations section.");
        assertTrue(additionalData.contains("The interface is intuitive"), "Aggregated data should contain specific positive feedback.");
        assertTrue(additionalData.contains("Enable offline mode functionality"), "Aggregated data should contain a specific recommendation.");
    }

    @Test
    void testExample_csvDataAnalysis_generatesRealFile() throws Exception {
        // This is a full end-to-end test that calls the real PresentationMakerOrchestrator,
        // which will in turn make a real call to the Dial API.
        // Ensure your API keys are configured correctly before running.

        // Step 1: Configure the JSPresentationMakerBridge
        JSONObject config = new JSONObject();
        config.put("clientName", "CsvAnalysisTest");
        // We pass the resource path, JSPresentationMakerBridge knows how to load it from the classpath
        config.put("jsScriptPath", "js/examples/csv_data_analysis.js");

        // Step 2: Instantiate the bridge
        JSPresentationMakerBridge presentationMaker = new JSPresentationMakerBridge(config);

        // Step 3: Execute the script and generate the presentation file
        // The script is self-contained, so we pass an empty JSON for params.
        File presentationFile = presentationMaker.createPresentationFile("{}", "CSV Feedback Analysis");

        // Step 4: Assert that the file was created
        assertNotNull(presentationFile, "The presentation file should not be null.");
        assertTrue(presentationFile.exists(), "The presentation file should exist.");
        assertTrue(presentationFile.length() > 0, "The presentation file should not be empty.");
        assertTrue(presentationFile.getName().endsWith(".html"), "The file should be an HTML file.");

        // Clean up the generated file
        //presentationFile.deleteOnExit();

        System.out.println("E2E Test for CSV Analysis Passed. Presentation file generated at: " + presentationFile.getAbsolutePath());
    }

    @Test
    void testExample_bugReportAnalysis_isExecutable() throws Exception {
        // Step 1: Get the script content from the resource file
        String scriptContent = getExampleScript("js/examples/bug_report_analysis.js");
        assertTrue(scriptContent.contains("function generatePresentationJs(paramsForJs, javaClient)"), "Script should contain the main function.");

        // Step 2: Set up the script engine
        javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName("graal.js");
        assertNotNull(engine, "GraalJS engine not found.");
        engine.eval(scriptContent);
        javax.script.Invocable invocable = (javax.script.Invocable) engine;

        // Step 3: Set up mock behavior and argument captor
        when(mockBridge.generateBugsReportWithTypes(any(), anyString(), anyString(), anyString(), any(), anyBoolean(), anyString()))
                .thenReturn("<html><body>Mock Bug Report Content</body></html>");

        org.mockito.ArgumentCaptor<String> orchestratorParamsCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(mockBridge.runPresentationOrchestrator(orchestratorParamsCaptor.capture())).thenReturn(
                new JSONObject().put("title", "Bug Analysis Presentation").toString()
        );

        // Step 4: Execute the script, passing the project key as a parameter
        String jiraExtraFieldsProject = new PropertyReader().getJiraExtraFieldsProject();
        String paramsForJs = new JSONObject().put("projectKey", jiraExtraFieldsProject).toString();
        Object result = invocable.invokeFunction("generatePresentationJs", paramsForJs, mockBridge);

        // Step 5: Assert the script output
        assertNotNull(result, "Script result should not be null.");
        JSONObject resultJson = new JSONObject(result.toString());
        assertEquals("Bug Analysis Presentation", resultJson.getString("title"));

        // Step 6: Assert that the script passed the correct data to the orchestrator
        String capturedOrchestratorParams = orchestratorParamsCaptor.getValue();
        JSONObject capturedJson = new JSONObject(capturedOrchestratorParams);
        assertEquals("Analysis of Currently Open Bugs in " + jiraExtraFieldsProject, capturedJson.getString("topic"));
        assertEquals("<html><body>Mock Bug Report Content</body></html>", capturedJson.getJSONArray("requestDataList").getJSONObject(0).getString("additionalData"));
    }
} 