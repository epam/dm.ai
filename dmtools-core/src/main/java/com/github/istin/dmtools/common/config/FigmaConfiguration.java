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
} 