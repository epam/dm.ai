package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Dial-specific settings.
 */
public interface DialConfiguration {
    /**
     * Gets the Dial base path URL
     * @return The Dial base path URL
     */
    String getDialBathPath();

    /**
     * Gets the Dial API key
     * @return The Dial API key
     */
    String getDialApiKey();

    /**
     * Gets the Dial model name
     * @return The Dial model name
     */
    String getDialModel();
} 