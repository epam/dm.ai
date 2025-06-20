package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for AI model selection settings.
 */
public interface AIModelConfiguration {
    /**
     * Gets the AI model for code-related tasks
     * @return The AI model for code-related tasks
     */
    String getCodeAIModel();

    /**
     * Gets the AI model for test-related tasks
     * @return The AI model for test-related tasks
     */
    String getTestAIModel();
} 