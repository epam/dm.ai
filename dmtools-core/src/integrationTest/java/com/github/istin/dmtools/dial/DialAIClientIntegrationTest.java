package com.github.istin.dmtools.dial;

import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

// @Ignore("This test requires actual Dial API key and network access. Enable for manual integration testing.") // Removing this to enable tests
public class DialAIClientIntegrationTest {

    private BasicDialAI dialClient;
    private File iconFile;

    @Before
    public void setUp() throws Exception {
        try {
            dialClient = new BasicDialAI();
        } catch (Exception e) {
            fail("Failed to initialize BasicDIAL client: " + e.getMessage());
        }

        // Load the test_image_icon.png resource
        // IMPORTANT: Ensure test_image_icon.png is in src/integrationTest/resources/
        URL iconUrl = getClass().getClassLoader().getResource("test_image_icon.png");
        if (iconUrl == null) {
            System.err.println("WARNING: test_image_icon.png not found in resources. Image-related tests might fail or be skipped.");
            iconFile = null; // Or handle as a failing condition for specific tests
        } else {
            iconFile = new File(iconUrl.toURI());
        }
    }

    private void assertResponse(String response) {
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        System.out.println("AI Response: " + response);
    }

    @Test
    public void testChatStringMessage() throws Exception {
        String response = dialClient.chat("Hello, DIAL! What is your model name?");
        assertResponse(response);
    }

    @Test
    public void testChatModelAndStringMessage() throws Exception {
        String response = dialClient.chat(dialClient.getModel(), "Hello, DIAL from a specific model call! Can you tell me a fun fact?");
        assertResponse(response);
    }

    @Test
    public void testChatModelStringMessageAndFile() throws Exception {
        if (iconFile == null || !iconFile.exists()) {
            System.out.println("Skipping testChatModelStringMessageAndFile: test_image_icon.png not found.");
            return;
        }
        String response = dialClient.chat(dialClient.getModel(), "Describe the main subject of this image.", iconFile);
        assertResponse(response);
        // Add more specific assertions based on expected content related to test_image_icon.png if possible
        assertTrue("Response should ideally mention an 'icon' or 'logo' or 'emoticon' if that's what test_image_icon.png is.", response.toLowerCase().contains("icon") || response.toLowerCase().contains("logo") || response.toLowerCase().contains("emoticon") || response.toLowerCase().contains("smiley") || response.toLowerCase().contains("emoji") || response.toLowerCase().contains("sun"));
    }
    
    @Test
    public void testChatModelStringMessageAndFileList() throws Exception {
        // Test with null file list
        String responseNullList = dialClient.chat(dialClient.getModel(), "Hello, DIAL with null file list!", (java.util.List<File>) null);
        assertResponse(responseNullList);

        // Test with empty file list
        String responseEmptyList = dialClient.chat(dialClient.getModel(), "Hello, DIAL with empty file list!", new ArrayList<>());
        assertResponse(responseEmptyList);
        
        // Test with a list containing the iconFile
        if (iconFile == null || !iconFile.exists()) {
            System.out.println("Skipping image part of testChatModelStringMessageAndFileList: test_image_icon.png not found.");
            return;
        }
        String responseWithFile = dialClient.chat(dialClient.getModel(), "What is depicted in the provided image?", Collections.singletonList(iconFile));
        assertResponse(responseWithFile);
        assertTrue("Response should be relevant to the image provided in the list.", responseWithFile.toLowerCase().contains("icon") || responseWithFile.toLowerCase().contains("smiley") || responseWithFile.toLowerCase().contains("illustration"));
    }

    @Test
    public void testChatMessagesVarargs() throws Exception {
        Message instruction = new Message("system", "You are a helpful assistant. When asked for a picnic suggestion, combine the user's favorite fruit and the weather forecast.", null);
        Message userFact1 = new Message("user", "My favorite fruit is an apple.", null);
        Message userFact2 = new Message("user", "The weather for the picnic is expected to be sunny and warm.", null);
        Message userQuestion = new Message("user", "Given my preferences and the weather, what's a simple dessert I could bring to the picnic?", null);
        
        String response = dialClient.chat(instruction, userFact1, userFact2, userQuestion);
        assertResponse(response);
        assertTrue("Response should mention 'apple' based on user's favorite fruit.", response.toLowerCase().contains("apple"));
        assertTrue("Response should consider 'sunny' or 'warm' weather for the suggestion.", response.toLowerCase().contains("sunny") || response.toLowerCase().contains("warm") || response.toLowerCase().contains("refreshing") || response.toLowerCase().contains("cool"));
    }

    @Test
    public void testChatModelAndMessagesVarargs() throws Exception {
        Message sysPrompt = new Message("system", "You are a story writer. The user will provide two elements, and you should suggest a very short story title incorporating both.", null);
        Message element1 = new Message("user", "The first element is: a talking cat.", null);
        Message element2 = new Message("user", "The second element is: a mysterious old library.", null);
        Message request = new Message("user", "Suggest a story title.", null);

        String response = dialClient.chat(dialClient.getModel(), sysPrompt, element1, element2, request);
        assertResponse(response);
        assertTrue("Response should mention 'cat' or 'feline'.", response.toLowerCase().contains("cat") || response.toLowerCase().contains("feline"));
        assertTrue("Response should mention 'library' or 'books'.", response.toLowerCase().contains("library") || response.toLowerCase().contains("book"));
    }
    
    @Test
    public void testChatModelAndMessagesVarargsWithImage() throws Exception {
        if (iconFile == null || !iconFile.exists()) {
            System.out.println("Skipping testChatModelAndMessagesVarargsWithImage: test_image_icon.png not found.");
            return;
        }
        Message imageMessage = new Message("user", "Consider this image carefully.", Collections.singletonList(iconFile));
        Message questionMessage = new Message("user", "Based on the image I just sent you, what is one primary color you observe in it? Be specific.", null);

        String response = dialClient.chat(dialClient.getModel(), imageMessage, questionMessage);
        assertResponse(response);
        // This assertion is highly dependent on the content of test_image_icon.png
        // Example: if test_image_icon.png has a prominent yellow smiley.
        assertTrue("Response should mention a color prominently visible in the icon.", 
                response.toLowerCase().contains("yellow") || 
                response.toLowerCase().contains("blue") || 
                response.toLowerCase().contains("red") || 
                response.toLowerCase().contains("green") || // Add other prominent colors from test_image_icon.png
                response.toLowerCase().contains("orange") ||
                response.toLowerCase().contains("bright")
        );
    }

} 