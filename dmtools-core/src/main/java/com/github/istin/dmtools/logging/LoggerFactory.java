// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory for creating loggers with optional callback functionality.
 * Used by ServerManagedIntegrationsModule to inject monitoring-enabled loggers.
 */
public class LoggerFactory {
    
    /**
     * Creates a logger for the given class.
     * If executionId and callback are provided, wraps with callback functionality.
     * Otherwise returns a standard logger.
     */
    public static Object createLogger(Class<?> clazz, String executionId, LogCallback callback) {
        if (executionId != null && callback != null) {
            return new CallbackLogger(clazz, executionId, callback);
        } else {
            return LogManager.getLogger(clazz);
        }
    }
    
    /**
     * Creates a standard logger without callback functionality
     */
    public static Logger createStandardLogger(Class<?> clazz) {
        return LogManager.getLogger(clazz);
    }
} 