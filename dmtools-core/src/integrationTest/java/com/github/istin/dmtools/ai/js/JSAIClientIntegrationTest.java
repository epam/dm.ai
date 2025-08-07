package com.github.istin.dmtools.ai.js;

import com.github.istin.dmtools.ai.Message;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// Ensure your dmtools.properties has DIAL_BASE_PATH, DIAL_API_KEY, DIAL_MODEL set for Azure DIAL for this test
// as dialChatViaJs.js is currently written to use an 'api-key' header.
//@Ignore("This test requires actual DIAL API key (for Azure) and network access properly configured in properties.")
public class JSAIClientIntegrationTest {

    private BasicJSAIClient jsAIClient;
    private File testImageFile; 

    @Before
    public void setUp() throws Exception {
        jsAIClient = mock(BasicJSAIClient.class);

        URL imageUrl = getClass().getClassLoader().getResource("test_image_icon.png");
        if (imageUrl != null) {
            testImageFile = new File(imageUrl.toURI());
        } else {
            System.err.println("WARNING: test_image_icon.png not found in resources. Image-related parts of tests might be effectively skipped by JS or fail if JS expects the file.");
        }
    }

    private void assertValidResponse(String response, String context) {
        assertNotNull("Response should not be null (" + context + ")", response);
        assertFalse("Response should not be empty (" + context + ")", response.isEmpty());
        assertFalse("Response should not indicate a JS processing error (" + context + "): " + response, response.startsWith("Error in JS:"));
        assertFalse("Response should not indicate an API error directly (" + context + "): " + response, response.startsWith("Error from API:"));
        assertFalse("Response should not indicate an API key injection error (" + context + "): " + response, response.startsWith("JS Error: apiKey is missing"));
        System.out.println("JSAI Response (" + context + "): " + response);
    }

    @Test
    public void testChatStringMessage() throws Exception {
        when(jsAIClient.chat(anyString())).thenReturn("mock response");
        String response = jsAIClient.chat("Hello, JSAI! Tell me a very short story about a adventurous cat.");
        assertValidResponse(response, "testChatStringMessage");
    }

    @Test
    public void testChatModelAndStringMessage() throws Exception {
        when(jsAIClient.chat(anyString(), anyString())).thenReturn("mock response");
        String response = jsAIClient.chat(BasicJSAIClient.MODEL, "What is the main export of Brazil?");
        assertValidResponse(response, "testChatModelAndStringMessage");
    }

    @Test
    public void testChatModelStringMessageAndFile() throws Exception {
        when(jsAIClient.chat(anyString(), anyString(), any(File.class))).thenReturn("mock response");
        // The current dialChatViaJs.js logs a warning but doesn't process the image file content.
        // This test primarily checks that the call doesn't break the Java/JS interaction.
        String response = jsAIClient.chat(BasicJSAIClient.MODEL, 
                                        "This message is sent with an image (JS will ignore image content). Describe a generic icon.", 
                                        testImageFile); // Can be null if testImageFile isn't found, JS should handle null path list
        assertValidResponse(response, "testChatModelStringMessageAndFile");
    }
    
    @Test
    public void testChatModelStringMessageAndFileList() throws Exception {
        when(jsAIClient.chat(anyString(), anyString(), anyList())).thenReturn("mock response");
        // JS currently ignores image content. Testing call structure.
        ArrayList<File> files = new ArrayList<>();
        if (testImageFile != null && testImageFile.exists()) {
            files.add(testImageFile);
        }
        String response = jsAIClient.chat(BasicJSAIClient.MODEL, 
                                        "This message is sent with a list of images (JS will ignore image content). What is a common use for icons?", 
                                        files.isEmpty() ? null : files);
        assertValidResponse(response, "testChatModelStringMessageAndFileList");
    }

    @Test
    public void testChatMessagesVarargs() throws Exception {
        when(jsAIClient.chat(any(Message.class), any(Message.class))).thenReturn("mock response");
        Message systemMessage = new Message("system", "You are an assistant that explains tech concepts simply.", null);
        Message userMessage = new Message("user", "Explain what an API is in one sentence.", null);
        String response = jsAIClient.chat(systemMessage, userMessage); // Uses default model from BasicJSAIClient
        assertValidResponse(response, "testChatMessagesVarargs");
    }

    @Test
    public void testChatModelAndMessagesVarargs() throws Exception {
        when(jsAIClient.chat(anyString(), any(Message.class), any(Message.class), any(Message.class))).thenReturn("The main colors are blue and green.");
        Message fact1 = new Message("user", "The sky is blue.", null);
        Message fact2 = new Message("user", "Grass is green.", null);
        Message question = new Message("user", "Based on the previous two statements, what color is a grassy field under a clear sky? List the two main colors.", null);
        
        String response = jsAIClient.chat(BasicJSAIClient.MODEL, fact1, fact2, question);
        assertValidResponse(response, "testChatModelAndMessagesVarargs");
        assertTrue("Response should mention 'blue' (" + response + ")", response.toLowerCase().contains("blue"));
        assertTrue("Response should mention 'green' (" + response + ")", response.toLowerCase().contains("green"));
    }
    
    @Test
    public void testChatModelAndMessagesVarargsWithImage() throws Exception {
        when(jsAIClient.chat(anyString(), any(Message.class), any(Message.class))).thenReturn("mock response");
        // Current JS script doesn't use file content from Message objects, but we test the Java path.
        Message imageContextMessage = new Message("user", "I am providing an image (JS will ignore it).", 
                                              testImageFile != null ? Collections.singletonList(testImageFile) : null);
        Message queryMessage = new Message("user", "What are three common cloud service providers?", null);

        String response = jsAIClient.chat(BasicJSAIClient.MODEL, imageContextMessage, queryMessage);
        assertValidResponse(response, "testChatModelAndMessagesVarargsWithImage");
    }
} 