package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;

import java.io.IOException;

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
        super(createBedrockClientFromConfig(conversationObserver, configuration));
    }
    
    /**
     * Helper method to create BedrockAIClient instance based on available credentials.
     * Priority: Bearer Token > IAM Keys > Default Credentials
     * 
     * This method is used to work around Java's limitation that super() must be the first statement.
     * We create the appropriate BedrockAIClient instance and then delegate to it.
     */
    private static BedrockAIClient createBedrockClientFromConfig(
            ConversationObserver conversationObserver, 
            ApplicationConfiguration configuration) throws IOException {
        
        String basePath = configuration.getBedrockBasePath();
        String region = configuration.getBedrockRegion();
        String modelId = configuration.getBedrockModelId();
        int maxTokens = configuration.getBedrockMaxTokens();
        double temperature = configuration.getBedrockTemperature();
        
        // Determine authentication method based on available credentials
        String bearerToken = configuration.getBedrockBearerToken();
        String accessKeyId = configuration.getBedrockAccessKeyId();
        String secretAccessKey = configuration.getBedrockSecretAccessKey();
        String sessionToken = configuration.getBedrockSessionToken();
        
        // Priority 1: Bearer Token (if available)
        if (bearerToken != null && !bearerToken.trim().isEmpty() && !bearerToken.startsWith("$")) {
            return new BedrockAIClient(
                basePath,
                region,
                modelId,
                bearerToken,
                maxTokens,
                temperature,
                conversationObserver,
                null, // Custom headers not supported in BasicBedrockAI for now
                null  // Logger will be created by parent class
            );
        }
        // Priority 2: IAM Access Keys (if both accessKeyId and secretAccessKey are available)
        else if (accessKeyId != null && !accessKeyId.trim().isEmpty() && !accessKeyId.startsWith("$") &&
                 secretAccessKey != null && !secretAccessKey.trim().isEmpty() && !secretAccessKey.startsWith("$")) {
            return new BedrockAIClient(
                basePath,
                region,
                modelId,
                accessKeyId,
                secretAccessKey,
                sessionToken, // May be null for permanent credentials
                maxTokens,
                temperature,
                conversationObserver,
                null, // Custom headers not supported in BasicBedrockAI for now
                null  // Logger will be created by parent class
            );
        }
        // Priority 3: Default Credentials Provider (reads from ~/.aws/credentials)
        else {
            return new BedrockAIClient(
                basePath,
                region,
                modelId,
                maxTokens,
                temperature,
                conversationObserver,
                null, // Custom headers not supported in BasicBedrockAI for now
                null  // Logger will be created by parent class
            );
        }
    }
}
