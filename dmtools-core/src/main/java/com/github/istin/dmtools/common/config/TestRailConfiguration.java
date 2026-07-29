// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for TestRail settings.
 */
public interface TestRailConfiguration {
    /**
     * Gets the TestRail base path URL
     * @return The TestRail base path URL
     */
    String getTestRailBasePath();

    /**
     * Gets the TestRail username
     * @return The TestRail username
     */
    String getTestRailUsername();

    /**
     * Gets the TestRail API key
     * @return The TestRail API key
     */
    String getTestRailApiKey();

    /**
     * Gets the TestRail project
     * @return The TestRail project
     */
    String getTestRailProject();

    /**
     * Checks if TestRail logging is enabled
     * @return true if TestRail logging is enabled
     */
    boolean isTestRailLoggingEnabled();
}
