package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public interface AI {

    void setMetadata(Metadata metadata);

    String chat(String message) throws Exception;

    String chat(String model, String message) throws Exception;

    String chat(String model, String message, File imageFile) throws Exception;

    String chat(String model, String message, List<File> files) throws Exception;

    String chat(String model, Message... messages) throws Exception;

    String chat(Message... messages) throws Exception;

    /**
     * Returns the role name used by this AI provider for assistant/model responses.
     * This allows clients to use a consistent role naming while the AI provider
     * handles the appropriate mapping internally.
     * 
     * @return "assistant" for OpenAI/Dial integrations, "model" for Gemini integration
     */
    String roleName();

    /**
     * Converts message roles to match this AI provider's expected role naming.
     * Centralizes role mapping logic to support both "assistant" and "model" 
     * from clients while ensuring provider-specific role names are used internally.
     * Modifies roles in-place for optimal performance.
     * 
     * @param messages Array of messages that may contain mixed role names
     * @return The same array with roles converted to provider-specific naming
     */
    default Message[] normalizeMessageRoles(Message... messages) {
        if (messages == null || messages.length == 0) {
            return messages;
        }
        
        String expectedRole = roleName();
        
        // Directly modify roles in-place - much more efficient than creating new objects
        for (Message message : messages) {
            String role = message.getRole();
            // Convert both "assistant" and "model" to provider-specific role
            if (("assistant".equals(role) || "model".equals(role)) && !expectedRole.equals(role)) {
                message.setRole(expectedRole);
            }
            // Other roles (user, system, etc.) remain unchanged
        }
        
        return messages; // Return the same array with modified roles
    }

    class Utils {

        public static Boolean chatAsBoolean(AI ai, String model, String message) throws Exception {
            return Boolean.parseBoolean(ai.chat(model, message));
        }

        public static JSONArray chatAsJSONArray(AI ai, String model, String message) throws Exception {
            return AIResponseParser.parseResponseAsJSONArray(ai.chat(model, message, (File) null));
        }

        public static JSONArray chatAsJSONArray(AI ai, String message) throws Exception {
            return chatAsJSONArray(ai, null, message);
        }

        public static JSONObject chatAsJSONObject(AI ai, String model, String message) throws Exception {
            return AIResponseParser.parseResponseAsJSONObject(ai.chat(model, message, (File) null));
        }

        public static JSONObject chatAsJSONObject(AI ai, String message) throws Exception {
            return chatAsJSONObject(ai, null, message);
        }

        public static boolean chatAsBoolean(AI ai, String message) throws Exception {
            return chatAsBoolean(ai, null, message);
        }

    }

}
