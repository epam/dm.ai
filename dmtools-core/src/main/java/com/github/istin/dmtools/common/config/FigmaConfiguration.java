package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Figma settings.
 */
public interface FigmaConfiguration {
    /**
     * Gets the Figma base path
     * @return The Figma base path
     */
    String getFigmaBasePath();

    /**
     * Gets the Figma API key
     * @return The Figma API key
     */
    String getFigmaApiKey();
} 