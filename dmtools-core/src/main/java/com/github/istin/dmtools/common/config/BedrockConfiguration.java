package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for AWS Bedrock-specific settings.
 */
public interface BedrockConfiguration {
    /**
     * Gets the AWS Bedrock base path URL
     * @return The AWS Bedrock base path URL
     */
    String getBedrockBasePath();

    /**
     * Gets the AWS Bedrock region
     * @return The AWS Bedrock region
     */
    String getBedrockRegion();

    /**
     * Gets the AWS Bedrock model ID
     * @return The AWS Bedrock model ID
     */
    String getBedrockModelId();

    /**
     * Gets the AWS Bedrock bearer token
     * @return The AWS Bedrock bearer token
     */
    String getBedrockBearerToken();

    /**
     * Gets the AWS Bedrock max tokens
     * @return The max tokens for responses
     */
    int getBedrockMaxTokens();

    /**
     * Gets the AWS Bedrock temperature
     * @return The temperature value (0.0-1.0)
     */
    double getBedrockTemperature();
}
