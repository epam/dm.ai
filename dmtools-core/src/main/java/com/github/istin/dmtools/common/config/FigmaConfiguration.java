// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

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
    /**
     * Gets the OAuth2 access token for Figma (alternative to API key)
     * @return The OAuth2 access token, or null if not configured
     */
    String getFigmaOAuth2AccessToken();

    /**
     * Gets the OAuth2 refresh token for Figma
     * @return The OAuth2 refresh token, or null if not configured
     */
    String getFigmaOAuth2RefreshToken();
} 