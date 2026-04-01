// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

// To run these tests, ensure GEMINI_API_KEY is set in your system environment variables
// or in config.properties.
public class GeminiAIClientIntegrationTest {

    private AI geminiAI;
    private ConversationObserver observer;
    private static File testImageFile;
    private static final String TEST_IMAGE_RESOURCE_PATH = "/test_image_icon.png";
    private static final String TEST_IMAGE_FILENAME = "test_image_icon.png";
    private PropertyReader propertyReader;

    @Before
    public void setUp() throws Exception {
        observer = new ConversationObserver();
        propertyReader = new PropertyReader(); // Initialize propertyReader here
        // BasicGeminiAI.create will throw an exception if API key is not found
        geminiAI = BasicGeminiAI.create(observer, propertyReader);

        // Prepare the image file for tests that need it
        if (testImageFile == null) {
            try (InputStream inputStream = GeminiAIClientIntegrationTest.class.getResourceAsStream(TEST_IMAGE_RESOURCE_PATH)) {
                if (inputStream == null) {
                    throw new IOException("Test image resource not found: " + TEST_IMAGE_RESOURCE_PATH);
                }
                // Create a temporary file for the image
                Path tempDir = Files.createTempDirectory("gemini_test_images_");
                testImageFile = new File(tempDir.toFile(), TEST_IMAGE_FILENAME);
                Files.copy(inputStream, testImageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                testImageFile.deleteOnExit(); // Clean up on JVM exit
                tempDir.toFile().deleteOnExit();
            } catch (IOException e) {
                System.err.println("Failed to load test image for Gemini tests: " + e.getMessage());
                throw e; // Fail setup if image can't be loaded
            }
        }
    }

    @Test
    public void testSimpleChat() throws Exception {
        String prompt = "Hello Gemini, who are you?";
        String response = geminiAI.chat(prompt);
        System.out.println("Gemini response (simple): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        assertTrue("Response should mention Gemini or model", response.toLowerCase().contains("gemini") || response.toLowerCase().contains("model"));
    }

    @Test
    public void testChatWithModel() throws Exception {
        String model = propertyReader.getGeminiDefaultModel(); // Uses the configured default model
        assertNotNull("Gemini default model should be configured", model);

        String prompt = "Explain what a large language model is in one sentence.";
        String response = geminiAI.chat(model, prompt);
        System.out.println("Gemini response (model " + model + "): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testChatWithImage() throws Exception {
        assertTrue("Test image file must exist for this test.", testImageFile != null && testImageFile.exists());

        String prompt = "What is in this image?";
        String modelToUse = propertyReader.getGeminiDefaultModel();
        String response = geminiAI.chat(modelToUse, prompt, testImageFile);

        System.out.println("Gemini response (with image): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        assertFalse("Response should not indicate an error processing the image", response.toLowerCase().contains("error"));
    }

    @Test
    public void testMultiTurnChat() throws Exception {
        Message m1 = new Message("user", "My name is Bob. What is your name?", null);
        String response1 = geminiAI.chat(m1);
        System.out.println("User: My name is Bob. What is your name?\nGemini: " + response1);
        assertNotNull(response1);
        assertFalse(response1.isEmpty());

        Message m2 = new Message("model", response1, null);
        Message m3 = new Message("user", "Do you remember my name?", null);
        String response2 = geminiAI.chat(m1, m2, m3);
        System.out.println("User: Do you remember my name?\nGemini: " + response2);
        assertNotNull(response2);
        assertFalse(response2.isEmpty());
        assertTrue("Gemini should remember the name Bob in a multi-turn conversation.", response2.toLowerCase().contains("bob"));
    }

    @Test
    public void testMultiTurnChatWithImage() throws Exception {
        assertTrue("Test image file must exist for this test.", testImageFile != null && testImageFile.exists());

        Message m1 = new Message("user", "Here is an image. What color is the main object in it?", Collections.singletonList(testImageFile));
        String response1 = geminiAI.chat(m1);
        System.out.println("User: Here is an image. What color is the main object in it?\nGemini: " + response1);
        assertNotNull(response1);
        assertFalse(response1.isEmpty());
        assertFalse("Response1 should not indicate an error", response1.toLowerCase().contains("error"));

        Message m2 = new Message("model", response1, null);
        Message m3 = new Message("user", "Based on that image, what is a typical use for such an item?", null);
        String response2 = geminiAI.chat(m1, m2, m3);
        System.out.println("User: Based on that image, what is a typical use for such an item?\nGemini: " + response2);
        assertNotNull(response2);
        assertFalse(response2.isEmpty());
        assertFalse("Response2 should not indicate an error", response2.toLowerCase().contains("error"));
    }
} 