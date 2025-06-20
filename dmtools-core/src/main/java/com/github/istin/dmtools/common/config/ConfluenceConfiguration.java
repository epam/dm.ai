package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Confluence settings.
 */
public interface ConfluenceConfiguration {
    /**
     * Gets the Confluence base path
     * @return The Confluence base path
     */
    String getConfluenceBasePath();

    /**
     * Gets the Confluence login/password token
     * @return The Confluence login/password token
     */
    String getConfluenceLoginPassToken();

    /**
     * Gets the Confluence GraphQL path
     * @return The Confluence GraphQL path
     */
    String getConfluenceGraphQLPath();

    /**
     * Gets the default Confluence space
     * @return The default Confluence space
     */
    String getConfluenceDefaultSpace();
} 