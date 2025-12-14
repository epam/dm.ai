package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BasicBedrockAI extends BedrockAIClient {
    
    public BasicBedrockAI() throws IOException {
        this(null);
    }

    public BasicBedrockAI(ConversationObserver conversationObserver) throws IOException {
        this(conversationObserver, new com.github.istin.dmtools.common.config.PropertyReaderConfiguration());
    }
    
    /**
     * Creates a new BasicBedrockAI instance with the provided configuration
     * @param conversationObserver The conversation observer
     * @param configuration The application configuration
     * @throws IOException If an I/O error occurs
     */
    public BasicBedrockAI(ConversationObserver conversationObserver, ApplicationConfiguration configuration) throws IOException {
        super(
            configuration.getBedrockBasePath(),
            configuration.getBedrockRegion(),
            configuration.getBedrockModelId(),
            configuration.getBedrockBearerToken(),
            configuration.getBedrockMaxTokens(),
            configuration.getBedrockTemperature(),
            conversationObserver,
            null, // Custom headers not supported in BasicBedrockAI for now
            null  // Logger will be created by parent class
        );
    }
}
