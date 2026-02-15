package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.common.utils.SecurityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

public class BasicGeminiAI {

    private static final Logger logger = LogManager.getLogger(BasicGeminiAI.class);
    private static final String DEFAULT_GEMINI_CLIENT_NAME = "GeminiJSAIClientViaBasicGeminiAI";

    /**
     * Creates an AI instance using Gemini with the provided configuration.
     * Automatically selects between Vertex AI (service account) and public API (API key) modes.
     *
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return The AI instance
     */
    public static AI create(ConversationObserver observer, ApplicationConfiguration configuration) {
        if (configuration == null) {
            logger.error("ApplicationConfiguration cannot be null for BasicGeminiAI.create");
            throw new IllegalArgumentException("ApplicationConfiguration cannot be null");
        }

        // Priority 1: Check if Vertex AI is enabled
        if (configuration.isGeminiVertexEnabled()) {
            String projectId = configuration.getGeminiVertexProjectId();
            String location = configuration.getGeminiVertexLocation();

            if (isNotEmpty(projectId) && isNotEmpty(location)) {
                try {
                    logger.info("GEMINI_VERTEX_ENABLED=true, initializing VertexAIGeminiClient (service account mode)");
                    return createVertexAIClient(observer, configuration);
                } catch (Exception e) {
                    logger.warn("Failed to create VertexAIGeminiClient: {}. Attempting fallback to API key mode.", e.getMessage());
                    logger.debug("Vertex AI creation error details:", e);
                    // Fall through to API key mode if available
                }
            } else {
                logger.warn("GEMINI_VERTEX_ENABLED=true but project ID or location missing. Attempting fallback to API key mode.");
            }
        }

        // Priority 2: Fall back to API key mode (existing GeminiJSAIClient)
        logger.info("Initializing GeminiJSAIClient (API key mode)");
        return createJSAIClient(observer, configuration);
    }

    /**
     * Creates a Vertex AI Gemini client with service account authentication.
     *
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return VertexAIGeminiClient instance
     * @throws Exception if Vertex AI client cannot be created
     */
    private static AI createVertexAIClient(ConversationObserver observer, ApplicationConfiguration configuration)
            throws Exception {
        String projectId = configuration.getGeminiVertexProjectId();
        String location = configuration.getGeminiVertexLocation();
        String credentialsPath = configuration.getGeminiVertexCredentialsPath();
        String credentialsJson = configuration.getGeminiVertexCredentialsJson();
        String model = configuration.getGeminiDefaultModel();
        String apiVersion = configuration.getGeminiVertexApiVersion();

        if (model == null || model.trim().isEmpty()) {
            throw new IllegalStateException("GEMINI_MODEL is not configured. Please set GEMINI_MODEL or GEMINI_DEFAULT_MODEL in environment variables or config file.");
        }

        if (isNotEmpty(credentialsPath)) {
            logger.info("Using Vertex AI credentials from file: {}", credentialsPath);
            return new VertexAIGeminiClient(projectId, location, model, credentialsPath, observer, null, apiVersion);
        } else if (isNotEmpty(credentialsJson)) {
            logger.info("Using Vertex AI credentials from JSON string");
            return new VertexAIGeminiClient(projectId, location, model, observer, credentialsJson, null, apiVersion);
        } else {
            throw new IllegalStateException("GEMINI_VERTEX_ENABLED=true but no credentials provided (need GEMINI_VERTEX_CREDENTIALS_PATH or GEMINI_VERTEX_CREDENTIALS_JSON)");
        }
    }

    /**
     * Creates a GeminiJSAIClient with API key authentication (existing implementation).
     *
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return GeminiJSAIClient instance
     */
    private static AI createJSAIClient(ConversationObserver observer, ApplicationConfiguration configuration) {

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
        if (geminiModel == null || geminiModel.trim().isEmpty()) {
            String errorMessage = "GEMINI_MODEL is not configured. Please set GEMINI_MODEL or GEMINI_DEFAULT_MODEL in environment variables or config file.";
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
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
            logger.info("Initializing GeminiJSAIClient via BasicGeminiAI with config: {}", maskedConfig.toString(2));
            return new GeminiJSAIClient(configJson, observer);
        } catch (Exception e) {
            logger.error("Failed to create GeminiJSAIClient via BasicGeminiAI: {}", e.getMessage(), e);
            // Propagate as a runtime exception that AIComponentsModule can catch
            throw new RuntimeException("Failed to create GeminiJSAIClient via BasicGeminiAI", e);
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
        if (geminiModel == null || geminiModel.trim().isEmpty()) {
            String errorMessage = "GEMINI_MODEL is not configured. Please set GEMINI_MODEL or GEMINI_DEFAULT_MODEL in environment variables or config file.";
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
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
            logger.info("Initializing GeminiJSAIClient via BasicGeminiAI with config: {}", maskedConfig.toString(2));
            return new GeminiJSAIClient(configJson, observer);
        } catch (Exception e) {
            logger.error("Failed to create GeminiJSAIClient via BasicGeminiAI: {}", e.getMessage(), e);
            // Propagate as a runtime exception that AIComponentsModule can catch
            throw new RuntimeException("Failed to create GeminiJSAIClient via BasicGeminiAI", e);
        }
    }

    /**
     * Helper method to check if a string is not null and not empty.
     *
     * @param str The string to check
     * @return true if string is not null and not empty
     */
    private static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }
}