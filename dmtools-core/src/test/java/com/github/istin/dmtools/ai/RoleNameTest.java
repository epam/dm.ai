package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.ai.dial.DialAIClient;
import com.github.istin.dmtools.ai.google.GeminiJSAIClient;
import com.github.istin.dmtools.ai.js.JSAIClient;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RoleNameTest {

    @Test
    public void testDialAIClientRoleName() throws Exception {
        DialAIClient dialClient = new DialAIClient("https://example.com", "test-key", "test-model");
        assertEquals("assistant", dialClient.roleName());
    }

    @Test
    public void testJSAIClientFallbackRoleName() throws Exception {
        // Test with a script that doesn't implement getRoleName()
        JSONObject configJson = new JSONObject();
        configJson.put("jsScript", "function handleChat() { return 'test'; }"); // Simple script without getRoleName
        configJson.put("clientName", "TestClient");
        configJson.put("defaultModel", "test-model");
        
        try {
            JSAIClient jsClient = new JSAIClient(configJson, null);
            // Should fallback to "assistant" when getRoleName() is not implemented
            assertEquals("assistant", jsClient.roleName());
            System.out.println("Successfully tested JSAIClient fallback to 'assistant'");
        } catch (Exception e) {
            if (e.getMessage().contains("Graal.js script engine not found")) {
                System.out.println("GraalJS not available in test environment - this is expected");
            } else {
                throw e;
            }
        }
    }

    @Test 
    public void testGeminiJSAIClientRoleName() throws Exception {
        JSONObject configJson = new JSONObject();
        configJson.put("jsScriptPath", "js/geminiChatViaJs.js");
        configJson.put("clientName", "TestGeminiClient");
        configJson.put("defaultModel", "gemini-pro");
        
        JSONObject secretsJson = new JSONObject();
        secretsJson.put("GEMINI_API_KEY", "test-key");
        configJson.put("secrets", secretsJson);
        
        try {
            GeminiJSAIClient geminiClient = new GeminiJSAIClient(configJson, null);
            // Should delegate to JavaScript and return "model" from geminiChatViaJs.js
            assertEquals("model", geminiClient.roleName());
            System.out.println("Successfully tested GeminiJSAIClient returns 'model'");
        } catch (Exception e) {
            if (e.getMessage().contains("Graal.js script engine not found")) {
                System.out.println("GraalJS not available in test environment - this is expected");
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testJSAIClientWithCustomRoleName() throws Exception {
        // Test with a script that implements a custom getRoleName()
        JSONObject configJson = new JSONObject();
        configJson.put("jsScript", 
            "function handleChat() { return 'test'; } " +
            "function getRoleName() { return 'custom_role'; }");
        configJson.put("clientName", "TestCustomClient");
        configJson.put("defaultModel", "test-model");
        
        try {
            JSAIClient jsClient = new JSAIClient(configJson, null);
            assertEquals("custom_role", jsClient.roleName());
            System.out.println("Successfully tested JSAIClient custom role name delegation");
        } catch (Exception e) {
            if (e.getMessage().contains("Graal.js script engine not found")) {
                System.out.println("GraalJS not available in test environment - this is expected");
            } else {
                throw e;
            }
        }
    }
}
