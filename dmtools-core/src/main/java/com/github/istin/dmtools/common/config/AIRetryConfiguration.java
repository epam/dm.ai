package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for AI retry-related settings.
 */
public interface AIRetryConfiguration {
    /**
     * Gets the number of retry attempts for AI operations
     * @return The number of retry attempts
     */
    int getAiRetryAmount();

    /**
     * Gets the delay step in milliseconds between retry attempts
     * @return The delay step in milliseconds
     */
    long getAiRetryDelayStep();
} 