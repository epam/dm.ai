// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Anthropic-specific settings.
 */
public interface AnthropicConfiguration {
    /**
     * Gets the Anthropic base path URL
     * @return The Anthropic base path URL
     */
    String getAnthropicBasePath();

    /**
     * Gets the Anthropic model name
     * @return The Anthropic model name
     */
    String getAnthropicModel();

    /**
     * Gets the Anthropic max tokens
     * @return The max tokens for responses
     */
    int getAnthropicMaxTokens();
    /**
     * Gets the Anthropic custom header names
     * @return Comma-separated custom header names
     */
    String getAnthropicCustomHeaderNames();

    /**
     * Gets the Anthropic custom header values
     * @return Comma-separated custom header values
     */
    String getAnthropicCustomHeaderValues();
}

