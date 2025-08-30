package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.ai.dial.DialAIClient;
import com.github.istin.dmtools.ai.google.GeminiJSAIClient;
import com.github.istin.dmtools.ai.js.JSAIClient;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class RoleConversionTest {

    @Test
    public void testDialAIClientRoleConversion() throws Exception {
        DialAIClient dialClient = new DialAIClient("https://example.com", "test-key", "test-model");
        
        // Create messages with mixed role names
        Message[] originalMessages = {
            new Message("user", "Hello", null),
            new Message("model", "Hi there", null),      // Should convert to "assistant"
            new Message("assistant", "How are you?", null), // Should stay "assistant"
            new Message("system", "You are helpful", null)  // Should stay "system"
        };
        
        Message[] normalizedMessages = dialClient.normalizeMessageRoles(originalMessages);
        
        // Should return the same array instance (in-place modification)
        assertSame(originalMessages, normalizedMessages);
        
        // Verify conversions (messages were modified in-place)
        assertEquals("user", normalizedMessages[0].getRole());
        assertEquals("assistant", normalizedMessages[1].getRole()); // "model" -> "assistant"
        assertEquals("assistant", normalizedMessages[2].getRole()); // "assistant" stays
        assertEquals("system", normalizedMessages[3].getRole());    // "system" stays
        
        // Verify text and files are preserved
        assertEquals("Hi there", normalizedMessages[1].getText());
        assertEquals("How are you?", normalizedMessages[2].getText());
        assertEquals("You are helpful", normalizedMessages[3].getText());
    }

    @Test
    public void testGeminiJSAIClientRoleConversion() throws Exception {
        JSONObject configJson = new JSONObject();
        configJson.put("jsScriptPath", "js/geminiChatViaJs.js");
        configJson.put("clientName", "TestGeminiClient");
        configJson.put("defaultModel", "gemini-pro");
        
        JSONObject secretsJson = new JSONObject();
        secretsJson.put("GEMINI_API_KEY", "test-key");
        configJson.put("secrets", secretsJson);
        
        try {
            GeminiJSAIClient geminiClient = new GeminiJSAIClient(configJson, null);
            
            // Create messages with mixed role names
            Message[] originalMessages = {
                new Message("user", "Hello", null),
                new Message("assistant", "Hi there", null),    // Should convert to "model"
                new Message("model", "How are you?", null),    // Should stay "model"
                new Message("system", "You are helpful", null) // Should stay "system"
            };
            
            Message[] normalizedMessages = geminiClient.normalizeMessageRoles(originalMessages);
            
            // Verify conversions
            assertEquals("user", normalizedMessages[0].getRole());
            assertEquals("model", normalizedMessages[1].getRole());    // "assistant" -> "model"
            assertEquals("model", normalizedMessages[2].getRole());    // "model" stays
            assertEquals("system", normalizedMessages[3].getRole());   // "system" stays
            
            // Verify text is preserved
            assertEquals("Hi there", normalizedMessages[1].getText());
            assertEquals("How are you?", normalizedMessages[2].getText());
            assertEquals("You are helpful", normalizedMessages[3].getText());
            
            System.out.println("Successfully tested Gemini role conversion");
        } catch (Exception e) {
            if (e.getMessage().contains("Graal.js script engine not found")) {
                System.out.println("GraalJS not available in test environment - this is expected");
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testNoConversionNeeded() throws Exception {
        DialAIClient dialClient = new DialAIClient("https://example.com", "test-key", "test-model");
        
        // Create messages that don't need conversion
        Message[] originalMessages = {
            new Message("user", "Hello", null),
            new Message("assistant", "Hi there", null),
            new Message("system", "You are helpful", null)
        };
        
        Message[] normalizedMessages = dialClient.normalizeMessageRoles(originalMessages);
        
        // Should always return the same array instance (in-place modification)
        assertSame(originalMessages, normalizedMessages);
        
        // Roles should remain unchanged since they're already correct
        assertEquals("user", normalizedMessages[0].getRole());
        assertEquals("assistant", normalizedMessages[1].getRole());
        assertEquals("system", normalizedMessages[2].getRole());
    }

    @Test
    public void testEmptyMessagesHandling() throws Exception {
        DialAIClient dialClient = new DialAIClient("https://example.com", "test-key", "test-model");
        
        // Test null messages
        Message[] result = dialClient.normalizeMessageRoles((Message[]) null);
        assertNull(result);
        
        // Test empty array
        Message[] emptyMessages = new Message[0];
        Message[] emptyResult = dialClient.normalizeMessageRoles(emptyMessages);
        assertSame(emptyMessages, emptyResult);
    }

    @Test
    public void testPerformanceOptimization() throws Exception {
        DialAIClient dialClient = new DialAIClient("https://example.com", "test-key", "test-model");
        
        // Large array with mixed roles for conversion
        Message[] largeArray = new Message[1000];
        for (int i = 0; i < 1000; i++) {
            String role = (i % 2 == 0) ? "model" : "assistant"; // Mix of roles
            largeArray[i] = new Message(role, "Message " + i, null);
        }
        
        long startTime = System.nanoTime();
        Message[] result = dialClient.normalizeMessageRoles(largeArray);
        long endTime = System.nanoTime();
        
        // Should always return same array instance (in-place modification)
        assertSame(largeArray, result);
        
        // Should be very fast even with conversion (in-place modification is efficient)
        long durationMs = (endTime - startTime) / 1_000_000;
        assertTrue("Performance test: in-place modification should be fast", durationMs < 10);
        
        // Verify all roles were converted to "assistant"
        for (Message message : result) {
            assertEquals("assistant", message.getRole());
        }
        
        System.out.println("Performance test passed: " + durationMs + "ms for 1000 messages with in-place conversion");
    }

    @Test
    public void testCustomJSAIClientRoleConversion() throws Exception {
        // Test with custom JS script that returns custom role name
        JSONObject configJson = new JSONObject();
        configJson.put("jsScript", 
            "function handleChat() { return 'test'; } " +
            "function getRoleName() { return 'custom_assistant'; }");
        configJson.put("clientName", "TestCustomClient");
        configJson.put("defaultModel", "test-model");
        
        try {
            JSAIClient jsClient = new JSAIClient(configJson, null);
            
            Message[] originalMessages = {
                new Message("user", "Hello", null),
                new Message("assistant", "Hi", null),     // Should convert to "custom_assistant"
                new Message("model", "How are you?", null), // Should convert to "custom_assistant"
                new Message("system", "You are helpful", null) // Should stay "system"
            };
            
            Message[] normalizedMessages = jsClient.normalizeMessageRoles(originalMessages);
            
            // Verify conversions to custom role name
            assertEquals("user", normalizedMessages[0].getRole());
            assertEquals("custom_assistant", normalizedMessages[1].getRole()); // "assistant" -> "custom_assistant"
            assertEquals("custom_assistant", normalizedMessages[2].getRole()); // "model" -> "custom_assistant"
            assertEquals("system", normalizedMessages[3].getRole());           // "system" stays
            
            System.out.println("Successfully tested custom JSAIClient role conversion");
        } catch (Exception e) {
            if (e.getMessage().contains("Graal.js script engine not found")) {
                System.out.println("GraalJS not available in test environment - this is expected");
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testMessageWithFiles() throws Exception {
        DialAIClient dialClient = new DialAIClient("https://example.com", "test-key", "test-model");
        
        // Create message with files
        Message messageWithFiles = new Message("model", "Analyze this image", 
            Collections.singletonList(new java.io.File("test.jpg")));
        
        Message[] originalMessages = { messageWithFiles };
        Message[] normalizedMessages = dialClient.normalizeMessageRoles(originalMessages);
        
        // Verify role conversion and file preservation
        assertEquals("assistant", normalizedMessages[0].getRole());
        assertEquals("Analyze this image", normalizedMessages[0].getText());
        assertNotNull(normalizedMessages[0].getFiles());
        assertEquals(1, normalizedMessages[0].getFiles().size());
        assertEquals("test.jpg", normalizedMessages[0].getFiles().get(0).getName());
    }
}
