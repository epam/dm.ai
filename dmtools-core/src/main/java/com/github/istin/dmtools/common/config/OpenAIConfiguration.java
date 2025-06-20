package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for OpenAI-specific settings.
 */
public interface OpenAIConfiguration {
    /**
     * Gets the OpenAI base path URL
     * @return The OpenAI base path URL
     */
    String getOpenAIBathPath();

    /**
     * Gets the OpenAI API key
     * @return The OpenAI API key
     */
    String getOpenAIApiKey();

    /**
     * Gets the OpenAI model name
     * @return The OpenAI model name
     */
    String getOpenAIModel();
} 