// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

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
     * Gets the Confluence email address for authentication
     * @return The Confluence email address
     */
    String getConfluenceEmail();
    
    /**
     * Gets the Confluence API token for authentication
     * @return The Confluence API token
     */
    String getConfluenceApiToken();
    
    /**
     * Gets the Confluence authentication type
     * @return The Confluence authentication type (Basic or Bearer)
     */
    String getConfluenceAuthType();

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