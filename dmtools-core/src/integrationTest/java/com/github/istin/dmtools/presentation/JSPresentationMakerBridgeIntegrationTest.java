// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

// Assuming this file is now in src/integrationTest/java/com/github/istin/dmtools/presentation/
public class JSPresentationMakerBridgeIntegrationTest { // Renamed class for clarity

    @TempDir
    Path tempDir;

    private File testJsFile;
    private Path reportsDirPath; // To manage the reports directory

    @BeforeEach
    void setUp() throws IOException {
        // Define the path to the 'reports' directory relative to the project root
        // This assumes the test is run from the project's root directory.
        reportsDirPath = Paths.get("reports"); 
        // Clean up the specific presentation file if it exists from a previous run
        // More robust cleanup might be needed depending on HTMLPresentationDrawer's behavior
    }

    private void createTestJsFile(String scriptContent) throws IOException {
        testJsFile = tempDir.resolve("integrationTestPresentationMaker.js").toFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(testJsFile))) {
            writer.write(scriptContent);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up the specific test presentation file from the reports directory
        // This is a best-effort cleanup. A more robust solution would be to have HTMLPresentationDrawer
        // allow specifying an output directory or returning the file path.
        String expectedReportFileName = "reports" + File.separator + "AI Generated Test Presentation_presentation.html";
        Path reportFile = Paths.get(expectedReportFileName);
        Files.deleteIfExists(reportFile);
        
        String expectedOrchestratorReportFileName = "reports" + File.separator + "Orchestrator-AI Quarterly Review_presentation.html";
        Path orchestratorReportFile = Paths.get(expectedOrchestratorReportFileName);
        Files.deleteIfExists(orchestratorReportFile);
    }

    @Test
    void testPresentationGenerationWithSimulatedAIContent() throws Exception {
        String jsContent = 
            "function generatePresentationJs(paramsForJs, javaClient) {\n" +
            "    javaClient.jsLogInfo('[JS-IT] Test Script: generatePresentationJs called with params: ' + paramsForJs);\n" +
            "    const params = JSON.parse(paramsForJs);\n" +
            "    const topic = params.topic || 'Default AI Topic';\n" +
            "\n" +
            "    // Simulate AI generating slide content based on the topic\n" +
            "    let slides = [];\n" +
            "    slides.push({ type: 'title', title: topic, subtitle: 'AI Generated Content' });\n" +
            "    slides.push({ \n" +
            "        type: 'content', \n" +
            "        title: 'Introduction to ' + topic,\n" +
            "        content: 'This presentation will cover key aspects of ' + topic + '. We will explore A, B, and C.'\n" +
            "    });\n" +
            "    slides.push({ \n" +
            "        type: 'list', \n" +
            "        title: 'Key Areas', \n" +
            "        items: [topic + ' Area 1', topic + ' Area 2', topic + ' Area 3 (AI Special Focus)'] \n" +
            "    });\n" +
            "    slides.push({ \n" +
            "        type: 'summary', \n" +
            "        title: 'Conclusion for ' + topic,\n" +
            "        content: 'In summary, ' + topic + ' is a critical field with many opportunities. Thanks AI!'\n" +
            "    });\n" +
            "\n" +
            "    const presentationData = {\n" +
            "        title: topic + ' - An AI Perspective',\n" +
            "        author: 'AI Generation Module v1.0',\n" +
            "        slides: slides\n" +
            "    };\n" +
            "    \n" +
            "    return JSON.stringify(presentationData);\n" +
            "}";

        createTestJsFile(jsContent);

        JSONObject clientConfig = new JSONObject();
        clientConfig.put("clientName", "IntegrationTestJSClient");
        clientConfig.put("jsScriptPath", testJsFile.getAbsolutePath());

        JSPresentationMakerBridge client = new JSPresentationMakerBridge(clientConfig);

        JSONObject paramsForJsInput = new JSONObject();
        paramsForJsInput.put("topic", "AI Generated Test Presentation");

        // Test JSON-based method
        JSONObject result = client.createPresentation(paramsForJsInput.toString());

        assertNotNull(result, "The result from createPresentation should not be null.");
        if (result.has("error")) {
            fail("The JS script returned an error: " + result.getString("message") + " Details: " + result.optString("details"));
        }

        assertEquals("AI Generated Test Presentation - An AI Perspective", result.optString("title"));
        assertEquals("AI Generation Module v1.0", result.optString("author"));
        assertTrue(result.has("slides"), "Resulting JSON should have a 'slides' array.");
        JSONArray slidesArray = result.getJSONArray("slides");
        assertEquals(4, slidesArray.length(), "Expected 4 slides from the AI simulated script.");
        
        // Check some slide content
        assertEquals("title", slidesArray.getJSONObject(0).optString("type"));
        assertEquals("AI Generated Test Presentation", slidesArray.getJSONObject(0).optString("title"));
        assertEquals("Introduction to AI Generated Test Presentation", slidesArray.getJSONObject(1).optString("title"));
        assertEquals("list", slidesArray.getJSONObject(2).optString("type"));
        assertTrue(slidesArray.getJSONObject(2).getJSONArray("items").getString(2).contains("AI Special Focus"));

        // Test File-based method
        File presentationFile = client.createPresentationFile(paramsForJsInput.toString());
        assertNotNull(presentationFile, "The presentation file should not be null.");
        assertTrue(presentationFile.exists(), "The presentation file should exist.");
        assertTrue(presentationFile.getName().endsWith("_presentation.html"), "The file should be an HTML presentation.");
        
        // Clean up
        presentationFile.delete();
    }

    @Test
    void testPresentationGenerationWithOrchestratorParamsAndSimulatedAI() throws Exception {
        String jsContent = 
            "function generatePresentationJs(paramsStringFromJava, javaClient) {\n" +
            "    javaClient.jsLogInfo('[JS-IT] Orchestrator Test: Called with string: ' + paramsStringFromJava);\n" +
            "    const orchestratorParams = JSON.parse(paramsStringFromJava);\n" +
            "    const topic = orchestratorParams.topic || 'AI Orchestrated Topic';\n" +
            "\n" +
            "    // Simulate AI generating slide content based on orchestrator params\n" +
            "    let slides = [];\n" +
            "    slides.push({ type: 'title', title: topic, subtitle: 'Generated via Orchestrator Params by AI' });\n" +
            "    slides.push({ \n" +
            "        type: 'content', \n" +
            "        title: 'Audience: ' + orchestratorParams.audience,\n" +
            "        content: 'Presenter: ' + orchestratorParams.presenterName + '. Number of requests: ' + orchestratorParams.requestDataList.length \n" +
            "    });\n" +
            "    if (orchestratorParams.requestDataList && orchestratorParams.requestDataList.length > 0) {\n" +
            "        slides.push({type: 'content', title: 'First Request', content: orchestratorParams.requestDataList[0].request});\n" +
            "    }\n" +
            "    slides.push({type: 'content', title: 'Summary Slide Request', content: orchestratorParams.summarySlideRequest });\n" +
            "\n" +
            "    const presentationData = {\n" +
            "        title: 'Orchestrator Presentation: ' + topic,\n" +
            "        mainTopic: topic,\n" +
            "        audienceFor: orchestratorParams.audience,\n" +
            "        slides: slides\n" +
            "    };\n" +
            "    \n" +
            "    return JSON.stringify(presentationData);\n" +
            "}";
        
        createTestJsFile(jsContent);

        JSONObject clientConfig = new JSONObject();
        clientConfig.put("clientName", "IntegrationTestJSOrchestratorClient");
        clientConfig.put("jsScriptPath", testJsFile.getAbsolutePath());

        JSPresentationMakerBridge client = new JSPresentationMakerBridge(clientConfig);

        PresentationMakerOrchestrator.Params orchestratorParams = new PresentationMakerOrchestrator.Params(
                "AI Quarterly Review",
                "Executive Team",
                Collections.singletonList(new PresentationMakerOrchestrator.RequestData("Analyze Q1 AI performance", "Q1 AI data...")),
                "AI Test Bot",
                "Lead AI Strategist",
                "Summarize AI achievements and future AI roadmap."
        );

        // Test JSON-based method
        JSONObject result = client.createPresentation(orchestratorParams);

        assertNotNull(result, "Result from orchestrator params overload should not be null.");
        if (result.has("error")) {
            fail("The JS script for orchestrator params returned an error: " + result.getString("message") + " Details: " + result.optString("details"));
        }

        assertEquals("AI Quarterly Review", result.optString("mainTopic"));
        assertEquals("Executive Team", result.optString("audienceFor"));
        assertTrue(result.has("slides"), "Result should have 'slides'.");
        assertEquals(4, result.getJSONArray("slides").length());

        // Test File-based method
        File presentationFile = client.createPresentationFile(orchestratorParams);
        assertNotNull(presentationFile, "The presentation file should not be null.");
        assertTrue(presentationFile.exists(), "The presentation file should exist.");
        assertTrue(presentationFile.getName().startsWith("Orchestrator-AI_Quarterly_Review"), "The file should start with 'Orchestrator-AI_Quarterly_Review'.");
        assertTrue(presentationFile.getName().endsWith("_presentation.html"), "The file should be an HTML presentation.");
        
        // Clean up
        presentationFile.delete();
    }
} 