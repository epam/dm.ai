package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Rally settings.
 */
public interface RallyConfiguration {
    /**
     * Gets the Rally token
     * @return The Rally token
     */
    String getRallyToken();

    /**
     * Gets the Rally path
     * @return The Rally path
     */
    String getRallyPath();
} 