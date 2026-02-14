package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.anthropic.BasicAnthropicAI;
import com.github.istin.dmtools.ai.bedrock.BasicBedrockAI;
import com.github.istin.dmtools.ai.google.BasicGeminiAI;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.ai.openai.BasicOpenAI;
import com.github.istin.dmtools.bridge.DMToolsBridge;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.utils.SecurityUtils;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.json.JSONObject;

import javax.script.ScriptException;
import freemarker.template.TemplateException;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Module
public class AIComponentsModule {

    private static final Logger logger = LogManager.getLogger(AIComponentsModule.class);

    @Provides
    @Singleton
    ConversationObserver provideConversationObserver() {
        return new ConversationObserver();
    }

    @Provides
    @Singleton
    DMToolsBridge provideDMToolsBridge() {
        return DMToolsBridge.withAllPermissions("DefaultBridge");
    }

    @Provides
    @Singleton
    AI provideAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        // Track which providers we've already tried to avoid redundant attempts
        boolean ollamaAttempted = false;
        boolean dialAttempted = false;
        boolean geminiAttempted = false;
        boolean anthropicAttempted = false;
        boolean bedrockAttempted = false;
        boolean openaiAttempted = false;
        
        // Check if a specific default LLM is configured
        String defaultLLM = configuration.getDefaultLLM();
        logger.info("DEFAULT_LLM value from config: '{}'", defaultLLM);
        
        // If DEFAULT_LLM is set, try to initialize that specific provider first
        if (defaultLLM != null && !defaultLLM.trim().isEmpty()) {
            logger.info("DEFAULT_LLM is set to: '{}', initializing preferred provider...", defaultLLM);
            
            if ("ollama".equalsIgnoreCase(defaultLLM.trim())) {
                String ollamaModel = configuration.getOllamaModel();
                if (ollamaModel != null && !ollamaModel.trim().isEmpty() && !ollamaModel.startsWith("$")) {
                    try {
                        logger.debug("Attempting to initialize AI via BasicOllamaAI as DEFAULT_LLM=ollama...");
                        AI ollama = new com.github.istin.dmtools.ai.ollama.BasicOllamaAI(observer, configuration);
                        logger.debug("BasicOllamaAI initialized successfully.");
                        return ollama;
                    } catch (Exception e) {
                        logger.error("Failed to initialize BasicOllamaAI (DEFAULT_LLM=ollama): " + e.getMessage());
                    }
                } else {
                    logger.warn("DEFAULT_LLM is set to 'ollama' but OLLAMA_MODEL is not configured. Skipping Ollama initialization.");
                }
                ollamaAttempted = true;
            } else if ("dial".equalsIgnoreCase(defaultLLM.trim())) {
                try {
                    logger.debug("Attempting to initialize AI via BasicDIAL as DEFAULT_LLM=dial...");
                    AI dial = new BasicDialAI(observer, configuration);
                    logger.debug("BasicDIAL initialized successfully.");
                    return dial;
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicDIAL (DEFAULT_LLM=dial): " + e.getMessage());
                }
                dialAttempted = true;
            } else if ("gemini".equalsIgnoreCase(defaultLLM.trim())) {
                try {
                    logger.debug("Attempting to initialize AI via BasicGeminiAI as DEFAULT_LLM=gemini...");
                    AI geminiAI = BasicGeminiAI.create(observer, configuration);
                    logger.debug("BasicGeminiAI initialized successfully.");
                    return geminiAI;
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicGeminiAI (DEFAULT_LLM=gemini): " + e.getMessage());
                }
                geminiAttempted = true;
            } else if ("anthropic".equalsIgnoreCase(defaultLLM.trim())) {
                String anthropicModel = configuration.getAnthropicModel();
                if (anthropicModel != null && !anthropicModel.trim().isEmpty() && !anthropicModel.startsWith("$")) {
                    try {
                        logger.debug("Attempting to initialize AI via BasicAnthropicAI as DEFAULT_LLM=anthropic...");
                        AI anthropic = new BasicAnthropicAI(observer, configuration);
                        logger.debug("BasicAnthropicAI initialized successfully.");
                        return anthropic;
                    } catch (Exception e) {
                        logger.error("Failed to initialize BasicAnthropicAI (DEFAULT_LLM=anthropic): " + e.getMessage());
                    }
                } else {
                    logger.warn("DEFAULT_LLM is set to 'anthropic' but ANTHROPIC_MODEL is not configured. Skipping Anthropic initialization.");
                }
                anthropicAttempted = true;
            } else if ("aws_bedrock".equalsIgnoreCase(defaultLLM.trim()) || "bedrock".equalsIgnoreCase(defaultLLM.trim())) {
                if (isBedrockConfigured(configuration)) {
                    try {
                        logger.debug("Attempting to initialize AI via BasicBedrockAI as DEFAULT_LLM=aws_bedrock...");
                        AI bedrock = new BasicBedrockAI(observer, configuration);
                        logger.debug("BasicBedrockAI initialized successfully.");
                        return bedrock;
                    } catch (Exception e) {
                        logger.error("Failed to initialize BasicBedrockAI (DEFAULT_LLM=aws_bedrock): " + e.getMessage());
                    }
                } else {
                    logger.warn("DEFAULT_LLM is set to 'aws_bedrock' but Bedrock is not properly configured. " +
                            "Required: BEDROCK_MODEL_ID and (BEDROCK_BASE_PATH or BEDROCK_REGION). " +
                            "Authentication: BEDROCK_BEARER_TOKEN, or (BEDROCK_ACCESS_KEY_ID and BEDROCK_SECRET_ACCESS_KEY), or Default Credentials with BEDROCK_REGION.");
                }
                bedrockAttempted = true;
            } else if ("openai".equalsIgnoreCase(defaultLLM.trim())) {
                if (isOpenAIConfigured(configuration)) {
                    try {
                        logger.debug("Attempting to initialize AI via BasicOpenAI as DEFAULT_LLM=openai...");
                        AI openai = new BasicOpenAI(observer, configuration);
                        logger.debug("BasicOpenAI initialized successfully.");
                        return openai;
                    } catch (Exception e) {
                        logger.error("Failed to initialize BasicOpenAI (DEFAULT_LLM=openai): " + e.getMessage());
                    }
                } else {
                    logger.warn("DEFAULT_LLM is set to 'openai' but OpenAI is not properly configured. " +
                            "Required: OPENAI_API_KEY and OPENAI_MODEL.");
                }
                openaiAttempted = true;
            }
        }

        // If DEFAULT_LLM is not set or initialization failed, use auto-detection based on available configuration
        // Check for Ollama configuration (skip if already attempted)
        if (!ollamaAttempted) {
            String ollamaModel = configuration.getOllamaModel();
            if (ollamaModel != null && !ollamaModel.trim().isEmpty() && !ollamaModel.startsWith("$")) {
                try {
                    logger.debug("Attempting to initialize AI via BasicOllamaAI as OLLAMA_MODEL is set...");
                    AI ollama = new com.github.istin.dmtools.ai.ollama.BasicOllamaAI(observer, configuration);
                    logger.debug("BasicOllamaAI initialized successfully.");
                    return ollama;
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicOllamaAI, trying fallback options. Error: " + e.getMessage());
                }
            }
        }
        
        // Check for Anthropic configuration (skip if already attempted)
        if (!anthropicAttempted) {
            String anthropicModel = configuration.getAnthropicModel();
            if (anthropicModel != null && !anthropicModel.trim().isEmpty() && !anthropicModel.startsWith("$")) {
                try {
                    logger.debug("Attempting to initialize AI via BasicAnthropicAI as ANTHROPIC_MODEL is set...");
                    AI anthropic = new BasicAnthropicAI(observer, configuration);
                    logger.debug("BasicAnthropicAI initialized successfully.");
                    return anthropic;
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicAnthropicAI, trying fallback options. Error: " + e.getMessage());
                }
            }
        }
        
        // Check for Bedrock configuration (skip if already attempted)
        if (!bedrockAttempted) {
            if (isBedrockConfigured(configuration)) {
                try {
                    logger.debug("Attempting to initialize AI via BasicBedrockAI as Bedrock is configured...");
                    AI bedrock = new BasicBedrockAI(observer, configuration);
                    logger.debug("BasicBedrockAI initialized successfully.");
                    return bedrock;
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicBedrockAI, trying fallback options. Error: " + e.getMessage());
                }
            }
        }

        // Check for OpenAI configuration (skip if already attempted)
        if (!openaiAttempted) {
            if (isOpenAIConfigured(configuration)) {
                try {
                    logger.debug("Attempting to initialize AI via BasicOpenAI as OpenAI is configured...");
                    AI openai = new BasicOpenAI(observer, configuration);
                    logger.debug("BasicOpenAI initialized successfully.");
                    return openai;
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicOpenAI, trying fallback options. Error: " + e.getMessage());
                }
            }
        }

        // 1. Attempt to initialize AI via BasicGeminiAI if GEMINI_API_KEY is configured
        // Skip Gemini if DialAI is explicitly preferred or if Gemini has known issues
        String geminiApiKey = configuration.getGeminiApiKey();
        String dialApiKey = configuration.getDialApiKey();
        
        // Prefer Dial if both are available (to avoid Gemini location restrictions)
        // Skip if already attempted via DEFAULT_LLM
        if (!dialAttempted && dialApiKey != null && !dialApiKey.trim().isEmpty() && !dialApiKey.startsWith("$")) {
            try {
                logger.debug("Attempting to initialize AI via BasicDIAL as DIAL_API_KEY is set...");
                AI dial = new BasicDialAI(observer, configuration);
                logger.debug("BasicDIAL initialized successfully.");
                return dial;
            } catch (Exception e) {
                logger.error("Failed to initialize BasicDIAL, trying fallback options. Error: " + e.getMessage());
            }
        }
        
        // Only try Gemini if Dial is not available and not already attempted
        if (!geminiAttempted && geminiApiKey != null && !geminiApiKey.trim().isEmpty() && !geminiApiKey.startsWith("$")) {
            try {
                logger.debug("Attempting to initialize AI via BasicGeminiAI as GEMINI_API_KEY is set...");
                AI geminiAI = BasicGeminiAI.create(observer, configuration);
                logger.debug("BasicGeminiAI initialized successfully.");
                return geminiAI;
            } catch (Exception e) {
                logger.error("Failed to initialize BasicGeminiAI, falling back. Error: " + e.getMessage());
                // Log details, but don't print stack trace to console in production, use a logger in real app
                // e.printStackTrace();
            }
        }

        // 2. Try to initialize JSAIClient using generic JSAI_SCRIPT_PATH if configured
        String jsScriptPath = configuration.getJsScriptPath();
        if (jsScriptPath != null && !jsScriptPath.trim().isEmpty()) {
            try {
                JSONObject configJson = new JSONObject();
                configJson.put("jsScriptPath", jsScriptPath);
                configJson.put("clientName", configuration.getJsClientName());

                String jsModel = configuration.getJsDefaultModel();
                configJson.put("defaultModel", jsModel != null ? jsModel : configuration.getDialModel());

                String jsBasePath = configuration.getJsBasePath();
                configJson.put("basePath", jsBasePath != null ? jsBasePath : configuration.getDialBathPath());

                JSONObject secretsJson = new JSONObject();
                String[] secretKeys = configuration.getJsSecretsKeys();
                if (secretKeys != null) {
                    for (String key : secretKeys) {
                        String value = configuration.getValue(key.trim());
                        if (value != null) {
                            secretsJson.put(key.trim(), value);
                        }
                    }
                }
                configJson.put("secrets", secretsJson);

                // Log the masked version to protect sensitive information
                JSONObject maskedConfig = SecurityUtils.maskSensitiveInformation(configJson);
                logger.debug("Attempting to initialize generic JSAIClient with config: " + maskedConfig.toString(2));
                AI jsAIClient = new JSAIClient(configJson, observer);
                logger.debug("Generic JSAIClient initialized successfully.");
                return jsAIClient;
            } catch (IOException | ScriptException | TemplateException e) {
                logger.error("Failed to initialize generic JSAIClient from properties, falling back. Error: " + e.getMessage());
                // e.printStackTrace();
            }
        }

        // 3. Fallback to AIProvider.getCustomAI()
        AI customAI = com.github.istin.dmtools.ai.AIProvider.getCustomAI();
        if (customAI != null) {
            logger.debug("Using Custom AI from AIProvider.");
            return customAI;
        }

        // 4. Default fallback to BasicDial
        try {
            logger.debug("Falling back to BasicDIAL.");
            AI dial = new BasicDialAI(observer, configuration);
            logger.debug("BasicDIAL initialized successfully.");
            return dial;
        } catch (IOException e) {
            logger.error("Failed to initialize BasicDIAL: " + e.getMessage());
            throw new RuntimeException("Failed to initialize BasicDIAL and no other AI clients were available.", e);
        }
    }

    @Provides
    @Singleton
    IPromptTemplateReader providePromptTemplateReader() {
        return new PromptManager();
    }
    
    /**
     * Static helper method to create AI instance without Dagger injection.
     * This allows reuse of the AI detection logic in non-Dagger contexts (e.g., CLI).
     * 
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return AI instance based on configuration
     */
    public static AI createAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        AIComponentsModule module = new AIComponentsModule();
        return module.provideAI(observer, configuration);
    }
    
    /**
     * Creates an Ollama AI client if configuration is available.
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return Ollama AI instance or null if not configured
     */
    public static AI createOllamaAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        String ollamaModel = configuration.getOllamaModel();
        if (ollamaModel != null && !ollamaModel.trim().isEmpty() && !ollamaModel.startsWith("$")) {
            try {
                return new com.github.istin.dmtools.ai.ollama.BasicOllamaAI(observer, configuration);
            } catch (Exception e) {
                logger.debug("Failed to create BasicOllamaAI: {}", e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Creates an Anthropic AI client if configuration is available.
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return Anthropic AI instance or null if not configured
     */
    public static AI createAnthropicAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        String anthropicModel = configuration.getAnthropicModel();
        if (anthropicModel != null && !anthropicModel.trim().isEmpty() && !anthropicModel.startsWith("$")) {
            try {
                return new BasicAnthropicAI(observer, configuration);
            } catch (Exception e) {
                logger.debug("Failed to create BasicAnthropicAI: {}", e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Creates a Gemini AI client if configuration is available.
     * Supports both Vertex AI (service account) and API key modes.
     * BasicGeminiAI.create() handles the selection logic internally.
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return Gemini AI instance or null if not configured
     */
    public static AI createGeminiAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        // Check if any Gemini configuration is available
        boolean hasVertexConfig = configuration.isGeminiVertexEnabled() &&
                configuration.getGeminiVertexProjectId() != null &&
                configuration.getGeminiVertexLocation() != null;
        boolean hasApiKeyConfig = configuration.getGeminiApiKey() != null &&
                !configuration.getGeminiApiKey().trim().isEmpty() &&
                !configuration.getGeminiApiKey().startsWith("$");

        if (!hasVertexConfig && !hasApiKeyConfig) {
            return null;
        }

        try {
            logger.debug("Creating Gemini AI (BasicGeminiAI.create() will select Vertex AI or API key mode)");
            return BasicGeminiAI.create(observer, configuration);
        } catch (Exception e) {
            logger.debug("Failed to create Gemini AI: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates a Dial AI client (always available as fallback).
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return Dial AI instance or null if creation fails
     */
    public static AI createDialAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        try {
            return new BasicDialAI(observer, configuration);
        } catch (Exception e) {
            logger.debug("Failed to create BasicDialAI: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if Bedrock is configured with any supported authentication method.
     * Supports: Bearer Token, IAM Keys, or Default Credentials.
     * 
     * @param configuration The application configuration
     * @return true if Bedrock is properly configured, false otherwise
     */
    private static boolean isBedrockConfigured(ApplicationConfiguration configuration) {
        String bedrockModelId = configuration.getBedrockModelId();
        String bedrockBasePath = configuration.getBedrockBasePath();
        String bedrockRegion = configuration.getBedrockRegion();
        
        // Check required fields: model ID and either base path or region
        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            return false;
        }
        
        if ((bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) &&
            (bedrockRegion == null || bedrockRegion.trim().isEmpty())) {
            return false;
        }
        
        // Check if at least one authentication method is configured
        String bedrockBearerToken = configuration.getBedrockBearerToken();
        String accessKeyId = configuration.getBedrockAccessKeyId();
        String secretAccessKey = configuration.getBedrockSecretAccessKey();
        
        // Priority 1: Bearer Token
        boolean hasBearerToken = bedrockBearerToken != null && 
                                 !bedrockBearerToken.trim().isEmpty() && 
                                 !bedrockBearerToken.startsWith("$");
        
        // Priority 2: IAM Keys
        boolean hasIAMKeys = accessKeyId != null && 
                            !accessKeyId.trim().isEmpty() && 
                            !accessKeyId.startsWith("$") &&
                            secretAccessKey != null && 
                            !secretAccessKey.trim().isEmpty() && 
                            !secretAccessKey.startsWith("$");
        
        // Priority 3: Default Credentials (requires region, credentials read from ~/.aws/credentials)
        boolean hasDefaultCredentials = bedrockRegion != null && !bedrockRegion.trim().isEmpty();
        
        return hasBearerToken || hasIAMKeys || hasDefaultCredentials;
    }
    
    /**
     * Creates a Bedrock AI client if configuration is available.
     * Supports all authentication methods: Bearer Token, IAM Keys, or Default Credentials.
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return Bedrock AI instance or null if not configured
     */
    public static AI createBedrockAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        if (isBedrockConfigured(configuration)) {
            try {
                return new BasicBedrockAI(observer, configuration);
            } catch (Exception e) {
                logger.debug("Failed to create BasicBedrockAI: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Checks if OpenAI is configured with required API key and model.
     *
     * @param configuration The application configuration
     * @return true if OpenAI is properly configured, false otherwise
     */
    private static boolean isOpenAIConfigured(ApplicationConfiguration configuration) {
        String apiKey = configuration.getOpenAIApiKey();
        String model = configuration.getOpenAIModel();

        // Check required fields: API key and model
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("$")) {
            return false;
        }

        if (model == null || model.trim().isEmpty() || model.startsWith("$")) {
            return false;
        }

        return true;
    }

    /**
     * Creates an OpenAI client if configuration is available.
     * @param observer The conversation observer
     * @param configuration The application configuration
     * @return OpenAI AI instance or null if not configured
     */
    public static AI createOpenAIAI(ConversationObserver observer, ApplicationConfiguration configuration) {
        if (isOpenAIConfigured(configuration)) {
            try {
                return new BasicOpenAI(observer, configuration);
            } catch (Exception e) {
                logger.debug("Failed to create BasicOpenAI: {}", e.getMessage());
            }
        }
        return null;
    }

}
