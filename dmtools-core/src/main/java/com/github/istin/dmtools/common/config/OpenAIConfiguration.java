// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for OpenAI-specific settings.
 */
public interface OpenAIConfiguration {
    /**
     * Gets the OpenAI API key
     * @return The OpenAI API key
     */
    String getOpenAIApiKey();

    /**
     * Gets the OpenAI base path URL
     * @return The OpenAI base path URL
     */
    String getOpenAIBasePath();

    /**
     * Gets the OpenAI model name
     * @return The OpenAI model name (e.g., gpt-4, gpt-3.5-turbo)
     */
    String getOpenAIModel();

    /**
     * Gets the OpenAI max tokens
     * @return The max tokens for responses
     */
    int getOpenAIMaxTokens();

    /**
     * Gets the OpenAI temperature
     * @return The temperature value (0.0-2.0)
     */
    double getOpenAITemperature();

    /**
     * Gets the parameter name for max tokens
     * @return The parameter name: "max_completion_tokens" (default for newer models), "max_tokens" (older models), or empty to skip
     */
    String getOpenAIMaxTokensParamName();
}
