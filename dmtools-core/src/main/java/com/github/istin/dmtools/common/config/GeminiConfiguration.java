package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Gemini-specific settings.
 */
public interface GeminiConfiguration {
    /**
     * Gets the Gemini API key
     * @return The Gemini API key
     */
    String getGeminiApiKey();
    
    /**
     * Gets the default Gemini model name
     * @return The default Gemini model name
     */
    String getGeminiDefaultModel();
    
    /**
     * Gets the Gemini base path URL
     * @return The Gemini base path URL
     */
    String getGeminiBasePath();
} 