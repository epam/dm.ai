package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.common.utils.SecurityUtils;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BasicGeminiAI {

    private static final Logger logger = LogManager.getLogger(BasicGeminiAI.class);
    private static final String DEFAULT_GEMINI_CLIENT_NAME = "GeminiJSAIClientViaBasicGeminiAI";

    /**
     * Creates an AI instance using Gemini with the provided configuration
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return The AI instance
     */
    public static AI create(ConversationObserver observer, ApplicationConfiguration configuration) {
        if (configuration == null) {
            logger.error("ApplicationConfiguration cannot be null for BasicGeminiAI.create");
            throw new IllegalArgumentException("ApplicationConfiguration cannot be null");
        }

        JSONObject configJson = new JSONObject();
        // This script path will be loaded from src/main/resources/js/geminiChatViaJs.js
        configJson.put("jsScriptPath", "js/geminiChatViaJs.js");

        // Use JSAI_CLIENT_NAME from properties, or a specific default for Gemini
        String clientName = configuration.getJsClientName(); // getJsClientName has its own default
        if (clientName == null || clientName.equals(PropertyReader.DEFAULT_JSAI_CLIENT_NAME)) {
             // If JSAI_CLIENT_NAME is not set or is the generic default, use a more specific Gemini name
            clientName = DEFAULT_GEMINI_CLIENT_NAME;
        }
        configJson.put("clientName", clientName);

        String geminiModel = configuration.getGeminiDefaultModel();
        // getGeminiDefaultModel has its own default, so no need for null check & manual default here usually
        configJson.put("defaultModel", geminiModel);

        configJson.put("basePath", configuration.getGeminiBasePath());

        JSONObject secretsJson = new JSONObject();
        String apiKey = configuration.getGeminiApiKey();

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("$")) {
            String errorMessage = "GEMINI_API_KEY is missing, not set, or a placeholder in config.properties or environment variables. BasicGeminiAI cannot be initialized.";
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
        secretsJson.put("GEMINI_API_KEY", apiKey);
        configJson.put("secrets", secretsJson);

        try {
            // Log the masked version to protect sensitive information
            JSONObject maskedConfig = SecurityUtils.maskSensitiveInformation(configJson);
            logger.info("Initializing JSAIClient for Gemini via BasicGeminiAI with config: {}", maskedConfig.toString(2));
            return new JSAIClient(configJson, observer);
        } catch (Exception e) {
            logger.error("Failed to create JSAIClient for Gemini via BasicGeminiAI: {}", e.getMessage(), e);
            // Propagate as a runtime exception that AIComponentsModule can catch
            throw new RuntimeException("Failed to create JSAIClient for Gemini via BasicGeminiAI", e);
        }
    }
    
    /**
     * Creates an AI instance using Gemini with the provided PropertyReader (for backward compatibility)
     * @param observer The conversation observer
     * @param propertyReader The property reader
     * @return The AI instance
     */
    public static AI create(ConversationObserver observer, PropertyReader propertyReader) {
        if (propertyReader == null) {
            logger.error("PropertyReader cannot be null for BasicGeminiAI.create");
            throw new IllegalArgumentException("PropertyReader cannot be null");
        }

        JSONObject configJson = new JSONObject();
        // This script path will be loaded from src/main/resources/js/geminiChatViaJs.js
        configJson.put("jsScriptPath", "js/geminiChatViaJs.js");

        // Use JSAI_CLIENT_NAME from properties, or a specific default for Gemini
        String clientName = propertyReader.getJsClientName(); // getJsClientName has its own default
        if (clientName == null || clientName.equals(PropertyReader.DEFAULT_JSAI_CLIENT_NAME)) {
             // If JSAI_CLIENT_NAME is not set or is the generic default, use a more specific Gemini name
            clientName = DEFAULT_GEMINI_CLIENT_NAME;
        }
        configJson.put("clientName", clientName);

        String geminiModel = propertyReader.getGeminiDefaultModel();
        // getGeminiDefaultModel has its own default, so no need for null check & manual default here usually
        configJson.put("defaultModel", geminiModel);

        configJson.put("basePath", propertyReader.getGeminiBasePath());

        JSONObject secretsJson = new JSONObject();
        String apiKey = propertyReader.getGeminiApiKey();

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("$")) {
            String errorMessage = "GEMINI_API_KEY is missing, not set, or a placeholder in config.properties or environment variables. BasicGeminiAI cannot be initialized.";
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
        secretsJson.put("GEMINI_API_KEY", apiKey);
        configJson.put("secrets", secretsJson);

        try {
            // Log the masked version to protect sensitive information
            JSONObject maskedConfig = SecurityUtils.maskSensitiveInformation(configJson);
            logger.info("Initializing JSAIClient for Gemini via BasicGeminiAI with config: {}", maskedConfig.toString(2));
            return new JSAIClient(configJson, observer);
        } catch (Exception e) {
            logger.error("Failed to create JSAIClient for Gemini via BasicGeminiAI: {}", e.getMessage(), e);
            // Propagate as a runtime exception that AIComponentsModule can catch
            throw new RuntimeException("Failed to create JSAIClient for Gemini via BasicGeminiAI", e);
        }
    }
} 