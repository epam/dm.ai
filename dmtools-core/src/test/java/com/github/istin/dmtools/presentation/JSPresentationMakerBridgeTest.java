// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class JSPresentationMakerBridgeTest {

    @TempDir // JUnit 5 annotation to create a temporary directory for the test
    Path tempDir;

    private File createTestJsFile(String scriptContent) throws IOException {
        File jsFile = tempDir.resolve("testPresentationMaker.js").toFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsFile))) {
            writer.write(scriptContent);
        }
        return jsFile;
    }

    @Test
    void testCreatePresentationWithSampleJSInteraction() throws Exception {
        String jsContent =
                "function generatePresentationJs(paramsForJs, javaClient) {\n" +
                "    javaClient.jsLogInfo('[JS] Sample Test: generatePresentationJs called with params: ' + paramsForJs);\n" +
                "    const params = JSON.parse(paramsForJs);\n" +
                "    const topic = params.topic || 'Sample Presentation';\n" +
                "    const presenter = params.presenter || 'Test Presenter';\n" +
                "\n" +
                "    // Create a basic sample presentation JSON structure\n" +
                "    const presentation = {\n" +
                "        title: 'Sample Presentation',\n" +
                "        slides: [\n" +
                "            {\n" +
                "                type: 'title',\n" +
                "                title: 'Sample Presentation',\n" +
                "                subtitle: 'Presentation for Test Audience',\n" +
                "                presenter: presenter,\n" +
                "                presenterTitle: 'Test Title',\n" +
                "                date: 'May 2023'\n" +
                "            },\n" +
                "            {\n" +
                "                type: 'content',\n" +
                "                title: 'Sample Content',\n" +
                "                subtitle: 'Important Information',\n" +
                "                description: {\n" +
                "                    title: 'Key Points',\n" +
                "                    text: 'This is sample text for the presentation',\n" +
                "                    bullets: ['Point 1', 'Point 2', 'Point 3']\n" +
                "                },\n" +
                "                content: '## Sample Content\\n\\n* Bullet 1\\n* Bullet 2'\n" +
                "            },\n" +
                "            {\n" +
                "                type: 'content',\n" +
                "                title: 'Conclusion',\n" +
                "                subtitle: 'Key Takeaways',\n" +
                "                content: '## Thank You\\n\\n* Questions?\\n* Comments?'\n" +
                "            }\n" +
                "        ]\n" +
                "    };\n" +
                "\n" +
                "    return presentation;\n" +
                "}";

        File testJs = createTestJsFile(jsContent);

        JSONObject clientConfig = new JSONObject();
        clientConfig.put("clientName", "TestJSPresentationClient");
        clientConfig.put("jsScriptPath", testJs.getAbsolutePath());

        JSPresentationMakerBridge client = new JSPresentationMakerBridge(clientConfig);

        JSONObject paramsForJsInput = new JSONObject();
        paramsForJsInput.put("topic", "My Test Presentation via JS");
        paramsForJsInput.put("presenter", "JUnit Test");

        File presentationFile = client.createPresentationFile(paramsForJsInput.toString());

        assertNotNull(presentationFile, "The presentation file should not be null.");
        assertTrue(presentationFile.exists(), "The presentation file should exist.");
        assertTrue(presentationFile.getName().endsWith("_presentation.html"), "The file should be an HTML presentation.");
        
        // Clean up
        //presentationFile.delete();
    }

    @Test
    void testCreatePresentationWithSimpleJsInteraction() throws Exception {
        String jsContent =
            "function generatePresentationJs(paramsForJs, javaClient) {\n" +
            "    javaClient.jsLogInfo('[JS] Test Script: generatePresentationJs called with params: ' + paramsForJs);\n" +
            "    const params = JSON.parse(paramsForJs);\n" +
            "    const topic = params.topic || 'Default JS Topic';\n" +
            "    const presenter = params.presenter || 'JS Presenter';\n" +
            "\n" +
            "    const presentationData = {\n" +
            "        title: 'Presentation for ' + topic,\n" +
            "        presenter: presenter,\n" +
            "        slides: [\n" +
            "            { type: 'title', title: topic, subtitle: 'Generated by JS' },\n" +
            "            { type: 'content', title: 'First Content Slide', content: 'Hello from JavaScript, ' + presenter + '!' }\n" +
            "        ]\n" +
            "    };\n" +
            "    return presentationData;\n" +
            "}";

        File testJs = createTestJsFile(jsContent);

        JSONObject clientConfig = new JSONObject();
        clientConfig.put("clientName", "TestJSPresentationClient");
        clientConfig.put("jsScriptPath", testJs.getAbsolutePath());

        JSPresentationMakerBridge client = new JSPresentationMakerBridge(clientConfig);

        JSONObject paramsForJsInput = new JSONObject();
        paramsForJsInput.put("topic", "My Test Presentation via JS");
        paramsForJsInput.put("presenter", "JUnit Test");

        JSONObject result = client.createPresentation(paramsForJsInput.toString());

        assertNotNull(result, "The result from createPresentation should not be null.");
        assertFalse(result.has("error"), "The JS script should not have returned an error. Check logs for '[JS] Test Script: Error...'. Details: " + result.optString("details"));

        assertEquals("Presentation for My Test Presentation via JS", result.optString("title"));
        assertEquals("JUnit Test", result.optString("presenter"));
        assertTrue(result.has("slides"), "Resulting JSON should have a 'slides' array.");
        assertEquals(2, result.getJSONArray("slides").length(), "Expected 2 slides from the JS script.");
        assertEquals("My Test Presentation via JS", result.getJSONArray("slides").getJSONObject(0).optString("title"));
        assertEquals("First Content Slide", result.getJSONArray("slides").getJSONObject(1).optString("title"));
    }

    @Test
    void testCreatePresentationWithOrchestratorParamsOverload() throws Exception {
        String jsContent = 
            "function generatePresentationJs(paramsStringFromJava, javaClient) {\n" +
            "    javaClient.jsLogInfo('[JS] Orchestrator Test: Called with string: ' + paramsStringFromJava);\n" +
            "    const orchestratorParams = JSON.parse(paramsStringFromJava);\n" + 
            "    const topic = orchestratorParams.topic;\n" +
            "\n" +
            "    const presentationOutput = {\n" +
            "        title: 'Orchestrator Presentation for ' + topic,\n" +
            "        mainTopic: topic,\n" +
            "        audienceFor: orchestratorParams.audience,\n" +
            "        numberOfRequests: orchestratorParams.requestDataList.length,\n" +
            "        slides: [\n" +
            "            {\n" +
            "                type: 'title',\n" +
            "                title: topic,\n" +
            "                subtitle: 'Generated via Orchestrator',\n" +
            "                presenter: orchestratorParams.presenterName,\n" +
            "                date: new Date().toLocaleDateString()\n" +
            "            },\n" +
            "            {\n" +
            "                type: 'content',\n" +
            "                title: 'Audience: ' + orchestratorParams.audience,\n" +
            "                content: 'Presenter: ' + orchestratorParams.presenterName + '\\\\nNumber of requests: ' + orchestratorParams.requestDataList.length\n" +
            "            }\n" +
            "        ]\n" +
            "    };\n" +
            "\n" +
            "    return presentationOutput;\n" +
            "}";
        
        File testJs = createTestJsFile(jsContent);

        JSONObject clientConfig = new JSONObject();
        clientConfig.put("clientName", "TestJSOrchestratorClient");
        clientConfig.put("jsScriptPath", testJs.getAbsolutePath());

        JSPresentationMakerBridge client = new JSPresentationMakerBridge(clientConfig);

        PresentationMakerOrchestrator.Params orchestratorParams = new PresentationMakerOrchestrator.Params(
                "Quarterly Review",
                "Management Team",
                Collections.singletonList(new PresentationMakerOrchestrator.RequestData("Analyze sales data", "Sales figures...")),
                "AI Bot",
                "Chief Presenter",
                "Summarize key findings and next steps."
        );

        // Test JSON-based method
        JSONObject result = client.createPresentation(orchestratorParams);
        assertNotNull(result, "Result from orchestrator params overload should not be null.");
        assertFalse(result.has("error"), "The JS script for orchestrator params should not have returned an error. Details: " + result.optString("details"));
        assertEquals("Quarterly Review", result.optString("mainTopic"));
        assertEquals("Management Team", result.optString("audienceFor"));
        assertEquals(1, result.optInt("numberOfRequests"));

        // Test File-based method
        File presentationFile = client.createPresentationFile(orchestratorParams);
        assertNotNull(presentationFile, "The presentation file should not be null.");
        assertTrue(presentationFile.exists(), "The presentation file should exist.");
        assertTrue(presentationFile.getName().startsWith("Orchestrator-Quarterly_Review"), "The file should start with 'Orchestrator-Quarterly_Review'.");
        assertTrue(presentationFile.getName().endsWith("_presentation.html"), "The file should be an HTML presentation.");
        
        // Clean up
        //presentationFile.delete();
    }
} 