// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Xray settings.
 */
public interface XrayConfiguration {
    /**
     * Gets the Xray client ID
     * @return The Xray client ID
     */
    String getXrayClientId();

    /**
     * Gets the Xray client secret
     * @return The Xray client secret
     */
    String getXrayClientSecret();

    /**
     * Gets the Xray base path URL
     * @return The Xray base path URL
     */
    String getXrayBasePath();
}
