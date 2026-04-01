// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.logging;

/**
 * Callback interface for forwarding logs from CORE module to server module.
 * Enables execution monitoring without creating database dependencies in CORE.
 */
public interface LogCallback {
    /**
     * Called when a log entry needs to be forwarded to the server
     * @param executionId The unique execution ID for tracking
     * @param level The log level (DEBUG, INFO, WARN, ERROR)
     * @param message The log message
     * @param component The component name that generated the log
     */
    void onLog(String executionId, String level, String message, String component);
} 