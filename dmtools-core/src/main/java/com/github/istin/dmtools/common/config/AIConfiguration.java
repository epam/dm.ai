// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Composite configuration interface for all AI-related settings.
 * This interface combines all specific AI provider configurations.
 */
public interface AIConfiguration extends
        DialConfiguration,
        GeminiConfiguration,
        OllamaConfiguration,
        PromptConfiguration,
        AIRetryConfiguration,
        AIModelConfiguration,
        AIAttachmentConfiguration {
} 