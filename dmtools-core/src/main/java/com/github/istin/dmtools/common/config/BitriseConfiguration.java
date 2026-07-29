// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Bitrise settings.
 */
public interface BitriseConfiguration {
    /**
     * Gets the Bitrise token
     * @return The Bitrise token
     */
    String getBitriseToken();

    /**
     * Gets the Bitrise base path URL
     * @return The Bitrise base path URL
     */
    String getBitriseBasePath();

    /**
     * Gets the Bitrise app slug
     * @return The Bitrise app slug
     */
    String getBitriseAppSlug();
}
